"""Hostinger release control. Run as root on the VPS; output contains metadata only."""
import argparse, base64, hashlib, hmac, json, os, re, smtplib, ssl, subprocess, time
import urllib.error, urllib.request
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

DEPLOY = Path(__file__).resolve().parent
ROOT = DEPLOY.parents[2]
ENV = Path('/etc/facelit/production.env')
MANIFEST = ROOT / 'release-manifest.json'
M = json.loads(MANIFEST.read_text())
if not re.fullmatch(r'facelit-2026-\d{8}T\d{6}Z(?:-preview)?', M['releaseId']):
    raise RuntimeError('invalid_release_identifier')
if ROOT != Path('/opt/facelit-v2/releases') / M['releaseId']:
    raise RuntimeError('unexpected_release_directory')
SOURCES = {r['repository']: r for r in M['sources']}
if set(SOURCES) != {'frontend', 'backend', 'db', 'microservice'} or any(
    r['url'] != 'https://github.com/FaceLit-2026/' + name or not re.fullmatch(r'[0-9a-f]{40}', r['commit'])
    for name, r in SOURCES.items()
):
    raise RuntimeError('unexpected_source_manifest')
STATE = Path('/var/lib/facelit-deploy') / M['releaseId']
STATE.mkdir(parents=True, exist_ok=True, mode=0o700)
os.chmod(STATE, 0o700); os.umask(0o077)
C = ['docker', 'compose', '--project-directory', str(DEPLOY), '--env-file', str(ENV),
     '--env-file', str(ROOT / 'release.env'), '-f', str(DEPLOY / 'compose.yaml')]

def emit(event, **details):
    print(json.dumps({'event': event, **details}), flush=True)

def write(path, value):
    with open(path, 'w') as f:
        os.chmod(path, 0o600); json.dump(value, f, indent=2)

def run(args, label, data=None, timeout=90):
    p = subprocess.run(args, input=data, capture_output=True, timeout=timeout)
    with open(STATE / (label + '.log'), 'wb') as f:
        f.write(p.stdout); f.write(p.stderr)
    if p.returncode:
        raise RuntimeError(label + '_failed_private_log_available')
    return p.stdout

def environment(d):
    return dict(v.split('=', 1) for v in d['Config']['Env'] if '=' in v)

def metadata(service):
    # Full docker inspect contains credentials and must never be logged or persisted.
    p = subprocess.run(['docker', 'inspect', 'facelit-production-' + service + '-1'], capture_output=True)
    if p.returncode:
        raise RuntimeError('container_not_found_' + service)
    return json.loads(p.stdout)[0]

