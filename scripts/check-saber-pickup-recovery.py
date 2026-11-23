"""Verify new-server native pickup after a real saber-drop crash, without fixture inventories."""
import argparse
from collections import Counter
import importlib.util
import json
from pathlib import Path
from read_nbt import load

spec = importlib.util.spec_from_file_location('drop', Path(__file__).with_name('check-saber-drop-crash.py'))
drop = importlib.util.module_from_spec(spec)
spec.loader.exec_module(drop)
uid, MOD = drop.uid, drop.MOD


def stock(inventory):
    return Counter(json.dumps({key: value for key, value in stack.items() if key != 'Slot'}, sort_keys=True) for stack in inventory)


def hilts(inventory):
    result = {}
    for stack in inventory:
        if stack['id'] == MOD + 'lightsaber':
            assert stack['count'] == 1
            component = stack['components'][MOD + 'saber_assembly']
            identity = uid(component['saberId'])
            assert identity not in result
            result[identity] = component
    return result


def receipt(actor):
    return actor['neoforge:attachments'].get(MOD + 'saber_transfer_receipt', {'schemaVersion': 1, 'revision': 0})


def progress(actor):
    return actor['neoforge:attachments'][MOD + 'player_progress']


def verify(repo, run, source):
    prior_result = drop.verify(repo, source, False)
    world = repo / f'run-adventure-{run}/world'
    source_world = repo / f'run-adventure-{source}/world'
    prior = load(source_world / 'validation/saber-crash-drop.nbt')
    proof = load(world / 'validation/adventure-saberpickup-server.nbt')
    result = json.loads((repo / f'.tools/adventure-saberpickup-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert result['observed'] and not result['killed'] and result['serverExitCode'] == 0
    assert proof['schemaVersion'] == 1 and proof['pid'] not in prior_result['nativeProcesses'] and proof['crashedPid'] == prior['pid']
    assert (source_world / 'validation/saber-crash-drop.nbt').read_bytes() == (world / 'validation/saber-crash-drop.nbt').read_bytes()
    entity, identity = uid(proof['entity']), uid(prior['saberId'])
    assert entity == uid(prior['drop']['UUID'])
    assert len(proof['before']) == len(proof['after']) == len(result['clients']) == 2 and len(proof['pickups']) == 1
    frames = {frame['phase']: frame for frame in proof['frames']}
    assert len(frames) == len(proof['frames']) == 2 and set(frames) == {'before_collect', 'collected'}
    assert frames['before_collect']['present'] and not frames['collected']['present']
    assert frames['before_collect']['drop']['Item'] == prior['drop']['Item'] and uid(frames['before_collect']['drop']['UUID']) == entity
    owners = [uid(actor['UUID']) for actor in proof['before']]
    assert len(set(owners)) == 2 and owners[0] == uid(prior['savedActor']['UUID'])
    pickup = proof['pickups'][0]
    assert uid(pickup['owner']) == owners[0] and uid(pickup['entity']) == entity and pickup['original'] == prior['drop']['Item']
    source_saved = [load(source_world / f'playerdata/{owner}.dat') for owner in owners]
    pids = {proof['pid'], *prior_result['nativeProcesses']}
    all_ids = set()
    for role, item in enumerate(result['clients']):
        before, after = proof['before'][role], proof['after'][role]
        assert before['Inventory'] == source_saved[role]['Inventory'] and progress(before) == progress(after) == progress(source_saved[role])
        expected = stock(before['Inventory'])
        if role == 0:
            expected += stock([prior['drop']['Item']])
            assert receipt(before) == receipt(prior['savedActor']) and receipt(before)['revision'] == 1
            assert receipt(after)['schemaVersion'] == 1 and receipt(after)['revision'] == 2 and receipt(after)['operation'] != receipt(before)['operation']
            assert receipt(pickup['player']) == receipt(after)
        else:
            assert receipt(after) == receipt(before) == receipt(source_saved[role])
        assert stock(after['Inventory']) == expected
        old, new = hilts(before['Inventory']), hilts(after['Inventory'])
        assert len(old) == (1 if role == 0 else 2) and len(new) == 2 and old.items() <= new.items()
        assert (identity in new) == (role == 0) and (identity in old) is False
        assert not all_ids.intersection(new)
        all_ids.update(new)
        saved = load(world / f'playerdata/{owners[role]}.dat')
        assert saved['Inventory'] == after['Inventory'] and receipt(saved) == receipt(after) and progress(saved) == progress(after)
        folder = repo / f'run-adventure-client-{["one", "two"][role]}-{run}'
        assert item['exitCode'] == 0 and not item['forced'] and Path(item['profile']).resolve() == folder.resolve()
        client = load(folder / 'validation/adventure-saberpickup-client.nbt')
        assert client['schemaVersion'] == 1 and client['pid'] == item['pid'] and client['pid'] not in pids
        pids.add(client['pid'])
        assert client['role'] == role and uid(client['owner']) == owners[role] and client['Inventory'] == after['Inventory']
        assert [frame['phase'] for frame in client['frames']] == ['before_collect', 'collected']
        for local in client['frames']:
            phase = local['phase']
            native = frames[phase]['players'][role]
            assert local['Inventory'] == native['Inventory']
            assert local['present'] == (phase == 'before_collect')
            if local['present']:
                assert uid(local['entity']) == entity and local['Item'] == prior['drop']['Item']
            assert (folder / f'screenshots/saber-pickup-{phase}.png').read_bytes()[:8] == b'\x89PNG\r\n\x1a\n'
        log = (folder / 'logs/latest.log').read_text(encoding='utf-8')
        assert 'ADVENTURE_SABERPICKUP_CLIENT_PASS' in log and 'ADVENTURE_SABERPICKUP_CLIENT_FAILED' not in log
    assert len(all_ids) == 4
    assert not any(uid(item['UUID']) == entity for item in drop.prep.entities(world))
    assert not any(item['id'] == 'minecraft:item' and drop.matching([item['Item']], prior['saberId']) for item in drop.prep.entities(world))
    assert not list((world / 'data/legacyoftheforce/saber_transfers').glob('*.nbt'))
    log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'ADVENTURE_SABERPICKUP_SERVER_PASS' in log and 'SERVER_FAILED' not in log and 'All dimensions are saved' in log
    return {'crash': source, 'recovery': run, 'nativeProcesses': sorted(pids), 'uniqueHiltIds': sorted(all_ids),
            'nativePickupCount': 1, 'collectorReceiptRevision': 2, 'sourceEntityDurablyAbsent': True,
            'peerInventoryUnchanged': True, 'fixtureGatedBeforeObservation': True,
            'midTransactionProcessCrashCertified': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('run')
    parser.add_argument('--source', default='sd3')
    args = parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parents[1], args.run, args.source), indent=2))
