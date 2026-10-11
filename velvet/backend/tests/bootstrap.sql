-- Minimal Supabase Auth stubs for isolated PostgreSQL migration tests.
-- NEVER deploy this test fixture to a real Supabase project.
create role anon nologin;
create role authenticated nologin;
create schema auth;
create table auth.users (
  id uuid primary key,
  email_confirmed_at timestamptz,
  phone_confirmed_at timestamptz
);
create function auth.uid() returns uuid language sql stable
  as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
create function auth.jwt() returns jsonb language sql stable
  as $$ select coalesce(nullif(current_setting('request.jwt.claims',true),'')::jsonb,'{}'::jsonb) $$;
grant usage on schema auth,public to authenticated,anon;
grant execute on function auth.uid(),auth.jwt() to authenticated,anon;
grant temporary on database postgres to authenticated;
insert into auth.users(id,email_confirmed_at) values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',now()),
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',now()),
  ('cccccccc-cccc-4ccc-8ccc-cccccccccccc',now()),
  ('dddddddd-dddd-4ddd-8ddd-dddddddddddd',null);

-- PostgreSQL-only storage API metadata stand-in, NOT a production migration.
-- Supabase creates and owns these real objects; CI only checks our policies/RPCs.
create schema storage;
create table storage.buckets (
  id text primary key,name text not null,public boolean not null default false,
  file_size_limit bigint,allowed_mime_types text[]
);
create table storage.objects (
  id uuid primary key default gen_random_uuid(),
  bucket_id text not null references storage.buckets(id),
  name text not null,
  owner_id text,
  unique(bucket_id,name)
);
create function storage.foldername(path text) returns text[]
language sql immutable as $$
  select string_to_array(
    case when position('/' in path)>0
      then left(path,length(path)-length(split_part(path,'/',array_length(string_to_array(path,'/'),1)))-1)
      else '' end,'/')
$$;
alter table storage.objects enable row level security;
grant usage on schema storage to authenticated,anon;
grant select,insert on storage.objects to authenticated;
