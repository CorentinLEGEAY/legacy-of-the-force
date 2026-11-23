"""Reconcile native part recipes, two rendered workshops, assembly payment and lifecycle saves."""
import argparse
from collections import Counter
import importlib.util
import json
from pathlib import Path
from PIL import Image
from read_nbt import load

spec = importlib.util.spec_from_file_location('prep', Path(__file__).with_name('check-adventure-preparation.py'))
prep = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prep)
uid, MOD = prep.ilum.uid, prep.MOD


def progress(actor):
    return actor['neoforge:attachments'][MOD + 'player_progress']


def hilts(inventory):
    result = {}
    for stack in inventory:
        if stack['id'] != MOD + 'lightsaber':
            continue
        assert stack['count'] == 1
        assembly = stack['components'][MOD + 'saber_assembly']
        assert assembly['schemaVersion'] == 1 and assembly['type'] == 'single' and 'crystal' not in assembly
        key = uid(assembly['saberId'])
        assert key not in result
        result[key] = assembly
    return result


def panel(folder, phase):
    with Image.open(folder / f'screenshots/saber-workshop-{phase}.png') as image:
        pixels = Counter(image.convert('RGB').get_flattened_data())
        assert pixels[(20, 35, 50)] > 10000, f'Rendered workshop background missing: {folder}/{phase}'


