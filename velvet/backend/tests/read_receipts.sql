-- Read receipts security: sender cannot mark own messages, peers cannot
-- forge each other's cursors, hidden/deleted content cannot be read-marked.
-- Trusted fixture seeding; clients have no direct mutation permissions.
insert into public.velvet_messages(id,couple_id,sender_id,kind,body,created_at)
select 'eeeeeeee-0000-4000-8000-000000000001',m.couple_id,m.user_id,
 'text','Read receipt one',now()-interval '6 minutes'
from public.velvet_members m where m.user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
insert into public.velvet_messages(id,couple_id,sender_id,kind,body,created_at)
select 'eeeeeeee-0000-4000-8000-000000000002',m.couple_id,m.user_id,
 'image','Read receipt photo',now()-interval '5 minutes'
from public.velvet_members m where m.user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
insert into public.velvet_messages(id,couple_id,sender_id,kind,body,created_at)
select 'eeeeeeee-0000-4000-8000-000000000003',m.couple_id,m.user_id,
 'text','Partner read receipt',now()-interval '4 minutes'
from public.velvet_members m where m.user_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
insert into public.velvet_messages(id,couple_id,sender_id,kind,body,created_at)
select 'eeeeeeee-0000-4000-8000-000000000004',m.couple_id,m.user_id,
 'text','To be personally hidden',now()-interval '3 minutes'
from public.velvet_members m where m.user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
insert into public.velvet_messages(id,couple_id,sender_id,kind,body,created_at)
select 'eeeeeeee-0000-4000-8000-000000000005',m.couple_id,m.user_id,
 'text','To be deleted globally',now()-interval '2 minutes'
from public.velvet_members m where m.user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';

set role authenticated;
set request.jwt.claims='{"is_anonymous":false}';
do $$
declare r jsonb; state jsonb; denied boolean:=false;
begin
  perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
  state:=public.velvet_chat_read_state();
  if state->>'partner_read_id' is not null or state->>'my_read_id' is not null then
    raise exception 'Read receipts not initially empty: %',state;
  end if;
  r:=public.velvet_mark_chat_read('eeeeeeee-0000-4000-8000-000000000001');
  if r->>'ok'<>'true' or r->>'advanced'<>'true' then
    raise exception 'Recipient could not mark first text read: %',r;
  end if;
  r:=public.velvet_mark_chat_read('eeeeeeee-0000-4000-8000-000000000002');
  if r->>'ok'<>'true' or r->>'advanced'<>'true' then
    raise exception 'Recipient could not advance to photo: %',r;
  end if;
  r:=public.velvet_mark_chat_read('eeeeeeee-0000-4000-8000-000000000002');
  if r->>'ok'<>'true' or r->>'advanced'<>'false' then
    raise exception 'Duplicate receipt advanced cursor: %',r;
  end if;
  r:=public.velvet_mark_chat_read('eeeeeeee-0000-4000-8000-000000000001');
  if r->>'advanced'<>'false' or r->>'message_id'<>
     'eeeeeeee-0000-4000-8000-000000000002' then
    raise exception 'Read cursor regressed to older message: %',r;
  end if;
  r:=public.velvet_mark_chat_read('eeeeeeee-0000-4000-8000-000000000003');
  if r->>'error'<>'own_message' then
    raise exception 'Sender could mark own text read: %',r;
  end if;
  state:=public.velvet_chat_read_state();
  if state->>'my_read_id'<>'eeeeeeee-0000-4000-8000-000000000002'
     or state->>'partner_read_id' is not null then
    raise exception 'Incorrect member-scoped receipt state: %',state;
  end if;
  begin
    update public.velvet_read_cursors
      set message_id='eeeeeeee-0000-4000-8000-000000000005';
  exception when insufficient_privilege then denied:=true;
  end;
  if not denied then
    raise exception 'Client can forge own/partner receipt by direct SQL update';
  end if;
  r:=public.velvet_delete_text('eeeeeeee-0000-4000-8000-000000000004',false);
  if r->>'ok'<>'true' then raise exception 'Cannot hide test receipt message';end if;
  r:=public.velvet_mark_chat_read('eeeeeeee-0000-4000-8000-000000000004');
  if r->>'error'<>'not_visible' then
    raise exception 'Hidden text could be newly acknowledged: %',r;
  end if;
