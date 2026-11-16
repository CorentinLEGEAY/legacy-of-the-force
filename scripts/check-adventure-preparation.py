"""Reconcile two native preparation clients, crafting inputs, WALs and actual saved regions.

This checks preparation only; it deliberately cannot validate the P4-05 adventure.
"""
import argparse
from collections import Counter
import importlib.util
import json
from pathlib import Path
import struct
from read_nbt import load

spec = importlib.util.spec_from_file_location('ilum', Path(__file__).with_name('check-ilum-worlds.py'))
ilum = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ilum)
MOD = 'legacyoftheforce:'
VANILLA = {'oak_planks': 48, 'iron_ingot': 96, 'copper_ingot': 48, 'redstone': 64,
           'leather': 16, 'paper': 32, 'ink_sac': 4, 'glass': 8, 'amethyst_shard': 8,
           'lapis_lazuli': 16, 'stone': 8, 'gold_ingot': 8, 'poppy': 4, 'cooked_beef': 32}
PARTS = ['emitter', 'neck', 'grip', 'control', 'circuit', 'pommel']
MODULES = ['chassis_light', 'engine_standard', 'battery_small', 'seat_pilot']
RECIPES = (['minecraft:crafting_table'] * 3 + ['minecraft:' + s for s in
           ['stick', 'iron_nugget', 'stone_button', 'compass', 'iron_pickaxe', 'iron_sword',
            'iron_helmet', 'iron_chestplate', 'iron_leggings', 'iron_boots']] +
           [MOD + s for s in [*(p + '_basic' for p in PARTS), 'saber_workshop',
            'speeder_garage', *MODULES, 'speeder_blueprint', 'ilum_coordinates',
            'galactic_terminal']] + [MOD + 'speeder_energy_cell'] * 4)


def counts(inventory):
    result = Counter()
    for stack in inventory:
        assert isinstance(stack['count'], int) and 1 <= stack['count'] <= 64
        result[stack['id']] += stack['count']
    return result


def spend(ledger, material, count=1):
    assert ledger[material] >= count, f'Unpaid material: {material}'
    ledger[material] -= count
    if not ledger[material]:
        del ledger[material]


