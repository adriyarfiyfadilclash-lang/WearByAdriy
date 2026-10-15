-- WearByAdriy initial schema. Every table is owned per user and protected by Row Level Security.
-- Safe to run on an empty project; it does not drop anything.

create extension if not exists pgcrypto;

-- ---------- profiles ----------
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text check (char_length(display_name) <= 50),
  city text check (char_length(city) <= 120),
  latitude double precision check (latitude between -90 and 90),
  longitude double precision check (longitude between -180 and 180),
  preferred_styles text[] not null default '{}',
  preferred_colors text[] not null default '{}',
  avoided_colors text[] not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- ---------- wardrobe ----------
create table if not exists public.wardrobe_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 80),
  category text not null check (category in ('tops','bottoms','outerwear','shoes','bags','accessories','others')),
  colors text[] not null default '{}' check (cardinality(colors) <= 6),
  styles text[] not null default '{}' check (cardinality(styles) <= 10),
  occasions text[] not null default '{}' check (cardinality(occasions) <= 12),
  is_available boolean not null default true,
  is_favorite boolean not null default false,
  notes text check (char_length(notes) <= 500),
  -- Storage object path inside the private "wardrobe" bucket; must live in the owner's folder.
  image_path text check (image_path is null or image_path like (user_id::text || '/%')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists wardrobe_items_user_idx on public.wardrobe_items (user_id, created_at desc);

-- ---------- looks ----------
create table if not exists public.looks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 60),
  description text check (char_length(description) <= 300),
  reasons text[] not null default '{}' check (cardinality(reasons) <= 5),
  occasion text check (char_length(occasion) <= 40),
  tags text[] not null default '{}' check (cardinality(tags) <= 5),
  source text not null default 'manual' check (source in ('manual','rules','ai')),
  ai_provider text check (char_length(ai_provider) <= 40),
  is_favorite boolean not null default false,
  worn_dates date[] not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists looks_user_idx on public.looks (user_id, created_at desc);

create table if not exists public.look_items (
  look_id uuid not null references public.looks (id) on delete cascade,
  item_id uuid not null references public.wardrobe_items (id) on delete cascade,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  position smallint not null default 0,
  primary key (look_id, item_id)
);
create index if not exists look_items_item_idx on public.look_items (item_id);
create index if not exists look_items_user_idx on public.look_items (user_id);

-- A look may only contain the owner's own items.
create or replace function public.check_look_item_owner()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if not exists (select 1 from public.looks l where l.id = new.look_id and l.user_id = new.user_id)
     or not exists (select 1 from public.wardrobe_items w where w.id = new.item_id and w.user_id = new.user_id) then
    raise exception 'look and item must belong to the same user';
  end if;
  return new;
end $$;
drop trigger if exists look_items_owner on public.look_items;
create trigger look_items_owner before insert or update on public.look_items
  for each row execute function public.check_look_item_owner();

-- ---------- AI generation log (metadata only: no prompts, no keys) ----------
create table if not exists public.ai_generations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  provider text not null check (char_length(provider) <= 40),
  model text check (char_length(model) <= 80),
  status text not null check (status in ('success','failed')),
  error text check (char_length(error) <= 300),
  occasion text check (char_length(occasion) <= 40),
  look_count smallint not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists ai_generations_user_idx on public.ai_generations (user_id, created_at desc);

-- ---------- updated_at ----------
create or replace function public.touch_updated_at()
returns trigger language plpgsql set search_path = '' as $$
begin new.updated_at = now(); return new; end $$;

drop trigger if exists profiles_touch on public.profiles;
create trigger profiles_touch before update on public.profiles for each row execute function public.touch_updated_at();
drop trigger if exists wardrobe_touch on public.wardrobe_items;
create trigger wardrobe_touch before update on public.wardrobe_items for each row execute function public.touch_updated_at();
drop trigger if exists looks_touch on public.looks;
create trigger looks_touch before update on public.looks for each row execute function public.touch_updated_at();

-- ---------- profile row for every new user ----------
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, left(nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''), 50))
  on conflict (id) do nothing;
  return new;
end $$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------- Row Level Security ----------
alter table public.profiles enable row level security;
alter table public.wardrobe_items enable row level security;
alter table public.looks enable row level security;
alter table public.look_items enable row level security;
alter table public.ai_generations enable row level security;

create policy "profiles: read own" on public.profiles for select to authenticated using ((select auth.uid()) = id);
create policy "profiles: update own" on public.profiles for update to authenticated
  using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

create policy "items: read own" on public.wardrobe_items for select to authenticated using ((select auth.uid()) = user_id);
create policy "items: insert own" on public.wardrobe_items for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "items: update own" on public.wardrobe_items for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "items: delete own" on public.wardrobe_items for delete to authenticated using ((select auth.uid()) = user_id);

create policy "looks: read own" on public.looks for select to authenticated using ((select auth.uid()) = user_id);
create policy "looks: insert own" on public.looks for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "looks: update own" on public.looks for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "looks: delete own" on public.looks for delete to authenticated using ((select auth.uid()) = user_id);

create policy "look_items: read own" on public.look_items for select to authenticated using ((select auth.uid()) = user_id);
create policy "look_items: insert own" on public.look_items for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "look_items: update own" on public.look_items for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "look_items: delete own" on public.look_items for delete to authenticated using ((select auth.uid()) = user_id);

create policy "ai_generations: read own" on public.ai_generations for select to authenticated using ((select auth.uid()) = user_id);
create policy "ai_generations: insert own" on public.ai_generations for insert to authenticated with check ((select auth.uid()) = user_id);

revoke all on public.profiles, public.wardrobe_items, public.looks, public.look_items, public.ai_generations from anon;
revoke execute on function public.handle_new_user() from public, anon, authenticated;
revoke execute on function public.check_look_item_owner() from public, anon, authenticated;

-- ---------- private photo bucket ----------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('wardrobe', 'wardrobe', false, 5242880, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

-- Objects must be stored as <user id>/<file>; users can only touch their own folder.
create policy "wardrobe: read own" on storage.objects for select to authenticated
  using (bucket_id = 'wardrobe' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "wardrobe: upload own" on storage.objects for insert to authenticated
  with check (bucket_id = 'wardrobe' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "wardrobe: update own" on storage.objects for update to authenticated
  using (bucket_id = 'wardrobe' and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id = 'wardrobe' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "wardrobe: delete own" on storage.objects for delete to authenticated
  using (bucket_id = 'wardrobe' and (storage.foldername(name))[1] = (select auth.uid())::text);
