"""Original mechanical models/materials, authored procedurally; no external asset input."""
import json
import math
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/legacyoftheforce'
MODELS = ROOT / 'models/item'
TEXTURES = ROOT / 'textures/item/saber'
FACES = ('down', 'up', 'north', 'south', 'west', 'east')
MATERIALS = {'alloy': (160, 173, 181), 'edge': (209, 221, 225),
             'gunmetal': (51, 62, 72), 'grip': (30, 35, 39), 'copper': (170, 99, 61),
             'indicator': (65, 194, 221), 'button': (136, 33, 30)}


def materials():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for name, base in MATERIALS.items():
        image = Image.new('RGB', (32, 32))
        for y in range(32):
            for x in range(32):
                # Fine horizontal machining, restrained wear; deterministic and tileable.
                value = math.sin(y * math.pi / 2) * 3 + ((x * 17 + y * 13) % 7 - 3)
                if name == 'grip':
                    value = (4 if y % 8 == 0 else -3 if y % 8 == 1 else 0) + ((x + y) % 3 - 1)
                elif name in ('indicator', 'button'):
                    value = 5 * math.cos((x - 15.5) * math.pi / 32)
                image.putpixel((x, y), tuple(max(0, min(255, round(v + value))) for v in base))
        image.save(TEXTURES / (name + '.png'))


def box(a, b, material, rotation=None):
    result = {'from': a, 'to': b, 'faces': {f: {'texture': '#' + material, 'uv': [0, 0, 16, 16]} for f in FACES}}
    if rotation:
        result['rotation'] = {'origin': [8, 8, 8], 'axis': 'y', 'angle': rotation}
    return result


def band(y, height, radius, material):
    # Stepped chamfer around each collar; shared dimensions align all modules.
    return [box([8-radius+.15, y, 8-radius+.15], [8+radius-.15, y+height, 8+radius-.15], material),
            box([8-radius, y+.12, 8-radius+.4], [8+radius, y+height-.12, 8+radius-.4], material),
            box([8-radius+.4, y+.12, 8-radius], [8+radius-.4, y+height-.12, 8+radius], material)]


def modules(claw=False):
    parts = {}
    parts['pommel'] = band(.25, .7, 1.9, 'edge') + band(.95, .65, 1.75, 'gunmetal')
    for x in (7.3, 8.35):
        parts['pommel'].append(box([x, .3, 6.05], [x+.3, .8, 6.2], 'gunmetal'))
    grip = [box([6.55, 1.6, 6.55], [9.45, 7.3, 9.45], 'gunmetal')]
    for x, z in ((6.35, 6.8), (9.25, 6.8)):
        grip.append(box([x, 1.85, z], [x+.4, 7.05, z+2.4], 'grip'))
    for z in (6.35, 9.25):
        grip.append(box([6.8, 1.85, z], [9.2, 7.05, z+.4], 'grip'))
    for y in (2, 3, 4, 5, 6):
        grip += band(y, .3, 1.8, 'gunmetal')
    grip += band(7, .45, 1.85, 'edge')
    parts['grip'] = grip
    parts['control'] = [box([6.5, 7.45, 6.5], [9.5, 9.4, 9.5], 'alloy'),
                        box([6.9, 7.65, 6.15], [9.1, 9.1, 6.5], 'gunmetal'),
                        box([7.2, 8.2, 5.95], [8.2, 8.9, 6.15], 'button'),
                        box([8.5, 8.2, 6.05], [8.85, 8.9, 6.15], 'indicator')]
    parts['neck'] = band(9.4, .45, 1.85, 'edge') + [box([6.8, 9.85, 6.8], [9.2, 11.25, 9.2], 'gunmetal')]
    for y in (10, 10.6):
        parts['neck'] += band(y, .3, 1.45, 'copper')
    emitter = band(11.25, .6, 1.85, 'alloy') + band(11.85, .5, 2.05, 'edge')
    emitter += [box([6.5, 12.35, 6.5], [9.5, 13, 9.5], 'gunmetal')]
    emitter += band(13, .4, 1.5, 'copper')
    emitter += [box([7.15, 13.4, 7.15], [8.85, 13.65, 8.85], 'gunmetal')]
    if claw:
        for x in (5.7, 9.7):
            emitter += [box([x, 11.8, 7.25], [x+.6, 15.5, 8.75], 'alloy'),
                        box([x+.1, 14.4, 7.4], [x+.5, 15.65, 8.6], 'edge')]
    parts['emitter'] = emitter
    circuit = [box([6.5, 5, 7.5], [9.5, 11, 8.5], 'gunmetal')]
    for x in (6.8, 8.7):
        circuit.append(box([x, 5.2, 7.3], [x+.25, 10.8, 7.5], 'copper'))
    for y in (6, 8.4):
        circuit.append(box([7.3, y, 7.1], [8.5, y+1.2, 7.5], 'indicator' if y==8.4 else 'grip'))
    parts['circuit'] = circuit
    return parts


DISPLAY = {'gui': {'rotation': [22, 225, -18], 'scale': [1.05]*3},
           'ground': {'rotation': [0, 0, 90], 'translation': [0, 1.7, 0], 'scale': [.5]*3},
           'fixed': {'rotation': [0, 0, 0], 'scale': [.9]*3}}
for hand, sign in (('righthand', 1), ('lefthand', -1)):
    DISPLAY['firstperson_'+hand] = {'rotation': [0, sign*90, -sign*25], 'translation': [sign*1, 1, 0], 'scale': [.8]*3}
    DISPLAY['thirdperson_'+hand] = {'rotation': [0, sign*90, -sign*20], 'translation': [0, 2, 0], 'scale': [.8]*3}


def write_model(name, elements, overrides=None, display=None, extra_textures=None):
    textures = {name: 'legacyoftheforce:item/saber/'+name for name in MATERIALS}
    textures['particle'] = textures['alloy']
    textures.update(extra_textures or {})
    result = {'textures': textures, 'elements': elements, 'display': display or DISPLAY}
    if overrides:
        result['overrides'] = overrides
    (MODELS/(name+'.json')).write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')


def main():
    # Retain all blade-specific emission metadata and predicates used by production.
    old = {name: json.loads((MODELS/(name+'.json')).read_text()) for name in
           ('lightsaber', 'lightsaber_active', 'lightsaber_claw', 'lightsaber_claw_active')}
    materials()
    for claw in (False, True):
        parts = modules(claw)
        hilt = [cube for part in ('pommel', 'grip', 'control', 'neck', 'emitter') for cube in parts[part]]
        name = 'lightsaber_claw' if claw else 'lightsaber'
        write_model(name, hilt, old[name].get('overrides'))
        active = name+'_active'
        write_model(active, hilt+old[active]['elements'][-2:], extra_textures={'blue': 'minecraft:block/light_blue_stained_glass', 'white': 'minecraft:block/white_concrete'})
        if claw:
            write_model('emitter_claw', parts['emitter'], display={'gui': {'rotation': [25, 225, 0], 'translation': [0, -5, 0], 'scale': [1.5]*3}})
        else:
            for part, cubes in parts.items():
                center = (min(c['from'][1] for c in cubes)+max(c['to'][1] for c in cubes))/2
                write_model(part+'_basic', cubes, display={'gui': {'rotation': [25, 225, 0], 'translation': [0, 8-center, 0], 'scale': [1.35]*3}})
    print('Generated 11 mechanical models and 7 original 32px material textures.')


if __name__ == '__main__':
    main()
