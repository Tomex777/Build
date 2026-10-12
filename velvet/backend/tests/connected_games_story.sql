-- Runs after the original 14 test groups. Keep new fixtures isolated.
begin;
set role authenticated;
set request.jwt.claims='{"is_anonymous":false}';
set request.jwt.claim.sub='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
create temporary table online_test(id uuid);
do $$
declare g jsonb;v jsonb;gid uuid:=gen_random_uuid();other uuid:=gen_random_uuid();moves integer[]:=array[0,3,1,4,2];i integer;
begin
 v:=public.velvet_game_create(gid,'ttt');
 if v->>'ok'<>'true' then raise exception 'Create failed: %',v;end if;
 insert into online_test values(gid);
 v:=public.velvet_game_create(gid,'ttt');
 if v->'game'->>'version'<>'0' then raise exception 'Create retry changed round';end if;
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',true);
 v:=public.velvet_game_move(gid,0,0);
 if v->>'error'<>'not_your_turn' then raise exception 'Out of turn accepted';end if;
 for i in 1..5 loop
  perform set_config('request.jwt.claim.sub',case when i%2=1 then 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' else 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' end,true);
  v:=public.velvet_game_move(gid,i-1,moves[i]);
  if v->>'ok'<>'true' then raise exception 'Legal move % failed: %',i,v;end if;
  if i=1 then
   v:=public.velvet_game_move(gid,0,8);
   if v->>'error'<>'stale_move' or v->'game'->>'version'<>'1' then raise exception 'Stale move accepted';end if;
  end if;
 end loop;
 if v->'game'->>'winner'<>'1' then raise exception 'TTT horizontal win not detected: %',v;end if;
 v:=public.velvet_game_move(gid,5,8);
 if v->>'error'<>'game_finished' then raise exception 'Move after victory accepted';end if;
 v:=public.velvet_game_create(other,'ttt');
 v:=public.velvet_game_move(other,0,-1);
 if v->>'error'<>'invalid_move' then raise exception 'Invalid cell accepted';end if;
 v:=public.velvet_game_move(other,0,null);
 if v->>'error'<>'invalid_move' then raise exception 'Null cell accepted';end if;
 v:=public.velvet_game_move(other,0,0);
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',true);
 v:=public.velvet_game_move(other,1,0);
 if v->>'error'<>'occupied' then raise exception 'Occupied cell accepted';end if;
 perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',true);
 begin perform public.velvet_game_move(gid,5,6);raise exception 'Outsider permitted';
 exception when insufficient_privilege then null;end;
 begin perform public.velvet_game_list();raise exception 'Unpaired list permitted';
 exception when insufficient_privilege then null;end;
end $$;
reset role;
-- Seed legitimate nearly-finished positions to cover draw and both C4 diagonals.
create temporary table game_positions(kind text,board integer[],turn integer,cell integer,winner integer);
insert into game_positions values
 ('ttt',array[1,2,1,1,2,2,2,1,0],1,8,3),
 ('ttt',array[1,0,2,0,1,2,0,0,0],1,8,1),
 ('ttt',array[1,2,0,1,2,0,0,0,0],1,6,1),
 ('connect4',array_fill(0,array[42]),1,0,0);
do $$
declare g public.velvet_online_games; pos record;v jsonb;b integer[];i integer;gid uuid;
begin
 for pos in select * from game_positions loop
  gid:=gen_random_uuid();
  insert into public.velvet_online_games(id,couple_id,kind,player_one,player_two,board,turn)
  select gid,couple_id,pos.kind,'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',pos.board,pos.turn
  from public.velvet_members where user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true);
  v:=public.velvet_game_move(gid,0,pos.cell);
  if (v->'game'->>'winner')::integer<>pos.winner then raise exception 'Win/draw position failed: %',v;end if;
  if pos.kind='connect4' and v->'game'->'board'->>35<>'1' then raise exception 'Disc did not fall to bottom';end if;
 end loop;
 -- Connect Four: horizontal, vertical, ascending and descending wins.
 for i in 1..4 loop
  b:=array_fill(0,array[42]);
  if i=1 then b[36]:=1;b[37]:=1;b[38]:=1;
  elsif i=2 then b[36]:=1;b[29]:=1;b[22]:=1;
  elsif i=3 then b[36]:=1;b[30]:=1;b[24]:=1;b[39]:=2;b[32]:=2;b[25]:=2;
  else b[15]:=1;b[23]:=1;b[31]:=1;end if;
  gid:=gen_random_uuid();
  insert into public.velvet_online_games(id,couple_id,kind,player_one,player_two,board)
  select gid,couple_id,'connect4','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',b
  from public.velvet_members where user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  v:=public.velvet_game_move(gid,0,case when i=2 then 0 else 3 end);
  if v->'game'->>'winner'<>'1' then raise exception 'C4 direction % failed: %',i,v;end if;
 end loop;
end $$;
set role authenticated;
set request.jwt.claim.sub='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
do $$begin
 if has_table_privilege('authenticated','public.velvet_online_games','INSERT') or has_table_privilege('authenticated','public.velvet_online_games','UPDATE') then raise exception 'Direct board mutation permitted';end if;
