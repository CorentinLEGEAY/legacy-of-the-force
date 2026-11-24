"""Localized explanations for server-projected handling; no client simulation."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/legacyoftheforce/lang'
for locale,fr in [('fr_fr',True),('en_us',False)]:
    path=ROOT/f'{locale}.json';v=json.loads(path.read_text(encoding='utf-8'))
    entries={'handling':'Direction %1$s%% · biais %2$s%%' if fr else 'Steering %1$s%% · trim %2$s%%','handling_response':'Dir. %s%%' if fr else 'Steer %s%%','handling_trim':'Biais %s%%' if fr else 'Trim %s%%','handling_help':'Une masse dispersée ralentit la direction ; rapprochez les pièces lourdes. Une poussée décentrée crée une légère tendance à tourner : équilibrez les propulseurs autour du centre de masse ou ajoutez des stabilisateurs. La cargaison et les occupants comptent. À grande vitesse, la direction s’adoucit. Ces valeurs viennent du serveur, sans effet des peintures.' if fr else 'Spread-out mass slows steering; bring heavy modules closer. Off-center thrust creates a slight turning tendency: balance thrusters around the mass center or add stabilizers. Cargo and riders count. Steering softens at higher speed. These values come from the server; paint has no effect.'}
    for key,text in entries.items():v['speeder.legacyoftheforce.'+key]=text
    path.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
