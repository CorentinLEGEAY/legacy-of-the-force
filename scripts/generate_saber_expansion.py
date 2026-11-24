"""Original modular variations, finite type profiles and physical-part recipes.

Geometry uses the established machined material library; no external models/media.
Advanced structural plans are discovered through planetary content, not created by
selecting an advanced type in the workbench.
"""
import copy
import json
import runpy
from pathlib import Path

HERE=Path(__file__).resolve().parent
base=runpy.run_path(str(HERE/'generate_saber_design.py'))
ROOT=HERE.parent/'src/main/resources'
ASSETS=ROOT/'assets/legacyoftheforce'
DATA=ROOT/'data/legacyoftheforce'
box,band=base['box'],base['band']
normal=base['modules'](False)
geometry={slot+'_basic':copy.deepcopy(parts) for slot,parts in normal.items()}
geometry['emitter_claw']=base['modules'](True)['emitter']
geometry['emitter_ring']=normal['emitter']+band(12.5,1.0,2.5,'alloy')+band(13.5,.4,2.1,'edge')
geometry['emitter_flared']=band(11.25,.6,1.85,'alloy')+band(11.85,.6,2.1,'gunmetal')+band(12.45,.6,2.4,'alloy')+band(13.05,.5,2.7,'edge')
geometry['emitter_guard']=normal['emitter']+[box([2.8,11.6,7.1],[13.2,13.4,8.9],'gunmetal'),box([2.5,12,6.8],[4,13,9.2],'edge'),box([12,12,6.8],[13.5,13,9.2],'edge')]
geometry['emitter_twin']=normal['emitter']+band(11.5,.4,2.2,'edge')+[box([6.1,12,7.2],[6.5,13.5,8.8],'copper'),box([9.5,12,7.2],[9.9,13.5,8.8],'copper')]
geometry['neck_short']=band(9.4,.6,1.8,'edge')+band(10,.65,1.6,'alloy')+band(10.65,.6,1.8,'gunmetal')
geometry['neck_long']=normal['neck']+band(10.3,.25,1.7,'edge')+band(10.8,.25,1.7,'edge')
geometry['neck_curved']=normal['neck']+[box([6.5,9.9,7.3],[6.8,11.1,8.7],'copper'),box([9.2,9.9,7.3],[9.5,11.1,8.7],'copper')]
geometry['grip_short']=normal['grip']+band(3.1,.3,1.95,'copper')+band(5.9,.3,1.95,'edge')
geometry['grip_leather']=normal['grip']
for y in (2.2,3.1,4,4.9,5.8):geometry['grip_leather'].append(box([6.9,y,6.15],[9.1,y+.12,6.4],'edge'))
geometry['grip_engraved']=normal['grip']+[box([7.1,2,6.15],[7.2,6.7,6.4],'copper'),box([8.8,2,6.15],[8.9,6.7,6.4],'copper')]
for y in (3,4,5,6):geometry['grip_engraved'].append(box([7.4,y,6.15],[8.6,y+.1,6.4],'edge'))
geometry['grip_curved']=normal['grip']+[box([6.0,3,6.8],[6.4,5.6,9.2],'alloy'),box([9.6,4,6.8],[10,6.9,9.2],'alloy')]
geometry['control_trigger']=normal['control']+[box([9.4,7.6,7.2],[10.7,8,8.8],'edge'),box([10.3,8,7.3],[10.7,9.3,8.7],'gunmetal')]
geometry['control_lever']=normal['control']+[box([6.9,8.6,5.8],[9,8.9,6.15],'copper'),box([8.7,8.3,5.65],[9.2,9.2,5.95],'grip')]
geometry['circuit_efficient']=normal['circuit']+[box([6.7,7,7],[7,10.5,7.3],'edge')]
geometry['circuit_power']=normal['circuit']
for y in (5.5,6.5,7.5,8.5,9.5):geometry['circuit_power'].append(box([6.2,y,7],[9.8,y+.3,9],'copper'))
geometry['pommel_cone']=band(.25,.3,1.1,'edge')+band(.55,.4,1.4,'alloy')+band(.95,.65,1.8,'gunmetal')
geometry['pommel_balanced']=band(.1,.5,2.1,'gunmetal')+band(.6,.5,2.2,'alloy')+band(1.1,.5,1.8,'edge')
geometry['pommel_connector']=band(.25,.45,1.9,'copper')+band(.7,.45,2.1,'edge')+band(1.15,.45,1.9,'copper')

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

all_types=['single','short','curved','double','crossguard']
allowed={
 'emitter_basic':['single','short','curved'],'emitter_claw':['single','short','curved'],
 'emitter_ring':['single','double','curved'],'emitter_flared':['single','curved'],'emitter_guard':['crossguard'],'emitter_twin':['double'],
 'neck_basic':['single','crossguard'],'neck_short':['short'],'neck_long':['single','double'],'neck_curved':['curved'],
 'grip_basic':['single','crossguard'],'grip_short':['short'],'grip_leather':['single','crossguard'],'grip_engraved':['single','double','crossguard'],'grip_curved':['curved'],
 'control_basic':all_types,'control_trigger':all_types,'control_lever':all_types,
 'circuit_basic':['single','short','curved'],'circuit_efficient':['single','short','curved'],'circuit_power':all_types,
 'pommel_basic':['single','short','curved','crossguard'],'pommel_cone':['single','short','curved'],'pommel_balanced':['single','short','curved','crossguard'],'pommel_connector':['double']}
