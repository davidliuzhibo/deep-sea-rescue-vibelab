"""Read-only offline APK/source verification. Does not connect to a device."""
from pathlib import Path
import hashlib
import json
import os
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "demos/deep-sea-rescue"
OUT = ROOT / ".scratch/deep-sea-rescue-20261105"
TOOLS = Path("C:/Users/david/AppData/Local/Android/Sdk/build-tools/35.0.0")
ENV = dict(os.environ, JAVA_HOME=str(ROOT / ".tools/jdk21/jdk-21.0.12.1+1"))

def sha(data):
    return hashlib.sha256(data).hexdigest()

def command(args):
    p = subprocess.run(args, env=ENV, capture_output=True)
    assert p.returncode == 0, (Path(args[0]).name, p.returncode)
    return p.stdout.decode("utf-8", errors="replace")

def project_files():
    roots = [PROJECT / "app/src", PROJECT / "gradle"]
    files = [p for d in roots for p in d.rglob("*") if p.is_file()]
    files += [PROJECT / s for s in (
        "README.md", "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
        "gradlew", "gradlew.bat", "app/build.gradle.kts", "app/proguard-rules.pro")]
    return sorted(files)

def verify():
    report = {"scope": "Offline only; no Android instrumentation or native/optical acceptance", "apks": {}, "tests": [], "errors": []}
    assets = PROJECT / "app/src/main/assets"
    expected = json.loads((PROJECT / "app/src/androidTest/assets/expected-assets.json").read_text(encoding="utf8"))
    assert expected == {p.relative_to(assets).as_posix(): sha(p.read_bytes()) for p in assets.rglob("*") if p.is_file()}
    for variant, suffix in [("release", "release/app-release.apk"), ("debug", "debug/app-debug.apk"), ("androidTest", "androidTest/debug/app-debug-androidTest.apk")]:
        p = PROJECT / "app/build/outputs/apk" / suffix
        badging = command([str(TOOLS / "aapt2.exe"), "dump", "badging", str(p)])
        signature = command([str(TOOLS / "apksigner.bat"), "verify", "--verbose", "--print-certs", str(p)])
        (OUT / f"apk-{variant}-badging.txt").write_bytes(badging.encode())
        (OUT / f"apk-{variant}-signature.txt").write_bytes(signature.encode())
        assert "Verifies" in signature and "CN=Android Debug" in signature
        if variant != "androidTest":
            assert "name='com.example.deepsearescue.pcd0b39b0'" in badging
            assert "versionCode='1'" in badging and "versionName='0.1.0-turtle-rc1'" in badging
            assert "android.permission.INTERNET" not in badging
        with zipfile.ZipFile(p) as z:
            if variant != "androidTest":
                for name, digest in expected.items():
                    assert sha(z.read("assets/" + name)) == digest, (variant, name)
                    if name.endswith(".wav"):
                        assert z.getinfo("assets/" + name).compress_type == zipfile.ZIP_STORED
            else:
                assert json.loads(z.read("assets/expected-assets.json")) == expected
        report["apks"][variant] = {"path": str(p), "bytes": p.stat().st_size, "sha256": sha(p.read_bytes()), "signature": "verified Android Debug internal candidate", "assetHashesMatched": len(expected) if variant != "androidTest" else None}
    for p in sorted((PROJECT / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")):
        t = ET.parse(p).getroot()
        row = {"suite": t.get("name"), **{k: int(t.get(k, "0")) for k in ["tests", "failures", "errors", "skipped"]}}
        assert row["failures"] == row["errors"] == row["skipped"] == 0
        report["tests"].append(row)
    report["jvmTotal"] = sum(t["tests"] for t in report["tests"])
    assert report["jvmTotal"] == 84
    lint = ET.parse(PROJECT / "app/build/reports/lint-results-debug.xml").getroot()
    counts = {}
    for issue in lint.findall("issue"):
        key = issue.get("severity") + ":" + issue.get("id")
        counts[key] = counts.get(key, 0) + 1
    assert not any(k.startswith(("Error:", "Fatal:")) for k in counts), counts
    report["lint"] = counts
    report["sourceFiles"] = {p.relative_to(PROJECT).as_posix(): sha(p.read_bytes()) for p in project_files()}
    # Findings deliberately contain paths/rule names only, never matched secrets.
    patterns = {
        "private-key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        "provider-token": re.compile(rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|sk-[A-Za-z0-9_-]{30,})\b"),
        "literal-secret": re.compile(rb"(?i)(?:api[_-]?key|access[_-]?token|client[_-]?secret)\s*[=:]\s*[\"'][A-Za-z0-9_+/=.-]{16,}[\"']"),
    }
    findings = []
    scanned = 0
    for p in project_files():
        if p.suffix.lower() not in {".kt", ".kts", ".json", ".md", ".xml", ".toml", ".properties", ".bat"}:
            continue
        scanned += 1
        data = p.read_bytes()
        for name, pattern in patterns.items():
            if pattern.search(data): findings.append({"path": p.relative_to(PROJECT).as_posix(), "rule": name})
    report["commonCredentialScan"] = {"textFiles": scanned, "findings": findings, "scope": "Common patterns only; not a guarantee of absence of every possible secret"}
    assert not findings, findings
    report["passed"] = True
    (OUT / "delivery-verification.json").write_bytes((json.dumps(report, ensure_ascii=False, indent=2) + "\n").encode())
    print(json.dumps({"passed": True, "jvm": report["jvmTotal"], "apks": len(report["apks"]), "assets": len(expected), "sourceFiles": len(report["sourceFiles"]), "lintIssues": sum(counts.values()), "credentialPatternFindings": len(findings)}, ensure_ascii=False))
    return report

if __name__ == "__main__":
    verify()
