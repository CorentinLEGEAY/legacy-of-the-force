"""Reconcile native integrated owner knowledge, S2C journal snapshots and framebuffers across real JVMs."""
import argparse
import hashlib
import importlib.util
import json
import struct
from pathlib import Path
from read_nbt import load
spec = importlib.util.spec_from_file_location('ilum', Path(__file__).with_name('check-ilum-worlds.py'))
ilum = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ilum)
KNOWLEDGE = 'legacyoftheforce:quest_knowledge'
CHECKPOINT = 'legacyoftheforce:save_checkpoint'

def knowledge(player):
    return player['neoforge:attachments'][KNOWLEDGE]

def frames(profile, recovered):
    names = ['quest-known.png', 'quest-scrolled.png']
    if not recovered:
        names.append('quest-empty.png')
    hashes = []
    for name in names:
        data = (profile / f'screenshots/{name}').read_bytes()
        assert len(data) > 1000 and data[:8] == b'\x89PNG\r\n\x1a\n'
        width, height = struct.unpack('>II', data[16:24])
        assert width >= 640 and height >= 400
        hashes.append(hashlib.sha256(data).hexdigest())
    assert len(set(hashes)) == len(names), 'Frames must reflect distinct screens/scroll positions'
    return names

def verify(repo, run):
    result = json.loads((repo / f'.tools/quest-solo-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert result['exitCode'] == 0 and not result['forced']
    recovered = result['recover']
    profile = repo / f'run-quest-client-solo-{run}'
    world = profile / 'saves/QuestValidation'
    client = load(profile / 'validation/quest-solo-client.nbt')
    name = 'quest-solo-recovered' if recovered else 'quest-solo-server'
    proof = load(world / f'validation/{name}.nbt')
    assert client['schemaVersion'] == proof['schemaVersion'] == 1
    assert client['pid'] == proof['pid'] == result['pid']
    assert client['recovered'] == recovered and client['nativeJournalScreen'] == 1
    owner = ilum.uid(client['owner'])
    saved = load(world / f'playerdata/{owner}.dat')
    embedded = load(world / 'level.dat')['Data']['Player']
    native = proof['native']
    state = knowledge(native)
    assert state['schemaVersion'] == 1 and state['revision'] == 1 and state['holocrons'] == ['legacyoftheforce:echo_of_ilum']
    assert state['visitedIlum'] == 0 and 'crystal' not in state
    for player in (saved, embedded, native):
        assert ilum.uid(player['UUID']) == owner and knowledge(player) == state
        assert player['Inventory'] == client['Inventory'] == []
        assert player['Dimension'] == client['dimension'] == 'minecraft:overworld'
        rpg = player['neoforge:attachments']['legacyoftheforce:player_progress']
        assert rpg['totalXpEarned'] == rpg['alignmentTenths'] == 0
    assert saved['neoforge:attachments'][CHECKPOINT] == embedded['neoforge:attachments'][CHECKPOINT] == native['neoforge:attachments'][CHECKPOINT]
    lines = client['knownLines']
    assert any(line['key'] == 'quest.legacyoftheforce.echo.clue' for line in lines)
    assert [line['status'] for line in lines if line['status'] > 0] == [2, 1, 1]
    assert client['commands'] == 2 and client['itemUses'] == (0 if recovered else 1)
    if recovered:
        previous = verify(repo, result['templateRun'])
        assert previous['pid'] != proof['pid'] and previous['owner'] == owner
        original = load(repo / f"run-quest-client-solo-{result['templateRun']}/saves/QuestValidation/playerdata/{owner}.dat")
        assert knowledge(original) == knowledge(proof['loaded']) == state
        assert proof['loaded']['Inventory'] == original['Inventory'] == []
        assert native['neoforge:attachments'][CHECKPOINT]['generation'] > original['neoforge:attachments'][CHECKPOINT]['generation']
    else:
        assert knowledge(proof['before'])['holocrons'] == [] and knowledge(proof['before'])['revision'] == 0
        assert any(line['key'] == 'quest.legacyoftheforce.find_echo' for line in client['emptyLines'])
        x, y, z = proof['holocron']
        region = ilum.read_chunk(world / 'region', x // 16, z // 16)
        start = region['structures']['starts']['legacyoftheforce:echo_ruin']
        assert start['Children'] == proof['site']['Children']
        assert ilum.block(region, x, y, z) == 'legacyoftheforce:ilum_holocron'
        px, _, pz = saved['Pos']
        assert (px - x - .5) ** 2 + (pz - z - .5) ** 2 > 256
    log = (repo / f'.tools/quest-solo-{run}-console.log').read_text(encoding='utf-8-sig')
    assert 'QUEST_SOLO_SERVER_PASS' in log and 'QUEST_SOLO_CLIENT_PASS' in log and 'All dimensions are saved' in log
    assert 'QUEST_SOLO_CLIENT_FAILED' not in log and 'QUEST_SOLO_SERVER_FAILED' not in log
    return {'pid': proof['pid'], 'owner': owner, 'recovered': recovered, 'embeddedOwnerPreserved': True,
            'knowledgeRevision': 1, 'nativeUsePackets': client['itemUses'], 'journalCommands': client['commands'],
            'nativeFramebuffers': frames(profile, recovered), 'rpgRewards': 0}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run')
    args = parser.parse_args()
    assert 1 <= len(args.run) <= 12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.run), indent=2))
