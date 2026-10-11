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
