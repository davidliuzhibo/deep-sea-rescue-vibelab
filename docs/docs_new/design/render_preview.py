from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).resolve().parent;OUT=ROOT/'demos/deep-sea-rescue/.scratch'
spec=json.loads((OUT/'design-spec.json').read_text(encoding='utf8'))
bundle=(ROOT/'.agents/skills/pico-spatial-app-designer/assets/spatialui-web/spatialui.bundle.js').read_text(encoding='utf8')
css='body{margin:0;background-color:#DAD6D3;background-image:none}*{box-sizing:border-box}#app{display:flex;flex-direction:column;align-items:center;gap:24px;padding:24px 16px}.system-window{background:var(--Background-MaterialRegular,rgba(255,255,255,.34))}[data-design-surface]{background:var(--pico-design-surface-fill)}sui-button{max-width:100%;min-width:0;flex-shrink:0}section{min-width:0}'
html='<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>'+spec['metadata']['projectName']+'</title><style>'+css+'</style></head><body><main id="app"></main><script>'+bundle+'</script><script id="pico-design-spec" type="application/json">'+json.dumps(spec,ensure_ascii=False).replace('</','<\\/')+'</script><script>'+(HERE/'renderer.js').read_text(encoding='utf8')+'</script></body></html>'
(OUT/'preview.html').write_bytes(html.encode());(OUT/'design-doc.md').write_bytes((HERE/'design-doc.md').read_bytes())
print('preview bytes',len(html.encode()))
