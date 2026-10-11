-- Read-only, authenticated WhatsApp-style Chat Info surfaces.
-- Chat photos are a separate library from Our Story: a query never copies or
-- creates velvet_story_entries. Both endpoints are owner-pair scoped.
create index if not exists velvet_gallery_recent
  on public.velvet_messages(couple_id,created_at desc,id desc)
  where kind='image' and deleted_at is null;

create or replace function public.velvet_chat_photos(
  p_before_at timestamptz default null,
  p_before_id uuid default null,
  p_limit integer default 30
) returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_result jsonb;
  v_limit integer := least(greatest(coalesce(p_limit,30),1),60);
begin
  if v_user is null then
    raise exception 'Sign in first' using errcode='42501';
  end if;
  if (p_before_at is null)<>(p_before_id is null) then
    raise exception 'Both history cursor fields are required' using errcode='22023';
  end if;
  select m.couple_id into v_couple from public.velvet_members m
  where m.user_id=v_user;
  if v_couple is null or
     (select count(*) from public.velvet_members m
       where m.couple_id=v_couple)<>2 then
    return '[]'::jsonb;
  end if;
  select coalesce(jsonb_agg(to_jsonb(rows) order by rows.created_at desc,rows.id desc),'[]'::jsonb)
    into v_result from (
    select m.id,m.sender_id,m.body as caption,m.created_at,
      m.payload->>'storage_path' as storage_path,
      m.payload->>'mime' as mime
    from public.velvet_messages m
    where m.couple_id=v_couple and m.kind='image' and m.deleted_at is null
      and nullif(m.payload->>'storage_path','') is not null
      and (p_before_at is null or (m.created_at,m.id)<(p_before_at,p_before_id))
      and not exists(select 1 from public.velvet_hidden_messages h
        where h.user_id=v_user and h.message_id=m.id)
    order by m.created_at desc,m.id desc
    limit v_limit
  ) rows;
  return v_result;
end;
$$;

create or replace function public.velvet_search_chat(
  p_query text,
  p_before_at timestamptz default null,
  p_before_id uuid default null,
  p_limit integer default 40
) returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_query text := btrim(coalesce(p_query,''));
  v_result jsonb;
  v_limit integer := least(greatest(coalesce(p_limit,40),1),60);
begin
  if v_user is null then
    raise exception 'Sign in first' using errcode='42501';
  end if;
  if char_length(v_query) not between 2 and 64 then
    return '[]'::jsonb;
  end if;
  if (p_before_at is null)<>(p_before_id is null) then
    raise exception 'Both search cursor fields are required' using errcode='22023';
  end if;
  select m.couple_id into v_couple from public.velvet_members m
  where m.user_id=v_user;
  if v_couple is null or
     (select count(*) from public.velvet_members m
       where m.couple_id=v_couple)<>2 then
    return '[]'::jsonb;
  end if;
  -- strpos means literal user input: %, _, and SQL metacharacters are NOT
  -- wildcard operators. No unrestricted cross-couple text or deleted content.
  select coalesce(jsonb_agg(to_jsonb(rows) order by rows.created_at desc,rows.id desc),'[]'::jsonb)
    into v_result from (
    select m.id,m.sender_id,m.kind,m.body,m.created_at
    from public.velvet_messages m
    where m.couple_id=v_couple and m.kind in ('text','image')
      and m.deleted_at is null
      and m.body is not null
      and strpos(lower(m.body),lower(v_query))>0
      and (p_before_at is null or (m.created_at,m.id)<(p_before_at,p_before_id))
      and not exists(select 1 from public.velvet_hidden_messages h
        where h.user_id=v_user and h.message_id=m.id)
    order by m.created_at desc,m.id desc
    limit v_limit
  ) rows;
  return v_result;
end;
$$;

revoke execute on function public.velvet_chat_photos(timestamptz,uuid,integer)
 from public,anon,authenticated;
revoke execute on function public.velvet_search_chat(text,timestamptz,uuid,integer)
 from public,anon,authenticated;
grant execute on function public.velvet_chat_photos(timestamptz,uuid,integer)
 to authenticated;
grant execute on function public.velvet_search_chat(text,timestamptz,uuid,integer)
 to authenticated;
