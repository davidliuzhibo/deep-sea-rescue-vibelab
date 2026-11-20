"""Compare actual exported GLB/skin bounds with production route constants offline."""
from pathlib import Path
import json, re
import numpy as np
R=Path(__file__).resolve().parents[2];E=R/'.scratch/deep-sea-rescue-full-20261105'
src=(R/'demos/deep-sea-rescue/app/src/main/java/com/example/deepsearescue/pcd0b39b0/domain/model/Campaign.kt').read_text(encoding='utf8')
art=json.loads((E/'asset-validation.json').read_text(encoding='utf8'))
anim=json.loads((R/'.scratch/deep-sea-rescue-20261105/turtle-animation-validation.json').read_text(encoding='utf8'))
report={'scope':'Offline sampled skin/GLB bounds and swept-path clearance; not native rendering or physics-engine acceptance','errors':[]}
def v(s):return np.array([float(x) for x in s.split(',')])
def constant(n):return v(re.search(r'val '+n+r'=V3\(([^)]+)\)',src).group(1))
try:
 lo,hi=constant('escortMin'),constant('escortMax')
 al,ah=np.array(anim['bounds'])*.35
 assert np.all(lo<=al) and np.all(hi>=ah),'proxy misses actual animated fin/limb envelope'
 report['proxy']={'min':lo.tolist(),'max':hi.tolist(),'sampledMin':al.tolist(),'sampledMax':ah.tolist()}
 # Same hero bytes as the immutable slice whose skin samples were measured.
 import zipfile,hashlib
 with zipfile.ZipFile(R/'releases/deep-sea-rescue-20261105/deep-sea-rescue-0.1.0-turtle-rc1-code1.apk') as z:
  assert hashlib.sha256(z.read('assets/models/turtle.glb')).hexdigest()==art['models']['turtle']['sha256']
 blocks=[]
 for center,half,label,ground in re.findall(r'Block\(V3\(([^)]+)\),V3\(([^)]+)\),"([^"]+)"(,true)?\)',src):
  c,h=v(center),v(half);lower=c-h
  if ground:lower[1]=-.42
  blocks.append((lower,c+h))
 assert len(blocks)==3
 rock=np.array(art['models']['route_rock']['bindPoseBounds'])
 for i,c in enumerate([np.array([.23,.02,-.46]),np.array([-.23,.09,-.78])]):
  scale=np.array([1,(c[1]+.40)/.42,1]);b=rock*scale+c
  # A buried foot may continue 5mm into the floor; it is not in the navigable volume.
  assert np.all(b[0]>=blocks[i][0]-.005) and np.all(b[1]<=blocks[i][1]),('rock',i,b)
 ys=[.02,.09,.17];zs=[-.46,-.78,-1.10];checkpoint=constant('escortStart');count=0;routes=[]
 for i,lane in enumerate([-1,1,-1]):
  goal=np.array([lane*.28,ys[i],zs[i]])
  targets=[np.array([checkpoint[0],goal[1],goal[2]+.17]),goal+np.array([0,0,.17]),goal]
  start=checkpoint.copy()
  for target in targets:
   for t in np.linspace(0,1,max(2,int(np.linalg.norm(target-start)/.002)+1)):
    p=start*(1-t)+target*t;low,high=p+lo,p+hi
    assert np.all(low>=[-.75,-.45,-1.27]) and np.all(high<=[.75,.45,-.05]),('portal',i,p)
    assert not any(np.all(high>a) and np.all(low<b) for a,b in blocks),('obstacle',i,p)
    count+=1
   start=target.copy()
  routes.append({'segment':i,'start':checkpoint.tolist(),'legs':[p.tolist() for p in targets]});checkpoint=goal
 ending=checkpoint+np.array([-.07,0,-.04]);assert np.all(ending+lo>=[-.75,-.45,-1.27]) and np.all(ending+hi<=[.75,.45,-.05])
 # Raised load is deliberately level and cannot be re-inflated away from its cradle.
 cage=np.array(art['models']['cage']['bindPoseBounds']);bag=np.array(art['models']['float_bag']['bindPoseBounds'])
 checks=0
 for height in [0,.07,.14,.21,.28]:
  for offset in [0,.08,.16,.24,.32,.40]:
   for left,right in [(2,3),(3,2)]:
    at=np.array([offset,-.18+height,-.48]);pieces=[cage+at]
    for x,air in [(-.2,left),(.2,right)]:pieces.append(bag*(.5+air*.15)+at+np.array([x,.23,0]))
    for b in pieces:
     assert np.all(b[0]>=[-.75,-.45,-1.27]) and np.all(b[1]<=[.75,.45,-.05]),('load',height,offset,b)
    checks+=1
 report.update(passed=True,routeSamples=count,safeRoute=routes,loadConfigurations=checks)
except Exception as e:report.update(passed=False);report['errors'].append(repr(e))
(E/'geometry-validation.json').write_bytes((json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode())
print(json.dumps({k:v for k,v in report.items() if k not in ['proxy','safeRoute']},ensure_ascii=False))
raise SystemExit(0 if report['passed']else 1)
