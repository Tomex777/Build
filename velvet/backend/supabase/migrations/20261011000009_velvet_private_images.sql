-- Private, couple-scoped image messages. Supabase Storage must be installed.
-- No public image links. Images remain in Chat and are NEVER auto-added to Our Story.
-- Requires migration 20261011000008.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('velvet-chat-media','velvet-chat-media',false,8388608,
  array['image/jpeg','image/png','image/webp','image/gif'])
on conflict (id) do update set public=false,file_size_limit=8388608,
  allowed_mime_types=excluded.allowed_mime_types;

-- Preserve an immutable claim for every bound Storage object, including after a
-- delete-for-everyone tombstone removes the message payload. Prevents recycling
-- a deleted image path into another message. Clients have zero table privileges.
create table if not exists public.velvet_image_claims (
  storage_path text primary key,
  message_id uuid not null unique,
  couple_id uuid not null references public.velvet_couples(id) on delete cascade,
  owner_id uuid not null references auth.users(id) on delete cascade,
  claimed_at timestamptz not null default now()
);
alter table public.velvet_image_claims enable row level security;
revoke all on public.velvet_image_claims from public,anon,authenticated;

-- <couple UUID>/<sender UUID>/<random UUID>.<ext> exactly.
-- A paired member can upload only under their OWN user-id folder.
create policy velvet_image_upload on storage.objects for insert to authenticated
with check (
  bucket_id='velvet-chat-media'
  and (storage.foldername(name))[2]=(select auth.uid())::text
  and name ~* '^[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\.(jpg|png|webp|gif)$'
  and exists (
    select 1 from public.velvet_members mine
    where mine.user_id=(select auth.uid())
      and mine.couple_id::text=(storage.foldername(name))[1]
      and (select count(*) from public.velvet_members members
           where members.couple_id=mine.couple_id)=2
  )
);

-- SECURITY DEFINER check: signed-in Storage roles have no unrestricted
-- SELECT on velvet_messages. A narrowly-scoped boolean helper makes the
-- Storage GET policy work without granting clients direct message-table access.
create or replace function public.velvet_image_is_visible(p_name text)
returns boolean
language sql stable security definer set search_path=''
as $$
  select (select auth.uid()) is not null and exists (
    select 1 from public.velvet_members mine
      join public.velvet_messages m on m.couple_id=mine.couple_id
    where mine.user_id=(select auth.uid())
      and mine.couple_id::text=(storage.foldername(p_name))[1]
      and m.kind='image' and m.payload->>'storage_path'=p_name
      and m.deleted_at is null
      and (select count(*) from public.velvet_members members
        where members.couple_id=mine.couple_id)=2
      and not exists(select 1 from public.velvet_hidden_messages hidden
        where hidden.user_id=mine.user_id and hidden.message_id=m.id)
  )
$$;
revoke execute on function public.velvet_image_is_visible(text)
  from public,anon,authenticated;
grant execute on function public.velvet_image_is_visible(text) to authenticated;

-- The object's path alone is not permission to download. Only an existing,
-- non-deleted, non-personally-hidden image message enables authenticated GET.
create policy velvet_image_download on storage.objects for select to authenticated
using (
  bucket_id='velvet-chat-media' and public.velvet_image_is_visible(name)
);

-- There is deliberately no UPDATE/DELETE storage policy for the mobile client.
-- Failed uploads can create orphan objects; deployment must include scheduled
-- server-side orphan cleanup, which is NOT yet provisioned.

create or replace function public.velvet_send_image(
  p_id uuid,p_storage_path text,p_mime text,p_bytes bigint,
  p_caption text default null,p_reply_to uuid default null
) returns jsonb
language plpgsql security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_count integer;
  v_caption text := btrim(coalesce(p_caption,''));
  v_message public.velvet_messages%rowtype;
  v_attempts integer;
  v_ext text;
