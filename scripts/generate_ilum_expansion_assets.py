"""Original frost metal, cryogenic module and optional Ilum expedition notes."""
import json
import runpy
from pathlib import Path
from PIL import Image, ImageDraw

shared = runpy.run_path(str(Path(__file__).with_name('generate_endor_assets.py')), run_name='lotf_authoring_helpers')
DATA, ASSETS, NS, write, tag, texture, cube = (shared[key] for key in ['DATA', 'ASSETS', 'NS', 'write', 'tag', 'texture', 'cube'])


def generate():
    ore = Image.new('RGB', (32, 32))
    for y in range(32):
        for x in range(32):
            grain = ((x * 17 + y * 23) % 17) - 8
            ore.putpixel((x, y), (126 + grain, 150 + grain, 162 + grain))
    draw = ImageDraw.Draw(ore)
    for points in [[(2, 8), (9, 7), (12, 12), (18, 11)], [(4, 23), (11, 25), (15, 20), (24, 22)], [(21, 4), (24, 9), (29, 8)]]:
        draw.line(points, fill=(45, 82, 104), width=4); draw.line(points, fill=(180, 220, 228), width=2)
    for x, y in [(8, 7), (15, 20), (25, 8)]: draw.rectangle((x, y, x + 2, y + 2), fill=(225, 239, 232))
    texture('block/ilum_frost_ore', ore)
    write(ASSETS / 'models/block/ilum_frost_ore.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': NS + 'block/ilum_frost_ore'}})
    write(ASSETS / 'blockstates/ilum_frost_ore.json', {'variants': {'': {'model': NS + 'block/ilum_frost_ore'}}})
    tag('mineable/pickaxe', [NS + 'ilum_frost_ore']); tag('needs_iron_tool', [NS + 'ilum_frost_ore'])
    write(DATA / 'loot_table/blocks/ilum_frost_ore.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'ilum_frost_fragment'}],
        'conditions': [{'condition': 'minecraft:match_tool', 'predicate': {'items': '#minecraft:pickaxes'}}, {'condition': 'minecraft:survives_explosion'}]}]})
    for recipe, kind, time in [('ilum_frost_alloy_smelting', 'smelting', 200), ('ilum_frost_alloy_blasting', 'blasting', 100)]:
        write(DATA / f'recipe/{recipe}.json', {'type': 'minecraft:' + kind, 'category': 'misc', 'ingredient': {'item': NS + 'ilum_frost_fragment'}, 'result': {'id': NS + 'ilum_frost_alloy', 'count': 1}, 'experience': .5, 'cookingtime': time})
    write(DATA / 'speeder_modules/thruster_cryo.json', {'schemaVersion': 1, 'family': 'thruster', 'mass': 2.5, 'thrust': 8, 'lift': 14, 'capacity': 0, 'stability': .08,
        'connectors': ['up', 'down', 'north', 'south', 'east', 'west'], 'salvage': [{'item': 'minecraft:copper_ingot', 'count': 1}, {'item': NS + 'ilum_frost_alloy', 'count': 1}]})
    write(DATA / 'recipe/thruster_cryo.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['FCF', 'ATA', 'F F'],
        'key': {'F': {'item': NS + 'ilum_frost_alloy'}, 'C': {'item': 'minecraft:copper_ingot'}, 'A': {'item': 'minecraft:amethyst_shard'}, 'T': {'item': NS + 'thruster_vector'}}, 'result': {'id': NS + 'thruster_cryo', 'count': 1}})
    model = json.loads((ASSETS / 'models/item/thruster_vector.json').read_text(encoding='utf-8'))
    coolant = Image.new('RGB', (32, 32), (76, 159, 184)); draw = ImageDraw.Draw(coolant)
    for x in range(0, 32, 4): draw.line((x, 0, x, 31), fill=(142, 210, 220))
    draw.rectangle((0, 0, 31, 31), outline=(34, 71, 88)); texture('item/navigation/ilum_coolant', coolant)
    model['textures']['coolant'] = NS + 'item/navigation/ilum_coolant'
    # Shroud, hollow nozzle, four heat exchangers, manifold and two visible coolant lines.
    elements = [cube([3, 2, 3], [13, 12, 12], 'gunmetal'), cube([2, 2, 2], [14, 4, 4], 'edge'), cube([2, 10, 2], [14, 12, 4], 'edge'),
        cube([2, 4, 2], [4, 10, 4], 'edge'), cube([12, 4, 2], [14, 10, 4], 'edge'), cube([4, 4, 3], [12, 10, 3.5], 'coolant'),
        cube([2, 1, 12], [14, 3, 15], 'alloy'), cube([6, 11, 8], [10, 14, 11], 'copper'), cube([4, 13, 7], [12, 15, 12], 'alloy')]
    for x in [1, 13]:
        elements.append(cube([x, 3, 5], [x + 2, 11, 12], 'alloy'))
        for y in [4, 6, 8, 10]: elements.append(cube([x - .5, y, 5], [x + 2.5, y + .5, 12], 'edge'))
    for x in [4, 11]: elements.append(cube([x, 4, 12], [x + 1, 12, 13], 'coolant'))
    for element in elements:
        if element['faces']['north']['texture'] == '#gunmetal':
            for face in element['faces'].values(): face['tintindex'] = 0
    model['elements'] = elements; write(ASSETS / 'models/item/thruster_cryo.json', model)
    fragment = {'textures': {'ore': NS + 'block/ilum_frost_ore', 'metal': NS + 'item/saber/alloy', 'particle': NS + 'block/ilum_frost_ore'},
        'elements': [cube([3, 1, 4], [10, 5, 10], 'ore'), cube([8, 2, 7], [13, 7, 12], 'metal'), cube([4, 5, 5], [8, 8, 8], 'ore')], 'display': model['display']}
    write(ASSETS / 'models/item/ilum_frost_fragment.json', fragment)
    notes = Image.new('RGB', (32, 32), (87, 118, 133)); draw = ImageDraw.Draw(notes); draw.rectangle((3, 3, 28, 28), outline=(186, 222, 226), width=2); draw.rectangle((6, 6, 25, 25), fill=(214, 226, 219))
    for y in [10, 14, 18, 22]: draw.line((9, y, 22 if y != 18 else 18, y), fill=(57, 82, 91))
    texture('item/navigation/ilum_notes', notes)
    write(ASSETS / 'models/item/ilum_field_notes.json', {'textures': {'paper': NS + 'item/navigation/ilum_notes', 'particle': NS + 'item/navigation/ilum_notes'},
        'elements': [cube([2, 1, 2], [14, 3, 14], 'paper'), cube([1, 0, 1], [15, 1, 15], 'paper'), cube([1, 3, 1], [15, 4, 15], 'paper')], 'display': model['display']})
    write(DATA / 'recipe/ilum_field_notes.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [{'item': item} for item in ['minecraft:paper', 'minecraft:charcoal', 'minecraft:snowball']], 'result': {'id': NS + 'ilum_field_notes', 'count': 1}})
    entries = {'block.legacyoftheforce.ilum_frost_ore': ('Minerai de métal gelé', 'Frost metal ore'), 'item.legacyoftheforce.ilum_frost_fragment': ('Éclat de métal gelé', 'Frost metal fragment'),
        'item.legacyoftheforce.thruster_cryo': ('Propulseur cryogénique d’Ilum', 'Ilum cryogenic thruster'), 'item.legacyoftheforce.ilum_field_notes': ('Carnet d’expédition : Ilum', 'Ilum expedition notes')}
    notes = [('Les tours d’accès mènent aux cavernes rares. Les sources bleues sont entourées de glace dense.', 'Access towers lead to rare caverns. Blue sources are surrounded by dense ice.'),
        ('Les veines de métal gelé sont dans les sols des nouvelles cavernes. Une pioche de fer permet de récupérer des éclats à fondre.', 'Frost metal veins lie in the floors of new caverns. An iron pickaxe yields fragments to smelt.'),
        ('Le propulseur cryogénique favorise la portance, avec plus de masse et moins de poussée que le vectoriel standard.', 'Cryogenic thrusters favor lift, with more mass and less thrust than standard vector thrusters.'),
        ('Les autels du Gardien et du Seigneur du Givre lancent un défi. Glaçons protecteurs et projectiles sont de vrais objets temporaires.', 'Guardian and Frost Lord altars start a challenge. Protective ice and projectiles are real temporary objects.'),
        ('Une victoire éligible fournit du métal, des coordonnées et parfois un cristal bleu. Les récompenses ont un délai personnel et une recharge d’arène.', 'Eligible victories yield metal, coordinates and sometimes blue kyber. Rewards have personal and arena cooldowns.')]
    for index, value in enumerate(notes): entries[f'notes.legacyoftheforce.ilum.{index}'] = value
    for lang, column in [('fr_fr', 0), ('en_us', 1)]:
        path = ASSETS / f'lang/{lang}.json'; content = json.loads(path.read_text(encoding='utf-8')); content.update({key: value[column] for key, value in entries.items()}); write(path, content)


if __name__ == '__main__':
    generate()
    print('Ilum material and cryogenic resources generated; native gameplay and visual validation remain pending.')
