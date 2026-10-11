-- Read receipts are explicit per-recipient acknowledgments, not delivery claims.
-- User can advance only their own read cursor for a visible message authored
-- by their partner in the same fully-paired couple. Both chats are text/images.
create table if not exists public.velvet_read_cursors(
  couple_id uuid not null references public.velvet_couples(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  message_id uuid not null references public.velvet_messages(id) on delete cascade,
  message_at timestamptz not null,
  marked_at timestamptz not null default now(),
  primary key(couple_id,user_id)
);
alter table public.velvet_read_cursors enable row level security;
revoke all on public.velvet_read_cursors from public,anon,authenticated;

create or replace function public.velvet_mark_chat_read(p_message_id uuid)
returns jsonb
language plpgsql security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_message public.velvet_messages%rowtype;
  v_previous record;
  v_applied boolean := false;
begin
  if v_user is null then
    raise exception 'Sign in first' using errcode='42501';
  end if;
  if p_message_id is null then
    return jsonb_build_object('ok',false,'error','not_found');
  end if;
  select m.couple_id into v_couple from public.velvet_members m
    where m.user_id=v_user;
  if v_couple is null then
    return jsonb_build_object('ok',false,'error','not_paired');
  end if;
  -- All per-couple writes acquire the same parent lock. Concurrent receipts
  -- serialize and cannot decrease the watermark, even across client retries.
  perform 1 from public.velvet_couples c where c.id=v_couple for update;
  if (select count(*) from public.velvet_members m where m.couple_id=v_couple)<>2 then
    return jsonb_build_object('ok',false,'error','not_paired');
  end if;
  select * into v_message from public.velvet_messages m
    where m.id=p_message_id and m.couple_id=v_couple
      and m.kind in ('text','image');
  if not found then
    return jsonb_build_object('ok',false,'error','not_found');
  end if;
  if v_message.sender_id=v_user then
    return jsonb_build_object('ok',false,'error','own_message');
  end if;
  if v_message.deleted_at is not null or exists (
    select 1 from public.velvet_hidden_messages h
      where h.user_id=v_user and h.message_id=p_message_id
  ) then
    return jsonb_build_object('ok',false,'error','not_visible');
  end if;

  select c.message_id,c.message_at into v_previous
    from public.velvet_read_cursors c
    where c.user_id=v_user and c.couple_id=v_couple;
  if not found or
    (v_previous.message_at,v_previous.message_id)<(v_message.created_at,v_message.id) then
    insert into public.velvet_read_cursors(couple_id,user_id,message_id,message_at,marked_at)
      values(v_couple,v_user,p_message_id,v_message.created_at,now())
    on conflict(couple_id,user_id) do update set
      message_id=excluded.message_id,
      message_at=excluded.message_at,
      marked_at=excluded.marked_at;
    v_applied:=true;
  end if;

  return jsonb_build_object('ok',true,'advanced',v_applied,
    'message_id',case when v_applied then p_message_id
         else v_previous.message_id end);
end;
$$;

create or replace function public.velvet_chat_read_state()
returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_partner uuid;
  v_mine record;
  v_theirs record;
begin
  if v_user is null then
    raise exception 'Sign in first' using errcode='42501';
  end if;
  select m.couple_id into v_couple from public.velvet_members m
    where m.user_id=v_user;
  if v_couple is null or
     (select count(*) from public.velvet_members m
        where m.couple_id=v_couple)<>2 then
    return null;
  end if;
  select m.user_id into v_partner from public.velvet_members m
    where m.couple_id=v_couple and m.user_id<>v_user;
  select r.message_id,r.message_at into v_mine
    from public.velvet_read_cursors r
    where r.couple_id=v_couple and r.user_id=v_user;
  select r.message_id,r.message_at into v_theirs
    from public.velvet_read_cursors r
    where r.couple_id=v_couple and r.user_id=v_partner;
  return jsonb_build_object(
    'my_read_id',v_mine.message_id,'my_read_at',v_mine.message_at,
    'partner_read_id',v_theirs.message_id,'partner_read_at',v_theirs.message_at);
end;
$$;

revoke execute on function public.velvet_mark_chat_read(uuid)
  from public,anon,authenticated;
revoke execute on function public.velvet_chat_read_state()
  from public,anon,authenticated;
grant execute on function public.velvet_mark_chat_read(uuid) to authenticated;
grant execute on function public.velvet_chat_read_state() to authenticated;
