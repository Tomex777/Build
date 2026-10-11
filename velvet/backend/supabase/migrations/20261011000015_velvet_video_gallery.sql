-- v0.18.1: Private, read-only Video page in connected Chat Info.
create index if not exists velvet_video_gallery_recent
 on public.velvet_messages(couple_id,created_at desc,id desc)
 where kind='video' and deleted_at is null;

create or replace function public.velvet_chat_videos(
 p_before_at timestamptz default null,
 p_before_id uuid default null,
 p_limit integer default 16
) returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare
 v_user uuid := (select auth.uid());
 v_couple uuid;
 v_items jsonb;
 v_limit integer:=least(greatest(coalesce(p_limit,16),1),60);
begin
 if v_user is null then
  raise exception 'Sign in first' using errcode='42501';
 end if;
 if (p_before_at is null)<>(p_before_id is null) then
  raise exception 'Both video cursor fields required' using errcode='22023';
 end if;
 select m.couple_id into v_couple from public.velvet_members m
 where m.user_id=v_user;
 if v_couple is null or
   (select count(*) from public.velvet_members m where m.couple_id=v_couple)<>2 then
  return '[]'::jsonb;
 end if;
 select coalesce(
  jsonb_agg(to_jsonb(rows) order by rows.created_at desc,rows.id desc),
  '[]'::jsonb
 ) into v_items from (
  select m.id,m.sender_id,m.created_at,m.body as caption,
    m.payload->>'storage_path' as storage_path,
    (m.payload->>'duration_ms')::integer as duration_ms,
    m.payload->>'mime' as mime
  from public.velvet_messages m
  where m.couple_id=v_couple and m.kind='video'
   and m.deleted_at is null
   and m.payload->>'mime'='video/mp4'
   and nullif(m.payload->>'storage_path','') is not null
   and (m.payload->>'duration_ms') ~ '^[0-9]{1,6}$'
   and (m.payload->>'duration_ms')::integer between 100 and 120000
   and (p_before_at is null or
     (m.created_at,m.id)<(p_before_at,p_before_id))
   and not exists(
    select 1 from public.velvet_hidden_messages h
    where h.user_id=v_user and h.message_id=m.id
   )
  order by m.created_at desc,m.id desc
  limit v_limit
 ) rows;
 return v_items;
end;
$$;
revoke execute on function public.velvet_chat_videos(timestamptz,uuid,integer)
 from public,anon,authenticated;
grant execute on function public.velvet_chat_videos(timestamptz,uuid,integer)
 to authenticated;
