-- Run AFTER pairing.sql and messages.sql.
set role authenticated;
set request.jwt.claims = '{"is_anonymous":false}';

do $$
declare
  newest jsonb; older jsonb; first_id uuid; first_at timestamptz;
  a_first uuid; second_page jsonb; i int;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
  newest:=public.velvet_page_text(null,null,10);
  if jsonb_array_length(newest)<>10 then
    raise exception 'Expected ten recent messages: %',newest;
  end if;
  -- A page must be chronological, although selected newest-first internally.
  for i in 0..8 loop
    if (newest->i->>'created_at')::timestamptz >
        (newest->(i+1)->>'created_at')::timestamptz then
      raise exception 'History page is not chronological';
    end if;
  end loop;
  first_id := (newest->0->>'id')::uuid;
  first_at := (newest->0->>'created_at')::timestamptz;
  older:=public.velvet_page_text(first_at,first_id,10);
  if jsonb_array_length(older)<>10 then
    raise exception 'Older pagination failed: %',older;
  end if;
  if older->9->>'id'=newest->0->>'id' then
    raise exception 'Cursor duplicated a boundary message';
  end if;
  if (older->9->>'created_at')::timestamptz > first_at then
    raise exception 'History returned a newer message';
  end if;
  second_page := public.velvet_page_text(null,null,500);
  if jsonb_array_length(second_page)>100 then
    raise exception 'Page size upper bound ignored';
  end if;
  second_page:=public.velvet_page_text(first_at,null,10);
  raise exception 'Cursor without id unexpectedly accepted';
exception when sqlstate '22023' then
  null;
end $$;

do $$
declare rows jsonb;
begin
  perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
  rows:=public.velvet_page_text();
  if jsonb_array_length(rows)<>0 then raise exception 'Outsider can page through private messages'; end if;
end $$;
reset role;

set role anon;
do $$
declare denied boolean := false;
begin
  begin
    perform public.velvet_page_text();
  exception when insufficient_privilege then denied:=true;
  end;
  if not denied then raise exception 'Anon can read paginated messages'; end if;
end $$;
reset role;
select 'Velvet history page checks passed' as status;
