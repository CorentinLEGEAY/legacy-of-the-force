"""Original volcanic data and equipment; shared helpers only read our own authoring code."""
import json
import runpy
from pathlib import Path
from PIL import Image, ImageDraw

shared = runpy.run_path(str(Path(__file__).with_name('generate_endor_assets.py')), run_name='lotf_authoring_helpers')
ROOT, DATA, ASSETS, NS = (shared[key] for key in ['ROOT', 'DATA', 'ASSETS', 'NS'])
write, tag, texture, cube, noise, grad, binary, block = (shared[key] for key in ['write', 'tag', 'texture', 'cube', 'noise', 'grad', 'binary', 'block'])


def terrain():
    river = noise('mustafar_channels', -7, [1, .6, .3])
    mountains = noise('mustafar_ridges', -8, [1, .7, .4])
    channel = {'type': 'minecraft:abs', 'argument': river}
    density = binary('add', grad(0, 176, 1, -1), binary('add', -.35, binary('add', binary('mul', .8, channel), binary('mul', .32, mountains))))
    density = binary('min', binary('max', density, grad(-64, -48, 4, -4)), grad(160, 192, 4, -4))
    fields = ['barrier', 'fluid_level_floodedness', 'fluid_level_spread', 'lava', 'temperature', 'vegetation', 'continents', 'erosion',
              'depth', 'ridges', 'initial_density_without_jaggedness', 'final_density', 'vein_toggle', 'vein_ridged', 'vein_gap']
    router = dict.fromkeys(fields, 0)
    router.update(continents=channel, initial_density_without_jaggedness=density, final_density={'type': 'minecraft:interpolated', 'argument': density})
    surface = {'type': 'minecraft:sequence', 'sequence': [
        {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:vertical_gradient', 'random_name': NS + 'mustafar_bedrock',
          'true_at_and_below': {'above_bottom': 0}, 'false_at_and_above': {'above_bottom': 5}}, 'then_run': block('bedrock')}, block('basalt')]}
    write(DATA / 'worldgen/noise_settings/mustafar.json', {'noise': {'min_y': -64, 'height': 384, 'size_horizontal': 1, 'size_vertical': 2},
        'default_block': {'Name': 'minecraft:basalt'}, 'default_fluid': {'Name': 'minecraft:lava'}, 'noise_router': router,
        'surface_rule': surface, 'spawn_target': [], 'sea_level': 64, 'disable_mob_generation': False,
        'aquifers_enabled': False, 'ore_veins_enabled': False, 'legacy_random_source': False})
    dimension = json.loads((DATA / 'dimension_type/ilum.json').read_text(encoding='utf-8'))
    dimension.update(ultrawarm=True, ambient_light=.12, fixed_time=18000)
    write(DATA / 'dimension_type/mustafar.json', dimension)
    biomes = ['mustafar_lava_rifts', 'mustafar_ash_plains']
    source = {'type': 'minecraft:multi_noise', 'biomes': [{'biome': NS + name, 'parameters': {'temperature': 0, 'humidity': 0,
        'continentalness': [-1, .15] if i == 0 else [.15, 1], 'erosion': 0, 'depth': 0, 'weirdness': 0, 'offset': 0}} for i, name in enumerate(biomes)]}
    write(DATA / 'dimension/mustafar.json', {'type': NS + 'mustafar', 'generator': {'type': 'minecraft:noise', 'settings': NS + 'mustafar', 'biome_source': source}})
    write(DATA / 'tags/block/mustafar_carver_replaceables.json', {'replace': False, 'values': ['minecraft:basalt', 'minecraft:blackstone', 'minecraft:stone']})
    # Field names checked against the pinned 1.21.1 CarverConfiguration/CaveCarverConfiguration codecs.
    write(DATA / 'worldgen/configured_carver/mustafar_caves.json', {'type': 'minecraft:cave', 'config': {'probability': .12,
        'y': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': -32}, 'max_inclusive': {'absolute': 96}}, 'yScale': .6,
        'lava_level': {'above_bottom': 8}, 'replaceable': '#' + NS + 'mustafar_carver_replaceables',
        'horizontal_radius_multiplier': 1.2, 'vertical_radius_multiplier': .9, 'floor_level': -.7}})
    for i, name in enumerate(biomes):
        features = [[] for _ in range(11)]; feature = 'mustafar_vents' if i == 0 else 'mustafar_volcanoes'
        features[10] = [NS + feature]
        write(DATA / f'worldgen/biome/{name}.json', {'has_precipitation': False, 'temperature': 2, 'downfall': 0,
            'effects': {'fog_color': 0x663B30, 'sky_color': 0x5A3631, 'water_color': 0x774536, 'water_fog_color': 0x351E1A,
                'particle': {'probability': .004, 'options': {'type': 'minecraft:ash'}}},
            'carvers': {'air': [NS + 'mustafar_caves']}, 'features': features, 'spawn_costs': {},
            'spawners': {'creature': [{'type': 'minecraft:strider', 'weight': 4, 'minCount': 1, 'maxCount': 2}],
                'monster': [{'type': 'minecraft:magma_cube', 'weight': 30, 'minCount': 1, 'maxCount': 2}, {'type': 'minecraft:blaze', 'weight': 5, 'minCount': 1, 'maxCount': 1}], 'ambient': []}})
        write(DATA / f'worldgen/configured_feature/{feature}.json', {'type': NS + 'mustafar_landscape', 'config': {'volcano_chance': 20 if i == 0 else 12,
            'minimum_height': 5 if i == 0 else 8, 'maximum_height': 10 if i == 0 else 16}})
        write(DATA / f'worldgen/placed_feature/{feature}.json', {'feature': NS + feature, 'placement': []})
    sites = ['mustafar_crystal_vault', 'mustafar_refinery', 'mustafar_ruins', 'mustafar_jedi_temple', 'mustafar_sith_sanctum']
    for kind, name in enumerate(sites):
        write(DATA / f'worldgen/structure/{name}.json', {'type': NS + 'mustafar_site', 'site_kind': kind, 'biomes': [NS + name for name in biomes],
            'spawn_overrides': {}, 'step': 'surface_structures', 'terrain_adaptation': 'none'})
    write(DATA / 'worldgen/structure_set/mustafar_expeditions.json', {'structures': [{'structure': NS + name, 'weight': weight} for name, weight in zip(sites, [2, 2, 1, 1, 1])],
        'placement': {'type': 'minecraft:random_spread', 'salt': 903717, 'spacing': 14, 'separation': 7, 'spread_type': 'linear'}})


