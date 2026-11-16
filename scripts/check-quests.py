"""Read-only reconciliation of generated quest sites and native player files across two JVMs."""
import argparse
import importlib.util
import json
from pathlib import Path
from read_nbt import load
spec = importlib.util.spec_from_file_location('ilum', Path(__file__).with_name('check-ilum-worlds.py'))
ilum = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ilum)

def verify(repo, run):
    world = repo / f'run-quests-{run}/world'
    journey = load(world / 'validation/quest-journey.nbt')
    reload = load(world / 'validation/quest-reload.nbt')
    assert journey['schemaVersion'] == reload['schemaVersion'] == 1 and journey['pid'] != reload['pid']
    assert len(journey['players']) == len(reload['players']) == 2
    first_id = None
    for index, (before, after) in enumerate(zip(journey['players'], reload['players'])):
        owner = ilum.uid(before['UUID'])
        assert owner == ilum.uid(after['UUID'])
        saved = load(world / f'playerdata/{owner}.dat')
        assert saved['Inventory'] == before['Inventory'] == after['Inventory']
        key = 'legacyoftheforce:quest_knowledge'
        state = saved['neoforge:attachments'][key]
        assert state == before['neoforge:attachments'][key] == after['neoforge:attachments'][key]
        assert state['schemaVersion'] == 1 and state['visitedIlum'] == 1 and state['holocrons'] == ['legacyoftheforce:echo_of_ilum']
        assert state['revision'] == (3 if index == 0 else 2)
        rpg = saved['neoforge:attachments']['legacyoftheforce:player_progress']
        assert rpg['alignmentTenths'] == rpg['totalXpEarned'] == 0
        crystals = [s for s in saved['Inventory'] if s['id'] == 'legacyoftheforce:ilum_crystal']
        assert len(crystals) == (1 if index == 0 else 0)
        if index == 0:
            assert crystals[0]['count'] == 1
            data = crystals[0]['components']['legacyoftheforce:kyber']
            assert data['originPlanet'] == 'legacyoftheforce:ilum'
            first_id = ilum.uid(data['crystalId'])
            assert first_id == ilum.uid(state['crystal'])
        else:
            assert 'crystal' not in state
    for key, structure_id, region, position, expected in [
        ('ruin', 'echo_ruin', world / 'region', journey['holocron'], 'legacyoftheforce:ilum_holocron'),
        ('mine', 'ilum_crystal_mine', world / 'dimensions/legacyoftheforce/ilum/region', journey['source'], None),
    ]:
        x, y, z = position
        native = ilum.read_chunk(region, x // 16, z // 16)
        start = native['structures']['starts']['legacyoftheforce:' + structure_id]
        assert start['Children'] == journey[key]['Children']
        assert start['Children'][0]['schemaVersion'] == 1
        block = ilum.block(native, x, y, z)
        if expected is None:
            assert block in ('minecraft:air', 'minecraft:cave_air')
        else:
            assert block == expected
    for phase in ('JOURNEY', 'RELOAD'):
        log = (repo / f'.tools/quests-{run}-{phase}-server.log').read_text(encoding='utf-8-sig')
        assert f'QUEST_NATIVE_PASS kind={phase}' in log and 'All dimensions are saved' in log and 'failed at' not in log
    return {'journeyPid': journey['pid'], 'reloadPid': reload['pid'], 'owners': 2, 'identifiedCrystals': 1, 'crystalId': first_id,
            'peerCrystalGranted': False, 'rpgRewardsGranted': 0, 'holocronSurvivesRestart': True, 'sourceRegenerated': False}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run')
    args = parser.parse_args()
    assert 1 <= len(args.run) <= 12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.run), indent=2))
