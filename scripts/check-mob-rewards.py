"""Correlate actual melee damage, native dead mobs, reward events, receipts and two projections."""
import argparse
import json
import uuid
import tomllib
from pathlib import Path
from read_nbt import load


def uid(parts):
    return str(uuid.UUID(int=sum((part & 0xffffffff) << (96-32*i) for i, part in enumerate(parts))))


def profile(actor):
    return actor['neoforge:attachments']['legacyoftheforce:player_progress']


def verify(repo, run, source):
    world = repo / f'run-adventure-{run}/world'
    proof = load(world / 'validation/adventure-rewards-server.nbt')
    result = json.loads((repo / f'.tools/adventure-rewards-{run}-result.json').read_text(encoding='utf-8-sig'))
    original = load(repo / f'run-adventure-{source}/world/validation/adventure-preparation-server.nbt')
    assert proof['schemaVersion'] == 1 and result['observed'] and result['serverExitCode'] == 0
    assert proof['farmWindow'] == 7200 and proof['farmThreshold'] == 20 and proof['farmMultiplier'] == .25
    assert len(proof['before']) == len(proof['after']) == len(result['clients']) == 2
    owners = [uid(actor['UUID']) for actor in proof['before']]
    assert len(set(owners)) == 2
    kills = {item['validationCase']: item for item in proof['kills']}
    assert len(kills) == len(proof['kills']) == 67
    credits = proof['credits']
    assert len(credits) == len({uid(item['victim']) for item in credits}) == 65
    assert {uid(item['victim']) for item in credits} <= {uid(item['UUID']) for item in kills.values()}
    expected_labels = {f'lane{role}/{index}' for role in range(2) for index in range(32)} | {'shared', 'environment', 'machine'}
    assert set(kills) == expected_labels
    root = world / 'data/legacyoftheforce/rewards'
    receipts = {p.stem: load(p) for p in (root / 'receipts').rglob('*.nbt')}
    assert len(receipts) == 65 and not list((root / 'active').glob('*.nbt'))
    for role, owner in enumerate(owners):
        before = proof['before'][role]
        baseline = next(actor for actor in original['players'] if uid(actor['UUID']) == owner)
        # Native SWAP may already have equipped the sword when the second actor finishes login.
        items = lambda inventory: sorted(json.dumps({k: v for k, v in stack.items() if k != 'Slot'}, sort_keys=True) for stack in inventory)
        assert items(before['Inventory']) == items(baseline['Inventory'])
        before_slots = {stack['Slot']: stack for stack in before['Inventory']}
        baseline_slots = {stack['Slot']: stack for stack in baseline['Inventory']}
        assert {k: v for k, v in before_slots.items() if k not in (8, 29)} == {k: v for k, v in baseline_slots.items() if k not in (8, 29)}
        assert profile(before) == profile(baseline)
        assert profile(before)['totalXpEarned'] == profile(before)['alignmentTenths'] == 0 and before['Health'] > 0
        actor_credits = [item for item in credits if uid(item['owner']) == owner]
        assert len(actor_credits) == 32 + role
        assert sum(item['xp'] for item in actor_credits) == [151, 156][role]
        assert sum(item['alignment'] for item in actor_credits) == [15, 16][role]
        for index in range(32):
            label = f'lane{role}/{index}'
            kill = kills[label]
            assert kill['Health'] <= 0 and kill['NoAI'] and not kill['Invulnerable']
            assert kill['neoforge:attachments']['legacyoftheforce:kill_credit'] == {'schemaVersion': 1, 'paid': 1}
            category = 'HOSTILE' if index < 21 else 'PASSIVE' if index < 31 else 'ELITE'
            reward = next(item for item in credits if item['case'] == label)
            assert uid(reward['owner']) == owner and reward['category'] == category
            assert reward['xp'] == (5 if index < 20 else 1 if index < 31 else 40)
            assert reward['alignment'] == (1 if index < 20 else 0 if index == 20 else -1 if index < 31 else 5)
            assert kill['Pos'] == [199.5+16*role, 100.0, 199.5]
            hits = [hit for hit in proof['hits'] if uid(hit['victim']) == uid(kill['UUID'])]
            assert hits and all(uid(hit['actor']) == owner and not hit['fake'] and hit['damage'] >= .5 for hit in hits)
            assert all(hit['source'] == 'minecraft:player_attack' for hit in hits)
            assert sum(hit['damage'] for hit in hits) >= (20 if index < 21 else 10 if index < 31 else 50)
            victim = uid(kill['UUID'])
            assert uid(receipts[victim]['owner']) == owner and receipts[victim]['accepted'] and uid(receipts[victim]['event']) == victim
        stages = {item['name']: item['progress'] for item in proof['checkpoints'] if uid(item['owner']) == owner}
        assert (stages['ten_hostiles']['totalXpEarned'], stages['ten_hostiles']['alignmentTenths']) == (50, 10)
        assert (stages['farm_21']['totalXpEarned'], stages['farm_21']['alignmentTenths']) == (101, 20)
        assert (stages['ten_passives']['totalXpEarned'], stages['ten_passives']['alignmentTenths']) == (111, 10)
        assert (stages['elite']['totalXpEarned'], stages['elite']['alignmentTenths']) == (151, 15)
        assert stages['shared'] == stages['final']
        after = proof['after'][role]
        assert uid(after['UUID']) == owner and profile(after) == stages['final']
        assert profile(after)['rewardRevision'] == 32 + role
        assert profile(after)['level'] == 4 and profile(after)['xpInLevel'] == [4, 9][role]
        saved = load(world / f'playerdata/{owner}.dat')
        assert profile(saved) == profile(after) and saved['Inventory'] == after['Inventory']
        checkpoint = load(root / f'checkpoints/{owner}.nbt')
        assert checkpoint['profile'] == profile(after) and checkpoint['revision'] == 32 + role
    shared = next(item for item in credits if item['case'] == 'shared')
    assert uid(shared['owner']) == owners[1] and shared['xp'] == 5 and shared['alignment'] == 1
    shared_hits = [hit for hit in proof['hits'] if hit['case'] == 'shared']
    assert uid(shared_hits[0]['actor']) == owners[0] and uid(shared_hits[-1]['actor']) == owners[1]
    assert all(hit['source'] == 'minecraft:player_attack' and not hit['fake'] for hit in shared_hits)
    assert not any(item['case'] in ('environment', 'machine') for item in credits)
    env_hits = [hit for hit in proof['hits'] if hit['case'] == 'environment']
    assert uid(env_hits[0]['actor']) == owners[0] and env_hits[-1]['source'] == 'minecraft:generic'
    machine_hits = [hit for hit in proof['hits'] if hit['case'] == 'machine']
    assert machine_hits and all(hit['fake'] for hit in machine_hits)
    for label in ('environment', 'machine'):
        assert kills[label]['Health'] <= 0 and uid(kills[label]['UUID']) not in receipts
    assert max(c['tick'] for c in credits)-min(c['tick'] for c in credits) < 7200
    pids = {proof['pid']}
    for role, item in enumerate(result['clients']):
        folder = repo / f'run-adventure-client-{["one", "two"][role]}-{run}'
        assert item['name'] == ['AdventureOne', 'AdventureTwo'][role] and item['exitCode'] == 0 and not item['forced']
        assert Path(item['profile']).resolve() == folder.resolve()
        client = load(folder / 'validation/adventure-rewards-client.nbt')
        assert client['schemaVersion'] == 1 and client['pid'] == item['pid'] and client['pid'] not in pids
        pids.add(client['pid'])
        assert uid(client['owner']) == owners[role]
        assert client['Inventory'] == proof['after'][role]['Inventory']
        samples = client['snapshots']
        assert any(s['xp'] == 50 and s['alignment'] == 10 for s in samples)
        assert samples[-1]['xp'] == [151, 156][role] and samples[-1]['alignment'] == [15, 16][role]
        assert client['orientations'] == ['Orientation : Tendance lumineuse']
        actual_targets = {uid(hit['victim']) for hit in proof['hits'] if 'actor' in hit and uid(hit['actor']) == owners[role]}
        assert {uid(hit['victim']) for hit in client['attacks']} == actual_targets
        log = (folder / 'logs/latest.log').read_text(encoding='utf-8')
        assert 'ADVENTURE_REWARDS_CLIENT_PASS' in log and 'ADVENTURE_REWARDS_CLIENT_FAILED' not in log
        from PIL import Image
        with Image.open(folder / 'screenshots/mob-rewards-final.png') as capture:
            assert capture.width >= 800 and capture.height >= 450
            capture.verify()
    log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'ADVENTURE_REWARDS_SERVER_PASS' in log and 'ADVENTURE_REWARDS_SERVER_FAILED' not in log and 'All dimensions are saved' in log
    return {'run': run, 'scope': 'native_two_client_melee_attribution_and_anti_farm', 'nativeDeaths': 67,
            'uniqueRewards': 65, 'profileXp': [151, 156], 'alignmentTenths': [15, 16],
            'configuredOrientation': 'LIGHT_LEANING', 'saveFilesVerified': True}


