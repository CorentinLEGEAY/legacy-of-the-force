"""Verify active native entity persistence at real OS kill, then two-client cooldown/reuse.

The fixture deliberately pauses its server thread after flushing an active impulse.
No test state is re-injected during recovery; original crashed regions remain available.
"""
import argparse
import importlib.util
import json
import math
from pathlib import Path
from read_nbt import load

spec = importlib.util.spec_from_file_location('network', Path(__file__).with_name('check-mob-impulse-network.py'))
network = importlib.util.module_from_spec(spec)
spec.loader.exec_module(network)
uid, MOD, progress, state = network.uid, network.MOD, network.progress, network.state


def verify(repo, crash, recovery):
    world = repo / f'run-adventure-{crash}/world'
    resumed = repo / f'run-adventure-{recovery}/world'
    killed = json.loads((repo / f'.tools/adventure-impulsecrash-{crash}-result.json').read_text(encoding='utf-8-sig'))
    restarted = json.loads((repo / f'.tools/adventure-impulserecover-{recovery}-result.json').read_text(encoding='utf-8-sig'))
    checkpoint = load(world / 'validation/impulse-crash-active.nbt')
    proof = load(resumed / 'validation/adventure-impulserecover-server.nbt')
    assert killed['observed'] and killed['killed'] and killed['killedPid'] == checkpoint['pid'] and killed['killUtc']
    assert killed['serverExitCode'] != 0 and restarted['observed'] and not restarted['killed'] and restarted['serverExitCode'] == 0
    assert proof['schemaVersion'] == checkpoint['schemaVersion'] == 1
    assert Path(checkpoint['world']).resolve() == world.resolve() and Path(proof['world']).resolve() == resumed.resolve()
    assert proof['pid'] != checkpoint['pid'] and proof['crashedPid'] == checkpoint['pid']
    assert (world / 'validation/impulse-crash-active.nbt').read_bytes() == (resumed / 'validation/impulse-crash-active.nbt').read_bytes()
    assert len(checkpoint['events']) == len(proof['events']) == 1
    event, reused = checkpoint['events'][0], proof['events'][0]
    durable = checkpoint['durable']
    owners = [uid(actor['UUID']) for actor in checkpoint['before']]
    target = uid(durable['target']['UUID'])
    assert len(set(owners)) == 2 and uid(event['actor']) == uid(reused['actor']) == owners[0]
    assert uid(event['target']) == uid(reused['target']) == target
    assert event['action'] == reused['action'] == 'PUSH'
    assert durable['tick'] == event['tick'] + 2
    assert state(durable['target']) == {'schemaVersion': 1, 'active': 1, 'cooldownUntil': event['tick'] + 1200}
    assert durable['target']['Motion'][2] > 0 and durable['target']['Pos'][2] > event['targetImage']['Pos'][2] + 1
    assert state(event['targetImage']) == state(durable['target'])
    native = [entity for entity in network.prep.entities(world) if uid(entity['UUID']) == target]
    assert len(native) == 1 and native[0]['id'] == 'minecraft:cow'
    for field in ('Pos', 'Motion', 'Health'):
        assert native[0][field] == durable['target'][field]
    assert state(native[0]) == state(durable['target'])
    frames = {frame['phase']: frame for frame in proof['frames']}
    assert len(frames) == len(proof['frames']) == 5
    assert set(frames) == {'recovered', 'blocked_primary', 'blocked_peer', 'expired', 'reused'}
    until = state(durable['target'])['cooldownUntil']
    for phase, frame in frames.items():
        assert uid(frame['target']['UUID']) == target and frame['target']['Health'] == 10
        assert state(frame['target'])['active'] == 0
        assert frame['target']['Motion'][0] == frame['target']['Motion'][2] == 0
        if phase != 'reused':
            assert frame['target']['Pos'] == durable['target']['Pos']
            assert state(frame['target'])['cooldownUntil'] == until
        if phase in ('recovered', 'blocked_primary', 'blocked_peer'):
            assert frame['tick'] < until
    assert frames['expired']['tick'] >= until and reused['tick'] >= frames['expired']['tick']
    assert frames['reused']['target']['Pos'][2] > durable['target']['Pos'][2] + 1
    assert state(frames['reused']['target'])['cooldownUntil'] == reused['tick'] + 1200
    for role, owner in enumerate(owners):
        before, paid = checkpoint['before'][role], durable['players'][role]
        original = load(world / f'playerdata/{owner}.dat')
        after = proof['after'][role]
        assert uid(paid['UUID']) == uid(after['UUID']) == uid(original['UUID']) == owner
        assert before['Inventory'] == paid['Inventory'] == original['Inventory'] == after['Inventory']
        cost = (8 + 2 * 1.7446152980511005) if role == 0 else 0
        assert math.isclose(progress(paid)['forceEnergy'], 100 - cost, abs_tol=1e-9)
        assert progress(original) == progress(paid)
        assert progress(proof['before'][role]) == progress(paid)
        for phase in ('recovered', 'blocked_primary', 'blocked_peer', 'expired'):
            assert progress(frames[phase]['players'][role]) == progress(paid)
        assert math.isclose(progress(after)['forceEnergy'], 100 - 2 * cost, abs_tol=1e-9)
        assert progress(paid)['powerCooldowns'] == ({MOD + 'telekinetic_impulse': until} if role == 0 else {})
        assert progress(after)['powerCooldowns'] == ({MOD + 'telekinetic_impulse': reused['tick'] + 1200} if role == 0 else {})
        for field, value in progress(before).items():
            if field not in ('forceEnergy', 'powerCooldowns'):
                assert progress(after)[field] == value
        saved = load(resumed / f'playerdata/{owner}.dat')
        assert saved['Inventory'] == after['Inventory'] and progress(saved) == progress(after)
    saved = [entity for entity in network.prep.entities(resumed) if uid(entity['UUID']) == target]
    assert len(saved) == 1 and saved[0]['Pos'] == frames['reused']['target']['Pos'] and state(saved[0]) == state(frames['reused']['target'])
    pids = {checkpoint['pid'], proof['pid']}
    for run, result, artifact, phases in [(crash, killed, 'impulsecrash', ['baseline']),
                                           (recovery, restarted, 'impulserecover', ['recovered', 'reused'])]:
        assert len(result['clients']) == 2
        for role, item in enumerate(result['clients']):
            folder = repo / f'run-adventure-client-{["one", "two"][role]}-{run}'
            assert item['exitCode'] == 0 and not item['forced'] and Path(item['profile']).resolve() == folder.resolve()
            client = load(folder / f'validation/adventure-{artifact}-client.nbt')
            assert client['schemaVersion'] == 1 and client['pid'] == item['pid'] and client['pid'] not in pids
            pids.add(client['pid'])
            assert client['role'] == role and uid(client['owner']) == owners[role]
            assert client['Inventory'] == checkpoint['before'][role]['Inventory']
            assert [frame['phase'] for frame in client['frames']] == phases
            for frame in client['frames']:
                assert uid(frame['target']) == target and frame['health'] == 10
                server_frames = checkpoint['frames'] if run == crash else proof['frames']
                expected = next(value for value in server_frames if value['phase'] == frame['phase'])
                assert math.dist(frame['Pos'], expected['target']['Pos']) < .2
                assert (folder / f'screenshots/impulse-restart-{frame["phase"]}.png').read_bytes()[:8] == b'\x89PNG\r\n\x1a\n'
            if run == crash:
                assert client['disconnectObserved'] and client['disconnectReason']
            else:
                assert {action['phase']: action['result'] for action in client['actions']} == ({'blocked_primary': 'cooldown', 'ready_push': 'mob_pushed'} if role == 0 else {'blocked_peer': 'cooldown'})
            log = (folder / 'logs/latest.log').read_text(encoding='utf-8')
            assert f'ADVENTURE_{artifact.upper()}_CLIENT_PASS' in log and f'ADVENTURE_{artifact.upper()}_CLIENT_FAILED' not in log
    crash_log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'IMPULSE_CRASH_READY' in crash_log and 'Stopping server' not in crash_log and 'SERVER_FAILED' not in crash_log
    recovery_log = (resumed.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'ADVENTURE_IMPULSERECOVER_SERVER_PASS' in recovery_log and 'All dimensions are saved' in recovery_log and 'SERVER_FAILED' not in recovery_log
    return {'crash': crash, 'recovery': recovery, 'nativeProcesses': sorted(pids), 'activeSavedAtTick': durable['tick'],
            'originalCooldownUntil': until, 'reusedAtTick': reused['tick'], 'nativeActiveEntityAtKill': True,
            'preservedCooldownRefusedBothActors': True, 'nativeInventoryPreserved': True,
            'targetChunkUnloadCertified': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('crash')
    parser.add_argument('recovery')
    args = parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parents[1], args.crash, args.recovery), indent=2))
