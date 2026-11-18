-- PaketKu Tracker: private cloud table
-- Run this in Supabase Dashboard > SQL Editor.
create table if not exists public.parcels (
  id text not null,
  user_id uuid not null references auth.users(id) on delete cascade,
  awb text not null,
  courier text not null,
  courier_name text not null,
  label text not null default '',
  status text not null default 'Belum dilacak',
  last_update text not null default '',
  history text not null default '',
  updated_at timestamptz not null default now(),
  primary key (user_id, id)
);

alter table public.parcels enable row level security;

-- Each signed-in user can access only their own parcels.
drop policy if exists "Users can read own parcels" on public.parcels;
create policy "Users can read own parcels" on public.parcels
  for select to authenticated using (auth.uid() = user_id);

drop policy if exists "Users can insert own parcels" on public.parcels;
create policy "Users can insert own parcels" on public.parcels
  for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists "Users can update own parcels" on public.parcels;
create policy "Users can update own parcels" on public.parcels
  for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "Users can delete own parcels" on public.parcels;
create policy "Users can delete own parcels" on public.parcels
  for delete to authenticated using (auth.uid() = user_id);
