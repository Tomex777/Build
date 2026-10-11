-- Dedicated scoped Chat Info gallery and conversation search regression tests.
-- This test runs after all message/image/forward-cursor tests.
-- Create 4 real-looking image messages via the trusted fixture owner only;
-- authenticated mobile roles cannot write these tables directly.
insert into public.velvet_messages(id,couple_id,sender_id,kind,body,payload,created_at)
select ('00000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,
    member.couple_id,member.user_id,'image',
    case n when 1 then 'Gallery rose FIRST'
           when 2 then 'Gallery second'
           when 3 then 'Gallery third'
           else 'Gallery FOURTH' end,
    jsonb_build_object('storage_path',
      member.couple_id::text||'/'||member.user_id::text||'/'
        ||('00000000-0000-4000-8000-'||lpad(n::text,12,'0'))||'.jpg',
      'mime','image/jpeg','bytes',100),
    now()-n*interval '1 hour'
from public.velvet_members member cross join generate_series(1,4) n
where member.user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';

insert into public.velvet_messages(id,couple_id,sender_id,kind,body,created_at)
select '00000000-0000-4000-9000-000000000101',member.couple_id,
 member.user_id,'text','This contains %vault% and An_underscore',now()-interval '12 minutes'
from public.velvet_members member
where member.user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
insert into public.velvet_messages(id,couple_id,sender_id,kind,body,created_at)
select '00000000-0000-4000-9000-000000000102',member.couple_id,
 member.user_id,'text','vault without literal percent symbols',now()-interval '11 minutes'
from public.velvet_members member
where member.user_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';

set role authenticated;
set request.jwt.claims='{"is_anonymous":false}';

do $$
declare gallery jsonb; results jsonb; first_at timestamptz; first_id uuid;
 next_at timestamptz; next_id uuid;
begin
 perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
 gallery:=public.velvet_chat_photos(null,null,2);
 if jsonb_array_length(gallery)<>2 then raise exception 'Gallery page not limited: %',gallery;end if;
 if gallery->0->>'id'<>'00000000-0000-4000-8000-000000000001'
    or gallery->1->>'id'<>'00000000-0000-4000-8000-000000000002' then
   raise exception 'Gallery photo sort failed: %',gallery;
 end if;
 if gallery->0->>'storage_path' not like '%/00000000-0000-4000-8000-000000000001.jpg' then
   raise exception 'Private media path unavailable: %',gallery;
 end if;
 first_at:=(gallery->1->>'created_at')::timestamptz;
 first_id:=(gallery->1->>'id')::uuid;
 gallery:=public.velvet_chat_photos(first_at,first_id,2);
 if jsonb_array_length(gallery)<>2
    or gallery->0->>'id'<>'00000000-0000-4000-8000-000000000003'
    or gallery->1->>'id'<>'00000000-0000-4000-8000-000000000004' then
   raise exception 'Gallery older cursor failed: %',gallery;
 end if;
 next_at:=(gallery->1->>'created_at')::timestamptz;
 next_id:=(gallery->1->>'id')::uuid;
 if public.velvet_chat_photos(next_at,next_id,2) <> '[]'::jsonb then
   raise exception 'Gallery cursor repeated older photos';
 end if;

 results:=public.velvet_search_chat('%vault%');
 if jsonb_array_length(results)<>1
    or results->0->>'id'<>'00000000-0000-4000-9000-000000000101' then
   raise exception 'Percent wildcards interpreted as SQL operators: %',results;
 end if;
 results:=public.velvet_search_chat('An_underscore');
 if jsonb_array_length(results)<>1
    or results->0->>'id'<>'00000000-0000-4000-9000-000000000101' then
   raise exception 'Underscore treated as wildcard: %',results;
 end if;
 results:=public.velvet_search_chat('gAlLeRy');
 if jsonb_array_length(results)<>4 then
   raise exception 'Case-insensitive caption search failed: %',results;
 end if;
 if public.velvet_search_chat('x') <> '[]'::jsonb then
   raise exception 'Search accepted one-character broad query';
 end if;
 if public.velvet_search_chat(repeat('x',65)) <> '[]'::jsonb then
   raise exception 'Search accepted oversized broad query';
 end if;
end $$;

do $$
declare gallery jsonb; found jsonb; v jsonb; invalid_cursor boolean:=false;
begin
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
 gallery:=public.velvet_chat_photos();
 if jsonb_array_length(gallery)<>4 then
   raise exception 'Partner cannot view couple photo gallery: %',gallery;end if;
 v:=public.velvet_delete_text('00000000-0000-4000-8000-000000000002',false);
 if v->>'ok'<>'true' then raise exception 'Personal media hide denied: %',v;end if;
 gallery:=public.velvet_chat_photos();
 if jsonb_array_length(gallery)<>3 or exists(
     select 1 from jsonb_array_elements(gallery) as row(value)
       where row.value->>'id'='00000000-0000-4000-8000-000000000002') then
   raise exception 'Personally hidden photo leaked into gallery: %',gallery;end if;
 found:=public.velvet_search_chat('Gallery second');
 if found<>'[]'::jsonb then
   raise exception 'Hidden photo caption exposed through search: %',found;end if;
 begin
   perform public.velvet_chat_photos(now(),null);
 exception when invalid_parameter_value then invalid_cursor:=true;end;
 if not invalid_cursor then raise exception 'Malformed gallery cursor was accepted';end if;
 invalid_cursor:=false;
 begin
   perform public.velvet_search_chat('Gallery',now(),null);
 exception when invalid_parameter_value then invalid_cursor:=true;end;
 if not invalid_cursor then raise exception 'Malformed search cursor was accepted';end if;
end $$;

do $$
declare gallery jsonb; found jsonb; v jsonb;
begin
 perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
 gallery:=public.velvet_chat_photos();
 if jsonb_array_length(gallery)<>4 then
   raise exception 'One partner hide changed the other partner gallery: %',gallery;end if;
 v:=public.velvet_delete_text('00000000-0000-4000-8000-000000000001',true);
 if v->>'ok'<>'true' then raise exception 'Owner could not delete photo for everyone: %',v;end if;
 gallery:=public.velvet_chat_photos();
 if jsonb_array_length(gallery)<>3
   or exists(select 1 from jsonb_array_elements(gallery) as row(value)
     where row.value->>'id'='00000000-0000-4000-8000-000000000001') then
   raise exception 'Deleted media leaked through gallery: %',gallery;end if;
 found:=public.velvet_search_chat('Gallery rose FIRST');
 if found<>'[]'::jsonb then
   raise exception 'Deleted photo caption leaked through search: %',found;end if;
end $$;

do $$
declare gallery jsonb; found jsonb;
begin
 perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
 gallery:=public.velvet_chat_photos();
 found:=public.velvet_search_chat('Gallery');
 if gallery<>'[]'::jsonb or found<>'[]'::jsonb then
   raise exception 'Unpaired account can read photo gallery or conversation';
 end if;
end $$;
reset role;

set role anon;
do $$
declare blocked boolean:=false;
begin
 begin
  perform public.velvet_chat_photos();
 exception when insufficient_privilege then blocked:=true;end;
 if not blocked then raise exception 'Anonymous gallery access granted';end if;
 blocked:=false;
 begin
  perform public.velvet_search_chat('Gallery');
 exception when insufficient_privilege then blocked:=true;end;
 if not blocked then raise exception 'Anonymous conversation search granted';end if;
end $$;
reset role;
select 'Velvet Chat Info gallery and search security tests passed' as status;
