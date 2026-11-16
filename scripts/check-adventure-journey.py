"""Independent paid preparation -> guided native mine -> identified crystal -> install -> return check.

Bosses and paid personalization are still required before the complete P4-05 ticket can pass.
"""
import argparse
from collections import Counter
import importlib.util
import json
import math
from pathlib import Path
from read_nbt import load

spec = importlib.util.spec_from_file_location('travel', Path(__file__).with_name('check-adventure-travel.py'))
travel = importlib.util.module_from_spec(spec)
spec.loader.exec_module(travel)
prep, MOD = travel.prep, travel.MOD


def stacks(player):
    return {s['id']: s for s in player['Inventory'] if s['count'] == 1}


def verify(repo, run, preparation):
    prep.verify(repo, preparation)
    world = repo / f'run-adventure-{run}/world'
    source = load(repo / f'run-adventure-{preparation}/world/validation/adventure-preparation-server.nbt')
    assert load(world / 'validation/adventure-preparation-server.nbt') == source
    result = json.loads((repo / f'.tools/adventure-journey-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert result['observed'] and result['serverExitCode'] == 0 and len(result['clients']) == 2
    proof = load(world / 'validation/adventure-journey-server.nbt')
    crystal = load(world / 'validation/adventure-crystal-stage.nbt')
    assert proof['scope'] == 'preparation_then_crystal_journey' and proof['schemaVersion'] == 1
    assert crystal['scope'] == 'guided_native_crystal_route' and crystal['schemaVersion'] == 1
    assert proof['preparationPid'] == source['pid'] and proof['pid'] != source['pid']
    assert len(proof['actors']) == len(crystal['players']) == 2 and len(crystal['crafts']) == 4
    assert load(world / 'level.dat')['Data']['Difficulty'] == 2
    mine = crystal['mine']
    assert mine['Children'][0]['siteKind'] == 0 and mine['Children'][0]['schemaVersion'] == 1
    x, y, z = crystal['source']
    bb = mine['Children'][0]['BB']
    assert [x, y, z] == [bb[0] + 7, bb[1] + 1, bb[2] + 8]
    chunk = prep.ilum.chunk(world, x // 16, z // 16)
    actual_start = chunk['structures']['starts'][MOD + 'ilum_crystal_mine']
    assert actual_start == mine, 'Native generated structure differs from claimed mine'
    for bx, by, bz in [(x, y, z), (x, y, z - 1), (x, y + 1, z - 1)]:
        assert prep.ilum.block(prep.ilum.chunk(world, bx // 16, bz // 16), bx, by, bz) in ('minecraft:air', 'minecraft:cave_air')
    wx, wy, wz = crystal['workshop']
    assert prep.ilum.block(prep.ilum.chunk(world, wx // 16, wz // 16), wx, wy, wz) == MOD + 'saber_workshop'
    drop, pickup = crystal['drop'], crystal['pickup']
    assert drop['entity'] == pickup['entity'] and drop['stack'] == pickup['stack']
    assert drop['stack']['id'] == MOD + 'ilum_crystal' and drop['stack']['count'] == 1
    assert math.dist([drop[k] for k in ['x', 'y', 'z']], [x + .5, y + .5, z + .5]) < 1
    kyber = drop['stack']['components'][MOD + 'kyber']
    assert kyber['schemaVersion'] == 1 and kyber['originPlanet'] == MOD + 'ilum' and kyber['baseKind'] == 'blue'
    assert .55 <= kyber['purity'] <= .90 and .60 <= kyber['stability'] <= .95
    assert kyber['bondLevel'] == kyber['bondProgress'] == 0
    owners = [prep.ilum.uid(a['owner']) for a in proof['actors']]
    assert len(set(owners)) == 2 and prep.ilum.uid(pickup['owner']) == owners[0]
    pids, terminals = [proof['pid']], set()
    for index, actor in enumerate(proof['actors']):
        owner = owners[index]
        client = result['clients'][index]
        assert client['name'] == ['AdventureOne', 'AdventureTwo'][index] and client['exitCode'] == 0 and not client['forced']
        profile = repo / f'run-adventure-client-{["one", "two"][index]}-{run}'
        assert Path(client['profile']).resolve() == profile.resolve()
        cp = load(profile / 'validation/adventure-journey-client.nbt')
        assert cp['scope'] == proof['scope'] and cp['schemaVersion'] == 1 and cp['pid'] == client['pid']
        assert prep.ilum.uid(cp['owner']) == owner
        pids.append(cp['pid'])
        assert [c['stage'] for c in cp['checkpoints']] == ['resumed', 'packed', 'decoded', 'arrival', 'crystal_stage', 'portable', 'returned']
        client_stages = {c['stage']: c for c in cp['checkpoints']}
        assert actor['before']['Inventory'] == source['players'][index]['Inventory']
        ledger = prep.counts(actor['before']['Inventory'])
        recipes = [c for c in crystal['crafts'] if prep.ilum.uid(c['owner']) == owner]
        assert [c['recipe'] for c in recipes] == [c['recipe'] for c in cp['packingCrafts']] == ['minecraft:crafting_table', MOD + 'saber_workshop']
        for ordinal, (server_craft, client_craft) in enumerate(zip(recipes, cp['packingCrafts'])):
            assert prep.counts(client_craft['before']['Inventory']) == ledger
            assert server_craft['result'] == client_craft['result'] and client_craft['grid'] == (2 if ordinal == 0 else 3)
            for material in server_craft['inputs']:
                assert material['count'] == 1
                prep.spend(ledger, material['id'])
            ledger.update(prep.counts([server_craft['result']]))
            assert prep.counts(client_craft['after']['Inventory']) == ledger
        assert prep.counts(client_stages['packed']['Inventory']) == ledger
        for label, vanilla, client_label in [('decoded', actor['decoded'], 'decoded'), ('arrival', actor['arrival'], 'arrival'),
                                              ('crystal', crystal['players'][index], 'crystal_stage'), ('portable', actor['portable'], 'portable'),
                                              ('returned', actor['returned'], 'returned')]:
            if label == 'decoded': prep.spend(ledger, MOD + 'ilum_coordinates')
            if label == 'arrival': prep.spend(ledger, MOD + 'speeder_energy_cell', 2)
            if label == 'crystal' and index == 0: prep.spend(ledger, MOD + 'saber_workshop')
            if label == 'portable': prep.spend(ledger, MOD + 'galactic_terminal')
            assert prep.counts(vanilla['Inventory']) == prep.counts(client_stages[client_label]['Inventory']) == ledger, (owner, label)
            assert travel.components(vanilla['Inventory']) == travel.components(client_stages[client_label]['Inventory']), (owner, label, 'components')
            assert prep.ilum.uid(vanilla['UUID']) == owner and vanilla['playerGameType'] == 0 and not vanilla['abilities']['instabuild']
            dimension = MOD + 'ilum' if label in ('arrival', 'crystal', 'portable') else 'minecraft:overworld'
            assert vanilla['Dimension'] == client_stages[client_label]['dimension'] == dimension
            progress = vanilla['neoforge:attachments'][MOD + 'player_progress']
            assert progress['level'] == 1 and progress['totalXpEarned'] == progress['alignmentTenths'] == 0
        saber = stacks(actor['returned'])[MOD + 'lightsaber']['components'][MOD + 'saber_assembly']
        original = stacks(actor['before'])[MOD + 'lightsaber']['components'][MOD + 'saber_assembly']
        assert saber['saberId'] == original['saberId'] and saber['parts'] == original['parts']
        if index == 0:
            assert saber['crystal'] == kyber and saber['active']
            assert stacks(actor['returned'])['minecraft:iron_pickaxe']['components']['minecraft:damage'] == 3
        else:
            assert 'crystal' not in saber
        native = load(world / f'playerdata/{owner}.dat')
        assert native['Inventory'] == actor['returned']['Inventory'] and native['Pos'] == actor['before']['Pos']
        assert native['neoforge:attachments'][MOD + 'travel_state']['revision'] == 3
        assert actor['terminal'] == cp['terminal']
        tx, ty, tz = actor['terminal']
        terminals.add((tx, ty, tz))
        assert prep.ilum.block(prep.ilum.chunk(world, tx // 16, tz // 16), tx, ty, tz) == MOD + 'galactic_terminal'
        paths = [p for p in crystal['paths'] if prep.ilum.uid(p['owner']) == owner]
        assert {'entrance', 'cavern'} == {p['goal'] for p in paths} and all(1 <= len(p['nodes']) <= 32 for p in paths)
        movement = [p for p in crystal['movement'] if prep.ilum.uid(p['owner']) == owner]
        assert len(movement) >= 10, 'Require actual native server movement observations, not only path proposals'
        for a, b in zip(movement, movement[1:]):
            assert b['tick'] - a['tick'] == 20 and math.dist([a[k] for k in ['x','y','z']], [b[k] for k in ['x','y','z']]) < 16
            assert b['health'] > 0
        assert math.dist([movement[-1][k] for k in ['x','y','z']], [x, y, z]) < 10
        log = (profile / 'logs/latest.log').read_text(encoding='utf-8-sig')
        assert 'ADVENTURE_JOURNEY_CLIENT_PASS' in log and 'ADVENTURE_JOURNEY_CLIENT_FAILED' not in log
    vehicles = [e for e in prep.entities(world) if e['id'] == MOD + 'speeder']
    assert len(vehicles) == len(terminals) == 2 and len(set(pids)) == 3
    assert {prep.ilum.uid(v['UUID']) for v in vehicles} == {prep.ilum.uid(v['UUID']) for v in source['vehicles']}
    for vehicle in vehicles:
        assert vehicle['vehicleState'] == next(v['vehicleState'] for v in source['vehicles'] if v['UUID'] == vehicle['UUID'])
    log = (repo / f'run-adventure-{run}/logs/latest.log').read_text(encoding='utf-8-sig')
    assert 'ADVENTURE_JOURNEY_SERVER_PASS' in log and 'ADVENTURE_JOURNEY_CRYSTAL_PASS' in log and 'ADVENTURE_JOURNEY_SERVER_FAILED' not in log and 'All dimensions are saved' in log
    return {'scope': proof['scope'], 'pids': pids, 'realMine': True, 'twoNativeWalkers': True,
            'nativeCrystalDrops': 1, 'sameCrystalInstalled': True, 'sameParkedVehicles': 2, 'completeP4_05': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run')
    parser.add_argument('--preparation', default='ap4')
    args = parser.parse_args()
    for value in [args.run, args.preparation]:
        assert 1 <= len(value) <= 12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in value)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.run, args.preparation), indent=2))
