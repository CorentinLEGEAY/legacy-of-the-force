"""Original teaching instruments and expedition loot, applied after planet generation."""
import json
import runpy
from pathlib import Path
from PIL import Image, ImageDraw

shared = runpy.run_path(str(Path(__file__).with_name('generate_endor_assets.py')), run_name='lotf_authoring_helpers')
DATA, ASSETS, NS, write, texture, cube = (shared[key] for key in ['DATA', 'ASSETS', 'NS', 'write', 'texture', 'cube'])


def generate():
    forms = {'precision': ((89, 183, 216), 'Précision', 'Precision'), 'ward': ((100, 180, 147), 'Protection', 'Ward'),
             'momentum': ((211, 142, 83), 'Élan', 'Momentum'), 'twin': ((164, 127, 207), 'Deux lames', 'Twin blades')}
    display = json.loads((ASSETS / 'models/item/ilum_coordinates.json').read_text(encoding='utf-8'))['display']
    bronze = Image.new('RGB', (32, 32))
    for y in range(32):
        for x in range(32):
            grain = ((x * 31 + y * 3) % 13) - 6; seam = -25 if x in [0, 15, 16, 31] or y in [0, 31] else 0
            bronze.putpixel((x, y), tuple(max(0, min(255, c + grain + seam)) for c in (153, 129, 84)))
    draw = ImageDraw.Draw(bronze)
    for x in [3, 28]:
        for y in [3, 28]: draw.ellipse((x - 1, y - 1, x + 1, y + 1), fill=(68, 61, 45)); draw.point((x, y - 1), fill=(231, 210, 157))
    texture('item/holocron/bronze', bronze)
    entries = {'saber.legacyoftheforce.form_yes': ('oui', 'yes'), 'saber.legacyoftheforce.form_no': ('non', 'no'), 'saber.legacyoftheforce.form_basic': ('Fondamentale', 'Foundation'),
        'saber.legacyoftheforce.form_known': ('Cette forme est déjà connue.', 'You already know this form.'),
        'saber.legacyoftheforce.form_learned': ('Forme apprise : %s. Consultez /lotf saber form list.', 'Form learned: %s. See /lotf saber form list.'),
        'saber.legacyoftheforce.form_selected': ('Forme active : %s.', 'Active form: %s.'),
        'saber.legacyoftheforce.form_list': ('%s · niveau %s · connue : %s · active : %s', '%s · level %s · learned: %s · active: %s'),
        'saber.legacyoftheforce.holocron_help': ('Utiliser en main apprend la forme et consomme un appareil. Niveau et équipement compatibles requis pour la sélectionner.', 'Use in hand to learn the form and consume one device. Selecting it requires the correct level and compatible equipment.'),
        'saber.legacyoftheforce.offhand_swing': ('Frappe secondaire engagée.', 'Off-hand strike committed.'),
        'key.legacyoftheforce.saber_form': ('Choisir la prochaine forme compatible', 'Choose next compatible form'),
        'key.legacyoftheforce.saber_offhand': ('Frappe du sabre secondaire', 'Strike with the off-hand saber')}
    details = {
        'precision': ('Simple, court ou courbe : endurance offensive réduite, dégâts légèrement réduits et meilleure parade.', 'Single, short or curved: lower attack fatigue, slightly lower damage and improved parry.'),
        'ward': ('Simple ou garde : parade et garde économes, offense moins forte.', 'Single or crossguard: stronger, cheaper guard at the cost of offense.'),
        'momentum': ('Simple, double ou garde : dégâts renforcés, attaque et garde plus fatigantes.', 'Single, double or crossguard: stronger damage with more tiring attacks and guards.'),
        'twin': ('Deux sabres simples distincts, éveillés et allumés. Coût élevé, combo partagé ; /lotf saber offhand frappe avec la seconde lame.', 'Two distinct awakened, ignited single sabers. High fatigue, shared combo; /lotf saber offhand strikes with the second blade.')}
    for index, (form, (color, fr, en)) in enumerate(forms.items()):
        core = Image.new('RGB', (32, 32))
        for y in range(32):
            for x in range(32):
                light = 10 - int(abs(x - 15.5) + abs(y - 15.5)); grain = ((x * 7 + y * 13) % 7) - 3
                core.putpixel((x, y), tuple(max(0, min(255, c + light + grain)) for c in color))
        draw = ImageDraw.Draw(core)
        for inset in [2, 5]: draw.rectangle((inset, inset, 31 - inset, 31 - inset), outline=(41, 51, 62))
        if form == 'precision': draw.line((8, 23, 23, 8), fill=(233, 246, 238), width=2); draw.ellipse((12, 12, 19, 19), outline=(233, 246, 238), width=2)
        elif form == 'ward': draw.polygon([(8, 8), (23, 8), (22, 19), (16, 25), (9, 19)], outline=(233, 246, 238), width=2)
        elif form == 'momentum':
            for y in [10, 17]: draw.line((8, y + 5, 16, y, 24, y + 5), fill=(247, 235, 212), width=2)
        else:
            for x in [11, 21]: draw.line((x, 8, x, 24), fill=(240, 231, 250), width=2)
        texture('item/holocron/' + form, core)
        materials = {'bronze': NS + 'item/holocron/bronze', 'core': NS + 'item/holocron/' + form, 'dark': NS + 'item/saber/gunmetal', 'particle': NS + 'item/holocron/bronze'}
        elements = [cube([2, 1, 2], [14, 3, 14], 'bronze'), cube([3, 3, 3], [13, 4, 13], 'dark'), cube([4, 4, 4], [12, 12, 12], 'core'), cube([3, 12, 3], [13, 13, 13], 'dark'), cube([2, 13, 2], [14, 15, 14], 'bronze')]
        for x in [2, 12]:
            for z in [2, 12]: elements.append(cube([x, 3, z], [x + 2, 13, z + 2], 'bronze'))
        # Open protective cage and independent inset faces keep the core visible in hand and inventory.
        for y in [5, 10]:
            elements += [cube([2, y, 2], [14, y + .7, 3], 'bronze'), cube([2, y, 13], [14, y + .7, 14], 'bronze')]
        if form == 'precision': elements.append(cube([7, 15, 7], [9, 16, 9], 'core'))
        if form == 'twin':
            for x in [5, 10]: elements.append(cube([x, 15, 7], [x + 1, 16, 9], 'core'))
        write(ASSETS / f'models/item/holocron_{form}.json', {'textures': materials, 'elements': elements, 'display': display})
        entries[f'item.legacyoftheforce.holocron_{form}'] = ('Holocron de combat : ' + fr, en + ' combat holocron')
        entries[f'saber.legacyoftheforce.form_{form}'] = (fr, en)
        entries[f'saber.legacyoftheforce.form_help_{form}'] = details[form]
    # An Ilum ruin gains a native, seeded chest only on new structure generation.
    write(DATA / 'loot_table/chests/ilum_expedition.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': {'type': 'minecraft:uniform', 'min': 2, 'max': 3}, 'entries': [{'type': 'minecraft:item', 'name': name, 'weight': weight} for name, weight in [(NS + 'ilum_frost_fragment', 4), ('minecraft:bread', 4), ('minecraft:iron_ingot', 2), (NS + 'ilum_field_notes', 1)]]}]})
    for planet, pool in [('ilum', ['precision', 'ward']), ('tatooine', ['precision', 'twin']), ('endor', ['ward', 'twin']), ('mustafar', ['momentum']), ('dagobah', ['precision', 'ward']), ('jedha', ['twin', 'momentum'])]:
        path = DATA / f'loot_table/chests/{planet}_expedition.json'
        content = json.loads(path.read_text(encoding='utf-8'))
        content['pools'] = [entry for entry in content['pools'] if entry.get('name') != NS + 'combat_holocron']
        content['pools'].append({'name': NS + 'combat_holocron', 'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'holocron_' + form} for form in pool] + [{'type': 'minecraft:empty', 'weight': 3 * len(pool)}]})
        write(path, content)
    for lang, column in [('fr_fr', 0), ('en_us', 1)]:
        path = ASSETS / f'lang/{lang}.json'; content = json.loads(path.read_text(encoding='utf-8')); content.update({key: value[column] for key, value in entries.items()}); write(path, content)


if __name__ == '__main__':
    generate()
    print('Original combat holocrons and expedition loot generated; native validation remains pending.')
