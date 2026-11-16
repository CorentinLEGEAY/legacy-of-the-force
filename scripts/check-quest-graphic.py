"""Check saved personal knowledge after the partial two-client graphical test; does not certify rendering."""
import argparse
import hashlib
import importlib.util
import json
import uuid
from pathlib import Path
from read_nbt import load
spec = importlib.util.spec_from_file_location('ilum', Path(__file__).with_name('check-ilum-worlds.py'))
ilum = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ilum)

def verify(repo, run):
    root = repo / f'run-quests-{run}'
    states = []
    for index, name in enumerate(('QuestViewer', 'QuestReader')):
        owner = uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + name).encode()).digest(), version=3)
        saved = load(root / f'world/playerdata/{owner}.dat')
        state = saved['neoforge:attachments']['legacyoftheforce:quest_knowledge']
        assert state['schemaVersion'] == 1 and state['visitedIlum'] == 0 and 'crystal' not in state
        assert state['revision'] == (1 if index == 0 else 0)
        assert state['holocrons'] == (['legacyoftheforce:echo_of_ilum'] if index == 0 else [])
        assert not saved['Inventory']
        rpg = saved['neoforge:attachments']['legacyoftheforce:player_progress']
        assert rpg['totalXpEarned'] == rpg['alignmentTenths'] == 0
        states.append({'player': name, 'revision': state['revision'], 'knownHolocrons': len(state['holocrons'])})
        if index == 0:
            x, _, z = saved['Pos']
            native = ilum.read_chunk(root / 'world/region', int(x) // 16, int(z) // 16)
            start = native['structures']['starts']['legacyoftheforce:echo_ruin']
            bx, by, bz, _, _, _ = start['Children'][0]['BB']
            assert ilum.block(native, bx + 3, by + 2, bz + 4) == 'legacyoftheforce:ilum_holocron'
    log = (root / 'logs/latest.log').read_text(encoding='utf-8-sig')
    assert all(f'{name}[/127.0.0.1:' in log for name in ('QuestViewer', 'QuestReader'))
    assert 'All dimensions are saved' in log
    return {'personalSavedStates': states, 'serverStoppedNormally': True, 'secondClientRenderingValidated': False}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run')
    args = parser.parse_args()
    assert 1 <= len(args.run) <= 12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.run), indent=2))
