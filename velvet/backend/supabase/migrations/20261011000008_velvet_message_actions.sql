-- Text message actions + deterministic per-couple mutation feed.
-- Requires 20261011000007_velvet_shared_anniversary.sql.
-- The feed is scoped by authenticated couple membership; never accept a
-- caller-chosen couple_id. Revision sequence is serialized using couple locks.

create table if not exists public.velvet_text_changes (
  id bigint generated always as identity primary key,
  couple_id uuid not null references public.velvet_couples(id) on delete cascade,
  message_id uuid not null references public.velvet_messages(id) on delete cascade,
  actor_id uuid not null references auth.users(id) on delete cascade,
  action text not null check(action in ('edited','deleted','hidden')),
  created_at timestamptz not null default now()
);
create index if not exists velvet_text_changes_by_couple
  on public.velvet_text_changes(couple_id,id);
alter table public.velvet_text_changes enable row level security;
revoke all on public.velvet_text_changes from public,anon,authenticated;

create or replace function public.velvet_edit_text(p_id uuid,p_body text)
returns jsonb
language plpgsql security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_record public.velvet_messages%rowtype;
  v_body text := btrim(coalesce(p_body,''));
  v_edit_at timestamptz;
begin
  if v_user is null then raise exception 'Sign in first' using errcode='42501'; end if;
  if p_id is null or char_length(v_body) not between 1 and 4000 then
    return jsonb_build_object('ok',false,'error','invalid_message');
  end if;
  select m.couple_id into v_couple from public.velvet_members m where m.user_id=v_user;
  if v_couple is null then return jsonb_build_object('ok',false,'error','not_paired'); end if;
  -- Always lock the couple before any message lock to serialize event ordering.
  perform 1 from public.velvet_couples c where c.id=v_couple for update;
  if (select count(*) from public.velvet_members m where m.couple_id=v_couple)<>2 then
    return jsonb_build_object('ok',false,'error','not_paired');
  end if;
  select * into v_record from public.velvet_messages m
    where m.id=p_id and m.couple_id=v_couple and m.kind='text' for update;
  if not found then return jsonb_build_object('ok',false,'error','not_found'); end if;
  if v_record.sender_id<>v_user then return jsonb_build_object('ok',false,'error','not_sender'); end if;
  if v_record.deleted_at is not null then
    return jsonb_build_object('ok',false,'error','already_deleted');
  end if;
  if v_record.created_at<now()-interval '10 minutes' then
    return jsonb_build_object('ok',false,'error','edit_window_expired');
  end if;
  if v_record.body=v_body then
    return jsonb_build_object('ok',true,'body',v_body,'edited_at',v_record.edited_at,'unchanged',true);
  end if;
  update public.velvet_messages set body=v_body,edited_at=clock_timestamp()
    where id=p_id returning edited_at into v_edit_at;
  insert into public.velvet_text_changes(couple_id,message_id,actor_id,action)
    values(v_couple,p_id,v_user,'edited');
  return jsonb_build_object('ok',true,'body',v_body,'edited_at',v_edit_at,'unchanged',false);
end;
$$;

create or replace function public.velvet_delete_text(p_id uuid,p_for_everyone boolean default false)
returns jsonb
language plpgsql security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_record public.velvet_messages%rowtype;
begin
  if v_user is null then raise exception 'Sign in first' using errcode='42501'; end if;
  if p_id is null then return jsonb_build_object('ok',false,'error','not_found'); end if;
  select m.couple_id into v_couple from public.velvet_members m where m.user_id=v_user;
  if v_couple is null then return jsonb_build_object('ok',false,'error','not_paired'); end if;
  perform 1 from public.velvet_couples c where c.id=v_couple for update;
  if (select count(*) from public.velvet_members m where m.couple_id=v_couple)<>2 then
    return jsonb_build_object('ok',false,'error','not_paired');
  end if;
  select * into v_record from public.velvet_messages m
    where m.id=p_id and m.couple_id=v_couple and m.kind='text' for update;
  if not found then return jsonb_build_object('ok',false,'error','not_found'); end if;

  if coalesce(p_for_everyone,false) then
    if v_record.sender_id<>v_user then
      return jsonb_build_object('ok',false,'error','not_sender');
    end if;
    if v_record.deleted_at is not null then
      return jsonb_build_object('ok',true,'already_deleted',true);
    end if;
    if v_record.created_at<now()-interval '48 hours' then
      return jsonb_build_object('ok',false,'error','delete_window_expired');
    end if;
    -- Erase message body, retain only tombstone metadata for quoted replies.
    update public.velvet_messages
      set body=null,deleted_at=clock_timestamp() where id=p_id;
    insert into public.velvet_text_changes(couple_id,message_id,actor_id,action)
      values(v_couple,p_id,v_user,'deleted');
    return jsonb_build_object('ok',true,'for_everyone',true);
  end if;

  -- Hide only from this member; the partner's copy is untouched.
  insert into public.velvet_hidden_messages(user_id,message_id)
    values(v_user,p_id) on conflict do nothing;
  if found then
    insert into public.velvet_text_changes(couple_id,message_id,actor_id,action)
      values(v_couple,p_id,v_user,'hidden');
  end if;
  return jsonb_build_object('ok',true,'for_everyone',false);
end;
$$;

