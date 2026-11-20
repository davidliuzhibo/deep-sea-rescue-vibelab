"""Immutable full-campaign background handoff. No device/network/screen operations."""
from pathlib import Path
from datetime import datetime,timezone
import json,zipfile
from verify_full_delivery import ROOT,PROJECT,OUT,project_files,sha
DEST=ROOT/'releases/deep-sea-rescue-full-20261105';DOC=ROOT/'docs/deep-sea-rescue'
report=json.loads((OUT/'delivery-verification.json').read_text(encoding='utf8'))
assert report['passed'] and report['jvmTotal']==107
assert report['sourceFiles']=={p.relative_to(PROJECT).as_posix():sha(p.read_bytes()) for p in project_files()}
for n in ['asset-validation.json','geometry-validation.json','design-web-check.json']:
 assert json.loads((OUT/n).read_text(encoding='utf8'))['passed'],n
assert not DEST.exists(),'Never replace an earlier delivery; choose a new revision'
DEST.mkdir(parents=True)
def put(p,n):
 target=DEST/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(p.read_bytes())
for variant,row in report['apks'].items():
 p=Path(row['path']);assert sha(p.read_bytes())==row['sha256']
 put(p,'deep-sea-rescue-1.0.0-full-rc1-code2.apk' if variant=='release'else f'validation/deep-sea-rescue-code2-{variant}.apk')
for n in ['TEST-REPORT.md','FIRST-NATIVE-SESSION.md','LESSONS-APPLICATION.md','design-doc.md']:put(DOC/n,n)
put(PROJECT/'README.md','README.md')
for i in [1,2,3]:put(OUT/f'mission-{i}-offline.png',f'preview/OFFLINE-mission-{i}.png')
put(ROOT/'art/deep-sea-rescue/runtime/renders/rescue-bay-offline.png','preview/OFFLINE-mission-0.png')
put(ROOT/'art/deep-sea-rescue/icon/icon-1024.png','preview/icon-1024.png')
for n in ['design-spec.json','preview.html','design_implementation_map.json']:put(PROJECT/'.scratch'/n,'design/'+n)
for n in ['delivery-verification.json','asset-validation.json','geometry-validation.json','route-envelope-before-fix.json','design-web-check.json','design-style-final.txt','placement-source-final.json','fidelity-final.txt','architecture-final.txt','managed-limitations.json','build-final-verified.log','SESSION-STATE.json']:
 put(OUT/n,'evidence/'+n)
for p in OUT.glob('apk-*.txt'):put(p,'evidence/'+p.name)
for n in ['architecture_check_result.json','design_fidelity_result.json']:put(PROJECT/'.scratch'/n,'evidence/'+n)
put(PROJECT/'app/build/reports/lint-results-debug.txt','evidence/lint-results-debug.txt')
put(ROOT/'.scratch/deep-sea-rescue-20261105/turtle-animation-validation.json','evidence/unchanged-turtle-animation-samples.json')
put(ROOT/'art/deep-sea-rescue/full-runtime/asset-manifest.json','evidence/new-equipment-manifest.json')
put(ROOT/'art/deep-sea-rescue/asset-source/seed3d-manifest.json','evidence/seed3d-provenance.json')
for p in (PROJECT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):put(p,'evidence/jvm/'+p.name)
for n in ['build-test-01.log','route-fix-tests.log','suspended-load-before-fix.log','build-final.log']:
 put(OUT/n,'evidence/historical-failures/'+n)
for p in (OUT/'before-scene-polish').glob('*'):put(p,'evidence/before-scene-polish/'+p.name)

archive=DEST/'deep-sea-rescue-1.0.0-full-rc1-code2-source.zip'
with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in project_files():z.write(p,'deep-sea-rescue/'+p.relative_to(PROJECT).as_posix())
 for n in ['design-spec.json','preview.html','design_implementation_map.json']:z.write(PROJECT/'.scratch'/n,'design/'+n)
 for n in ['TEST-REPORT.md','FIRST-NATIVE-SESSION.md','LESSONS-APPLICATION.md','design-doc.md']:z.write(DOC/n,'handoff/'+n)
 z.writestr('source-sha256.json',json.dumps(report['sourceFiles'],ensure_ascii=False,indent=2))
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None
 for n,h in report['sourceFiles'].items():assert sha(z.read('deep-sea-rescue/'+n))==h
 assert all(not any(v in {'local.properties','.gradle','build','.git'} for v in Path(n).parts) for n in z.namelist())

manifest={'schema':1,'createdAt':datetime.now(timezone.utc).isoformat(),'project':'Deep Sea Rescue / 深海救援站','versionName':'1.0.0-full-rc1','versionCode':2,'designRevision':6,'package':'com.example.deepsearescue.pcd0b39b0','scope':'Four-mission background implementation and offline validation; 15–25 minute first-player target unmeasured','nativeValidated':False,'headsetValidated':False,'businessServer':None,'signing':'Android Debug internal candidate; not store signing acceptance','managedWorkflow':'App/Editor/codegen failed with preserved public evidence; independent direct Gradle pass','testCounts':{'jvmPassed':107,'androidTestsCompiledOnly':3,'webLayouts':90,'sourceIrReferences':531,'routeGeometrySamples':1139,'loadConfigurations':60},'sourceRoot':str(PROJECT),'artSourceRoot':str(ROOT/'art/deep-sea-rescue'),'previousDeliveryPreserved':str(ROOT/'releases/deep-sea-rescue-20261105'),'files':{p.relative_to(DEST).as_posix():{'sha256':sha(p.read_bytes()),'bytes':p.stat().st_size} for p in sorted(DEST.rglob('*')) if p.is_file()}}
(DEST/'manifest.json').write_bytes((json.dumps(manifest,ensure_ascii=False,indent=2)+'\n').encode())
(DEST/'SHA256SUMS.txt').write_bytes(''.join(f"{v['sha256']}  {n}\n" for n,v in manifest['files'].items()).encode())
for n,v in manifest['files'].items():assert sha((DEST/n).read_bytes())==v['sha256']
print(json.dumps({'directory':str(DEST),'files':len(manifest['files']),'sourceFilesVerified':len(report['sourceFiles']),'release':manifest['files']['deep-sea-rescue-1.0.0-full-rc1-code2.apk']},ensure_ascii=False))
