-- Authenticated two-person games and explicitly curated Chat media.
create schema if not exists velvet_private;
revoke all on schema velvet_private from public,anon;
grant usage on schema velvet_private to authenticated;

create function velvet_private.pair_id() returns uuid
language plpgsql stable security definer set search_path='' as $$
declare u uuid:=(select auth.uid()); c uuid;
begin
 if u is null or coalesce(auth.jwt()->>'is_anonymous','false')='true'
 or not exists(select 1 from auth.users where id=u and (email_confirmed_at is not null or phone_confirmed_at is not null)) then
  raise exception 'Verified sign-in required' using errcode='42501';
 end if;
 select couple_id into c from public.velvet_members where user_id=u;
 if c is null or (select count(*) from public.velvet_members where couple_id=c)<>2 then
  raise exception 'Pair both accounts first' using errcode='42501';
 end if;
 return c;
end $$;
revoke all on function velvet_private.pair_id() from public,anon;
grant execute on function velvet_private.pair_id() to authenticated;

-- No client controls game state, turn, winner, or version directly.
create table public.velvet_online_games(
 id uuid primary key default gen_random_uuid(),
 couple_id uuid not null references public.velvet_couples(id) on delete cascade,
 kind text not null check(kind in ('ttt','connect4')),
 player_one uuid not null references auth.users(id),
 player_two uuid not null references auth.users(id),
 board integer[] not null,
 turn integer not null default 1 check(turn in (1,2)),
 winner integer not null default 0 check(winner in (0,1,2,3)), -- 3 = draw
 version integer not null default 0,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 check(player_one<>player_two)
);
alter table public.velvet_online_games enable row level security;
revoke all on public.velvet_online_games from public,anon,authenticated;
create index velvet_online_games_recent on public.velvet_online_games(couple_id,created_at desc,id desc);

create function velvet_private.game_list() returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare c uuid:=velvet_private.pair_id(); result jsonb;
begin
 select coalesce(jsonb_agg(to_jsonb(g) order by g.created_at desc,g.id desc),'[]') into result
 from (select * from public.velvet_online_games where couple_id=c order by created_at desc,id desc limit 20) g;
 return result;
