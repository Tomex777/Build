-- v0.18: Private, scoped MP4 video attachments for connected Chat.
-- Video files are never copied to Our Story without explicit user intent.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('velvet-chat-video','velvet-chat-video',false,20971520,array['video/mp4'])
on conflict(id) do update set public=false,file_size_limit=20971520,
 allowed_mime_types=excluded.allowed_mime_types;

create table if not exists public.velvet_video_claims(
 storage_path text primary key,
 message_id uuid not null unique,
 couple_id uuid not null references public.velvet_couples(id) on delete cascade,
 owner_id uuid not null references auth.users(id) on delete cascade,
 claimed_at timestamptz not null default now()
);
alter table public.velvet_video_claims enable row level security;
revoke all on public.velvet_video_claims from public,anon,authenticated;

create or replace function public.velvet_video_upload_allowed(p_name text)
returns boolean language sql stable security definer set search_path=''
as $$
 select (select auth.uid()) is not null
 and p_name ~* '^[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\.mp4$'
 and split_part(p_name,'/',2)=(select auth.uid())::text
 and exists(
   select 1 from public.velvet_members mine
   where mine.user_id=(select auth.uid())
     and mine.couple_id::text=split_part(p_name,'/',1)
     and (select count(*) from public.velvet_members members
       where members.couple_id=mine.couple_id)=2
 );
$$;
revoke execute on function public.velvet_video_upload_allowed(text)
 from public,anon,authenticated;
grant execute on function public.velvet_video_upload_allowed(text) to authenticated;

create or replace function public.velvet_video_is_visible(p_name text)
returns boolean language sql stable security definer set search_path=''
as $$
 select (select auth.uid()) is not null and exists(
   select 1 from public.velvet_members mine
     join public.velvet_messages m on m.couple_id=mine.couple_id
   where mine.user_id=(select auth.uid())
     and m.kind='video' and m.deleted_at is null
     and m.payload->>'storage_path'=p_name
     and (select count(*) from public.velvet_members members
       where members.couple_id=mine.couple_id)=2
     and not exists(select 1 from public.velvet_hidden_messages hidden
       where hidden.user_id=mine.user_id and hidden.message_id=m.id)
 );
$$;
revoke execute on function public.velvet_video_is_visible(text)
 from public,anon,authenticated;
grant execute on function public.velvet_video_is_visible(text) to authenticated;

create policy velvet_video_upload on storage.objects for insert to authenticated
with check(bucket_id='velvet-chat-video'
 and public.velvet_video_upload_allowed(name));
create policy velvet_video_download on storage.objects for select to authenticated
using(bucket_id='velvet-chat-video' and public.velvet_video_is_visible(name));
-- No client Storage update/delete. Trusted orphan cleanup is a separate feature.

-- Bind a pre-uploaded private MP4 file to exactly one chat message.
create or replace function public.velvet_send_video(
 p_id uuid,p_storage_path text,p_bytes bigint,p_duration_ms integer,
 p_caption text default null,p_reply_to uuid default null
) returns jsonb language plpgsql security definer set search_path=''
as $$
declare
 v_user uuid := (select auth.uid());
 v_couple uuid;
 v_caption text := btrim(coalesce(p_caption,''));
 v_prev public.velvet_messages%rowtype;
 v_attempts integer;
begin
 if v_user is null or coalesce((select auth.jwt()->>'is_anonymous'),'false')='true' then
   raise exception 'Sign in to send videos' using errcode='42501';
 end if;
 if p_id is null or p_bytes is null or p_bytes not between 1 and 20971520
  or p_duration_ms is null or p_duration_ms not between 100 and 120000
  or char_length(v_caption)>1200
  or p_storage_path is null
  or p_storage_path !~* '^[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\.mp4$' then
  return jsonb_build_object('ok',false,'error','invalid_video');
 end if;
 select m.couple_id into v_couple from public.velvet_members m
 where m.user_id=v_user;
 if v_couple is null then return jsonb_build_object('ok',false,'error','not_paired');end if;
 if split_part(p_storage_path,'/',1)<>v_couple::text
   or split_part(p_storage_path,'/',2)<>v_user::text then
   return jsonb_build_object('ok',false,'error','wrong_owner');
 end if;
 perform 1 from public.velvet_couples c where c.id=v_couple for update;
 if (select count(*) from public.velvet_members m where m.couple_id=v_couple)<>2 then
   return jsonb_build_object('ok',false,'error','not_paired');
 end if;
 select * into v_prev from public.velvet_messages m where m.id=p_id;
 if found then
   if v_prev.couple_id=v_couple and v_prev.sender_id=v_user
    and v_prev.kind='video' and v_prev.deleted_at is null
    and v_prev.payload->>'storage_path'=p_storage_path
    and (v_prev.payload->>'bytes')::bigint=p_bytes
    and (v_prev.payload->>'duration_ms')::integer=p_duration_ms
    and v_prev.body is not distinct from nullif(v_caption,'')
    and v_prev.reply_to is not distinct from p_reply_to then
    return jsonb_build_object('ok',true,'id',p_id,'duplicate',true);
   end if;
   return jsonb_build_object('ok',false,'error','id_conflict');
 end if;
 if p_reply_to is not null and not exists(
   select 1 from public.velvet_messages m
   where m.id=p_reply_to and m.couple_id=v_couple and m.deleted_at is null
 ) then return jsonb_build_object('ok',false,'error','invalid_reply');end if;
 if not exists(select 1 from storage.objects o
   where o.bucket_id='velvet-chat-video'
     and o.name=p_storage_path and o.owner_id=v_user::text
 ) then return jsonb_build_object('ok',false,'error','file_not_uploaded');end if;
 insert into public.velvet_text_send_rates(user_id,window_start,attempts)
 values(v_user,now(),1)
 on conflict(user_id) do update set
   attempts=case when public.velvet_text_send_rates.window_start<now()-interval '1 minute'
     then 1 else public.velvet_text_send_rates.attempts+1 end,
   window_start=case when public.velvet_text_send_rates.window_start<now()-interval '1 minute'
     then now() else public.velvet_text_send_rates.window_start end
 returning attempts into v_attempts;
 if v_attempts>40 then return jsonb_build_object('ok',false,'error','rate_limited');end if;
 insert into public.velvet_video_claims(storage_path,message_id,couple_id,owner_id)
 values(p_storage_path,p_id,v_couple,v_user)
 on conflict do nothing;
 if not found then return jsonb_build_object('ok',false,'error','file_already_sent');end if;
 insert into public.velvet_messages(id,couple_id,sender_id,kind,body,reply_to,payload)
 values(p_id,v_couple,v_user,'video',nullif(v_caption,''),p_reply_to,
   jsonb_build_object('storage_path',p_storage_path,'mime','video/mp4',
      'bytes',p_bytes,'duration_ms',p_duration_ms));
 return jsonb_build_object('ok',true,'id',p_id,'duplicate',false);
end;
$$;

revoke execute on function public.velvet_send_video(uuid,text,bigint,integer,text,uuid)
 from public,anon,authenticated;
grant execute on function public.velvet_send_video(uuid,text,bigint,integer,text,uuid)
 to authenticated;