begin
  if v_user is null or coalesce((select auth.jwt()->>'is_anonymous'),'false')='true' then
    raise exception 'Sign in to share images' using errcode='42501';
  end if;
  if p_id is null or p_bytes is null or p_bytes<1 or p_bytes>8388608
      or char_length(v_caption)>1200 or p_storage_path is null
      or p_storage_path !~* '^[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\.(jpg|png|webp|gif)$'
      or p_mime not in ('image/jpeg','image/png','image/webp','image/gif') then
    return jsonb_build_object('ok',false,'error','invalid_image');
  end if;
  v_ext:=lower(substring(p_storage_path from '\.([^.]+)$'));
  if (p_mime='image/jpeg' and v_ext<>'jpg')
    or (p_mime='image/png' and v_ext<>'png')
    or (p_mime='image/webp' and v_ext<>'webp')
    or (p_mime='image/gif' and v_ext<>'gif') then
    return jsonb_build_object('ok',false,'error','invalid_image');
  end if;

  select mine.couple_id into v_couple from public.velvet_members mine
    where mine.user_id=v_user;
  if v_couple is null then return jsonb_build_object('ok',false,'error','not_paired'); end if;
  if split_part(p_storage_path,'/',1)<>v_couple::text
      or split_part(p_storage_path,'/',2)<>v_user::text then
    return jsonb_build_object('ok',false,'error','wrong_owner');
  end if;
  perform 1 from public.velvet_couples where id=v_couple for update;
  select count(*) into v_count from public.velvet_members where couple_id=v_couple;
  if v_count<>2 then return jsonb_build_object('ok',false,'error','not_paired'); end if;

  select * into v_message from public.velvet_messages where id=p_id;
  if found then
    if v_message.couple_id=v_couple and v_message.sender_id=v_user
      and v_message.kind='image' and v_message.body is not distinct from nullif(v_caption,'')
      and v_message.reply_to is not distinct from p_reply_to
      and v_message.payload->>'storage_path'=p_storage_path
      and v_message.payload->>'mime'=p_mime
      and (v_message.payload->>'bytes')::bigint=p_bytes then
      return jsonb_build_object('ok',true,'id',v_message.id,'duplicate',true);
    end if;
    return jsonb_build_object('ok',false,'error','id_conflict');
  end if;

  if p_reply_to is not null and not exists(
    select 1 from public.velvet_messages m where m.id=p_reply_to
      and m.couple_id=v_couple and m.deleted_at is null
  ) then return jsonb_build_object('ok',false,'error','invalid_reply'); end if;

  -- Ensure binary actually exists in the private bucket, was uploaded by
  -- the sender, and has not already been bound to another chat message.
  if not exists (
    select 1 from storage.objects o where o.bucket_id='velvet-chat-media'
      and o.name=p_storage_path and o.owner_id=v_user::text
  ) then return jsonb_build_object('ok',false,'error','file_not_uploaded'); end if;
  if exists (
    select 1 from public.velvet_messages m
    where m.kind='image' and m.payload->>'storage_path'=p_storage_path
  ) then return jsonb_build_object('ok',false,'error','file_already_sent'); end if;

  insert into public.velvet_text_send_rates(user_id,window_start,attempts)
    values(v_user,now(),1)
  on conflict(user_id) do update set
    attempts=case when public.velvet_text_send_rates.window_start<now()-interval '1 minute'
      then 1 else public.velvet_text_send_rates.attempts+1 end,
    window_start=case when public.velvet_text_send_rates.window_start<now()-interval '1 minute'
      then now() else public.velvet_text_send_rates.window_start end
  returning attempts into v_attempts;
  if v_attempts>40 then return jsonb_build_object('ok',false,'error','rate_limited'); end if;

  insert into public.velvet_image_claims(storage_path,message_id,couple_id,owner_id)
    values(p_storage_path,p_id,v_couple,v_user)
    on conflict do nothing;
  if not found then
    return jsonb_build_object('ok',false,'error','file_already_sent');
  end if;

  insert into public.velvet_messages(id,couple_id,sender_id,kind,body,reply_to,payload)
  values(p_id,v_couple,v_user,'image',nullif(v_caption,''),p_reply_to,
    jsonb_build_object('storage_path',p_storage_path,'mime',p_mime,'bytes',p_bytes))
  on conflict(id) do nothing;
  select * into v_message from public.velvet_messages where id=p_id;
  if v_message.couple_id<>v_couple or v_message.sender_id<>v_user
    or v_message.kind<>'image' or v_message.payload->>'storage_path'<>p_storage_path
  then return jsonb_build_object('ok',false,'error','id_conflict'); end if;
  return jsonb_build_object('ok',true,'id',v_message.id,'duplicate',false);
end;
$$;

-- Extend delete-for-me/everyone to image messages. Editing remains text-only.
-- The old delete procedure locks the couple and sender independently.
create or replace function public.velvet_delete_text(p_id uuid,p_for_everyone boolean default false)
returns jsonb language plpgsql security definer set search_path=''
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
  select * into v_record from public.velvet_messages m where m.id=p_id
    and m.couple_id=v_couple and m.kind in ('text','image') for update;
  if not found then return jsonb_build_object('ok',false,'error','not_found'); end if;
  if coalesce(p_for_everyone,false) then
    if v_record.sender_id<>v_user then return jsonb_build_object('ok',false,'error','not_sender'); end if;
    if v_record.deleted_at is not null then return jsonb_build_object('ok',true,'already_deleted',true); end if;
    if v_record.created_at<now()-interval '48 hours' then
      return jsonb_build_object('ok',false,'error','delete_window_expired');
    end if;
    update public.velvet_messages set body=null,payload='{}'::jsonb,
      deleted_at=clock_timestamp() where id=p_id;
    insert into public.velvet_text_changes(couple_id,message_id,actor_id,action)
      values(v_couple,p_id,v_user,'deleted');
    return jsonb_build_object('ok',true,'for_everyone',true);
  end if;
  insert into public.velvet_hidden_messages(user_id,message_id)
    values(v_user,p_id) on conflict do nothing;
  if found then
    insert into public.velvet_text_changes(couple_id,message_id,actor_id,action)
      values(v_couple,p_id,v_user,'hidden');
  end if;
  return jsonb_build_object('ok',true,'for_everyone',false);