def verify_recovery(repo, recovery, run, source):
    base = verify(repo, run, source)
    old = load(repo / f'run-adventure-{run}/world/validation/adventure-rewards-server.nbt')
    world = repo / f'run-adventure-{recovery}/world'
    proof = load(world / 'validation/adventure-rewards-recovery-server.nbt')
    result = json.loads((repo / f'.tools/adventure-rewardsrecovery-{recovery}-result.json').read_text(encoding='utf-8-sig'))
    assert proof['schemaVersion'] == 1 and proof['pid'] != old['pid'] and not proof['solo']
    assert result['observed'] and result['serverExitCode'] == 0 and len(result['clients']) == 2
    assert len(proof['before']) == len(proof['after']) == len(proof['credits']) == len(proof['kills']) == 2
    pids = {old['pid'], proof['pid']}
    receipts = {p.stem: load(p) for p in (world / 'data/legacyoftheforce/rewards/receipts').rglob('*.nbt')}
    assert len(receipts) == 67 and not list((world / 'data/legacyoftheforce/rewards/active').glob('*.nbt'))
    for role in range(2):
        before, after, previous = proof['before'][role], proof['after'][role], old['after'][role]
        owner = uid(before['UUID'])
        assert uid(after['UUID']) == uid(previous['UUID']) == owner
        assert profile(before) == profile(previous)
        before_p, after_p = profile(before), profile(after)
        assert after_p['totalXpEarned'] == before_p['totalXpEarned'] + 1 == [152, 157][role]
        assert after_p['alignmentTenths'] == before_p['alignmentTenths'] == [15, 16][role]
        assert after_p['rewardRevision'] == before_p['rewardRevision'] + 1 == 33 + role
        for key in before_p:
            if key not in ('totalXpEarned', 'xpInLevel', 'rewardRevision', 'recentKills', 'rewardEvents'):
                assert before_p[key] == after_p[key], key
        assert after_p['xpInLevel'] == before_p['xpInLevel'] + 1
        assert after_p['rewardEvents'][:-1] == before_p['rewardEvents']
        assert after_p['recentKills'][:-1] == before_p['recentKills']
        cell = before_p['recentKills'][0]['cell']
        assert '/minecraft:zombie/' in cell
        assert sum(item['cell'] == cell for item in before_p['recentKills']) == 21
        assert after_p['recentKills'][-1]['cell'] == cell
        assert after_p['recentKills'][-1]['tick'] - before_p['recentKills'][0]['tick'] < 7200
        reward = next(item for item in proof['credits'] if uid(item['owner']) == owner)
        assert reward['xp'] == 1 and reward['alignment'] == 0 and reward['category'] == 'HOSTILE'
        victim = uid(reward['victim'])
        kill = next(item for item in proof['kills'] if uid(item['UUID']) == victim)
        assert kill['Health'] <= 0 and kill['NoAI']
        hits = [item for item in proof['hits'] if uid(item['victim']) == victim]
        assert hits and all(uid(hit['actor']) == owner and not hit['fake'] and hit['source'] == 'minecraft:player_attack' for hit in hits)
        assert sum(item['damage'] for item in hits) >= 20
        assert uid(receipts[victim]['owner']) == owner and receipts[victim]['accepted']
        saved = load(world / f'playerdata/{owner}.dat')
        assert profile(saved) == after_p and saved['Inventory'] == after['Inventory']
        checkpoint = load(world / f'data/legacyoftheforce/rewards/checkpoints/{owner}.nbt')
        assert checkpoint['profile'] == after_p
        item = result['clients'][role]
        folder = repo / f'run-adventure-client-{["one", "two"][role]}-{recovery}'
        assert item['name'] == ['AdventureOne', 'AdventureTwo'][role] and item['exitCode'] == 0 and not item['forced']
        assert Path(item['profile']).resolve() == folder.resolve()
        client = load(folder / 'validation/adventure-rewards-recovery-client.nbt')
        assert client['pid'] == item['pid'] and client['pid'] not in pids and uid(client['owner']) == owner
        pids.add(client['pid'])
        assert client['Inventory'] == after['Inventory'] and client['orientations'] == ['Orientation : Tendance lumineuse']
        assert client['snapshots'][-1]['xp'] == [152, 157][role] and client['snapshots'][-1]['alignment'] == [15, 16][role]
        assert {uid(hit['victim']) for hit in client['attacks']} == {victim}
    log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'ADVENTURE_REWARDS_RECOVERY_SERVER_PASS' in log and 'ADVENTURE_REWARDS_RECOVERY_SERVER_FAILED' not in log and 'All dimensions are saved' in log
    base.update({'recovery': recovery, 'persistedFarmPenaltyVerified': True, 'recoveryXp': [152, 157]})
    return base


