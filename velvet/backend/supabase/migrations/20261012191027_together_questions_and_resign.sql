-- Shared questions: membership-protected answers withheld until both submit.
create table public.velvet_together_rounds(
 id uuid primary key,
 couple_id uuid not null references public.velvet_couples(id) on delete cascade,
 created_by uuid not null references auth.users(id),
 question text not null check(char_length(question) between 1 and 1000),
 category text not null check(char_length(category) between 1 and 64),
 created_at timestamptz not null default now()
);
create table public.velvet_together_answers(
 round_id uuid not null references public.velvet_together_rounds(id) on delete cascade,
 user_id uuid not null references auth.users(id),
 answer text not null check(char_length(answer) between 1 and 2000),
 submitted_at timestamptz not null default now(),
 primary key(round_id,user_id)
);
alter table public.velvet_together_rounds enable row level security;
alter table public.velvet_together_answers enable row level security;
revoke all on public.velvet_together_rounds,public.velvet_together_answers from public,anon,authenticated;
create index velvet_together_recent on public.velvet_together_rounds(couple_id,created_at desc,id desc);

create function velvet_private.together_snapshot(p_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare c uuid:=velvet_private.pair_id();u uuid:=(select auth.uid());q public.velvet_together_rounds;
 mine text;theirs text;revealed boolean;
begin
 select * into q from public.velvet_together_rounds where id=p_id and couple_id=c;
 if not found then return null;end if;
 select answer into mine from public.velvet_together_answers where round_id=q.id and user_id=u;
 select answer into theirs from public.velvet_together_answers where round_id=q.id and user_id<>u;
 revealed:=mine is not null and theirs is not null;
 return jsonb_build_object('id',q.id,'question',q.question,'category',q.category,
 'created_by',q.created_by,'created_at',q.created_at,'my_answer',mine,
 'partner_answer',case when revealed then theirs else null end,
 'partner_answered',theirs is not null,'revealed',revealed);
end $$;
create function velvet_private.together_create(p_id uuid,p_question text,p_category text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare c uuid:=velvet_private.pair_id();u uuid:=(select auth.uid());q public.velvet_together_rounds;
 body text:=btrim(coalesce(p_question,''));cat text:=btrim(coalesce(p_category,''));
begin
 if p_id is null or char_length(body) not between 1 and 1000 or char_length(cat) not between 1 and 64 then
 return jsonb_build_object('ok',false,'error','invalid_question');end if;
 perform 1 from public.velvet_couples where id=c for update;
 select * into q from public.velvet_together_rounds where id=p_id;
 if found then
  if q.couple_id=c and q.created_by=u and q.question=body and q.category=cat then
   return jsonb_build_object('ok',true,'round',velvet_private.together_snapshot(q.id));end if;
  return jsonb_build_object('ok',false,'error','id_conflict');end if;
 if (select count(*) from public.velvet_together_rounds where couple_id=c and created_at>now()-interval '1 minute')>=10 then
 return jsonb_build_object('ok',false,'error','rate_limited');end if;
 insert into public.velvet_together_rounds(id,couple_id,created_by,question,category) values(p_id,c,u,body,cat);
 return jsonb_build_object('ok',true,'round',velvet_private.together_snapshot(p_id));
end $$;
create function velvet_private.together_answer(p_id uuid,p_answer text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare c uuid:=velvet_private.pair_id();u uuid:=(select auth.uid());q public.velvet_together_rounds;
 body text:=btrim(coalesce(p_answer,''));previous text;
begin
 if char_length(body) not between 1 and 2000 then return jsonb_build_object('ok',false,'error','invalid_answer');end if;
 select * into q from public.velvet_together_rounds where id=p_id and couple_id=c for update;
 if not found then return jsonb_build_object('ok',false,'error','round_not_found');end if;
 select answer into previous from public.velvet_together_answers where round_id=q.id and user_id=u;
 if previous is not null and previous<>body then return jsonb_build_object('ok',false,'error','answer_already_submitted');end if;
 insert into public.velvet_together_answers(round_id,user_id,answer) values(q.id,u,body) on conflict do nothing;
 return jsonb_build_object('ok',true,'round',velvet_private.together_snapshot(p_id));
end $$;
create function velvet_private.together_list(p_before_at timestamptz,p_before_id uuid,p_limit integer) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare c uuid:=velvet_private.pair_id();result jsonb;
begin
 if (p_before_at is null)<>(p_before_id is null) then raise exception 'Both cursor fields required' using errcode='22023';end if;
 select coalesce(jsonb_agg(velvet_private.together_snapshot(q.id) order by q.created_at desc,q.id desc),'[]') into result
 from (select id,created_at from public.velvet_together_rounds where couple_id=c
 and (p_before_at is null or (created_at,id)<(p_before_at,p_before_id))
 order by created_at desc,id desc limit least(greatest(coalesce(p_limit,20),1),60)) q;
 return result;
end $$;
create function public.velvet_together_create(p_id uuid,p_question text,p_category text) returns jsonb language sql security invoker set search_path='' as $$select velvet_private.together_create(p_id,p_question,p_category)$$;
create function public.velvet_together_answer(p_id uuid,p_answer text) returns jsonb language sql security invoker set search_path='' as $$select velvet_private.together_answer(p_id,p_answer)$$;
create function public.velvet_together_get(p_id uuid) returns jsonb language sql security invoker set search_path='' as $$select velvet_private.together_snapshot(p_id)$$;
create function public.velvet_together_list(p_before_at timestamptz default null,p_before_id uuid default null,p_limit integer default 20) returns jsonb language sql security invoker set search_path='' as $$select velvet_private.together_list(p_before_at,p_before_id,p_limit)$$;

-- Keep all active games reachable ahead of completed rounds.
create or replace function velvet_private.game_list() returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare c uuid:=velvet_private.pair_id();result jsonb;
begin
 select coalesce(jsonb_agg(to_jsonb(g) order by (g.winner=0) desc,g.created_at desc,g.id desc),'[]') into result
 from (select * from public.velvet_online_games where couple_id=c order by (winner=0) desc,created_at desc,id desc limit 20) g;
 return result;
end $$;
create function velvet_private.game_resign(p_id uuid,p_version integer) returns jsonb
language plpgsql security definer set search_path='' as $$
declare c uuid:=velvet_private.pair_id();u uuid:=(select auth.uid());g public.velvet_online_games;
begin
 select * into g from public.velvet_online_games where id=p_id and couple_id=c for update;
 if not found then return jsonb_build_object('ok',false,'error','game_not_found');end if;
 if p_version is null or g.version<>p_version then return jsonb_build_object('ok',false,'error','stale_move','game',to_jsonb(g));end if;
 if g.winner<>0 then return jsonb_build_object('ok',false,'error','game_finished');end if;
 if u not in (g.player_one,g.player_two) then raise exception 'Not a player' using errcode='42501';end if;
 update public.velvet_online_games set winner=case when u=g.player_one then 2 else 1 end,
 version=version+1,updated_at=now() where id=g.id returning * into g;
 return jsonb_build_object('ok',true,'game',to_jsonb(g));
end $$;
create function public.velvet_game_resign(p_id uuid,p_version integer) returns jsonb language sql security invoker set search_path='' as $$select velvet_private.game_resign(p_id,p_version)$$;
revoke all on function velvet_private.together_snapshot(uuid),velvet_private.together_create(uuid,text,text),velvet_private.together_answer(uuid,text),velvet_private.together_list(timestamptz,uuid,integer),velvet_private.game_resign(uuid,integer) from public,anon;
grant execute on function velvet_private.together_snapshot(uuid),velvet_private.together_create(uuid,text,text),velvet_private.together_answer(uuid,text),velvet_private.together_list(timestamptz,uuid,integer),velvet_private.game_resign(uuid,integer) to authenticated;
revoke all on function public.velvet_together_get(uuid),public.velvet_together_create(uuid,text,text),public.velvet_together_answer(uuid,text),public.velvet_together_list(timestamptz,uuid,integer),public.velvet_game_resign(uuid,integer) from public,anon;
grant execute on function public.velvet_together_get(uuid),public.velvet_together_create(uuid,text,text),public.velvet_together_answer(uuid,text),public.velvet_together_list(timestamptz,uuid,integer),public.velvet_game_resign(uuid,integer) to authenticated;
