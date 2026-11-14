"""Generate one Deep Sea Rescue hero with the established Seed3D pipeline.

Credentials are read from private stdin only. Re-running resumes the recorded task.
"""
from pathlib import Path
import importlib.util
import json
import os
import getpass

root = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('seed_pipeline', Path(__file__).with_name('generate_assets.py'))
pipeline = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pipeline)
pipeline.ASSETS = root / 'art/deep-sea-rescue/asset-source'
pipeline.NAMES = ['sea-turtle']
pipeline.ASSETS.mkdir(parents=True, exist_ok=True)
manifest = pipeline.ASSETS / 'seed3d-manifest.json'
if not manifest.exists():
    pipeline.save(manifest, dict(schema=1, model=pipeline.MODEL, source='Volcano Ark Seed3D API',
        endpoint_host='ark.cn-beijing.volces.com', assets={},
        notes='Original generated reference. Credentials, signed URLs and billing assertions excluded.'))
if not os.environ.get('ARK_API_KEY'):
    os.environ['ARK_API_KEY'] = getpass.getpass('ARK key (hidden, not saved): ').strip()
try:
    raise SystemExit(pipeline.main())
finally:
    os.environ.pop('ARK_API_KEY', None)