def verify(repo, run, source):
    world = repo / f'run-adventure-{run}/world'
    proof = load(world / 'validation/adventure-saberworkshop-server.nbt')
    initial = load(repo / f'run-adventure-{source}/world/validation/adventure-preparation-server.nbt')
    result = json.loads((repo / f'.tools/adventure-saberworkshop-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert result['observed'] and result['serverExitCode'] == 0 and not result['killed']
    assert proof['schemaVersion'] == 1 and len(proof['before']) == len(proof['after']) == len(result['clients']) == 2
    assert len(proof['crafts']) == 14
    frames = {frame['phase']: frame for frame in proof['frames']}
    assert len(frames) == len(proof['frames']) == 6
    owners = [uid(actor['UUID']) for actor in proof['before']]
    ids, pids = set(), {proof['pid']}
    for role, item in enumerate(result['clients']):
        folder = repo / f'run-adventure-client-{["one", "two"][role]}-{run}'
        name, owner = ['AdventureOne', 'AdventureTwo'][role], owners[role]
        assert item['name'] == name and item['exitCode'] == 0 and not item['forced']
        assert Path(item['profile']).resolve() == folder.resolve()
        client = load(folder / 'validation/adventure-saberworkshop-client.nbt')
        assert client['schemaVersion'] == 1 and client['role'] == role and uid(client['owner']) == owner
        assert client['pid'] == item['pid'] and client['pid'] not in pids
        pids.add(client['pid'])
        before, after = proof['before'][role], proof['after'][role]
        original = next(actor for actor in initial['players'] if uid(actor['UUID']) == owner)
        assert before['Inventory'] == original['Inventory']
        native_crafts = [craft for craft in proof['crafts'] if uid(craft['owner']) == owner]
        part_names = ['emitter_claw' if role else 'emitter_basic'] + [part + '_basic' for part in prep.PARTS[1:]]
        recipes = ['minecraft:stone_button'] + [MOD + part for part in part_names]
        assert [craft['recipe'] for craft in native_crafts] == [craft['recipe'] for craft in client['crafts']] == recipes
        ledger = prep.counts(before['Inventory'])
        for native, local in zip(native_crafts, client['crafts']):
            assert prep.counts(local['before']['Inventory']) == ledger and local['grid'] == 3
            assert native['result'] == local['result'] and 1 <= len(native['inputs']) <= 9
            if native['recipe'] == 'minecraft:stone_button':
                expected = Counter({'minecraft:stone': 1})
            else:
                definition = json.loads((repo / f'src/main/resources/data/legacyoftheforce/recipe/{native["recipe"].split(":")[1]}.json').read_text(encoding='utf-8'))
                expected = Counter(definition['key'][symbol]['item'] for row in definition['pattern'] for symbol in row if symbol != ' ')
            assert prep.counts(native['inputs']) == expected
            for material, count in expected.items():
                prep.spend(ledger, material, count)
            ledger[native['result']['id']] += native['result']['count']
            assert prep.counts(local['after']['Inventory']) == ledger
        local_frames = {frame['phase']: frame for frame in client['frames']}
        for phase in ('parts', 'before', 'selected'):
            assert prep.counts(local_frames[phase]['Inventory']) == ledger
        native_parts = next(actor for actor in frames['parts_' + name]['players'] if uid(actor['UUID']) == owner)
        assert prep.counts(native_parts['Inventory']) == ledger
        assert local_frames['selected']['emitter'] == role and all(value == 1 for value in local_frames['selected']['owned'].values())
        for part in part_names:
            prep.spend(ledger, MOD + part)
        ledger[MOD + 'lightsaber'] += 1
        for phase in ('assembled-replay', 'missing'):
            assert prep.counts(local_frames[phase]['Inventory']) == ledger
        assert local_frames['assembled-replay']['status'] == 'COOLDOWN' and local_frames['missing']['status'] == 'MISSING'
        assert prep.counts(after['Inventory']) == ledger
        old, new = hilts(before['Inventory']), hilts(after['Inventory'])
        assert len(old) == 1 and len(new) == 2 and old.items() <= new.items()
        added = next(iter(new.keys() - old.keys()))
        assert new[added]['parts'] == dict(zip(prep.PARTS, [MOD + part for part in part_names]))
        assert not ids.intersection(new)
        ids.update(new)
        native_assembled = next(actor for actor in frames['assembled_' + name]['players'] if uid(actor['UUID']) == owner)
        assert hilts(native_assembled['Inventory']) == new
        assert sorted(after['Inventory'], key=lambda stack: stack['Slot']) == sorted(client['Inventory'], key=lambda stack: stack['Slot'])
        saved = load(world / f'playerdata/{owner}.dat')
        assert saved['Inventory'] == after['Inventory'] and progress(saved) == progress(after) == progress(before)
        assert client['realRespawn'] == (1 if role == 0 else 0)
        assert client['ending'] == ['logout_after_respawn', 'peer_observed_lifecycle'][role]
        if role == 0:
            assert hilts(local_frames['death-screen']['Inventory']) == hilts(local_frames['respawned']['Inventory']) == new
        for phase in ('before', 'selected', 'assembled-replay', 'missing'):
            panel(folder, phase)
        log = (folder / 'logs/latest.log').read_text(encoding='utf-8')
        assert 'ADVENTURE_SABERWORKSHOP_CLIENT_PASS' in log and 'ADVENTURE_SABERWORKSHOP_CLIENT_FAILED' not in log
    dead = next(actor for actor in frames['dead']['players'] if uid(actor['UUID']) == owners[0])
    alive = next(actor for actor in frames['respawned']['players'] if uid(actor['UUID']) == owners[0])
    assert dead['Health'] == 0 and alive['Health'] > 0 and dead['Inventory'] == alive['Inventory']
    assert not any(entity['id'] == 'minecraft:item' and entity['Item']['id'] == MOD + 'lightsaber' for entity in prep.entities(world))
    log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'ADVENTURE_SABERWORKSHOP_SERVER_PASS' in log and 'SERVER_FAILED' not in log and 'All dimensions are saved' in log
    return {'run': run, 'nativeProcesses': sorted(pids), 'nativeRecipes': 14, 'uniqueHiltIds': sorted(ids),
            'renderedWorkshops': 2, 'paidAssemblies': 2, 'unpaidReplays': 2, 'unpaidMissingRequests': 2,
            'nativeDeathAndLogoutCertified': True, 'processCrashCertified': False}


def verify_recovery(repo, run, source, preparation):
    previous = verify(repo, source, preparation)
    world = repo / f'run-adventure-{run}/world'
    proof = load(world / 'validation/adventure-saberrecover-server.nbt')
    prior = load(repo / f'run-adventure-{source}/world/validation/adventure-saberworkshop-server.nbt')
    result = json.loads((repo / f'.tools/adventure-saberrecover-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert result['observed'] and result['serverExitCode'] == 0 and not result['killed']
    assert proof['schemaVersion'] == 1 and proof['pid'] not in previous['nativeProcesses'] and proof['crafts'] == []
    pids = {proof['pid'], *previous['nativeProcesses']}
    assert len(proof['before']) == len(proof['after']) == len(result['clients']) == 2
    for role, item in enumerate(result['clients']):
        owner = uid(prior['after'][role]['UUID'])
        folder = repo / f'run-adventure-client-{["one", "two"][role]}-{run}'
        assert item['exitCode'] == 0 and not item['forced'] and Path(item['profile']).resolve() == folder.resolve()
        client = load(folder / 'validation/adventure-saberrecover-client.nbt')
        assert client['schemaVersion'] == 1 and client['role'] == role and uid(client['owner']) == owner
        assert client['pid'] == item['pid'] and client['pid'] not in pids
        pids.add(client['pid'])
        assert client['crafts'] == [] and client['ending'] == 'native_reloaded_workshop' and client['realRespawn'] == 0
        original = prior['after'][role]['Inventory']
        saved = load(world / f'playerdata/{owner}.dat')
        assert proof['before'][role]['Inventory'] == proof['after'][role]['Inventory'] == client['Inventory'] == saved['Inventory'] == original
        assert progress(proof['before'][role]) == progress(proof['after'][role]) == progress(prior['after'][role]) == progress(saved)
        assert [frame['phase'] for frame in client['frames']] == ['reloaded', 'reloaded-refused']
        assert client['frames'][1]['status'] == 'MISSING'
        assert all(frame['Inventory'] == original for frame in client['frames'])
        for phase in ('reloaded', 'reloaded-refused'):
            panel(folder, phase)
        assert 'ADVENTURE_SABERRECOVER_CLIENT_PASS' in (folder / 'logs/latest.log').read_text(encoding='utf-8')
    log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'ADVENTURE_SABERRECOVER_SERVER_PASS' in log and 'SERVER_FAILED' not in log and 'All dimensions are saved' in log
    previous.update(recovery=run, nativeProcesses=sorted(pids), nativeReloadCertified=True)
    return previous


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('run')
    parser.add_argument('--source', default='ap4')
    parser.add_argument('--recovery')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    print(json.dumps(verify_recovery(repo, args.recovery, args.run, args.source) if args.recovery else verify(repo, args.run, args.source), indent=2))
