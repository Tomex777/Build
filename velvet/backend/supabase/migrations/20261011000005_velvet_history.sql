-- Older chat-history pagination. Requires 20261011000004_velvet_text_chat.sql.
-- Intentionally does NOT grant unrestricted SELECT on velvet_messages.
create or replace function public.velvet_page_text(
  p_before_at timestamptz default null,
  p_before_id uuid default null,
  p_limit integer default 80
) returns jsonb
language plpgsql stable security definer set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_couple uuid;
  v_count integer;
  v_limit integer := least(greatest(coalesce(p_limit,80),1),100);
  v_items jsonb;
begin
  if v_user is null then raise exception 'Sign in first' using errcode='42501'; end if;
  if p_before_at is not null and p_before_id is null then
    raise exception 'Timestamp cursors require a message id' using errcode='22023';
  end if;
  if p_before_at is null and p_before_id is not null then
    raise exception 'Message id cursors require a timestamp' using errcode='22023';
  end if;

  select m.couple_id into v_couple from public.velvet_members m where m.user_id=v_user;
  if v_couple is null then return '[]'::jsonb; end if;
  select count(*) into v_count from public.velvet_members where couple_id=v_couple;
  if v_count<>2 then return '[]'::jsonb; end if;

  select coalesce(jsonb_agg(to_jsonb(t) order by t.created_at,t.id),'[]'::jsonb)
    into v_items from (
    select id,sender_id,body,reply_to,created_at
      from public.velvet_messages
      where couple_id=v_couple and kind='text' and deleted_at is null
        and (p_before_at is null or (created_at,id)<(p_before_at,p_before_id))
      order by created_at desc,id desc limit v_limit
    ) t;
  return v_items;
end;
$$;
revoke execute on function public.velvet_page_text(timestamptz,uuid,integer) from public,anon,authenticated;
grant execute on function public.velvet_page_text(timestamptz,uuid,integer) to authenticated;