def entities(world):
    result = []
    folder = world / 'entities'
    for region in folder.glob('r.*.*.mca'):
        _, rx, rz, _ = region.name.split('.')
        header = region.read_bytes()[:4096]
        for index in range(1024):
            if struct.unpack_from('>I', header, index * 4)[0]:
                chunk = ilum.read_chunk(folder, int(rx) * 32 + index % 32, int(rz) * 32 + index // 32)
                result.extend(chunk['Entities'])
    return result


def verify(repo, run):
    world = repo / f'run-adventure-{run}/world'
    result = json.loads((repo / f'.tools/adventure-preparation-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert result['observed'] and result['serverExitCode'] == 0 and len(result['clients']) == 2
    proof = load(world / 'validation/adventure-preparation-server.nbt')
    assert proof['schemaVersion'] == 1 and proof['scope'] == 'preparation_only'
    assert len(proof['before']) == len(proof['players']) == len(proof['vehicles']) == 2
    assert len(proof['crafts']) == 64
    owners = [ilum.uid(p['UUID']) for p in proof['players']]
    assert len(set(owners)) == 2
    pids = [proof['pid']]
    saved_entities = entities(world)
    vehicles = [e for e in saved_entities if e['id'] == MOD + 'speeder']
    assert len(vehicles) == 2, 'Count all saved vehicles, including outside the camp search boxes'
    assert len({ilum.uid(e['UUID']) for e in vehicles}) == 2
    assert {ilum.uid(e['vehicleState']['ownerId']) for e in vehicles} == set(owners)
    assert not any(e['id'] == 'minecraft:item' and e['Item']['id'].startswith(MOD) for e in saved_entities)
    journals = list((world / 'data/legacyoftheforce/speeder_assembly').glob('*.nbt'))
    assert len(journals) == 2
    hilts, blueprints, operations = set(), set(), set()
    for index, actor in enumerate(proof['players']):
        owner = owners[index]
        client = result['clients'][index]
        assert client['name'] == ['AdventureOne', 'AdventureTwo'][index]
        assert client['exitCode'] == 0 and not client['forced']
        profile = Path(client['profile'])
        assert profile.resolve() == (repo / f'run-adventure-client-{["one", "two"][index]}-{run}').resolve()
        cp = load(profile / 'validation/adventure-preparation-client.nbt')
        assert cp['scope'] == 'preparation_only' and cp['schemaVersion'] == 1
        assert cp['pid'] == client['pid'] and ilum.uid(cp['owner']) == owner
        assert cp['dimension'] == 'minecraft:overworld'
        pids.append(cp['pid'])
        before = proof['before'][index]
        assert ilum.uid(before['UUID']) == owner
        ledger = counts(before['Inventory'])
        assert ledger == Counter({'minecraft:' + s: n for s, n in VANILLA.items()})
        native_crafts = [c for c in proof['crafts'] if ilum.uid(c['owner']) == owner]
        assert [c['recipe'] for c in native_crafts] == [c['recipe'] for c in cp['crafts']] == RECIPES
        for ordinal, (server_craft, client_craft) in enumerate(zip(native_crafts, cp['crafts'])):
            if ordinal == 3:
                spend(ledger, 'minecraft:crafting_table')  # Placed before opening the native 3x3 menu.
            assert counts(client_craft['before']['Inventory']) == ledger, (owner, ordinal, 'before')
            assert client_craft['grid'] == (2 if ordinal < 3 else 3)
            assert server_craft['result'] == client_craft['result']
            inputs = server_craft['inputs']
            assert 1 <= len(inputs) <= 9 and len({s['gridSlot'] for s in inputs}) == len(inputs)
            # Picking up a single-item source leaves no remainder to return.
            assert len(inputs) * 2 + 1 <= client_craft['clicks'] <= len(inputs) * 3 + 1
            for stack in inputs:
                assert stack['count'] == 1 and 0 <= stack['gridSlot'] < client_craft['grid'] ** 2
                spend(ledger, stack['id'])
            output = server_craft['result']
            ledger[output['id']] += output['count']
            assert counts(client_craft['after']['Inventory']) == ledger, (owner, ordinal, 'after')
        for part in PARTS:
            spend(ledger, MOD + part + '_basic')
        ledger[MOD + 'lightsaber'] += 1
        for name in MODULES + ['saber_workshop', 'speeder_garage', 'galactic_terminal']:
            spend(ledger, MOD + name)
        saved = load(world / f'playerdata/{owner}.dat')
        assert counts(actor['Inventory']) == counts(saved['Inventory']) == counts(cp['Inventory']) == ledger
        assert saved['Inventory'] == actor['Inventory'], 'Actual saved components/slots differ from server proof'
        assert sorted(cp['Inventory'], key=lambda s: s['Slot']) == sorted(actor['Inventory'], key=lambda s: s['Slot'])
        for state in [before, actor, saved]:
            assert state['playerGameType'] == 0 and not state['abilities']['instabuild']
            assert state['Dimension'] == 'minecraft:overworld' and 'RootVehicle' not in state
            rpg = state['neoforge:attachments'][MOD + 'player_progress']
            assert rpg['level'] == 1 and rpg['totalXpEarned'] == rpg['xpInLevel'] == rpg['alignmentTenths'] == 0
        armor = {s['Slot'] & 255: s['id'] for s in saved['Inventory'] if (s['Slot'] & 255) >= 100}
        assert armor == dict(enumerate(['minecraft:iron_boots', 'minecraft:iron_leggings',
                                      'minecraft:iron_chestplate', 'minecraft:iron_helmet'], 100))
        stacks = {s['id']: s for s in saved['Inventory'] if s['id'].startswith(MOD)}
        assert set(stacks) == {MOD + n for n in ['lightsaber', 'speeder_blueprint', 'ilum_coordinates', 'galactic_terminal', 'speeder_energy_cell']}
        assert all(stacks[MOD + n]['count'] == count for n, count in
                   [('lightsaber', 1), ('speeder_blueprint', 1), ('ilum_coordinates', 1), ('galactic_terminal', 1), ('speeder_energy_cell', 4)])
        hilt = stacks[MOD + 'lightsaber']['components'][MOD + 'saber_assembly']
        assert hilt['schemaVersion'] == 1 and hilt['type'] == 'single' and 'crystal' not in hilt
        assert hilt['parts'] == {p: MOD + p + '_basic' for p in PARTS}
        hilts.add(ilum.uid(hilt['saberId']))
        bp = stacks[MOD + 'speeder_blueprint']['components'][MOD + 'speeder_blueprint']
        assert bp['schemaVersion'] == 1 and ilum.uid(bp['authorId']) == owner
        assert Counter(m['kind'] for m in bp['modules']) == Counter(MOD + m for m in MODULES)
        blueprints.add(ilum.uid(bp['blueprintId']))
        receipt = saved['neoforge:attachments'][MOD + 'speeder_assembly_receipt']
        assert receipt['schemaVersion'] == 1 and receipt['revision'] == 1
        operation = ilum.uid(receipt['operation'])
        operations.add(operation)
        wal = load(world / f'data/legacyoftheforce/speeder_assembly/{owner}.nbt')
        assert wal['schemaVersion'] == 1 and wal['phase'] == 'DONE'
        assert ilum.uid(wal['owner']) == owner and ilum.uid(wal['operation']) == operation
        assert wal['before']['receipt']['revision'] == 0 and wal['after']['receipt'] == receipt
        cost = counts(wal['before']['Inventory'])
        for module in MODULES:
            spend(cost, MOD + module)
        assert cost == counts(wal['after']['Inventory']) == ledger
        vehicle = next(v for v in vehicles if ilum.uid(v['vehicleState']['ownerId']) == owner)
        state = vehicle['vehicleState']
        recorded = next(v for v in proof['vehicles'] if ilum.uid(v['UUID']) == ilum.uid(vehicle['UUID']))
        assert recorded['vehicleState'] == state
        wal_vehicle = dict(wal['vehicle'])
        assert ilum.uid(wal_vehicle.pop('assemblyId')) == operation
        assert wal_vehicle == state
        assert state['schemaVersion'] == 1 and state['blueprint'] == bp and state['moduleHealth'] == [100] * 4
        assert state['dimension'] == 'minecraft:overworld' and state['energy'] == 1000 and not state['garaged']
        assert ilum.uid(state['vehicleId']) == ilum.uid(vehicle['UUID'])
        for dx, dz, block in [(0, 2, 'minecraft:crafting_table'), (-2, 0, MOD + 'saber_workshop'),
                              (2, 0, MOD + 'speeder_garage'), (0, -2, MOD + 'galactic_terminal')]:
            x, z = 200 + 16 * index + dx, 200 + dz
            chunk = ilum.read_chunk(world / 'region', x // 16, z // 16)
            assert ilum.block(chunk, x, 100, z) == block
            assert ilum.block(chunk, x, 99, z) == 'minecraft:stone'
        # Pixel content is reviewed separately; file presence alone is not visual approval.
        from PIL import Image
        for name in ['workshop', 'garage']:
            with Image.open(profile / f'screenshots/adventure-{name}.png') as image:
                assert image.width >= 800 and image.height >= 450
                image.verify()
        log = (repo / f'.tools/adventure-preparation-{run}-{client["name"]}-console.log').read_text(encoding='utf-8-sig')
        assert 'ADVENTURE_PREPARATION_CLIENT_PASS' in log and 'ADVENTURE_PREPARATION_CLIENT_FAILED' not in log
    assert len(set(pids)) == 3 and len(hilts) == len(blueprints) == len(operations) == 2
    log = (repo / f'run-adventure-{run}/logs/latest.log').read_text(encoding='utf-8-sig')
    assert 'ADVENTURE_PREPARATION_SERVER_PASS' in log and 'ADVENTURE_PREPARATION_SERVER_FAILED' not in log
    assert 'All dimensions are saved' in log
    return {'scope': 'preparation_only', 'pids': pids, 'nativeCrafts': 64, 'owners': 2,
            'savedVehicles': 2, 'paidHiltsWithoutCrystal': 2, 'inventoryConservation': True,
            'completeP4_05': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run')
    args = parser.parse_args()
    assert 1 <= len(args.run) <= 12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.run), indent=2))