end $$;
create function velvet_private.game_create(p_id uuid,p_kind text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare c uuid:=velvet_private.pair_id(); u uuid:=(select auth.uid()); g public.velvet_online_games; other uuid;
begin
 if p_id is null or p_kind not in ('ttt','connect4') or p_kind is null then
  return jsonb_build_object('ok',false,'error','invalid_game');end if;
 perform 1 from public.velvet_couples where id=c for update;
 select * into g from public.velvet_online_games where id=p_id;
 if found then
  if g.couple_id=c and g.player_one=u and g.kind=p_kind then
   return jsonb_build_object('ok',true,'game',to_jsonb(g));end if;
  return jsonb_build_object('ok',false,'error','id_conflict');end if;
 if (select count(*) from public.velvet_online_games where couple_id=c and winner=0)>=5 then
  return jsonb_build_object('ok',false,'error','too_many_active_games');end if;
 if (select count(*) from public.velvet_online_games where couple_id=c and created_at>now()-interval '1 minute')>=10 then
  return jsonb_build_object('ok',false,'error','rate_limited');end if;
 select user_id into other from public.velvet_members where couple_id=c and user_id<>u;
 insert into public.velvet_online_games(id,couple_id,kind,player_one,player_two,board)
 values(p_id,c,p_kind,u,other,array_fill(0,array[case when p_kind='ttt' then 9 else 42 end])) returning * into g;
 return jsonb_build_object('ok',true,'game',to_jsonb(g));
end $$;
create function velvet_private.game_move(p_id uuid,p_version integer,p_cell integer) returns jsonb
language plpgsql security definer set search_path='' as $$
declare c uuid:=velvet_private.pair_id(); u uuid:=(select auth.uid()); g public.velvet_online_games;
 pos integer; rows integer; cols integer; needed integer; r integer; col integer;
 dr integer; dc integer; step integer; n integer; nr integer; nc integer; win boolean:=false;
begin
 select * into g from public.velvet_online_games where id=p_id and couple_id=c for update;
 if not found then return jsonb_build_object('ok',false,'error','game_not_found');end if;
 if p_version is null or p_version<>g.version then
  return jsonb_build_object('ok',false,'error','stale_move','game',to_jsonb(g));end if;
 if g.winner<>0 then return jsonb_build_object('ok',false,'error','game_finished');end if;
 if u<>(case when g.turn=1 then g.player_one else g.player_two end) then
  return jsonb_build_object('ok',false,'error','not_your_turn');end if;
 rows:=case when g.kind='ttt' then 3 else 6 end;
 cols:=case when g.kind='ttt' then 3 else 7 end;
 needed:=case when g.kind='ttt' then 3 else 4 end;
 if p_cell is null or p_cell<0 or p_cell>=(case when g.kind='ttt' then 9 else 7 end) then
  return jsonb_build_object('ok',false,'error','invalid_move');end if;
 if g.kind='ttt' then pos:=p_cell+1;
 else
  pos:=null;
  for r in reverse 5..0 loop
   if g.board[r*7+p_cell+1]=0 then pos:=r*7+p_cell+1;exit;end if;
  end loop;
 end if;
 if pos is null or g.board[pos]<>0 then return jsonb_build_object('ok',false,'error','occupied');end if;
 g.board[pos]:=g.turn; r:=(pos-1)/cols;col:=(pos-1)%cols;
 -- Count contiguous stones through the new move in all four directions.
 for dr in 0..1 loop
  for dc in -1..1 loop
   if (dr=0 and dc<>1) then continue;end if;
   n:=1;
   for step in -1..1 loop
    if step=0 then continue;end if;
    nr:=r+dr*step;nc:=col+dc*step;
    while nr>=0 and nr<rows and nc>=0 and nc<cols loop
     exit when g.board[nr*cols+nc+1]<>g.turn;
     n:=n+1;nr:=nr+dr*step;nc:=nc+dc*step;
    end loop;
   end loop;
   if n>=needed then win:=true;end if;
  end loop;
 end loop;
 if win then g.winner:=g.turn;
 elsif not (0=any(g.board)) then g.winner:=3;
 else g.turn:=3-g.turn;end if;
 update public.velvet_online_games set board=g.board,turn=g.turn,winner=g.winner,
 version=version+1,updated_at=now() where id=g.id returning * into g;
 return jsonb_build_object('ok',true,'game',to_jsonb(g));
end $$;

-- Separate curated records. Saving never modifies Chat or copies media automatically.
create table public.velvet_saved_story(
 id uuid primary key default gen_random_uuid(),
 couple_id uuid not null references public.velvet_couples(id) on delete cascade,
 message_id uuid not null references public.velvet_messages(id),
 original_sender uuid not null references auth.users(id),
 saved_by uuid not null references auth.users(id),
 bucket text not null check(bucket in ('velvet-chat-media','velvet-chat-video')),
 storage_path text not null,
 mime text not null,
 caption text not null default '',
 duration_ms integer not null default 0,
 saved_at timestamptz not null default now(),
 unique(couple_id,message_id)
);
alter table public.velvet_saved_story enable row level security;
revoke all on public.velvet_saved_story from public,anon,authenticated;
create index velvet_saved_story_recent on public.velvet_saved_story(couple_id,saved_at desc,id desc);
create index velvet_saved_story_storage on public.velvet_saved_story(bucket,storage_path);
create function velvet_private.story_save(p_message uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare c uuid:=velvet_private.pair_id();u uuid:=(select auth.uid());m public.velvet_messages; s public.velvet_saved_story;
begin
 perform 1 from public.velvet_couples where id=c for update;
 select * into m from public.velvet_messages where id=p_message and couple_id=c for update;
 if not found or m.deleted_at is not null or m.kind not in ('image','video')
 or exists(select 1 from public.velvet_hidden_messages where user_id=u and message_id=p_message)
 or nullif(m.payload->>'storage_path','') is null then
  return jsonb_build_object('ok',false,'error','media_unavailable');end if;
 select * into s from public.velvet_saved_story where couple_id=c and message_id=p_message;
 if found then return jsonb_build_object('ok',true,'duplicate',true,'entry',to_jsonb(s));end if;
 if (select count(*) from public.velvet_saved_story where couple_id=c)>=500 then
  return jsonb_build_object('ok',false,'error','story_limit');end if;
 insert into public.velvet_saved_story(couple_id,message_id,original_sender,saved_by,bucket,storage_path,mime,caption,duration_ms)
 values(c,p_message,m.sender_id,u,case when m.kind='image' then 'velvet-chat-media' else 'velvet-chat-video' end,
 m.payload->>'storage_path',m.payload->>'mime',coalesce(m.body,''),coalesce((m.payload->>'duration_ms')::integer,0)) returning * into s;
 return jsonb_build_object('ok',true,'duplicate',false,'entry',to_jsonb(s));
end $$;
create function velvet_private.story_list(p_before_at timestamptz,p_before_id uuid,p_limit integer) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare c uuid:=velvet_private.pair_id(); result jsonb;
begin
 if (p_before_at is null)<>(p_before_id is null) then raise exception 'Both cursor fields required' using errcode='22023';end if;
 select coalesce(jsonb_agg(to_jsonb(s) order by s.saved_at desc,s.id desc),'[]') into result from
 (select * from public.velvet_saved_story where couple_id=c and (p_before_at is null or (saved_at,id)<(p_before_at,p_before_id))
 order by saved_at desc,id desc limit least(greatest(coalesce(p_limit,20),1),60)) s;
 return result;
end $$;
create function velvet_private.story_remove(p_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare c uuid:=velvet_private.pair_id();u uuid:=(select auth.uid()); s public.velvet_saved_story;
begin
 select * into s from public.velvet_saved_story where id=p_id and couple_id=c for update;
 if not found then return jsonb_build_object('ok',false,'error','entry_not_found');end if;
 if s.saved_by<>u then return jsonb_build_object('ok',false,'error','only_saver_can_remove');end if;
 delete from public.velvet_saved_story where id=s.id;
 return jsonb_build_object('ok',true);
end $$;
-- A deliberately saved Story reference remains accessible independently of Chat deletion/hiding.
create function velvet_private.story_visible(p_bucket text,p_path text) returns boolean
language sql stable security definer set search_path='' as $$
 select (select auth.uid()) is not null and exists(
 select 1 from public.velvet_saved_story s join public.velvet_members m on m.couple_id=s.couple_id
 where m.user_id=(select auth.uid()) and s.bucket=p_bucket and s.storage_path=p_path
 and (select count(*) from public.velvet_members where couple_id=s.couple_id)=2)
$$;
create policy velvet_explicit_story_media on storage.objects for select to authenticated
 using(bucket_id in ('velvet-chat-media','velvet-chat-video') and velvet_private.story_visible(bucket_id,name));

-- Data API wrappers run as caller; privileged work stays in a non-exposed schema.
create function public.velvet_game_list() returns jsonb language sql security invoker set search_path='' as $$select velvet_private.game_list()$$;
create function public.velvet_game_create(p_id uuid,p_kind text) returns jsonb language sql security invoker set search_path='' as $$select velvet_private.game_create(p_id,p_kind)$$;
create function public.velvet_game_move(p_id uuid,p_version integer,p_cell integer) returns jsonb language sql security invoker set search_path='' as $$select velvet_private.game_move(p_id,p_version,p_cell)$$;
create function public.velvet_story_save(p_message uuid) returns jsonb language sql security invoker set search_path='' as $$select velvet_private.story_save(p_message)$$;
create function public.velvet_story_list(p_before_at timestamptz default null,p_before_id uuid default null,p_limit integer default 20) returns jsonb language sql security invoker set search_path='' as $$select velvet_private.story_list(p_before_at,p_before_id,p_limit)$$;
create function public.velvet_story_remove(p_id uuid) returns jsonb language sql security invoker set search_path='' as $$select velvet_private.story_remove(p_id)$$;
revoke all on all functions in schema velvet_private from public,anon;
grant execute on all functions in schema velvet_private to authenticated;
revoke all on function public.velvet_game_list(),public.velvet_game_create(uuid,text),public.velvet_game_move(uuid,integer,integer),public.velvet_story_save(uuid),public.velvet_story_list(timestamptz,uuid,integer),public.velvet_story_remove(uuid) from public,anon;
grant execute on function public.velvet_game_list(),public.velvet_game_create(uuid,text),public.velvet_game_move(uuid,integer,integer),public.velvet_story_save(uuid),public.velvet_story_list(timestamptz,uuid,integer),public.velvet_story_remove(uuid) to authenticated;
