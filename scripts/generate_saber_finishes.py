"""Original machined finishes and physical grip inlays; no copied media or shader dependency."""
import copy,json,math,random
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
ASSETS=ROOT/'assets/legacyoftheforce'
FINISHES=['original','silver','blackened','gold','copper','enamel']


def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def materials():
    # Periodic machining plus seeded fine wear, baked albedo for vanilla lighting.
    original={'alloy':(160,173,181),'edge':(209,221,225),'gunmetal':(51,62,72),'grip':(30,35,39),'copper':(170,99,61),'indicator':(65,194,221),'button':(136,33,30)}
    tones={**original,'satin':(194,194,194),'polished':(233,233,233),'oxide':(164,164,164),'enamel':(231,231,231),'etch':(212,212,212)}
    for index,(name,base)in enumerate(tones.items()):
        rng=random.Random(7891+index);image=Image.new('RGB',(64,64))
        for y in range(64):
            for x in range(64):
                grain=math.sin(y*math.pi)*1.2+math.sin(y*math.pi/2)*2.1+rng.uniform(-2,2)
                if name in ('satin','alloy','copper','polished','edge'):
                    grain+=1.3*math.sin(x*math.pi/16)+1.7*math.sin(y*math.pi/8)
                    if y%17==3 and x%29<10:grain+=6 if y%2 else -5
                elif name in ('grip','oxide'):
                    grain=(4 if(x+y)%8==0 else -3 if(x-y)%8==0 else 0)+rng.uniform(-2,2)
                elif name=='enamel':grain=rng.uniform(-1.5,1.5)+2.4*math.cos(x*math.pi/32)
                elif name in ('indicator','button'):grain=6*math.cos((x-31.5)*math.pi/64)
                image.putpixel((x,y),tuple(max(0,min(255,round(c+grain)))for c in base))
        path=ASSETS/f'textures/item/saber/{name if name in original else "finish/"+name}.png';path.parent.mkdir(parents=True,exist_ok=True);image.save(path)


def prepare(model):
    for element in model.get('elements',[]):
        for face in element['faces'].values():
            if face['texture'] in ('#alloy','#edge','#copper','#grip'):face['tintindex']=0
    model.setdefault('textures',{})['etch']='legacyoftheforce:item/saber/finish/etch'


def inlay(model,pattern):
    if pattern==0:return
    elements=model['elements'];lo=min(e['from'][1]for e in elements);hi=max(e['to'][1]for e in elements);front=min(e['from'][2]for e in elements);back=max(e['to'][2]for e in elements)
    left=min(e['from'][0]for e in elements);right=max(e['to'][0]for e in elements);center=(left+right)/2;span=(right-left)*.48;height=(hi-lo)*.65;bottom=lo+(hi-lo)*.16
    shapes=[]
    if pattern==1:
        for t in [.12,.5,.88]:shapes.append((center-span*.38,bottom+height*t,span*.76,.055))
    elif pattern==2:
        for dx,t in [(-.22,.2),(.18,.36),(-.05,.58),(.2,.81)]:shapes.append((center+dx*span,bottom+height*t,.085,.085))
        shapes.append((center-span*.12,bottom+height*.36,.06,height*.22))
    else:
        shapes=[(center-span*.25,bottom,.055,height),(center+span*.22,bottom+height*.12,.055,height*.78),(center-span*.25,bottom+height*.3,span*.5,.055),(center-span*.25,bottom+height*.7,span*.5,.055)]
    for x,y,w,h in shapes:
        for z in [front-.035,back+.012]:
            elements.append({'from':[round(x,3),round(y,3),round(z,3)],'to':[round(x+w,3),round(y+h,3),round(z+.018,3)],'shade':False,'faces':{side:{'texture':'#etch','tintindex':1}for side in ['north','south','east','west','up','down']}})


