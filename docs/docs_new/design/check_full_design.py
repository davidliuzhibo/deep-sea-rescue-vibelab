"""Headless Web-only layout and event validation, never native gameplay evidence."""
from pathlib import Path
import json,hashlib,traceback
import jsonschema
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'demos/deep-sea-rescue/.scratch';E=ROOT/'.scratch/deep-sea-rescue-full-20261105'
s=json.loads((OUT/'design-spec.json').read_text(encoding='utf8'))
report={'scope':'web_design_validation_only','native':False,'revision':s['metadata']['revision'],'layouts':[],'errors':[]}
try:
 jsonschema.validate(s,json.loads((ROOT/'.agents/skills/pico-spatial-app-designer/assets/design-spec.schema.json').read_text(encoding='utf8')))
 ids={n['id'] for n in s['nodes']};assert len(ids)==len(s['nodes']);actions={a['id'] for a in s['actions']};assert len(actions)==len(s['actions'])
 for n in s['nodes']:
  for child in n.get('children',[]):assert child in ids
  for act in n.get('events',{}).values():assert act in actions
 with sync_playwright() as p:
  browser=p.chromium.launch(headless=True,executable_path='C:/Program Files/Google/Chrome/Application/chrome.exe')
  page=browser.new_page(viewport={'width':1040,'height':1180},device_scale_factor=1)
  page.on('pageerror',lambda e:report['errors'].append(str(e)))
  page.goto((OUT/'preview.html').as_uri());page.wait_for_function('window.DESIGN_TEST !== undefined')
  assert page.evaluate('JSON.parse(document.querySelector("#pico-design-spec").textContent)')==s
  assert page.evaluate('getComputedStyle(document.body).backgroundColor')=='rgb(218, 214, 211)'
  def snap():return page.evaluate('DESIGN_TEST.snapshot()')
  def click(id):page.locator('[data-node-id="'+id+'"]').locator('button').click()
  def state(expected):assert snap()['state']==expected,(snap()['state'],expected)
  click('scan-wall');state('wall-preview');click('confirm-wall');state('table-select');click('virtual-console');click('start');state('inspect')
  for target in ['front-rope','rear-rope','brace-point']:
   click('inspect-'+target);click('inspect-aim');click('inspect-execute')
  state('brace');click('brace-brace-point');click('brace-aim');click('brace-execute');state('cut')
  click('cut-rear-rope');click('cut-aim');click('cut-execute');assert not snap()['data']['fixture']['cut']
  for target in ['front-rope','rear-rope']:
   click('cut-'+target);click('cut-aim');click('cut-execute')
  state('bag');click('bag-float-hook');click('bag-aim');click('bag-execute');click('bag-air-plus');assert snap()['data']['fixture']['air']==1;click('bag-air-plus');state('winch')
  click('winch-execute');assert snap()['data']['fixture']['retrieval']==0
  click('steer-left')
  for _ in range(3):click('winch-execute')
  state('farewell');page.wait_for_function('DESIGN_TEST.snapshot().state==="result"');click('result-settings');state('settings');click('toggle-music');assert not snap()['data']['music'];click('settings-back');state('result');click('result-exit');click('cancel-exit');state('result');click('result-exit');click('confirm-exit');state('exited')
  report['fixtureFlow']='pass: Web fixture only; no physical/time/collision equivalence claimed'
  page.evaluate('DESIGN_TEST.setCase("normal")');page.evaluate('DESIGN_TEST.enter("result")');click('chart-open');click('mission-1');state('beacon-survey')
  for t in ['power-port','lens-left','lens-mid']:
   click('beacon-survey-'+t);click('beacon-survey-aim');click('beacon-survey-execute')
  for _ in range(2):
   click('beacon-power-power-port');click('beacon-power-aim');click('beacon-power-execute')
  for t,num in [('lens-left',1),('lens-mid',2)]:
   click('beacon-align-'+t);click('beacon-align-aim')
   for _ in range(num):click('dial-plus')
  page.screenshot(path=str(E/'full-beacon-preview.png'),full_page=True)
  click('beacon-align-execute');page.wait_for_function('DESIGN_TEST.snapshot().state==="result"');click('chart-open');click('mission-2')
  for t in ['anchor-left','anchor-right','load-weight']:
   click('balance-survey-'+t);click('balance-survey-aim');click('balance-survey-execute')
  for t in ['anchor-left','anchor-right']:
   click('balance-attach-'+t);click('balance-attach-aim');click('balance-attach-execute')
  for _ in range(2):click('bag-left1')
  for _ in range(3):click('bag-right1')
  for _ in range(4):click('balance-lift-execute')
  for _ in range(4):click('cage-right')
  page.screenshot(path=str(E/'full-balance-preview.png'),full_page=True)
  click('cage-lock');state('result');click('chart-open');click('mission-3')
  for i,lane in enumerate([0,2,0]):
   click('route-'+str(i));click('lane-'+str(lane));click('escort-plan-execute')
  state('ocean-farewell');click('farewell-skip');state('result')
  assert snap()['data']['fixture']['completed']==[0,1,2,3]
  report['fullFixtureFlow']='four tasks reach ending through actual Web buttons; fixture, not native simulation'
  page.evaluate('DESIGN_TEST.setCase("fallback")');click('scan-wall');state('placement');click('practice');state('ready')
  page.evaluate('DESIGN_TEST.setCase("error")');state('error');click('retry');state('placement')
  for width in [852,672,1032]:
   page.set_viewport_size({'width':width,'height':1180})
   for st in s['states']:
    page.evaluate('(s)=>DESIGN_TEST.enter(s)',st['id'])
    row=page.evaluate('''()=>{const s=document.querySelector('[data-surface-id="controls"]'),r=s.getBoundingClientRect();const body=s.querySelector('[data-node-id$="-content"]'),br=body.getBoundingClientRect();return {horizontal:document.documentElement.scrollWidth>innerWidth+1,bodyHeight:br.height,buttons:[...s.querySelectorAll('sui-button')].flatMap(e=>{const b=e.shadowRoot.querySelector('button').getBoundingClientRect();return b.width<55||b.height<55||b.right>r.right+1?[{id:e.dataset.nodeId,width:b.width,right:b.right,surfaceRight:r.right}]:[]}),footerInside:s.querySelector('[data-node-id$="-footer"]').getBoundingClientRect().bottom<=r.bottom+1}}''')
    row.update(width=width,state=st['id']);report['layouts'].append(row);assert not row['horizontal'] and not row['buttons'] and row['footerInside'] and row['bodyHeight']>=56,row
   page.evaluate('DESIGN_TEST.enter("inspect")');page.screenshot(path=str(E/f'design-inspect-{width}.png'),full_page=True)
  assert not report['errors'],report['errors']
  browser.close()
 report['passed']=True
except Exception as e:
 report['passed']=False;report['failure']=str(e);report['traceback']=traceback.format_exc()
finally:
 report['hashes']={f:hashlib.sha256((OUT/f).read_bytes()).hexdigest() for f in ['design-spec.json','preview.html']}
 (E/'design-web-check.json').write_bytes((json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode())
 print(json.dumps({k:v for k,v in report.items() if k not in ['layouts','hashes']},ensure_ascii=False))
raise SystemExit(0 if report['passed'] else 1)
