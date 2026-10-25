"""Check that the packaged application starts and refuses anonymous API access."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def main():
    jars = sorted((ROOT / "backend/target").glob("disaster-relief-backend-*.jar"))
    if len(jars) != 1:
        raise SystemExit("Expected exactly one executable backend JAR.")
    for name in ("DB_URL", "DB_USERNAME", "DB_PASSWORD"):
        if not os.environ.get(name):
            raise SystemExit(f"{name} must point to a disposable smoke-test database.")
    java = shutil.which("java")
    if not java:
        raise SystemExit("Java is required.")
    # Override inherited identity/bootstrap settings: this smoke test is intentionally
    # unauthenticated. Signed-token workflows are covered by the integration suite.
    environment = {**os.environ, "SECURITY_BOOTSTRAP_ENABLED": "false"}
    for key in ("SECURITY_OAUTH2_ISSUER_URI", "SECURITY_OAUTH2_AUDIENCE",
                "SECURITY_OAUTH2_JWK_SET_URI"):
        environment.pop(key, None)
    with tempfile.TemporaryFile(mode="w+b") as log:
        process = subprocess.Popen(
            [java, "-jar", str(jars[0]), "--server.address=127.0.0.1",
             "--server.port=18080"],
            cwd=ROOT / "backend", env=environment, stdout=log, stderr=log,
        )
        try:
            deadline = time.monotonic() + 120
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("Packaged application exited before becoming ready.")
                try:
                    with urllib.request.urlopen(
                        "http://127.0.0.1:18080/api/v1/me", timeout=2
                    ) as response:
                        raise RuntimeError(f"Anonymous API unexpectedly returned {response.status}.")
                except urllib.error.HTTPError as error:
                    if error.code != 401:
                        raise RuntimeError(f"Expected 401, received {error.code}.") from error
                    body = json.loads(error.read())
                    if body.get("status") != 401:
                        raise RuntimeError("API did not return the expected structured 401.")
                    print("Executable JAR started, migrations validated, anonymous API returned 401.")
                    return
                except (urllib.error.URLError, TimeoutError):
                    time.sleep(1)
            raise RuntimeError("Packaged application did not become ready within 120 seconds.")
        except Exception:
            log.seek(0)
            print(log.read().decode("utf-8", errors="replace")[-12000:])
            raise
        finally:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)


if __name__ == "__main__":
    main()
