"""Shared saber/loose-kyber transfer labels; legacy translation keys remain stable."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/legacyoftheforce/lang'
for locale,fr in [('fr_fr',True),('en_us',False)]:
    path=ROOT/f'{locale}.json';v=json.loads(path.read_text(encoding='utf-8'))
    entries={'transfer_unavailable':'Transfert de l’objet rare indisponible ou trop rapproché ; objet conservé.' if fr else 'Rare item transfer unavailable or too soon; item retained.','transfer_pending':'Transfert de l’objet rare en récupération. Reconnectez-vous dans quelques instants ; si le stockage reste indisponible, faites corriger le serveur.' if fr else 'Rare item transfer is recovering. Reconnect shortly; if storage remains unavailable, have the server storage repaired.','transfer_clear_cursor':'Posez d’abord l’objet tenu par la souris ; objet rare conservé.' if fr else 'Set down the cursor item first; rare item retained.','transfer_close_menu':'Fermez les menus et videz la grille de fabrication ; objet rare conservé.' if fr else 'Close menus and empty the crafting grid; rare item retained.'}
    for key,value in entries.items():v['saber.legacyoftheforce.'+key]=value
    path.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
