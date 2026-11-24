"""Original plateau terrain and a real, one-chunk rune puzzle; encounter additions follow separately."""
import json
import runpy
from pathlib import Path
from PIL import Image, ImageDraw

shared = runpy.run_path(str(Path(__file__).with_name('generate_endor_assets.py')), run_name='lotf_authoring_helpers')
ROOT, DATA, ASSETS, NS = (shared[key] for key in ['ROOT', 'DATA', 'ASSETS', 'NS'])
write, tag, texture, cube, noise, grad, binary, block = (shared[key] for key in ['write', 'tag', 'texture', 'cube', 'noise', 'grad', 'binary', 'block'])


def terrain():
    cliffs = noise('jedha_cliffs', -7, [1, .6, .3]); mesas = noise('jedha_mesas', -8, [1, .5])
    step = {'type': 'minecraft:range_choice', 'input': mesas, 'min_inclusive': -.1, 'max_exclusive': .2, 'when_in_range': .35, 'when_out_of_range': -.1}
    density = binary('add', grad(16, 176, 1, -1), binary('add', binary('mul', .6, cliffs), step))
    density = binary('min', binary('max', density, grad(-64, -48, 4, -4)), grad(192, 224, 4, -4))
    fields = ['barrier', 'fluid_level_floodedness', 'fluid_level_spread', 'lava', 'temperature', 'vegetation', 'continents', 'erosion', 'depth', 'ridges', 'initial_density_without_jaggedness', 'final_density', 'vein_toggle', 'vein_ridged', 'vein_gap']
    router = dict.fromkeys(fields, 0); router.update(continents=mesas, initial_density_without_jaggedness=density, final_density={'type': 'minecraft:interpolated', 'argument': density})
    sequence = [
        {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:vertical_gradient', 'random_name': NS + 'jedha_bedrock', 'true_at_and_below': {'above_bottom': 0}, 'false_at_and_above': {'above_bottom': 5}}, 'then_run': block('bedrock')},
        {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:stone_depth', 'offset': 1, 'add_surface_depth': False, 'secondary_depth_range': 0, 'surface_type': 'floor'}, 'then_run': block('sand')},
    ]
    for y, material in [(116, 'sandstone'), (112, 'calcite'), (94, 'sandstone'), (90, 'calcite')]:
        sequence.append({'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:y_above', 'anchor': {'absolute': y}, 'surface_depth_multiplier': 0, 'add_stone_depth': False}, 'then_run': block(material)})
    sequence.append(block('sandstone'))
    write(DATA / 'worldgen/noise_settings/jedha.json', {'noise': {'min_y': -64, 'height': 384, 'size_horizontal': 1, 'size_vertical': 2}, 'default_block': {'Name': 'minecraft:sandstone'},
        'default_fluid': {'Name': 'minecraft:water'}, 'noise_router': router, 'surface_rule': {'type': 'minecraft:sequence', 'sequence': sequence}, 'spawn_target': [], 'sea_level': 32,
        'disable_mob_generation': False, 'aquifers_enabled': False, 'ore_veins_enabled': False, 'legacy_random_source': False})
    write(DATA / 'dimension_type/jedha.json', json.loads((DATA / 'dimension_type/ilum.json').read_text(encoding='utf-8')))
    biomes = ['jedha_sacred_dunes', 'jedha_calcite_cliffs']
    source = {'type': 'minecraft:multi_noise', 'biomes': [{'biome': NS + name, 'parameters': {'temperature': 0, 'humidity': 0, 'continentalness': [-1, 0] if i == 0 else [0, 1], 'erosion': 0, 'depth': 0, 'weirdness': 0, 'offset': 0}} for i, name in enumerate(biomes)]}
    write(DATA / 'dimension/jedha.json', {'type': NS + 'jedha', 'generator': {'type': 'minecraft:noise', 'settings': NS + 'jedha', 'biome_source': source}})
    write(DATA / 'tags/block/jedha_carver_replaceables.json', {'replace': False, 'values': ['minecraft:sandstone', 'minecraft:calcite', 'minecraft:sand']})
    write(DATA / 'worldgen/configured_carver/jedha_caves.json', {'type': 'minecraft:cave', 'config': {'probability': .12, 'y': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': -16}, 'max_inclusive': {'absolute': 120}},
        'yScale': .6, 'lava_level': {'above_bottom': 8}, 'replaceable': '#' + NS + 'jedha_carver_replaceables', 'horizontal_radius_multiplier': 1.1, 'vertical_radius_multiplier': .8, 'floor_level': -.7}})
    for i, name in enumerate(biomes):
        features = [[] for _ in range(11)]; features[10] = [NS + 'jedha_formations']
        write(DATA / f'worldgen/biome/{name}.json', {'has_precipitation': False, 'temperature': 1.2, 'downfall': 0,
            'effects': {'fog_color': 0xD1C4AD, 'sky_color': 0xB5CFDE, 'water_color': 0x6C9BA6, 'water_fog_color': 0x314C5B}, 'carvers': {'air': [NS + 'jedha_caves']}, 'features': features, 'spawn_costs': {},
            'spawners': {'creature': [{'type': 'minecraft:rabbit', 'weight': 6, 'minCount': 1, 'maxCount': 3}], 'monster': [{'type': 'minecraft:husk', 'weight': 30, 'minCount': 1, 'maxCount': 3}, {'type': 'minecraft:skeleton', 'weight': 25, 'minCount': 1, 'maxCount': 2}], 'ambient': [{'type': 'minecraft:bat', 'weight': 3, 'minCount': 1, 'maxCount': 2}]}})
    write(DATA / 'worldgen/configured_feature/jedha_formations.json', {'type': NS + 'jedha_landscape', 'config': {'formation_chance': 12, 'maximum_height': 12}})
    write(DATA / 'worldgen/placed_feature/jedha_formations.json', {'feature': NS + 'jedha_formations', 'placement': []})
    sites = ['jedha_crystal_chamber', 'jedha_rune_archive', 'jedha_ruins']
    for kind, name in enumerate(sites): write(DATA / f'worldgen/structure/{name}.json', {'type': NS + 'jedha_site', 'site_kind': kind, 'biomes': [NS + name for name in biomes], 'spawn_overrides': {}, 'step': 'surface_structures', 'terrain_adaptation': 'none'})
    write(DATA / 'worldgen/structure_set/jedha_expeditions.json', {'structures': [{'structure': NS + name, 'weight': weight} for name, weight in zip(sites, [1, 1, 3])], 'placement': {'type': 'minecraft:random_spread', 'salt': 826391, 'spacing': 14, 'separation': 7, 'spread_type': 'linear'}})


def resources():
    ore = Image.new('RGB', (32, 32))
    for y in range(32):
        for x in range(32):
            grain = ((x * 13 + y * 19) % 13) - 6; ore.putpixel((x, y), (186 + grain, 171 + grain, 141 + grain))
    draw = ImageDraw.Draw(ore)
    for x, y in [(5, 7), (20, 17), (10, 24)]: draw.polygon([(x, y), (x + 5, y), (x + 6, y + 4), (x + 1, y + 5)], fill=(132, 108, 153), outline=(202, 182, 202))
    texture('block/jedha_relic_ore', ore)
    write(ASSETS / 'models/block/jedha_relic_ore.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': NS + 'block/jedha_relic_ore'}})
    write(ASSETS / 'blockstates/jedha_relic_ore.json', {'variants': {'': {'model': NS + 'block/jedha_relic_ore'}}})
    crystal = json.loads((ASSETS / 'models/item/crystal_purple.json').read_text(encoding='utf-8')); crystal.pop('display', None)
    write(ASSETS / 'models/block/jedha_crystal_open.json', crystal)
    write(ASSETS / 'models/block/jedha_crystal_sealed.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': NS + 'block/jedha_relic_ore'}})
    write(ASSETS / 'blockstates/jedha_crystal_cluster.json', {'variants': {'attuned=false': {'model': NS + 'block/jedha_crystal_sealed'}, 'attuned=true': {'model': NS + 'block/jedha_crystal_open'}}})
    write(DATA / 'loot_table/blocks/jedha_crystal_cluster.json', {'type': 'minecraft:block', 'random_sequence': NS + 'blocks/jedha_crystal_cluster', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': NS + 'ilum_crystal', 'functions': [{'function': NS + 'identify_ilum_crystal', 'planet': NS + 'jedha'}]}],
        'conditions': [{'condition': 'minecraft:location_check', 'predicate': {'dimension': NS + 'jedha'}}, {'condition': 'minecraft:block_state_property', 'block': NS + 'jedha_crystal_cluster', 'properties': {'attuned': 'true'}},
            {'condition': 'minecraft:match_tool', 'predicate': {'items': '#minecraft:pickaxes'}}, {'condition': 'minecraft:survives_explosion'}]}]})
    write(DATA / 'loot_table/blocks/jedha_relic_ore.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'jedha_relic_dust'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    tag('mineable/pickaxe', [NS + 'jedha_crystal_cluster', NS + 'jedha_relic_ore', NS + 'jedha_rune_console', *[NS + 'jedha_rune_' + str(i) for i in range(3)]])
    tag('needs_iron_tool', [NS + 'jedha_crystal_cluster', NS + 'jedha_relic_ore'])
    write(DATA / 'loot_table/chests/jedha_expedition.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}, 'entries': [{'type': 'minecraft:item', 'name': name, 'weight': weight, 'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': count}}]}
            for name, weight, count in [(NS + 'jedha_relic_dust', 5, 3), ('minecraft:iron_ingot', 3, 3), ('minecraft:bread', 5, 4), ('minecraft:amethyst_shard', 3, 3), ('minecraft:paper', 4, 5)]]},
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'jedha_field_notes'}, {'type': 'minecraft:empty'}]}]})
    write(DATA / 'recipe/jedha_coordinates.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [{'item': item} for item in ['minecraft:compass', 'minecraft:paper', 'minecraft:calcite', 'minecraft:amethyst_shard']], 'result': {'id': NS + 'jedha_coordinates', 'count': 1}})
    write(DATA / 'speeder_modules/circuit_energy.json', {'schemaVersion': 1, 'family': 'battery', 'mass': 2.8, 'thrust': 0, 'lift': 0, 'capacity': 180, 'stability': 0,
        'connectors': ['up', 'down', 'north', 'south', 'east', 'west'], 'salvage': [{'item': 'minecraft:copper_ingot', 'count': 1}, {'item': NS + 'jedha_relic_dust', 'count': 1}]})
    write(DATA / 'recipe/circuit_energy.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['DCD', 'ABA', 'DCD'],
        'key': {'D': {'item': NS + 'jedha_relic_dust'}, 'C': {'item': 'minecraft:copper_ingot'}, 'A': {'item': 'minecraft:amethyst_shard'}, 'B': {'item': NS + 'battery_small'}}, 'result': {'id': NS + 'circuit_energy', 'count': 1}})
    model = json.loads((ASSETS / 'models/item/battery_small.json').read_text(encoding='utf-8'))
    model['elements'] = [cube([1, 1, 1], [15, 3, 15], 'gunmetal'), cube([2, 3, 2], [14, 4, 14], 'alloy'), cube([4, 4, 4], [12, 7, 12], 'copper'), cube([6, 7, 6], [10, 10, 10], 'indicator')]
    for x in [2, 12]:
        for z in [3, 7, 11]: model['elements'].append(cube([x, 4, z], [x + 2, 9, z + 2], 'edge'))
    write(ASSETS / 'models/item/circuit_energy.json', model)


