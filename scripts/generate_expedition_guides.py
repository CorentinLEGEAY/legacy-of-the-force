"""Optional six-world guides and persistent teaching collection, without economic rewards."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
DATA, ASSETS = ROOT / 'data/legacyoftheforce', ROOT / 'assets/legacyoftheforce'
NS = 'legacyoftheforce:'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def generate():
    worlds = {
        'ilum': ('Ilum', 'ilum_guardian', 'ilum_frost_lord',
                 'Les tours donnent accès aux cavernes. Préparez une pioche de fer et un terminal pour rentrer.', 'Access towers lead to caverns. Bring an iron pickaxe and a terminal for the return trip.'),
        'tatooine': ('Tatooine', 'tatooine_wandering_knight', 'tatooine_desert_exile',
                    'Cherchez mines, habitats et épaves sur les dunes praticables. Couvrez-vous du soleil.', 'Look for mines, settlements and wrecks on traversable dunes. Shelter from the sun.'),
        'endor': ('Endor', 'endor_nature_keeper', 'endor_night_assassin',
                 'Les clairières et racines cachent grottes et ruines ; les hauteurs offrent des repères.', 'Clearings and roots hide caves and ruins; high lookouts help with navigation.'),
        'mustafar': ('Mustafar', 'mustafar_ruins_protector', 'mustafar_flame_lord',
                    'Préparez des protections contre le feu et cherchez des itinéraires stables hors des coulées.', 'Prepare fire protection and seek stable paths away from lava channels.'),
        'dagobah': ('Dagobah', 'dagobah_force_hermit', 'dagobah_illusion_master',
                   'Les petites hauteurs et huttes sur pilotis aident à retrouver les grottes. La perception dissipe les leurres.', 'Small rises and stilt huts help locate caves. Perception dispels decoys.'),
        'jedha': ('Jedha', 'jedha_temple_guardian', 'jedha_relic_lord',
                 'Les inscriptions donnent l’ordre des glyphes. Les cristaux des temples sont des ressources partagées.', 'Inscriptions give the glyph order. Temple crystals are shared resources.')}
    entries = {
        'quest.legacyoftheforce.save_pending': ('La sauvegarde du journal reste incertaine. Reconnectez-vous pour reprendre depuis les données natives.', 'Journal save remains uncertain. Reconnect to resume from native data.'),
        'quest.legacyoftheforce.section_1': ('Premières pistes', 'First leads'),
        'quest.legacyoftheforce.section_2': ('Expéditions planétaires', 'Planetary expeditions'),
        'quest.legacyoftheforce.section_3': ('Enseignements conservés', 'Retained teachings'),
        'quest.legacyoftheforce.section_empty': ('Aucune piste enregistrée dans cette rubrique.', 'No lead recorded in this section.'),
        'quest.legacyoftheforce.filter_0': ('Tout', 'All'), 'quest.legacyoftheforce.filter_1': ('Départ', 'Starting out'),
        'quest.legacyoftheforce.filter_2': ('Planètes', 'Planets'), 'quest.legacyoftheforce.filter_3': ('Enseignements', 'Teachings'),
        'quest.legacyoftheforce.legacy_provenance': ('Une ancienne trace de cristal est conservée. Les nouvelles expéditions exigent une provenance observée.', 'An old crystal record is retained. New expeditions require observed provenance.'),
        'quest.legacyoftheforce.teaching_origin': ('Archives des voyageurs de la Force', 'Archives of Force travelers'),
        'quest.legacyoftheforce.teaching_recorded': ('Enseignement appris, consultable sans conserver l’appareil.', 'Teaching learned and available without retaining its device.')}
    for planet, (name, jedi, sith, fr, en) in worlds.items():
        prefix = 'quest.legacyoftheforce.expedition.' + planet + '.'
        definition = {'schemaVersion': 2, 'title': prefix + 'title', 'origin': prefix + 'origin', 'clue': prefix + 'clue', 'planet': NS + planet,
                      'repeatable': False, 'dependencies': [], 'rewards': {'xp': 0, 'alignmentTenths': 0}, 'steps': [
                          {'id': key, 'text': prefix + key, 'condition': kind, 'subject': subject} for key, kind, subject in [
                              ('visit', 'VISITED_PLANET', NS + planet), ('crystal', 'PLANET_CRYSTAL', NS + planet),
                              ('jedi', 'BOSS', NS + jedi), ('sith', 'BOSS', NS + sith)]]}
        write(DATA / f'quests/expedition_{planet}.json', definition)
        entries.update({prefix + 'title': ('Expédition : ' + name, name + ' expedition'),
                        prefix + 'origin': ('Pistes locales, sans obligation de duel ni récompense supplémentaire.', 'Local leads, without mandatory duels or additional rewards.'),
                        prefix + 'clue': (fr, en), prefix + 'visit': ('Visiter réellement ' + name + '.', 'Actually visit ' + name + '.'),
                        prefix + 'crystal': ('Observer un cristal provenant de ' + name + ', libre ou serti.', 'Observe kyber originating from ' + name + ', loose or installed.'),
                        prefix + 'jedi': ('Facultatif : victoire éligible contre le Jedi local (alignement −25).', 'Optional: eligible victory against the local Jedi (alignment −25).'),
                        prefix + 'sith': ('Facultatif : victoire éligible contre le Sith local (alignement +25).', 'Optional: eligible victory against the local Sith (alignment +25).')})
    for form in ['precision', 'ward', 'momentum', 'twin']:
        write(DATA / f'quests/holocron_{form}.json', {'schemaVersion': 1, 'title': f'item.legacyoftheforce.holocron_{form}',
            'origin': 'quest.legacyoftheforce.teaching_origin', 'clue': f'saber.legacyoftheforce.form_help_{form}', 'repeatable': False,
            'dependencies': [], 'rewards': {'xp': 0, 'alignmentTenths': 0},
            'steps': [{'id': 'learned', 'condition': 'HOLOCRON', 'text': 'quest.legacyoftheforce.teaching_recorded'}]})
    for lang, column in [('fr_fr', 0), ('en_us', 1)]:
        path = ASSETS / f'lang/{lang}.json'
        value = json.loads(path.read_text(encoding='utf-8')); value.update({key: pair[column] for key, pair in entries.items()}); write(path, value)


if __name__ == '__main__':
    generate()
    print('Six expedition guides and four teaching collections generated; gameplay validation remains pending.')