end $$;

do $$
declare r jsonb; state jsonb;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
  state:=public.velvet_chat_read_state();
  if state->>'partner_read_id'<>'eeeeeeee-0000-4000-8000-000000000002'
     or state->>'my_read_id' is not null then
    raise exception 'Partner watermark not visible to sender: %',state;
  end if;
  r:=public.velvet_mark_chat_read('eeeeeeee-0000-4000-8000-000000000003');
  if r->>'ok'<>'true' then
    raise exception 'Other member cannot acknowledge partner message: %',r;
  end if;
  r:=public.velvet_mark_chat_read('eeeeeeee-0000-4000-8000-000000000002');
  if r->>'error'<>'own_message' then
    raise exception 'Sender could mark own photo seen: %',r;
  end if;
  r:=public.velvet_delete_text('eeeeeeee-0000-4000-8000-000000000005',true);
  if r->>'ok'<>'true' then raise exception 'Cannot delete receipt test message';end if;
end $$;

do $$
declare r jsonb; state jsonb;
begin
  perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
  r:=public.velvet_mark_chat_read('eeeeeeee-0000-4000-8000-000000000005');
  if r->>'error'<>'not_visible' then
    raise exception 'Globally deleted text could be newly marked read: %',r;
  end if;
  state:=public.velvet_chat_read_state();
  if state->>'partner_read_id'<>'eeeeeeee-0000-4000-8000-000000000003' then
    raise exception 'Partner acknowledgement not reciprocally visible: %',state;
  end if;
end $$;

do $$
declare r jsonb; state jsonb;
begin
  perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
  if public.velvet_chat_read_state() is not null then
    raise exception 'Unpaired outsider can inspect paired receipts';
  end if;
  r:=public.velvet_mark_chat_read('eeeeeeee-0000-4000-8000-000000000002');
  if r->>'error'<>'not_paired' then
    raise exception 'Outsider advanced couple read receipt: %',r;
  end if;
end $$;
reset role;
set role anon;
do $$
declare denied boolean:=false;
begin
  begin
    perform public.velvet_chat_read_state();
  exception when insufficient_privilege then denied:=true; end;
  if not denied then raise exception 'Anonymous account inspected receipts';end if;
  denied:=false;
  begin
    perform public.velvet_mark_chat_read('eeeeeeee-0000-4000-8000-000000000002');
  exception when insufficient_privilege then denied:=true; end;
  if not denied then raise exception 'Anonymous account marked message read';end if;
end $$;
reset role;
-- Cross-couple adversary: two other fixture users form a separate pair.
-- Neither partner can acknowledge or inspect messages from the original pair.
insert into public.velvet_couples(created_by)
  values('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
insert into public.velvet_members(couple_id,user_id)
select c.id,u.id from public.velvet_couples c
cross join (values
  ('cccccccc-cccc-4ccc-8ccc-cccccccccccc'::uuid),
  ('dddddddd-dddd-4ddd-8ddd-dddddddddddd'::uuid)
) as u(id)
where c.created_by='cccccccc-cccc-4ccc-8ccc-cccccccccccc';

set role authenticated;
set request.jwt.claims='{"is_anonymous":false}';
do $$
declare r jsonb; state jsonb;
begin
  perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
  r:=public.velvet_mark_chat_read('eeeeeeee-0000-4000-8000-000000000002');
  if r->>'error'<>'not_found' then
    raise exception 'Member of another couple acknowledged foreign photo: %',r;
  end if;
  state:=public.velvet_chat_read_state();
  if state is null or state->>'my_read_id' is not null or
     state->>'partner_read_id' is not null then
    raise exception 'Cross-couple read state exposed foreign receipts: %',state;
  end if;
  perform set_config('request.jwt.claim.sub','dddddddd-dddd-4ddd-8ddd-dddddddddddd',false);
  r:=public.velvet_mark_chat_read('eeeeeeee-0000-4000-8000-000000000001');
  if r->>'error'<>'not_found' then
    raise exception 'Second outsider of another couple read text: %',r;
  end if;
end $$;
reset role;

select 'Velvet couple read receipt tests passed' as status;
