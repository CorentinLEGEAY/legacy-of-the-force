"""Original navigation instruments and authored Tatooine terrain/site data.

Does not read or extract game assets. Settings and loot are normal datapack entries;
generation is never attached to login, chunk load, or travel handlers.
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


def update_tag(path, values):
    tag = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'replace': False, 'values': []}
    tag['values'] = list(dict.fromkeys(tag['values'] + values))
    write(path, tag)


def texture_path(name):
    path = ASSETS / f'textures/{name}.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def cube(lo, hi, texture):
    return {'from': lo, 'to': hi, 'faces': {face: {'texture': '#' + texture} for face in ('up', 'down', 'north', 'south', 'east', 'west')}}


def instruments():
    planets = ['ilum', 'tatooine', 'endor', 'mustafar', 'dagobah', 'jedha']
    colors = [(77, 153, 225), (229, 184, 67), (83, 175, 103), (230, 105, 47), (65, 187, 191), (165, 107, 216)]
    for index, (planet, color) in enumerate(zip(planets, colors)):
        image = Image.new('RGB', (32, 32))
        for y in range(32):
            for x in range(32):
                grain = ((x * 7 + y * 13) % 7) - 3
                image.putpixel((x, y), tuple(max(0, min(255, c + grain)) for c in (28, 43, 55)))
        draw = ImageDraw.Draw(image)
        draw.rectangle((2, 2, 29, 29), outline=(116, 129, 136), width=2)
        draw.line((5, 16, 27, 16), fill=(46, 75, 86))
        draw.line((16, 5, 16, 27), fill=(46, 75, 86))
        draw.ellipse((7, 7, 24, 24), outline=color, width=2)
        # Counted etched marks remain distinguishable independently of hue.
        for mark in range(index + 1):
            draw.line((7 + mark * 3, 25, 7 + mark * 3, 27), fill=color, width=1)
        draw.line((12, 20, 19, 11, 21, 18), fill=color, width=2)
        image.save(texture_path(f'item/navigation/{planet}'))
        write(ASSETS / f'models/item/{planet}_coordinates.json', {
            'textures': {'face': NS + f'item/navigation/{planet}', 'metal': NS + 'item/navigation/' + planet, 'particle': NS + f'item/navigation/{planet}'},
            'elements': [cube([1, 0, 3], [15, 1.5, 13], 'metal'), cube([2, 1.5, 4], [14, 2, 12], 'face'),
                         cube([0, 0, 6], [1, 2, 10], 'metal'), cube([15, 0, 6], [16, 2, 10], 'metal')],
            'display': {'gui': {'rotation': [35, -25, 0], 'translation': [0, 2, 0], 'scale': [1, 1, 1]},
                        'firstperson_righthand': {'rotation': [0, -25, 10], 'translation': [0, 4, -1], 'scale': [.8, .8, .8]},
                        'thirdperson_righthand': {'rotation': [80, 0, 0], 'translation': [0, 2, 0], 'scale': [.6, .6, .6]},
                        'ground': {'translation': [0, 3, 0], 'scale': [.6, .6, .6]}, 'fixed': {'rotation': [90, 0, 0]}}})


def noise(name, octave, amplitudes):
    write(DATA / f'worldgen/noise/{name}.json', {'firstOctave': octave, 'amplitudes': amplitudes})
    return {'type': 'minecraft:noise', 'noise': NS + name, 'xz_scale': 1, 'y_scale': 0}


def mul(amount, source):
    return {'type': 'minecraft:mul', 'argument1': amount, 'argument2': source}


def add(a, b):
    return {'type': 'minecraft:add', 'argument1': a, 'argument2': b}


def block(name):
    return {'type': 'minecraft:block', 'result_state': {'Name': 'minecraft:' + name}}


def terrain():
    dunes = noise('tatooine_dunes', -5, [1, .5, .25])
    canyon = noise('tatooine_canyons', -8, [1, .7, .4])
    density = add({'type': 'minecraft:y_clamped_gradient', 'from_y': 32, 'to_y': 160, 'from_value': 1, 'to_value': -1},
                  add(mul(.45, dunes), mul(.7, canyon)))
    # Bound the authored terrain vertically even at the extreme theoretical noise values.
    density = {'type': 'minecraft:min', 'argument1': {'type': 'minecraft:max', 'argument1': density,
               'argument2': {'type': 'minecraft:y_clamped_gradient', 'from_y': -64, 'to_y': -48, 'from_value': 4, 'to_value': -4}},
               'argument2': {'type': 'minecraft:y_clamped_gradient', 'from_y': 160, 'to_y': 192, 'from_value': 4, 'to_value': -4}}
    fields = ['barrier', 'fluid_level_floodedness', 'fluid_level_spread', 'lava', 'temperature', 'vegetation', 'continents', 'erosion',
              'depth', 'ridges', 'initial_density_without_jaggedness', 'final_density', 'vein_toggle', 'vein_ridged', 'vein_gap']
    router = dict.fromkeys(fields, 0)
    router.update(initial_density_without_jaggedness=density, final_density={'type': 'minecraft:interpolated', 'argument': density}, ridges=canyon)
    surface = {'type': 'minecraft:sequence', 'sequence': [
        {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:vertical_gradient', 'random_name': NS + 'tatooine_bedrock',
          'true_at_and_below': {'above_bottom': 0}, 'false_at_and_above': {'above_bottom': 5}}, 'then_run': block('bedrock')},
        {'type': 'minecraft:condition', 'if_true': {'type': 'minecraft:stone_depth', 'offset': 2, 'add_surface_depth': False,
          'secondary_depth_range': 0, 'surface_type': 'floor'}, 'then_run': block('sand')}, block('sandstone')]}
    write(DATA / 'worldgen/noise_settings/tatooine.json', {
        'noise': {'min_y': -64, 'height': 384, 'size_horizontal': 1, 'size_vertical': 2},
        'default_block': {'Name': 'minecraft:sandstone'}, 'default_fluid': {'Name': 'minecraft:water'}, 'noise_router': router,
        'surface_rule': surface, 'spawn_target': [], 'sea_level': 32, 'disable_mob_generation': False,
        'aquifers_enabled': False, 'ore_veins_enabled': False, 'legacy_random_source': False})
    dimension_type = json.loads((DATA / 'dimension_type/ilum.json').read_text(encoding='utf-8'))
    write(DATA / 'dimension_type/tatooine.json', dimension_type)
    biomes = ['tatooine_dunes', 'tatooine_canyons']
    biome_source = {'type': 'minecraft:multi_noise', 'biomes': [
        {'biome': NS + name, 'parameters': {'temperature': 0, 'humidity': 0, 'continentalness': 0, 'erosion': 0, 'depth': 0,
                                          'weirdness': [-1, 0] if i == 0 else [0, 1], 'offset': 0}} for i, name in enumerate(biomes)]}
    write(DATA / 'dimension/tatooine.json', {'type': NS + 'tatooine', 'generator': {'type': 'minecraft:noise', 'settings': NS + 'tatooine', 'biome_source': biome_source}})
    for i, name in enumerate(biomes):
        features = [[] for _ in range(11)]
        features[6] = ['minecraft:ore_iron_middle', 'minecraft:ore_copper']
        features[10] = [NS + 'tatooine_landscape']
        write(DATA / f'worldgen/biome/{name}.json', {'has_precipitation': False, 'temperature': 2, 'downfall': 0,
            'effects': {'fog_color': 0xD9B68A if i == 0 else 0xB5896C, 'sky_color': 0xB7CFDD, 'water_color': 0x568B9A, 'water_fog_color': 0x264459},
            'carvers': {'air': ['minecraft:cave', 'minecraft:canyon']}, 'features': features, 'spawn_costs': {},
            'spawners': {'creature': [{'type': 'minecraft:rabbit', 'weight': 6, 'minCount': 1, 'maxCount': 3}],
                'monster': [{'type': 'minecraft:husk', 'weight': 75, 'minCount': 1, 'maxCount': 3}, {'type': 'minecraft:spider', 'weight': 20, 'minCount': 1, 'maxCount': 2}],
                'ambient': [{'type': 'minecraft:bat', 'weight': 5, 'minCount': 1, 'maxCount': 2}]}})
    write(DATA / 'worldgen/configured_feature/tatooine_landscape.json', {'type': NS + 'tatooine_landscape', 'config': {'spire_chance': 9, 'cactus_attempts': 2}})
    write(DATA / 'worldgen/placed_feature/tatooine_landscape.json', {'feature': NS + 'tatooine_landscape', 'placement': []})
    names = ['tatooine_crystal_mine', 'tatooine_settlement', 'tatooine_wreck', 'tatooine_jedi_temple', 'tatooine_sith_sanctum']
    for kind, name in enumerate(names):
        write(DATA / f'worldgen/structure/{name}.json', {'type': NS + 'tatooine_site', 'site_kind': kind, 'biomes': [NS + name for name in biomes],
            'spawn_overrides': {}, 'step': 'surface_structures', 'terrain_adaptation': 'none'})
    write(DATA / 'worldgen/structure_set/tatooine_expeditions.json', {'structures': [{'structure': NS + name, 'weight': weight} for name, weight in zip(names, [2, 2, 1, 1, 1])],
        'placement': {'type': 'minecraft:random_spread', 'salt': 741928, 'spacing': 14, 'separation': 7, 'spread_type': 'linear'}})


def loot_and_resources():
    for name, color in [('tatooine_silica_ore', (187, 151, 103))]:
        image = Image.new('RGB', (32, 32))
        for y in range(32):
            for x in range(32):
                g = ((x * 13 + y * 31 + x * y) % 17) - 8
                image.putpixel((x, y), tuple(max(0, min(255, c + g)) for c in color))
        draw = ImageDraw.Draw(image)
        for x, y in [(4, 7), (16, 3), (23, 18), (8, 25), (17, 14)]:
            draw.polygon([(x, y - 2), (x + 3, y), (x + 2, y + 4), (x - 2, y + 2)], fill=(245, 219, 157), outline=(137, 112, 72))
        image.save(texture_path('block/' + name))
        write(ASSETS / f'models/block/{name}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': NS + 'block/' + name}})
        write(ASSETS / f'blockstates/{name}.json', {'variants': {'': {'model': NS + 'block/' + name}}})
    crystal = json.loads((ASSETS / 'models/item/crystal_yellow.json').read_text(encoding='utf-8'))
    crystal.pop('display', None)
    write(ASSETS / 'models/block/tatooine_crystal_cluster.json', crystal)
    write(ASSETS / 'blockstates/tatooine_crystal_cluster.json', {'variants': {'': {'model': NS + 'block/tatooine_crystal_cluster'}}})
    write(DATA / 'loot_table/blocks/tatooine_crystal_cluster.json', {'type': 'minecraft:block', 'random_sequence': NS + 'blocks/tatooine_crystal_cluster', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'ilum_crystal', 'functions': [{'function': NS + 'identify_ilum_crystal', 'planet': NS + 'tatooine'}]}],
        'conditions': [{'condition': 'minecraft:location_check', 'predicate': {'dimension': NS + 'tatooine'}},
                       {'condition': 'minecraft:match_tool', 'predicate': {'items': '#minecraft:pickaxes'}}, {'condition': 'minecraft:survives_explosion'}]}]})
    write(DATA / 'loot_table/blocks/tatooine_silica_ore.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': NS + 'tatooine_silica'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(DATA / 'loot_table/chests/tatooine_expedition.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}, 'entries': [
            {'type': 'minecraft:item', 'name': name, 'weight': weight, 'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': count}}]}
            for name, weight, count in [(NS + 'tatooine_silica', 5, 3), ('minecraft:iron_ingot', 4, 3), ('minecraft:bread', 5, 4),
                                        ('minecraft:coal', 4, 5), ('minecraft:gold_nugget', 3, 6), ('minecraft:paper', 2, 3)]]},
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'endor_coordinates', 'weight': 1}, {'type': 'minecraft:empty', 'weight': 3}]}]})
    for tag in ['mineable/pickaxe', 'needs_iron_tool']:
        update_tag(ROOT / f'data/minecraft/tags/block/{tag}.json', [NS + 'tatooine_crystal_cluster', NS + 'tatooine_silica_ore'])
    write(DATA / 'recipe/tatooine_coordinates.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': item} for item in ['minecraft:compass', 'minecraft:paper', 'minecraft:gold_ingot', 'minecraft:amethyst_shard']],
        'result': {'id': NS + 'tatooine_coordinates', 'count': 1}})


def desert_boss_assets():
    for name, base, dark in [('tatooine_wandering_knight', (186, 161, 121), False), ('tatooine_desert_exile', (92, 70, 60), True)]:
        image = Image.new('RGB', (16, 16))
        for y in range(16):
            for x in range(16):
                grain = ((x * 5 + y * 11) % 9) - 4
                seam = -18 if x in (1, 14) or y in (2, 13) else 0
                image.putpixel((x, y), tuple(max(0, min(255, c + grain + seam)) for c in base))
        draw = ImageDraw.Draw(image)
        draw.line((3, 4, 12, 4), fill=(137, 103, 66) if not dark else (159, 101, 65))
        draw.rectangle((6, 11, 9, 12), outline=(196, 167, 104))
        image.save(texture_path('entity/' + name))
    for name, dark in [('tatooine_jedi_altar', False), ('tatooine_sith_altar', True)]:
        texture = NS + 'item/navigation/tatooine'
        write(ASSETS / f'models/block/{name}.json', {'parent': 'minecraft:block/block', 'textures': {'metal': texture, 'particle': texture},
            'elements': [cube([1, 0, 1], [15, 3, 15], 'metal'), cube([3, 3, 3], [13, 9, 13], 'metal'),
                         cube([2, 9, 2], [14, 11, 14], 'metal'), cube([5 if dark else 6, 11, 6], [11 if dark else 10, 15, 10], 'metal')]})
        write(ASSETS / f'blockstates/{name}.json', {'variants': {'': {'model': NS + 'block/' + name}}})
    instrument = json.loads((ASSETS / 'models/item/tatooine_coordinates.json').read_text(encoding='utf-8'))
    relic = {'textures': instrument['textures'], 'display': instrument['display'], 'elements': [
        cube([4, 1, 4], [12, 3, 12], 'metal'), cube([5, 3, 5], [11, 11, 11], 'metal'), cube([3, 11, 3], [13, 13, 13], 'metal'),
        cube([6, 13, 6], [10, 15, 10], 'face')]}
    write(ASSETS / 'models/item/tatooine_boss_relic.json', relic)
    update_tag(ROOT / 'data/minecraft/tags/block/mineable/pickaxe.json', [NS + 'tatooine_jedi_altar', NS + 'tatooine_sith_altar'])
    update_tag(ROOT / 'data/minecraft/tags/block/needs_iron_tool.json', [NS + 'tatooine_jedi_altar', NS + 'tatooine_sith_altar'])
    # Racing engine trades lift and stability for propulsion; it is not a universal upgrade.
    write(DATA / 'speeder_modules/engine_racer.json', {'schemaVersion': 1, 'family': 'engine', 'mass': 2.8, 'thrust': 34, 'lift': 16,
        'capacity': 0, 'stability': .08, 'connectors': ['up', 'down', 'north', 'south', 'east', 'west'],
        'salvage': [{'item': 'minecraft:iron_nugget', 'count': 9}, {'item': NS + 'tatooine_silica', 'count': 1}]})
    write(DATA / 'recipe/engine_racer.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['SGS', 'RER', 'SGS'],
        'key': {'S': {'item': NS + 'tatooine_silica'}, 'G': {'item': 'minecraft:gold_ingot'}, 'R': {'item': 'minecraft:redstone'}, 'E': {'item': NS + 'engine_standard'}},
        'result': {'id': NS + 'engine_racer', 'count': 1}})
    standard = json.loads((ASSETS / 'models/item/engine_standard.json').read_text(encoding='utf-8'))
    standard['elements'] = [cube([2, 3, 2], [14, 6, 14], 'gunmetal'), cube([3, 6, 3], [13, 11, 13], 'alloy'),
        cube([5, 4, 0], [11, 10, 3], 'edge'), cube([5.5, 5, -.1], [10.5, 9, .5], 'indicator'),
        cube([4, 4, 13], [12, 10, 16], 'copper')]
    for x in [2, 4, 6, 8, 10, 12]:
        standard['elements'].append(cube([x, 10.5, 4], [x + .7, 14, 12], 'edge'))
    for z in [3, 6, 9, 12]:
        standard['elements'].append(cube([1, 6, z], [3, 12, z + .5], 'copper'))
        standard['elements'].append(cube([13, 6, z], [15, 12, z + .5], 'copper'))
    write(ASSETS / 'models/item/engine_racer.json', standard)
    notes = Image.new('RGB', (32, 32), (173, 143, 100))
    draw = ImageDraw.Draw(notes)
    draw.rectangle((2, 2, 29, 29), outline=(78, 60, 42), width=2)
    draw.rectangle((5, 5, 26, 26), fill=(223, 202, 156))
    for y in [9, 13, 17, 21]:
        draw.line((8, y, 22 if y != 17 else 17, y), fill=(98, 80, 52), width=1)
    draw.line((4, 2, 4, 29), fill=(94, 71, 50), width=2)
    notes.save(texture_path('item/navigation/tatooine_notes'))
    write(ASSETS / 'models/item/tatooine_field_notes.json', {'parent': 'minecraft:block/block',
        'textures': {'paper': NS + 'item/navigation/tatooine_notes', 'particle': NS + 'item/navigation/tatooine_notes'},
        'elements': [cube([2, 1, 2], [14, 3, 14], 'paper'), cube([1, 0, 1], [15, 1, 15], 'paper'), cube([1, 3, 1], [15, 4, 15], 'paper')],
        'display': instrument['display']})
    write(DATA / 'recipe/tatooine_field_notes.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': item} for item in ['minecraft:paper', 'minecraft:charcoal', 'minecraft:sandstone']],
        'result': {'id': NS + 'tatooine_field_notes', 'count': 1}})
    chest_path = DATA / 'loot_table/chests/tatooine_expedition.json'
    chest = json.loads(chest_path.read_text(encoding='utf-8'))
    chest['pools'].append({'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + 'tatooine_field_notes'}, {'type': 'minecraft:empty'}]})
    write(chest_path, chest)


def language():
    for lang in ['fr_fr', 'en_us']:
        path = ASSETS / f'lang/{lang}.json'
        content = json.loads(path.read_text(encoding='utf-8'))
        french = lang == 'fr_fr'
        info = [('ilum', 'Ilum', 'Glaces · cristal bleu/parade · dangers modérés', 'Ice · blue kyber/parry · moderate danger'),
                ('tatooine', 'Tatooine', 'Dunes et canyons · jaune/endurance · mines et épaves', 'Dunes and canyons · yellow/endurance · mines and wrecks'),
                ('endor', 'Endor', 'Forêts géantes · vert/régénération Force', 'Giant forests · green/Force regeneration'),
                ('mustafar', 'Mustafar', 'Volcans et lave · orange/puissance · danger extrême', 'Volcanoes and lava · orange/power · extreme danger'),
                ('dagobah', 'Dagobah', 'Marais et brume · cyan/télékinésie', 'Swamps and fog · cyan/telekinesis'),
                ('jedha', 'Jedha', 'Falaises et sanctuaires · violet/pouvoirs avancés', 'Cliffs and sanctuaries · purple/advanced powers')]
        for key, name, fr, en in info:
            content[f'travel.legacyoftheforce.planet_{key}'] = name
            content[f'travel.legacyoftheforce.info_{key}'] = fr if french else en
            content[f'item.legacyoftheforce.{key}_coordinates'] = f'Coordonnées : {name}' if french else f'{name} coordinates'
        content['block.legacyoftheforce.tatooine_crystal_cluster'] = 'Formation de kyber des dunes' if french else 'Dune kyber formation'
        content['block.legacyoftheforce.tatooine_silica_ore'] = 'Grès à silice de Tatooine' if french else 'Tatooine silica sandstone'
        content.update({
            'entity.legacyoftheforce.tatooine_wandering_knight': 'Chevalier Errant' if french else 'Wandering Knight',
            'entity.legacyoftheforce.tatooine_desert_exile': 'Exilé du Désert' if french else 'Desert Exile',
            'block.legacyoftheforce.tatooine_jedi_altar': 'Autel du Chevalier Errant' if french else 'Wandering Knight altar',
            'block.legacyoftheforce.tatooine_sith_altar': 'Autel de l’Exilé du Désert' if french else 'Desert Exile altar',
            'item.legacyoftheforce.tatooine_boss_relic': 'Relique des dunes' if french else 'Dune relic',
            'item.legacyoftheforce.engine_racer': 'Moteur de course de Tatooine' if french else 'Tatooine racing engine',
            'travel.legacyoftheforce.heat_warning': 'Le soleil vous fatigue : couvrez votre tête ou cherchez de l’ombre.' if french else 'The sun is wearing you down: cover your head or find shade.',
            'item.legacyoftheforce.tatooine_field_notes': 'Carnet d’expédition : Tatooine' if french else 'Tatooine expedition notes',
            'entity.legacyoftheforce.arena_ice': 'Objet d’arène' if french else 'Arena object',
            'boss.legacyoftheforce.step_inside': 'Entrez dans l’arène avant de lancer l’épreuve.' if french else 'Step inside the arena before starting the trial.',
        })
        notes = [
            ('Les entrées des mines se repèrent dans les plateaux de grès. Leur escalier descend vers une formation jaune rare.', 'Look for mine entrances in sandstone plateaus. Their stairs descend to a rare yellow formation.'),
            ('Le Chevalier Errant riposte après une frappe gardée. Déplacer ou briser son couvert de grès ouvre sa défense.', 'The Wandering Knight counters a guarded hit. Moving or breaking his sandstone cover opens his defense.'),
            ('L’Exilé annonce ses rafales et sa tempête. Mettez un pilier entre vous et le danger ; déplacer ses pierres brise sa concentration.', 'The Exile signals gusts and storms. Put a pillar between yourself and danger; moving his stones breaks his focus.'),
            ('L’anneau de sable laisse son centre libre et peut être sauté. Au soleil, un couvre-chef, l’ombre ou l’eau limitent la fatigue.', 'The sand ring leaves a safe center and can be jumped. Head coverings, shade or water limit sun fatigue.'),
            ('La silice entre dans le moteur de course : plus de propulsion, moins de portance. Les épaves peuvent contenir une carte vers Endor.', 'Silica is used in racing engines: more thrust, less lift. Wrecks can hold an Endor chart.'),
        ]
        for index, (fr, en) in enumerate(notes):
            content[f'notes.legacyoftheforce.tatooine.{index}'] = fr if french else en
        attacks = [('sidestep', 'Pas de côté', 'Sidestep'), ('riposte', 'Riposte', 'Riposte'), ('vault', 'Bond offensif', 'Vaulting strike'),
                   ('crosscut', 'Croisé des dunes', 'Dune crosscut'), ('sand_gust', 'Rafale de sable', 'Sand gust'), ('repulse', 'Poussée frontale', 'Frontal repulse'),
                   ('sandstorm', 'Tempête localisée', 'Focused sandstorm'), ('sand_ring', 'Anneau des sables', 'Sand ring')]
        for key, fr, en in attacks:
            content['boss.legacyoftheforce.attack.' + key] = fr if french else en
        content['travel.legacyoftheforce.help'] = (
            'Choisissez une destination et décodez sa carte. Posez un terminal à la base et emportez-en un second. Le départ finance le retour : posez le terminal transporté sur place pour revenir. Une destination indisponible ne permet pas le départ.'
            if french else 'Select a destination and decode its chart. Place one terminal at home and carry another. Departure prepays the return: place the carried terminal on site to go home. Unavailable destinations cannot be travelled to.')
        write(path, content)


if __name__ == '__main__':
    instruments()
    terrain()
    loot_and_resources()
    desert_boss_assets()
    language()
    import runpy
    runpy.run_path(str(Path(__file__).with_name('generate_endor_assets.py')), run_name='__main__')
    runpy.run_path(str(Path(__file__).with_name('generate_mustafar_assets.py')), run_name='__main__')
    runpy.run_path(str(Path(__file__).with_name('generate_dagobah_assets.py')), run_name='__main__')
    runpy.run_path(str(Path(__file__).with_name('generate_jedha_assets.py')), run_name='__main__')
    runpy.run_path(str(Path(__file__).with_name('generate_ilum_expansion_assets.py')), run_name='__main__')
    runpy.run_path(str(Path(__file__).with_name('generate_combat_holocrons.py')), run_name='__main__')
    runpy.run_path(str(Path(__file__).with_name('generate_expedition_guides.py')), run_name='__main__')
    runpy.run_path(str(Path(__file__).with_name('generate_environment_data.py')), run_name='__main__')
    runpy.run_path(str(Path(__file__).with_name('generate_people_assets.py')), run_name='__main__')
    print('Authored navigation and Tatooine resources generated; runtime verification remains pending.')
