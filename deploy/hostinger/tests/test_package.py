import contextlib
import importlib.util
import io
import json
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('release_package', Path(__file__).resolve().parents[1] / 'package.py')
package = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)

class PackageSafetyTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='facelit-package-check-')
        self.root = Path(self.tmp.name).resolve()
        self.assertEqual(self.root.parent, Path(tempfile.gettempdir()).resolve())
        self.assertTrue(self.root.name.startswith('facelit-package-check-'))
        for name in package.REPOS:
            p = self.root / name; p.mkdir()
            self.git(name, 'init', '-b', 'main')
            self.git(name, 'config', 'user.name', 'Package Test')
            self.git(name, 'config', 'user.email', 'package-test@example.invalid')
            self.git(name, 'remote', 'add', 'origin', 'https://github.com/FaceLit-2026/' + name + '.git')
            (p / '.gitignore').write_text('.env\n')
            (p / 'app.txt').write_text('public committed source\n')
            (p / '.env').write_text('TEST_ONLY=not-a-real-credential\n')
            self.git(name, 'add', '.'); self.git(name, 'commit', '-m', 'test source')
            self.git(name, 'update-ref', 'refs/remotes/origin/main', self.git(name, 'rev-parse', 'HEAD'))

    def tearDown(self):
        # Verify the resolved disposable directory before recursive cleanup on Windows.
        self.assertEqual(self.root.parent, Path(tempfile.gettempdir()).resolve())
        self.assertTrue(self.root.name.startswith('facelit-package-check-'))
        self.tmp.cleanup()

    def git(self, repo, *args):
        p = subprocess.run(['git', '-C', str(self.root / repo), *args], capture_output=True, text=True)
        self.assertEqual(p.returncode, 0, p.stderr)
        return p.stdout.strip()

    def build(self, preview=False):
        with contextlib.redirect_stdout(io.StringIO()):
            return package.build(self.root, self.root / 'output', preview)

    def test_archive_uses_committed_sources_and_excludes_private_env(self):
        result = self.build()
        with tarfile.open(result) as t:
            self.assertFalse(any(Path(n).name == '.env' for n in t.getnames()))
            manifest = json.load(t.extractfile('release-manifest.json'))
            self.assertFalse(manifest['preview'])
            for row in manifest['sources']:
                self.assertEqual(row['branch'], 'main')
                self.assertEqual(row['commit'], self.git(row['repository'], 'rev-parse', 'HEAD'))
                expected = subprocess.check_output(['git', '-C', str(self.root / row['repository']),
                                                    'show', row['commit'] + ':app.txt'])
                self.assertEqual(t.extractfile(row['repository'] + '/app.txt').read(), expected)

    def test_tracked_private_env_is_rejected(self):
        self.git('backend', 'add', '-f', '.env'); self.git('backend', 'commit', '-m', 'invalid test fixture')
        self.git('backend', 'update-ref', 'refs/remotes/origin/main', self.git('backend', 'rev-parse', 'HEAD'))
        with self.assertRaisesRegex(RuntimeError, 'Private filename'):
            self.build()

    def test_old_organization_is_rejected(self):
        self.git('frontend', 'remote', 'set-url', 'origin', 'https://github.com/other-org/frontend.git')
        with self.assertRaisesRegex(RuntimeError, 'Unexpected remote'):
            self.build()

    def test_uncommitted_edit_is_rejected(self):
        (self.root / 'db/app.txt').write_text('unreviewed edit\n')
        with self.assertRaisesRegex(RuntimeError, 'local edits'):
            self.build()

    def test_unpublished_main_is_rejected(self):
        (self.root / 'db/app.txt').write_text('new change\n')
        self.git('db', 'add', 'app.txt'); self.git('db', 'commit', '-m', 'unpublished change')
        with self.assertRaisesRegex(RuntimeError, 'not synchronized'):
            self.build()

    def test_preview_records_its_actual_branch(self):
        self.git('frontend', 'switch', '-c', 'codex/review')
        with tarfile.open(self.build(preview=True)) as t:
            manifest = json.load(t.extractfile('release-manifest.json'))
            self.assertTrue(manifest['preview'])
            self.assertEqual(manifest['sources'][0]['branch'], 'codex/review')

if __name__ == '__main__':
    unittest.main()
