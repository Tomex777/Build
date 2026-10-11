-- Online text chat milestone. Requires 20261009 core + 20261011 pairing migrations.
-- Authenticated RPCs expose only one paired couple's text messages; never trust
-- couple_id or sender_id supplied by a mobile client.
-- This migration is versioned in GitHub but NOT deployed to Supabase yet.

create table if not exists public.velvet_text_send_rates (
  user_id uuid primary key references auth.users(id) on delete cascade,
  window_start timestamptz not null default now(),
  attempts integer not null default 0
);
alter table public.velvet_text_send_rates enable row level security;
revoke all on public.velvet_text_send_rates from public, anon, authenticated;

-- Stop legacy direct client INSERT/UPDATE/DELETE: existing update RLS allows
-- changes to arbitrary columns on one's own message. Route writes through the
-- scoped RPC below until field-specific edit/delete endpoints are audited.
revoke insert,update,delete on public.velvet_messages from public,anon,authenticated;
revoke select on public.velvet_messages from public,anon,authenticated;

create or replace function public.velvet_send_text(
  p_id uuid, p_body text, p_reply_to uuid default null
) returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_count integer;
  v_attempts integer;
  v_text text := btrim(coalesce(p_body,''));
  v_message public.velvet_messages%rowtype;
begin
  if v_user is null or coalesce((select auth.jwt()->>'is_anonymous'),'false')='true' then
    raise exception 'Sign in to send messages' using errcode='42501';
  end if;
  if p_id is null or char_length(v_text) not between 1 and 4000 then
    return jsonb_build_object('ok',false,'error','invalid_message');
  end if;
  select m.couple_id into v_couple from public.velvet_members m where m.user_id=v_user;
  if v_couple is null then
    return jsonb_build_object('ok',false,'error','not_paired');
  end if;

  -- A duplicate retry must be free of rate limits and never rewrite data.
  select * into v_message from public.velvet_messages where id=p_id;
  if found then
    if v_message.couple_id=v_couple and v_message.sender_id=v_user
       and v_message.kind='text' and v_message.body=v_text
       and v_message.reply_to is not distinct from p_reply_to then
      return jsonb_build_object('ok',true,'id',v_message.id,
        'created_at',v_message.created_at,'duplicate',true);
    end if;
    return jsonb_build_object('ok',false,'error','id_conflict');
  end if;

  select count(*) into v_count from public.velvet_members where couple_id=v_couple;
  if v_count<>2 then return jsonb_build_object('ok',false,'error','not_paired'); end if;
  if p_reply_to is not null and not exists(
    select 1 from public.velvet_messages m
    where m.id=p_reply_to and m.couple_id=v_couple and m.deleted_at is null
  ) then
    return jsonb_build_object('ok',false,'error','invalid_reply');
  end if;

  -- Rate-limit without an exception, so a failed request still counts.
  insert into public.velvet_text_send_rates(user_id,window_start,attempts)
    values(v_user,now(),1)
  on conflict(user_id) do update set
    attempts=case when public.velvet_text_send_rates.window_start<now()-interval '1 minute'
      then 1 else public.velvet_text_send_rates.attempts+1 end,
    window_start=case when public.velvet_text_send_rates.window_start<now()-interval '1 minute'
      then now() else public.velvet_text_send_rates.window_start end
  returning attempts into v_attempts;
  if v_attempts>40 then return jsonb_build_object('ok',false,'error','rate_limited'); end if;

  insert into public.velvet_messages(id,couple_id,sender_id,kind,body,reply_to)
    values(p_id,v_couple,v_user,'text',v_text,p_reply_to)
  on conflict(id) do nothing;
  select * into v_message from public.velvet_messages where id=p_id;
  if v_message.couple_id<>v_couple or v_message.sender_id<>v_user
     or v_message.kind<>'text' or v_message.body<>v_text
     or v_message.reply_to is distinct from p_reply_to then
    return jsonb_build_object('ok',false,'error','id_conflict');
  end if;
  return jsonb_build_object('ok',true,'id',v_message.id,
    'created_at',v_message.created_at,'duplicate',false);
end;
$$;

create or replace function public.velvet_list_text(
  p_after_at timestamptz default null,
  p_after_id uuid default null,
  p_limit integer default 80
) returns jsonb
language plpgsql stable security definer set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_count integer;
  v_items jsonb;
  v_limit integer := least(greatest(coalesce(p_limit,80),1),100);
begin
  if v_user is null then raise exception 'Sign in first' using errcode='42501'; end if;
  select m.couple_id into v_couple from public.velvet_members m where m.user_id=v_user;
  if v_couple is null then return '[]'::jsonb; end if;
  select count(*) into v_count from public.velvet_members where couple_id=v_couple;
  if v_count<>2 then return '[]'::jsonb; end if;

  -- The first page is the newest 80. Subsequent pages are chronological
  -- after a full (timestamp, UUID) cursor, which avoids timestamp ties.
  if p_after_at is null then
    select coalesce(jsonb_agg(to_jsonb(t) order by t.created_at,t.id),'[]'::jsonb)
      into v_items from (
      select id,sender_id,body,reply_to,created_at
        from public.velvet_messages
        where couple_id=v_couple and kind='text' and deleted_at is null
        order by created_at desc,id desc limit v_limit
      ) t;
  else
    if p_after_id is null then
      raise exception 'Timestamp cursors require a message id' using errcode='22023';
    end if;
    select coalesce(jsonb_agg(to_jsonb(t) order by t.created_at,t.id),'[]'::jsonb)
      into v_items from (
      select id,sender_id,body,reply_to,created_at
        from public.velvet_messages
        where couple_id=v_couple and kind='text' and deleted_at is null
          and (created_at,id)>(p_after_at,p_after_id)
        order by created_at,id limit v_limit
      ) t;
  end if;
  return v_items;
end;
$$;

-- Ensure both account functions and chat functions are restricted to a real
-- signed-in role; pair membership is rechecked INSIDE each RPC.
revoke execute on function public.velvet_send_text(uuid,text,uuid) from public,anon,authenticated;
revoke execute on function public.velvet_list_text(timestamptz,uuid,integer) from public,anon,authenticated;
grant execute on function public.velvet_send_text(uuid,text,uuid) to authenticated;
grant execute on function public.velvet_list_text(timestamptz,uuid,integer) to authenticated;

-- The app needs its own ID to distinguish sender bubbles; no arbitrary user
-- lookup is exposed. This replaces the existing membership-only JSON contract.
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
    'my_id',v_user,'partner_id',v_partner,'partner_name',v_name);
end;
$$;
revoke execute on function public.velvet_my_pair() from public,anon,authenticated;
grant execute on function public.velvet_my_pair() to authenticated;
