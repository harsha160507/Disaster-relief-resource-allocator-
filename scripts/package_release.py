"""Assemble a verifiable deployment candidate using only standard-library tools."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def package(root, output, commit, run_id="", public_config=None):
    root = Path(root).resolve()
    output = Path(output).resolve()
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("An exact 40-character source commit is required.")
    jars = sorted((root / "backend/target").glob("disaster-relief-backend-*.jar"))
    if len(jars) != 1:
        raise ValueError("Expected exactly one executable backend JAR.")
    if not (root / "frontend/dist/index.html").is_file():
        raise ValueError("The production frontend index.html is missing.")
    if not any((root / "frontend/dist/assets").glob("*.js")):
        raise ValueError("The production frontend JavaScript is missing.")
    if not (root / "docs/release-readiness.md").is_file():
        raise ValueError("The release runbook is missing.")
    if not (root / "README.md").is_file():
        raise ValueError("The project README is missing.")
    entries = {"backend/application.jar": jars[0]}
    for folder, prefix in ((root / "frontend/dist", "frontend"),
                           (root / "docs", "docs")):
        for path in sorted(folder.rglob("*")):
            if path.is_symlink():
                raise ValueError(f"Symlinks are not allowed in the candidate: {path.name}")
            if path.is_file():
                entries[f"{prefix}/{path.relative_to(folder).as_posix()}"] = path
    entries["README.md"] = root / "README.md"
    # Read and validate all input before writing any release output.
    payload = {}
    for name, path in entries.items():
        if path.is_symlink() or not path.resolve().is_relative_to(root):
            raise ValueError("A release input points outside the checkout.")
        payload[name] = path.read_bytes()
    with zipfile.ZipFile(jars[0]) as jar:
        if not any(name.startswith("BOOT-INF/classes/") for name in jar.namelist()):
            raise ValueError("The backend JAR is not a Spring Boot executable.")
    config = public_config or {}
    manifest = {
        "project": "Disaster Relief Resource Allocator",
        "kind": "release-candidate",
        "source_commit": commit,
        "ci_run_id": str(run_id),
        "identity_configured": bool(config.get("authority") and config.get("client_id")),
        "frontend_public_config": config,
        "deployment_approved": False,
        "files": {name: {"sha256": digest(data), "bytes": len(data)}
                  for name, data in sorted(payload.items())},
    }
    manifest_bytes = (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode()
    output.mkdir(parents=True, exist_ok=True)
    archive = output / f"disaster-relief-{commit[:12]}.zip"
    # Stable member order and timestamps make the archive reproducible for identical inputs.
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as bundle:
        for name, data in sorted({**payload, "manifest.json": manifest_bytes}.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            bundle.writestr(info, data)
    (output / "SHA256SUMS").write_text(
        f"{digest(archive.read_bytes())}  {archive.name}\n", encoding="utf-8"
    )
    print(f"Created {archive.name} for commit {commit}")
    if not manifest["identity_configured"]:
        print("Candidate has no organization sign-in configuration; configure and rebuild before deployment.")
    return archive


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--run-id", default="")
    parser.add_argument("--output", type=Path, default=ROOT / "release")
    args = parser.parse_args()
    public = {
        "authority": os.environ.get("VITE_OIDC_AUTHORITY", ""),
        "client_id": os.environ.get("VITE_OIDC_CLIENT_ID", ""),
        "scope": os.environ.get("VITE_OIDC_SCOPE", "openid profile email"),
        "audience": os.environ.get("VITE_OIDC_AUDIENCE", ""),
        "api_base_url": "",
    }
    package(ROOT, args.output, args.commit, args.run_id, public)


if __name__ == "__main__":
    main()
