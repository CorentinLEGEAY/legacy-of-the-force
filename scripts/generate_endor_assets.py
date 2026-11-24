"""Authored forest terrain, exploration loot and original equipment/cloth assets.

Only references vanilla registries and this repository's own models. No extracted
media, regeneration hooks or client-side loot decisions.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
DATA = ROOT / 'data/legacyoftheforce'
ASSETS = ROOT / 'assets/legacyoftheforce'
NS = 'legacyoftheforce:'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def tag(name, values):
    path = ROOT / ('data/minecraft/tags/block/' + name + '.json')
    value = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'replace': False, 'values': []}
    value['values'] = list(dict.fromkeys(value['values'] + values))
    write(path, value)


def texture(name, image):
    path = ASSETS / ('textures/' + name + '.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def cube(lo, hi, material):
    return {'from': lo, 'to': hi, 'faces': {face: {'texture': '#' + material} for face in ['up', 'down', 'north', 'south', 'east', 'west']}}


def noise(name, octave, amplitudes):
    write(DATA / f'worldgen/noise/{name}.json', {'firstOctave': octave, 'amplitudes': amplitudes})
    return {'type': 'minecraft:noise', 'noise': NS + name, 'xz_scale': 1, 'y_scale': 0}


def grad(a, b, first, last):
    return {'type': 'minecraft:y_clamped_gradient', 'from_y': a, 'to_y': b, 'from_value': first, 'to_value': last}


def binary(kind, a, b):
    return {'type': 'minecraft:' + kind, 'argument1': a, 'argument2': b}


def block(name):
    return {'type': 'minecraft:block', 'result_state': {'Name': 'minecraft:' + name}}


def terrain():
    hills = noise('endor_hills', -7, [1, .7, .3])
    floor = noise('endor_forest_floor', -5, [1, .4])
    canopy = noise('endor_canopy_density', -8, [1, .5])
    density = binary('add', grad(16, 144, 1, -1), binary('add', binary('mul', .65, hills), binary('mul', .18, floor)))
    density = binary('min', binary('max', density, grad(-64, -48, 4, -4)), grad(160, 192, 4, -4))
    fields = ['barrier', 'fluid_level_floodedness', 'fluid_level_spread', 'lava', 'temperature', 'vegetation', 'continents', 'erosion',
              'depth', 'ridges', 'initial_density_without_jaggedness', 'final_density', 'vein_toggle', 'vein_ridged', 'vein_gap']
    router = dict.fromkeys(fields, 0)
    router.update(vegetation=canopy, initial_density_without_jaggedness=density, final_density={'type': 'minecraft:interpolated', 'argument': density})
    surface = {'type': 'minecraft:sequence', 'sequence': [
        {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:vertical_gradient', 'random_name': NS + 'endor_bedrock',
            'true_at_and_below': {'above_bottom': 0}, 'false_at_and_above': {'above_bottom': 5}}, 'then_run': block('bedrock')},
        {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:stone_depth', 'offset': 0, 'add_surface_depth': False, 'secondary_depth_range': 0, 'surface_type': 'floor'}, 'then_run': block('grass_block')},
        {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:stone_depth', 'offset': 3, 'add_surface_depth': False, 'secondary_depth_range': 0, 'surface_type': 'floor'}, 'then_run': block('dirt')}, block('stone')]}
    write(DATA / 'worldgen/noise_settings/endor.json', {'noise': {'min_y': -64, 'height': 384, 'size_horizontal': 1, 'size_vertical': 2},
        'default_block': {'Name': 'minecraft:stone'}, 'default_fluid': {'Name': 'minecraft:water'}, 'noise_router': router,
        'surface_rule': surface, 'spawn_target': [], 'sea_level': 54, 'disable_mob_generation': False,
        'aquifers_enabled': False, 'ore_veins_enabled': False, 'legacy_random_source': False})
    write(DATA / 'dimension_type/endor.json', json.loads((DATA / 'dimension_type/ilum.json').read_text(encoding='utf-8')))
    biomes = ['endor_clearings', 'endor_giant_forest']
    source = {'type': 'minecraft:multi_noise', 'biomes': [{'biome': NS + name, 'parameters': {
        'temperature': 0, 'humidity': [-1, 0] if i == 0 else [0, 1], 'continentalness': 0, 'erosion': 0, 'depth': 0, 'weirdness': 0, 'offset': 0}}
        for i, name in enumerate(biomes)]}
    write(DATA / 'dimension/endor.json', {'type': NS + 'endor', 'generator': {'type': 'minecraft:noise', 'settings': NS + 'endor', 'biome_source': source}})
    for i, name in enumerate(biomes):
        feature = 'endor_clearing' if i == 0 else 'endor_canopy'
        features = [[] for _ in range(11)]
        features[6] = ['minecraft:ore_coal_upper', 'minecraft:ore_iron_middle', 'minecraft:ore_copper']
        features[10] = [NS + feature]
        write(DATA / f'worldgen/biome/{name}.json', {'has_precipitation': True, 'temperature': .7, 'downfall': .85,
            'effects': {'fog_color': 0x778C75 if i == 0 else 0x526D61, 'sky_color': 0x9DB8BD, 'water_color': 0x3E786B, 'water_fog_color': 0x1A3F37,
                'grass_color': 0x669243, 'foliage_color': 0x48733C, 'particle': {'probability': .002, 'options': {'type': 'minecraft:spore_blossom_air'}}},
            'carvers': {'air': ['minecraft:cave', 'minecraft:canyon']}, 'features': features, 'spawn_costs': {},
            'spawners': {'creature': [{'type': 'minecraft:wolf', 'weight': 4, 'minCount': 1, 'maxCount': 3}, {'type': 'minecraft:fox', 'weight': 4, 'minCount': 1, 'maxCount': 2},
                                     {'type': 'minecraft:rabbit', 'weight': 6, 'minCount': 2, 'maxCount': 3}],
                'monster': [{'type': 'minecraft:spider', 'weight': 50, 'minCount': 1, 'maxCount': 3}, {'type': 'minecraft:skeleton', 'weight': 30, 'minCount': 1, 'maxCount': 2},
                            {'type': 'minecraft:enderman', 'weight': 3, 'minCount': 1, 'maxCount': 1}],
                'ambient': [{'type': 'minecraft:bat', 'weight': 5, 'minCount': 1, 'maxCount': 2}]}})
        write(DATA / f'worldgen/configured_feature/{feature}.json', {'type': NS + 'endor_canopy', 'config': {'tree_chance': 6 if i == 0 else 1,
            'minimum_height': 18 if i == 0 else 24, 'maximum_height': 26 if i == 0 else 34, 'fern_attempts': 10 if i == 0 else 24}})
        write(DATA / f'worldgen/placed_feature/{feature}.json', {'feature': NS + feature, 'placement': []})
    sites = ['endor_root_grotto', 'endor_lookout', 'endor_ruins', 'endor_jedi_temple', 'endor_sith_sanctum']
    for kind, name in enumerate(sites):
        write(DATA / f'worldgen/structure/{name}.json', {'type': NS + 'endor_site', 'site_kind': kind, 'biomes': [NS + name for name in biomes],
            'spawn_overrides': {}, 'step': 'surface_structures', 'terrain_adaptation': 'none'})
    write(DATA / 'worldgen/structure_set/endor_expeditions.json', {'structures': [{'structure': NS + name, 'weight': weight} for name, weight in zip(sites, [2, 2, 1, 1, 1])],
        'placement': {'type': 'minecraft:random_spread', 'salt': 610493, 'spacing': 14, 'separation': 7, 'spread_type': 'linear'}})


def resources():
    path = DATA / 'tags/entity_type/boss_mobs.json'
    bosses = json.loads(path.read_text(encoding='utf-8'))
    bosses['values'] = list(dict.fromkeys(bosses['values'] + [NS + name for name in ['tatooine_wandering_knight', 'tatooine_desert_exile', 'endor_nature_keeper', 'endor_night_assassin']]))
    write(path, bosses)
    wood = Image.new('RGB', (32, 32))
    for y in range(32):
        for x in range(32):
            stripe = ((x * 11 + y // 5) % 13) - 6
            wood.putpixel((x, y), (max(0, 93 + stripe * 3), max(0, 73 + stripe * 2), max(0, 46 + stripe)))
    draw = ImageDraw.Draw(wood)
    for x, y in [(5, 8), (19, 19), (25, 4)]:
        draw.ellipse((x, y, x + 4, y + 6), fill=(206, 151, 44), outline=(247, 202, 86))
    texture('block/endor_resin_wood', wood)
    write(ASSETS / 'models/block/endor_resin_wood.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': NS + 'block/endor_resin_wood'}})
    write(ASSETS / 'blockstates/endor_resin_wood.json', {'variants': {'': {'model': NS + 'block/endor_resin_wood'}}})
    crystal = json.loads((ASSETS / 'models/item/crystal_green.json').read_text(encoding='utf-8'))
    crystal.pop('display', None)
    write(ASSETS / 'models/block/endor_crystal_cluster.json', crystal)
    write(ASSETS / 'blockstates/endor_crystal_cluster.json', {'variants': {'': {'model': NS + 'block/endor_crystal_cluster'}}})
    write(DATA / 'loot_table/blocks/endor_crystal_cluster.json', {'type': 'minecraft:block', 'random_sequence': NS + 'blocks/endor_crystal_cluster', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': NS + 'ilum_crystal', 'functions': [{'function': NS + 'identify_ilum_crystal', 'planet': NS + 'endor'}]}],
        'conditions': [{'condition': 'minecraft:location_check', 'predicate': {'dimension': NS + 'endor'}}, {'condition': 'minecraft:match_tool', 'predicate': {'items': '#minecraft:pickaxes'}}, {'condition': 'minecraft:survives_explosion'}]}]})
    write(DATA / 'loot_table/blocks/endor_resin_wood.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': NS + 'endor_resin'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for name in ['mineable/pickaxe', 'needs_iron_tool']:
        tag(name, [NS + 'endor_crystal_cluster', NS + 'endor_jedi_altar', NS + 'endor_sith_altar'])
    tag('mineable/axe', [NS + 'endor_resin_wood'])
    tag('needs_stone_tool', [NS + 'endor_resin_wood'])
    write(DATA / 'loot_table/chests/endor_expedition.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}, 'entries': [{'type': 'minecraft:item', 'name': name, 'weight': weight,
            'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': count}}]}
            for name, weight, count in [(NS + 'endor_resin', 5, 3), ('minecraft:iron_ingot', 3, 3), ('minecraft:bread', 4, 4), ('minecraft:arrow', 4, 6), ('minecraft:emerald', 1, 2)]]},
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'mustafar_coordinates'}, {'type': 'minecraft:empty', 'weight': 3}]},
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'endor_field_notes'}, {'type': 'minecraft:empty'}]}]})
    write(DATA / 'recipe/endor_coordinates.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': item} for item in ['minecraft:compass', 'minecraft:paper', 'minecraft:emerald', 'minecraft:amethyst_shard']], 'result': {'id': NS + 'endor_coordinates', 'count': 1}})
    write(DATA / 'speeder_modules/chassis_forest.json', {'schemaVersion': 1, 'family': 'chassis', 'mass': 4.2, 'thrust': 0, 'lift': 0, 'capacity': 0, 'stability': 1.2,
        'connectors': ['up', 'down', 'north', 'south', 'east', 'west'], 'salvage': [{'item': 'minecraft:iron_nugget', 'count': 18}, {'item': NS + 'endor_resin', 'count': 1}]})
    write(DATA / 'recipe/chassis_forest.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['RIR', 'PCP', ' R '],
        'key': {'R': {'item': NS + 'endor_resin'}, 'I': {'item': 'minecraft:iron_ingot'}, 'P': {'item': 'minecraft:spruce_planks'}, 'C': {'item': NS + 'chassis_light'}},
        'result': {'id': NS + 'chassis_forest', 'count': 1}})
    chassis = json.loads((ASSETS / 'models/item/chassis_light.json').read_text(encoding='utf-8'))
    chassis['textures']['wood'] = NS + 'block/endor_resin_wood'
    chassis['elements'] = [cube([0, 3, 1], [16, 5, 4], 'wood'), cube([0, 3, 12], [16, 5, 15], 'wood'), cube([1, 4, 3], [3, 7, 13], 'alloy'), cube([13, 4, 3], [15, 7, 13], 'alloy')]
    for x in [3, 7, 11]:
        chassis['elements'].append(cube([x, 4, 3], [x + 2, 5.5, 13], 'wood'))
        chassis['elements'].append(cube([x, 2, 6], [x + 2, 4, 10], 'gunmetal'))
    write(ASSETS / 'models/item/chassis_forest.json', chassis)


def artwork():
    for name, base in [('endor_nature_keeper', (102, 135, 75)), ('endor_night_assassin', (50, 66, 63))]:
        image = Image.new('RGB', (16, 16))
        for y in range(16):
            for x in range(16):
                grain = ((x * 7 + y * 11) % 9) - 4
                seam = -15 if x in [1, 14] else 0
                image.putpixel((x, y), tuple(max(0, min(255, c + grain + seam)) for c in base))
        draw = ImageDraw.Draw(image)
        draw.line((2, 10, 13, 10), fill=(153, 127, 76), width=2)
        draw.line((4, 3, 11, 3), fill=(150, 175, 100) if name.endswith('keeper') else (112, 142, 127))
        texture('entity/' + name, image)
    display = json.loads((ASSETS / 'models/item/endor_coordinates.json').read_text(encoding='utf-8'))['display']
    for name, dark in [('endor_jedi_altar', False), ('endor_sith_altar', True)]:
        write(ASSETS / f'models/block/{name}.json', {'parent': 'minecraft:block/block', 'textures': {'wood': NS + 'block/endor_resin_wood', 'glyph': NS + 'item/navigation/endor', 'particle': NS + 'block/endor_resin_wood'},
            'elements': [cube([1, 0, 1], [15, 3, 15], 'wood'), cube([3, 3, 3], [13, 9, 13], 'wood'), cube([2, 9, 2], [14, 11, 14], 'glyph'),
                         cube([2 if dark else 4, 11, 6], [5 if dark else 12, 15, 10], 'wood')]})
        write(ASSETS / f'blockstates/{name}.json', {'variants': {'': {'model': NS + 'block/' + name}}})
    write(ASSETS / 'models/item/endor_boss_relic.json', {'textures': {'wood': NS + 'block/endor_resin_wood', 'glyph': NS + 'item/navigation/endor', 'particle': NS + 'block/endor_resin_wood'},
        'elements': [cube([4, 1, 4], [12, 3, 12], 'wood'), cube([5, 3, 5], [7, 14, 11], 'wood'), cube([9, 3, 5], [11, 14, 11], 'wood'),
                     cube([5, 12, 5], [11, 15, 11], 'glyph'), cube([7, 6, 6], [9, 10, 10], 'glyph')], 'display': display})
    notes = Image.new('RGB', (32, 32), (65, 91, 56)); draw = ImageDraw.Draw(notes)
    draw.rectangle((3, 3, 28, 28), outline=(153, 170, 98), width=2)
    draw.rectangle((6, 6, 25, 25), fill=(203, 207, 161))
    for y in [10, 14, 18, 22]:
        draw.line((9, y, 22 if y != 18 else 18, y), fill=(62, 89, 52))
    texture('item/navigation/endor_notes', notes)
    write(ASSETS / 'models/item/endor_field_notes.json', {'textures': {'paper': NS + 'item/navigation/endor_notes', 'particle': NS + 'item/navigation/endor_notes'},
        'elements': [cube([2, 1, 2], [14, 3, 14], 'paper'), cube([1, 0, 1], [15, 1, 15], 'paper'), cube([1, 3, 1], [15, 4, 15], 'paper')], 'display': display})
    write(DATA / 'recipe/endor_field_notes.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': item} for item in ['minecraft:paper', 'minecraft:charcoal', 'minecraft:spruce_log']], 'result': {'id': NS + 'endor_field_notes', 'count': 1}})


def language():
    entries = {
        'entity.legacyoftheforce.endor_nature_keeper': ('Gardienne de la Nature', 'Nature Keeper'),
        'entity.legacyoftheforce.endor_night_assassin': ('Assassin Nocturne', 'Night Assassin'),
        'entity.legacyoftheforce.arena_illusion': ('Leurre d’arène', 'Arena decoy'),
        'block.legacyoftheforce.endor_crystal_cluster': ('Formation de kyber des racines', 'Root kyber formation'),
        'block.legacyoftheforce.endor_resin_wood': ('Bois résineux d’Endor', 'Endor resinwood'),
        'block.legacyoftheforce.endor_jedi_altar': ('Autel de la Gardienne', 'Nature Keeper altar'),
        'block.legacyoftheforce.endor_sith_altar': ('Autel de l’Assassin', 'Night Assassin altar'),
        'item.legacyoftheforce.endor_boss_relic': ('Relique des racines', 'Root relic'),
        'item.legacyoftheforce.endor_field_notes': ('Carnet d’expédition : Endor', 'Endor expedition notes'),
        'item.legacyoftheforce.chassis_forest': ('Châssis forestier d’Endor', 'Endor forest chassis'),
    }
    for key, fr, en in [('leap_strike', 'Bond des cimes', 'Canopy leap'), ('verdant_guard', 'Garde des racines', 'Verdant guard'), ('root_snare', 'Étreinte des racines', 'Root snare'),
                        ('renewal', 'Renouveau', 'Renewal'), ('canopy_wave', 'Onde de la canopée', 'Canopy wave'), ('shadow_dash', 'Ruée de l’ombre', 'Shadow dash'),
                        ('shadow_double', 'Doubles nocturnes', 'Shadow doubles'), ('smoke_fan', 'Éventail de fumée', 'Smoke fan'), ('ambush', 'Embuscade', 'Ambush')]:
        entries['boss.legacyoftheforce.attack.' + key] = (fr, en)
    notes = [
        ('Cherchez les anciens escaliers sous les racines : une formation verte peut attendre dans la grotte. Le bois ambré fournit la résine.', 'Look for old stairs beneath the roots: a green formation may lie in the grotto. Amber wood yields resin.'),
        ('Les tours de guet se gravissent par une échelle. Elles donnent des provisions et parfois une carte de Mustafar.', 'Climb lookout ladders for supplies and occasional Mustafar charts.'),
        ('Les objets de bois soutiennent la garde et le soin de la Gardienne. Déplacez-les par la Force ou brisez-les ; sautez pour éviter l’onde et les racines.', 'Wooden anchors sustain the Keeper’s guard and healing. Move them with the Force or break them; jump to avoid waves and roots.'),
        ('L’Assassin emploie des leurres sans poids. Un coup les dissipe ; la perception de Force révèle le véritable adversaire.', 'The Assassin uses weightless decoys. A hit dispels them; Force perception reveals the real foe.'),
        ('Le châssis forestier résiste mieux aux écarts de trajectoire, mais pèse plus lourd. Les clairières facilitent les trajets en speeder.', 'The forest chassis provides more stability at a higher mass. Clearings make speeder travel easier.'),
    ]
    for index, value in enumerate(notes):
        entries[f'notes.legacyoftheforce.endor.{index}'] = value
    for lang, column in [('fr_fr', 0), ('en_us', 1)]:
        path = ASSETS / f'lang/{lang}.json'; content = json.loads(path.read_text(encoding='utf-8'))
        content.update({key: value[column] for key, value in entries.items()}); write(path, content)


if __name__ == '__main__':
    terrain(); resources(); artwork(); language()
    print('Endor resources generated; native loading, gameplay and visual validation remain pending.')