end $$;
do $$declare v jsonb;
begin
 v:=public.velvet_game_create(gen_random_uuid(),'ttt');
 if v->>'error'<>'too_many_active_games' then raise exception 'Active round cap missing: %',v;end if;
 v:=public.velvet_game_create(gen_random_uuid(),'chess');
 if v->>'error'<>'invalid_game' then raise exception 'Unknown game accepted';end if;
 v:=public.velvet_game_move((select id from online_test),null,0);
 if v->>'error'<>'stale_move' then raise exception 'Null version accepted';end if;
end $$;
reset role;
-- Explicit Story save of partner media, retained sender/saver, no automatic copy.
insert into storage.objects(bucket_id,name,owner_id)
select 'velvet-chat-media',couple_id::text||'/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/99999999-1111-4111-8111-111111111111.png','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
from public.velvet_members where user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
insert into public.velvet_messages(id,couple_id,sender_id,kind,body,payload)
select '99999999-1111-4111-8111-111111111111',couple_id,'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','image','Our moment',
jsonb_build_object('storage_path',couple_id::text||'/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/99999999-1111-4111-8111-111111111111.png','mime','image/png','bytes',100)
from public.velvet_members where user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
set role authenticated;
set request.jwt.claim.sub='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
create temporary table saved_test(id uuid,path text);
do $$declare v jsonb;s jsonb;
begin
 if public.velvet_story_list()<>'[]'::jsonb then raise exception 'Chat media auto-saved';end if;
 v:=public.velvet_story_save('99999999-1111-4111-8111-111111111111');s:=v->'entry';
 if v->>'ok'<>'true' or s->>'original_sender'<>'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' or s->>'saved_by'<>'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' then raise exception 'Attribution failed: %',v;end if;
 insert into saved_test values((s->>'id')::uuid,s->>'storage_path');
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',true);
 v:=public.velvet_story_save('99999999-1111-4111-8111-111111111111');
 if v->>'duplicate'<>'true' or v->'entry'->>'saved_by'<>'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' then raise exception 'Duplicate replaced saver';end if;
 v:=public.velvet_story_remove((s->>'id')::uuid);
 if v->>'error'<>'only_saver_can_remove' then raise exception 'Partner removed another saver entry';end if;
 perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',true);
 if velvet_private.story_visible('velvet-chat-media',s->>'storage_path') then raise exception 'Outsider can read saved blob';end if;
 begin perform public.velvet_story_list();raise exception 'Outsider read Story';exception when insufficient_privilege then null;end;
end $$;
reset role;
update public.velvet_messages set deleted_at=now(),body=null,payload='{}' where id='99999999-1111-4111-8111-111111111111';
set role authenticated;
set request.jwt.claim.sub='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
do $$declare s record;v jsonb;
begin
 select * into s from saved_test;
 if jsonb_array_length(public.velvet_story_list())<>1 then raise exception 'Chat deletion removed curated Story';end if;
 if not exists(select 1 from storage.objects where name=s.path) then raise exception 'Saved Story lost private Storage access';end if;
 v:=public.velvet_story_remove(s.id);
 if v->>'ok'<>'true' or public.velvet_story_list()<>'[]'::jsonb then raise exception 'Saver cannot remove curated entry';end if;
 if exists(select 1 from storage.objects where name=s.path) then raise exception 'Deleted Chat blob remained visible after Story removal';end if;
end $$;
reset role;
insert into auth.users(id,email_confirmed_at) values('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',now());
insert into public.velvet_couples(id,created_by) values('eeeeeeee-0000-4000-8000-000000000001','cccccccc-cccc-4ccc-8ccc-cccccccccccc');
insert into public.velvet_members(couple_id,user_id) values
 ('eeeeeeee-0000-4000-8000-000000000001','cccccccc-cccc-4ccc-8ccc-cccccccccccc'),
 ('eeeeeeee-0000-4000-8000-000000000001','eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee');
set role authenticated;
set request.jwt.claim.sub='cccccccc-cccc-4ccc-8ccc-cccccccccccc';
do $$declare v jsonb;begin
 if public.velvet_game_list()<>'[]'::jsonb or public.velvet_story_list()<>'[]'::jsonb then raise exception 'Other pair data leaked';end if;
 v:=public.velvet_game_move((select id from online_test),0,0);
 if v->>'error'<>'game_not_found' then raise exception 'Other pair move accepted';end if;
 v:=public.velvet_story_save('99999999-1111-4111-8111-111111111111');
 if v->>'error'<>'media_unavailable' then raise exception 'Other pair media save accepted';end if;
end $$;
reset role;
set role anon;
do $$begin
 begin perform public.velvet_game_list();raise exception 'Anon listed games';exception when insufficient_privilege then null;end;
 begin perform public.velvet_story_list();raise exception 'Anon listed Story';exception when insufficient_privilege then null;end;
end $$;
reset role;
rollback;
