-- Pairing milestone: a two-person, expiring, one-time invitation handshake.
-- Apply AFTER 20261009000001_velvet_core.sql.
-- No client may insert or update couple memberships directly.
-- Not yet deployed to a Supabase project.
create table if not exists public.velvet_pair_invites (
  couple_id uuid primary key references public.velvet_couples(id) on delete cascade,
  code_hash bytea not null unique,
  issued_by uuid not null references auth.users(id) on delete cascade,
  expires_at timestamptz not null,
  consumed_by uuid references auth.users(id),
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.velvet_pair_attempts (
  user_id uuid primary key references auth.users(id) on delete cascade,
  window_start timestamptz not null default now(),
  attempts integer not null default 0
);

alter table public.velvet_pair_invites enable row level security;
alter table public.velvet_pair_attempts enable row level security;
-- Intentionally no RLS policies for either table. Only these narrowly scoped,
-- audited RPCs can read/write them; client REST table operations remain denied.

create or replace function public.velvet_create_pair_invite()
returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_code text;
  v_count integer;
  v_expiry timestamptz := now() + interval '20 minutes';
begin
  if v_user is null or coalesce((select auth.jwt()->>'is_anonymous'),'false')='true' then
    raise exception 'Sign in with a verified account first' using errcode='42501';
  end if;
  if not exists(
    select 1 from auth.users u where u.id=v_user
      and (u.email_confirmed_at is not null or u.phone_confirmed_at is not null)
  ) then
    raise exception 'Confirm your email or phone first' using errcode='42501';
  end if;

  select m.couple_id into v_couple
    from public.velvet_members m where m.user_id=v_user;
  if v_couple is null then
    insert into public.velvet_couples(created_by) values(v_user)
      returning id into v_couple;
    insert into public.velvet_members(couple_id,user_id) values(v_couple,v_user);
  end if;

  -- Serialize invitation creation with joins on the same couple.
  perform 1 from public.velvet_couples where id=v_couple for update;
  select count(*) into v_count from public.velvet_members where couple_id=v_couple;
  if v_count <> 1 then
    raise exception 'This space is already paired' using errcode='23514';
  end if;

  -- A 20-hex-character code holds 80 unpredictable bits and is copy/shareable.
  -- Only its SHA256 digest is stored; repeated generation invalidates older codes.
  v_code := upper(substr(pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(
    pg_catalog.gen_random_uuid()::text || pg_catalog.gen_random_uuid()::text,'UTF8')),'hex'),1,20));
  insert into public.velvet_pair_invites(couple_id,code_hash,issued_by,expires_at,consumed_by,consumed_at,created_at)
    values(v_couple,pg_catalog.sha256(pg_catalog.convert_to(v_code,'UTF8')),v_user,v_expiry,null,null,now())
  on conflict(couple_id) do update set
    code_hash=excluded.code_hash, issued_by=excluded.issued_by,
    expires_at=excluded.expires_at, consumed_by=null,consumed_at=null,created_at=now();
  return jsonb_build_object(
    'couple_id',v_couple,'code',substring(v_code from 1 for 4)||'-'||
      substring(v_code from 5 for 4)||'-'||substring(v_code from 9 for 4)||'-'||
      substring(v_code from 13 for 4)||'-'||substring(v_code from 17 for 4),
    'expires_at',v_expiry
  );
end;
$$;

create or replace function public.velvet_join_pair_invite(p_code text)
returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_code text;
  v_attempts integer;
  v_invite record;
  v_count integer;
