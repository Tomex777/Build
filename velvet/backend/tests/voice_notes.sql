-- Voice messages: POST ownership, scoped private GET, monotonic history,
-- read acknowledgments, personal hide, global delete and path reuse prevention.
set role authenticated;
set request.jwt.claims='{"is_anonymous":false}';
do $$
declare path text; couple uuid; v jsonb; history jsonb; unauthorized boolean:=false;
begin
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
 select couple_id into couple from public.velvet_members
 where user_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
 path:=couple::text||'/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/12121212-1212-4121-8121-121212121212.m4a';
 v:=public.velvet_send_voice('22222222-2222-4222-8222-222222222222',
   path,22012,3000,'[8,20,60,15]'::jsonb);
 if v->>'error'<>'file_not_uploaded' then raise exception 'Unuploaded voice published: %',v;end if;

 begin
   insert into storage.objects(bucket_id,name,owner_id) values(
    'velvet-chat-voice',
    couple::text||'/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/34343434-3434-4434-8434-343434343434.m4a',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
 exception when insufficient_privilege then unauthorized:=true;end;
 if not unauthorized then raise exception 'Sender could upload into partner folder';end if;

 insert into storage.objects(bucket_id,name,owner_id) values(
    'velvet-chat-voice',path,'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
 if exists(select 1 from storage.objects o where o.name=path) then
   raise exception 'Unpublished voice can be downloaded';end if;

 v:=public.velvet_send_voice('22222222-2222-4222-8222-222222222222',
   path,22012,3000,'[8,20,60,15]'::jsonb);
 if v->>'ok'<>'true' then raise exception 'Published voice rejected: %',v;end if;
 v:=public.velvet_send_voice('22222222-2222-4222-8222-222222222222',
   path,22012,3000,'[8,20,60,15]'::jsonb);
 if v->>'duplicate'<>'true' then raise exception 'Retry inserted duplicate: %',v;end if;
 v:=public.velvet_send_voice('33333333-3333-4333-8333-333333333333',
   path,22012,3000,'[8,20,60,15]'::jsonb);
 if v->>'error'<>'file_already_sent' then raise exception 'Reused uploaded voice: %',v;end if;
 v:=public.velvet_send_voice('33333333-3333-4333-8333-333333333333',
   path,22012,3000,'[8,105,15]'::jsonb);
 if v->>'error'<>'invalid_voice' then raise exception 'Invalid waveform accepted: %',v;end if;
 if not exists(select 1 from storage.objects o where o.name=path) then
   raise exception 'Sender cannot download sent voice';end if;

 history:=public.velvet_list_chat();
 if not exists(select 1 from jsonb_array_elements(history) as row(value)
   where row.value->>'id'='22222222-2222-4222-8222-222222222222'
    and row.value->>'kind'='voice'
    and row.value->'payload'->>'duration_ms'='3000'
    and row.value->'payload'->'waveform'='[8,20,60,15]'::jsonb) then
   raise exception 'Voice metadata missing from mixed chat: %',history;end if;
end $$;

do $$
declare path text;v jsonb;history jsonb;
begin
 perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
 select row.value->'payload'->>'storage_path' into path
 from jsonb_array_elements(public.velvet_list_chat()) row(value)
 where row.value->>'id'='22222222-2222-4222-8222-222222222222';
 if path is null or not exists(select 1 from storage.objects o where o.name=path) then
   raise exception 'Partner cannot fetch private voice attachment';end if;
 v:=public.velvet_mark_chat_read('22222222-2222-4222-8222-222222222222');
 if v->>'ok'<>'true' then raise exception 'Voice playback note could not be read-marked: %',v;end if;
 v:=public.velvet_delete_text('22222222-2222-4222-8222-222222222222',true);
 if v->>'error'<>'not_sender' then raise exception 'Partner could globally erase voice: %',v;end if;
 v:=public.velvet_delete_text('22222222-2222-4222-8222-222222222222',false);
 if v->>'ok'<>'true' then raise exception 'Partner could not personally hide voice: %',v;end if;
 if exists(select 1 from storage.objects o where o.name=path) then
   raise exception 'Hidden voice still accessible by partner';end if;
 history:=public.velvet_list_chat();
 if exists(select 1 from jsonb_array_elements(history) row(value)
    where row.value->>'id'='22222222-2222-4222-8222-222222222222') then
   raise exception 'Hidden voice appeared in mixed chat list';end if;
end $$;

do $$
declare path text;v jsonb;
begin
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
 select row.value->'payload'->>'storage_path' into path
 from jsonb_array_elements(public.velvet_list_chat()) row(value)
 where row.value->>'id'='22222222-2222-4222-8222-222222222222';
 if not exists(select 1 from storage.objects o where o.name=path) then
   raise exception 'Personal hide affected sender';end if;
 v:=public.velvet_delete_text('22222222-2222-4222-8222-222222222222',true);
 if v->>'ok'<>'true' then raise exception 'Sender voice deletion failed: %',v;end if;
 if exists(select 1 from storage.objects o where o.name=path) then
   raise exception 'Globally deleted voice remains downloadable';end if;
 v:=public.velvet_send_voice('33333333-3333-4333-8333-333333333333',
   path,22012,3000,'[8,20,60,15]'::jsonb);
 if v->>'error'<>'file_already_sent' then raise exception 'Deleted voice path reused: %',v;end if;
end $$;

do $$
declare v jsonb;
begin
 perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
 v:=public.velvet_send_voice('33333333-3333-4333-8333-333333333333',
  '12121212-1212-4121-8121-121212121212/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/12121212-1212-4121-8121-121212121212.m4a',
  22012,3000,'[10]'::jsonb);
 if v->>'error'<>'wrong_owner' then raise exception 'Other couple can post voice into foreign path: %',v;end if;
end $$;
reset role;
set role anon;
do $$
declare blocked boolean:=false;
begin
 begin
   perform public.velvet_send_voice('33333333-3333-4333-8333-333333333333',
    '12121212-1212-4121-8121-121212121212/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/12121212-1212-4121-8121-121212121212.m4a',
    22012,3000,'[10]'::jsonb);
 exception when insufficient_privilege then blocked:=true;end;
 if not blocked then raise exception 'Anon accessed voice RPC';end if;
end $$;
reset role;
select 'Velvet private voice messaging tests passed' as status;
