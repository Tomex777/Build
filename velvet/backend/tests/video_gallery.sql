-- Private Chat Info video gallery after the full video message security suite.
insert into public.velvet_messages(
 id,couple_id,sender_id,kind,body,payload,created_at
)
select ('55550000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,
 m.couple_id,m.user_id,'video',
 'Clip '||n::text,
 jsonb_build_object(
   'storage_path',m.couple_id::text||'/'||m.user_id::text||'/'||
     ('55550000-0000-4000-8000-'||lpad(n::text,12,'0'))||'.mp4',
   'mime','video/mp4','duration_ms',n*1000,'bytes',2500
 ),now()-n*interval '1 hour'
from public.velvet_members m cross join generate_series(1,3) n
where m.user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';

set role authenticated;
set request.jwt.claims='{"is_anonymous":false}';
do $$
declare a jsonb;b jsonb;
begin
 perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
 a:=public.velvet_chat_videos(null,null,2);
 if jsonb_array_length(a)<>2
  or a->0->>'id'<>'55550000-0000-4000-8000-000000000001'
  or a->1->>'id'<>'55550000-0000-4000-8000-000000000002' then
  raise exception 'Video gallery order/pagination failed: %',a;
 end if;
 if a->0->>'caption'<>'Clip 1' or a->0->>'mime'<>'video/mp4'
    or a->0->>'duration_ms'<>'1000' then
    raise exception 'Gallery video metadata invalid: %',a;
 end if;
 b:=public.velvet_chat_videos(
    (a->1->>'created_at')::timestamptz,(a->1->>'id')::uuid,2);
 if jsonb_array_length(b)<>1 or
    b->0->>'id'<>'55550000-0000-4000-8000-000000000003' then
    raise exception 'Second video gallery page skipped oldest: %',b;
 end if;
 if public.velvet_chat_videos((b->0->>'created_at')::timestamptz,
     (b->0->>'id')::uuid,2)<>'[]'::jsonb then
    raise exception 'Video gallery cursor repeated older pages';
 end if;
end $$;

do $$
declare gallery jsonb;v jsonb;bad boolean:=false;
begin
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
 gallery:=public.velvet_chat_videos();
 if jsonb_array_length(gallery)<>3 then
   raise exception 'Partner cannot see private videos: %',gallery;end if;
 v:=public.velvet_delete_text('55550000-0000-4000-8000-000000000002',false);
 if v->>'ok'<>'true' then raise exception 'Partner video hide failed: %',v;end if;
 gallery:=public.velvet_chat_videos();
 if jsonb_array_length(gallery)<>2 then
   raise exception 'Hidden video leaked into partner video gallery: %',gallery;end if;
 begin
  perform public.velvet_chat_videos(now(),null);
 exception when invalid_parameter_value then bad:=true;end;
 if not bad then raise exception 'Video gallery accepted malformed cursor';end if;
end $$;

do $$
declare gallery jsonb;v jsonb;
begin
 perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
 gallery:=public.velvet_chat_videos();
 if jsonb_array_length(gallery)<>3 then
   raise exception 'Partner hide changed sender video gallery';end if;
 v:=public.velvet_delete_text('55550000-0000-4000-8000-000000000001',true);
 if v->>'ok'<>'true' then raise exception 'Sender cannot erase own video';end if;
 gallery:=public.velvet_chat_videos();
 if jsonb_array_length(gallery)<>2 then
   raise exception 'Globally deleted video leaked to gallery';end if;
end $$;

do $$
begin
 perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
 if public.velvet_chat_videos()<>'[]'::jsonb then
  raise exception 'Unrelated member got video gallery';
 end if;
 perform set_config('request.jwt.claim.sub','dddddddd-dddd-4ddd-8ddd-dddddddddddd',false);
 if public.velvet_chat_videos()<>'[]'::jsonb then
  raise exception 'Another couple partner got foreign video gallery';
 end if;
end $$;
reset role;
set role anon;
do $$
declare denied boolean:=false;
begin
 begin perform public.velvet_chat_videos();
 exception when insufficient_privilege then denied:=true;end;
 if not denied then raise exception 'Anonymous video gallery was allowed';end if;
end $$;
reset role;
select 'Velvet Chat Info video gallery checks passed' as status;