def artwork():
    for glyph in range(3):
        image = Image.new('RGB', (32, 32), (170, 153, 119)); draw = ImageDraw.Draw(image); draw.rectangle((2, 2, 29, 29), outline=(105, 92, 73), width=2)
        if glyph == 0:
            draw.ellipse((9, 9, 22, 22), outline=(78, 67, 60), width=2)
            for line in [(16, 5, 16, 8), (16, 24, 16, 27), (5, 16, 8, 16), (24, 16, 27, 16)]: draw.line(line, fill=(78, 67, 60), width=2)
        elif glyph == 1:
            draw.ellipse((8, 7, 24, 25), fill=(78, 67, 60)); draw.ellipse((13, 5, 27, 21), fill=(170, 153, 119))
        else:
            for x in [10, 15, 20]: draw.rectangle((x, 8, x + 2, 24), fill=(78, 67, 60))
        for mark in range(glyph + 1): draw.point((12 + mark * 4, 27), fill=(78, 67, 60))
        texture('block/jedha_rune_' + str(glyph), image)
        write(ASSETS / f'models/block/jedha_rune_{glyph}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': NS + f'block/jedha_rune_{glyph}'}})
        write(ASSETS / f'blockstates/jedha_rune_{glyph}.json', {'variants': {'': {'model': NS + f'block/jedha_rune_{glyph}'}}})
    write(ASSETS / 'models/block/jedha_rune_console.json', {'textures': {'body': NS + 'block/jedha_relic_ore', 'glyph': NS + 'item/navigation/jedha', 'particle': NS + 'block/jedha_relic_ore'},
        'elements': [cube([1, 0, 1], [15, 3, 15], 'body'), cube([3, 3, 3], [13, 10, 13], 'body'), cube([2, 10, 2], [14, 13, 14], 'glyph')]})
    write(ASSETS / 'blockstates/jedha_rune_console.json', {'variants': {'': {'model': NS + 'block/jedha_rune_console'}}})
    display = json.loads((ASSETS / 'models/item/jedha_coordinates.json').read_text(encoding='utf-8'))['display']
    notes = Image.new('RGB', (32, 32), (126, 107, 91)); draw = ImageDraw.Draw(notes); draw.rectangle((3, 3, 28, 28), outline=(188, 161, 190), width=2); draw.rectangle((6, 6, 25, 25), fill=(223, 210, 179))
    for y in [10, 14, 18, 22]: draw.line((9, y, 22 if y != 18 else 18, y), fill=(103, 80, 112))
    texture('item/navigation/jedha_notes', notes)
    write(ASSETS / 'models/item/jedha_field_notes.json', {'textures': {'paper': NS + 'item/navigation/jedha_notes', 'particle': NS + 'item/navigation/jedha_notes'}, 'elements': [cube([2, 1, 2], [14, 3, 14], 'paper'), cube([1, 0, 1], [15, 1, 15], 'paper'), cube([1, 3, 1], [15, 4, 15], 'paper')], 'display': display})
    write(DATA / 'recipe/jedha_field_notes.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [{'item': item} for item in ['minecraft:paper', 'minecraft:charcoal', 'minecraft:calcite']], 'result': {'id': NS + 'jedha_field_notes', 'count': 1}})


def language():
    entries = {'block.legacyoftheforce.jedha_crystal_cluster': ('Sceau de kyber de Jedha', 'Jedha kyber seal'), 'block.legacyoftheforce.jedha_relic_ore': ('Pierre à reliques de Jedha', 'Jedha relic stone'),
        'block.legacyoftheforce.jedha_rune_console': ('Console des trois glyphes', 'Three-glyph console'), 'item.legacyoftheforce.jedha_field_notes': ('Carnet d’expédition : Jedha', 'Jedha expedition notes'),
        'item.legacyoftheforce.circuit_energy': ('Circuit énergétique de Jedha', 'Jedha energy circuit')}
    for i, (fr, en) in enumerate([('Soleil', 'Sun'), ('Lune', 'Moon'), ('Colonne', 'Pillar')]): entries[f'block.legacyoftheforce.jedha_rune_{i}'] = ('Glyphe : ' + fr, en + ' glyph')
    messages = {'rune_clue_0': ('La colonne veille ; le soleil se lève, puis la lune répond.', 'The pillar watches; the sun rises, then the moon answers.'),
        'rune_clue_1': ('La lune guide vers la colonne ; à l’aube, le soleil l’éclaire.', 'The moon guides toward the pillar; at dawn the sun lights it.'),
        'rune_clue_2': ('Le soleil cède à la lune ; sa lumière découvre la colonne.', 'The sun yields to the moon; its light reveals the pillar.'),
        'rune_progress': ('Résonance : %s/3 glyphes.', 'Resonance: %s/3 glyphs.'), 'rune_wrong': ('Le sceau se tait. Relisez l’indice et recommencez.', 'The seal falls silent. Read the clue and try again.'),
        'rune_open': ('Le sceau est stabilisé : le cristal peut être extrait.', 'The seal is stable: the crystal can be mined.'), 'rune_invalid': ('La source du sceau est absente ou incohérente.', 'The seal source is missing or inconsistent.'),
        'rune_pending': ('Déverrouillage conservé, minage suspendu. Retouchez la console pour reprendre.', 'Unlock retained, mining suspended. Touch the console to retry.')}
    for key, value in messages.items(): entries['travel.legacyoftheforce.' + key] = value
    notes = [('Les bandes blanches des falaises sont de la calcite. Les chambres et archives se trouvent sur des plateaux praticables.', 'The white cliff bands are calcite. Chambers and archives lie on traversable plateaus.'),
        ('Touchez la console pour lire son inscription. Les trois symboles sont le Soleil, la Lune et la Colonne ; suivez l’ordre des mots.', 'Touch a console to read its inscription. Its symbols are Sun, Moon and Pillar; follow their order in the words.'),
        ('Une erreur efface la séquence. Plusieurs voyageurs peuvent coopérer, mais la source de cristal est partagée.', 'A mistake resets the sequence. Travelers may cooperate, but the crystal source is shared.'),
        ('Une fois le sceau stabilisé, minez le cristal violet avec une pioche de fer. Le mécanisme ne crée pas de nouveau gisement.', 'Once the seal is stable, mine the purple crystal with an iron pickaxe. The mechanism does not create a new deposit.'),
        ('Les poussières de reliques servent à purifier un cristal corrompu. Le circuit de Jedha stocke plus d’énergie par module, mais pèse plus lourd.', 'Relic dust helps purify corrupted crystals. Jedha circuits store more energy per module but are heavier.')]
    for i, value in enumerate(notes): entries[f'notes.legacyoftheforce.jedha.{i}'] = value
    for lang, column in [('fr_fr', 0), ('en_us', 1)]:
        path = ASSETS / f'lang/{lang}.json'; content = json.loads(path.read_text(encoding='utf-8')); content.update({key: value[column] for key, value in entries.items()}); write(path, content)


if __name__ == '__main__':
    terrain(); resources(); artwork(); language()
    print('Jedha terrain and rune puzzle resources generated; encounters and native validation remain pending.')
