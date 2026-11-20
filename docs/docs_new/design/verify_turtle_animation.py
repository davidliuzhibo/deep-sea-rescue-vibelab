"""Sample actual exported skin matrices offline; not PICO rendering or frame-rate proof."""
from pathlib import Path
import json,struct,numpy as np
R=Path(__file__).resolve().parents[2];raw=(R/'art/deep-sea-rescue/runtime/turtle.glb').read_bytes();n=struct.unpack_from('<I',raw,12)[0];d=json.loads(raw[20:20+n]);blob=raw[28+n:]
def a(i):
 v=d['accessors'][i];b=d['bufferViews'][v['bufferView']];typ={5126:'<f4',5123:'<u2',5121:'u1',5125:'<u4'}[v['componentType']];dt=np.dtype(typ);w={'SCALAR':1,'VEC3':3,'VEC4':4,'MAT4':16}[v['type']]
 return np.ndarray((v['count'],w),dtype=dt,buffer=blob,offset=b.get('byteOffset',0)+v.get('byteOffset',0),strides=(b.get('byteStride',w*dt.itemsize),dt.itemsize)).copy()
def mat(n):
 if 'matrix' in n:return np.array(n['matrix']).reshape(4,4).T
 x,y,z,w=n.get('rotation',[0,0,0,1]);r=np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
 m=np.eye(4);m[:3,:3]=r@np.diag(n.get('scale',[1,1,1]));m[:3,3]=n.get('translation',[0,0,0]);return m
skin=d['skins'][0];ib=a(skin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1);anim=d['animations'][0]
meshNode=next(n for n in d['nodes'] if 'skin' in n);prims=[]
for p in d['meshes'][meshNode['mesh']]['primitives']:
 attrs=p['attributes'];pos=a(attrs['POSITION']);prims.append((np.c_[pos,np.ones(len(pos))],a(attrs['JOINTS_0']),a(attrs['WEIGHTS_0'])))
samples=[];first=None;maxMove=0
for t in np.linspace(0,4.5,37):
 nodes=[dict(n) for n in d['nodes']]
 for c in anim['channels']:
  sampler=anim['samplers'][c['sampler']];times=a(sampler['input']).ravel();values=a(sampler['output']);idx=min(len(times)-2,max(0,np.searchsorted(times,t,side='right')-1));f=float(np.clip((t-times[idx])/(times[idx+1]-times[idx]),0,1));v0=values[idx];v1=values[idx+1]
  if c['target']['path']=='rotation' and np.dot(v0,v1)<0:v1=-v1
  v=v0*(1-f)+v1*f
  if c['target']['path']=='rotation':v=v/np.linalg.norm(v)
  nodes[c['target']['node']][c['target']['path']]=v.tolist()
 globals={}
 def walk(i,parent):
  globals[i]=parent@mat(nodes[i])
  for child in nodes[i].get('children',[]):walk(child,globals[i])
 for i in d['scenes'][d.get('scene',0)]['nodes']:walk(i,np.eye(4))
 matrices=np.array([globals[j]@ib[k] for k,j in enumerate(skin['joints'])]);points=[]
 for pos,joints,weights in prims:
  p=np.zeros((len(pos),4))
  for k in range(4):p+=np.einsum('nij,nj->ni',matrices[joints[:,k]],pos)*weights[:,k:k+1]
  points.append(p[:,:3])
 points=np.concatenate(points);assert np.isfinite(points).all()
 if first is None:first=points.copy()
 maxMove=max(maxMove,float(np.max(np.linalg.norm(points-first,axis=1))))
 samples.append({'time':float(t),'min':points.min(axis=0).tolist(),'max':points.max(axis=0).tolist()})
lo=np.min([s['min'] for s in samples],axis=0);hi=np.max([s['max'] for s in samples],axis=0);endDelta=float(np.max(np.linalg.norm(points-first,axis=1)))
assert .002<maxMove<.14,(maxMove,lo,hi)
assert endDelta<.002,endDelta
checks={}
for name,pos in {'start':[-.10,-.16,-.68],'clear-left':[-.30,-.16,-.68],'lift':[-.30,.151,-.68],'farewell':[-.16,.151,-.88]}.items():
 low=lo+pos;high=hi+pos;checks[name]={'min':low.tolist(),'max':high.tolist(),'withinPortal':bool(np.all(low>=[-.75,-.45,-1.27]) and np.all(high<=[.75,.45,-.05]))}
result={'scope':'37 offline samples of exported glTF skin matrices, not native GPU','samples':samples,'maxVertexTravel':maxMove,'loopSeamDisplacement':endDelta,'bounds':[lo.tolist(),hi.tolist()],'poses':checks,'passed':all(v['withinPortal'] for v in checks.values())}
(R/'.scratch/deep-sea-rescue-20261105/turtle-animation-validation.json').write_bytes((json.dumps(result,indent=2)+'\n').encode())
print(json.dumps({k:v for k,v in result.items() if k!='samples'}));assert result['passed']
