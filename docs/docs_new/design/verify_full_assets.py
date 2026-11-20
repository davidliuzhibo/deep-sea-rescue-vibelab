"""Validate exported/packaged source data offline. Not native GPU or optical evidence."""
from pathlib import Path
import hashlib, io, json, math, struct, wave
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'demos/deep-sea-rescue/app/src/main/assets'
OUT=ROOT/'.scratch/deep-sea-rescue-full-20261105'
report={'scope':'exported GLB/PCM bytes and source bind-pose bounds only','nativeValidated':False,'models':{},'audio':{},'errors':[]}
types={5120:'i1',5121:'u1',5122:'<i2',5123:'<u2',5125:'<u4',5126:'<f4'}
widths={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}

def glb(path):
    raw=path.read_bytes();magic,version,length=struct.unpack_from('<4sII',raw)
    assert magic==b'glTF' and version==2 and length==len(raw)
    n,kind=struct.unpack_from('<II',raw,12);assert kind==0x4e4f534a
    doc=json.loads(raw[20:20+n]);offset=20+n
    size,kind=struct.unpack_from('<II',raw,offset);assert kind==0x004e4942
    blob=raw[offset+8:offset+8+size];assert len(blob)==size
    def accessor(i):
        a=doc['accessors'][i];assert 'sparse' not in a
        v=doc['bufferViews'][a['bufferView']];dtype=np.dtype(types[a['componentType']]);w=widths[a['type']]
        start=v.get('byteOffset',0)+a.get('byteOffset',0);stride=v.get('byteStride',w*dtype.itemsize)
        assert start+(a['count']-1)*stride+w*dtype.itemsize<=len(blob)
        arr=np.ndarray((a['count'],w),dtype=dtype,buffer=blob,offset=start,strides=(stride,dtype.itemsize)).copy()
        assert np.isfinite(arr).all()
        return arr
    def transform(n):
        if 'matrix' in n:return np.asarray(n['matrix']).reshape(4,4).T
        x,y,z,w=n.get('rotation',[0,0,0,1]);r=np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
        m=np.eye(4);m[:3,:3]=r@np.diag(n.get('scale',[1,1,1]));m[:3,3]=n.get('translation',[0,0,0]);return m
    points=[];triangles=0;primitives=0
    def node(i,parent):
        nonlocal triangles,primitives
        n=doc['nodes'][i];matrix=parent@transform(n)
        if 'mesh' in n:
            for p in doc['meshes'][n['mesh']]['primitives']:
                assert p.get('mode',4)==4
                v=accessor(p['attributes']['POSITION']);ind=accessor(p['indices']).ravel()
                assert len(ind)%3==0 and ind.min()>=0 and ind.max()<len(v)
                assert 'NORMAL' in p['attributes'];normal=accessor(p['attributes']['NORMAL']);assert np.max(np.linalg.norm(normal,axis=1))<1.01
                world=np.c_[v,np.ones(len(v))]@matrix.T;points.append(world[:,:3]);triangles+=len(ind)//3;primitives+=1
                if 'skin' in n:
                    joints=accessor(p['attributes']['JOINTS_0']);weights=accessor(p['attributes']['WEIGHTS_0'])
                    assert joints.max()<len(doc['skins'][n['skin']]['joints'])
                    assert np.abs(weights.sum(axis=1)-1).max()<.002
        for c in n.get('children',[]):node(c,matrix)
    for i in doc['scenes'][doc.get('scene',0)]['nodes']:node(i,np.eye(4))
    images=[]
    for image in doc.get('images',[]):
        assert 'uri' not in image,'External image not allowed'
        v=doc['bufferViews'][image['bufferView']];start=v.get('byteOffset',0)
        with Image.open(io.BytesIO(blob[start:start+v['byteLength']])) as im:
            assert max(im.size)<=2048;im.verify();images.append(list(im.size))
    anim=[]
    for a in doc.get('animations',[]):
        duration=max(float(accessor(s['input']).max()) for s in a['samplers'])
        assert duration>0 and a['channels'];anim.append({'name':a.get('name'),'duration':duration,'channels':len(a['channels'])})
        for s in a['samplers']:
            times=accessor(s['input']).ravel();assert np.all(np.diff(times)>0);accessor(s['output'])
    p=np.concatenate(points)
    return {'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest(),'triangles':triangles,'primitives':primitives,'embeddedImages':images,'skins':len(doc.get('skins',[])),'animations':anim,'bindPoseBounds':[p.min(axis=0).tolist(),p.max(axis=0).tolist()]}

try:
    for path in sorted((ASSETS/'models').glob('*.glb')):
        row=glb(path);report['models'][path.stem]=row
        assert path.read_bytes()==((ROOT/'art/deep-sea-rescue/full-runtime'/path.name) if (ROOT/'art/deep-sea-rescue/full-runtime'/path.name).exists() else (ROOT/'art/deep-sea-rescue/runtime'/path.name)).read_bytes()
    assert len(report['models'])==23
    turtle=report['models']['turtle'];assert turtle['skins']>=1 and turtle['animations'];assert turtle['triangles']==100000
    frame=np.array(report['models']['frame']['bindPoseBounds']);assert np.all((frame[1]-frame[0])[:2]<[1.62,1.02])
    # This includes the deliberately enclosing water box and floor, not only props.
    reef=np.array(report['models']['reef']['bindPoseBounds']);assert reef[0,0]>=-.761 and reef[1,0]<=.761
    assert reef[0,1]>=-.471 and reef[1,1]<=.471 and reef[0,2]>=-1.331 and reef[1,2]<=.001
    # Check the visible turtle envelope over start, lift and complete farewell; animations are separate.
    bounds=np.array(turtle['bindPoseBounds'])
    report['turtlePoses']={}
    for name,position in {'start':[-.10,-.16,-.68],'clear-left':[-.30,-.16,-.68],'lift':[-.30,.151,-.68],'farewell':[-.16,.151,-.88]}.items():
        b=bounds+position;report['turtlePoses'][name]=b.tolist()
        assert np.all(b[0]>=[-.75,-.45,-1.27]) and np.all(b[1]<=[.75,.45,-.05]),(name,b)
    for path in sorted((ASSETS/'audio').glob('*.wav')):
        with wave.open(str(path),'rb') as w:
            assert w.getnchannels()==1 and w.getsampwidth()==2 and w.getframerate()==24000
            frames=w.readframes(w.getnframes());samples=np.frombuffer(frames,dtype='<i2')/32768.;duration=w.getnframes()/24000
        peak=float(np.max(abs(samples)));rms=float(np.sqrt(np.mean(samples*samples)))
        assert .001<peak<.55 and rms>.00001
        report['audio'][path.name]={'seconds':duration,'peak':peak,'rms':rms,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'listened':False}
    assert len(report['audio'])==8
    assert (ASSETS/'design/design-spec.json').read_bytes()==(ROOT/'demos/deep-sea-rescue/.scratch/design-spec.json').read_bytes()
    report['passed']=True
except Exception as e:
    report['passed']=False;report['errors'].append(repr(e))
finally:
    (OUT/'asset-validation.json').write_bytes((json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode())
    print(json.dumps({'passed':report['passed'],'models':len(report['models']),'triangles':sum(x['triangles'] for x in report['models'].values()),'audio':len(report['audio']),'errors':report['errors']},ensure_ascii=False))
raise SystemExit(0 if report['passed'] else 1)
