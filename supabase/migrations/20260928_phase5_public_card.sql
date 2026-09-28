-- MediQR Phase 5 - public medical card (no-login read)
-- Run once in the Supabase dashboard: SQL Editor -> New query -> paste -> Run.
-- Idempotent: safe to run more than once.

-- ---------------------------------------------------------------------------
-- 1) The only read path for anonymous callers: a whitelisted lookup by public
--    card id. SECURITY DEFINER + pinned search_path means the function runs
--    with the owner's rights (bypassing RLS on the private tables) while
--    returning EXACTLY the fields below - and nothing else. No auth uid, no
--    email, no timestamps, no database internals ever leave through it.
create or replace function public.get_public_card(p_card_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select jsonb_build_object(
        'full_name',          p.full_name,
        'blood_group',         p.blood_group,
        'allergies',           p.allergies,
        'medical_conditions',  p.medical_conditions,
        'medications',         p.medications,
        'additional_info',     p.additional_info,
        'emergency_contacts',  coalesce(
            (select jsonb_agg(
                        jsonb_build_object(
                            'contact_name', ec.contact_name,
                            'phone_number', ec.phone_number,
                            'position',     ec.position
                        )
                        order by ec.position
                    )
               from public.emergency_contacts ec
              where ec.user_id = p.id),
            '[]'::jsonb
        )
    )
    from public.profiles p
    where p.card_id = p_card_id
$$;

-- Tighten execution rights: no blanket PUBLIC execute, explicit allow-list.
revoke execute on function public.get_public_card(uuid) from public;
grant  execute on function public.get_public_card(uuid) to anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 2) Defense in depth: anon can no longer read the raw tables at all. The
--    public card is reachable only through get_public_card(); anything else an
--    anonymous caller tries against these tables fails with a permission
--    error (42501) instead of silently depending on RLS row filters.
revoke select on public.profiles           from anon;
revoke select on public.emergency_contacts from anon;

-- ---------------------------------------------------------------------------
-- Notes:
-- * Authenticated users are unaffected: their RLS (own rows only) still applies.
-- * Unknown/rotated card ids simply match no row -> the function returns JSON
--   null, which the app renders as "card not found".
-- * "deleted card": a removed profile row matches nothing -> same result.
-- * To intentionally invalidate old QR codes (rotate the id):
--     update public.profiles set card_id = gen_random_uuid() where id = '<uid>';
