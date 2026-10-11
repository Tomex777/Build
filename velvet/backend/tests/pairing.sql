-- Assumes the core + question + pairing migrations have been applied.
-- Fail the CI job on any assertion; all calls are made as the app-facing role.
grant select on public.velvet_couples,public.velvet_members,public.velvet_profiles
  to authenticated;
grant insert on public.velvet_members to authenticated;
set role authenticated;
create temporary table pairing_test(code text not null,couple_id uuid not null);
set request.jwt.claims = '{"is_anonymous":false}';

do $$
declare v jsonb; c text; pair uuid; p jsonb;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
  v := public.velvet_create_pair_invite();
  c := v->>'code';
  pair := (v->>'couple_id')::uuid;
  if c !~ '^[A-F0-9]{4}(-[A-F0-9]{4}){4}$' then
    raise exception 'Invite must be a formatted, 80-bit token: %',c;
  end if;
  if (select count(*) from public.velvet_members where couple_id=pair)<>1 then
    raise exception 'Creating an invite must enroll only the creator';
  end if;
  insert into pairing_test values(c,pair);
  p := public.velvet_my_pair();
  if (p->>'member_count')::integer<>1 then raise exception 'Incorrect single-member state'; end if;
  if (public.velvet_join_pair_invite(c)->>'ok')::boolean is true then
    raise exception 'Inviter cannot redeem their own token';
  end if;
end $$;

do $$
declare invite text; pair uuid; v jsonb;
begin
  select code,couple_id into invite,pair from pairing_test;
  perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
  v := public.velvet_join_pair_invite(invite);
  if v->>'ok' <> 'true' or (v->>'couple_id')::uuid<>pair then
    raise exception 'Invite redemption failed: %',v;
  end if;
  if (public.velvet_my_pair()->>'member_count')::int <>2 then
    raise exception 'Second member cannot view their paired space';
  end if;
end $$;

do $$
declare invite text; pair uuid; v jsonb; raised_error boolean := false;
begin
  select code,couple_id into invite,pair from pairing_test;
  perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
  v := public.velvet_join_pair_invite(invite);
  if v->>'error'<>'invalid_or_expired' then raise exception 'Replay was accepted'; end if;
  if public.velvet_my_pair() is not null then raise exception 'Unpaired account saw a pair'; end if;
  if exists(select 1 from public.velvet_couples where id=pair) then
    raise exception 'Outsider can read another couple through RLS';
  end if;
  begin
    insert into public.velvet_members(couple_id,user_id)
      values(pair,'cccccccc-cccc-4ccc-8ccc-cccccccccccc');
  exception when others then raised_error:=true;
  end;
  if not raised_error then raise exception 'Direct membership modification was accepted'; end if;
  for i in 1..11 loop
    v := public.velvet_join_pair_invite('INVALID');
  end loop;
  if v->>'error'<>'rate_limited' then raise exception 'Rate limit did not engage: %',v; end if;
end $$;

do $$
declare blocked boolean := false;
begin
  perform set_config('request.jwt.claim.sub','dddddddd-dddd-4ddd-8ddd-dddddddddddd',false);
  begin
    perform public.velvet_create_pair_invite();
  exception when others then blocked:=true;
  end;
  if not blocked then raise exception 'Unverified account created an invitation'; end if;
end $$;

do $$
declare blocked boolean := false;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
  begin
    perform public.velvet_create_pair_invite();
  exception when others then blocked:=true;
  end;
  if not blocked then raise exception 'Paired account generated a third invitation'; end if;
end $$;

reset role;
-- ANON cannot execute the intentionally elevated account or pairing endpoints.
set role anon;
do $$
declare blocked boolean := false;
begin
  begin
    perform public.velvet_my_pair();
  exception when insufficient_privilege then blocked:=true;
  end;
  if not blocked then raise exception 'Anonymous role could execute private pairing RPC'; end if;
end $$;
reset role;
select 'Velvet pairing tests passed' as status;
