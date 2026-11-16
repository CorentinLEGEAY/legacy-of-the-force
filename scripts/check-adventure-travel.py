"""Verify native restart, paid travel and return using the actual crafted preparation state."""
import argparse
from collections import Counter
import importlib.util
import json
from pathlib import Path
from read_nbt import load

spec = importlib.util.spec_from_file_location('prep', Path(__file__).with_name('check-adventure-preparation.py'))
prep = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prep)
MOD = prep.MOD


def components(inventory):
    return Counter(json.dumps({k: v for k, v in stack.items() if k != 'Slot'}, sort_keys=True)
                   for stack in inventory)


def verify(repo, run, preparation, garage=False):
    prep.verify(repo, preparation)
    world = repo / f'run-adventure-{run}/world'
    source = load(repo / f'run-adventure-{preparation}/world/validation/adventure-preparation-server.nbt')
    assert load(world / 'validation/adventure-preparation-server.nbt') == source
    result = json.loads((repo / f'.tools/adventure-travel-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert result['observed'] and result['serverExitCode'] == 0 and len(result['clients']) == 2
    proof = load(world / 'validation/adventure-travel-server.nbt')
    assert proof['schemaVersion'] == 1 and proof['scope'] == 'preparation_then_travel_only'
    assert proof['preparationPid'] == source['pid'] and proof['pid'] != source['pid']
    assert len(proof['actors']) == 2
    assert load(world / 'level.dat')['Data']['Difficulty'] == 2
    pids = [proof['pid']]
    terminals = set()
    for index, actor in enumerate(proof['actors']):
        owner = prep.ilum.uid(actor['owner'])
        previous = source['players'][index]
        assert prep.ilum.uid(previous['UUID']) == owner
        client = result['clients'][index]
        assert client['name'] == ['AdventureOne', 'AdventureTwo'][index] and client['exitCode'] == 0 and not client['forced']
        profile = repo / f'run-adventure-client-{["one", "two"][index]}-{run}'
        assert Path(client['profile']).resolve() == profile.resolve()
        cp = load(profile / 'validation/adventure-travel-client.nbt')
        assert cp['schemaVersion'] == 1 and cp['scope'] == proof['scope']
        assert prep.ilum.uid(cp['owner']) == owner and cp['pid'] == client['pid']
        if garage:
            from PIL import Image
            assert cp['garageCaptureVersion'] == 2
            for name in ['resumed', 'seat']:
                with Image.open(profile / f'screenshots/adventure-garage-{name}.png') as image:
                    assert image.width >= 640 and image.height >= 480
                    pixels = Counter(image.convert('RGB').get_flattened_data())
                    assert pixels[(20, 35, 50)] > image.width * image.height // 4, 'Garage panel absent from actual framebuffer'
        pids.append(cp['pid'])
        labels = ['resumed', 'decoded', 'arrival', 'portable', 'returned']
        assert [s['stage'] for s in cp['checkpoints']] == labels
        assert previous['Inventory'] == actor['before']['Inventory']
        ledger = prep.counts(previous['Inventory'])
        for ordinal, (name, client_stage) in enumerate(zip(['before', *labels[1:]], cp['checkpoints'])):
            native = actor[name]
            if name == 'decoded':
                prep.spend(ledger, MOD + 'ilum_coordinates')
            elif name == 'arrival':
                prep.spend(ledger, MOD + 'speeder_energy_cell', 2)
            elif name == 'portable':
                prep.spend(ledger, MOD + 'galactic_terminal')
            assert prep.counts(native['Inventory']) == prep.counts(client_stage['Inventory']) == ledger
            assert components(native['Inventory']) == components(client_stage['Inventory'])
            dimension = 'legacyoftheforce:ilum' if name in ('arrival', 'portable') else 'minecraft:overworld'
            assert native['Dimension'] == client_stage['dimension'] == dimension
            assert prep.ilum.uid(native['UUID']) == owner and native['playerGameType'] == 0
            assert not native['abilities']['instabuild'] and 'RootVehicle' not in native
            progress = native['neoforge:attachments'][MOD + 'player_progress']
            assert progress['totalXpEarned'] == progress['alignmentTenths'] == progress['xpInLevel'] == 0 and progress['level'] == 1
            atlas = native['neoforge:attachments'][MOD + 'travel_state']
            assert atlas['revision'] == [0, 1, 2, 2, 3][ordinal]
            assert atlas['returnCredit'] == (1 if name in ('arrival', 'portable') else 0)
            assert atlas['knownPlanets'] == ([] if name == 'before' else [MOD + 'ilum']) and 'pending' not in atlas
        # Item identities/components outside the consumed stacks must survive every journey stage.
        stable = [s for s in previous['Inventory'] if s['id'] not in {MOD + 'ilum_coordinates', MOD + 'speeder_energy_cell', MOD + 'galactic_terminal'}]
        returned = actor['returned']
        assert components(stable) == components([s for s in returned['Inventory'] if s['id'] != MOD + 'speeder_energy_cell'])
        assert returned['Pos'] == actor['before']['Pos'] == previous['Pos']
        saved = load(world / f'playerdata/{owner}.dat')
        assert saved['Inventory'] == returned['Inventory'] and saved['Pos'] == returned['Pos']
        assert saved['neoforge:attachments'][MOD + 'travel_state'] == returned['neoforge:attachments'][MOD + 'travel_state']
        assert actor['terminal'] == cp['terminal']
        position = tuple(actor['terminal'])
        terminals.add(position)
        chunk = prep.ilum.chunk(world, position[0] // 16, position[2] // 16)
        assert prep.ilum.block(chunk, *position) == MOD + 'galactic_terminal'
        journal = load(world / f'data/legacyoftheforce/travel/{owner}.nbt')
        assert journal['phase'] == 'DONE' and journal['kind'] == 'RETURN' and journal['cost'] == 0
        log = (profile / 'logs/latest.log').read_text(encoding='utf-8-sig')
        assert 'ADVENTURE_TRAVEL_CLIENT_PASS' in log and 'ADVENTURE_TRAVEL_CLIENT_FAILED' not in log
    assert len(set(pids)) == 3 and len(terminals) == 2
    assert proof['actors'][0]['endpoint'] != proof['actors'][1]['endpoint']
    native_vehicles = [e for e in prep.entities(world) if e['id'] == MOD + 'speeder']
    assert len(native_vehicles) == 2
    assert {prep.ilum.uid(v['UUID']) for v in native_vehicles} == {prep.ilum.uid(v['UUID']) for v in source['vehicles']}
    for vehicle in native_vehicles:
        old = next(v for v in source['vehicles'] if v['UUID'] == vehicle['UUID'])
        assert old['vehicleState'] == vehicle['vehicleState']
    log = (repo / f'run-adventure-{run}/logs/latest.log').read_text(encoding='utf-8-sig')
    assert 'ADVENTURE_TRAVEL_SERVER_PASS' in log and 'ADVENTURE_TRAVEL_SERVER_FAILED' not in log and 'All dimensions are saved' in log
    return {'scope': proof['scope'], 'pids': pids, 'ownersResumed': 2, 'coordinateCardsSpent': 2,
            'fuelCellsSpent': 4, 'portableTerminalsPlaced': 2, 'sameParkedVehicles': 2,
            'exactHomeRestored': True, 'completeP4_05': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run')
    parser.add_argument('--preparation', default='ap4')
    parser.add_argument('--garage', action='store_true', help='Require final rendered garage/layer captures; earlier data-only runs cannot pass this check.')
    args = parser.parse_args()
    for name in [args.run, args.preparation]:
        assert 1 <= len(name) <= 12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in name)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.run, args.preparation, args.garage), indent=2))
