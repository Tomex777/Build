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
