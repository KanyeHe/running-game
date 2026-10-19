"""Deterministic Cocos Creator 3.8 source scene and import metadata (editor verification required)."""
import json
import uuid
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'client'
namespace = uuid.UUID('c378d3b9-b64d-41ab-bf4c-a0b7836c0b67')
def identity(path):
    return str(uuid.uuid5(namespace, path))
def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
def compress(value):
    # Cocos 23-character UUID compression: preserve five hex digits, pack each following 12 bits.
    chars='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
    value=value.replace('-','')
    return value[:5]+''.join(chars[int(value[i:i+3],16)>>6]+chars[int(value[i:i+3],16)&63] for i in range(5,32,3))
for path in sorted((root/'assets/scripts').glob('*.ts')):
    write(Path(str(path)+'.meta'), {'ver':'4.0.24','importer':'typescript','imported':True,'uuid':identity(path.name),'files':[],'subMetas':{},'userData':{}})
for name in ['scripts','scenes']:
    write(root/f'assets/{name}.meta',{'ver':'1.2.0','importer':'directory','imported':True,'uuid':identity(name),'files':[],'subMetas':{},'userData':{}})
scene_id=identity('Main.scene')
node={'__type__':'cc.Node','_name':'Game','_objFlags':0,'__editorExtras__':{},'_parent':{'__id__':1},'_children':[], '_active':True,'_components':[{'__id__':3}], '_prefab':None,'_lpos':{'__type__':'cc.Vec3','x':0,'y':0,'z':0},'_lrot':{'__type__':'cc.Quat','x':0,'y':0,'z':0,'w':1},'_lscale':{'__type__':'cc.Vec3','x':1,'y':1,'z':1},'_mobility':0,'_layer':1073741824,'_euler':{'__type__':'cc.Vec3','x':0,'y':0,'z':0},'_id':identity('game-node')}
scene=[{'__type__':'cc.SceneAsset','_name':'Main','_objFlags':0,'__editorExtras__':{},'_native':'','scene':{'__id__':1}},
       {'__type__':'cc.Scene','_name':'Main','_objFlags':0,'__editorExtras__':{},'_parent':None,'_children':[{'__id__':2}],'_active':True,'_components':[], '_prefab':None,'_lpos':{'__type__':'cc.Vec3','x':0,'y':0,'z':0},'_lrot':{'__type__':'cc.Quat','x':0,'y':0,'z':0,'w':1},'_lscale':{'__type__':'cc.Vec3','x':1,'y':1,'z':1},'_layer':1073741824,'_id':scene_id,'autoReleaseAssets':False},
       node,{'__type__':compress(identity('Bootstrap.ts')),'_name':'','_objFlags':0,'__editorExtras__':{},'node':{'__id__':2},'_enabled':True,'__prefab':None,'_id':identity('bootstrap-component')}]
write(root/'assets/scenes/Main.scene',scene)
write(root/'assets/scenes/Main.scene.meta',{'ver':'1.1.50','importer':'scene','imported':True,'uuid':scene_id,'files':['.json'],'subMetas':{},'userData':{}})
write(root/'settings/v2/packages/project.json', {'__version__':'1.0.0','general':{'designResolution':{'width':1280,'height':720,'fitWidth':True,'fitHeight':True}}})
print('Generated Cocos scene and stable metadata:',scene_id)