begin
  if v_user is null or coalesce((select auth.jwt()->>'is_anonymous'),'false')='true' then
    raise exception 'Sign in with a verified account first' using errcode='42501';
  end if;
  if not exists(
    select 1 from auth.users u where u.id=v_user
      and (u.email_confirmed_at is not null or u.phone_confirmed_at is not null)
  ) then
    raise exception 'Confirm your email or phone first' using errcode='42501';
  end if;
  if exists(select 1 from public.velvet_members where user_id=v_user) then
    return jsonb_build_object('ok',false,'error','already_paired');
  end if;

  -- Increment failures before validation. Use JSON error results instead of
  -- exceptions, so failed attempts cannot roll back the rate-limit counter.
  insert into public.velvet_pair_attempts(user_id,window_start,attempts)
    values(v_user,now(),1)
  on conflict(user_id) do update set
    attempts=case when public.velvet_pair_attempts.window_start<now()-interval '1 hour'
      then 1 else public.velvet_pair_attempts.attempts+1 end,
    window_start=case when public.velvet_pair_attempts.window_start<now()-interval '1 hour'
      then now() else public.velvet_pair_attempts.window_start end
  returning attempts into v_attempts;
  if v_attempts > 10 then
    return jsonb_build_object('ok',false,'error','rate_limited');
  end if;

  v_code := replace(upper(btrim(coalesce(p_code,''))),'-','');
  if v_code !~ '^[A-F0-9]{20}$' then
    return jsonb_build_object('ok',false,'error','invalid_or_expired');
  end if;
  -- Find a candidate without locking. Consistently acquire the couple lock
  -- FIRST, then recheck/lock the invite to avoid a join/reinvite deadlock.
  select i.couple_id into v_invite
    from public.velvet_pair_invites i
    where i.code_hash=pg_catalog.sha256(pg_catalog.convert_to(v_code,'UTF8'));
  if not found then
    return jsonb_build_object('ok',false,'error','invalid_or_expired');
  end if;
  perform 1 from public.velvet_couples where id=v_invite.couple_id for update;
  select i.couple_id,i.issued_by,i.expires_at,i.consumed_by into v_invite
    from public.velvet_pair_invites i
    where i.couple_id=v_invite.couple_id
      and i.code_hash=pg_catalog.sha256(pg_catalog.convert_to(v_code,'UTF8'))
    for update;
  if not found then
    return jsonb_build_object('ok',false,'error','invalid_or_expired');
  end if;
  if v_invite.expires_at<=now() or v_invite.consumed_by is not null
     or v_invite.issued_by=v_user then
    return jsonb_build_object('ok',false,'error','invalid_or_expired');
  end if;
  select count(*) into v_count from public.velvet_members where couple_id=v_invite.couple_id;
  if v_count<>1 then
    return jsonb_build_object('ok',false,'error','invalid_or_expired');
  end if;
  insert into public.velvet_members(couple_id,user_id) values(v_invite.couple_id,v_user);
  update public.velvet_pair_invites
    set consumed_by=v_user,consumed_at=now() where couple_id=v_invite.couple_id;
  delete from public.velvet_pair_attempts where user_id=v_user;
  return jsonb_build_object('ok',true,'couple_id',v_invite.couple_id);
end;
$$;

create or replace function public.velvet_my_pair()
returns jsonb
language plpgsql stable security definer set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_partner uuid;
  v_name text;
  v_count integer;
begin
  if v_user is null then return null; end if;
  select m.couple_id into v_couple from public.velvet_members m where m.user_id=v_user;
  if v_couple is null then return null; end if;
  select count(*) into v_count from public.velvet_members where couple_id=v_couple;
  select m.user_id,p.display_name into v_partner,v_name
    from public.velvet_members m left join public.velvet_profiles p on p.user_id=m.user_id
    where m.couple_id=v_couple and m.user_id<>v_user limit 1;
  return jsonb_build_object('couple_id',v_couple,'member_count',v_count,
    'partner_id',v_partner,'partner_name',v_name);
end;
$$;

create or replace function public.velvet_revoke_pair_invite()
returns boolean
language plpgsql security definer set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
begin
  if v_user is null then raise exception 'Sign in first' using errcode='42501'; end if;
  delete from public.velvet_pair_invites i where i.issued_by=v_user
    and i.consumed_at is null and exists(
      select 1 from public.velvet_members m where m.couple_id=i.couple_id and m.user_id=v_user
    );
  return found;
end;
$$;

-- Narrow explicit grants: PUBLIC automatically receives EXECUTE unless revoked.
revoke all on public.velvet_pair_invites from anon,authenticated;
revoke all on public.velvet_pair_attempts from anon,authenticated;
revoke execute on function public.velvet_create_pair_invite() from public,anon,authenticated;
revoke execute on function public.velvet_join_pair_invite(text) from public,anon,authenticated;
revoke execute on function public.velvet_my_pair() from public,anon,authenticated;
revoke execute on function public.velvet_revoke_pair_invite() from public,anon,authenticated;
grant execute on function public.velvet_create_pair_invite() to authenticated;
grant execute on function public.velvet_join_pair_invite(text) to authenticated;
grant execute on function public.velvet_my_pair() to authenticated;
grant execute on function public.velvet_revoke_pair_invite() to authenticated;

-- Supabase advisors may flag the intentionally exposed security-definer RPCs.
-- They only use auth.uid() and never allow caller-supplied member IDs.
-- Future: add IP-level abuse protection at the API gateway and invitation audit logs.
