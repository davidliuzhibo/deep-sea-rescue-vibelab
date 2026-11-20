"""Source/IR traceability receipt; explicitly not an image or native-runtime certification."""
from pathlib import Path
import hashlib, importlib.util, json
R=Path(__file__).resolve().parents[2];P=R/'demos/deep-sea-rescue';S=P/'.scratch';specPath=S/'design-spec.json'
authority=json.loads(specPath.read_text(encoding='utf8'))
packaged=json.loads((P/'app/src/main/assets/design/design-spec.json').read_text(encoding='utf8'));assert packaged==authority
module=importlib.util.spec_from_file_location('fidelity',R/'.agents/skills/spatial-design-to-app/scripts/check_design_fidelity.py')
f=importlib.util.module_from_spec(module);module.loader.exec_module(f)
contract=f.build_contract(packaged);base='app/src/main/java/com/example/deepsearescue/pcd0b39b0/'
mapping=[]
def add(ref,file,symbol,evidence):
    text=(P/file).read_text(encoding='utf8');assert symbol in text and evidence in text,(ref,symbol,evidence)
    mapping.append({'design_refs':[ref],'file':file,'symbols':[symbol],'evidence':[evidence]})
for ref in sorted(f.required_design_refs(contract)):
    cat,ident=ref.split(':',1)
    if cat=='surface':
        if ident=='ocean':add(ref,base+'spatial/RescueScene.kt','PortalWorldComponent','MeshResource.createPlane(1.5f,.9f,.025f)')
        else:add(ref,base+'ui/rescue/RescueScreen.kt','AttachmentPanel(id="controls")','Modifier.size(820.dp,660.dp)')
    elif cat=='node':
        if ident in ['ocean-root','ocean-visual']:add(ref,base+'spatial/RescueScene.kt','fittedSceneRoot','PortalComponent(ocean,true,false,true,false)')
        else:add(ref,base+'ui/rescue/components/RescuePanel.kt','PanelNode','c.layout(n,LocalPanelWidth.current)')
    elif cat=='state-model':add(ref,base+'ui/rescue/RescueUiState.kt','RescueUiState','val page:String="placement"')
    elif cat=='state':add(ref,base+'ui/rescue/components/PanelContract.kt','fun root(state:String)','roots.getValue(state)')
    elif cat=='action':
        action=next(a for a in packaged['actions'] if a['id']==ident);op=action['payload']['op']
        if ident=='select-visible-target':add(ref,base+'ui/rescue/RescueViewModel.kt','onEvent','is RescueEvent.SelectTarget')
        else:add(ref,base+'ui/rescue/RescueViewModel.kt','control',f'"{op}"->')
    elif cat=='responsive-rule':add(ref,base+'ui/rescue/components/PanelContract.kt','fun layout','maxWidthDp')
    elif cat=='data-case':add(ref,'app/src/main/assets/design/design-spec.json','"dataCases"',f'"{ident}"')
    else:raise ValueError(ref)
receipt={
    'schema_version':1,'design_spec':{'path':'design-spec.json','sha256':hashlib.sha256(specPath.read_bytes()).hexdigest()},
    'implementation':contract,'source_mappings':mapping,
    'scope':'Logical source/packaged-IR traceability only. UI reads this same packaged JSON. Web fixture shapes/data cases are declared Web-only, not native rendering facts. Native visual bounds, optical input and OS resizing remain unverified. Min/max panel sizes are component preview constraints, not promised OS resize support.',
    'review':'Direct renderer branches, production action dispatch, source hierarchy, metre dimensions, collider parent separation and tests inspected. No passing native receipt fabricated.',
    'designRevision6':'Four mission campaign, coupled beacon wheels, two-bag balance and physically swept escort; grounded equipment and load-bearing straps. Route corridor/envelope use sampled animated turtle bounds. Original slice retained.'
}
(S/'design_implementation_map.json').write_bytes((json.dumps(receipt,ensure_ascii=False,indent=2)+'\n').encode())
print('Source/IR references mapped:',len(mapping))
