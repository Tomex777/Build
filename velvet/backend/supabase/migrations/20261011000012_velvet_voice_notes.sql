-- Connected AAC voice notes; separate from curated Our Story media.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('velvet-chat-voice','velvet-chat-voice',false,5242880,array['audio/mp4'])
on conflict (id) do update set public=false,file_size_limit=5242880,
 allowed_mime_types=excluded.allowed_mime_types;

create table if not exists public.velvet_voice_claims(
 storage_path text primary key,
 message_id uuid not null unique,
 couple_id uuid not null references public.velvet_couples(id) on delete cascade,
 owner_id uuid not null references auth.users(id) on delete cascade,
 claimed_at timestamptz not null default now()
);
alter table public.velvet_voice_claims enable row level security;
revoke all on public.velvet_voice_claims from public,anon,authenticated;

-- Storage policy helpers have no caller-chosen identity; authenticated only.
create or replace function public.velvet_voice_upload_allowed(p_name text)
returns boolean language sql stable security definer set search_path=''
as $$
 select (select auth.uid()) is not null
  and p_name ~* '^[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\.m4a$'
  and split_part(p_name,'/',2)=(select auth.uid())::text
  and exists(
    select 1 from public.velvet_members mine
    where mine.user_id=(select auth.uid())
      and mine.couple_id::text=split_part(p_name,'/',1)
      and (select count(*) from public.velvet_members two
        where two.couple_id=mine.couple_id)=2
  );
$$;
revoke execute on function public.velvet_voice_upload_allowed(text)
 from public,anon,authenticated;
grant execute on function public.velvet_voice_upload_allowed(text) to authenticated;

create or replace function public.velvet_voice_is_visible(p_name text)
returns boolean language sql stable security definer set search_path=''
as $$
 select (select auth.uid()) is not null and exists(
   select 1 from public.velvet_members mine
   join public.velvet_messages m on m.couple_id=mine.couple_id
   where mine.user_id=(select auth.uid())
    and m.kind='voice' and m.deleted_at is null
    and m.payload->>'storage_path'=p_name
    and (select count(*) from public.velvet_members two
      where two.couple_id=mine.couple_id)=2
    and not exists(select 1 from public.velvet_hidden_messages h
      where h.user_id=mine.user_id and h.message_id=m.id)
 );
$$;
revoke execute on function public.velvet_voice_is_visible(text)
 from public,anon,authenticated;
grant execute on function public.velvet_voice_is_visible(text) to authenticated;

create policy velvet_voice_upload on storage.objects for insert to authenticated
with check(bucket_id='velvet-chat-voice'
 and public.velvet_voice_upload_allowed(name));
create policy velvet_voice_download on storage.objects for select to authenticated
using(bucket_id='velvet-chat-voice' and public.velvet_voice_is_visible(name));
-- Intentionally no client update/delete policy. Trusted cleanup is future work.
