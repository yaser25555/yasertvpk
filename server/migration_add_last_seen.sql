-- Run once in Supabase SQL Editor (Dashboard → SQL → New query)
-- Adds online presence tracking for YasserTV

ALTER TABLE app_users
  ADD COLUMN IF NOT EXISTS last_seen TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS device_type TEXT;

-- Optional: allow app clients to update their own presence via device_key
-- (Skip if you already have a permissive UPDATE policy on app_users)
--
-- CREATE POLICY "users_update_own_presence" ON app_users
--   FOR UPDATE
--   USING (true)
--   WITH CHECK (true);