def resources():
    path = DATA / 'tags/entity_type/boss_mobs.json'; value = json.loads(path.read_text(encoding='utf-8'))
    value['values'] = list(dict.fromkeys(value['values'] + [NS + 'mustafar_ruins_protector', NS + 'mustafar_flame_lord'])); write(path, value)
    ore = Image.new('RGB', (32, 32))
    for y in range(32):
        for x in range(32):
            grain = ((x * 13 + y * 17) % 15) - 7; ore.putpixel((x, y), (52 + grain, 49 + grain, 48 + grain))
    draw = ImageDraw.Draw(ore)
    for x, y in [(4, 6), (21, 4), (16, 21), (7, 24)]:
        draw.polygon([(x, y), (x + 6, y + 1), (x + 4, y + 5), (x - 1, y + 4)], fill=(166, 94, 52), outline=(211, 130, 64))
    texture('block/mustafar_slag_ore', ore)
    write(ASSETS / 'models/block/mustafar_slag_ore.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': NS + 'block/mustafar_slag_ore'}})
    write(ASSETS / 'blockstates/mustafar_slag_ore.json', {'variants': {'': {'model': NS + 'block/mustafar_slag_ore'}}})
    crystal = json.loads((ASSETS / 'models/item/crystal_orange.json').read_text(encoding='utf-8')); crystal.pop('display', None)
    write(ASSETS / 'models/block/mustafar_crystal_cluster.json', crystal)
    write(ASSETS / 'blockstates/mustafar_crystal_cluster.json', {'variants': {'': {'model': NS + 'block/mustafar_crystal_cluster'}}})
    write(DATA / 'loot_table/blocks/mustafar_crystal_cluster.json', {'type': 'minecraft:block', 'random_sequence': NS + 'blocks/mustafar_crystal_cluster', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': NS + 'ilum_crystal', 'functions': [{'function': NS + 'identify_ilum_crystal', 'planet': NS + 'mustafar'}]}],
        'conditions': [{'condition': 'minecraft:location_check', 'predicate': {'dimension': NS + 'mustafar'}}, {'condition': 'minecraft:match_tool', 'predicate': {'items': '#minecraft:pickaxes'}}, {'condition': 'minecraft:survives_explosion'}]}]})
    write(DATA / 'loot_table/blocks/mustafar_slag_ore.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': NS + 'mustafar_slag'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for name in ['mineable/pickaxe', 'needs_iron_tool']:
        tag(name, [NS + key for key in ['mustafar_slag_ore', 'mustafar_crystal_cluster', 'mustafar_jedi_altar', 'mustafar_sith_altar']])
    write(DATA / 'loot_table/chests/mustafar_expedition.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}, 'entries': [{'type': 'minecraft:item', 'name': name, 'weight': weight,
            'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': count}}]}
            for name, weight, count in [(NS + 'mustafar_slag', 5, 3), ('minecraft:iron_ingot', 4, 3), ('minecraft:copper_ingot', 4, 3), ('minecraft:bread', 5, 4), ('minecraft:blaze_powder', 2, 2)]]},
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'dagobah_coordinates'}, {'type': 'minecraft:empty', 'weight': 3}]},
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'mustafar_field_notes'}, {'type': 'minecraft:empty'}]}]})
    write(DATA / 'recipe/mustafar_coordinates.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': item} for item in ['minecraft:compass', 'minecraft:paper', 'minecraft:redstone', 'minecraft:obsidian']], 'result': {'id': NS + 'mustafar_coordinates', 'count': 1}})
    write(DATA / 'speeder_modules/engine_high_power.json', {'schemaVersion': 1, 'family': 'engine', 'mass': 4.5, 'thrust': 48, 'lift': 40, 'capacity': 0, 'stability': .12,
        'connectors': ['up', 'down', 'north', 'south', 'east', 'west'], 'salvage': [{'item': 'minecraft:iron_nugget', 'count': 18}, {'item': NS + 'mustafar_slag', 'count': 1}]})
    write(DATA / 'recipe/engine_high_power.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['SIS', 'CEC', 'SIS'],
        'key': {'S': {'item': NS + 'mustafar_slag'}, 'I': {'item': 'minecraft:iron_ingot'}, 'C': {'item': 'minecraft:copper_ingot'}, 'E': {'item': NS + 'engine_standard'}}, 'result': {'id': NS + 'engine_high_power', 'count': 1}})
    model = json.loads((ASSETS / 'models/item/engine_standard.json').read_text(encoding='utf-8'))
    model['elements'] = [cube([2, 1, 2], [14, 4, 14], 'gunmetal'), cube([3, 4, 3], [13, 11, 13], 'alloy'),
        cube([1, 5, 1], [6, 12, 4], 'edge'), cube([10, 5, 1], [15, 12, 4], 'edge'), cube([2, 6, .8], [5, 10, 1.2], 'indicator'), cube([11, 6, .8], [14, 10, 1.2], 'indicator')]
    for x in [3, 7, 11]:
        model['elements'].append(cube([x, 5, 12], [x + 2, 13, 16], 'copper'))
    for y in [5, 7, 9, 11]:
        model['elements'].append(cube([1, y, 4], [3, y + .7, 13], 'gunmetal'))
        model['elements'].append(cube([13, y, 4], [15, y + .7, 13], 'gunmetal'))
    write(ASSETS / 'models/item/engine_high_power.json', model)


