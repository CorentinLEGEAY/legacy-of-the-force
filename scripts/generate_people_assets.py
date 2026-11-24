"""Seven original procedural costumes, optional dialogues and common barter definitions."""
import json,random
from pathlib import Path
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
ROLES=['jedi','hermit','sith','explorer','merchant','pilot','hunter']

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def costumes():
    palettes=[((117,91,63),(228,200,155),(128,81,54)),((78,89,72),(145,158,113),(162,112,76)),((44,37,48),(148,48,56),(195,145,111)),((65,97,113),(204,161,81),(173,119,76)),((101,65,43),(207,166,79),(112,72,53)),((66,87,111),(212,140,60),(194,148,111)),((59,76,70),(141,158,145),(128,82,65))]
    regions=[(0,0,31,15),(16,16,39,31),(40,16,55,31),(0,16,15,31),(16,48,31,63),(32,48,47,63)]
    for index,(role,(cloth,trim,skin)) in enumerate(zip(ROLES,palettes)):
        rng=random.Random(1197+index);im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
        for region in regions:
            for y in range(region[1],region[3]+1):
                for x in range(region[0],region[2]+1):
                    base=skin if y<16 else cloth;n=rng.randint(-7,7);im.putpixel((x,y),tuple(max(0,min(255,c+n)) for c in base)+(255,))
        # Front-facing eyes, brow, nose, hair, collars, woven seams, belt and small metal buckles.
        d.rectangle((8,8,15,9),fill=cloth+(255,));d.point((9,11),fill=(30,37,41));d.point((14,11),fill=(30,37,41));d.line((11,12,11,13),fill=tuple(max(0,c-20) for c in skin));d.line((10,14,13,14),fill=(95,61,49))
        d.line((20,20,23,24),fill=trim,width=2);d.line((27,20,24,24),fill=trim,width=2)
        d.rectangle((20,28,27,29),fill=(43,38,32));d.rectangle((23,28,24,29),fill=trim)
        for x in (20,27):d.line((x,22,x,27),fill=tuple(max(0,c-22) for c in cloth))
        d.rectangle((44,28,47,31),fill=(47,40,35));d.rectangle((36,60,39,63),fill=(47,40,35))
        for x,y in ((4,28),(20,60)):d.rectangle((x,y,x+3,y+3),fill=(36,34,31))
        if role in ('pilot','hunter'):d.rectangle((8,10,15,11),fill=(36,43,47));d.line((9,10,14,10),fill=(121,188,195));d.line((20,21,27,27),fill=trim,width=2)
        if role=='merchant':d.rectangle((25,23,27,26),fill=trim);d.point((26,24),fill=(245,217,136))
        if role in ('jedi','sith','hermit'):
            d.rectangle((32,0,63,15),fill=cloth+(255,));d.rectangle((41,9,46,15),fill=(0,0,0,0));d.line((20,30,27,30),fill=trim)
        if role=='explorer':d.line((20,21,27,26),fill=(76,51,33),width=2);d.rectangle((25,25,27,27),fill=trim)
        path=ROOT/f'assets/legacyoftheforce/textures/entity/people/{role}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)


