"""Generate the three Lost Star Observatory source assets with Seed3D.

The key is accepted only from ARK_API_KEY or a non-echoing terminal prompt.
No credential, image request payload, or signed download URL is persisted.
"""
from __future__ import annotations

import argparse
import base64
import getpass
import hashlib
import json
import os
from pathlib import Path
import time
from urllib.parse import urlparse
import zipfile

import requests

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'demos/lost-star-observatory/asset-source'
API = 'https://ark.cn-beijing.volces.com/api/v3/contents/generations/tasks'
MODEL = 'doubao-seed3d-2-0-260328'
NAMES = ['bronze-mirror', 'star-instrument-base', 'archive-cabinet']


def save(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))


def clean_response(data: dict) -> dict:
    copy = dict(data)
    if isinstance(copy.get('content'), dict):
        copy['content'] = {k: v for k, v in copy['content'].items() if k != 'file_url'}
        copy['content']['file_url_present'] = bool(data['content'].get('file_url'))
    return copy


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--resume', action='store_true', help='Only resume recorded tasks; never creates replacements.')
    parser.add_argument('--timeout-minutes', type=int, default=40)
    args = parser.parse_args()
    key = os.environ.get('ARK_API_KEY') or getpass.getpass('ARK_API_KEY (hidden; not saved): ')
    if not key:
        raise SystemExit('No API key provided.')
    session = requests.Session()
    session.headers.update({'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
    manifest_path = ASSETS / 'seed3d-manifest.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8')) if manifest_path.exists() else {
        'schema': 1, 'model': MODEL, 'source': 'Volcano Ark Seed3D API',
        'endpoint_host': 'ark.cn-beijing.volces.com',
        'public_unit_price_cny_at_research': 2.40,
        'notes': 'Cost estimate only; no account billing assertion. Credentials and signed URLs excluded.',
        'assets': {},
    }
    for name in NAMES:
        if name in manifest['assets']:
            continue
        if args.resume:
            print(f'{name}: not recorded; resume will not create.', flush=True)
            continue
        image = ASSETS / 'reference' / (name + '.png')
        payload = {
            'model': MODEL,
            'content': [{'type': 'text', 'text': '--subdivisionlevel low --fileformat glb'}, {'type': 'image_url', 'image_url': {
                'url': 'data:image/png;base64,' + base64.b64encode(image.read_bytes()).decode('ascii')
            }}],
            'subdivision_level': 'low', 'file_format': 'glb',
        }
        response = session.post(API, json=payload, timeout=(20, 120))
        if not response.ok:
            message = response.text.replace(key, '[REDACTED]')[:2000]
            save(ASSETS / 'api-error.json', {'asset': name, 'http_status': response.status_code, 'body': message})
            print(f'{name}: HTTP {response.status_code}; {message}', flush=True)
            return 2
        result = response.json()
        task_id = result.get('id')
        if not task_id:
            raise RuntimeError('Create response did not include task id. Do not retry automatically.')
        manifest['assets'][name] = {
            'task_id': task_id, 'status': 'submitted',
            'reference': str(image.relative_to(ASSETS)).replace('\\', '/'),
            'reference_sha256': hashlib.sha256(image.read_bytes()).hexdigest(),
            'requested_subdivision_level': 'low', 'requested_file_format': 'glb',
            'created_locally_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
        }
        save(manifest_path, manifest)
        print(f'{name}: submitted {task_id}', flush=True)
    end = time.monotonic() + args.timeout_minutes * 60
    pending = {name for name, item in manifest['assets'].items() if not item.get('downloaded') and item.get('status') != 'failed'}
    while pending and time.monotonic() < end:
        for name in sorted(pending):
            item = manifest['assets'][name]
            response = session.get(API + '/' + item['task_id'], timeout=(20, 60))
            if not response.ok:
                print(f'{name}: status query HTTP {response.status_code}; will retry read.', flush=True)
                continue
            result = response.json()
            status = result.get('status')
            if status != item['status']:
                print(f'{name}: {status}', flush=True)
            item['status'] = status
            item['response'] = clean_response(result)
            save(manifest_path, manifest)
            if status == 'failed':
                pending.remove(name)
                print(f'{name}: task failed {result.get("error")}', flush=True)
                continue
            if status == 'succeeded':
                url = result.get('content', {}).get('file_url')
                if not url or urlparse(url).scheme != 'https':
                    raise RuntimeError('Expected HTTPS asset download URL')
                # Deliberately use a separate unauthenticated request. Never send API credentials to an asset host.
                download = requests.get(url, timeout=(20, 180))
                download.raise_for_status()
                folder = ASSETS / 'raw' / name
                folder.mkdir(parents=True, exist_ok=True)
                archive = folder / 'seed3d-output.zip'
                archive.write_bytes(download.content)
                with zipfile.ZipFile(archive) as z:
                    for member in z.infolist():
                        destination = (folder / member.filename).resolve()
                        if not destination.is_relative_to(folder.resolve()):
                            raise RuntimeError('Rejected unsafe archive path')
                    z.extractall(folder)
                item['archive_sha256'] = hashlib.sha256(download.content).hexdigest()
                item['archive_bytes'] = len(download.content)
                item['files'] = [str(p.relative_to(ASSETS)).replace('\\', '/') for p in folder.rglob('*') if p.is_file()]
                item['downloaded'] = True
                save(manifest_path, manifest)
                pending.remove(name)
                print(f'{name}: downloaded {len(download.content)} bytes', flush=True)
        if pending:
            time.sleep(30)
    print('Asset generation states: ' + ', '.join(f'{name}={item["status"]}' for name, item in manifest['assets'].items()), flush=True)
    return 0 if not pending and all(x.get('downloaded') for x in manifest['assets'].values()) else 3


if __name__ == '__main__':
    raise SystemExit(main())
