"""Check conservation after a native Q drop and real process kill.

Default: assert the product invariant (one persisted copy). --expect-duplicate only
certifies reproduction of the known defect; its JSON explicitly reports failure.
"""
import argparse
import importlib.util
import json
from pathlib import Path
from read_nbt import load

spec = importlib.util.spec_from_file_location('prep', Path(__file__).with_name('check-adventure-preparation.py'))
prep = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prep)
MOD, uid = prep.MOD, prep.ilum.uid


def matching(inventory, identity):
    return [stack for stack in inventory if stack['id'] == MOD + 'lightsaber'
            and stack.get('components', {}).get(MOD + 'saber_assembly', {}).get('saberId') == identity]


def verify(repo, run, expect_duplicate):
    world = repo / f'run-adventure-{run}/world'
    proof = load(world / 'validation/saber-crash-drop.nbt')
    result = json.loads((repo / f'.tools/adventure-sabercrash-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert proof['schemaVersion'] == 1 and Path(proof['world']).resolve() == world.resolve()
    assert result['observed'] and result['killed'] and result['killedPid'] == proof['pid'] and result['serverExitCode'] != 0
    assert result['killUtc'] and len(result['clients']) == len(proof['before']) == 2
    owner, identity = uid(proof['savedActor']['UUID']), proof['saberId']
    saved = load(world / f'playerdata/{owner}.dat')
    assert saved['Inventory'] == proof['savedActor']['Inventory']
    assert len(matching(proof['before'][0]['Inventory'], identity)) == 1
    assert matching(proof['liveActor']['Inventory'], identity) == []
    assert uid(proof['liveActor']['UUID']) == owner
    drops = [entity for entity in prep.entities(world) if entity['id'] == 'minecraft:item' and matching([entity['Item']], identity)]
    assert len(drops) == 1 and drops[0]['UUID'] == proof['drop']['UUID']
    assert drops[0]['Item'] == proof['drop']['Item'] and drops[0]['Item']['count'] == 1
    count = len(matching(saved['Inventory'], identity)) + len(drops)
    pids = {proof['pid']}
    for role, item in enumerate(result['clients']):
        folder = repo / f'run-adventure-client-{["one", "two"][role]}-{run}'
        assert item['exitCode'] == 0 and not item['forced'] and Path(item['profile']).resolve() == folder.resolve()
        client = load(folder / 'validation/adventure-sabercrash-client.nbt')
        assert client['schemaVersion'] == 1 and client['pid'] == item['pid'] and client['pid'] not in pids
        pids.add(client['pid'])
        assert client['role'] == role and client['disconnectObserved'] and client['disconnectReason']
        assert uid(client['owner']) == uid(proof['before'][role]['UUID'])
        assert client['Inventory'] == proof['before'][role]['Inventory']
        assert 'ADVENTURE_SABERCRASH_CLIENT_PASS' in (folder / 'logs/latest.log').read_text(encoding='utf-8')
    log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'SABER_CRASH_READY' in log and 'Stopping server' not in log and 'SERVER_FAILED' not in log
    assert count == (2 if expect_duplicate else 1), f'PRODUCT INVARIANT FAILED: {count} persisted copies of saber {uid(identity)}'
    return {'run': run, 'nativeProcesses': sorted(pids), 'saberId': uid(identity), 'persistedCopies': count,
            'productInvariantPassed': count == 1, 'knownDefectReproduced': expect_duplicate,
            'freshServerReloadCertified': False, 'saveOrderExplicitlyControlled': True}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('run')
    parser.add_argument('--expect-duplicate', action='store_true')
    args = parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parents[1], args.run, args.expect_duplicate), indent=2))
