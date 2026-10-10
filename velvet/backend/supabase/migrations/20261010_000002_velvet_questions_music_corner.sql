-- Velvet's next data contract: questions, music dedications, and a couples' corner.
-- DESIGN ONLY: this migration is not applied to any user's Supabase project.
-- Requires 20261009_000001_velvet_core.sql first.
-- Only trusted server code may create pairings or validate online game moves.

create table if not exists public.velvet_question_catalog (
  id text primary key,
  category text not null,
  question text not null,
  intent_key text not null unique,
  revision integer not null default 1,
  approved boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists velvet_question_approved_category on public.velvet_question_catalog(category) where approved;
alter table public.velvet_question_catalog enable row level security;
create policy "velvet_questions_read_published" on public.velvet_question_catalog
  for select to authenticated using(approved);
-- No client insert/update/delete: offline packs are checked and published by trusted tools.

create table if not exists public.velvet_question_progress (
  user_id uuid not null references auth.users(id) on delete cascade,
  question_id text not null,
  seen_at timestamptz,
  saved_at timestamptz,
  answered_at timestamptz,
  primary key (user_id, question_id)
);
alter table public.velvet_question_progress enable row level security;
create policy "velvet_progress_read_own" on public.velvet_question_progress for select
  to authenticated using(user_id=(select auth.uid()));
create policy "velvet_progress_insert_own" on public.velvet_question_progress for insert
  to authenticated with check(user_id=(select auth.uid()));
create policy "velvet_progress_update_own" on public.velvet_question_progress for update
  to authenticated using(user_id=(select auth.uid())) with check(user_id=(select auth.uid()));

create table if not exists public.velvet_custom_decks (
  id uuid primary key default gen_random_uuid(),
  couple_id uuid not null references public.velvet_couples(id) on delete cascade,
  created_by uuid not null references auth.users(id) on delete cascade,
  title text not null,
  questions jsonb not null default '[]'::jsonb,
  shared boolean not null default false,
  created_at timestamptz not null default now(),
  check(jsonb_typeof(questions)='array'),
  check(length(title) between 1 and 100)
);
alter table public.velvet_custom_decks enable row level security;
create policy "velvet_decks_read" on public.velvet_custom_decks for select
  to authenticated using(public.velvet_is_member(couple_id) and (shared or created_by=(select auth.uid())));
create policy "velvet_decks_create_own" on public.velvet_custom_decks for insert
  to authenticated with check(public.velvet_is_member(couple_id) and created_by=(select auth.uid()));
create policy "velvet_decks_edit_own" on public.velvet_custom_decks for update
  to authenticated using(public.velvet_is_member(couple_id) and created_by=(select auth.uid()))
  with check(public.velvet_is_member(couple_id) and created_by=(select auth.uid()));

-- We store Spotify LINKS and dedications, not Spotify's music files or third-party tokens.
create table if not exists public.velvet_music_dedications (
  id uuid primary key default gen_random_uuid(),
  couple_id uuid not null references public.velvet_couples(id) on delete cascade,
  created_by uuid not null references auth.users(id) on delete cascade,
  title text not null check(length(title) between 1 and 160),
  artist text not null check(length(artist) between 1 and 160),
  spotify_url text not null check(spotify_url ~ '^https://(open[.]spotify[.]com|spotify[.]link)/'),
  note text check(length(note)<=600),
  created_at timestamptz not null default now()
);
create index if not exists velvet_music_couple_recent
  on public.velvet_music_dedications(couple_id,created_at desc);
alter table public.velvet_music_dedications enable row level security;
create policy "velvet_music_read_pair" on public.velvet_music_dedications for select
  to authenticated using(public.velvet_is_member(couple_id));
create policy "velvet_music_create_own" on public.velvet_music_dedications for insert
  to authenticated with check(public.velvet_is_member(couple_id) and created_by=(select auth.uid()));
create policy "velvet_music_remove_own" on public.velvet_music_dedications for delete
  to authenticated using(public.velvet_is_member(couple_id) and created_by=(select auth.uid()));

create table if not exists public.velvet_jam_invites (
  couple_id uuid primary key references public.velvet_couples(id) on delete cascade,
  created_by uuid not null references auth.users(id),
  spotify_url text not null check(spotify_url ~ '^https://(open[.]spotify[.]com|spotify[.]link)/'),
  updated_at timestamptz not null default now()
);
alter table public.velvet_jam_invites enable row level security;
create policy "velvet_jam_read_pair" on public.velvet_jam_invites for select
  to authenticated using(public.velvet_is_member(couple_id));
-- Creating/updating shared invitations goes through a trusted membership-checked endpoint.

create table if not exists public.velvet_shared_corner (
  couple_id uuid primary key references public.velvet_couples(id) on delete cascade,
  note text check(length(note)<=500),
  background_media_id uuid references public.velvet_media(id),
  last_edited_by uuid references auth.users(id),
  updated_at timestamptz not null default now()
);
alter table public.velvet_shared_corner enable row level security;
create policy "velvet_corner_read_pair" on public.velvet_shared_corner for select
  to authenticated using(public.velvet_is_member(couple_id));
-- No client mutation policy until the partner-confirmation model is implemented.

-- SECURITY TODO before production:
-- limit and validate JSON deck sizes server-side, enforce unique semantic intent ids
-- during publication, audit SECURITY DEFINER helpers, validate shared edit ownership,
-- and restrict delete requests. No service-role/Azure secrets may be embedded in apps.
