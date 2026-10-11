-- v0.17 Chat Info Audio tab: paging, ownership and visibility tests.
-- Existing test runner has a separate couple (cccc/dddd) and voice RPC fixtures.
-- The following rows simulate prior authenticated voice sends; production media
-- insertion still requires verified, private Storage upload via velvet_send_voice.
insert into public.velvet_messages(
  id,couple_id,sender_id,kind,body,payload,created_at
)
select ('77770000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,
  m.couple_id,
  case when n%2=0 then 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'::uuid
       else 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::uuid end,
  'voice',null,
  jsonb_build_object('storage_path',m.couple_id::text||'/'||
    (case when n%2=0 then 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
          else 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' end)||'/'||
    ('77770000-0000-4000-8000-'||lpad(n::text,12,'0'))||'.m4a',
    'mime','audio/mp4','bytes',2048,'duration_ms',1000+n*500,
    'waveform',jsonb_build_array(n*10,n*12,40)),
  now()-n*interval '1 hour'
from public.velvet_members m cross join generate_series(1,4) n
where m.user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';

set role authenticated;
set request.jwt.claims='{"is_anonymous":false}';
do $$
declare a jsonb; b jsonb; c jsonb;
begin
 perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
 a:=public.velvet_chat_audio(null,null,2);
 if jsonb_array_length(a)<>2
    or a->0->>'id'<>'77770000-0000-4000-8000-000000000001'
    or a->1->>'id'<>'77770000-0000-4000-8000-000000000002' then
   raise exception 'Initial audio page missing newest two: %',a;
 end if;
 if a->0->>'duration_ms'<>'1500'
    or a->0->'waveform'<>'[10,12,40]'::jsonb
    or a->0->>'mime'<>'audio/mp4'
    or a->0->>'storage_path' not like '%/77770000-0000-4000-8000-000000000001.m4a' then
   raise exception 'Private audio gallery metadata malformed: %',a;
 end if;
 b:=public.velvet_chat_audio(
   (a->1->>'created_at')::timestamptz,(a->1->>'id')::uuid,2);
 if jsonb_array_length(b)<>2
    or b->0->>'id'<>'77770000-0000-4000-8000-000000000003'
    or b->1->>'id'<>'77770000-0000-4000-8000-000000000004' then
   raise exception 'Audio gallery cursor skipped/duplicated rows: %',b;
 end if;
 c:=public.velvet_chat_audio(
   (b->1->>'created_at')::timestamptz,(b->1->>'id')::uuid,2);
 if c<>'[]'::jsonb then
   raise exception 'End of audio history repeated items: %',c;
 end if;
 a:=public.velvet_chat_audio(null,null,999);
 if jsonb_array_length(a)<>4 then raise exception 'Page bounds/filter wrong: %',a;end if;
end $$;

do $$
declare gallery jsonb; result jsonb; invalid_cursor boolean:=false;
begin
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
 gallery:=public.velvet_chat_audio();
 if jsonb_array_length(gallery)<>4 then
   raise exception 'Second partner cannot browse audio: %',gallery;
 end if;
 result:=public.velvet_delete_text('77770000-0000-4000-8000-000000000001',false);
 if result->>'ok'<>'true' then
   raise exception 'Partner personal audio hide failed: %',result;end if;
 gallery:=public.velvet_chat_audio();
 if jsonb_array_length(gallery)<>3 or exists(
   select 1 from jsonb_array_elements(gallery) row(value)
   where row.value->>'id'='77770000-0000-4000-8000-000000000001'
 ) then raise exception 'Partner-hidden recording leaked via audio gallery: %',gallery;end if;

 begin
   perform public.velvet_chat_audio(now(),null);
 exception when invalid_parameter_value then invalid_cursor:=true;end;
 if not invalid_cursor then raise exception 'Malformed audio cursor accepted';end if;
end $$;

do $$
declare gallery jsonb; result jsonb;
begin
 perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
 gallery:=public.velvet_chat_audio();
 if jsonb_array_length(gallery)<>4 then
   raise exception 'Personal hide changed other partner gallery: %',gallery;end if;
 result:=public.velvet_delete_text('77770000-0000-4000-8000-000000000002',true);
 if result->>'error'<>'not_sender' then
   raise exception 'Sender could delete the other partner recording: %',result;
 end if;
 result:=public.velvet_delete_text('77770000-0000-4000-8000-000000000001',true);
 if result->>'ok'<>'true' then raise exception 'Sender cannot erase own audio: %',result;end if;
 gallery:=public.velvet_chat_audio();
 if jsonb_array_length(gallery)<>3 or exists(
   select 1 from jsonb_array_elements(gallery) row(value)
   where row.value->>'id'='77770000-0000-4000-8000-000000000001'
 ) then raise exception 'Global audio deletion visible in gallery: %',gallery;end if;
end $$;

do $$
declare gallery jsonb;
begin
 perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
 gallery:=public.velvet_chat_audio();
 if gallery<>'[]'::jsonb then
   raise exception 'Member from another couple read private voice gallery: %',gallery;
 end if;
 perform set_config('request.jwt.claim.sub','dddddddd-dddd-4ddd-8ddd-dddddddddddd',false);
 gallery:=public.velvet_chat_audio();
 if gallery<>'[]'::jsonb then
   raise exception 'Second external member read private voice gallery: %',gallery;
 end if;
end $$;
reset role;

set role anon;
do $$
declare denied boolean:=false;
begin
 begin perform public.velvet_chat_audio();
 exception when insufficient_privilege then denied:=true;end;
 if not denied then raise exception 'Anonymous user accessed audio gallery';end if;
end $$;
reset role;

select 'Velvet private Chat Info audio gallery tests passed' as status;
