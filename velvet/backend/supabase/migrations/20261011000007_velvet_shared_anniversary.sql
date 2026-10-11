-- Shared anniversary synchronization. Requires the Velvet core and pairing
-- migrations. Both verified members may propose the relationship start date.
-- This is a shared field, not the other person's profile.
alter table public.velvet_couples
  add column if not exists anniversary_updated_at timestamptz,
  add column if not exists anniversary_edited_by uuid references auth.users(id);

-- A member only gets the shared date for their own fully paired relationship.
create or replace function public.velvet_get_anniversary()
returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_count integer;
  v_anniversary date;
  v_changed_at timestamptz;
  v_editor uuid;
begin
  if v_user is null then raise exception 'Sign in first' using errcode='42501'; end if;
  select m.couple_id into v_couple
    from public.velvet_members m where m.user_id=v_user;
  if v_couple is null then return null; end if;
  select count(*) into v_count from public.velvet_members m where m.couple_id=v_couple;
  if v_count<>2 then return null; end if;
  select c.anniversary,c.anniversary_updated_at,c.anniversary_edited_by
    into v_anniversary,v_changed_at,v_editor
    from public.velvet_couples c where c.id=v_couple;
  return jsonb_build_object('anniversary',v_anniversary,
    'updated_at',v_changed_at,'edited_by',v_editor);
end;
$$;

create or replace function public.velvet_set_anniversary(p_date date)
returns jsonb
language plpgsql security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_count integer;
  v_updated timestamptz;
begin
  if v_user is null or coalesce((select auth.jwt()->>'is_anonymous'),'false')='true' then
    raise exception 'Sign in to update our date' using errcode='42501';
  end if;
  if not exists (
    select 1 from auth.users u where u.id=v_user
      and (u.email_confirmed_at is not null or u.phone_confirmed_at is not null)
  ) then
    raise exception 'Confirm your email or phone first' using errcode='42501';
  end if;
  if p_date is null or p_date>current_date or p_date<date '1900-01-01' then
    return jsonb_build_object('ok',false,'error','invalid_date');
  end if;
  select m.couple_id into v_couple from public.velvet_members m where m.user_id=v_user;
  if v_couple is null then
    return jsonb_build_object('ok',false,'error','not_paired');
  end if;

  -- Lock the relationship row to serialize two partners' date edits.
  perform 1 from public.velvet_couples c where c.id=v_couple for update;
  select count(*) into v_count from public.velvet_members m where m.couple_id=v_couple;
  if v_count<>2 then
    return jsonb_build_object('ok',false,'error','not_paired');
  end if;
  update public.velvet_couples set anniversary=p_date,
    anniversary_updated_at=now(),anniversary_edited_by=v_user
    where id=v_couple
    returning anniversary_updated_at into v_updated;
  return jsonb_build_object('ok',true,'anniversary',p_date,
    'updated_at',v_updated,'edited_by',v_user);
end;
$$;

-- Direct client writes to the couple's date and creator are forbidden.
revoke insert,update,delete on public.velvet_couples from public,anon,authenticated;
revoke execute on function public.velvet_get_anniversary() from public,anon,authenticated;
revoke execute on function public.velvet_set_anniversary(date) from public,anon,authenticated;
grant execute on function public.velvet_get_anniversary() to authenticated;
grant execute on function public.velvet_set_anniversary(date) to authenticated;
