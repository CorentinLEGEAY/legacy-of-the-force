"""Reconcile two genuine socket clients' personal quest collections and native rendered framebuffers."""
import argparse
import importlib.util
import json
import re
from pathlib import Path
from read_nbt import load

def module(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value

solo = module('solo_quest', 'check-quest-solo.py')
ilum = solo.ilum

def knowledge(player):
    return player['neoforge:attachments']['legacyoftheforce:quest_knowledge']

def verify(repo, run):
    prefix = repo / f'.tools/quest-network-{run}'
    result = json.loads(Path(str(prefix) + '-result.json').read_text(encoding='utf-8-sig'))
    assert result['observed'] and result['serverExitCode'] == 0 and len(result['clients']) == 2
    assert all(c['exitCode'] == 0 and not c['forced'] for c in result['clients'])
    world = repo / f'run-quests-{run}/world'
    proof = load(world / 'validation/quest-network-server.nbt')
    assert proof['schemaVersion'] == 1
    assert len({proof['pid'], *(c['pid'] for c in result['clients'])}) == 3
    assert len(proof['before']) == len(proof['afterFirst']) == len(proof['players']) == 2
    owners = set()
    for index, client_result in enumerate(result['clients']):
        profile = Path(client_result['profile'])
        client = load(profile / 'validation/quest-network-client.nbt')
        expected = proof['players'][index]
        owner = ilum.uid(expected['UUID'])
        owners.add(owner)
        assert client_result['name'] == ('QuestViewer' if index == 0 else 'QuestReader')
        assert client['schemaVersion'] == 1 and client['network'] == 1 and not client['recovered']
        assert client['nativeJournalScreen'] == 1 and client['frameCaptureVersion'] == 2 and client['pid'] == client_result['pid'] and ilum.uid(client['owner']) == owner
        assert client['itemUses'] == 1 and client['commands'] == (2 if index == 0 else 3)
        assert [l['status'] for l in client['knownLines'] if l['status'] > 0] == [2, 1, 1]
        assert any(l['key'] == 'quest.legacyoftheforce.find_echo' for l in client['emptyLines'])
        if index == 1:
            assert any(l['key'] == 'quest.legacyoftheforce.find_echo' for l in client['privateLines'])
            assert all(l['status'] == 0 for l in client['privateLines'])
            assert (profile / 'screenshots/quest-peer-private.png').stat().st_size > 1000
            solo.panel_frame(profile / 'screenshots/quest-peer-private.png')
        saved = load(world / f'playerdata/{owner}.dat')
        assert saved['Inventory'] == expected['Inventory'] == client['Inventory'] == []
        assert knowledge(saved) == knowledge(expected)
        state = knowledge(saved)
        assert state['schemaVersion'] == 1 and state['revision'] == 1 and state['holocrons'] == ['legacyoftheforce:echo_of_ilum']
        assert state['visitedIlum'] == 0 and 'crystal' not in state
        before, after_first = proof['before'][index], proof['afterFirst'][index]
        assert knowledge(before)['revision'] == 0 and knowledge(before)['holocrons'] == []
        assert knowledge(after_first)['revision'] == (1 if index == 0 else 0)
        assert knowledge(after_first)['holocrons'] == (['legacyoftheforce:echo_of_ilum'] if index == 0 else [])
        rpg = saved['neoforge:attachments']['legacyoftheforce:player_progress']
        assert rpg['alignmentTenths'] == rpg['totalXpEarned'] == 0 and saved['Health'] > 0
        solo.frames(profile, False)
        log = Path(str(prefix) + f"-{client_result['name']}-console.log").read_text(encoding='utf-8-sig')
        assert 'QUEST_NETWORK_CLIENT_PASS' in log and 'QUEST_NETWORK_CLIENT_FAILED' not in log
    assert len(owners) == 2 and len(proof['events']) == 2
    assert {ilum.uid(e['owner']) for e in proof['events']} == owners
    assert len({ilum.uid(e['event']) for e in proof['events']}) == 2
    x, y, z = proof['holocron']
    native = ilum.read_chunk(world / 'region', x // 16, z // 16)
    assert native['structures']['starts']['legacyoftheforce:echo_ruin']['Children'] == proof['site']['Children']
    assert ilum.block(native, x, y, z) == 'legacyoftheforce:ilum_holocron'
    log = Path(str(prefix) + '-server.log').read_text(encoding='utf-8-sig')
    addresses = re.findall(r'(QuestViewer|QuestReader)\[/127\.0\.0\.1:(\d+)\]', log)
    assert len(addresses) == 2 and len({port for _, port in addresses}) == 2
    assert 'QUEST_NETWORK_SERVER_PASS' in log and 'All dimensions are saved' in log and 'QUEST_NETWORK_SERVER_FAILED' not in log
    return {'serverPid': proof['pid'], 'clientPids': [c['pid'] for c in result['clients']], 'distinctSockets': 2,
            'peerEmptyAfterFirstDiscovery': True, 'personalDiscoveryEvents': 2, 'knowledgeRevisionPerOwner': 1,
            'nativeUsePackets': 2, 'nativeRenderedClientCollections': 2, 'rpgRewards': 0}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run')
    args = parser.parse_args()
    assert 1 <= len(args.run) <= 12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.run), indent=2))
