-- Run once in a dedicated Supabase Free project using the SQL Editor.
begin;
create table public.aura_backups (
    user_id uuid primary key references auth.users(id) on delete cascade,
    data jsonb not null,
    updated_at timestamptz not null default now(),
    constraint aura_backup_size check (octet_length(data::text) <= 524288),
    constraint aura_backup_shape check (
        jsonb_typeof(data) = 'object'
        and data @> '{"format":"aura-local-backup","version":1}'::jsonb
        and jsonb_typeof(data->'settings') = 'object'
        and jsonb_typeof(data->'profiles') = 'array'
        and jsonb_typeof(data->'sessions') = 'array'
        and data ?& array['settings','profiles','sessions']
        and jsonb_array_length(data->'profiles') <= 20
        and jsonb_array_length(data->'sessions') <= 200
    )
);
alter table public.aura_backups enable row level security;
alter table public.aura_backups force row level security;
revoke all on public.aura_backups from anon, authenticated;
grant select, insert, update, delete on public.aura_backups to authenticated;
create policy aura_read_own on public.aura_backups for select to authenticated
    using ((select auth.uid()) = user_id);
create policy aura_insert_own on public.aura_backups for insert to authenticated
    with check ((select auth.uid()) = user_id);
create policy aura_update_own on public.aura_backups for update to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy aura_delete_own on public.aura_backups for delete to authenticated
    using ((select auth.uid()) = user_id);

create function public.aura_stamp_backup() returns trigger
language plpgsql set search_path = '' as $$
begin
    new.updated_at = now();
    return new;
end;
$$;
revoke all on function public.aura_stamp_backup() from public, anon, authenticated;
create trigger aura_backup_timestamp before insert or update on public.aura_backups
    for each row execute function public.aura_stamp_backup();

-- This project must be dedicated to AURA: this deletes the signed-in auth account.
-- There is deliberately no caller-supplied user ID and no dynamic SQL.
create function public.aura_delete_account() returns void
language plpgsql security definer set search_path = '' as $$
declare
    requesting_user uuid := (select auth.uid());
begin
    if requesting_user is null then
        raise exception 'Sign in first' using errcode = '42501';
    end if;
    delete from auth.users where id = requesting_user;
end;
$$;
revoke all on function public.aura_delete_account() from public, anon;
grant execute on function public.aura_delete_account() to authenticated;
commit;