def artwork():
    for name, base in [('mustafar_ruins_protector', (110, 108, 103)), ('mustafar_flame_lord', (68, 46, 43))]:
        image = Image.new('RGB', (16, 16))
        for y in range(16):
            for x in range(16):
                seam = -18 if x in [1, 7, 14] else 0; grain = ((x * 17 + y * 11) % 9) - 4
                image.putpixel((x, y), tuple(max(0, min(255, c + seam + grain)) for c in base))
        draw = ImageDraw.Draw(image); draw.line((3, 5, 12, 5), fill=(189, 121, 58), width=1); draw.line((3, 11, 12, 11), fill=(139, 91, 55), width=2)
        texture('entity/' + name, image)
    display = json.loads((ASSETS / 'models/item/mustafar_coordinates.json').read_text(encoding='utf-8'))['display']
    for name, dark in [('mustafar_jedi_altar', False), ('mustafar_sith_altar', True)]:
        write(ASSETS / f'models/block/{name}.json', {'textures': {'body': NS + 'block/mustafar_slag_ore', 'glyph': NS + 'item/navigation/mustafar', 'particle': NS + 'block/mustafar_slag_ore'},
            'elements': [cube([1, 0, 1], [15, 4, 15], 'body'), cube([3, 4, 3], [13, 10, 13], 'body'), cube([2, 10, 2], [14, 12, 14], 'glyph'),
                         cube([4, 12, 5], [6, 16 if dark else 14, 11], 'body'), cube([10, 12, 5], [12, 16 if dark else 14, 11], 'body')]})
        write(ASSETS / f'blockstates/{name}.json', {'variants': {'': {'model': NS + 'block/' + name}}})
    write(ASSETS / 'models/item/mustafar_boss_relic.json', {'textures': {'body': NS + 'block/mustafar_slag_ore', 'glyph': NS + 'item/navigation/mustafar', 'particle': NS + 'block/mustafar_slag_ore'},
        'elements': [cube([4, 1, 4], [12, 3, 12], 'body'), cube([5, 3, 5], [11, 11, 11], 'body'), cube([6, 11, 6], [10, 15, 10], 'glyph'),
                     cube([3, 5, 6], [5, 13, 10], 'body'), cube([11, 5, 6], [13, 13, 10], 'body')], 'display': display})
    notes = Image.new('RGB', (32, 32), (70, 57, 52)); draw = ImageDraw.Draw(notes)
    draw.rectangle((3, 3, 28, 28), outline=(184, 115, 61), width=2); draw.rectangle((6, 6, 25, 25), fill=(205, 179, 142))
    for y in [10, 14, 18, 22]: draw.line((9, y, 22 if y != 18 else 18, y), fill=(96, 57, 39))
    texture('item/navigation/mustafar_notes', notes)
    write(ASSETS / 'models/item/mustafar_field_notes.json', {'textures': {'paper': NS + 'item/navigation/mustafar_notes', 'particle': NS + 'item/navigation/mustafar_notes'},
        'elements': [cube([2, 1, 2], [14, 3, 14], 'paper'), cube([1, 0, 1], [15, 1, 15], 'paper'), cube([1, 3, 1], [15, 4, 15], 'paper')], 'display': display})
    write(DATA / 'recipe/mustafar_field_notes.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': item} for item in ['minecraft:paper', 'minecraft:charcoal', 'minecraft:basalt']], 'result': {'id': NS + 'mustafar_field_notes', 'count': 1}})


