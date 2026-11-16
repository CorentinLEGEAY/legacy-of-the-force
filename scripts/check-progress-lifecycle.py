"""Verify native death, replacement player, snapshots and saved nontrivial RPG profiles."""
import argparse
import json
import math
import uuid
from pathlib import Path
from read_nbt import load


def uid(parts):
    return str(uuid.UUID(int=sum((part & 0xffffffff) << (96-32*i) for i, part in enumerate(parts))))


def progress(actor):
    return actor['neoforge:attachments']['legacyoftheforce:player_progress']


def stable(value):
    return {key: item for key, item in value.items() if key not in ('forceEnergy', 'maxForceEnergy')}


def verify(repo, run, source):
    world = repo / f'run-adventure-{run}/world'
    proof = load(world / 'validation/adventure-progress-server.nbt')
    result = json.loads((repo / f'.tools/adventure-progress-{run}-result.json').read_text(encoding='utf-8-sig'))
    original = load(repo / f'run-adventure-{source}/world/validation/adventure-personalization-stage.nbt')
    assert result['observed'] and result['serverExitCode'] == 0 and len(result['clients']) == 2
    assert proof['schemaVersion'] == 1 and proof['keepInventory'] and proof['maxEnergy'] == 137
    assert proof['deathFraction'] == .37 and proof['regen'] == 0
    assert all(len(proof[key]) == 2 for key in ('before', 'deaths', 'respawns', 'after'))
    owners = {uid(actor['UUID']) for actor in original['players']}
    assert len(owners) == 2 and {uid(actor['UUID']) for actor in proof['before']} == owners
    for before in proof['before']:
        owner = uid(before['UUID'])
        baseline = next(actor for actor in original['players'] if uid(actor['UUID']) == owner)
        assert before['Inventory'] == baseline['Inventory'] and before['Health'] > 0
        assert stable(progress(before)) == stable(progress(baseline))
        assert progress(before)['level'] == 11 and progress(before)['totalXpEarned'] == 800 and progress(before)['alignmentTenths'] == 250
        assert progress(before)['forceEnergy'] == 82.25 and progress(before)['maxForceEnergy'] == 137
        death = next(actor for actor in proof['deaths'] if uid(actor['UUID']) == owner)
        assert death['Health'] <= 0 and death['Inventory'] == before['Inventory']
        assert progress(death) == progress(before)
        for stage in ('respawns', 'after'):
            actor = next(actor for actor in proof[stage] if uid(actor['UUID']) == owner)
            assert actor['Health'] > 0 and actor['Inventory'] == before['Inventory']
            assert stable(progress(actor)) == stable(progress(before))
            assert progress(actor)['maxForceEnergy'] == 137 and math.isclose(progress(actor)['forceEnergy'], 50.69, abs_tol=1e-9)
        saved = load(world / f'playerdata/{owner}.dat')
        after = next(actor for actor in proof['after'] if uid(actor['UUID']) == owner)
        assert saved['Inventory'] == after['Inventory'] and progress(saved) == progress(after)
    seen = set()
    pids = {proof['pid']}
    for index, item in enumerate(result['clients']):
        assert item['name'] == ['AdventureOne', 'AdventureTwo'][index] and item['exitCode'] == 0 and not item['forced']
        profile = repo / f'run-adventure-client-{["one", "two"][index]}-{run}'
        assert Path(item['profile']).resolve() == profile.resolve()
        client = load(profile / 'validation/adventure-progress-client.nbt')
        owner = uid(client['owner'])
        assert client['schemaVersion'] == 1 and owner in owners and owner not in seen
        seen.add(owner)
        assert client['pid'] == item['pid'] and client['pid'] not in pids
        pids.add(client['pid'])
        after = next(actor for actor in proof['after'] if uid(actor['UUID']) == owner)
        assert client['Inventory'] == after['Inventory']
        samples = client['snapshots']
        assert all(s['level'] == 11 and s['xp'] == 800 and s['alignment'] == 250 and s['max'] == 137 for s in samples)
        spent = [s for s in samples if math.isclose(s['energy'], 82.25, abs_tol=1e-9)]
        reset = [s for s in samples if math.isclose(s['energy'], 50.69, abs_tol=1e-9)]
        assert spent and reset and max(s['tick'] for s in reset) > min(s['tick'] for s in spent)
        log = (profile / 'logs/latest.log').read_text(encoding='utf-8')
        assert 'ADVENTURE_PROGRESS_CLIENT_PASS' in log and 'ADVENTURE_PROGRESS_CLIENT_FAILED' not in log
        from PIL import Image
        for view in ('spent', 'death', 'respawned'):
            with Image.open(profile / f'screenshots/progress-{view}.png') as capture:
                assert capture.width >= 800 and capture.height >= 450
                capture.verify()
    log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'ADVENTURE_PROGRESS_SERVER_PASS' in log and 'ADVENTURE_PROGRESS_SERVER_FAILED' not in log and 'All dimensions are saved' in log
    assert log.count('AdventureOne was killed') == log.count('AdventureTwo was killed') == 1
    return {'run': run, 'source': source, 'scope': 'native_two_client_nontrivial_rpg_death_keep_inventory',
            'owners': sorted(owners), 'spentEnergy': 82.25, 'respawnEnergy': 50.69, 'maxEnergy': 137,
            'dropModeCertified': False}


