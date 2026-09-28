-- MediQR Phase 4 - public card identifier for the QR code
-- Run once in the Supabase dashboard: SQL Editor -> New query -> paste -> Run.
-- Idempotent: safe to run more than once.

-- ---------------------------------------------------------------------------
-- 1) Every user needs a stable, public card identifier that the QR code can
--    encode as https://YOUR-DOMAIN/card/{card_id}.
--    Deliberately separate from profiles.id (the auth uid): the public link
--    must never expose the internal user id used by RLS policies, and the card
--    id can be intentionally regenerated (invalidating old QR codes) without
--    touching the account itself.
alter table public.profiles
    add column if not exists card_id uuid;

-- ---------------------------------------------------------------------------
-- 2) Existing rows get their identifier.
update public.profiles
   set card_id = gen_random_uuid()
 where card_id is null;

-- ---------------------------------------------------------------------------
-- 3) New profile rows get one automatically (applied by Postgres/PostgREST on
--    insert, so the app never has to generate or store it).
alter table public.profiles
    alter column card_id set default gen_random_uuid();

-- Every user must have a card id from now on.
alter table public.profiles
    alter column card_id set not null;

-- ---------------------------------------------------------------------------
-- 4) Uniqueness: /card/{cardId} must resolve to exactly one person. The unique
--    index also makes PostgREST lookups on card_id fast in Phase 5.
create unique index if not exists profiles_card_id_key
    on public.profiles (card_id);

-- ---------------------------------------------------------------------------
-- Notes:
-- * Not exposed to anon yet: the public-card read policy comes with Phase 5.
--   Until then RLS still restricts every profiles query to the row owner.
-- * To intentionally rotate a card id (old QR codes stop working):
--     update public.profiles set card_id = gen_random_uuid() where id = '<uid>';
