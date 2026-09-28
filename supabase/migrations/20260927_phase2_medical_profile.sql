-- MediQR Phase 2 - medical profile persistence
-- Run once in the Supabase dashboard: SQL Editor -> New query -> paste -> Run.
-- Idempotent: safe to run more than once.

-- ---------------------------------------------------------------------------
-- 1) "Additional Emergency Information" has no column yet.
alter table public.profiles
    add column if not exists additional_info text;

-- ---------------------------------------------------------------------------
-- 2) Remove development probe rows so testing starts from a clean slate.
--    (Created while verifying RLS/upsert over the REST API.)
delete from public.emergency_contacts
 where user_id = 'e1b2fdbd-1b69-4208-9d66-219ae7d299eb'
    or contact_name = 'SECRET-MOM';

delete from public.profiles
 where id = 'e1b2fdbd-1b69-4208-9d66-219ae7d299eb'
    or full_name like 'Upsert Test%';

-- ---------------------------------------------------------------------------
-- 3) Emergency contacts need a stable slot (1-3) so saving can upsert each
--    slot independently instead of delete-and-reinsert.
alter table public.emergency_contacts
    add column if not exists position integer not null default 1;

-- One row per user per slot; also the ON CONFLICT target for upserts.
create unique index if not exists emergency_contacts_user_position_key
    on public.emergency_contacts (user_id, position);

-- ---------------------------------------------------------------------------
-- Notes:
-- * RLS policies are untouched: they only reference (user_id / auth.uid()).
-- * profiles has no DELETE policy (by design) - saves use UPSERT on id.
-- * No trigger exists for updated_at; the app does not write it (creation
--   time stays accurate, update time is cosmetic).
