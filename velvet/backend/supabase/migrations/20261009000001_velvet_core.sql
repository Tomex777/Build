-- Velvet's initial relational data contract; not yet deployed to Supabase.
-- Only backend functions with elevated, protected credentials can enroll/invite members.
create extension if not exists pgcrypto;

create table if not exists public.velvet_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '',
  pronouns text,
  avatar_blob_key text,
  updated_at timestamptz not null default now()
);

create table if not exists public.velvet_couples (
  id uuid primary key default gen_random_uuid(),
  created_by uuid not null references auth.users(id),
  anniversary date,
  created_at timestamptz not null default now()
);

create table if not exists public.velvet_members (
  couple_id uuid not null references public.velvet_couples(id) on delete cascade,
  user_id uuid not null unique references auth.users(id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (couple_id, user_id)
);

-- Lock the parent row before checking capacity to avoid parallel joins overfilling a couple.
create or replace function public.velvet_enforce_pair_capacity() returns trigger
language plpgsql set search_path = public as $$
begin
  perform 1 from public.velvet_couples where id=new.couple_id for update;
  if (select count(*) from public.velvet_members where couple_id=new.couple_id)>=2 then
    raise exception 'This Velvet space already has two members';
  end if;
  return new;
end $$;
drop trigger if exists velvet_pair_capacity on public.velvet_members;
create trigger velvet_pair_capacity before insert on public.velvet_members
for each row execute function public.velvet_enforce_pair_capacity();

-- Security-definer membership checks avoid RLS recursion through velvet_members.
-- Never expose arbitrary parameters as a privilege escalation path; both helpers only return booleans.
create or replace function public.velvet_is_member(p_couple uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.velvet_members m
    where m.couple_id = p_couple and m.user_id = (select auth.uid()))
$$;

create or replace function public.velvet_shares_couple(p_user uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.velvet_members mine
    join public.velvet_members theirs on mine.couple_id=theirs.couple_id
    where mine.user_id=(select auth.uid()) and theirs.user_id=p_user)
$$;

create table if not exists public.velvet_messages (
  id uuid primary key default gen_random_uuid(),
  couple_id uuid not null references public.velvet_couples(id) on delete cascade,
  sender_id uuid not null references auth.users(id),
  kind text not null check(kind in ('text','question_card','voice','image','video','document','game_invite')),
  body text,
  payload jsonb not null default '{}'::jsonb,
  reply_to uuid references public.velvet_messages(id),
  pinned_at timestamptz,
  edited_at timestamptz,
  deleted_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists velvet_messages_chronological on public.velvet_messages(couple_id,created_at,id);

create table if not exists public.velvet_stars (
  user_id uuid not null references auth.users(id) on delete cascade,
  message_id uuid not null references public.velvet_messages(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(user_id,message_id)
);
create table if not exists public.velvet_hidden_messages (
  user_id uuid not null references auth.users(id) on delete cascade,
  message_id uuid not null references public.velvet_messages(id) on delete cascade,
  primary key(user_id,message_id)
);

-- The blob key is a private Azure object reference, never a publicly readable URL or a secret.
create table if not exists public.velvet_media (
  id uuid primary key default gen_random_uuid(),
  couple_id uuid not null references public.velvet_couples(id) on delete cascade,
  uploaded_by uuid not null references auth.users(id),
  azure_blob_key text not null unique,
  mime_type text not null,
  bytes bigint not null check(bytes>=0),
  created_at timestamptz not null default now()
);
create table if not exists public.velvet_message_media (
  message_id uuid not null references public.velvet_messages(id) on delete cascade,
  media_id uuid not null references public.velvet_media(id) on delete restrict,
  primary key(message_id,media_id)
);
create table if not exists public.velvet_story_entries (
  id uuid primary key default gen_random_uuid(),
  couple_id uuid not null references public.velvet_couples(id) on delete cascade,
  media_id uuid not null references public.velvet_media(id),
  added_by uuid not null references auth.users(id),
  caption text,
  added_at timestamptz not null default now()
);

create table if not exists public.velvet_game_sessions (
  id uuid primary key default gen_random_uuid(),
  couple_id uuid not null references public.velvet_couples(id) on delete cascade,
  game_type text not null,
  game_state jsonb not null default '{}'::jsonb,
  current_turn uuid references auth.users(id),
  version integer not null default 0,
  updated_at timestamptz not null default now()
);

alter table public.velvet_profiles enable row level security;
alter table public.velvet_couples enable row level security;
alter table public.velvet_members enable row level security;
alter table public.velvet_messages enable row level security;
alter table public.velvet_stars enable row level security;
alter table public.velvet_hidden_messages enable row level security;
alter table public.velvet_media enable row level security;
alter table public.velvet_message_media enable row level security;
alter table public.velvet_story_entries enable row level security;
alter table public.velvet_game_sessions enable row level security;

create policy "velvet_profiles_read_pair" on public.velvet_profiles for select to authenticated
using(user_id=(select auth.uid()) or public.velvet_shares_couple(user_id));
create policy "velvet_profiles_write_self" on public.velvet_profiles for insert to authenticated with check(user_id=(select auth.uid()));
create policy "velvet_profiles_edit_self" on public.velvet_profiles for update to authenticated
using(user_id=(select auth.uid())) with check(user_id=(select auth.uid()));

create policy "velvet_couples_view" on public.velvet_couples for select to authenticated
using(public.velvet_is_member(id));
create policy "velvet_members_view" on public.velvet_members for select to authenticated
using(public.velvet_is_member(couple_id));
-- No client insert/update/delete policies on member lists: pairing must go through a trusted function.

create policy "velvet_messages_view" on public.velvet_messages for select to authenticated
using(public.velvet_is_member(couple_id));
create policy "velvet_messages_send" on public.velvet_messages for insert to authenticated
with check(sender_id=(select auth.uid()) and public.velvet_is_member(couple_id));
create policy "velvet_messages_edit_own" on public.velvet_messages for update to authenticated
using(sender_id=(select auth.uid()) and public.velvet_is_member(couple_id))
with check(sender_id=(select auth.uid()) and public.velvet_is_member(couple_id));

create policy "velvet_stars_view" on public.velvet_stars for select to authenticated
using(user_id=(select auth.uid()));
create policy "velvet_stars_insert" on public.velvet_stars for insert to authenticated
with check(user_id=(select auth.uid()) and exists(select 1 from public.velvet_messages msg
  where msg.id=message_id and public.velvet_is_member(msg.couple_id)));
create policy "velvet_stars_remove" on public.velvet_stars for delete to authenticated
using(user_id=(select auth.uid()));
create policy "velvet_hides_view" on public.velvet_hidden_messages for select to authenticated
using(user_id=(select auth.uid()));
create policy "velvet_hides_insert" on public.velvet_hidden_messages for insert to authenticated
with check(user_id=(select auth.uid()) and exists(select 1 from public.velvet_messages msg
  where msg.id=message_id and public.velvet_is_member(msg.couple_id)));

create policy "velvet_media_view" on public.velvet_media for select to authenticated
using(public.velvet_is_member(couple_id));
create policy "velvet_media_insert" on public.velvet_media for insert to authenticated
with check(uploaded_by=(select auth.uid()) and public.velvet_is_member(couple_id));
create policy "velvet_message_media_view" on public.velvet_message_media for select to authenticated
using(exists(select 1 from public.velvet_messages msg where msg.id=message_id and public.velvet_is_member(msg.couple_id)));
create policy "velvet_story_view" on public.velvet_story_entries for select to authenticated
using(public.velvet_is_member(couple_id));
create policy "velvet_story_insert" on public.velvet_story_entries for insert to authenticated
with check(added_by=(select auth.uid()) and public.velvet_is_member(couple_id)
  and exists(select 1 from public.velvet_media asset where asset.id=media_id and asset.couple_id=velvet_story_entries.couple_id));
create policy "velvet_game_view" on public.velvet_game_sessions for select to authenticated
using(public.velvet_is_member(couple_id));
-- Game moves, pins and shared anniversary updates require trusted mutation endpoints
-- with ownership checks and, for games, a transactional version + rules validator.
