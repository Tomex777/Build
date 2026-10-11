-- Runs AFTER pairing.sql, messages.sql and history.sql.
set role authenticated;
set request.jwt.claims = '{"is_anonymous":false}';

do $$
declare v jsonb; p jsonb; blocked boolean:=false;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
  v:=public.velvet_update_my_profile('  Alex  ',' she/her ');
  if v->>'ok'<>'true' or v->>'display_name'<>'Alex' or v->>'pronouns'<>'she/her' then
    raise exception 'Own profile was not normalized: %',v;
  end if;
  p:=public.velvet_get_my_profile();
  if p->>'display_name'<>'Alex' then raise exception 'Own profile read failed: %',p; end if;
  v:=public.velvet_update_my_profile('',null);
  if v->>'error'<>'invalid_name' then raise exception 'Empty name accepted: %',v; end if;
  v:=public.velvet_update_my_profile('Alex',repeat('x',33));
  if v->>'error'<>'invalid_pronouns' then raise exception 'Overlong pronouns accepted: %',v; end if;

  begin
    update public.velvet_profiles set display_name='HACKED'
      where user_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  exception when insufficient_privilege then blocked:=true;
  end;
  if not blocked then raise exception 'Direct profile updates remain allowed'; end if;
end $$;

do $$
declare v jsonb; p jsonb;
begin
  perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
  v:=public.velvet_update_my_profile('Robin','they/them');
  if v->>'ok'<>'true' then raise exception 'Partner profile failed: %',v; end if;
  p:=public.velvet_my_pair();
  if p->>'partner_name'<>'Alex' or p->>'partner_pronouns'<>'she/her'
      or p->>'own_name'<>'Robin' or p->>'own_pronouns'<>'they/them' then
    raise exception 'Own and partner profiles improperly mapped: %',p;
  end if;
end $$;

do $$
declare v jsonb; p jsonb;
begin
  perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
  p:=public.velvet_my_pair();
  if p is not null then raise exception 'Unpaired account saw another profile: %',p; end if;
  v:=public.velvet_get_my_profile();
  if v->>'display_name'<>''
     then raise exception 'Unpaired account saw another user name: %',v; end if;
  v:=public.velvet_update_my_profile('Casey',null);
  if v->>'ok'<>'true' then raise exception 'Unpaired user could not edit their own profile'; end if;
end $$;

do $$
declare denied boolean:=false;
begin
  perform set_config('request.jwt.claim.sub','dddddddd-dddd-4ddd-8ddd-dddddddddddd',false);
  begin
    perform public.velvet_update_my_profile('Unverified',null);
  exception when insufficient_privilege then denied:=true;
  end;
  if not denied then raise exception 'Unverified user changed profile'; end if;
end $$;

do $$
declare p jsonb;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
  p:=public.velvet_my_pair();
  if p->>'partner_name'<>'Robin' or p->>'own_name'<>'Alex'
      then raise exception 'User A cannot see correctly scoped profiles: %',p; end if;
end $$;
reset role;

set role anon;
do $$
declare denied boolean:=false;
begin
  begin
    perform public.velvet_update_my_profile('Anon',null);
  exception when insufficient_privilege then denied:=true;
  end;
  if not denied then raise exception 'Anonymous client edited a profile'; end if;
end $$;
reset role;
select 'Velvet profile security tests passed' as status;
