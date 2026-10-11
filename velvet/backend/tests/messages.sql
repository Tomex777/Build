-- Executed after pairing.sql; members A and B are already paired.
set role authenticated;
set request.jwt.claims = '{"is_anonymous":false}';

create temporary table text_test(message_id uuid, created_at timestamptz);
do $$
declare v jsonb; msg uuid := '11111111-1111-4111-8111-111111111111'; rows jsonb;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
  v:=public.velvet_send_text(msg,'  Hello from Velvet  ');
  if v->>'ok'<>'true' or v->>'duplicate'<>'false' then
    raise exception 'Initial message failed: %',v;
  end if;
  insert into text_test values(msg,(v->>'created_at')::timestamptz);
  v:=public.velvet_send_text(msg,'Hello from Velvet');
  if v->>'ok'<>'true' or v->>'duplicate'<>'true' then
    raise exception 'Idempotent retry failed: %',v;
  end if;
  v:=public.velvet_send_text(msg,'tampered text');
  if v->>'error'<>'id_conflict' then raise exception 'Duplicate UUID mutation accepted'; end if;
  v:=public.velvet_send_text('22222222-2222-4222-8222-222222222222',repeat('x',4001));
  if v->>'error'<>'invalid_message' then raise exception 'Oversized text accepted'; end if;
  v:=public.velvet_send_text('22222222-2222-4222-8222-222222222222','Reply?',
      '99999999-9999-4999-8999-999999999999');
  if v->>'error'<>'invalid_reply' then raise exception 'Foreign/nonexistent reply accepted'; end if;
  rows:=public.velvet_list_text();
  if jsonb_array_length(rows)<>1 or rows->0->>'body'<>'Hello from Velvet' then
    raise exception 'Unexpected own message list: %',rows;
  end if;
end $$;

do $$
declare rows jsonb; v jsonb; ts timestamptz;
begin
  perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
  rows:=public.velvet_list_text();
  if jsonb_array_length(rows)<>1 then raise exception 'Partner did not receive text'; end if;
  v:=public.velvet_send_text('33333333-3333-4333-8333-333333333333','Hi there!');
  if v->>'ok'<>'true' then raise exception 'Partner cannot reply: %',v; end if;
  select created_at into ts from text_test limit 1;
  rows:=public.velvet_list_text(ts,'11111111-1111-4111-8111-111111111111',80);
  if jsonb_array_length(rows)<>1 or rows->0->>'body'<>'Hi there!' then
    raise exception 'Cursor polling did not return new message: %',rows;
  end if;
end $$;

do $$
declare v jsonb; rows jsonb; direct_blocked boolean:=false;
begin
  perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',false);
  rows:=public.velvet_list_text();
  if jsonb_array_length(rows)<>0 then raise exception 'Outsider read couple messages'; end if;
  v:=public.velvet_send_text('44444444-4444-4444-8444-444444444444','Outsider!');
  if v->>'error'<>'not_paired' then raise exception 'Outsider message accepted: %',v; end if;
  begin
    insert into public.velvet_messages(id,couple_id,sender_id,kind,body)
      select '55555555-5555-4555-8555-555555555555',couple_id,
         'cccccccc-cccc-4ccc-8ccc-cccccccccccc','text','direct'
      from public.velvet_members limit 1;
  exception when insufficient_privilege then direct_blocked:=true;
  end;
  if not direct_blocked then raise exception 'Authenticated client has direct write privileges'; end if;
end $$;

do $$
declare v jsonb; n int;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',false);
  for n in 1..40 loop
    v:=public.velvet_send_text(gen_random_uuid(),'Test ping '||n::text);
  end loop;
  if v->>'error'<>'rate_limited' then raise exception 'Send flood limit not enforced: %',v; end if;
end $$;

reset role;
set role anon;
do $$
declare blocked boolean := false;
begin
  begin
    perform public.velvet_list_text();
  exception when insufficient_privilege then blocked:=true;
  end;
  if not blocked then raise exception 'Anonymous user can read messages'; end if;
end $$;
reset role;
select 'Velvet chat tests passed' as status;
