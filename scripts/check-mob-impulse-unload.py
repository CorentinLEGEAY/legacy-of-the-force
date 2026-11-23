"""Verify native entity-chunk storage/unload/reload, production cleanup and two-client reuse.

The fixture drives entity-manager visibility explicitly; terrain eviction is not certified.
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


def horizontal_stopped(entity):
    return entity['Motion'][0] == entity['Motion'][2] == 0


def verify(repo, run, source):
    world = repo / f'run-adventure-{run}/world'
    proof = load(world / 'validation/adventure-impulseunload-server.nbt')
    initial = load(repo / f'run-adventure-{source}/world/validation/adventure-preparation-server.nbt')
    result = json.loads((repo / f'.tools/adventure-impulseunload-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert result['observed'] and not result['killed'] and result['serverExitCode'] == 0
    assert proof['schemaVersion'] == 1 and Path(proof['world']).resolve() == world.resolve()
    assert len(proof['before']) == len(proof['after']) == len(result['clients']) == 2
    owners = [uid(actor['UUID']) for actor in proof['before']]
    assert len(set(owners)) == 2
    assert len(proof['events']) == 2
    first, second = proof['events']
    frames = {frame['phase']: frame for frame in proof['frames']}
    assert len(frames) == len(proof['frames']) == 7
    assert set(frames) == {'baseline', 'before_unload', 'reloaded', 'blocked_primary', 'blocked_peer', 'expired', 'reused'}
    target = uid(first['target'])
    assert uid(second['target']) == target and uid(first['actor']) == uid(second['actor']) == owners[0]
    assert first['action'] == second['action'] == 'PUSH'
    before, evidence = frames['before_unload'], proof['unload']
    until = first['tick'] + 1200
    assert before['tick'] == evidence['unloadedAt'] == first['tick'] + 2
    assert state(before['target']) == {'schemaVersion': 1, 'active': 1, 'cooldownUntil': until}
    assert before['target']['Motion'][2] > 0 and before['target']['Pos'][2] > frames['baseline']['target']['Pos'][2] + 1
    assert evidence['removalReason'] == 'UNLOADED_TO_CHUNK' and evidence['absentFromLevel']
    assert evidence['oldStoppedAt'] == evidence['unloadedAt'] + 1
    assert state(evidence['removed']) == state(before['target'])
    stopped = {**state(before['target']), 'active': 0}
    assert state(evidence['oldStopped']) == stopped and horizontal_stopped(evidence['oldStopped'])
    raw, joined = evidence['joinBeforeProduction'], evidence['joinAfterProduction']
    assert evidence['joinTick'] >= evidence['oldStoppedAt'] and evidence['joinTick'] < until
    assert state(raw) == state(before['target']) and raw['Motion'] == before['target']['Motion']
    assert state(joined) == stopped and horizontal_stopped(joined)
    assert raw['Pos'] == joined['Pos'] == before['target']['Pos']
    assert uid(raw['UUID']) == uid(joined['UUID']) == target
    for phase in ('reloaded', 'blocked_primary', 'blocked_peer', 'expired'):
        frame = frames[phase]
        assert frame['target']['Pos'] == before['target']['Pos'] and state(frame['target']) == stopped
        assert horizontal_stopped(frame['target'])
        assert frame['tick'] >= evidence['joinTick']
        assert frame['tick'] >= until if phase == 'expired' else frame['tick'] < until
    assert second['tick'] >= frames['expired']['tick'] >= until
    assert state(frames['reused']['target']) == {'schemaVersion': 1, 'active': 0, 'cooldownUntil': second['tick'] + 1200}
    assert horizontal_stopped(frames['reused']['target'])
    assert frames['reused']['target']['Pos'][2] > before['target']['Pos'][2] + 1
    for frame in proof['frames']:
        assert uid(frame['target']['UUID']) == target and frame['target']['Health'] == 10
        assert len(frame['players']) == 2
    native = [entity for entity in network.prep.entities(world) if uid(entity['UUID']) == target]
    assert len(native) == 1 and native[0]['id'] == 'minecraft:cow' and not native[0].get('NoAI', 0)
    assert native[0]['Pos'] == frames['reused']['target']['Pos'] and state(native[0]) == state(frames['reused']['target'])
    pids = {proof['pid']}
    for role, item in enumerate(result['clients']):
        owner = owners[role]
        folder = repo / f'run-adventure-client-{["one", "two"][role]}-{run}'
        assert item['exitCode'] == 0 and not item['forced'] and Path(item['profile']).resolve() == folder.resolve()
        client = load(folder / 'validation/adventure-impulseunload-client.nbt')
        assert client['schemaVersion'] == 1 and client['pid'] == item['pid'] and client['pid'] not in pids
        pids.add(client['pid'])
        assert client['role'] == role and uid(client['owner']) == owner
        assert [frame['phase'] for frame in client['frames']] == ['baseline', 'reloaded', 'reused']
        for frame in client['frames']:
            assert uid(frame['target']) == target and frame['health'] == 10
            assert math.dist(frame['Pos'], frames[frame['phase']]['target']['Pos']) < .2
            assert (folder / f'screenshots/impulse-restart-{frame["phase"]}.png').read_bytes()[:8] == b'\x89PNG\r\n\x1a\n'
        assert {action['phase']: action['result'] for action in client['actions']} == ({'unload_push': 'mob_pushed', 'blocked_primary': 'cooldown', 'ready_push': 'mob_pushed'} if role == 0 else {'blocked_peer': 'cooldown'})
        original = next(actor for actor in initial['players'] if uid(actor['UUID']) == owner)
        before_actor, after = proof['before'][role], proof['after'][role]
        saved = load(world / f'playerdata/{owner}.dat')
        assert before_actor['Inventory'] == original['Inventory'] == after['Inventory'] == client['Inventory'] == saved['Inventory']
        assert progress(before_actor)['forceEnergy'] == 100
        assert progress(saved) == progress(after)
        cost = (8 + 2 * 1.7446152980511005) if role == 0 else 0
        for phase in ('before_unload', 'reloaded', 'blocked_primary', 'blocked_peer', 'expired'):
            assert math.isclose(progress(frames[phase]['players'][role])['forceEnergy'], 100 - cost, abs_tol=1e-9)
            assert progress(frames[phase]['players'][role])['powerCooldowns'] == ({MOD + 'telekinetic_impulse': until} if role == 0 else {})
        assert math.isclose(progress(after)['forceEnergy'], 100 - 2 * cost, abs_tol=1e-9)
        assert progress(after)['powerCooldowns'] == ({MOD + 'telekinetic_impulse': second['tick'] + 1200} if role == 0 else {})
        for field, value in progress(before_actor).items():
            if field not in ('forceEnergy', 'powerCooldowns'):
                assert progress(after)[field] == value
        log = (folder / 'logs/latest.log').read_text(encoding='utf-8')
        assert 'ADVENTURE_IMPULSEUNLOAD_CLIENT_PASS' in log and 'ADVENTURE_IMPULSEUNLOAD_CLIENT_FAILED' not in log
    log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'ADVENTURE_IMPULSEUNLOAD_SERVER_PASS' in log and 'All dimensions are saved' in log and 'SERVER_FAILED' not in log
    return {'run': run, 'nativeProcesses': sorted(pids), 'nativeEntityChunkUnload': True,
            'unloadedAt': evidence['unloadedAt'], 'oldStoppedAt': evidence['oldStoppedAt'], 'loadedFromDiskAt': evidence['joinTick'],
            'cooldownUntil': until, 'reusedAt': second['tick'], 'nativeInventoryPreserved': True,
            'terrainChunkEvictionCertified': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('run')
    parser.add_argument('--source', default='ap4')
    args = parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parents[1], args.run, args.source), indent=2))