def models():
    count=0
    for data in sorted((ROOT/'data/legacyoftheforce/saber_parts').glob('*.json')):
        name=data.stem
        if name.startswith('circuit_'):continue
        path=ASSETS/f'models/item/{name}.json';source=json.loads(path.read_text(encoding='utf-8'));prepare(source)
        source['overrides']=[o for o in source.get('overrides',[])if not o.get('model','').startswith('legacyoftheforce:item/saber_finish/')]
        engravings=range(4)if name.startswith('grip_')else range(1)
        # More specific engraving predicates come after plain finish predicates.
        for finish in range(6):
            for engraving in engravings:
                if finish==0 and engraving==0:continue
                variant=copy.deepcopy(source);variant.pop('overrides',None)
                if finish:
                    material='oxide'if finish==2 else 'enamel'if finish==5 else 'satin'
                    for key in ['alloy','copper','grip']:variant['textures'][key]='legacyoftheforce:item/saber/finish/'+material
                    variant['textures']['edge']='legacyoftheforce:item/saber/finish/polished'
                if engraving:inlay(variant,engraving)
                target=f'item/saber_finish/{name}_{finish}_{engraving}'
                write(ASSETS/f'models/{target}.json',variant);predicate={'legacyoftheforce:finish':finish}
                if engraving:predicate['legacyoftheforce:engraving']=engraving
                source['overrides'].append({'predicate':predicate,'model':'legacyoftheforce:'+target});count+=1
        write(path,source)
    print(f'Authored {count} finish/inlay model variants; native render validation pending.')


def prices_and_language():
    values={'original':('iron_nugget',4),'silver':('iron_nugget',4),'blackened':('ink_sac',1),'gold':('gold_nugget',4),'copper':('copper_ingot',1),'enamel':('white_dye',1),'engraving':('iron_nugget',4)}
    for key,(item,count)in values.items():write(ROOT/f'data/legacyoftheforce/saber_finishes/{key}.json',{'schemaVersion':1,'item':'minecraft:'+item,'count':count})
    for locale,fr in [('fr_fr',True),('en_us',False)]:
        path=ASSETS/f'lang/{locale}.json';v=json.loads(path.read_text(encoding='utf-8'))
        names=['Origine','Argent brossé','Métal noirci','Dorure','Cuivre','Émail peint']if fr else ['Original','Brushed silver','Blackened metal','Gold plating','Copper','Painted enamel']
        for key,name in zip(FINISHES,names):v['finish.legacyoftheforce.'+key]=name
        for i,name in enumerate(['Sans gravure ajoutée','Anneaux gravés','Constellation','Circuit gravé']if fr else ['No added engraving','Etched rings','Constellation','Etched circuit']):v['engraving.legacyoftheforce.'+str(i)]=name
        entries={'style_toggle':'Finitions'if fr else 'Finishes','finish_paint':'Peinture suivante'if fr else 'Next paint color','finish_paint_help':'Choisir une teinture vanilla et appliquer l’émail à cette zone du brouillon.'if fr else 'Choose a vanilla dye and apply enamel to this draft zone.','finish_apply':'Finir le sabre tenu'if fr else 'Finish held saber','finish_zone_help':'Changer de zone physique. Le cristal, la lame et le circuit ne sont pas recolorés.'if fr else 'Select a physical zone. Kyber, blade and circuit are not recolored.','finish_cost':'Coût de finition du sabre tenu, sans changer ses pièces :'if fr else 'Cost to finish the held saber without changing its parts:','finish_craft_cost':'Supplément cosmétique pour un nouveau sabre :'if fr else 'Cosmetic surcharge for a new saber:','finish_no_cost':'Aucun matériau de finition supplémentaire.'if fr else 'No additional finishing materials.','finished':'Finition sauvegardée ; cristal et identité conservés.'if fr else 'Finish saved; kyber and identity preserved.','finish_materials':'Matériaux ordinaires de finition manquants.'if fr else 'Missing ordinary finishing materials.','finish_unavailable':'Catalogue de finition indisponible ou choix non pris en charge.'if fr else 'Finish catalog unavailable or selection unsupported.'}
        entries['engraving_help']='Gravure cosmétique ajoutée à la poignée. Les reliefs d’une pièce déjà gravée restent présents ; changer de pièce demande une modification distincte.' if fr else 'Additional cosmetic grip engraving. Relief on an already engraved part remains; changing the part is a separate modification.'
        for key,text in entries.items():v['workshop.legacyoftheforce.'+key]=text
        write(path,v)


def main():
    materials();models();prices_and_language()
    import runpy
    runpy.run_path(str(Path(__file__).with_name("generate_rare_transfer_labels.py")),run_name="__main__")

if __name__=='__main__':main()
