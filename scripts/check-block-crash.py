"""Verify a genuine killed HELD server, preserved native before-image and two-client recovery."""
import argparse
import importlib.util
import json
import math
from pathlib import Path
from read_nbt import load

spec = importlib.util.spec_from_file_location('block_network', Path(__file__).with_name('check-block-network.py'))
block = importlib.util.module_from_spec(spec)
spec.loader.exec_module(block)
prep, uid, profile = block.prep, block.uid, block.progress


def verify(repo, crashed, recovered):
    original = repo / f'run-adventure-{crashed}/world'
    world = repo / f'run-adventure-{recovered}/world'
    held = load(original / 'validation/block-crash-held.nbt')
    proof = load(world / 'validation/adventure-block-recovered-server.nbt')
    kill = json.loads((repo / f'.tools/adventure-block-{crashed}-result.json').read_text(encoding='utf-8-sig'))
    restart = json.loads((repo / f'.tools/adventure-blockrecover-{recovered}-result.json').read_text(encoding='utf-8-sig'))
    assert kill['observed'] and kill['killed'] and kill['killedPid'] == held['pid'] and kill['serverExitCode'] != 0 and kill['killUtc']
    assert restart['observed'] and not restart['killed'] and restart['serverExitCode'] == 0
    assert held['schemaVersion'] == proof['schemaVersion'] == 1 and proof['pid'] != held['pid'] and proof['crashedPid'] == held['pid']
    assert Path(held['world']).resolve() == original.resolve()
    assert (original / 'validation/block-crash-held.nbt').read_bytes() == (world / 'validation/block-crash-held.nbt').read_bytes()
    assert [frame['name'] for frame in held['frames']] == ['INVALID_chest', 'INVALID_bedrock', 'INVALID_stone', 'INVALID_claim', 'INVALID_far', 'held', 'crash_durable']
    durable = held['frames'][-1]
    assert durable['source'] == 'legacyoftheforce:transaction_anchor' and durable['destination'] == 'minecraft:air'
    assert len(durable['journal']) == len(durable['displays']) == 1
    operation = durable['journal'][0]
    event = uid(operation['operationId'])
    assert operation['phase'] == 'HELD' and operation['payload']['original'] == {'Name': 'minecraft:dirt'}
    assert operation['payload']['source'] == block.packed(block.SOURCE)
    assert uid(durable['sourceEntity']['operationId']) == event == uid(durable['displays'][0]['UUID'])
    assert len(list((original / 'data/legacyoftheforce/block_moves').glob('*.nbt'))) == 1
    assert load(original / f'data/legacyoftheforce/block_moves/{event}.nbt') == operation
    chunk = prep.ilum.read_chunk(original / 'region', block.SOURCE[0] // 16, block.SOURCE[2] // 16)
    assert prep.ilum.block(chunk, *block.SOURCE) == 'legacyoftheforce:transaction_anchor'
    anchor = next(item for item in chunk['block_entities'] if tuple(item[key] for key in ('x', 'y', 'z')) == block.SOURCE)
    assert uid(anchor['operationId']) == event and anchor['schemaVersion'] == 1
    saved_displays = [item for item in prep.entities(original) if item['id'] == 'minecraft:block_display']
    assert len(saved_displays) == 1 and uid(saved_displays[0]['UUID']) == event
    assert saved_displays[0]['block_state'] == {'Name': 'minecraft:dirt'}
    assert saved_displays[0]['Pos'] == durable['displays'][0]['Pos']
    assert len(proof['frames']) == 1 and proof['frames'][0]['name'] == 'recovered'
    recovered_frame = proof['frames'][0]
    assert recovered_frame['source'] == 'minecraft:dirt' and recovered_frame['destination'] == 'minecraft:air'
    assert recovered_frame['journal'] == recovered_frame['displays'] == []
    assert not list((world / 'data/legacyoftheforce/block_moves').glob('*.nbt'))
    for at, wanted in [(block.SOURCE, 'minecraft:dirt'), (block.DEST, 'minecraft:air'), ((204, 100, 254), 'minecraft:dirt'),
                       ((196, 100, 250), 'minecraft:chest'), ((198, 100, 253), 'minecraft:bedrock'),
                       ((198, 100, 251), 'minecraft:stone'), ((205, 100, 258), 'minecraft:dirt')]:
        native = prep.ilum.read_chunk(world / 'region', at[0] // 16, at[2] // 16)
        assert prep.ilum.block(native, *at) == wanted
    entities = prep.entities(world)
    assert not any(item['id'] == 'minecraft:block_display' for item in entities)
    assert not any(item['id'] == 'minecraft:item' and item['Item']['id'] == 'minecraft:dirt' for item in entities)
    owners = [uid(actor['UUID']) for actor in held['before']]
    assert len(owners) == len(set(owners)) == len(proof['before']) == len(proof['after']) == 2
    assert uid(operation['payload']['actor']) == owners[0]
    pids = {held['pid'], proof['pid']}
    from PIL import Image
    for role, owner in enumerate(owners):
        stored = load(original / f'playerdata/{owner}.dat')
        paid = next(actor for actor in durable['players'] if uid(actor['UUID']) == owner)
        assert stored['Inventory'] == paid['Inventory'] == held['before'][role]['Inventory']
        assert profile(stored) == profile(paid)
        assert profile(stored)['level'] == 15 and profile(stored)['totalXpEarned'] == 1232
        assert profile(stored)['rewardRevision'] == 1 and profile(stored)['alignmentTenths'] == 0
        if role == 0:
            assert 0 < profile(stored)['forceEnergy'] < 94
        else:
            assert profile(stored)['forceEnergy'] == 100
        for image in [proof['before'][role], proof['after'][role], load(world / f'playerdata/{owner}.dat')]:
            assert uid(image['UUID']) == owner and image['Inventory'] == stored['Inventory'] and profile(image) == profile(stored)
        for epoch, run, result, artifact in [('kill', crashed, kill, 'adventure-block-client.nbt'),
                                              ('recover', recovered, restart, 'adventure-block-recovered-client.nbt')]:
            item = result['clients'][role]
            folder = repo / f'run-adventure-client-{["one", "two"][role]}-{run}'
            assert item['name'] == ['AdventureOne', 'AdventureTwo'][role] and item['exitCode'] == 0 and not item['forced']
            assert Path(item['profile']).resolve() == folder.resolve()
            client = load(folder / 'validation' / artifact)
            assert client['schemaVersion'] == 1 and client['pid'] == item['pid'] and client['pid'] not in pids
            pids.add(client['pid'])
            assert uid(client['owner']) == owner and client['Inventory'] == stored['Inventory'] and client['role'] == role
            if epoch == 'kill':
                assert client['ending'] == 'server_process_killed_while_holding' and client['disconnectObserved'] and client['disconnectReason']
                assert len(client['frames']) == 1 and client['frames'][0]['phase'] == 'held'
                view = client['frames'][0]
                assert uid(view['operation']) == event and view['block'] == 'minecraft:dirt' and view['displayCount'] == 1
                assert math.dist(view['Pos'], held['frames'][-2]['displays'][0]['Pos']) <= .15
                prefix = load(folder / 'validation/adventure-block-crash-prefix-client.nbt')
                assert prefix['ending'] == 'awaiting_server_process_kill' and prefix['frames'] == client['frames']
                picture = 'force-block-held.png'
            else:
                assert client['ending'] == 'native_recovery_after_crash' and client['actions'] == []
                assert len(client['frames']) == 1
                view = client['frames'][0]
                assert view['phase'] == 'recovered' and view['displayCount'] == 0
                assert view['source'] == 'minecraft:dirt' and view['destination'] == 'minecraft:air'
                picture = 'force-block-recovered.png'
            with Image.open(folder / 'screenshots' / picture) as capture:
                assert capture.width >= 800 and capture.height >= 450
                capture.verify()
    killed_log = (original.parent / 'logs/latest.log').read_text(encoding='utf-8')
    recovered_log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'BLOCK_CRASH_READY' in killed_log and 'Stopping the server' not in killed_log and 'Stopping server' not in killed_log
    assert 'Recovered and closed 1 block move operations' in recovered_log
    assert 'ADVENTURE_BLOCK_RECOVER_SERVER_PASS' in recovered_log and 'ADVENTURE_BLOCK_SERVER_FAILED' not in recovered_log
    assert 'All dimensions are saved' in recovered_log
    return {'crashed': crashed, 'recovered': recovered, 'actuallyKilledServerPid': held['pid'],
            'restartedServerPid': proof['pid'], 'persistedAnchorAndDisplayBeforeKill': True,
            'matchingNativeViewsPerEpoch': 2, 'sourceRestoredNoDuplicate': True,
            'paidSavedProfilesPreserved': True, 'operation': event}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('crashed')
    parser.add_argument('recovered')
    args = parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parents[1], args.crashed, args.recovered), indent=2))
