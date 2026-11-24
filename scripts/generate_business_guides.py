"""Optional business guide uses durable owner witnesses, never inventory possession as crafting proof."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def main():
    prefix='quest.legacyoftheforce.path.'
    steps=[('assembled','saber_assembled','Assembler un sabre à l’atelier, avec ses vrais composants.','Assemble a saber at the workshop using real parts.'),
           ('mounted','crystal_mounted','Sertir un cristal identifié dans un sabre compatible.','Install identified kyber in a compatible saber.'),
           ('force','force_block_used','Déplacer puis déposer réellement un bloc ou groupe autorisé avec la Force.','Actually move and deposit a permitted block or group using the Force.'),
           ('speeder','speeder_assembled','Fabriquer un speeder payé, publié et activé depuis un plan valide.','Fabricate a paid, published and activated speeder from a valid blueprint.'),
           ('help','help_confirmed','Facultatif : confirmer une aide à un PNJ, avec réputation relationnelle distincte.','Optional: confirm help for an NPC, earning separate relational reputation.')]
    write(ROOT/'data/legacyoftheforce/quests/path_of_traveler.json',{'schemaVersion':3,'title':prefix+'title','origin':prefix+'origin','clue':prefix+'clue','unlockWith':'legacyoftheforce:echo_of_ilum','repeatable':False,'dependencies':[],'rewards':{'xp':0,'alignmentTenths':0},'steps':[{'id':key,'text':prefix+key,'condition':'MILESTONE','subject':'legacyoftheforce:'+subject}for key,subject,fr,en in steps]})
    for locale,fr in [('fr_fr',True),('en_us',False)]:
        path=ROOT/f'assets/legacyoftheforce/lang/{locale}.json';v=json.loads(path.read_text(encoding='utf-8'))
        v[prefix+'title']='L’établi du voyageur' if fr else 'The traveler’s workbench'
        v[prefix+'origin']='Guide facultatif issu de l’écho d’Ilum ; étapes libres, sans récompense supplémentaire.' if fr else 'Optional guide from the Ilum echo; freely ordered steps without additional rewards.'
        v[prefix+'clue']='Fabriquez un atelier et un terminal, explorez Ilum, puis revenez sertir le cristal. Un plan de speeder reste réutilisable. Une simple possession ou un clic ne prouve pas une fabrication ; les traces anciennes non démontrables ne sont pas inventées.' if fr else 'Craft a workshop and terminal, explore Ilum, then return to install kyber. A speeder blueprint remains reusable. Possession or a click does not prove fabrication; unverifiable older history is never invented.'
        for key,subject,text_fr,text_en in steps:v[prefix+key]=text_fr if fr else text_en
        write(path,v)

if __name__=='__main__':main()
