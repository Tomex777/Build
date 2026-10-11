-- Verify mixed-chat cursor delivery remains gap-free after >80 messages
-- arrive while a member's Chat screen is open.
set role authenticated;
set request.jwt.claims='{"is_anonymous":false}';
do $$
declare items jsonb; last_message jsonb;
begin
  perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
  items:=public.velvet_list_chat();
  if jsonb_array_length(items)=0 then raise exception 'No seed chat history';end if;
  last_message:=items->(jsonb_array_length(items)-1);
  perform set_config('velvet.test.cursor_at',last_message->>'created_at',false);
  perform set_config('velvet.test.cursor_id',last_message->>'id',false);
end $$;
reset role;

-- Trusted fixture-only write, not accessible to Android clients.
insert into public.velvet_messages(id,couple_id,sender_id,kind,body,created_at)
select gen_random_uuid(),mine.couple_id,mine.user_id,'text',
  'Flood '||lpad(seq::text,3,'0'),clock_timestamp()+seq*interval '1 second'
from public.velvet_members mine cross join generate_series(1,180) as seq
where mine.user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';

set role authenticated;
set request.jwt.claims='{"is_anonymous":false}';
do $$
declare items jsonb; cursor_at timestamptz;
 cursor_id uuid; previous_at timestamptz; previous_id uuid;
 i int; page int; expected int:=1; entry jsonb;
begin
  perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',false);
  cursor_at:=current_setting('velvet.test.cursor_at')::timestamptz;
  cursor_id:=current_setting('velvet.test.cursor_id')::uuid;
  for page in 1..3 loop
    items:=public.velvet_list_chat(cursor_at,cursor_id,80);
    if jsonb_array_length(items)<>(case when page<3 then 80 else 20 end) then
      raise exception 'Wrong page length at %: %',page,jsonb_array_length(items);
    end if;
    for i in 0..(jsonb_array_length(items)-1) loop
      entry:=items->i;
      if entry->>'body'<>'Flood '||lpad(expected::text,3,'0') then
        raise exception 'Skipped or reordered message %, actual %',expected,entry;
      end if;
      expected:=expected+1;
    end loop;
    entry:=items->(jsonb_array_length(items)-1);
    cursor_at:=(entry->>'created_at')::timestamptz;
    cursor_id:=(entry->>'id')::uuid;
  end loop;
  if expected<>181 then raise exception 'Cursor failed to deliver all 180 messages';end if;
  if public.velvet_list_chat(cursor_at,cursor_id,80)<>'[]'::jsonb then
    raise exception 'Final forward cursor did not exhaust messages';
  end if;
end $$;
reset role;
select 'Velvet mixed-chat forward cursor flood checks passed' as status;
