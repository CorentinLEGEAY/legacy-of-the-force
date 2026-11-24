"""Original wetland data, equipment and cloth textures; no external media."""
import json
import runpy
from pathlib import Path
from PIL import Image, ImageDraw

shared = runpy.run_path(str(Path(__file__).with_name('generate_endor_assets.py')), run_name='lotf_authoring_helpers')
ROOT, DATA, ASSETS, NS = (shared[key] for key in ['ROOT', 'DATA', 'ASSETS', 'NS'])
write, tag, texture, cube, noise, grad, binary, block = (shared[key] for key in ['write', 'tag', 'texture', 'cube', 'noise', 'grad', 'binary', 'block'])


def terrain():
    islands = noise('dagobah_islands', -7, [1, .5, .25])
    bogs = noise('dagobah_bogs', -8, [1, .6])
    density = binary('add', grad(32, 96, 1, -1), binary('add', binary('mul', .25, islands), binary('mul', .12, bogs)))
    density = binary('min', binary('max', density, grad(-64, -48, 4, -4)), grad(112, 144, 4, -4))
    fields = ['barrier', 'fluid_level_floodedness', 'fluid_level_spread', 'lava', 'temperature', 'vegetation', 'continents', 'erosion',
              'depth', 'ridges', 'initial_density_without_jaggedness', 'final_density', 'vein_toggle', 'vein_ridged', 'vein_gap']
    router = dict.fromkeys(fields, 0); router.update(continents=bogs, initial_density_without_jaggedness=density, final_density={'type': 'minecraft:interpolated', 'argument': density})
    surface = {'type': 'minecraft:sequence', 'sequence': [
        {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:vertical_gradient', 'random_name': NS + 'dagobah_bedrock', 'true_at_and_below': {'above_bottom': 0}, 'false_at_and_above': {'above_bottom': 5}}, 'then_run': block('bedrock')},
        {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:stone_depth', 'offset': 1, 'add_surface_depth': False, 'secondary_depth_range': 0, 'surface_type': 'floor'}, 'then_run': block('mud')},
        {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:stone_depth', 'offset': 3, 'add_surface_depth': False, 'secondary_depth_range': 0, 'surface_type': 'floor'}, 'then_run': block('clay')}, block('stone')]}
    write(DATA / 'worldgen/noise_settings/dagobah.json', {'noise': {'min_y': -64, 'height': 384, 'size_horizontal': 1, 'size_vertical': 2},
        'default_block': {'Name': 'minecraft:stone'}, 'default_fluid': {'Name': 'minecraft:water'}, 'noise_router': router, 'surface_rule': surface, 'spawn_target': [],
        'sea_level': 64, 'disable_mob_generation': False, 'aquifers_enabled': False, 'ore_veins_enabled': False, 'legacy_random_source': False})
    write(DATA / 'dimension_type/dagobah.json', json.loads((DATA / 'dimension_type/ilum.json').read_text(encoding='utf-8')))
    biomes = ['dagobah_drowned_marsh', 'dagobah_root_bogs']
    path = ROOT / 'data/minecraft/tags/worldgen/biome/allows_surface_slime_spawns.json'
    slime_biomes = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'replace': False, 'values': []}
    slime_biomes['values'] = list(dict.fromkeys(slime_biomes['values'] + [NS + name for name in biomes])); write(path, slime_biomes)
    source = {'type': 'minecraft:multi_noise', 'biomes': [{'biome': NS + name, 'parameters': {'temperature': 0, 'humidity': 0, 'continentalness': [-1, 0] if i == 0 else [0, 1],
        'erosion': 0, 'depth': 0, 'weirdness': 0, 'offset': 0}} for i, name in enumerate(biomes)]}
    write(DATA / 'dimension/dagobah.json', {'type': NS + 'dagobah', 'generator': {'type': 'minecraft:noise', 'settings': NS + 'dagobah', 'biome_source': source}})
    for i, name in enumerate(biomes):
        features = [[] for _ in range(11)]; feature = 'dagobah_marsh' if i == 0 else 'dagobah_roots'
        features[6] = ['minecraft:ore_coal_upper', 'minecraft:ore_iron_middle', 'minecraft:ore_copper']; features[10] = [NS + feature]
        write(DATA / f'worldgen/biome/{name}.json', {'has_precipitation': True, 'temperature': .8, 'downfall': 1,
            'effects': {'fog_color': 0x6F817E, 'sky_color': 0x9EAFAC, 'water_color': 0x526847, 'water_fog_color': 0x273D32, 'grass_color': 0x78834F, 'foliage_color': 0x536E48,
                'particle': {'probability': .003, 'options': {'type': 'minecraft:spore_blossom_air'}}},
            'carvers': {'air': ['minecraft:cave']}, 'features': features, 'spawn_costs': {}, 'spawners': {
                'creature': [{'type': 'minecraft:frog', 'weight': 8, 'minCount': 1, 'maxCount': 3}],
                'monster': [{'type': 'minecraft:spider', 'weight': 30, 'minCount': 1, 'maxCount': 2}, {'type': 'minecraft:slime', 'weight': 10, 'minCount': 1, 'maxCount': 2}, {'type': 'minecraft:drowned', 'weight': 15, 'minCount': 1, 'maxCount': 2}],
                'water_ambient': [{'type': 'minecraft:tropical_fish', 'weight': 5, 'minCount': 1, 'maxCount': 3}], 'ambient': [{'type': 'minecraft:bat', 'weight': 3, 'minCount': 1, 'maxCount': 2}]}})
        write(DATA / f'worldgen/configured_feature/{feature}.json', {'type': NS + 'dagobah_landscape', 'config': {'tree_chance': 3 if i == 0 else 1, 'minimum_height': 8, 'maximum_height': 14, 'plant_attempts': 24}})
        write(DATA / f'worldgen/placed_feature/{feature}.json', {'feature': NS + feature, 'placement': []})
    sites = ['dagobah_crystal_grotto', 'dagobah_stilt_hut', 'dagobah_sunken_ruins', 'dagobah_jedi_temple', 'dagobah_sith_sanctum']
    for kind, name in enumerate(sites):
        write(DATA / f'worldgen/structure/{name}.json', {'type': NS + 'dagobah_site', 'site_kind': kind, 'biomes': [NS + name for name in biomes], 'spawn_overrides': {}, 'step': 'surface_structures', 'terrain_adaptation': 'none'})
    write(DATA / 'worldgen/structure_set/dagobah_expeditions.json', {'structures': [{'structure': NS + name, 'weight': weight} for name, weight in zip(sites, [2, 2, 1, 1, 1])],
        'placement': {'type': 'minecraft:random_spread', 'salt': 384127, 'spacing': 14, 'separation': 7, 'spread_type': 'linear'}})


def resources():
    path = DATA / 'tags/entity_type/boss_mobs.json'; value = json.loads(path.read_text(encoding='utf-8')); value['values'] = list(dict.fromkeys(value['values'] + [NS + 'dagobah_force_hermit', NS + 'dagobah_illusion_master'])); write(path, value)
    root = Image.new('RGB', (32, 32))
    for y in range(32):
        for x in range(32):
            grain = ((x * 13 + y * 11) % 11) - 5; line = -15 if (x + y // 4) % 8 < 2 else 0
            root.putpixel((x, y), (90 + grain + line, 83 + grain + line, 60 + grain + line))
    draw = ImageDraw.Draw(root)
    for x, y in [(6, 5), (21, 16), (9, 24)]: draw.ellipse((x, y, x + 4, y + 5), fill=(99, 166, 163), outline=(161, 203, 183))
    texture('block/dagobah_essence_root', root)
    write(ASSETS / 'models/block/dagobah_essence_root.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': NS + 'block/dagobah_essence_root'}})
    write(ASSETS / 'blockstates/dagobah_essence_root.json', {'variants': {'': {'model': NS + 'block/dagobah_essence_root'}}})
    crystal = json.loads((ASSETS / 'models/item/crystal_cyan.json').read_text(encoding='utf-8')); crystal.pop('display', None)
    write(ASSETS / 'models/block/dagobah_crystal_cluster.json', crystal); write(ASSETS / 'blockstates/dagobah_crystal_cluster.json', {'variants': {'': {'model': NS + 'block/dagobah_crystal_cluster'}}})
    write(DATA / 'loot_table/blocks/dagobah_crystal_cluster.json', {'type': 'minecraft:block', 'random_sequence': NS + 'blocks/dagobah_crystal_cluster', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': NS + 'ilum_crystal', 'functions': [{'function': NS + 'identify_ilum_crystal', 'planet': NS + 'dagobah'}]}],
        'conditions': [{'condition': 'minecraft:location_check', 'predicate': {'dimension': NS + 'dagobah'}}, {'condition': 'minecraft:match_tool', 'predicate': {'items': '#minecraft:pickaxes'}}, {'condition': 'minecraft:survives_explosion'}]}]})
    write(DATA / 'loot_table/blocks/dagobah_essence_root.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'dagobah_essence'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for name in ['mineable/pickaxe', 'needs_iron_tool']: tag(name, [NS + key for key in ['dagobah_crystal_cluster', 'dagobah_jedi_altar', 'dagobah_sith_altar']])
    tag('mineable/shovel', [NS + 'dagobah_essence_root']); tag('needs_stone_tool', [NS + 'dagobah_essence_root'])
    write(DATA / 'loot_table/chests/dagobah_expedition.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}, 'entries': [{'type': 'minecraft:item', 'name': name, 'weight': weight, 'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': count}}]}
            for name, weight, count in [(NS + 'dagobah_essence', 5, 3), ('minecraft:iron_ingot', 3, 3), ('minecraft:bread', 5, 4), ('minecraft:copper_ingot', 3, 3), ('minecraft:glow_berries', 4, 6)]]},
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'jedha_coordinates'}, {'type': 'minecraft:empty', 'weight': 3}]},
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'dagobah_field_notes'}, {'type': 'minecraft:empty'}]}]})
    write(DATA / 'recipe/dagobah_coordinates.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [{'item': item} for item in ['minecraft:compass', 'minecraft:paper', 'minecraft:slime_ball', 'minecraft:amethyst_shard']], 'result': {'id': NS + 'dagobah_coordinates', 'count': 1}})
    write(DATA / 'speeder_modules/stabilizer_levitation.json', {'schemaVersion': 1, 'family': 'stabilizer', 'mass': 2.5, 'thrust': 0, 'lift': 16, 'capacity': 0, 'stability': .3,
        'connectors': ['up', 'down', 'north', 'south', 'east', 'west'], 'salvage': [{'item': 'minecraft:copper_ingot', 'count': 1}, {'item': NS + 'dagobah_essence', 'count': 1}]})
    write(DATA / 'recipe/stabilizer_levitation.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['ECE', 'RGR', ' E '],
        'key': {'E': {'item': NS + 'dagobah_essence'}, 'C': {'item': 'minecraft:copper_ingot'}, 'R': {'item': 'minecraft:redstone'}, 'G': {'item': NS + 'stabilizer_gyro'}}, 'result': {'id': NS + 'stabilizer_levitation', 'count': 1}})
    model = json.loads((ASSETS / 'models/item/stabilizer_gyro.json').read_text(encoding='utf-8'))
    model['elements'] = [cube([2, 1, 2], [14, 3, 14], 'gunmetal'), cube([6, 3, 6], [10, 13, 10], 'copper')]
    for y in [4, 8, 12]:
        for lo, hi in [([2, y, 2], [14, y + 1, 4]), ([2, y, 12], [14, y + 1, 14]), ([2, y, 4], [4, y + 1, 12]), ([12, y, 4], [14, y + 1, 12])]: model['elements'].append(cube(lo, hi, 'edge'))
    model['elements'].append(cube([6.5, 13, 6.5], [9.5, 15, 9.5], 'indicator')); write(ASSETS / 'models/item/stabilizer_levitation.json', model)


