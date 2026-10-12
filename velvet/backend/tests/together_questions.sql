begin;
set role authenticated;
set request.jwt.claims='{"is_anonymous":false}';
set request.jwt.claim.sub='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
create temporary table question_test(id uuid,game uuid);
do $$
declare id uuid:=gen_random_uuid();gid uuid:=gen_random_uuid();v jsonb;r jsonb;page jsonb;
begin
 insert into question_test values(id,gid);
 v:=public.velvet_together_create(id,'  What makes a good day?  ','Heart to heart');
 if v->>'ok'<>'true' or v->'round'->>'question'<>'What makes a good day?' then raise exception 'Question create failed: %',v;end if;
 v:=public.velvet_together_create(id,'What makes a good day?','Heart to heart');
 if v->>'ok'<>'true' then raise exception 'Create retry failed';end if;
 v:=public.velvet_together_create(id,'Tampered','Heart to heart');
 if v->>'error'<>'id_conflict' then raise exception 'Question mutation accepted';end if;
 v:=public.velvet_together_create(gen_random_uuid(),repeat('q',1001),'Heart to heart');
 if v->>'error'<>'invalid_question' then raise exception 'Oversized question accepted';end if;
 v:=public.velvet_together_answer(id,'A private answer from A');r:=v->'round';
 if v->>'ok'<>'true' or r->>'my_answer'<>'A private answer from A' or r->>'revealed'<>'false' or r->>'partner_answer' is not null then raise exception 'Premature reveal: %',v;end if;
 v:=public.velvet_together_answer(id,'A private answer from A');
 if v->>'ok'<>'true' then raise exception 'Answer retry failed';end if;
 v:=public.velvet_together_answer(id,'Edited after submission');
 if v->>'error'<>'answer_already_submitted' then raise exception 'Final answer overwritten';end if;
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',true);
 r:=public.velvet_together_get(id);
 if r->>'my_answer' is not null or r->>'partner_answer' is not null or r->>'partner_answered'<>'true' then raise exception 'Partner read private answer before submitting: %',r;end if;
 page:=public.velvet_together_list();
 if page->0->>'partner_answer' is not null then raise exception 'List leaked hidden answer';end if;
 v:=public.velvet_together_answer(id,'');
 if v->>'error'<>'invalid_answer' then raise exception 'Blank answer accepted';end if;
 v:=public.velvet_together_answer(id,repeat('a',2001));
 if v->>'error'<>'invalid_answer' then raise exception 'Oversized answer accepted';end if;
 v:=public.velvet_together_answer(id,'A private answer from B');r:=v->'round';
 if r->>'revealed'<>'true' or r->>'my_answer'<>'A private answer from B' or r->>'partner_answer'<>'A private answer from A' then raise exception 'Joint reveal failed: %',r;end if;
 perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true);
 r:=public.velvet_together_get(id);
 if r->>'partner_answer'<>'A private answer from B' then raise exception 'Original author missed reveal';end if;
 if has_table_privilege('authenticated','public.velvet_together_answers','SELECT') or has_table_privilege('authenticated','public.velvet_together_answers','INSERT') then raise exception 'Raw answers accessible';end if;
 v:=public.velvet_game_create(gid,'ttt');
 if v->>'ok'<>'true' then raise exception 'Game fixture failed';end if;
 perform set_config('request.jwt.claim.sub','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',true);
 v:=public.velvet_game_resign(gid,1);
 if v->>'error'<>'stale_move' then raise exception 'Stale resignation accepted';end if;
 -- Either player may resign, regardless of whose turn it is.
 v:=public.velvet_game_resign(gid,0);
 if v->>'ok'<>'true' or v->'game'->>'winner'<>'1' or v->'game'->>'version'<>'1' then raise exception 'Resignation failed: %',v;end if;
 v:=public.velvet_game_move(gid,1,0);
 if v->>'error'<>'game_finished' then raise exception 'Move after resignation accepted';end if;
 v:=public.velvet_game_resign(gid,1);
 if v->>'error'<>'game_finished' then raise exception 'Repeated resignation changed winner';end if;
 perform set_config('request.jwt.claim.sub','cccccccc-cccc-4ccc-8ccc-cccccccccccc',true);
 if public.velvet_together_get(id) is not null or public.velvet_together_list()<>'[]'::jsonb then raise exception 'Other couple read round';end if;
 v:=public.velvet_together_answer(id,'Outside');
 if v->>'error'<>'round_not_found' then raise exception 'Other couple answered';end if;
 v:=public.velvet_game_resign(gid,1);
 if v->>'error'<>'game_not_found' then raise exception 'Other couple resigned';end if;
end $$;
reset role;
-- Active rounds survive a long run of completed boards.
insert into public.velvet_online_games(couple_id,kind,player_one,player_two,board,winner,created_at)
select m.couple_id,'ttt','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',array_fill(0,array[9]),3,now()-n*interval '1 minute'
from public.velvet_members m cross join generate_series(1,25) n where m.user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
insert into public.velvet_online_games(id,couple_id,kind,player_one,player_two,board,created_at)
select '77777777-7777-4777-8777-777777777777',couple_id,'ttt','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',array_fill(0,array[9]),now()-interval '1 day'
from public.velvet_members where user_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
set role authenticated;
set request.jwt.claim.sub='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
do $$declare p jsonb;v jsonb;begin
 p:=public.velvet_game_list();
 if jsonb_array_length(p)<>20 or p->0->>'id'<>'77777777-7777-4777-8777-777777777777' then raise exception 'Active game disappeared behind completed rounds';end if;
 p:=public.velvet_together_list(null,null,1);
 if jsonb_array_length(p)<>1 then raise exception 'Question page missing';end if;
 if public.velvet_together_list((p->0->>'created_at')::timestamptz,(p->0->>'id')::uuid,1)<>'[]'::jsonb then raise exception 'Cursor duplicated a question';end if;
 begin perform public.velvet_together_list(now(),null,1);raise exception 'Malformed question cursor accepted';exception when invalid_parameter_value then null;end;
 perform set_config('request.jwt.claims','{"is_anonymous":true}',true);
 begin perform public.velvet_together_get((select id from question_test));raise exception 'Anonymous JWT read answers';exception when insufficient_privilege then null;end;
end $$;
reset role;
set role anon;
do $$begin
 begin perform public.velvet_together_list();raise exception 'Anon list accepted';exception when insufficient_privilege then null;end;
 begin perform public.velvet_game_resign(gen_random_uuid(),0);raise exception 'Anon resign accepted';exception when insufficient_privilege then null;end;
end $$;
reset role;
rollback;
