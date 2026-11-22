"""Check two actual ClientLevels against native held journals, anchors, saved chunks and logout recovery."""
import argparse
import importlib.util
import json
import math
from pathlib import Path
from read_nbt import load

spec = importlib.util.spec_from_file_location('preparation', Path(__file__).with_name('check-adventure-preparation.py'))
prep = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prep)
uid, MOD = prep.ilum.uid, 'legacyoftheforce:'
SOURCE, DEST = (200, 100, 254), (202, 100, 252)


def packed(at):
    x, y, z = at
    return ((x & 0x3ffffff) << 38) | ((z & 0x3ffffff) << 12) | (y & 0xfff)


def progress(actor):
    return actor['neoforge:attachments'][MOD + 'player_progress']


def verify(repo, run, source):
    world = repo / f'run-adventure-{run}/world'
    proof = load(world / 'validation/adventure-block-server.nbt')
    initial = load(repo / f'run-adventure-{source}/world/validation/adventure-preparation-server.nbt')
    result = json.loads((repo / f'.tools/adventure-block-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert proof['schemaVersion'] == 1 and result['observed'] and result['serverExitCode'] == 0
    assert len(proof['before']) == len(proof['after']) == len(result['clients']) == 2
    owners = [uid(actor['UUID']) for actor in proof['before']]
    assert len(set(owners)) == 2
    for role, actor in enumerate(proof['before']):
        original = next(item for item in initial['players'] if uid(item['UUID']) == owners[role])
        assert actor['Inventory'] == original['Inventory']
        assert progress(actor)['level'] == 15 and progress(actor)['totalXpEarned'] == 1232
        assert progress(actor)['alignmentTenths'] == 0 and progress(actor)['rewardRevision'] == 1
        assert progress(actor)['forceEnergy'] == 100 and not actor['Invulnerable']
    frames = {frame['name']: frame for frame in proof['frames']}
    expected_frames = ['INVALID_chest', 'INVALID_bedrock', 'INVALID_stone', 'INVALID_claim', 'INVALID_far',
                       'held', 'locked_peer', 'moved', 'deposited', 'before_revoke', 'revoked', 'logout_held', 'logged_out']
    assert list(frames) == expected_frames and len(frames) == len(proof['frames'])
    ticks = [item['tick'] for item in proof['frames']]
    assert ticks == sorted(ticks) and ticks[-1] - ticks[0] < 6000
    operations = []
    for name, frame in frames.items():
        assert frame['claim'] == 'minecraft:dirt'
        if name.startswith('INVALID_'):
            assert frame['source'] == 'minecraft:dirt' and frame['destination'] == 'minecraft:air'
            assert frame['journal'] == frame['displays'] == []
            assert all(progress(actor)['forceEnergy'] == 100 for actor in frame['players'])
        elif name in ('held', 'locked_peer', 'moved', 'before_revoke', 'logout_held'):
            assert len(frame['journal']) == len(frame['displays']) == 1
            record, display = frame['journal'][0], frame['displays'][0]
            operation = uid(record['operationId'])
            if operation not in operations:
                operations.append(operation)
            assert record['schemaVersion'] == 1 and record['phase'] == 'HELD'
            payload = record['payload']
            assert uid(payload['actor']) == owners[0] and payload['dimension'] == 'minecraft:overworld'
            assert payload['schemaVersion'] == 1 and payload['original'] == {'Name': 'minecraft:dirt'} and payload['mass'] == 1
            assert uid(display['UUID']) == operation and display['block'] == 'minecraft:dirt'
            assert display['block_state'] == {'Name': 'minecraft:dirt'}
            first = name in ('held', 'locked_peer', 'moved')
            assert payload['source'] == packed(SOURCE if first else DEST)
            anchor = frame['sourceEntity' if first else 'destinationEntity']
            assert uid(anchor['operationId']) == operation and anchor['schemaVersion'] == 1
            assert tuple(anchor[k] for k in ('x', 'y', 'z')) == (SOURCE if first else DEST)
            assert frame['source'] == (MOD + 'transaction_anchor' if first else 'minecraft:air')
            assert frame['destination'] == ('minecraft:air' if first else MOD + 'transaction_anchor')
            peer = next(actor for actor in frame['players'] if uid(actor['UUID']) == owners[1])
            assert progress(peer)['forceEnergy'] == 100
        else:
            assert frame['source'] == 'minecraft:air' and frame['destination'] == 'minecraft:dirt'
            assert frame['journal'] == frame['displays'] == []
    assert len(operations) == 3
    assert frames['held']['journal'][0]['operationId'] == frames['moved']['journal'][0]['operationId']
    held, moved = frames['held']['displays'][0]['Pos'], frames['moved']['displays'][0]['Pos']
    assert math.dist(held, moved) > 1
    assert math.dist([v + .5 for v in moved], [v + .5 for v in DEST]) < .15
    assert len(frames['logged_out']['players']) == 1 and uid(frames['logged_out']['players'][0]['UUID']) == owners[1]
    assert not list((world / 'data/legacyoftheforce/block_moves').glob('*.nbt'))
    for at, expected in [(SOURCE, 'minecraft:air'), (DEST, 'minecraft:dirt'), ((204, 100, 254), 'minecraft:dirt'),
                         ((196, 100, 250), 'minecraft:chest'), ((198, 100, 253), 'minecraft:bedrock'),
                         ((198, 100, 251), 'minecraft:stone'), ((205, 100, 258), 'minecraft:dirt')]:
        native = prep.ilum.read_chunk(world / 'region', at[0] // 16, at[2] // 16)
        assert prep.ilum.block(native, *at) == expected
    entities = prep.entities(world)
    assert not any(item['id'] == 'minecraft:block_display' for item in entities)
    assert not any(item['id'] == 'minecraft:item' and item['Item']['id'] == 'minecraft:dirt' for item in entities)
    pid_set = {proof['pid']}
    client_frames = []
    for role, item in enumerate(result['clients']):
        folder = repo / f'run-adventure-client-{["one", "two"][role]}-{run}'
        assert item['name'] == ['AdventureOne', 'AdventureTwo'][role] and item['exitCode'] == 0 and not item['forced']
        assert Path(item['profile']).resolve() == folder.resolve()
        client = load(folder / 'validation/adventure-block-client.nbt')
        assert client['schemaVersion'] == 1 and client['pid'] == item['pid'] and client['pid'] not in pid_set
        pid_set.add(client['pid'])
        assert uid(client['owner']) == owners[role] and client['role'] == role
        assert client['ending'] == ['normal_logout_while_holding', 'peer_observed_restoration'][role]
        expected_actions = (['INVALID_chest', 'INVALID_bedrock', 'INVALID_stone', 'INVALID_claim', 'INVALID_far',
                             'GRAB', 'MOVE', 'DROP', 'REGRAB', 'LOGOUT_GRAB'] if role == 0 else ['LOCK'])
        assert [step['phase'] for step in client['actions']] == expected_actions
        reasons = {'INVALID_chest': 'invalid_target', 'INVALID_bedrock': 'invalid_target', 'INVALID_stone': 'level_required',
                   'INVALID_claim': 'protected_area', 'INVALID_far': 'no_target', 'LOCK': 'target_locked',
                   'GRAB': 'grabbed', 'MOVE': 'look', 'DROP': 'deposited', 'REGRAB': 'grabbed', 'LOGOUT_GRAB': 'grabbed'}
        assert all(step['result'] == reasons[step['phase']] for step in client['actions'])
        seen = {step['phase']: step for step in client['frames']}
        assert list(seen) == ['held', 'moved', 'deposited', 'revoked', 'logout_held'] + (['logged_out'] if role else [])
        from PIL import Image
        for phase, observed in seen.items():
            native = frames[phase]
            assert observed['source'] == native['source'] and observed['destination'] == native['destination']
            assert observed['displayCount'] == len(native['displays'])
            if observed['displayCount']:
                display = native['displays'][0]
                assert uid(observed['operation']) == uid(display['UUID']) and observed['block'] == 'minecraft:dirt'
                assert math.dist(observed['Pos'], display['Pos']) <= .15
            with Image.open(folder / f'screenshots/force-block-{phase}.png') as capture:
                assert capture.width >= 800 and capture.height >= 450
                capture.verify()
        client_frames.append(seen)
        after = proof['after'][role]
        assert uid(after['UUID']) == owners[role] and after['Inventory'] == proof['before'][role]['Inventory'] == client['Inventory']
        for field in ('level', 'totalXpEarned', 'alignmentTenths', 'rewardRevision', 'rewardEvents'):
            assert progress(after)[field] == progress(proof['before'][role])[field]
        assert 0 < progress(after)['forceEnergy'] < 94 if role == 0 else progress(after)['forceEnergy'] == 100
        saved = load(world / f'playerdata/{owners[role]}.dat')
        assert saved['Inventory'] == after['Inventory'] and progress(saved) == progress(after)
        log = (folder / 'logs/latest.log').read_text(encoding='utf-8')
        assert 'ADVENTURE_BLOCK_CLIENT_PASS' in log and 'ADVENTURE_BLOCK_CLIENT_FAILED' not in log
    for phase in ('held', 'moved', 'logout_held'):
        assert client_frames[0][phase]['operation'] == client_frames[1][phase]['operation']
        assert math.dist(client_frames[0][phase]['Pos'], client_frames[1][phase]['Pos']) <= .15
    log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'ADVENTURE_BLOCK_SERVER_PASS' in log and 'ADVENTURE_BLOCK_SERVER_FAILED' not in log and 'All dimensions are saved' in log
    return {'run': run, 'scope': 'native_two_client_single_block_transfer_protection_logout', 'matchingViews': 2,
            'heldOperationIds': operations, 'invalidRequestsUnpaid': 5, 'nativeSavedBlockConserved': True,
            'peerLockUnpaid': True, 'permissionRevocationRestored': True, 'logoutRestored': True,
            'currentProcessCrashCertified': False, 'thirdPartyClaimsCertified': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('run')
    parser.add_argument('--source', default='ap4')
    args = parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parents[1], args.run, args.source), indent=2))
