"""Original faceted crystal meshes/materials and planetary affinity data.

Colors communicate families alongside explicit names; materials are generated from
deterministic pixel patterns rather than extracted game/film art.
"""
import json
from pathlib import Path
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
ASSETS=ROOT/'assets/legacyoftheforce';DATA=ROOT/'data/legacyoftheforce'
COLORS={'blue':(63,141,230),'yellow':(227,183,50),'green':(65,179,107),'orange':(228,112,39),'cyan':(60,185,201),'purple':(154,99,214),'red':(201,46,42),'white':(217,236,240)}

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def texture(name,base):
    image=Image.new('RGB',(32,32))
    for y in range(32):
        for x in range(32):
            light=((x//4)%2)*16-8+((x*19+y*11)%9)-4+(8 if (x+y)%23==0 else 0)
            image.putpixel((x,y),tuple(max(0,min(255,c+light)) for c in base))
    glyphs={'blue':[(10,22),(10,10),(16,16),(22,10),(22,22)],'yellow':[(16,8),(23,16),(16,24),(9,16),(16,8)],
            'green':[(16,24),(16,15),(9,9),(16,15),(23,9)],'orange':[(10,10),(22,13),(11,19),(22,23)],
            'cyan':[(9,12),(16,9),(23,12),(9,22),(16,19),(23,22)],'purple':[(16,7),(16,25),(7,16),(25,16)],
            'red':[(13,6),(19,13),(13,19),(18,26)],'white':[(10,10),(22,10),(24,16),(22,22),(10,22),(8,16),(10,10)]}
    if name in glyphs:ImageDraw.Draw(image).line(glyphs[name],fill=tuple(min(255,c+65) for c in base),width=2)
    path=ASSETS/f'textures/item/kyber/{name}.png';path.parent.mkdir(parents=True,exist_ok=True);image.save(path)

def shard(x,z,y,height,width,texture_name,rotation=0):
    cubes=[]
    for a,b,r in [(0,.14,.4),(.14,.3,.75),(.3,.75,1),(.75,.9,.7),(.9,1,.3)]:
        radius=width*r;cube={'from':[x-radius,y+a*height,z-radius],'to':[x+radius,y+b*height,z+radius],
            'faces':{face:{'texture':'#body','uv':[0,0,16,16]} for face in ('up','down','north','south','east','west')}}
        if rotation:cube['rotation']={'origin':[x,y,z],'axis':'y','angle':rotation,'rescale':False}
        cubes.append(cube)
    return cubes

for index,(name,rgb) in enumerate(COLORS.items()):
    texture(name,rgb)
    shapes={
        'blue':[(8,8,1.5,12,2),(5.5,8.5,2,7,1.1)],
        'yellow':[(8,8,1,13,1.5),(10.5,7.5,3,6,1)],
        'green':[(8,8,2,10,2.2),(5.2,8.5,2,7,1.2),(10.8,8,2,5,1)],
        'orange':[(8,8,2.5,8,2.7),(5.2,8,2,6,1.3),(11,7.5,2,5,1.2)],
        'cyan':[(6.8,8,1.5,12,1.25),(9.7,8,2,10,1.3)],
        'purple':[(8,8,3,9,2.6),(5,8,3,6,1.3),(11,8,3,6,1.3)],
        'red':[(8,8,1,12,1.8),(10.5,7.5,3,7,1.2)],
        'white':[(8,8,2,11,2.4)]}
    elements=[cube for x,z,y,h,w in shapes[name] for cube in shard(x,z,y,h,w,name,22.5)]
    model={'textures':{'body':'legacyoftheforce:item/kyber/'+name,'particle':'legacyoftheforce:item/kyber/'+name},'elements':elements,
        'display':{'gui':{'rotation':[20,225,-12],'scale':[1.05]*3},'ground':{'rotation':[0,0,70],'translation':[0,2,0],'scale':[.45]*3},
                   'firstperson_righthand':{'rotation':[0,45,0],'scale':[.5]*3},'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.5]*3}}}
    write(ASSETS/f'models/item/crystal_{name}.json',model)
write(ASSETS/'models/item/ilum_crystal.json',{'parent':'legacyoftheforce:item/crystal_blue','overrides':[{'predicate':{'legacyoftheforce:crystal_color':i},'model':'legacyoftheforce:item/crystal_'+name} for i,name in enumerate(COLORS) if i>0]})

materials=[('ilum_frost_alloy',(118,161,184)),('tatooine_silica',(183,143,83)),('endor_resin',(112,123,56)),('mustafar_slag',(89,54,49)),('dagobah_essence',(63,123,130)),('jedha_relic_dust',(139,110,160))]
for name,rgb in materials:
    texture(name,rgb)
    write(ASSETS/f'models/item/{name}.json',{'textures':{'body':'legacyoftheforce:item/kyber/'+name,'particle':'legacyoftheforce:item/kyber/'+name},
        'elements':shard(8,8,3,8,3,name,22.5),'display':{'gui':{'rotation':[25,225,0],'scale':[1.1]*3},'ground':{'rotation':[0,0,70],'translation':[0,2,0],'scale':[.4]*3}}})

profiles=[('ilum',1,1,1,1,1,0,.05),('tatooine',0,.94,1,1,1,0,.15),('endor',0,1,1.08,1,1,0,.15),('mustafar',0,1.06,1,1,1.04,.5,.15),('dagobah',0,1,1,.92,1,0,.15),('jedha',0,1,1,1,.93,0,.15)]
for planet,parry,endurance,regeneration,telekinesis,advanced,damage,growth in profiles:
    write(DATA/f'kyber_affinities/{planet}.json',{'schemaVersion':1,'parryScale':parry,'enduranceCost':endurance,'forceRegeneration':regeneration,'telekinesisCost':telekinesis,'advancedCost':advanced,'damageBonus':damage,'growth':growth,'focusStrength':.5,'focusCost':.97})

for locale,french in [('fr_fr',True),('en_us',False)]:
    path=ASSETS/f'lang/{locale}.json';strings=json.loads(path.read_text(encoding='utf-8'))
    kind_names=['Cristal d’Ilum','Cristal des dunes','Cristal sylvestre','Cristal volcanique','Cristal des marais','Cristal sacré'] if french else ['Ilum crystal','Dune crystal','Woodland crystal','Volcanic crystal','Marsh crystal','Sacred crystal']
    for (planet,*_),label in zip(profiles,kind_names):strings['crystal.legacyoftheforce.kind_'+planet]=label;strings['crystal.legacyoftheforce.planet_'+planet]=planet.capitalize()
    names=['Alliage givré d’Ilum','Silice des dunes','Résine sylvestre','Scorie volcanique','Essence des marais','Poussière de relique'] if french else ['Ilum frost alloy','Dune silica','Woodland resin','Volcanic slag','Marsh essence','Relic dust']
    for (name,_),label in zip(materials,names):strings['item.legacyoftheforce.'+name]=label
    messages={'origin_named':'Origine : %s','state_natural':'État naturel','state_corrupted':'Corrompu · rouge','state_purified':'Purifié · blanc','affinity_defense':'Affinité : parade et précision','affinity_endurance':'Affinité : économie d’endurance','affinity_restoration':'Affinité : régénération de Force','affinity_offense':'Affinité : puissance et consommation accrues','affinity_telekinesis':'Affinité : économie de télékinésie','affinity_technique':'Affinité : économie des pouvoirs avancés','affinity_active':'Affinité disponible en tenant le sabre actif.','specialization':'Spécialisation : %s'} if french else {'origin_named':'Origin: %s','state_natural':'Natural state','state_corrupted':'Corrupted · red','state_purified':'Purified · white','affinity_defense':'Affinity: parry and precision','affinity_endurance':'Affinity: endurance efficiency','affinity_restoration':'Affinity: Force regeneration','affinity_offense':'Affinity: power and increased consumption','affinity_telekinesis':'Affinity: telekinesis efficiency','affinity_technique':'Affinity: advanced power efficiency','affinity_active':'Affinity available while holding the active saber.','specialization':'Specialization: %s'}
    for key,value in messages.items():strings['crystal.legacyoftheforce.'+key]=value
    for name,fr,en in [('defense','défense','defense'),('endurance','endurance','endurance'),('restoration','régénération','regeneration'),('offense','offensive','offense'),('telekinesis','télékinésie','telekinesis'),('technique','technique','technique'),('focus','concentration','focus')]:strings['crystal.legacyoftheforce.specialization_'+name]=fr if french else en
    messages={'corrupt':'Corrompre','purify':'Purifier','focus':'Concentration','corrupted':'Cristal corrompu ; identité conservée','purified':'Cristal purifié ; identité conservée','ritual_alignment':'Alignement incompatible avec ce rituel','ritual_level':'Niveau insuffisant pour ce rituel','ritual_materials':'Matériaux du rituel manquants',
        'corrupt_help':'Niveau %s · alignement ≤ %s. Coût : %s scories de Mustafar + %s poudres de blaze. Conserve origine, identité et lien.',
        'purify_help':'Cristal corrompu · niveau %s · alignement ≥ %s. Coût : %s poussières de Jedha + %s améthystes. Conserve origine, identité et lien.',
        'focus_help':'Cristal harmonisé, progrès ≥ %s. Coût : %s essence de Dagobah. Moitié du bonus d’affinité contre une économie générale de pouvoirs avancés.'} if french else {'corrupt':'Corrupt','purify':'Purify','focus':'Focus','corrupted':'Crystal corrupted; identity preserved','purified':'Crystal purified; identity preserved','ritual_alignment':'Alignment does not meet this ritual’s requirement','ritual_level':'Level does not meet this ritual’s requirement','ritual_materials':'Missing ritual materials',
        'corrupt_help':'Level %s · alignment ≤ %s. Cost: %s Mustafar slag + %s blaze powder. Preserves origin, identity and bond.',
        'purify_help':'Corrupted crystal · level %s · alignment ≥ %s. Cost: %s Jedha dust + %s amethyst. Preserves origin, identity and bond.',
        'focus_help':'Harmonized crystal, progress ≥ %s. Cost: %s Dagobah essence. Half affinity strength for general advanced power efficiency.'}
    for key,value in messages.items():strings['workshop.legacyoftheforce.'+key]=value
    strings['saber.legacyoftheforce.needs_awakening']='Éveillez le cristal à l’atelier avant d’activer la lame.' if french else 'Awaken the crystal at a workshop before igniting the blade.'
    write(path,strings)
