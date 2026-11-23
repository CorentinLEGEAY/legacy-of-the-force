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
    names = ['tatooine_crystal_mine', 'tatooine_settlement', 'tatooine_wreck']
    for kind, name in enumerate(names):
        write(DATA / f'worldgen/structure/{name}.json', {'type': NS + 'tatooine_site', 'site_kind': kind, 'biomes': [NS + name for name in biomes],
            'spawn_overrides': {}, 'step': 'surface_structures', 'terrain_adaptation': 'none'})
    write(DATA / 'worldgen/structure_set/tatooine_expeditions.json', {'structures': [{'structure': NS + name, 'weight': weight} for name, weight in zip(names, [2, 2, 1])],
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
        content['travel.legacyoftheforce.help'] = (
            'Choisissez une destination et décodez sa carte. Posez un terminal à la base et emportez-en un second. Le départ finance le retour : posez le terminal transporté sur place pour revenir. Une destination indisponible ne permet pas le départ.'
            if french else 'Select a destination and decode its chart. Place one terminal at home and carry another. Departure prepays the return: place the carried terminal on site to go home. Unavailable destinations cannot be travelled to.')
        write(path, content)


if __name__ == '__main__':
    instruments()
    terrain()
    loot_and_resources()
    language()
    print('Authored navigation and Tatooine resources generated; runtime verification remains pending.')
