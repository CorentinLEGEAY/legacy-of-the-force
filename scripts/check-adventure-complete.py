"""Reconcile the complete dedicated two-player P4-05 route, including earned victory and paid instance paint."""
import argparse
from collections import Counter
import importlib.util
import json
import math
from pathlib import Path
from read_nbt import load

spec = importlib.util.spec_from_file_location('journey', Path(__file__).with_name('check-adventure-journey.py'))
journey = importlib.util.module_from_spec(spec)
spec.loader.exec_module(journey)
prep, MOD = journey.prep, journey.MOD


def verify(repo, run, preparation):
    prefix = journey.verify(repo, run, preparation, prefix_of_full_run=True)
    world = repo / f'run-adventure-{run}/world'
    proof = load(world / 'validation/adventure-journey-server.nbt')
    crystal = load(world / 'validation/adventure-crystal-stage.nbt')
    duel = load(world / 'validation/adventure-duel-stage.nbt')
    paint = load(world / 'validation/adventure-personalization-stage.nbt')
    source = load(world / 'validation/adventure-preparation-server.nbt')
    owners = [prep.ilum.uid(a['owner']) for a in proof['actors']]
    assert duel['schemaVersion'] == paint['schemaVersion'] == 1 and set(duel['phases']) >= {1, 2, 3}
    assert {'SLASH', 'SHARDS', 'NOVA'} <= set(duel['attacks'])
    boss = duel['deadBoss']
    encounter = prep.ilum.uid(boss['UUID'])
    assert boss['Health'] == 0 and boss['lotfBoss']['phase'] == 3
    assert duel['lastLivingBoss']['Health'] > 0
    assert next(a for a in boss['attributes'] if a['id'] == 'minecraft:generic.max_health')['base'] == 297
    contributors = boss['lotfBoss']['contributors']
    assert {prep.ilum.uid(c['player']) for c in contributors} == set(owners)
    for owner in owners:
        assert sum(h['damage'] for h in duel['hits'] if prep.ilum.uid(h['owner']) == owner) >= 2
    assert all(h['damage'] > 0 and prep.ilum.uid(h['boss']) == encounter for h in duel['hits'])
    ax, ay, az = duel['altarPosition']
    chunk = prep.ilum.chunk(world, ax // 16, az // 16)
    assert chunk['structures']['starts'][MOD + 'ilum_sith_sanctum'] == duel['site']
    altar = next(b for b in chunk['block_entities'] if [b['x'], b['y'], b['z']] == [ax, ay, az])
    assert prep.ilum.uid(altar['lootVictory']) == encounter and altar.get('Items', []) == []
    assert 'pendingClaim' not in altar and {k:v for k,v in altar.items() if k != 'keepPacked'} == duel['altar']
    assert len(paint['players']) == len(paint['vehicles']) == 2
    vehicles = [e for e in prep.entities(world) if e['id'] == MOD + 'speeder']
    assert len(vehicles) == 2
    for index, actor in enumerate(proof['actors']):
        owner = owners[index]
        profile = repo / f'run-adventure-client-{["one", "two"][index]}-{run}'
        client = load(profile / 'validation/adventure-journey-client.nbt')
        stages = {s['stage']: s for s in client['checkpoints']}
        earned = duel['players'][index]
        assert prep.ilum.uid(earned['UUID']) == owner and earned['Health'] > 0
        ledger = prep.counts(crystal['players'][index]['Inventory'])
        foods = [f for f in duel['food'] if prep.ilum.uid(f['owner']) == owner]
        assert all(f['before'] - f['after'] == 1 for f in foods)
        if foods: prep.spend(ledger, 'minecraft:cooked_beef', len(foods))
        if index == 0:
            ledger.update({MOD + 'ilum_boss_relic': 1, MOD + 'speeder_energy_cell': 4})
        assert prep.counts(earned['Inventory']) == prep.counts(stages['duel_stage']['Inventory']) == ledger
        assert journey.travel.components(earned['Inventory']) == journey.travel.components(stages['duel_stage']['Inventory'])
        for native in [earned, actor['portable'], actor['returned'], paint['players'][index]]:
            assert prep.ilum.uid(native['UUID']) == owner and native['Health'] > 0 and native['playerGameType'] == 0 and not native['abilities']['instabuild']
            assert not native['Invulnerable'] and not native['abilities']['invulnerable'] and not native['abilities']['flying']
            progress = native['neoforge:attachments'][MOD + 'player_progress']
            assert progress['totalXpEarned'] == 800 and progress['alignmentTenths'] == 250
            assert progress['rewardRevision'] == 1 and progress['level'] == 11 and progress['xpInLevel'] == 85
            assert len(progress['rewardEvents']) == 1 and prep.ilum.uid(progress['rewardEvents'][0]['id']) == encounter
            assert MOD + 'ilum_frost_lord' in progress['bossRewardCooldowns']
        checkpoint = load(world / f'data/legacyoftheforce/rewards/checkpoints/{owner}.nbt')
        assert checkpoint['accepted'] and checkpoint['revision'] == 1 and checkpoint['profile']['totalXpEarned'] == 800
        assert prep.ilum.uid(checkpoint['owner']) == owner
        prep.spend(ledger, MOD + 'galactic_terminal')
        assert prep.counts(actor['portable']['Inventory']) == prep.counts(actor['returned']['Inventory']) == ledger
        for name in ['portable', 'returned']:
            assert journey.travel.components(actor[name]['Inventory']) == journey.travel.components(stages[name]['Inventory'])
        assert actor['returned']['Pos'] == actor['before']['Pos']
        dye = client['dyeCraft']
        assert dye['recipe'] == 'minecraft:red_dye_from_poppy' and dye['grid'] == 2
        assert prep.counts(dye['before']['Inventory']) == ledger
        native_craft = [c for c in paint['navigation']['crafts'] if prep.ilum.uid(c['owner']) == owner and c['recipe'] == dye['recipe']]
        assert len(native_craft) == 1 and native_craft[0]['result'] == dye['result']
        assert prep.counts(native_craft[0]['inputs']) == Counter({'minecraft:poppy': 1})
        prep.spend(ledger, 'minecraft:poppy')
        ledger['minecraft:red_dye'] += 1
        assert prep.counts(dye['after']['Inventory']) == ledger
        prep.spend(ledger, 'minecraft:red_dye')
        final = paint['players'][index]
        actual = load(world / f'playerdata/{owner}.dat')
        assert actual['Inventory'] == final['Inventory'] and actual['Pos'] == actor['before']['Pos']
        assert prep.counts(actual['Inventory']) == prep.counts(stages['personalized']['Inventory']) == ledger
        assert journey.travel.components(actual['Inventory']) == journey.travel.components(stages['personalized']['Inventory'])
        saber = journey.stacks(actual)[MOD + 'lightsaber']['components'][MOD + 'saber_assembly']
        old_saber = journey.stacks(source['players'][index])[MOD + 'lightsaber']['components'][MOD + 'saber_assembly']
        assert saber['saberId'] == old_saber['saberId'] and saber['parts'] == old_saber['parts']
        if index == 0:
            old_kyber = crystal['drop']['stack']['components'][MOD + 'kyber']
            assert saber['active'] and all(saber['crystal'][k] == old_kyber[k] for k in ['crystalId', 'originPlanet', 'baseKind', 'purity', 'stability'])
        else:
            assert 'crystal' not in saber
        vehicle = next(v for v in vehicles if prep.ilum.uid(v['vehicleState']['ownerId']) == owner)
        state = vehicle['vehicleState']
        assert next(v['vehicleState'] for v in paint['vehicles'] if v['UUID'] == vehicle['UUID']) == state
        original = next(v for v in source['vehicles'] if v['UUID'] == vehicle['UUID'])['vehicleState']
        assert state['blueprint']['primary'] == 0xB02E26
        expected = dict(original, blueprint=dict(original['blueprint'], primary=0xB02E26))
        assert state == expected, 'Paid painting may only change the selected color of the same parked instance'
        receipt = actual['neoforge:attachments'][MOD + 'speeder_recharge_receipt']
        assert receipt['revision'] == 1
        wal = load(world / f'data/legacyoftheforce/speeder_recharge/{owner}.nbt')
        assert wal['phase'] == 'DONE' and wal['schemaVersion'] == 3 and wal['kind'] == 'paint'
        assert wal['after']['receipt'] == receipt
        cost = prep.counts(wal['before']['Inventory']);prep.spend(cost, 'minecraft:red_dye')
        assert cost == prep.counts(wal['after']['Inventory']) == ledger
        movement = [p for p in duel['navigation']['movement'] if prep.ilum.uid(p['owner']) == owner]
        assert len(movement) > 100
        for a, b in zip(movement, movement[1:]):
            assert b['tick'] - a['tick'] == 20 and math.dist([a[k] for k in ['x','y','z']], [b[k] for k in ['x','y','z']]) < 16 and b['health'] > 0
        assert math.dist([movement[-1][k] for k in ['x','y','z']], [ax, ay, az]) < 12
        assert any(p['goal'] == 'boss' and prep.ilum.uid(p['owner']) == owner for p in duel['navigation']['paths'])
        from PIL import Image
        for phase in [1, 2, 3]:
            with Image.open(profile / f'screenshots/adventure-duel-phase-{phase}.png') as image: image.verify()
        with Image.open(profile / 'screenshots/adventure-painted-vehicle.png') as image:
            pixels = Counter(image.convert('RGB').get_flattened_data())
            assert pixels[(20,35,50)] > image.width * image.height // 4
    log = (repo / f'run-adventure-{run}/logs/latest.log').read_text(encoding='utf-8-sig')
    assert 'ADVENTURE_DUEL_NATIVE_PASS' in log and 'ADVENTURE_PAID_PERSONALIZATION_PASS' in log
    return dict(prefix, sameCrystalKeptAfterDuel=True, eligibleBossRewards=2, paidPaints=2,
                completeP4_05=True, validationScope='dedicated_two_clients_guided_route')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run')
    parser.add_argument('--preparation', default='ap4')
    args = parser.parse_args()
    for value in [args.run, args.preparation]:
        assert 1 <= len(value) <= 12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in value)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.run, args.preparation), indent=2))
