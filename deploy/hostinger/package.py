"""Create a public release exclusively from committed FaceLit-2026 sources."""
import argparse, hashlib, io, json, subprocess, tarfile
import copy
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

REPOS = ('frontend', 'backend', 'db', 'microservice')

def git(path, *args):
    p = subprocess.run(['git', '-C', str(path), *args], capture_output=True)
    if p.returncode:
        raise RuntimeError('Git operation failed: ' + path.name + ' / ' + args[0])
    return p.stdout

def build(project, output, preview=False, fetch=False):
    sources, archives = [], {}
    for name in REPOS:
        path = project / name
        remote = git(path, 'remote', 'get-url', 'origin').decode().strip()
        canonical = 'https://github.com/FaceLit-2026/' + name
        if remote.removesuffix('.git').rstrip('/') != canonical:
            raise RuntimeError('Unexpected remote: ' + name)
        if git(path, 'status', '--porcelain').strip():
            raise RuntimeError('Commit or preserve local edits before packaging: ' + name)
        if fetch:
            git(path, 'fetch', 'origin')
        ref = git(path, 'branch', '--show-current').decode().strip() if preview else 'main'
        sha = git(path, 'rev-parse', 'refs/heads/' + ref).decode().strip()
        if not preview and sha != git(path, 'rev-parse', 'refs/remotes/origin/main').decode().strip():
            raise RuntimeError('Local main is not synchronized with origin/main: ' + name)
        sources.append({'repository': name, 'url': canonical, 'branch': ref, 'commit': sha})
        archives[name] = git(path, '-c', 'core.autocrlf=false', '-c', 'core.eol=lf',
                             'archive', '--format=tar', '--prefix=' + name + '/', sha)
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    release_id = 'facelit-2026-' + stamp + ('-preview' if preview else '')
    manifest = {'releaseId': release_id, 'organization': 'FaceLit-2026', 'domain': 'facelit.tech',
                'generatedAtUtc': datetime.now(timezone.utc).isoformat(), 'preview': preview,
                'sources': sources, 'reviewedFrontendCommit': sources[0]['commit']}
    env = 'RELEASE_ID=' + release_id + '\n'
    env += ''.join(row['repository'].upper() + '_REVISION=' + row['commit'] + '\n' for row in sources)
    output.mkdir(parents=True, exist_ok=True)
    target = output / (release_id + '.tar.gz')
    with tarfile.open(target, 'w:gz') as result:
        for name in REPOS:
            with tarfile.open(fileobj=io.BytesIO(archives[name]), mode='r:') as source:
                for entry in source.getmembers():
                    parts = PurePosixPath(entry.name).parts
                    if not parts or parts[0] != name or '..' in parts or entry.name.startswith('/'):
                        raise RuntimeError('Unsafe archive path')
                    filename = parts[-1]
                    if (filename == '.env' or (filename.startswith('.env.') and filename != '.env.example')
                            or filename.endswith(('.pem', '.key')) or '.git' in parts):
                        raise RuntimeError('Private filename tracked in source: ' + name)
                    if entry.issym() or entry.islnk():
                        raise RuntimeError('Review symbolic links before packaging: ' + entry.name)
                    result.addfile(entry, source.extractfile(entry) if entry.isfile() else None)
                    # A regression test compares the landing route with its committed source.
                    # Export that source reference rather than including .git or Git credentials.
                    if name == 'frontend' and entry.isfile() and PurePosixPath(entry.name).suffix in {'.ts', '.tsx', '.js', '.jsx', '.json'}:
                        reference = copy.copy(entry)
                        reference.name = 'reviewed-frontend/' + '/'.join(parts[1:])
                        result.addfile(reference, source.extractfile(entry))
        for name, data in [('release-manifest.json', json.dumps(manifest, indent=2).encode()),
                           ('release.env', env.encode())]:
            entry = tarfile.TarInfo(name); entry.size = len(data); entry.mode = 0o644
            result.addfile(entry, io.BytesIO(data))
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix(target.suffix + '.sha256').write_text(digest + '  ' + target.name + '\n')
    print(json.dumps({'archive': str(target), 'sha256': digest, 'manifest': manifest}))
    return target

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project-root', type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument('--output-dir', type=Path)
    parser.add_argument('--fetch', action='store_true')
    parser.add_argument('--preview', action='store_true', help='Build a review package from current branches; cannot activate.')
    args = parser.parse_args()
    build(args.project_root.resolve(), (args.output_dir or args.project_root / 'output').resolve(), args.preview, args.fetch)