def language():
    entries = {
        'entity.legacyoftheforce.mustafar_ruins_protector': ('Protecteur des Ruines', 'Ruins Protector'),
        'entity.legacyoftheforce.mustafar_flame_lord': ('Seigneur des Flammes', 'Flame Lord'),
        'block.legacyoftheforce.mustafar_crystal_cluster': ('Formation de kyber volcanique', 'Volcanic kyber formation'),
        'block.legacyoftheforce.mustafar_slag_ore': ('Basalte à scories de Mustafar', 'Mustafar slag basalt'),
        'block.legacyoftheforce.mustafar_jedi_altar': ('Autel du Protecteur des Ruines', 'Ruins Protector altar'),
        'block.legacyoftheforce.mustafar_sith_altar': ('Autel du Seigneur des Flammes', 'Flame Lord altar'),
        'item.legacyoftheforce.mustafar_boss_relic': ('Relique des braises', 'Ember relic'),
        'item.legacyoftheforce.mustafar_field_notes': ('Carnet d’expédition : Mustafar', 'Mustafar expedition notes'),
        'item.legacyoftheforce.engine_high_power': ('Moteur haute puissance de Mustafar', 'Mustafar high-power engine'),
        'travel.legacyoftheforce.volcanic_warning': ('La lave proche vous épuise : cherchez un abri ou une protection contre le feu.', 'Nearby lava is wearing you down: seek shelter or fire protection.'),
    }
    for key, fr, en in [('bastion_guard', 'Garde de bastion', 'Bastion guard'), ('platform_leap', 'Bond de plateforme', 'Platform leap'), ('stone_riposte', 'Riposte de pierre', 'Stone riposte'),
        ('terrain_seal', 'Sceau brûlant', 'Scorching seal'), ('ruin_breaker', 'Briseur des ruines', 'Ruin breaker'), ('fire_bolt', 'Trait de braise', 'Ember bolt'),
        ('flame_dash', 'Ruée des flammes', 'Flame dash'), ('heat_pool', 'Poche de chaleur', 'Heat pool'), ('flame_sweep', 'Balayage ardent', 'Flame sweep'), ('eruption', 'Éruption', 'Eruption')]:
        entries['boss.legacyoftheforce.attack.' + key] = (fr, en)
    notes = [
        ('Les coffres des raffineries donnent provisions et scories. Les chambres de cristal possèdent un escalier gainé de pierre, au-dessus des coulées.', 'Refinery chests hold supplies and slag. Crystal vaults have stone-lined stairs above the flows.'),
        ('La résistance au feu aide face à la lave et aux braises. L’eau s’évapore ici ; une paroi protège de la chaleur proche, un couvre-chef seul ne suffit pas.', 'Fire resistance helps against lava and embers. Water evaporates here; walls shield nearby heat, but a head covering alone does not.'),
        ('Brisez ou déplacez les pierres du Protecteur pour ouvrir sa garde. Ses sceaux chauffent le sol : montez sur les plateformes ou quittez la marque.', 'Break or move the Protector’s stones to open his guard. His seals heat the floor: step onto platforms or leave the mark.'),
        ('Les traits de braise sont de vrais projectiles contrables par la Force. Poches et éruptions sont annoncées ; les couvertures restent utiles.', 'Ember bolts are real projectiles that can be countered with the Force. Pools and eruptions are signaled; cover remains useful.'),
        ('Le moteur haute puissance porte davantage mais pèse lourd. Les combats peuvent fournir un plan de sabre à garde et des coordonnées de Dagobah.', 'The high-power engine lifts more but is heavy. Encounters may yield a crossguard saber plan and Dagobah coordinates.'),
    ]
    for index, value in enumerate(notes): entries[f'notes.legacyoftheforce.mustafar.{index}'] = value
    for lang, column in [('fr_fr', 0), ('en_us', 1)]:
        path = ASSETS / f'lang/{lang}.json'; content = json.loads(path.read_text(encoding='utf-8')); content.update({key: value[column] for key, value in entries.items()}); write(path, content)


if __name__ == '__main__':
    terrain(); resources(); artwork(); language()
    print('Mustafar resources generated; native decoding, gameplay and rendering remain unverified.')