def main():
    for role in ROLES:
        help_item='minecraft:bread' if role in ('jedi','hermit','explorer') else 'minecraft:copper_ingot' if role in ('merchant','pilot') else 'minecraft:iron_ingot'
        value={'schemaVersion':1,'help':{'item':help_item,'count':4}}
        if role in ('merchant','pilot','hunter'):
            value['trade']={'price':{'item':'minecraft:emerald','count':3 if role=='merchant' else 2},'result':'legacyoftheforce:speeder_repair_kit' if role=='merchant' else 'legacyoftheforce:speeder_energy_cell' if role=='pilot' else 'minecraft:redstone','count':4 if role=='hunter' else 1,'minimumReputation':0}
        write(ROOT/f'data/legacyoftheforce/people/{role}.json',value)
    path=ROOT/'data/legacyoftheforce/tags/entity_type/force_immune_mobs.json';tag=json.loads(path.read_text(encoding='utf-8'))
    for role in ROLES:
        value='legacyoftheforce:person_'+role
        if value not in tag['values']:tag['values'].append(value)
    write(path,tag)
    names={'fr_fr':['Maître itinérant','Ermite des chemins','Émissaire Sith','Exploratrice des ruines','Marchande de composants','Pilote de liaison','Chasseur de primes'],'en_us':['Wandering master','Wayside hermit','Sith envoy','Ruin explorer','Component merchant','Liaison pilot','Bounty hunter']}
    clues_fr=["Le sabre se construit pièce par pièce. Une forme apprise n’est utile qu’avec un assemblage compatible. Cherchez les holocrons, puis entraînez-vous.","Sur Ilum, une pièce de cuir ou un abri suffit contre le froid. Le bleu vient des cavernes ; son identité survit au sertissage.","Les sanctuaires proposent un duel facultatif. Lisez l’autel avant de provoquer un adversaire ; aucune allégeance n’est exigée pour explorer.","Un carnet d’expédition rassemble les indices du monde visité. Le cristal conserve sa véritable origine, même si sa couleur change.","Les matériaux planétaires améliorent des usages différents. Conservez votre plan réutilisable et réparez les pièces endommagées au garage.","Le terminal réserve l’énergie du retour. Votre speeder reste dans sa dimension ; garez-le avant de voyager. Une batterie vide se recharge au garage.","La victoire exige une participation réelle. Le reliquaire et la récupération personnelle limitent les récompenses ; les autres boss restent accessibles."]
    clues_en=["A saber is built part by part. A learned form still needs a compatible assembly. Seek holocrons, then practise.","On Ilum, leather armor or shelter protects against cold. Blue crystals come from caverns; their identity survives mounting.","Sanctuaries offer optional duels. Read the altar before provoking an opponent; exploring requires no allegiance.","An expedition journal gathers clues for a visited world. A crystal keeps its true origin even when its color changes.","Planetary materials improve different uses. Keep your reusable blueprint and repair damaged modules at a garage.","The terminal prepays your return. Your speeder stays in its dimension; park before traveling. Recharge empty batteries at a garage.","Victory requires real participation. The reliquary and personal cooldown limit rewards; other bosses remain available."]
    for locale in names:
        path=ROOT/f'assets/legacyoftheforce/lang/{locale}.json';v=json.loads(path.read_text(encoding='utf-8'));fr=locale=='fr_fr'
        values={
            'ask':'Demander un conseil' if fr else 'Ask for advice','offer_help':'Proposer une aide' if fr else 'Offer help','barter':'Voir le troc' if fr else 'View barter','back':'Retour' if fr else 'Back',
            'confirm_help':'Confirmer le don' if fr else 'Confirm donation','confirm_trade':'Confirmer le troc' if fr else 'Confirm barter',
            'relations':'%1$s : %2$s · alignement %3$s' if fr else '%1$s: %2$s · alignment %3$s',
            'optional':'Aide facultative. Réputation et alignement sont distincts ; aucun gain d’XP.' if fr else 'Optional help. Reputation is separate from alignment; no XP is granted.',
            'help_done':'Cette aide est déjà enregistrée pour vous. Elle ne peut pas être payée deux fois.' if fr else 'Your help is already recorded. It cannot be rewarded twice.',
            'help_request':'Donner %1$s × %2$s ordinaires ? Ce don est consommé, une seule fois par rôle, contre +%3$s de réputation auprès de ce groupe. Aucun changement d’alignement.' if fr else 'Donate %1$s × ordinary %2$s? This consumes the donation once per role in exchange for +%3$s reputation with this group. Alignment is unchanged.',
            'trade_request':'Échanger %1$s × %2$s contre %3$s × %4$s ? Réputation requise : %5$s. Seuls les objets ordinaires sont acceptés ; le délai est personnel et commun aux PNJ.' if fr else 'Trade %1$s × %2$s for %3$s × %4$s? Required reputation: %5$s. Only ordinary items are accepted; the cooldown is personal and shared across NPCs.',
            'action_refused':'Action refusée : aide déjà enregistrée, objets ordinaires manquants, inventaire plein, réputation insuffisante ou récupération en cours.' if fr else 'Action refused: help already recorded, missing ordinary items, full inventory, insufficient reputation or cooldown.',
            'save_pending':'Sauvegarde de conversation incertaine. Paiement et progression conservés ; reconnectez-vous après résolution du stockage.' if fr else 'Conversation save uncertain. Payment and progress retained; reconnect after storage recovers.'}
        for key,text in values.items():v['people.legacyoftheforce.'+key]=text
        for key,text in zip(['jedi','sith','travelers'],['Gardiens Jedi','Cercle Sith','Voyageurs indépendants'] if fr else ['Jedi guardians','Sith circle','Independent travelers']):v['people.legacyoftheforce.faction.'+key]=text
        for i,role in enumerate(ROLES):
            name=names[locale][i];v['people.legacyoftheforce.role.'+role]=name;v['entity.legacyoftheforce.person_'+role]=name
            prefix='people.legacyoftheforce.'+role+'.'
            v[prefix+'welcome']=(name+' : « Nous pouvons discuter sans engagement. Vous cherchez un conseil, ou souhaitez-vous aider nos voyageurs ? »') if fr else name+': “We can talk without commitment. Do you seek advice, or wish to help our travelers?”'
            v[prefix+'trusted']='Votre aide a été remarquée. Vous êtes libre de suivre nos conseils ou de poursuivre votre propre voie.' if fr else 'Your help has been noticed. You remain free to follow our advice or choose your own path.'
            v[prefix+'cautious']='Nos convictions divergent, mais je vous écouterai. Une aide concrète peut changer nos relations ; vos voyages restent libres.' if fr else 'Our beliefs differ, but I will listen. Practical help can change our relationship; your travels remain free.'
            v[prefix+'clue']=(clues_fr if fr else clues_en)[i]
        write(path,v)
    costumes()

if __name__=='__main__':main()
