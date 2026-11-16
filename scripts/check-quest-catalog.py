"""Independent native player/journal reconciliation across actual server datapack reloads."""
import argparse
import importlib.util
import json
from pathlib import Path
from read_nbt import load
spec = importlib.util.spec_from_file_location('ilum', Path(__file__).with_name('check-ilum-worlds.py'))
ilum = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ilum)
ID = 'legacyoftheforce:quest_knowledge'
EMPTY = {'schemaVersion': 1, 'revision': 0, 'visitedIlum': 0, 'holocrons': []}

def knowledge(player):
    # Attachments are lazy: a player never consulting the journal can omit its empty default.
    return player.get('neoforge:attachments', {}).get(ID, EMPTY)

def verify(repo, run):
    root = repo / f'run-quests-{run}'
    proof = load(root / 'world/validation/quest-catalog.nbt')
    assert proof['schemaVersion'] == 1
    stages = proof['stages']
    assert [s['stage'] for s in stages] == ['initial', 'future_schema', 'reward_rejected', 'truncated_json', 'valid_override', 'restored']
    assert [s['definitions'] for s in stages] == [1, 0, 0, 0, 1, 1]
    owners = [ilum.uid(p['UUID']) for p in stages[0]['players']]
    assert len(set(owners)) == 2
    first_state = knowledge(stages[0]['players'][0])
    assert first_state['revision'] == 1 and first_state['holocrons'] == ['legacyoftheforce:echo_of_ilum']
    for index, stage in enumerate(stages):
        assert len(stage['players']) == len(stage['journals']) == 2
        assert stage['sourcePack'] == ('mod/legacyoftheforce' if index in (0, 5) else 'file/lotf_quest_validation')
        for owner_index, player in enumerate(stage['players']):
            assert ilum.uid(player['UUID']) == owners[owner_index]
            state = knowledge(player)
            assert state == (first_state if owner_index == 0 or index == 5 else EMPTY)
            assert player['Inventory'] == []
            rpg = player['neoforge:attachments']['legacyoftheforce:player_progress']
            assert rpg['totalXpEarned'] == rpg['alignmentTenths'] == 0
            lines = stage['journals'][owner_index]
            keys = [line['key'] for line in lines]
            if index in (1, 2, 3):
                assert 'quest.legacyoftheforce.archive_unavailable' in keys
                assert 'quest.legacyoftheforce.find_echo' not in keys
                assert all(line['status'] == 0 for line in lines)
            elif index == 4 and owner_index == 0:
                assert 'quest.legacyoftheforce.journal' in keys and 'quest.legacyoftheforce.echo.title' not in keys
            elif index == 5:
                assert 'quest.legacyoftheforce.echo.title' in keys and 'quest.legacyoftheforce.archive_unavailable' not in keys
    for index, owner in enumerate(owners):
        saved = load(root / f'world/playerdata/{owner}.dat')
        assert knowledge(saved) == first_state and saved['Inventory'] == []
    assert len(proof['events']) == 2
    assert {ilum.uid(e['owner']) for e in proof['events']} == set(owners)
    assert len({ilum.uid(e['event']) for e in proof['events']}) == 2
    packs = load(root / 'world/level.dat')['Data']['DataPacks']
    assert 'file/lotf_quest_validation' not in packs['Enabled'] and 'file/lotf_quest_validation' in packs['Disabled']
    overlay = json.loads((root / 'world/datapacks/lotf_quest_validation/data/legacyoftheforce/quests/echo_of_ilum.json').read_text(encoding='utf-8'))
    assert overlay['title'] == 'quest.legacyoftheforce.journal' and overlay['rewards'] == {'xp': 0, 'alignmentTenths': 0}
    log = (repo / f'.tools/quests-{run}-CATALOG-server.log').read_text(encoding='utf-8-sig')
    assert 'QUEST_NATIVE_PASS kind=CATALOG' in log and 'All dimensions are saved' in log and 'failed at' not in log
    assert 'Unsupported quest schema or dependencies' in log and 'This optional guide cannot grant RPG rewards' in log
    assert 'Loaded 0 optional quest guides' in log and 'com.google.gson.JsonParseException: java.io.EOFException: End of input' in log
    assert "Couldn't parse data file legacyoftheforce:echo_of_ilum from legacyoftheforce:quests/echo_of_ilum.json" in log
    return {'pid': proof['pid'], 'nativeReloads': 5, 'rejectedDefinitions': 3, 'knownKnowledgePreserved': True,
            'newDiscoveryDeniedWhileUnavailable': True, 'discoveriesAfterRestore': 2, 'rpgRewards': 0, 'testPackDisabled': True}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run')
    args = parser.parse_args()
    assert 1 <= len(args.run) <= 12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.run), indent=2))
