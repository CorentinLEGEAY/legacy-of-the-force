"""Original environmental labels and module adaptation tags; no external assets."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def main():
    for kind,parts in {
        'snow':['thruster_cryo'],
        'sand':['engine_racer'],
        'mud':['stabilizer_levitation','chassis_forest'],
        'heat':['engine_high_power'],
        'storm':['stabilizer_gyro','stabilizer_levitation'],
    }.items():
        write(ROOT/f'data/legacyoftheforce/tags/item/speeder_environment/{kind}.json',{'replace':False,'values':['legacyoftheforce:'+part for part in parts]})
    names={'fr_fr':{'snow':'Neige','sand':'Sable','mud':'Boue','heat':'Chaleur volcanique','storm':'Intempéries'},'en_us':{'snow':'Snow','sand':'Sand','mud':'Mud','heat':'Volcanic heat','storm':'Rough weather'}}
    for locale,labels in names.items():
        path=ROOT/f'assets/legacyoftheforce/lang/{locale}.json'
        values=json.loads(path.read_text(encoding='utf-8'))
        values['travel.legacyoftheforce.cold_warning']='Le froid vous ralentit. Cherchez un abri ou portez une pièce d’armure en cuir.' if locale=='fr_fr' else 'The cold slows you. Find shelter or wear a piece of leather armor.'
        for kind,label in labels.items():
            values['speeder.legacyoftheforce.environment.'+kind]=label+(' : vitesse −%1$s%%, énergie +%2$s%%. Un module adapté atténue cet effet.' if locale=='fr_fr' else ': speed −%1$s%%, energy +%2$s%%. An adapted module reduces this effect.')
        write(path,values)

if __name__=='__main__':main()
