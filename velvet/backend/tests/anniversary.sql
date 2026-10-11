-- Run after paired members have completed the profile checks.
set role authenticated;
set request.jwt.claims = '{"is_anonymous":false}';

do $$
declare v jsonb; blocked boolean:=false;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
  v:=public.velvet_set_anniversary('2024-02-29');
  if v->>'ok'<>'true' or v->>'anniversary'<>'2024-02-29' then
    raise exception 'Creator cannot set our valid leap-day date: %',v;
  end if;
  v:=public.velvet_set_anniversary(current_date+1);
  if v->>'error'<>'invalid_date' then raise exception 'Future start accepted: %',v; end if;
  v:=public.velvet_set_anniversary(null);
  if v->>'error'<>'invalid_date' then raise exception 'Null start accepted: %',v; end if;

  begin
    update public.velvet_couples set anniversary='2000-01-01';
  exception when insufficient_privilege then blocked:=true;
  end;
  if not blocked then raise exception 'Direct couple update still allowed'; end if;
end $$;

do $$
declare v jsonb;
begin
  perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
  v:=public.velvet_get_anniversary();
  if v->>'anniversary'<>'2024-02-29' then
    raise exception 'Partner cannot read their couple date: %',v;
  end if;
  v:=public.velvet_set_anniversary('2024-10-01');
  if v->>'ok'<>'true' then raise exception 'Partner date update rejected: %',v; end if;
end $$;

do $$
declare v jsonb;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
  v:=public.velvet_get_anniversary();
  if v->>'anniversary'<>'2024-10-01'
    or v->>'edited_by'<>'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' then
    raise exception 'Creator cannot see latest date and editor: %',v;
  end if;
end $$;

do $$
declare v jsonb;
begin
  perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
  v:=public.velvet_get_anniversary();
  if v is not null then raise exception 'Unpaired user saw a shared date'; end if;
  v:=public.velvet_set_anniversary('2024-10-01');
  if v->>'error'<>'not_paired' then raise exception 'Outsider changed another date: %',v; end if;
end $$;

do $$
declare blocked boolean:=false;
begin
  perform set_config('request.jwt.claim.sub','dddddddd-dddd-4ddd-8ddd-dddddddddddd',false);
  begin
    perform public.velvet_set_anniversary('2024-10-01');
  exception when insufficient_privilege then blocked:=true;
  end;
  if not blocked then raise exception 'Unverified member could edit date'; end if;
end $$;

reset role;
set role anon;
do $$
declare blocked boolean:=false;
begin
  begin
    perform public.velvet_get_anniversary();
  exception when insufficient_privilege then blocked:=true;
  end;
  if not blocked then raise exception 'Anonymous user can read couple date'; end if;
end $$;
reset role;
select 'Velvet shared anniversary checks passed' as status;
