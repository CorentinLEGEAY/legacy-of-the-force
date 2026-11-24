"""Original geometry decals, independent paint zones and emissive lamp lens for pinned NeoForge."""
import copy,json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
ASSETS=ROOT/'assets/legacyoftheforce'
BODIES={'chassis_light','chassis_forest','body_cowling','armor_plate'}

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def patch_materials(model):
    model.setdefault('textures',{})['lighting']='legacyoftheforce:item/speeder_lens'
    model['textures']['motif']='minecraft:block/white_concrete'
    for element in model.get('elements',[]):
        for face in element['faces'].values():
            texture=face['texture']
            if texture=='#edge':face['tintindex']=1
            if texture=='#indicator':face['texture']='#lighting';face['tintindex']=2

def box(x1,y,z1,x2,z2):
    return {'from':[round(x1,3),round(y,3),round(z1,3)],'to':[round(x2,3),round(y+.025,3),round(z2,3)],'shade':False,'faces':{face:{'texture':'#motif','tintindex':1}for face in ['up','down','north','south','west','east']}}

def decorated(model,pattern):
    result=copy.deepcopy(model);result.pop('overrides',None)
    candidates=[e for e in result['elements']if(e['to'][0]-e['from'][0])*(e['to'][2]-e['from'][2])>=12]
    plate=max(candidates,key=lambda e:(e['to'][1],(e['to'][0]-e['from'][0])*(e['to'][2]-e['from'][2])))
    a,b=plate['from'],plate['to'];x1,x2=a[0]+.1,b[0]-.1;z1,z2=a[2]+.1,b[2]-.1;y=b[1]+.035;w=x2-x1;length=z2-z1
    if pattern==1:
        for t in [.2,.7]:result['elements'].append(box(x1+w*t,y,z1,x1+w*(t+.1),z2))
    elif pattern==2:
        for n in range(5):
            spread=(4-n)*w*.07;z=z1+length*(.15+n*.13)
            for side in [-1,1]:
                x=(x1+x2)/2+side*spread;result['elements'].append(box(x-w*.06,y,z,x+w*.06,z+length*.1))
    else:
        for t in [.28,.66]:result['elements'].append(box(x1+w*t,y,z1,x1+w*(t+.06),z2))
        for t in [.2,.48,.76]:result['elements'].append(box(x1+w*.28,y,z1+length*t,x1+w*.72,z1+length*(t+.04)))
    return result

def main():
    # A monochrome original lens preserves the selected RGB instead of multiplying an already blue texture.
    image=Image.new('RGBA',(16,16))
    for y in range(16):
        for x in range(16):
            c=max(80,min(255,242-abs(x-7)*9-abs(y-7)*4+(12 if(x+y)%4==0 else 0)))
            image.putpixel((x,y),(c,c,c,255))
    path=ASSETS/'textures/item/speeder_lens.png';path.parent.mkdir(parents=True,exist_ok=True);image.save(path)
    module_files=sorted((ROOT/'data/legacyoftheforce/speeder_modules').glob('*.json'))
    for module in module_files:
        name=module.stem;path=ASSETS/f'models/item/{name}.json';model=json.loads(path.read_text(encoding='utf-8'));patch_materials(model)
        # Remove only this generator’s own old overrides when rerun; keep unrelated authored predicates.
        model['overrides']=[o for o in model.get('overrides',[])if not o.get('model','').startswith('legacyoftheforce:item/speeder_style/')]
        if name in BODIES:
            for pattern in range(1,4):
                target=f'item/speeder_style/{name}_pattern_{pattern}'
                variant=decorated(model,pattern);write(ASSETS/f'models/{target}.json',variant)
                model['overrides'].append({'predicate':{'legacyoftheforce:pattern':pattern},'model':'legacyoftheforce:'+target})
        if name=='lamp_module':
            lit=copy.deepcopy(model);lit.pop('overrides',None)
            for element in lit['elements']:
                for face in element['faces'].values():
                    if face.get('tintindex')==2:face['neoforge_data']={'block_light':15,'sky_light':15,'ambient_occlusion':False}
            write(ASSETS/'models/item/speeder_style/lamp_powered.json',lit)
            model['overrides'].append({'predicate':{'legacyoftheforce:powered':1},'model':'legacyoftheforce:item/speeder_style/lamp_powered'})
        write(path,model)
    for locale,fr in [('fr_fr',True),('en_us',False)]:
        path=ASSETS/f'lang/{locale}.json';v=json.loads(path.read_text(encoding='utf-8'))
        words={'paint_accent':'Accents / décors' if fr else 'Accents / decals','paint_lighting':'Éclairage / lentilles' if fr else 'Lighting / lenses','paint_pattern':'Motif décoratif' if fr else 'Decorative pattern','pattern_select':'Motif' if fr else 'Pattern','paint_cost_short':'Teinture ×%s' if fr else 'Dye ×%s','paint_draft_short':'Brouillon' if fr else 'Draft','pattern_0':'Uni' if fr else 'Plain','pattern_1':'Doubles bandes' if fr else 'Twin stripes','pattern_2':'Chevrons' if fr else 'Chevrons','pattern_3':'Circuit' if fr else 'Circuit'}
        for key,value in words.items():v['speeder.legacyoftheforce.'+key]=value
        v['speeder.legacyoftheforce.pattern_cost_hint']='%1$s · teinture consommée : %2$s (choisie dans une zone couleur).' if fr else '%1$s · consumed dye: %2$s (selected in a color zone).'
        v['speeder.legacyoftheforce.paint_vehicle_help']=('Repeindre la zone choisie ou le motif pour %s teinture(s) de la couleur sélectionnée. Aucun changement de pièces, de charge ou de performances.' if fr else 'Repaint the selected zone or pattern for %s dye(s) of the selected color. Modules, charge and performance are unchanged.')
        v['speeder.legacyoftheforce.paint_plan_help']=('Appliquer la couleur ou le motif au brouillon. Enregistrer pour conserver les quatre zones ; construire pour créer un véhicule avec cette apparence.' if fr else 'Apply the color or pattern to the draft. Save to retain all four zones; build to create a vehicle with this appearance.')
        write(path,v)

if __name__=='__main__':main()
