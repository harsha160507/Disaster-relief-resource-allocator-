import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location(
    "package_release", Path(__file__).resolve().parents[1] / "package_release.py"
)
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class ReleasePackageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for folder in ("backend/target", "frontend/dist/assets", "docs"):
            (self.root / folder).mkdir(parents=True)
        with zipfile.ZipFile(self.root / "backend/target/disaster-relief-backend-test.jar", "w") as jar:
            jar.writestr("BOOT-INF/classes/App.class", b"class")
        (self.root / "frontend/dist/index.html").write_text("<html>UI</html>")
        (self.root / "frontend/dist/assets/app.js").write_text("console.log('ready')")
        (self.root / "docs/release-readiness.md").write_text("Runbook")
        (self.root / "README.md").write_text("Project")
        self.commit = "a" * 40
        self.output = self.root / "release"

    def test_manifest_tracks_exact_files_and_archive_checksum(self):
        archive = release.package(self.root, self.output, self.commit, "123")
        expected = hashlib.sha256(archive.read_bytes()).hexdigest()
        self.assertEqual((self.output / "SHA256SUMS").read_text(),
                         f"{expected}  {archive.name}\n")
        with zipfile.ZipFile(archive) as bundle:
            manifest = json.loads(bundle.read("manifest.json"))
            self.assertEqual(manifest["source_commit"], self.commit)
            self.assertEqual(manifest["ci_run_id"], "123")
            self.assertFalse(manifest["identity_configured"])
            self.assertFalse(manifest["deployment_approved"])
            self.assertIn("backend/application.jar", manifest["files"])
            self.assertIn("frontend/index.html", manifest["files"])
            for name, metadata in manifest["files"].items():
                data = bundle.read(name)
                self.assertEqual(metadata["sha256"], hashlib.sha256(data).hexdigest())
                self.assertEqual(metadata["bytes"], len(data))

    def test_same_inputs_produce_same_archive_and_public_config_is_recorded(self):
        config = {"authority": "https://identity.example", "client_id": "public-spa"}
        first = release.package(self.root, self.output, self.commit, "123", config).read_bytes()
        second = release.package(self.root, self.output, self.commit, "123", config).read_bytes()
        self.assertEqual(first, second)
        with zipfile.ZipFile(self.output / f"disaster-relief-{self.commit[:12]}.zip") as bundle:
            self.assertTrue(json.loads(bundle.read("manifest.json"))["identity_configured"])

    def test_missing_frontend_fails_without_creating_candidate(self):
        (self.root / "frontend/dist/index.html").unlink()
        with self.assertRaisesRegex(ValueError, "index.html"):
            release.package(self.root, self.output, self.commit)
        self.assertFalse(self.output.exists())

    def test_ambiguous_backend_fails_without_creating_candidate(self):
        (self.root / "backend/target/disaster-relief-backend-other.jar").write_bytes(b"other")
        with self.assertRaisesRegex(ValueError, "exactly one"):
            release.package(self.root, self.output, self.commit)
        self.assertFalse(self.output.exists())

    def test_invalid_commit_and_non_executable_jar_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "source commit"):
            release.package(self.root, self.output, "main")
        with zipfile.ZipFile(self.root / "backend/target/disaster-relief-backend-test.jar", "w") as jar:
            jar.writestr("plain.txt", "not executable")
        with self.assertRaisesRegex(ValueError, "Spring Boot"):
            release.package(self.root, self.output, self.commit)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