def bootstrap_environment():
    if ENV.exists():
        os.chmod(ENV, 0o600); return
    b, f, p = (environment(metadata(k)) for k in ('backend', 'facial', 'postgres'))
    values = {'DOMAIN': urlsplit(b['FACELIT_ACCESS_URL']).hostname,
              'POSTGRES_DB': p['POSTGRES_DB'], 'POSTGRES_USER': p['POSTGRES_USER'],
              'POSTGRES_PASSWORD': b['DB_PASSWORD'], 'JWT_SECRET': b['JWT_SECRET'],
              'FACIAL_API_KEY': f['API_KEY'], 'MAIL_USERNAME': b['MAIL_USERNAME'],
              'MAIL_PASSWORD': b['MAIL_PASSWORD'], 'MAIL_HOST': b.get('SPRING_MAIL_HOST', 'smtp.gmail.com'),
              'MAIL_PORT': b.get('SPRING_MAIL_PORT', '587'), 'FACELIT_ALLOWED_ORIGINS': b['FACELIT_ALLOWED_ORIGINS']}
    if values['DOMAIN'] != M['domain'] or b['FACIAL_EMBEDDING_API_KEY'] != f['API_KEY']:
        raise RuntimeError('unexpected_live_configuration')
    ENV.parent.mkdir(mode=0o700, parents=True, exist_ok=True); os.chmod(ENV.parent, 0o700)
    for v in values.values():
        if '\n' in v or '\r' in v or "'" in v:
            raise RuntimeError('review_environment_quoting')
    fd = os.open(ENV, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as out:
        out.write(''.join(k + "='" + v + "'\n" for k, v in values.items()))
    emit('private_environment_ready')

def configuration():
    # Capture expanded configuration only in memory; it contains credentials.
    p = subprocess.run(C + ['config', '--format', 'json'], capture_output=True)
    if p.returncode:
        raise RuntimeError('compose_configuration_invalid')
    return json.loads(p.stdout)

def check_configuration():
    bootstrap_environment(); c = configuration()
    for s, keys in {'backend': ['JWT_SECRET', 'DB_PASSWORD', 'MAIL_USERNAME', 'MAIL_PASSWORD', 'FACIAL_EMBEDDING_API_KEY'],
                    'facial': ['API_KEY'], 'postgres': ['POSTGRES_PASSWORD']}.items():
        actual = environment(metadata(s))
        if any(c['services'][s]['environment'][k] != actual[k] for k in keys):
            raise RuntimeError('private_configuration_differs_' + s)
    for name in ('postgres_data', 'facial_models', 'caddy_data', 'caddy_config'):
        run(['docker', 'volume', 'inspect', 'facelit-production_' + name], 'volume-' + name)
    run(['docker', 'network', 'inspect', 'facelit-production_default'], 'network')
    emit('configuration_valid_existing_state_confirmed')

def sql(query):
    p = metadata('postgres'); e = environment(p)
    # Queries are provided through stdin and responses stay in memory.
    result = subprocess.run(['docker', 'exec', '-i', p['Id'], 'psql', '-XAtq', '-v', 'ON_ERROR_STOP=1',
                             '-U', e['POSTGRES_USER'], '-d', e['POSTGRES_DB']], input=query.encode(), capture_output=True)
    if result.returncode:
        raise RuntimeError('database_probe_failed')
    return result.stdout.decode().strip()

def backup():
    p = metadata('postgres'); e = environment(p)
    name = 'database-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '.dump'
    path = STATE / name
    with open(path, 'wb') as out:
        r = subprocess.run(['docker', 'exec', p['Id'], 'pg_dump', '-U', e['POSTGRES_USER'], '-d', e['POSTGRES_DB'], '-Fc'],
                           stdout=out, stderr=subprocess.PIPE, timeout=180)
    if r.returncode or not path.stat().st_size:
        raise RuntimeError('backup_failed')
    with open(path, 'rb') as source:
        run(['docker', 'exec', '-i', p['Id'], 'pg_restore', '--list'], 'backup-validation', source.read())
    emit('database_backup_validated', file=name)

def snapshot():
    if (STATE / 'previous.json').exists():
        return
    d = {s: metadata(s) for s in ('web', 'backend', 'facial', 'postgres')}
    labels = d['backend']['Config']['Labels']
    value = {'workingDirectory': labels['com.docker.compose.project.working_dir'],
             'configFiles': labels['com.docker.compose.project.config_files'].split(','),
             'images': {s: v['Image'] for s, v in d.items()},
             'imageNames': {s: v['Config']['Image'] for s, v in d.items()}}
    for s, v in d.items():
        actual = json.loads(run(['docker', 'image', 'inspect', v['Config']['Image']], 'previous-image-' + s))[0]
        if actual['Id'] != v['Image']:
            raise RuntimeError('previous_image_tag_changed_' + s)
    write(STATE / 'previous.json', value)

def request(url, token=None):
    try:
        req = urllib.request.Request(url, headers={'Authorization': 'Bearer ' + token} if token else {})
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, dict(r.headers), r.read()
    except urllib.error.HTTPError as e:
        return e.code, {}, b''
    except Exception:
        return 0, {}, b''

def address(service, port):
    d = metadata(service)
    return 'http://' + next(iter(d['NetworkSettings']['Networks'].values()))['IPAddress'] + ':' + str(port)

def token():
    claims = sql("""SELECT json_build_object('sid',s.id_user_session::text,'userId',u.id_user_app::text,
      'sub',u.id_user_app::text,'role',r.role_name,'permissions','[]'::json)
      FROM security.user_session s JOIN security.user_app u ON s.id_user_app=u.id_user_app
      JOIN security.credential c ON c.id_user_app=u.id_user_app
      JOIN roleandpermission.user_role ur ON ur.id_user_app=u.id_user_app
      JOIN roleandpermission.role r ON r.id_role=ur.id_role
      WHERE s.session_status='ACTIVE' AND s.end_date IS NULL AND s.deleted_at IS NULL
      AND u.account_status='ACTIVE' AND u.deleted_at IS NULL AND c.credential_status='ACTIVE'
      AND r.role_name='COORDINATOR' ORDER BY s.start_date DESC LIMIT 1;""")
    if not claims:
        raise RuntimeError('login_once_to_enable_authenticated_release_probe')
    claims = dict(json.loads(claims), iat=int(time.time()), exp=int(time.time()) + 300)
    key = environment(metadata('backend'))['JWT_SECRET'].encode()
    alg, digest = ('HS512', hashlib.sha512) if len(key) >= 64 else ('HS384', hashlib.sha384) if len(key) >= 48 else ('HS256', hashlib.sha256)
    def enc(v):
        return base64.urlsafe_b64encode(v).rstrip(b'=')
    data = enc(json.dumps({'alg': alg, 'typ': 'JWT'}).encode()) + b'.' + enc(json.dumps(claims).encode())
    return (data + b'.' + enc(hmac.new(key, data, digest).digest())).decode()

def mail():
    e = environment(metadata('backend')); c = None
    try:
        c = smtplib.SMTP(e.get('SPRING_MAIL_HOST', 'smtp.gmail.com'), int(e.get('SPRING_MAIL_PORT', '587')), timeout=20)
        c.ehlo(); c.starttls(context=ssl.create_default_context()); c.ehlo()
        c.auth('PLAIN', lambda challenge=None: '\0' + e['MAIL_USERNAME'] + '\0' + e['MAIL_PASSWORD'])
        return True
    except Exception:
        return False
    finally:
        if c is not None:
            try: c.quit()
            except Exception: c.close()

def baseline():
    result = {'web': request('https://' + M['domain'] + '/auth/login')[0],
              'backend': request(address('backend', 8080) + '/health')[0],
              'facial': request(address('facial', 8090) + '/health')[0],
              'authenticatedProfile': request('https://' + M['domain'] + '/api/profile/me', token())[0],
              'database': sql('SELECT 1;') == '1'}
    if not all(v == 200 for k, v in result.items() if k != 'database') or not result['database']:
        raise RuntimeError('baseline_service_check_failed')
    emit('baseline_valid', checks=result)

def migrations():
    run(C + ['--profile', 'tools', 'run', '--rm', '--no-deps', 'migrate', 'validate'], 'liquibase-validate', timeout=180)
    run(C + ['--profile', 'tools', 'run', '--rm', '--no-deps', 'migrate', 'status', '--verbose'], 'liquibase-status', timeout=180)
    text = (STATE / 'liquibase-status.log').read_text(errors='replace')
    text = re.sub(r'\x1b\[[0-9;]*m', '', text)
    pending = re.findall(r'^\s*(\S+\.yaml::\S+)\s*$', text, re.M)
    if any('/025_terms_acceptance_notice_version.' not in p and '/026_terms_acceptance_user_notice_version_index.' not in p for p in pending):
        raise RuntimeError('unexpected_pending_migration_review_required')
    count = re.search(r'(\d+)\s+changesets?\s+(?:have|has)\s+not\s+been\s+applied', text, re.I)
    if count and int(count.group(1)) != len(pending):
        raise RuntimeError('pending_migration_format_requires_review')
    if not pending and not count and 'up to date' not in text.lower():
        raise RuntimeError('migration_status_requires_review')
    emit('liquibase_validated', pending=len(pending))

def wait(service, seconds):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            if service == 'postgres':
                ready = metadata(service)['State'].get('Health', {}).get('Status') == 'healthy'
            else:
                ready = request(address(service, 8090 if service == 'facial' else 8080) + '/health')[0] == 200
            if ready: return
        except Exception: pass
        time.sleep(3)
    raise RuntimeError('readiness_timeout_' + service)

def verify():
    baseline()
    pub = 'https://' + M['domain']
    status, headers, body = request(pub + '/auth/login')
    marker = next((v for k, v in headers.items() if k.lower() == 'x-facelit-release'), None)
    if status != 200 or marker != M['releaseId']:
        raise RuntimeError('public_release_marker_mismatch')
    scripts = re.findall(r'<script[^>]+src="([^"]+)"', body.decode(errors='replace'))
    if not scripts or request(pub + '/' + scripts[0].lstrip('/'))[0] != 200:
        raise RuntimeError('public_javascript_unavailable')
    proven = {}
    for service, repo in [('web', 'frontend'), ('backend', 'backend'), ('facial', 'microservice')]:
        d = metadata(service)
        img = json.loads(run(['docker', 'image', 'inspect', d['Image']], 'image-provenance-' + service))[0]
        labels = img['Config'].get('Labels', {})
        if labels.get('org.opencontainers.image.source') != SOURCES[repo]['url'] or labels.get('org.opencontainers.image.revision') != SOURCES[repo]['commit']:
            raise RuntimeError('source_provenance_mismatch_' + service)
        if d['Config']['Labels'].get('com.docker.compose.project.working_dir') != str(DEPLOY):
            raise RuntimeError('working_directory_mismatch_' + service)
        proven[service] = {'repository': SOURCES[repo]['url'], 'revision': SOURCES[repo]['commit'], 'image': d['Image']}
    p = metadata('postgres')['Config']['Labels']
    if p.get('facelit.source.revision') != SOURCES['db']['commit']:
        raise RuntimeError('database_release_revision_mismatch')
    if not mail():
        raise RuntimeError('mail_authentication_not_confirmed')
    migrations()
    result = {'releaseId': M['releaseId'], 'checkedAtUtc': datetime.now(timezone.utc).isoformat(),
              'domain': M['domain'], 'sources': M['sources'], 'images': proven,
              'checks': {'https': 200, 'authenticatedProfile': 200, 'javascript': 200,
                         'backend': 200, 'facial': 200, 'database': True, 'smtpAuthentication': True}}
    write(STATE / 'verified.json', result)
    emit('release_verified', **result)

def rollback():
    old = json.loads((STATE / 'previous.json').read_text())
    args = ['docker', 'compose', '--project-directory', old['workingDirectory']]
    for f in old['configFiles']:
        args += ['-f', f]
    run(args + ['up', '-d', '--no-build', '--pull', 'never', '--force-recreate', 'postgres', 'facial', 'backend', 'web'], 'rollback', timeout=600)
    wait('backend', 180); wait('facial', 360); baseline()
    emit('previous_release_restored')

def activate():
    if M.get('preview') or any(r['branch'] != 'main' for r in M['sources']):
        raise RuntimeError('activation_requires_main_sources')
    if not (STATE / 'prepared.json').exists():
        raise RuntimeError('prepare_release_first')
    check_configuration(); baseline(); migrations(); backup(); snapshot()
    stopped = False
    try:
        emit('activation_stopping_consumers')
        run(C + ['stop', 'web', 'backend', 'facial'], 'stop-consumers', timeout=120); stopped = True
        backup()
        run(C + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never', '--force-recreate', 'postgres'], 'activate-postgres', timeout=90)
        wait('postgres', 120)
        run(C + ['--profile', 'tools', 'run', '--rm', '--no-deps', 'migrate', 'update'], 'liquibase-update', timeout=180)
        for service, timeout in [('facial', 420), ('backend', 240)]:
            emit('activation_starting', service=service)
            run(C + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never', service], 'activate-' + service)
            wait(service, timeout)
        run(C + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never', 'web'], 'activate-web')
        time.sleep(4); verify()
        current = Path('/opt/facelit-v2/current'); current.parent.mkdir(parents=True, exist_ok=True)
        if current.exists() and not current.is_symlink():
            raise RuntimeError('current_path_is_not_a_symlink')
        temp = current.with_name('current-next')
        if temp.is_symlink(): temp.unlink()
        temp.symlink_to(ROOT, target_is_directory=True); os.replace(temp, current)
        emit('activation_completed', releaseId=M['releaseId'])
    except Exception:
        if stopped: rollback()
        raise

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'activate', 'verify', 'backup', 'rollback'])
    args = parser.parse_args()
    try:
        if os.geteuid() != 0: raise RuntimeError('run_as_root_on_vps')
        if M['organization'] != 'FaceLit-2026': raise RuntimeError('unexpected_source_organization')
        if args.action == 'prepare':
            check_configuration(); baseline(); snapshot(); backup()
            emit('building_images')
            for service in ['migrate', 'backend', 'facial', 'web']:
                emit('building_service', service=service)
                run(C + ['build', service], 'build-' + service, timeout=1800)
            migrations(); write(STATE / 'prepared.json', {'releaseId': M['releaseId']})
            emit('release_prepared', releaseId=M['releaseId'])
        else:
            globals()[args.action]()
    except Exception as exc:
        emit('failed', reason=str(exc) if isinstance(exc, RuntimeError) else type(exc).__name__, privateLogs=str(STATE))
        raise SystemExit(1)