def verify_recovery(repo, recovery, run, source):
    previous = verify(repo, run, source)
    world = repo / f'run-adventure-{recovery}/world'
    original = load(repo / f'run-adventure-{run}/world/validation/adventure-progress-server.nbt')
    proof = load(world / 'validation/adventure-progress-recovery-server.nbt')
    result = json.loads((repo / f'.tools/adventure-progressrecovery-{recovery}-result.json').read_text(encoding='utf-8-sig'))
    assert proof['schemaVersion'] == 1 and proof['pid'] != original['pid']
    assert result['observed'] and result['serverExitCode'] == 0 and len(result['clients']) == 2
    assert proof['deaths'] == [] and proof['respawns'] == [] and len(proof['before']) == len(proof['after']) == 2
    owners = set()
    for before in proof['before']:
        owner = uid(before['UUID'])
        owners.add(owner)
        old = next(actor for actor in original['after'] if uid(actor['UUID']) == owner)
        after = next(actor for actor in proof['after'] if uid(actor['UUID']) == owner)
        saved = load(world / f'playerdata/{owner}.dat')
        assert all(actor['Inventory'] == old['Inventory'] and progress(actor) == progress(old) for actor in (before, after, saved))
        assert math.isclose(progress(saved)['forceEnergy'], 50.69, abs_tol=1e-9)
    pids = {proof['pid'], original['pid']}
    client_owners = set()
    for index, item in enumerate(result['clients']):
        profile = repo / f'run-adventure-client-{["one", "two"][index]}-{recovery}'
        assert item['name'] == ['AdventureOne', 'AdventureTwo'][index]
        assert item['exitCode'] == 0 and not item['forced'] and Path(item['profile']).resolve() == profile.resolve()
        client = load(profile / 'validation/adventure-progress-recovery-client.nbt')
        assert client['schemaVersion'] == 1 and client['pid'] == item['pid'] and client['pid'] not in pids
        pids.add(client['pid'])
        owner = uid(client['owner'])
        assert owner in owners and owner not in client_owners
        client_owners.add(owner)
        before = next(actor for actor in proof['before'] if uid(actor['UUID']) == owner)
        assert client['Inventory'] == before['Inventory'] and client['snapshots']
        for snapshot in client['snapshots']:
            assert snapshot['level'] == 11 and snapshot['xp'] == 800 and snapshot['alignment'] == 250
            assert snapshot['max'] == 137 and math.isclose(snapshot['energy'], 50.69, abs_tol=1e-9)
        from PIL import Image
        with Image.open(profile / 'screenshots/progress-reloaded.png') as capture:
            assert capture.width >= 800 and capture.height >= 450
            capture.verify()
        log = (profile / 'logs/latest.log').read_text(encoding='utf-8')
        assert 'ADVENTURE_PROGRESS_RECOVERY_CLIENT_PASS' in log and 'ADVENTURE_PROGRESS_RECOVERY_CLIENT_FAILED' not in log
    log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'ADVENTURE_PROGRESS_RECOVERY_SERVER_PASS' in log and 'ADVENTURE_PROGRESS_RECOVERY_SERVER_FAILED' not in log and 'All dimensions are saved' in log
    assert 'was killed' not in log
    previous['recovery'] = recovery
    previous['postDeathReloadCertified'] = True
    return previous


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('run')
    parser.add_argument('--source', default='ad4')
    parser.add_argument('--recovery')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    print(json.dumps(verify_recovery(repo, args.recovery, args.run, args.source) if args.recovery else verify(repo, args.run, args.source), indent=2))
