"""Read-only GLB structure audit; does not require a rendering context."""
import argparse
import json
from pathlib import Path
import struct


def inspect(path):
    raw = Path(path).read_bytes()
    magic, version, size = struct.unpack_from('<4sII', raw)
    if magic != b'glTF' or version != 2 or size != len(raw):
        raise ValueError('Invalid GLB header')
    offset = 12
    doc = None
    while offset < len(raw):
        length, kind = struct.unpack_from('<II', raw, offset)
        offset += 8
        if kind == 0x4E4F534A:
            doc = json.loads(raw[offset:offset+length])
        offset += length
    if not doc:
        raise ValueError('Missing glTF JSON')
    triangles = vertices = 0
    bounds = []
    for mesh in doc.get('meshes', []):
        for prim in mesh.get('primitives', []):
            pos = doc['accessors'][prim['attributes']['POSITION']]
            vertices += pos['count']
            indices = doc['accessors'][prim['indices']]['count'] if 'indices' in prim else pos['count']
            if prim.get('mode', 4) == 4:
                triangles += indices // 3
            bounds.append({'min': pos.get('min'), 'max': pos.get('max')})
    return {
        'path': str(Path(path).resolve()), 'bytes': len(raw), 'meshes': len(doc.get('meshes', [])),
        'nodes': len(doc.get('nodes', [])), 'materials': doc.get('materials', []),
        'images': [{k: v for k,v in im.items() if k != 'uri'} for im in doc.get('images', [])],
        'textures': len(doc.get('textures', [])), 'triangles': triangles, 'vertices': vertices,
        'local_accessor_bounds': bounds, 'extensions_used': doc.get('extensionsUsed', []),
        'extensions_required': doc.get('extensionsRequired', []),
    }


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('paths', nargs='+')
    p.add_argument('--output')
    args = p.parse_args()
    result = [inspect(path) for path in args.paths]
    value = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        Path(args.output).write_bytes((value+'\n').encode())
    else:
        print(value)
