-- Runs AFTER all previous test files. Uses fixture-only simulated storage catalog.
set role authenticated;
set request.jwt.claims = '{"is_anonymous":false}';
do $$
declare v jsonb; rows jsonb; object_path text :=
 'df3a962b-0139-49d3-bb6b-2b0c50ab4cf2/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/44444444-4444-4444-8444-444444444444.jpg';
 v_couple uuid;
begin
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
 select couple_id into v_couple from public.velvet_members
   where user_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
 object_path:=v_couple::text||'/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/44444444-4444-4444-8444-444444444444.jpg';

 v:=public.velvet_send_image('77777777-7777-4777-8777-777777777777',
   object_path,'image/jpeg',125,'A photo from Robin');
 if v->>'error'<>'file_not_uploaded' then
   raise exception 'Image message accepted before private object upload: %',v;
 end if;
 insert into storage.objects(bucket_id,name,owner_id) values
  ('velvet-chat-media',object_path,'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
 -- Image cannot be downloaded before a chat message binds the private object.
 if exists(select 1 from storage.objects where name=object_path) then
   raise exception 'Unsent image could be fetched via RLS';end if;

 v:=public.velvet_send_image('77777777-7777-4777-8777-777777777777',
   object_path,'image/jpeg',125,'A photo from Robin');
 if v->>'ok'<>'true' then raise exception 'Authenticated image send rejected: %',v;end if;
 v:=public.velvet_send_image('77777777-7777-4777-8777-777777777777',
   object_path,'image/jpeg',125,'A photo from Robin');
 if v->>'duplicate'<>'true' then raise exception 'Image duplicate not idempotent: %',v;end if;
 v:=public.velvet_send_image('88888888-8888-4888-8888-888888888888',
   object_path,'image/jpeg',125,'Reuse file');
 if v->>'error'<>'file_already_sent' then
   raise exception 'Image object re-used by a second message: %',v;end if;
 v:=public.velvet_send_image('88888888-8888-4888-8888-888888888888',
   object_path,'image/png',125,'wrong extension');
 if v->>'error'<>'invalid_image' then
   raise exception 'Mismatched image MIME accepted: %',v;end if;
 if not exists(select 1 from storage.objects where name=object_path) then
   raise exception 'Sent media inaccessible to sender';end if;
 rows:=public.velvet_list_chat();
 if not exists(select 1 from jsonb_array_elements(rows) as row(value)
     where row.value->>'id'='77777777-7777-4777-8777-777777777777'
       and row.value->>'kind'='image'
       and row.value->'payload'->>'storage_path'=object_path
       and row.value->>'body'='A photo from Robin') then
   raise exception 'Mixed message list omitted image: %',rows;end if;
end $$;

do $$
declare v jsonb; rows jsonb; object_path text;
begin
 perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
 select row.value->'payload'->>'storage_path' into object_path
   from jsonb_array_elements(public.velvet_list_chat()) as row(value)
   where row.value->>'id'='77777777-7777-4777-8777-777777777777';
 if not exists(select 1 from storage.objects where name=object_path) then
   raise exception 'Partner could not download image';end if;
 rows:=public.velvet_list_chat();
 if not exists(select 1 from jsonb_array_elements(rows) as row(value)
     where row.value->>'id'='77777777-7777-4777-8777-777777777777'
       and row.value->>'kind'='image') then
   raise exception 'Partner did not see image message';end if;
 v:=public.velvet_send_image('99999999-9999-4999-8999-999999999999',
    object_path,'image/jpeg',125,null);
 if v->>'error'<>'wrong_owner' then
   raise exception 'Partner could send sender-owned image: %',v;end if;
 v:=public.velvet_delete_text('77777777-7777-4777-8777-777777777777',true);
 if v->>'error'<>'not_sender' then
   raise exception 'Partner could delete image for everyone: %',v;end if;
 v:=public.velvet_delete_text('77777777-7777-4777-8777-777777777777',false);
 if v->>'ok'<>'true' then raise exception 'Partner cannot hide image: %',v;end if;
 if exists(select 1 from storage.objects where name=object_path) then
   raise exception 'Hidden image still downloadable by partner';end if;
end $$;

do $$
declare v jsonb; object_path text; rows jsonb;
begin
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
 select row.value->'payload'->>'storage_path' into object_path
   from jsonb_array_elements(public.velvet_list_chat()) as row(value)
   where row.value->>'id'='77777777-7777-4777-8777-777777777777';
 if not exists(select 1 from storage.objects where name=object_path) then
   raise exception 'Partner hide affected sender image access';end if;
 v:=public.velvet_delete_text('77777777-7777-4777-8777-777777777777',true);
 if v->>'ok'<>'true' then raise exception 'Sender global image delete failed: %',v;end if;
 if exists(select 1 from storage.objects where name=object_path) then
   raise exception 'Deleted image could still be downloaded';end if;
 rows:=public.velvet_list_chat();
 if not exists(select 1 from jsonb_array_elements(rows) as row(value)
   where row.value->>'id'='77777777-7777-4777-8777-777777777777'
    and row.value->>'deleted'='true' and row.value->'payload'='{}'::jsonb) then
   raise exception 'Image delete failed to erase payload: %',rows;end if;
end $$;

do $$
declare v jsonb; rows jsonb;
begin
 perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
 rows:=public.velvet_list_chat();
 if jsonb_array_length(rows)<>0 then raise exception 'Outsider saw mixed chat';end if;
 v:=public.velvet_send_image('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'df3a962b-0139-49d3-bb6b-2b0c50ab4cf2/cccccccc-cccc-4ccc-8ccc-cccccccccccc/11111111-1111-4111-8111-111111111111.jpg',
    'image/jpeg',125,'Outsider');
 if v->>'error'<>'not_paired' then raise exception 'Unpaired user can send image: %',v;end if;
end $$;
reset role;
set role anon;
do $$
declare denied boolean:=false;
begin
 begin perform public.velvet_list_chat();
 exception when insufficient_privilege then denied:=true;end;
 if not denied then raise exception 'Anonymous user can list images';end if;
end $$;
reset role;
select 'Velvet private image security tests passed' as status;