end;
$$;

-- The media RPC includes both types in chronological order. The older text-only
-- RPCs remain available to legacy clients; v0.13 uses the new ones.
create or replace function public.velvet_list_chat(
  p_after_at timestamptz default null,p_after_id uuid default null,
  p_limit integer default 80
) returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());v_couple uuid;v_items jsonb;
  v_limit integer:=least(greatest(coalesce(p_limit,80),1),100);
begin
  if v_user is null then raise exception 'Sign in first' using errcode='42501'; end if;
  if (p_after_at is null)<>(p_after_id is null) then
    raise exception 'Cursor fields must be provided together' using errcode='22023';
  end if;
  select couple_id into v_couple from public.velvet_members where user_id=v_user;
  if v_couple is null then return '[]'::jsonb; end if;
  if (select count(*) from public.velvet_members where couple_id=v_couple)<>2 then
    return '[]'::jsonb;
  end if;
  select coalesce(jsonb_agg(to_jsonb(t) order by t.created_at,t.id),'[]'::jsonb)
    into v_items from (
      select m.id,m.sender_id,m.kind,
        case when m.deleted_at is null then m.body else null end as body,
        case when m.deleted_at is null then m.payload else '{}'::jsonb end as payload,
        m.reply_to,m.created_at,m.edited_at,
        (m.deleted_at is not null) as deleted
      from public.velvet_messages m
      where m.couple_id=v_couple and m.kind in ('text','image')
        and not exists(select 1 from public.velvet_hidden_messages h
          where h.user_id=v_user and h.message_id=m.id)
        and (p_after_at is null or (m.created_at,m.id)>(p_after_at,p_after_id))
      order by
        case when p_after_at is null then m.created_at end desc,
        case when p_after_at is null then m.id end desc,
        case when p_after_at is not null then m.created_at end asc,
        case when p_after_at is not null then m.id end asc
      limit v_limit
    ) t;
  return v_items;
end;
$$;

-- Unlike the initial snapshot, an incremental page must read the OLDEST
-- messages after the cursor, so that >80 new items cannot skip messages.
create or replace function public.velvet_page_chat(
  p_before_at timestamptz default null,p_before_id uuid default null,
  p_limit integer default 80
) returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());v_couple uuid;v_items jsonb;
  v_limit integer:=least(greatest(coalesce(p_limit,80),1),100);
begin
  if v_user is null then raise exception 'Sign in first' using errcode='42501'; end if;
  if (p_before_at is null)<>(p_before_id is null) then
    raise exception 'Cursor fields must be provided together' using errcode='22023';
  end if;
  select couple_id into v_couple from public.velvet_members where user_id=v_user;
  if v_couple is null then return '[]'::jsonb; end if;
  if (select count(*) from public.velvet_members where couple_id=v_couple)<>2 then
    return '[]'::jsonb;
  end if;
  select coalesce(jsonb_agg(to_jsonb(t) order by t.created_at,t.id),'[]'::jsonb)
    into v_items from (
      select m.id,m.sender_id,m.kind,
        case when m.deleted_at is null then m.body else null end as body,
        case when m.deleted_at is null then m.payload else '{}'::jsonb end as payload,
        m.reply_to,m.created_at,m.edited_at,
        (m.deleted_at is not null) as deleted
      from public.velvet_messages m
      where m.couple_id=v_couple and m.kind in ('text','image')
        and not exists(select 1 from public.velvet_hidden_messages h
          where h.user_id=v_user and h.message_id=m.id)
        and (p_before_at is null or (m.created_at,m.id)<(p_before_at,p_before_id))
      order by m.created_at desc,m.id desc limit v_limit
    ) t;
  return v_items;
end;
$$;

revoke execute on function public.velvet_send_image(uuid,text,text,bigint,text,uuid) from public,anon,authenticated;
revoke execute on function public.velvet_list_chat(timestamptz,uuid,integer) from public,anon,authenticated;
revoke execute on function public.velvet_page_chat(timestamptz,uuid,integer) from public,anon,authenticated;
grant execute on function public.velvet_send_image(uuid,text,text,bigint,text,uuid) to authenticated;
grant execute on function public.velvet_list_chat(timestamptz,uuid,integer) to authenticated;
grant execute on function public.velvet_page_chat(timestamptz,uuid,integer) to authenticated;