create or replace function public.velvet_list_text_changes(
  p_after_id bigint default 0,p_limit integer default 100
) returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_items jsonb;
begin
  if v_user is null then raise exception 'Sign in first' using errcode='42501'; end if;
  select m.couple_id into v_couple from public.velvet_members m where m.user_id=v_user;
  if v_couple is null then return '[]'::jsonb; end if;
  if (select count(*) from public.velvet_members m where m.couple_id=v_couple)<>2 then
    return '[]'::jsonb;
  end if;
  select coalesce(jsonb_agg(to_jsonb(t) order by t.change_id),'[]'::jsonb)
    into v_items from (
      select c.id as change_id,c.message_id,c.action,
        case when m.deleted_at is null and c.action='edited' then m.body
          else null end as body,
        m.edited_at, (m.deleted_at is not null) as deleted
      from public.velvet_text_changes c
        join public.velvet_messages m on m.id=c.message_id
      where c.couple_id=v_couple and c.id>greatest(0,coalesce(p_after_id,0))
        and (c.action<>'hidden' or c.actor_id=v_user)
      order by c.id asc limit least(greatest(coalesce(p_limit,100),1),100)
    ) t;
  return v_items;
end;
$$;

-- Both list endpoints include tombstones but never reveal erased text.
-- A user's private hidden-message list is excluded, including during a replay.
create or replace function public.velvet_list_text(
  p_after_at timestamptz default null,
  p_after_id uuid default null,
  p_limit integer default 80
) returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_items jsonb;
  v_limit integer := least(greatest(coalesce(p_limit,80),1),100);
begin
  if v_user is null then raise exception 'Sign in first' using errcode='42501'; end if;
  if (p_after_at is null)<>(p_after_id is null) then
    raise exception 'Both cursor fields are required' using errcode='22023';
  end if;
  select m.couple_id into v_couple from public.velvet_members m where m.user_id=v_user;
  if v_couple is null then return '[]'::jsonb; end if;
  if (select count(*) from public.velvet_members m where m.couple_id=v_couple)<>2 then
    return '[]'::jsonb;
  end if;
  if p_after_at is null then
    select coalesce(jsonb_agg(to_jsonb(t) order by t.created_at,t.id),'[]'::jsonb)
      into v_items from (
      select m.id,m.sender_id,
        case when m.deleted_at is null then m.body else null end as body,
        m.reply_to,m.created_at,m.edited_at,
        (m.deleted_at is not null) as deleted
      from public.velvet_messages m
      where m.couple_id=v_couple and m.kind='text'
        and not exists (select 1 from public.velvet_hidden_messages h
          where h.message_id=m.id and h.user_id=v_user)
      order by m.created_at desc,m.id desc limit v_limit
      ) t;
  else
    select coalesce(jsonb_agg(to_jsonb(t) order by t.created_at,t.id),'[]'::jsonb)
      into v_items from (
      select m.id,m.sender_id,
        case when m.deleted_at is null then m.body else null end as body,
        m.reply_to,m.created_at,m.edited_at,
        (m.deleted_at is not null) as deleted
      from public.velvet_messages m
      where m.couple_id=v_couple and m.kind='text'
        and (m.created_at,m.id)>(p_after_at,p_after_id)
        and not exists (select 1 from public.velvet_hidden_messages h
          where h.message_id=m.id and h.user_id=v_user)
      order by m.created_at,m.id limit v_limit
      ) t;
  end if;
  return v_items;
end;
$$;

create or replace function public.velvet_page_text(
  p_before_at timestamptz default null,
  p_before_id uuid default null,
  p_limit integer default 80
) returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_items jsonb;
begin
  if v_user is null then raise exception 'Sign in first' using errcode='42501'; end if;
  if (p_before_at is null)<>(p_before_id is null) then
    raise exception 'Both cursor fields are required' using errcode='22023';
  end if;
  select m.couple_id into v_couple from public.velvet_members m where m.user_id=v_user;
  if v_couple is null then return '[]'::jsonb; end if;
  if (select count(*) from public.velvet_members m where m.couple_id=v_couple)<>2 then
    return '[]'::jsonb;
  end if;
  select coalesce(jsonb_agg(to_jsonb(t) order by t.created_at,t.id),'[]'::jsonb)
    into v_items from (
    select m.id,m.sender_id,
      case when m.deleted_at is null then m.body else null end as body,
      m.reply_to,m.created_at,m.edited_at,
      (m.deleted_at is not null) as deleted
    from public.velvet_messages m
    where m.couple_id=v_couple and m.kind='text'
      and (p_before_at is null or (m.created_at,m.id)<(p_before_at,p_before_id))
      and not exists (select 1 from public.velvet_hidden_messages h
        where h.message_id=m.id and h.user_id=v_user)
    order by m.created_at desc,m.id desc
    limit least(greatest(coalesce(p_limit,80),1),100)
    ) t;
  return v_items;
end;
$$;

revoke execute on function public.velvet_edit_text(uuid,text) from public,anon,authenticated;
revoke execute on function public.velvet_delete_text(uuid,boolean) from public,anon,authenticated;
revoke execute on function public.velvet_list_text_changes(bigint,integer) from public,anon,authenticated;
grant execute on function public.velvet_edit_text(uuid,text) to authenticated;
grant execute on function public.velvet_delete_text(uuid,boolean) to authenticated;
grant execute on function public.velvet_list_text_changes(bigint,integer) to authenticated;
