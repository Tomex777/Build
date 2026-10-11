-- v0.17: audio index for the connected Chat Info destination.
-- Voice recordings remain Chat-only assets; they are never added to Our Story.
create index if not exists velvet_voice_gallery_recent
  on public.velvet_messages(couple_id,created_at desc,id desc)
  where kind='voice' and deleted_at is null;

create or replace function public.velvet_chat_audio(
  p_before_at timestamptz default null,
  p_before_id uuid default null,
  p_limit integer default 24
) returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_result jsonb;
  v_limit integer := least(greatest(coalesce(p_limit,24),1),60);
begin
  if v_user is null then
    raise exception 'Sign in first' using errcode='42501';
  end if;
  if (p_before_at is null)<>(p_before_id is null) then
    raise exception 'Both audio cursor fields are required' using errcode='22023';
  end if;
  select m.couple_id into v_couple
    from public.velvet_members m where m.user_id=v_user;
  if v_couple is null or
     (select count(*) from public.velvet_members m
       where m.couple_id=v_couple)<>2 then
    return '[]'::jsonb;
  end if;
  select coalesce(
    jsonb_agg(to_jsonb(rows) order by rows.created_at desc,rows.id desc),
    '[]'::jsonb
  ) into v_result from (
    select m.id,m.sender_id,m.created_at,
      m.payload->>'storage_path' as storage_path,
      m.payload->>'mime' as mime,
      (m.payload->>'duration_ms')::integer as duration_ms,
      coalesce(m.payload->'waveform','[]'::jsonb) as waveform
    from public.velvet_messages m
    where m.couple_id=v_couple and m.kind='voice'
      and m.deleted_at is null
      and m.payload->>'mime'='audio/mp4'
      and (m.payload->>'duration_ms') ~ '^[0-9]{1,6}$'
      and (m.payload->>'duration_ms')::integer between 500 and 120000
      and nullif(m.payload->>'storage_path','') is not null
      and (p_before_at is null or
        (m.created_at,m.id)<(p_before_at,p_before_id))
      and not exists (
        select 1 from public.velvet_hidden_messages h
        where h.user_id=v_user and h.message_id=m.id
      )
    order by m.created_at desc,m.id desc
    limit v_limit
  ) rows;
  return v_result;
end;
$$;

revoke execute on function public.velvet_chat_audio(timestamptz,uuid,integer)
 from public,anon,authenticated;
grant execute on function public.velvet_chat_audio(timestamptz,uuid,integer)
 to authenticated;
