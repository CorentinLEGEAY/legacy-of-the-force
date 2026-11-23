"""Original mechanical geometry and six functional module definitions; no imported media.

Uses the original machined materials from generate_saber_design.py. This script is
the current speeder item-model pass; run after the historical prototype generator.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/legacyoftheforce'
DATA = ROOT / 'data/legacyoftheforce'
FACES = ('up', 'down', 'north', 'south', 'east', 'west')


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def box(start, end, material='alloy', tint=False):
    # Scaled UVs preserve the machining grain on narrow rails and fasteners.
    size = [max(.25, b-a) for a, b in zip(start, end)]
    faces = {}
    for face in FACES:
        w, h = (size[0], size[2]) if face in ('up', 'down') else (size[2], size[1]) if face in ('east', 'west') else (size[0], size[1])
        faces[face] = {'texture': '#'+material, 'uv': [0, 0, min(16,w), min(16,h)]}
        if tint: faces[face]['tintindex'] = 0
    return {'from': start, 'to': end, 'faces': faces}


def bolts(y, x=(2, 13), z=(2, 13)):
    return [box([a,y,b],[a+1,y+.6,b+1],'edge') for a in x for b in z]


def seat(pilot):
    result = [box([3,3,3],[13,5,13],'alloy',True), box([4,5,3.5],[12,7,12],'grip'),
              box([3,7,10.5],[13,14,13],'alloy',True), box([4,7,10],[12,13,10.6],'grip'),
              box([5.5,0,5.5],[10.5,3,10.5],'gunmetal'), box([2,7,5],[3,9,11],'edge'),
              box([13,7,5],[14,9,11],'edge')]
    for y in (7.5,9,10.5,12): result.append(box([4.3,y,9.85],[11.7,y+.25,10.1],'gunmetal'))
    if pilot:
        result += [box([2,4,1],[3,8,2],'gunmetal'),box([13,4,1],[14,8,2],'gunmetal'),
                   box([2,8,1],[6,9,2],'grip'),box([10,8,1],[14,9,2],'grip'),box([6,8,1],[10,10,2],'indicator')]
    else:
        result += [box([5,10,9.7],[6,12,10],'button'),box([10,10,9.7],[11,12,10],'button'),box([7,6.8,6],[9,7.3,8],'copper')]
    return result + bolts(4.8, (3,12), (3,12))


geometry = {}
geometry['chassis_light'] = [box([1,4,1],[3,8,15],'alloy',True),box([13,4,1],[15,8,15],'alloy',True)]
for z in (2,7,12): geometry['chassis_light'].append(box([3,5,z],[13,7,z+2],'gunmetal'))
geometry['chassis_light'] += bolts(8) + [box([5,7,6],[11,8.5,10],'edge'),box([6,8.5,7],[10,9,9],'copper')]
geometry['engine_standard'] = [box([3,3,3],[13,11,13],'gunmetal'),box([2,5,3],[14,9,12],'alloy',True),
    box([4,4,12],[12,10,15],'edge'),box([5,5,14.8],[11,9,16],'gunmetal'),box([6,6,15.8],[10,8,16],'indicator')]
for y in (3.5,5,6.5,8,9.5): geometry['engine_standard'] += [box([2.5,y,3],[3.2,y+.5,12],'copper'),box([12.8,y,3],[13.5,y+.5,12],'copper')]
for z in (4,6,8,10): geometry['engine_standard'].append(box([4,11,z],[12,12,z+.7],'edge'))
geometry['engine_standard'] += bolts(11, (3,12), (3,12))
geometry['battery_small'] = [box([3,2,4],[13,12,12],'alloy',True),box([3,12,4],[13,13,12],'gunmetal'),
    box([4,13,5],[6,15,7],'copper'),box([10,13,9],[12,15,11],'copper'),box([6,6,3.7],[10,10,4],'gunmetal')]
for y in (6.5,7.5,8.5): geometry['battery_small'].append(box([6.5,y,3.55],[9.5,y+.5,3.8],'indicator'))
for x in (4,11): geometry['battery_small'].append(box([x,1,3],[x+1,13.2,13],'edge'))
geometry['seat_pilot'] = seat(True)
geometry['seat_passenger'] = seat(False)
geometry['thruster_vector'] = [box([4,3,3],[12,11,12],'alloy',True),box([5,5,12],[11,9,16],'gunmetal'),
    box([6,6,15.8],[10,8,16],'indicator'),box([2,6,5],[4,8,10],'edge'),box([12,6,5],[14,8,10],'edge')]
for z in (4,6,8,10): geometry['thruster_vector'].append(box([3,4,z],[13,10,z+.6],'copper'))
geometry['stabilizer_gyro'] = [box([3,1,3],[13,3,13],'alloy',True),box([7,3,7],[9,12,9],'copper'),
    box([3,6,3],[13,8,5],'edge'),box([3,6,11],[13,8,13],'edge'),box([3,6,5],[5,8,11],'edge'),box([11,6,5],[13,8,11],'edge'),
    box([5,10,5],[11,11,11],'gunmetal'),box([6,11,6],[10,12,10],'indicator')] + bolts(3)
geometry['body_cowling'] = [box([1,5,2],[15,7,15],'alloy',True),box([2,7,3],[14,9,14],'alloy',True),
    box([3,9,5],[13,10,13],'alloy',True),box([4,10,6],[12,10.4,12],'edge')]
for x in (3,12):
    for z in (5,7,9): geometry['body_cowling'].append(box([x,9.1,z],[x+1,9.4,z+1],'gunmetal'))
geometry['armor_plate'] = [box([1,3,1],[15,5,15],'gunmetal'),box([2,5,2],[14,7,14],'alloy',True),
    box([3,7,3],[13,8,13],'edge'),box([4,8,4],[12,8.7,12],'alloy',True)] + bolts(8,(3,12),(3,12))
geometry['lamp_module'] = [box([4,1,5],[12,3,11],'alloy',True),box([7,3,7],[9,7,9],'gunmetal'),
    box([3,7,4],[13,13,12],'alloy',True),box([4,8,3],[12,12,4],'edge'),box([5,9,2.8],[11,11,3.1],'indicator')]
for x in (4,6,8,10,12): geometry['lamp_module'].append(box([x,8,12],[x+.5,12,13],'gunmetal'))
geometry['cargo_crate'] = [box([2,2,2],[14,13,14],'alloy',True),box([3,3,1.7],[13,12,2],'gunmetal'),
    box([3.5,3.5,1.4],[12.5,11.5,1.8],'alloy',True),box([2,13,2],[14,14,14],'edge'),
    box([7,6,1],[9,9,1.5],'copper'),box([6.5,8,1],[9.5,8.5,1.3],'edge')]
for x in (3,12): geometry['cargo_crate'].append(box([x,2,2],[x+1,13,14.2],'gunmetal'))
for y in (4,7,10): geometry['cargo_crate'].append(box([2.5,y,14],[13.5,y+.5,14.4],'edge'))
geometry['cargo_crate'] += bolts(14,(3,12),(3,12))

textures = {name: 'legacyoftheforce:item/saber/'+name for name in ('alloy','edge','gunmetal','grip','copper','indicator','button')}
for name, elements in geometry.items():
    write(ASSETS / f'models/item/{name}.json', {'parent':'minecraft:block/block','textures':dict(textures,particle=textures['alloy']),
        'display': {'gui': {'rotation':[25,225,0],'translation':[0,-.5,0],'scale':[.8,.8,.8]},
                    'ground': {'rotation':[0,0,0],'translation':[0,2,0],'scale':[.4,.4,.4]},
                    'fixed': {'rotation':[0,180,0],'scale':[.7,.7,.7]}}, 'elements':elements})

modules = [
    ('thruster_vector','thruster',1.5,10,8,0,.1,0,'copper_ingot','redstone'),
    ('stabilizer_gyro','stabilizer',1.5,0,8,0,.45,0,'copper_ingot','amethyst_shard'),
    ('seat_passenger','passenger',1,0,0,0,0,0,'leather','iron_ingot'),
    ('body_cowling','body',.5,0,0,0,0,0,'iron_ingot','iron_nugget'),
    ('armor_plate','armor',3,0,0,0,0,.18,'iron_ingot','copper_ingot'),
    ('lamp_module','decor',.5,0,0,0,0,0,'glass','glowstone_dust'),
    ('cargo_crate','cargo',2.5,0,0,0,0,0,'iron_ingot','chest')]
for name,family,mass,thrust,lift,capacity,stability,armor,a,b in modules:
    write(DATA/f'speeder_modules/{name}.json',{'schemaVersion':1,'family':family,'mass':mass,'thrust':thrust,'lift':lift,'capacity':capacity,
        'stability':stability,'armor':armor,'cargoSlots':9 if family=='cargo' else 0,'connectors':list(FACES), 'salvage':[{'item':'minecraft:'+a,'count':1}]})
    write(DATA/f'recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','group':'legacyoftheforce_speeder',
        'pattern':[' A ','ABA',' I '],'key':{'A':{'item':'minecraft:'+a},'B':{'item':'minecraft:'+b},'I':{'item':'minecraft:iron_nugget'}},
        'result':{'id':'legacyoftheforce:'+name,'count':1}})

names = {'fr_fr':['Propulseur vectoriel','Stabilisateur gyroscopique','Siège passager','Capot profilé','Plaque de blindage','Projecteur orientable','Coffre de transport'],
         'en_us':['Vector thruster','Gyroscopic stabilizer','Passenger seat','Streamlined cowling','Armor plate','Directional lamp','Cargo crate']}
for locale, labels in names.items():
    path=ASSETS/f'lang/{locale}.json'; data=json.loads(path.read_text(encoding='utf-8'))
    for row,label in zip(modules,labels): data['item.legacyoftheforce.'+row[0]]=label
    extra = {'next_parts':'Autres pièces','guest_pending':'Sauvegarde des autorisations incertaine : embarquement suspendu.','guest_saved':'Autorisation passager enregistrée.',
             'guest_denied':'Placez votre speeder vide et à l’arrêt à moins de six blocs ; trois invités maximum.','error_too_many_seats':'Quatre places maximum.'} if locale=='fr_fr' else {
             'next_parts':'More parts','guest_pending':'Guest save is uncertain: boarding suspended.','guest_saved':'Passenger permission saved.',
             'guest_denied':'Park your empty speeder within six blocks; at most three guests.','error_too_many_seats':'At most four seats.'}
    for key,value in extra.items(): data['speeder.legacyoftheforce.'+key]=value
    extra = {'cargo_title':'Cargaison du speeder','cargo_capacity':'%s places · masse %s',
             'cargo_help':'Clic : transférer la pile. Clic droit : un objet. Sans curseur. Les cases grisées restent récupérables si elles contiennent des objets.',
             'cargo_denied':'Votre speeder doit avoir un coffre, être proche, vide de passagers et à l’arrêt.',
             'recharge_cargo_moved':'Transfert sauvegardé','recharge_cargo_full':'Pile vide ou place insuffisante','recharge_cargo_not_empty':'Videz la cargaison avant recyclage'} if locale=='fr_fr' else {
             'cargo_title':'Speeder cargo','cargo_capacity':'%s slots · mass %s',
             'cargo_help':'Click: transfer stack. Right click: one item. No cursor. Items in disabled slots can still be retrieved.',
             'cargo_denied':'Your speeder needs a cargo crate; park it nearby without passengers.',
             'recharge_cargo_moved':'Transfer saved','recharge_cargo_full':'Empty stack or insufficient space','recharge_cargo_not_empty':'Unload cargo before recycling'}
    for key,value in extra.items(): data['speeder.legacyoftheforce.'+key]=value
    extra = {'grid_help':'Grilles5×3×3 /9×5×4 /12×7×5. Rétrécissement refusé si des pièces seraient perdues ;128 modules maximum.',
             'class_light':'Classe légère','class_utility':'Classe polyvalente','class_heavy':'Classe lourde','seats':'Places :%s',
             'occupancy':' · Places %s/%s','passenger_controls':'Passager · %s : descendre'} if locale=='fr_fr' else {
             'grid_help':'Grids5×3×3 /9×5×4 /12×7×5. Shrinking never discards parts; at most128 modules.',
             'class_light':'Light class','class_utility':'Utility class','class_heavy':'Heavy class','seats':'Seats: %s',
             'occupancy':' · Seats %s/%s','passenger_controls':'Passenger · %s: dismount'}
    for key,value in extra.items(): data['speeder.legacyoftheforce.'+key]=value
    write(path,data)
