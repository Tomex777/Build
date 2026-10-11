-- Velvet profile synchronization. Requires pairing, 20261011000004 text.
-- Profiles belong exclusively to their authenticated owner; partner profiles
-- are view-only. No client chooses a target user_id or avatar storage key.
revoke insert,update,delete on public.velvet_profiles from public,anon,authenticated;

create or replace function public.velvet_get_my_profile()
returns jsonb
language sql stable security definer set search_path=''
as $$
  select case when (select auth.uid()) is null then null
    else coalesce(
      (select jsonb_build_object(
        'display_name',p.display_name,'pronouns',p.pronouns,
        'updated_at',p.updated_at
      ) from public.velvet_profiles p where p.user_id=(select auth.uid())),
      jsonb_build_object('display_name','','pronouns',null,'updated_at',null)
    )
  end
$$;

create or replace function public.velvet_update_my_profile(
  p_display_name text, p_pronouns text default null
) returns jsonb
language plpgsql security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_name text := btrim(coalesce(p_display_name,''));
  v_pronouns text := nullif(btrim(coalesce(p_pronouns,'')),'');
  v_profile public.velvet_profiles%rowtype;
begin
  if v_user is null or coalesce((select auth.jwt()->>'is_anonymous'),'false')='true' then
    raise exception 'Sign in with a verified account' using errcode='42501';
  end if;
  if not exists (
    select 1 from auth.users u where u.id=v_user
      and (u.email_confirmed_at is not null or u.phone_confirmed_at is not null)
  ) then
    raise exception 'Confirm your email or phone first' using errcode='42501';
  end if;
  if char_length(v_name) not between 1 and 50 then
    return jsonb_build_object('ok',false,'error','invalid_name');
  end if;
  if v_pronouns is not null and char_length(v_pronouns)>32 then
    return jsonb_build_object('ok',false,'error','invalid_pronouns');
  end if;

  insert into public.velvet_profiles(user_id,display_name,pronouns,updated_at)
    values(v_user,v_name,v_pronouns,now())
  on conflict(user_id) do update set display_name=excluded.display_name,
    pronouns=excluded.pronouns,updated_at=excluded.updated_at
  returning * into v_profile;
  return jsonb_build_object('ok',true,'display_name',v_profile.display_name,
    'pronouns',v_profile.pronouns,'updated_at',v_profile.updated_at);
end;
$$;

-- Expand the existing pairing contract, retaining all v0.9 response fields.
-- The caller receives their own profile and their partner's display fields;
-- other couples, account emails and phone numbers are never returned.
create or replace function public.velvet_my_pair()
returns jsonb
language plpgsql stable security definer set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_partner uuid;
  v_name text;
  v_pronouns text;
  v_own_name text;
  v_own_pronouns text;
  v_count integer;
begin
  if v_user is null then return null; end if;
  select m.couple_id into v_couple from public.velvet_members m where m.user_id=v_user;
  if v_couple is null then return null; end if;
  select count(*) into v_count from public.velvet_members where couple_id=v_couple;
  select p.display_name,p.pronouns into v_own_name,v_own_pronouns
    from public.velvet_profiles p where p.user_id=v_user;
  select m.user_id,p.display_name,p.pronouns into v_partner,v_name,v_pronouns
    from public.velvet_members m
      left join public.velvet_profiles p on p.user_id=m.user_id
    where m.couple_id=v_couple and m.user_id<>v_user limit 1;
  return jsonb_build_object('couple_id',v_couple,'member_count',v_count,
    'my_id',v_user,'own_name',v_own_name,'own_pronouns',v_own_pronouns,
    'partner_id',v_partner,'partner_name',v_name,
    'partner_pronouns',v_pronouns);
end;
$$;

revoke execute on function public.velvet_get_my_profile() from public,anon,authenticated;
revoke execute on function public.velvet_update_my_profile(text,text) from public,anon,authenticated;
revoke execute on function public.velvet_my_pair() from public,anon,authenticated;
grant execute on function public.velvet_get_my_profile() to authenticated;
grant execute on function public.velvet_update_my_profile(text,text) to authenticated;
grant execute on function public.velvet_my_pair() to authenticated;
