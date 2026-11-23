"""Reconcile actual client observations, server events, costs and native entity/player saves.

This scenario does not certify process-crash recovery or third-party claim adapters.
"""
import argparse
import importlib.util
import json
import math
from pathlib import Path
from read_nbt import load

spec = importlib.util.spec_from_file_location('prep', Path(__file__).with_name('check-adventure-preparation.py'))
prep = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prep)
uid, MOD = prep.ilum.uid, 'legacyoftheforce:'
CASES = ['PUSH', 'PULL', 'REVOKE', 'DIMENSION', 'DEATH', 'LOGOUT']
REFUSALS = {'NOAI': 'invalid_target', 'RESISTANCE': 'resistant_target',
            'PROTECTED': 'protected_area', 'WALL': 'out_of_range'}


def progress(actor):
    return actor['neoforge:attachments'][MOD + 'player_progress']


def state(target):
    return target['neoforge:attachments'][MOD + 'mob_impulse']


def verify(repo, run, source):
    world = repo / f'run-adventure-{run}/world'
    proof = load(world / 'validation/adventure-impulse-server.nbt')
    initial = load(repo / f'run-adventure-{source}/world/validation/adventure-preparation-server.nbt')
    result = json.loads((repo / f'.tools/adventure-impulse-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert result['observed'] and result['serverExitCode'] == 0 and not result['killed']
    assert proof['schemaVersion'] == 1 and len(proof['before']) == len(proof['after']) == 2
    owners = [uid(actor['UUID']) for actor in proof['before']]
    target = uid(proof['target']['UUID'])
    assert len(set(owners)) == 2
    frames = {frame['name']: frame for frame in proof['frames']}
    assert len(frames) == len(proof['frames']) == 16
    for name, frame in frames.items():
        assert uid(frame['target']['UUID']) == target and frame['target']['Health'] == 10
        assert len(frame['players']) == (1 if name == 'logout' else 2)
        peer = next(actor for actor in frame['players'] if uid(actor['UUID']) == owners[1])
        assert progress(peer)['forceEnergy'] == 100
    for phase in REFUSALS:
        frame = frames['refused_' + phase]
        assert frame['target']['Pos'] == frames['baseline']['target']['Pos']
        assert state(frame['target']) == {'schemaVersion': 1, 'active': 0, 'cooldownUntil': 0}
        assert all(progress(actor)['forceEnergy'] == 100 for actor in frame['players'])
    events = proof['events']
    assert [event['case'] for event in events] == CASES
    interrupts = {event['case']: event for event in proof['interruptions']}
    assert len(interrupts) == len(proof['interruptions']) == 4
    assert set(interrupts) == set(CASES[2:])
    previous_energy = 100
    for index, event in enumerate(events):
        case = event['case']
        assert uid(event['target']) == target and uid(event['actor']) == owners[0]
        assert event['action'] == ('PULL' if case == 'PULL' else 'PUSH')
        assert math.isclose(event['mass'], .9 * .9 * 1.4 / .65, abs_tol=1e-6)
        assert math.isclose(event['beforeEnergy'] - event['afterEnergy'],
                            (10 if case == 'PULL' else 8) + 2 * event['mass'], abs_tol=1e-9)
        assert event['beforeEnergy'] == (100 if case == 'LOGOUT' else previous_energy)
        previous_energy = event['afterEnergy']
        assert event['state'] == {'schemaVersion': 1, 'active': 1, 'cooldownUntil': event['tick'] + 40}
        if index:
            assert event['tick'] >= events[index - 1]['tick'] + 40
        final = frames[case.lower()]
        assert state(final['target']) == {**event['state'], 'active': 0}
        assert final['motion'][0] ** 2 + final['motion'][2] ** 2 < 1e-6
        displacement = final['target']['Pos'][2] - event['origin'][2]
        assert displacement < -1 if case == 'PULL' else displacement > 1
        samples = [sample for sample in proof['trajectory'] if sample['case'] == case]
        assert samples and samples[-1]['active'] == 0
        # The production impulse adds a small lift; vanilla gravity returns it to the floor.
        assert all(100 <= sample['pos'][1] < 100.1 for sample in samples)
        assert samples[-1]['pos'][1] == 100
        assert any(sample['motion'][2] < 0 if case == 'PULL' else sample['motion'][2] > 0 for sample in samples)
        if case in interrupts:
            stop = interrupts[case]
            assert 0 <= stop['tick'] - event['tick'] < 8
            assert stop['state'] == {**event['state'], 'active': 0}
            assert stop['motion'][0] == stop['motion'][2] == 0
            if case != 'LOGOUT':
                active = frames['active_before_' + case]
                assert state(active['target']) == event['state'] and active['tick'] == event['tick'] + 2
    assert next(actor for actor in frames['dead']['players'] if uid(actor['UUID']) == owners[0])['Health'] == 0
    assert frames['contested']['tick'] < events[0]['state']['cooldownUntil']
    assert progress(next(actor for actor in frames['death']['players'] if uid(actor['UUID']) == owners[0]))['forceEnergy'] == 100
    saved_targets = [entity for entity in prep.entities(world) if uid(entity['UUID']) == target]
    assert len(saved_targets) == 1
    saved = saved_targets[0]
    assert saved['id'] == 'minecraft:cow' and saved['Health'] == 10 and not saved.get('NoAI', 0)
    assert saved['Pos'] == proof['target']['Pos'] and state(saved) == state(proof['target'])
    pids = {proof['pid']}
    assert len(result['clients']) == 2
    for role, item in enumerate(result['clients']):
        folder = repo / f'run-adventure-client-{["one", "two"][role]}-{run}'
        assert item['exitCode'] == 0 and not item['forced'] and Path(item['profile']).resolve() == folder.resolve()
        client = load(folder / 'validation/adventure-impulse-client.nbt')
        assert client['schemaVersion'] == 1 and client['role'] == role and uid(client['owner']) == owners[role]
        assert client['pid'] == item['pid'] and client['pid'] not in pids
        pids.add(client['pid'])
        assert client['ending'] == ['socket_closed_during_impulse', 'peer_observed_all_interruptions'][role]
        assert client['netherSeen'] == client['deathScreenAndRespawn'] == (1 if role == 0 else 0)
        expected_phases = ['baseline'] + [case.lower() for case in CASES[:5 if role == 0 else 6]]
        assert [frame['phase'] for frame in client['frames']] == expected_phases
        for frame in client['frames']:
            assert uid(frame['target']) == target and frame['health'] == 10
            assert math.dist(frame['Pos'], frames[frame['phase']]['target']['Pos']) <= .2
            screenshot = folder / f'screenshots/impulse-{frame["phase"]}.png'
            assert screenshot.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n'
        actions = {action['phase']: action['result'] for action in client['actions']}
        expected = {**REFUSALS, **{case: 'mob_pulled' if case == 'PULL' else 'mob_pushed' for case in CASES[:-1]}} if role == 0 else {'CONTEST': 'cooldown'}
        assert actions == expected  # LOGOUT closes the socket before feedback is acknowledged.
        before, after = proof['before'][role], proof['after'][role]
        original = next(actor for actor in initial['players'] if uid(actor['UUID']) == owners[role])
        assert before['Inventory'] == original['Inventory'] == after['Inventory'] == client['Inventory']
        saved_actor = load(world / f'playerdata/{owners[role]}.dat')
        assert saved_actor['Inventory'] == after['Inventory'] and progress(saved_actor) == progress(after)
        for field, value in progress(before).items():
            if field not in ('forceEnergy', 'powerCooldowns'):
                assert progress(after)[field] == value
        assert progress(after)['forceEnergy'] == (events[-1]['afterEnergy'] if role == 0 else 100)
        assert progress(after)['powerCooldowns'] == ({MOD + 'telekinetic_impulse': events[-1]['tick'] + 40} if role == 0 else {})
        log = (folder / 'logs/latest.log').read_text(encoding='utf-8')
        assert 'ADVENTURE_IMPULSE_CLIENT_PASS' in log and 'ADVENTURE_IMPULSE_CLIENT_FAILED' not in log
    log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'ADVENTURE_IMPULSE_SERVER_PASS' in log and 'ADVENTURE_IMPULSE_SERVER_FAILED' not in log and 'All dimensions are saved' in log
    return {'run': run, 'nativeProcesses': sorted(pids), 'committedImpulses': 6,
            'unpaidRefusals': 5, 'interruptedBeforeExpiry': {case: interrupts[case]['tick'] - events[CASES.index(case)]['tick'] for case in CASES[2:]},
            'matchingClientFrames': [6, 7], 'nativeInventoryPreserved': True,
            'processCrashCertified': False, 'thirdPartyClaimsCertified': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('run')
    parser.add_argument('--source', default='ap4')
    args = parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parents[1], args.run, args.source), indent=2))