def verify_solo(repo, run, source):
    folder = repo / f'run-adventure-client-solo-{run}'
    world = folder / 'saves/RewardsValidation'
    proof = load(world / 'validation/adventure-rewards-server.nbt')
    client = load(folder / 'validation/adventure-rewards-client.nbt')
    result = json.loads((repo / f'.tools/mob-rewards-solo-{run}-result.json').read_text(encoding='utf-8-sig'))
    original = load(repo / f'run-adventure-{source}/world/validation/adventure-preparation-server.nbt')
    assert result['exitCode'] == 0 and not result['forced'] and Path(result['profile']).resolve() == folder.resolve()
    assert proof['schemaVersion'] == client['schemaVersion'] == 1 and proof['solo']
    assert proof['pid'] == client['pid'] == result['pid']
    assert len(proof['before']) == len(proof['after']) == 1 and len(proof['kills']) == len(proof['credits']) == 32
    before, after = proof['before'][0], proof['after'][0]
    owner = uid(before['UUID'])
    assert uid(after['UUID']) == uid(client['owner']) == owner
    baseline = next(actor for actor in original['players'] if uid(actor['UUID']) == owner)
    assert profile(before) == profile(baseline)
    items = lambda inventory: sorted(json.dumps({k: v for k, v in stack.items() if k != 'Slot'}, sort_keys=True) for stack in inventory)
    assert items(before['Inventory']) == items(baseline['Inventory'])
    assert profile(after)['totalXpEarned'] == 151 and profile(after)['alignmentTenths'] == 15
    assert profile(after)['level'] == 4 and profile(after)['xpInLevel'] == 4 and profile(after)['rewardRevision'] == 32
    receipts = {p.stem: load(p) for p in (world / 'data/legacyoftheforce/rewards/receipts').rglob('*.nbt')}
    assert len(receipts) == 32 and not list((world / 'data/legacyoftheforce/rewards/active').glob('*.nbt'))
    assert len({uid(item['victim']) for item in proof['credits']}) == 32
    for index in range(32):
        label = f'lane0/{index}'
        kill = next(item for item in proof['kills'] if item['validationCase'] == label)
        reward = next(item for item in proof['credits'] if item['case'] == label)
        victim = uid(kill['UUID'])
        assert kill['Health'] <= 0 and kill['NoAI'] and not kill['Invulnerable']
        assert uid(reward['owner']) == owner and uid(reward['victim']) == victim
        assert reward['xp'] == (5 if index < 20 else 1 if index < 31 else 40)
        assert reward['alignment'] == (1 if index < 20 else 0 if index == 20 else -1 if index < 31 else 5)
        assert reward['category'] == ('HOSTILE' if index < 21 else 'PASSIVE' if index < 31 else 'ELITE')
        hits = [hit for hit in proof['hits'] if uid(hit['victim']) == victim]
        assert hits and all(uid(hit['actor']) == owner and not hit['fake'] and hit['source'] == 'minecraft:player_attack' for hit in hits)
        assert sum(hit['damage'] for hit in hits) >= (20 if index < 21 else 10 if index < 31 else 50)
        assert uid(receipts[victim]['owner']) == owner and receipts[victim]['accepted']
    stages = {item['name']: item['progress'] for item in proof['checkpoints']}
    assert (stages['ten_hostiles']['totalXpEarned'], stages['ten_hostiles']['alignmentTenths']) == (50, 10)
    assert (stages['farm_21']['totalXpEarned'], stages['farm_21']['alignmentTenths']) == (101, 20)
    assert (stages['ten_passives']['totalXpEarned'], stages['ten_passives']['alignmentTenths']) == (111, 10)
    assert any(s['xp'] == 50 and s['alignment'] == 10 for s in client['snapshots'])
    assert client['snapshots'][-1]['xp'] == 151 and client['snapshots'][-1]['alignment'] == 15
    assert client['orientations'] == ['Orientation : Tendance lumineuse']
    assert {uid(hit['victim']) for hit in client['attacks']} == {uid(item['UUID']) for item in proof['kills']}
    saved = load(world / f'playerdata/{owner}.dat')
    embedded = load(world / 'level.dat')['Data']['Player']
    assert profile(saved) == profile(embedded) == profile(after)
    assert saved['Inventory'] == embedded['Inventory'] == after['Inventory'] == client['Inventory']
    assert load(world / f'data/legacyoftheforce/rewards/checkpoints/{owner}.nbt')['profile'] == profile(after)
    for other in (repo / f'run-adventure-{source}/world/playerdata').glob('*.dat'):
        if other.stem != owner:
            assert other.read_bytes() == (world / f'playerdata/{other.name}').read_bytes()
    log = (folder / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'Starting integrated minecraft server' in log and 'ADVENTURE_REWARDS_SERVER_PASS' in log and 'ADVENTURE_REWARDS_CLIENT_PASS' in log and 'All dimensions are saved' in log
    assert 'ADVENTURE_REWARDS_CLIENT_FAILED' not in log and 'ADVENTURE_REWARDS_SERVER_FAILED' not in log
    from PIL import Image
    with Image.open(folder / 'screenshots/mob-rewards-final.png') as capture:
        assert capture.width >= 800 and capture.height >= 450
        capture.verify()
    return {'run': run, 'scope': 'native_integrated_melee_categories_and_farm', 'deathsAndUniqueRewards': 32,
            'forceXp': 151, 'alignmentTenths': 15, 'embeddedOwnerAndNativePlayerAgree': True}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('run')
    parser.add_argument('--source', default='ap4')
    parser.add_argument('--recovery')
    parser.add_argument('--solo', action='store_true')
    parser.add_argument('--file-config', action='store_true')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    assert not (args.solo and args.recovery)
    outcome = (verify_solo(repo, args.run, args.source) if args.solo else
               verify_recovery(repo, args.recovery, args.run, args.source) if args.recovery else
               verify(repo, args.run, args.source))
    if args.file_config:
        world = (repo / f'run-adventure-client-solo-{args.run}/saves/RewardsValidation' if args.solo else
                 repo / f'run-adventure-{args.recovery or args.run}/world')
        with (world / 'serverconfig/legacyoftheforce-server.toml').open('rb') as stream:
            config = tomllib.load(stream)
        assert config['rpg']['alignmentThresholds'] == [-100, -10, 10, 100]
        assert config['rpg']['farm']['windowTicks'] == 7200
        outcome['nativeTomlFixtureVerified'] = True
    print(json.dumps(outcome, indent=2))
