#!/usr/bin/env python3
"""Exercise the real migration in isolated PostgreSQL; mock only Supabase's auth schema.
No production project, network ports, user credentials, or remote data are used.
"""
from pathlib import Path
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
IMAGE = 'postgres:17-alpine@sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24'
NAME = 'aura-rls-' + uuid.uuid4().hex[:10]
checks = 0


def sql(statement, expected_error=None):
    global checks
    result = subprocess.run(['docker', 'exec', '-i', NAME, 'psql', '-X', '-U', 'postgres', '-v', 'ON_ERROR_STOP=1', '-At'], input=statement, text=True, capture_output=True)
    if expected_error:
        assert result.returncode != 0 and expected_error in result.stderr, result.stderr + result.stdout
    else:
        assert result.returncode == 0, result.stderr
    checks += 1
    return result.stdout.strip()


def as_user(uid, statement):
    return "set role authenticated; set request.jwt.claim.sub = '" + uid + "';\n" + statement


A = '00000000-0000-4000-8000-000000000001'
B = '00000000-0000-4000-8000-000000000002'
C = '00000000-0000-4000-8000-000000000003'
DATA = ''''{"format":"aura-local-backup","version":1,"settings":{},"profiles":[],"sessions":[]}'::jsonb'''
try:
    subprocess.run(['docker', 'run', '-d', '--name', NAME, '--network', 'none', '--tmpfs', '/var/lib/postgresql/data', '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', IMAGE], check=True, capture_output=True)
    for _ in range(80):
        if subprocess.run(['docker', 'exec', NAME, 'cat', '/proc/1/comm'], capture_output=True, text=True).stdout.strip() == 'postgres' and subprocess.run(['docker', 'exec', NAME, 'pg_isready', '-U', 'postgres'], capture_output=True).returncode == 0:
            break
        time.sleep(0.25)
    else:
        raise AssertionError('Local PostgreSQL did not become ready')
    sql('''create role anon nologin; create role authenticated nologin;
create schema auth; create table auth.users (id uuid primary key);
create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
grant usage on schema public, auth to anon, authenticated;
grant execute on function auth.uid() to anon, authenticated;''')
    sql((ROOT / 'supabase/migrations/202610180001_aura_backups.sql').read_text())
    sql(f"insert into auth.users values ('{A}'), ('{B}'), ('{C}');")
    sql(as_user(A, f"insert into public.aura_backups(user_id,data) values ('{A}',{DATA});"))
    sql(as_user(B, f"insert into public.aura_backups(user_id,data) values ('{B}',{DATA});"))
    assert sql(as_user(A, "select count(*) from public.aura_backups;")).splitlines()[-1] == '1'
    assert sql(as_user(A, f"select count(*) from public.aura_backups where user_id='{B}';")).splitlines()[-1] == '0'
    sql(as_user(A, f"insert into public.aura_backups(user_id,data) values ('{C}',{DATA});"), 'row-level security')
    sql(as_user(A, f"update public.aura_backups set user_id='{C}' where user_id='{A}';"), 'row-level security')
    assert sql(as_user(A, f"delete from public.aura_backups where user_id='{B}';")).splitlines()[-1] == 'DELETE 0'
    assert sql(as_user(A, f"update public.aura_backups set data={DATA} where user_id='{B}';")).splitlines()[-1] == 'UPDATE 0'
    sql(as_user(A, f"insert into public.aura_backups(user_id,data) values ('{A}',{DATA}) on conflict(user_id) do update set data=excluded.data;"))
    sql(as_user(A, "update public.aura_backups set data='{}'::jsonb;"), 'aura_backup_shape')
    sql(as_user(A, "update public.aura_backups set data=jsonb_set(data,'{sessions}',(select jsonb_agg('{}'::jsonb) from generate_series(1,201)));"), 'aura_backup_shape')
    sql(as_user(A, "update public.aura_backups set data=jsonb_set(data,'{profiles}',(select jsonb_agg('{}'::jsonb) from generate_series(1,21)));"), 'aura_backup_shape')
    sql(as_user(A, "update public.aura_backups set data=jsonb_set(data,'{extra}',to_jsonb(repeat('x',524289)));"), 'aura_backup_size')
    for statement in ["select * from public.aura_backups;", f"insert into public.aura_backups(user_id,data) values ('{C}',{DATA});", "delete from public.aura_backups;", "update public.aura_backups set data='{}';", "select public.aura_delete_account();"]:
        sql('set role anon; ' + statement, 'permission denied')
    sql(as_user('', 'select public.aura_delete_account();'), 'Sign in first')
    sql(as_user(A, "update public.aura_backups set updated_at='2000-01-01';"))
    assert sql(as_user(A, "select updated_at > '2020-01-01' from public.aura_backups;")).splitlines()[-1] == 't'
    sql(as_user(A, 'select public.aura_delete_account();'))
    assert sql(f"select count(*) from auth.users where id='{A}';") == '0'
    assert sql(f"select count(*) from public.aura_backups where user_id='{A}';") == '0'
    assert sql(f"select count(*) from auth.users where id='{B}';") == '1'
    assert sql(as_user(B, 'select count(*) from public.aura_backups;')).splitlines()[-1] == '1'
    sql(as_user(A, f"insert into public.aura_backups(user_id,data) values ('{A}',{DATA});"), 'foreign key')
    sql(as_user(B, f"delete from public.aura_backups where user_id='{B}';"))
    print(f'PASS: {checks} database operations; ownership isolation, anonymous denial, limits, timestamps, and self-deletion verified.')
finally:
    subprocess.run(['docker', 'rm', '-f', NAME], capture_output=True)