modifiers={'emitter_ring':{'stability':.05,'parry':.02},'emitter_flared':{'damage':.3,'cost':.05},'emitter_guard':{'parry':.05,'speed':-.05},
 'emitter_twin':{'cost':.05},'neck_short':{'speed':.04},'neck_long':{'damage':.2,'speed':-.04},'neck_curved':{'parry':.03},
 'grip_short':{'speed':.08},'grip_leather':{'parry':.03},'grip_engraved':{'stability':.03},'grip_curved':{'parry':.04},
 'control_trigger':{'speed':.03},'control_lever':{'cost':-.03},'circuit_efficient':{'cost':-.15,'damage':-.3},'circuit_power':{'damage':1,'cost':.2,'speed':-.05},
 'pommel_cone':{'speed':.03},'pommel_balanced':{'parry':.04,'speed':-.04},'pommel_connector':{'stability':.03}}
lengths={'emitter':2.4,'neck':1.8,'grip':5.7,'control':1.95,'circuit':1,'pommel':1.35}
legacy={'emitter_basic':(3,2,0),'emitter_claw':(4,2,.05),'neck_basic':(2,2,0),'grip_basic':(6,2,0),'control_basic':(1,2,0),'circuit_basic':(1,2,0),'pommel_basic':(2,2,0)}
for name,elements in geometry.items():
    family=name.split('_')[0]
    center=(min(c['from'][1] for c in elements)+max(c['to'][1] for c in elements))/2
    base['write_model'](name,elements,display={'gui':{'rotation':[25,225,0],'translation':[0,8-center,0],'scale':[1.35]*3}})
    definition={'schemaVersion':1,'slot':family,'mount':'standard','types':allowed[name],'length':lengths[family],'radius':5.5 if name=='emitter_guard' else 2.7 if family=='emitter' else 2.2,'stability':0}
    definition.update(modifiers.get(name,{}));write(DATA/f'saber_parts/{name}.json',definition)
    if name in legacy:
        definition.update(zip(('length','radius','stability'),legacy[name]));write(DATA/f'saber_parts/{name}.json',definition)
    if name.endswith('_basic') or name=='emitter_claw':continue
    ingredient='quartz' if name in ('circuit_power','emitter_guard','emitter_twin') else 'leather' if name=='grip_leather' else 'copper_ingot'
    write(DATA/f'recipe/{name}.json',{'type':'minecraft:crafting_shapeless','category':'equipment',
        'ingredients':[{'item':'legacyoftheforce:'+family+'_basic'},{'item':'minecraft:'+ingredient}],
        'result':{'id':'legacyoftheforce:'+name,'count':1}})

for name,damage,speed,reach,cost,parry in [('single',1,1,3,1,0),('short',.85,1.2,2.5,.85,.02),('curved',.95,1.05,3,1,.05),('double',1.1,.85,3.5,1.5,0),('crossguard',1.2,.8,3,1.3,.08)]:
    write(DATA/f'saber_types/{name}.json',{'schemaVersion':1,'damage':damage,'speed':speed,'reach':reach,'cost':cost,'parry':parry})
write(ASSETS/'models/item/lightsaber.json',{'parent':'builtin/entity','textures':{'particle':'legacyoftheforce:item/saber/alloy'},'gui_light':'front','display':base['DISPLAY']})

labels={'emitter_ring':('Émetteur annulaire','Ring emitter'),'emitter_flared':('Émetteur évasé','Flared emitter'),'emitter_guard':('Émetteur à garde','Crossguard emitter'),'emitter_twin':('Émetteur double','Twin emitter'),
 'neck_short':('Col court','Short neck'),'neck_long':('Col long','Long neck'),'neck_curved':('Col de sabre courbe','Curved saber neck'),
 'grip_short':('Poignée courte','Short grip'),'grip_leather':('Poignée cuir','Leather grip'),'grip_engraved':('Poignée gravée','Engraved grip'),'grip_curved':('Poignée courbe','Curved grip'),
 'control_trigger':('Commande à gâchette','Trigger control'),'control_lever':('Commande à levier','Lever control'),'circuit_efficient':('Circuit efficace','Efficient circuit'),'circuit_power':('Circuit haute puissance','High-power circuit'),
 'pommel_cone':('Pommeau conique','Conical pommel'),'pommel_balanced':('Pommeau équilibré','Balanced pommel'),'pommel_connector':('Raccord double','Double connector')}
for locale,index in [('fr_fr',0),('en_us',1)]:
    path=ASSETS/f'lang/{locale}.json';data=json.loads(path.read_text(encoding='utf-8'))
    for name,values in labels.items():data['item.legacyoftheforce.'+name]=values[index]
    for name,fr,en in [('single','Sabre simple','Single saber'),('short','Sabre court','Short saber'),('curved','Sabre courbe','Curved saber'),('double','Sabre double','Double saber'),('crossguard','Sabre à garde','Crossguard saber')]:data['saber.legacyoftheforce.type_'+name]=fr if index==0 else en
    values={'choose_part':'Choisir une pièce compatible ; les ressources restent dans votre inventaire.','choose_type':'Type','secondary':'Deuxième émetteur','type_locked':'Lire un plan de ce type avant fabrication.','type_learned':'Type appris : %s'} if index==0 else {'choose_part':'Choose a compatible part; resources stay in your inventory.','choose_type':'Type','secondary':'Second emitter','type_locked':'Read a plan for this type before crafting.','type_learned':'Type learned: %s'}
    for key,value in values.items():data['workshop.legacyoftheforce.'+key]=value
    data['workshop.legacyoftheforce.preview_stats']='Estimation du plan : portée %s · coups/s %s · coût ×%s. Sans cristal : aucun bonus de cristal ; activation impossible.' if index==0 else 'Design estimate: reach %s · attacks/s %s · cost ×%s. Without a crystal: no crystal bonus; activation unavailable.'
    write(path,data)

runpy.run_path(str(HERE/'generate_saber_finishes.py'),run_name='__main__')