def artwork():
    for name, base in [('dagobah_force_hermit', (112, 123, 100)), ('dagobah_illusion_master', (60, 77, 84))]:
        image = Image.new('RGB', (16, 16))
        for y in range(16):
            for x in range(16):
                line = -18 if x in [1, 14] or y == 11 else 0; grain = ((x * 7 + y * 17) % 9) - 4
                image.putpixel((x, y), tuple(max(0, min(255, c + grain + line)) for c in base))
        ImageDraw.Draw(image).line((4, 5, 11, 5), fill=(157, 181, 166)); texture('entity/' + name, image)
    display = json.loads((ASSETS / 'models/item/dagobah_coordinates.json').read_text(encoding='utf-8'))['display']
    for name, dark in [('dagobah_jedi_altar', False), ('dagobah_sith_altar', True)]:
        write(ASSETS / f'models/block/{name}.json', {'textures': {'body': NS + 'block/dagobah_essence_root', 'glyph': NS + 'item/navigation/dagobah', 'particle': NS + 'block/dagobah_essence_root'},
            'elements': [cube([1, 0, 1], [15, 3, 15], 'body'), cube([3, 3, 3], [13, 10, 13], 'body'), cube([2, 10, 2], [14, 12, 14], 'glyph'), cube([5, 12, 5], [11, 15 if dark else 14, 11], 'body')]})
        write(ASSETS / f'blockstates/{name}.json', {'variants': {'': {'model': NS + 'block/' + name}}})
    write(ASSETS / 'models/item/dagobah_boss_relic.json', {'textures': {'body': NS + 'block/dagobah_essence_root', 'glyph': NS + 'item/navigation/dagobah', 'particle': NS + 'block/dagobah_essence_root'},
        'elements': [cube([4, 1, 4], [12, 3, 12], 'body'), cube([5, 3, 5], [11, 5, 11], 'body'), cube([6, 5, 6], [10, 13, 10], 'glyph'), cube([4, 13, 4], [12, 15, 12], 'body')], 'display': display})
    notes = Image.new('RGB', (32, 32), (67, 86, 78)); draw = ImageDraw.Draw(notes); draw.rectangle((3, 3, 28, 28), outline=(139, 171, 150), width=2); draw.rectangle((6, 6, 25, 25), fill=(189, 201, 175))
    for y in [10, 14, 18, 22]: draw.line((9, y, 22 if y != 18 else 18, y), fill=(57, 82, 78))
    texture('item/navigation/dagobah_notes', notes)
    write(ASSETS / 'models/item/dagobah_field_notes.json', {'textures': {'paper': NS + 'item/navigation/dagobah_notes', 'particle': NS + 'item/navigation/dagobah_notes'}, 'elements': [cube([2, 1, 2], [14, 3, 14], 'paper'), cube([1, 0, 1], [15, 1, 15], 'paper'), cube([1, 3, 1], [15, 4, 15], 'paper')], 'display': display})
    write(DATA / 'recipe/dagobah_field_notes.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [{'item': item} for item in ['minecraft:paper', 'minecraft:charcoal', 'minecraft:clay_ball']], 'result': {'id': NS + 'dagobah_field_notes', 'count': 1}})


def language():
    entries = {
        'entity.legacyoftheforce.dagobah_force_hermit': ('Ermite de la Force', 'Force Hermit'), 'entity.legacyoftheforce.dagobah_illusion_master': ('Maître des Illusions', 'Illusion Master'),
        'block.legacyoftheforce.dagobah_crystal_cluster': ('Formation de kyber des marais', 'Marsh kyber formation'), 'block.legacyoftheforce.dagobah_essence_root': ('Racine imprégnée de Dagobah', 'Dagobah essence root'),
        'block.legacyoftheforce.dagobah_jedi_altar': ('Autel de l’Ermite', 'Force Hermit altar'), 'block.legacyoftheforce.dagobah_sith_altar': ('Autel du Maître des Illusions', 'Illusion Master altar'),
        'item.legacyoftheforce.dagobah_boss_relic': ('Relique des brumes', 'Mist relic'), 'item.legacyoftheforce.dagobah_field_notes': ('Carnet d’expédition : Dagobah', 'Dagobah expedition notes'),
        'item.legacyoftheforce.stabilizer_levitation': ('Stabilisateur de lévitation de Dagobah', 'Dagobah levitation stabilizer'),
        'boss.legacyoftheforce.dread_warning': ('Une peur passagère vous affaiblit. Éloignez-vous ou cherchez un couvert.', 'A brief dread weakens you. Move away or seek cover.'),
    }
    for key, fr, en in [('staff_strike', 'Frappe de l’Ermite', 'Hermit strike'), ('controlled_push', 'Poussée mesurée', 'Controlled push'), ('orbit_stones', 'Pierres en orbite', 'Orbiting stones'), ('perception_trial', 'Épreuve de perception', 'Perception trial'),
        ('mind_counter', 'Contre de Force', 'Force counter'), ('mire_wave', 'Onde des marais', 'Mire wave'), ('mirror_veil', 'Voile des doubles', 'Mirror veil'), ('dread_pulse', 'Onde de peur', 'Dread pulse'),
        ('deceptive_bolt', 'Trait trompeur', 'Deceptive bolt'), ('veil_step', 'Pas des brumes', 'Veil step'), ('mental_spike', 'Pointe mentale', 'Mental spike'), ('shadow_chorus', 'Chœur des ombres', 'Shadow chorus')]: entries['boss.legacyoftheforce.attack.' + key] = (fr, en)
    notes = [
        ('Les petites hauteurs abritent des escaliers vers les grottes scellées. Le cyan et les racines imprégnées y sont rares.', 'Small rises shelter stairs to sealed grottos. Cyan kyber and essence roots are rare there.'),
        ('La boue ralentit les pieds nus : des bottes aident. Le stabilisateur de Dagobah apporte plus de portance, au prix de masse et de stabilité.', 'Mud slows bare feet: boots help. The Dagobah stabilizer adds lift at the cost of mass and stability.'),
        ('Les pierres de l’Ermite bougent réellement. Déplacez-les ou brisez-les pour ouvrir sa défense ; une impulsion de Force peut susciter un contre annoncé.', 'The Hermit’s stones really move. Shift or break them to open his defense; a Force impulse can prompt a signaled counter.'),
        ('Le Sens révèle le vrai adversaire et dissipe ses doubles. Un leurre frappé disparaît sans donner d’XP ni changer l’alignement.', 'Sense reveals the real foe and dispels doubles. A struck decoy disappears without XP or alignment changes.'),
        ('Le Maître mêle des traits réels et inoffensifs. Perception, mouvement et couverts sont précieux ; l’essence sert aussi à concentrer un cristal lié.', 'The Master mixes real and harmless bolts. Perception, movement and cover matter; essence also helps focus a bonded crystal.'),
    ]
    for index, value in enumerate(notes): entries[f'notes.legacyoftheforce.dagobah.{index}'] = value
    for lang, column in [('fr_fr', 0), ('en_us', 1)]:
        path = ASSETS / f'lang/{lang}.json'; content = json.loads(path.read_text(encoding='utf-8')); content.update({key: value[column] for key, value in entries.items()}); write(path, content)


if __name__ == '__main__':
    terrain(); resources(); artwork(); language()
    print('Dagobah resources generated; native loading, gameplay and visual validation remain pending.')
