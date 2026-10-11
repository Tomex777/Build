-- Message action security, persistence and per-couple mutation feed.
-- Runs after all prior Velvet backend test scripts.
set role authenticated;
set request.jwt.claims='{"is_anonymous":false}';

do $$
declare v jsonb; feed jsonb; items jsonb;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
  v:=public.velvet_edit_text('11111111-1111-4111-8111-111111111111','   Edited by Alex   ');
  if v->>'ok'<>'true' or v->>'body'<>'Edited by Alex' or v->>'edited_at' is null then
    raise exception 'Sender edit failed: %',v;
  end if;
  v:=public.velvet_edit_text('33333333-3333-4333-8333-333333333333','Forged edit');
  if v->>'error'<>'not_sender' then raise exception 'Partner could modify other sender: %',v; end if;
  v:=public.velvet_delete_text('33333333-3333-4333-8333-333333333333',true);
  if v->>'error'<>'not_sender' then raise exception 'Delete-for-everyone forged by partner: %',v; end if;
  v:=public.velvet_edit_text('11111111-1111-4111-8111-111111111111','');
  if v->>'error'<>'invalid_message' then raise exception 'Blank edit accepted: %',v; end if;
  feed:=public.velvet_list_text_changes(0,100);
  if jsonb_array_length(feed)<>1 or feed->0->>'action'<>'edited'
      or feed->0->>'body'<>'Edited by Alex' then
    raise exception 'Mutation feed failed after edit: %',feed;
  end if;
end $$;

do $$
declare feed jsonb; v jsonb; rows jsonb;
begin
  perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
  feed:=public.velvet_list_text_changes(0,100);
  if jsonb_array_length(feed)<>1 or feed->0->>'body'<>'Edited by Alex' then
    raise exception 'Partner cannot see message edit: %',feed;
  end if;
  v:=public.velvet_delete_text('33333333-3333-4333-8333-333333333333',true);
  if v->>'ok'<>'true' then raise exception 'Sender delete-everyone failed: %',v; end if;
  rows:=public.velvet_list_text();
  if not exists(select 1 from jsonb_array_elements(rows) as t(value)
      where t.value->>'id'='33333333-3333-4333-8333-333333333333'
        and t.value->>'deleted'='true' and t.value->>'body' is null) then
    raise exception 'Deleted message must be a blank-content tombstone: %',rows;
  end if;
  v:=public.velvet_edit_text('33333333-3333-4333-8333-333333333333','Resurrect');
  if v->>'error'<>'already_deleted' then raise exception 'Deleted message edited: %',v; end if;
  v:=public.velvet_delete_text('11111111-1111-4111-8111-111111111111',false);
  if v->>'ok'<>'true' then raise exception 'Partner local hide failed: %',v; end if;
  rows:=public.velvet_list_text();
  if exists(select 1 from jsonb_array_elements(rows) as t(value)
      where t.value->>'id'='11111111-1111-4111-8111-111111111111') then
    raise exception 'Hidden message still in partner feed: %',rows;
  end if;
end $$;

do $$
declare feed jsonb; rows jsonb; v jsonb; last_id bigint;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
  feed:=public.velvet_list_text_changes();
  if jsonb_array_length(feed)<>2 then
    raise exception 'Hidden-for-partner event exposed or missing global edit/delete: %',feed;
  end if;
  if feed->1->>'action'<>'deleted' or feed->1->>'body' is not null
      or feed->1->>'deleted'<>'true' then
    raise exception 'Global delete event leaked erased text: %',feed;
  end if;
  last_id:=(feed->1->>'change_id')::bigint;
  if jsonb_array_length(public.velvet_list_text_changes(last_id,100))<>0 then
    raise exception 'Mutation cursor repeated or exposed other partner hide';
  end if;
  rows:=public.velvet_list_text();
  if not exists(select 1 from jsonb_array_elements(rows) as t(value)
      where t.value->>'id'='11111111-1111-4111-8111-111111111111'
        and t.value->>'body'='Edited by Alex') then
    raise exception 'Partner hide incorrectly removed original from sender: %',rows;
  end if;
  v:=public.velvet_delete_text('11111111-1111-4111-8111-111111111111',true);
  if v->>'ok'<>'true' then raise exception 'Author global delete failed: %',v; end if;
end $$;

do $$
declare v jsonb; blocked boolean:=false;
begin
  perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
  if public.velvet_list_text_changes() <> '[]'::jsonb then
    raise exception 'Unpaired outsider saw message mutations';
  end if;
  v:=public.velvet_edit_text('11111111-1111-4111-8111-111111111111','Hacked');
  if v->>'error'<>'not_paired' then raise exception 'Outsider edit accepted: %',v; end if;
  v:=public.velvet_delete_text('11111111-1111-4111-8111-111111111111',true);
  if v->>'error'<>'not_paired' then raise exception 'Outsider delete accepted: %',v; end if;
  begin
    update public.velvet_messages set body='hacked' where id='11111111-1111-4111-8111-111111111111';
  exception when insufficient_privilege then blocked:=true;
  end;
  if not blocked then raise exception 'Authenticated user can update messages directly'; end if;
end $$;
reset role;

-- Age-limit check must target an OLD BUT NOT DELETED message, otherwise
-- an already-deleted guard would mask the edit/deletion expiry policies.
insert into public.velvet_messages(id,couple_id,sender_id,kind,body,created_at)
  select '66666666-6666-4666-8666-666666666666',m.couple_id,m.user_id,'text',
    'Old enough to expire',now()-interval '50 hours'
  from public.velvet_members m
  where m.user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
set role authenticated;
set request.jwt.claims='{"is_anonymous":false}';
do $$
declare v jsonb;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
  v:=public.velvet_edit_text('66666666-6666-4666-8666-666666666666','Too late');
  if v->>'error'<>'edit_window_expired' then
    raise exception 'Old message editable outside ten-minute window: %',v;
  end if;
  v:=public.velvet_delete_text('66666666-6666-4666-8666-666666666666',true);
  if v->>'error'<>'delete_window_expired' then
    raise exception 'Old message delete-for-everyone accepted outside 48h: %',v;
  end if;
  v:=public.velvet_delete_text('66666666-6666-4666-8666-666666666666',false);
  if v->>'ok'<>'true' then raise exception 'User cannot hide old message locally: %',v; end if;
end $$;
reset role;

set role anon;
do $$
declare denied boolean:=false;
begin
  begin
    perform public.velvet_list_text_changes();
  exception when insufficient_privilege then denied:=true;
  end;
  if not denied then raise exception 'Anon can read message change feed'; end if;
end $$;
reset role;
select 'Velvet message action checks passed' as status;
