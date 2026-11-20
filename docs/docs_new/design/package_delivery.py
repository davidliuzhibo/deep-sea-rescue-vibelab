"""Package only the offline-verified candidate, preserving previous artifacts."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import shutil
import zipfile
from verify_delivery import ROOT, PROJECT, OUT, project_files, sha

DEST = ROOT / "releases/deep-sea-rescue-20261105"
DOC = ROOT / "docs/deep-sea-rescue"
report = json.loads((OUT / "delivery-verification.json").read_text(encoding="utf8"))
assert report["passed"]
assert report["sourceFiles"] == {p.relative_to(PROJECT).as_posix(): sha(p.read_bytes()) for p in project_files()}, "Source changed after verification"

def put(source, relative):
    target = DEST / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    data = source.read_bytes()
    if target.exists():
        assert target.read_bytes() == data, f"Refusing to replace previous delivery {relative}"
    else:
        target.write_bytes(data)
    return target

for variant, row in report["apks"].items():
    p = Path(row["path"])
    assert sha(p.read_bytes()) == row["sha256"], "APK changed after verification"
    rel = "deep-sea-rescue-0.1.0-turtle-rc1-code1.apk" if variant == "release" else f"validation/deep-sea-rescue-code1-{variant}.apk"
    put(p, rel)
for name in ["TEST-REPORT.md", "FIRST-NATIVE-SESSION.md", "LESSONS-APPLICATION.md", "design-doc.md"]:
    put(DOC / name, name)
put(PROJECT / "README.md", "README.md")
put(ROOT / "art/deep-sea-rescue/runtime/renders/rescue-bay-offline.png", "preview/OFFLINE-art-composition.png")
put(ROOT / "art/deep-sea-rescue/icon/icon-1024.png", "preview/icon-1024.png")
put(ROOT / "art/deep-sea-rescue/asset-source/seed3d-manifest.json", "evidence/seed3d-provenance.json")
put(ROOT / "art/deep-sea-rescue/runtime/asset-manifest.json", "evidence/asset-manifest.json")
for name in ["delivery-verification.json", "asset-validation.json", "turtle-animation-validation.json", "design-web-check.json", "design-style-final.txt", "lint-fresh.log", "codegen-final-read.json", "codegen-final-events.json", "app-workflow-limitations.json", "editor-limitations.json"]:
    put(OUT / name, "evidence/" + name)
for p in OUT.glob("apk-*.txt"):
    put(p, "evidence/" + p.name)
for name in ["architecture_check_result.json", "design_fidelity_result.json", "design_escalation_receipt.json"]:
    put(PROJECT / ".scratch" / name, "evidence/" + name)
put(PROJECT / "app/build/reports/lint-results-debug.txt", "evidence/lint-results-debug.txt")
for p in (PROJECT / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
    put(p, "evidence/jvm/" + p.name)
for name in ["build-final.log", "build-final-verified.log", "test-03.log", "lint-before-local-properties-fix.txt", "architecture-01.txt"]:
    put(OUT / name, "evidence/historical-failures/" + name)

archive = DEST / "deep-sea-rescue-0.1.0-turtle-rc1-code1-source.zip"
assert not archive.exists(), "Keep previous archive; use a new delivery revision"
with zipfile.ZipFile(archive, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for p in project_files():
        z.write(p, "deep-sea-rescue/" + p.relative_to(PROJECT).as_posix())
    for name in ["design-spec.json", "design-doc.md", "design_implementation_map.json", "preview.html"]:
        z.write(PROJECT / ".scratch" / name, "design/" + name)
    for p in [DOC / n for n in ["TEST-REPORT.md", "FIRST-NATIVE-SESSION.md", "LESSONS-APPLICATION.md"]]:
        z.write(p, "handoff/" + p.name)
    z.writestr("source-sha256.json", json.dumps(report["sourceFiles"], ensure_ascii=False, indent=2))
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    for name, digest in report["sourceFiles"].items():
        assert sha(z.read("deep-sea-rescue/" + name)) == digest
    assert all(not any(part in {"local.properties", ".gradle", "build", ".git"} for part in Path(n).parts) for n in z.namelist())

manifest = {
    "schema": 1,
    "createdAt": datetime.now(timezone.utc).isoformat(),
    "project": "Deep Sea Rescue / 深海救援站",
    "versionName": "0.1.0-turtle-rc1", "versionCode": 1,
    "package": "com.example.deepsearescue.pcd0b39b0",
    "scope": "3–5 minute turtle rescue slice; offline implementation/testing candidate; duration not player-validated",
    "nativeValidated": False, "headsetValidated": False, "businessServer": None,
    "managedWorkflow": "blocked validate-delivery; independent direct build and offline checks passed",
    "testCounts": {"jvmPassed": 84, "androidTestsCompiledOnly": 3, "webLayoutCombinations": 54},
    "signing": "Local Android Debug; internal validation candidate",
    "sourceRoot": str(PROJECT), "artSourceRoot": str(ROOT / "art/deep-sea-rescue"),
    "files": {p.relative_to(DEST).as_posix(): {"sha256": sha(p.read_bytes()), "bytes": p.stat().st_size} for p in sorted(DEST.rglob("*")) if p.is_file()},
}
(DEST / "manifest.json").write_bytes((json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode())
(DEST / "SHA256SUMS.txt").write_bytes(("".join(f"{row['sha256']}  {name}\n" for name, row in manifest["files"].items())).encode())
print(json.dumps({"directory": str(DEST), "files": len(manifest["files"]), "sourceFilesVerified": len(report["sourceFiles"]), "release": manifest["files"]["deep-sea-rescue-0.1.0-turtle-rc1-code1.apk"]}, ensure_ascii=False))
