-- Regression suite runs after all earlier pairing, media and audio tests.
set role authenticated;
set request.jwt.claims='{"is_anonymous":false}';
do $$
declare v jsonb; path text; pair uuid; items jsonb; refused boolean:=false;
begin
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
 select couple_id into pair from public.velvet_members
 where user_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
 path:=pair::text||'/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/34343434-3434-4434-8434-343434343434.mp4';
 v:=public.velvet_send_video('30303030-3030-4030-8030-303030303030',
   path,50345,3500,'Our little moment');
 if v->>'error'<>'file_not_uploaded' then
   raise exception 'Unuploaded video was published: %',v;end if;

 begin
   insert into storage.objects(bucket_id,name,owner_id)
   values('velvet-chat-video',
     pair::text||'/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/34343434-3434-4434-8434-343434343434.mp4',
     'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
 exception when insufficient_privilege then refused:=true;end;
 if not refused then raise exception 'Uploader wrote into partner video folder';end if;

 insert into storage.objects(bucket_id,name,owner_id) values(
    'velvet-chat-video',path,'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
 if exists(select 1 from storage.objects where name=path) then
   raise exception 'Unpublished video was downloadable';end if;
 v:=public.velvet_send_video('30303030-3030-4030-8030-303030303030',
   path,50345,3500,'Our little moment');
 if v->>'ok'<>'true' then raise exception 'Paired video send denied: %',v;end if;
 v:=public.velvet_send_video('30303030-3030-4030-8030-303030303030',
   path,50345,3500,'Our little moment');
 if v->>'duplicate'<>'true' then raise exception 'Identical video retry failed: %',v;end if;
 v:=public.velvet_send_video('40404040-4040-4040-8040-404040404040',
   path,50345,3500,'Retry path');
 if v->>'error'<>'file_already_sent' then
   raise exception 'Reused video path not rejected: %',v;end if;
 v:=public.velvet_send_video('40404040-4040-4040-8040-404040404040',
   path,50345,120001,'Invalid duration');
 if v->>'error'<>'invalid_video' then
   raise exception 'Oversized video duration accepted: %',v;end if;
 v:=public.velvet_send_video('40404040-4040-4040-8040-404040404040',
   path,20971521,3500,'Oversized file');
 if v->>'error'<>'invalid_video' then
   raise exception 'Oversized video attachment accepted: %',v;end if;
 if not exists(select 1 from storage.objects where name=path) then
   raise exception 'Sender cannot download sent video';end if;
 items:=public.velvet_list_chat(now()-interval '30 seconds',
    '00000000-0000-4000-8000-000000000000'::uuid);
 if not exists(select 1 from jsonb_array_elements(items) row(value)
   where row.value->>'id'='30303030-3030-4030-8030-303030303030'
    and row.value->>'kind'='video'
    and row.value->>'body'='Our little moment'
    and row.value->'payload'->>'mime'='video/mp4'
    and row.value->'payload'->>'duration_ms'='3500') then
    raise exception 'Video metadata absent from chat history';
 end if;
end $$;

do $$
declare path text; v jsonb;items jsonb;
begin
 perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
 select row.value->'payload'->>'storage_path' into path
 from jsonb_array_elements(public.velvet_list_chat(
   now()-interval '30 seconds','00000000-0000-4000-8000-000000000000'::uuid
 )) row(value)
 where row.value->>'id'='30303030-3030-4030-8030-303030303030';
 if path is null or not exists(select 1 from storage.objects where name=path) then
   raise exception 'Partner cannot download published video';end if;
 v:=public.velvet_mark_chat_read('30303030-3030-4030-8030-303030303030');
 if v->>'ok'<>'true' then raise exception 'Video was not read-markable: %',v;end if;
 v:=public.velvet_delete_text('30303030-3030-4030-8030-303030303030',true);
 if v->>'error'<>'not_sender' then
    raise exception 'Partner deleted sender video: %',v;end if;
 v:=public.velvet_delete_text('30303030-3030-4030-8030-303030303030',false);
 if v->>'ok'<>'true' then raise exception 'Partner cannot privately hide video: %',v;end if;
 if exists(select 1 from storage.objects where name=path) then
   raise exception 'Hidden video remains readable to hiding partner';end if;
 items:=public.velvet_list_chat(now()-interval '30 seconds',
    '00000000-0000-4000-8000-000000000000'::uuid);
 if exists(select 1 from jsonb_array_elements(items) row(value)
   where row.value->>'id'='30303030-3030-4030-8030-303030303030') then
   raise exception 'Hidden video still appears in Chat';end if;
end $$;

do $$
declare path text; v jsonb;
begin
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
 select row.value->'payload'->>'storage_path' into path
 from jsonb_array_elements(public.velvet_list_chat(
   now()-interval '30 seconds','00000000-0000-4000-8000-000000000000'::uuid
 )) row(value)
 where row.value->>'id'='30303030-3030-4030-8030-303030303030';
 if not exists(select 1 from storage.objects where name=path) then
   raise exception 'Partner hide affected sender video';end if;
 v:=public.velvet_delete_text('30303030-3030-4030-8030-303030303030',true);
 if v->>'ok'<>'true' then raise exception 'Sender could not erase video: %',v;end if;
 if exists(select 1 from storage.objects where name=path) then
   raise exception 'Video global delete did not revoke Storage GET';end if;
 v:=public.velvet_send_video('40404040-4040-4040-8040-404040404040',
   path,50345,3500,'Reusing tombstone');
 if v->>'error'<>'file_already_sent' then
   raise exception 'Deleted video path was recycled: %',v;end if;
end $$;

do $$
declare pair uuid; path text;v jsonb;
begin
 perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
 select couple_id into pair from public.velvet_members
 where user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
 path:=pair::text||'/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/34343434-3434-4434-8434-343434343434.mp4';
 if exists(select 1 from storage.objects where name=path) then
   raise exception 'Second couple read foreign video';end if;
 v:=public.velvet_send_video('40404040-4040-4040-8040-404040404040',
   path,50345,3500,'Other couple');
 if v->>'error'<>'wrong_owner' then
   raise exception 'Other couple published foreign video: %',v;end if;
end $$;
reset role;

set role anon;
do $$
declare blocked boolean:=false;
begin
 begin
  perform public.velvet_send_video('40404040-4040-4040-8040-404040404040',
    '11111111-1111-4111-8111-111111111111/22222222-2222-4222-8222-222222222222/33333333-3333-4333-8333-333333333333.mp4',
    50345,3500,null);
 exception when insufficient_privilege then blocked:=true;end;
 if not blocked then raise exception 'Anonymous video publishing was allowed';end if;
end $$;
reset role;
select 'Velvet private connected video tests passed' as status;
