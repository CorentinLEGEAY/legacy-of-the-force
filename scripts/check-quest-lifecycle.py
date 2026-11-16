"""Independent native death/respawn and Windows player-save refusal checks for quest knowledge."""
import argparse
import importlib.util
import json
from pathlib import Path
from read_nbt import load
spec = importlib.util.spec_from_file_location('ilum', Path(__file__).with_name('check-ilum-worlds.py'))
ilum = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ilum)

def knowledge(player):
    return player['neoforge:attachments']['legacyoftheforce:quest_knowledge']

def verify(repo, run):
    root = repo / f'run-quests-{run}'
    world = root / 'world'
    proof = load(world / 'validation/quest-lifecycle.nbt')
    assert proof['schemaVersion'] == 1 and proof['nativeSharingRefusal'] == 1 and len(proof['deaths']) == 2
    for index, case in enumerate(proof['deaths']):
        keep = index == 1
        assert case['keepInventory'] == keep
        owner = ilum.uid(case['before']['UUID'])
        saved = load(world / f'playerdata/{owner}.dat')
        assert owner == ilum.uid(case['after']['UUID']) == ilum.uid(saved['UUID'])
        state = knowledge(saved)
        assert state == knowledge(case['before']) == knowledge(case['after'])
        assert state['revision'] == 1 and state['holocrons'] == ['legacyoftheforce:echo_of_ilum']
        assert saved['Inventory'] == case['after']['Inventory']
        assert sum(s['count'] for s in saved['Inventory'] if s['id'] == 'minecraft:emerald') == (3 if keep else 0)
        assert sum(s['count'] for s in case['before']['Inventory'] if s['id'] == 'minecraft:emerald') == 3
        assert saved['Health'] > 0
        rpg = saved['neoforge:attachments']['legacyoftheforce:player_progress']
        assert rpg['totalXpEarned'] == rpg['alignmentTenths'] == 0
    before, after = proof['refusedBefore'], proof['retriedAfter']
    assert knowledge(before)['revision'] == 0 and knowledge(before)['holocrons'] == []
    assert knowledge(after)['revision'] == 1 and knowledge(after)['holocrons'] == ['legacyoftheforce:echo_of_ilum']
    owner = ilum.uid(before['UUID'])
    saved = load(world / f'playerdata/{owner}.dat')
    assert knowledge(saved) == knowledge(after) and saved['Inventory'] == before['Inventory'] == after['Inventory'] == []
    x, _, z = proof['site']
    entities = ilum.read_chunk(world / 'entities', x // 16, z // 16)['Entities']
    ground = sum(e['Item']['count'] for e in entities if e['id'] == 'minecraft:item' and e['Item']['id'] == 'minecraft:emerald')
    assert ground == 3, 'The dropped stack must be present in native entity storage, not only a test snapshot'
    log = (repo / f'.tools/quests-{run}-LIFECYCLE-server.log').read_text(encoding='utf-8-sig')
    assert 'QUEST_NATIVE_PASS kind=LIFECYCLE' in log and 'All dimensions are saved' in log and 'failed at' not in log
    assert 'Optional knowledge save failed' in log and 'Native player checkpoint was not persisted' in log
    return {'serverPid': proof['pid'], 'nativeDeaths': 2, 'knowledgeSurvivesBothInventoryRules': True,
            'keptEmeralds': 3, 'groundEmeralds': ground, 'nativeSaveRefused': True, 'retryKnowledgeRevision': 1}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run')
    args = parser.parse_args()
    assert 1 <= len(args.run) <= 12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.run), indent=2))
