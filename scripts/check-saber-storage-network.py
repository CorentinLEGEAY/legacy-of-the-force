"""Reconcile two real storage clients, server receipts and native player/region files."""
import argparse
from collections import Counter
import importlib.util
import json
from pathlib import Path
from PIL import Image
from read_nbt import load

spec=importlib.util.spec_from_file_location('prep',Path(__file__).with_name('check-adventure-preparation.py'))
prep=importlib.util.module_from_spec(spec);spec.loader.exec_module(prep)
MOD=prep.MOD
MENU=MOD+'personal_menu'
RECEIPT=MOD+'saber_storage_receipt'


def items(player):
    return player['Inventory']+player.get(MENU,{'items':[]})['items']


def stock(entries):
    result=Counter()
    for entry in entries:
        key=json.dumps({k:v for k,v in entry.items() if k not in ('Slot','slot','count')},sort_keys=True)
        result[key]+=entry['count']
    return result


def saber(entry):
    return entry.get('components',{}).get(MOD+'saber_assembly',{}).get('saberId')


def revision(player):
    return player.get('neoforge:attachments',{}).get(RECEIPT,{'revision':0})['revision']


def xyz(packed):
    def signed(value,bits):
        return value-(1<<bits) if value&(1<<(bits-1)) else value
    return signed((packed>>38)&0x3ffffff,26),signed(packed&0xfff,12),signed((packed>>12)&0x3ffffff,26)


def native_storage(world,image):
    x,y,z=xyz(image['pos']);chunk=prep.ilum.read_chunk(world/'region',x>>4,z>>4)
    result=next(be for be in chunk['block_entities'] if (be['x'],be['y'],be['z'])==(x,y,z))
    assert result['id']==image['type']
    marker=result['NeoForgeData'][MOD+'saber_storage']
    assert marker['schemaVersion']==1 and marker['id']==image['identity']
    assert result.get('Items',[])==image['items']
    return result.get('Items',[])


def verify(repo,run,source,recovery=False):
    artifact='saberstoragerecover' if recovery else 'saberstorage'
    world=repo/f'run-adventure-{run}/world';origin=repo/f'run-adventure-{source}/world'
    proof=load(world/f'validation/adventure-{artifact}-server.nbt')
    result=json.loads((repo/f'.tools/adventure-{artifact}-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert result['observed'] and result['serverExitCode']==0 and not result['killed']
    assert proof['schemaVersion']==1 and bool(proof['recovery'])==recovery
    assert len(proof['before'])==len(proof['after'])==len(proof['targets'])==len(result['clients'])==2
    assert len(proof['storage'])==3 and len({prep.ilum.uid(i['identity']) for i in proof['storage']})==3
    stored=[entry for image in proof['storage'] for entry in native_storage(world,image)]
    assert len(stored)==2 and all(e['id']==MOD+'lightsaber' and e['count']==1 for e in stored)
    assert not list((world/'data/legacyoftheforce/saber_storage').glob('*.nbt'))
    assert not list((world/'data/legacyoftheforce/saber_transfers').glob('*.nbt'))
    ids=set();pids={proof['pid']};base=3 if recovery else 0
    original_storage=load(origin/'validation/adventure-saberstorage-server.nbt')['storage'] if recovery else []
    for role,item in enumerate(result['clients']):
        before,after=proof['before'][role],proof['after'][role];owner=prep.ilum.uid(before['UUID']);target=proof['targets'][role]['saber']
        assert proof['targets'][role]['owner']==before['UUID']==after['UUID']
        assert revision(before)==base and revision(after)==(5 if recovery else 3)
        native=load(world/f'playerdata/{owner}.dat');original=load(origin/f'playerdata/{owner}.dat')
        assert native['Inventory']==after['Inventory'] and native[MENU]==after[MENU]
        assert native['neoforge:attachments'][RECEIPT]==after['neoforge:attachments'][RECEIPT]
        assert stock(items(original))==stock(items(before))
        held=[e for e in stored if saber(e)==target];assert len(held)==1
        assert stock(items(before))+(stock(held) if recovery else Counter())==stock(items(after))+stock(held)
        assert before['neoforge:attachments'][MOD+'player_progress']==after['neoforge:attachments'][MOD+'player_progress']
        for entry in items(after)+held:
            if entry['id']==MOD+'lightsaber':
                assert entry['count']==1 and saber(entry) is not None
                uid=prep.ilum.uid(saber(entry));assert uid not in ids;ids.add(uid)
        folder=repo/f'run-adventure-client-{"one" if role==0 else "two"}-{run}'
        client=load(folder/f'validation/adventure-{artifact}-client.nbt')
        assert item['name']==['AdventureOne','AdventureTwo'][role] and item['exitCode']==0 and not item['forced']
        assert client['pid']==item['pid'] and client['pid'] not in pids;pids.add(client['pid'])
        assert client['schemaVersion']==1 and client['role']==role and prep.ilum.uid(client['owner'])==owner and client['target']==target
        assert stock(client['Inventory']+client['menu']['items'])==stock(items(after))
        phases=['opened','withdrawn','stored','closed'] if recovery else ['opened','deposited','withdrawn','stored','closed']
        assert [f['phase'] for f in client['frames']]==phases
        server_frames=[f for f in proof['frames'] if prep.ilum.uid(f['owner'])==owner]
        assert [f['phase'] for f in server_frames]==phases
        expected_revisions=[3,4,5,5] if recovery else [0,1,2,3,3]
        aggregate=stock(items(before))+(stock(held) if recovery else Counter())
        for frame,server_frame,expected_revision in zip(client['frames'],server_frames,expected_revisions):
            assert revision(server_frame['player'])==expected_revision
            assert stock(frame['Inventory']+frame['menu']['items'])==stock(items(server_frame['player']))
            storage=[e for i in server_frame['storage'] for e in i['items']]
            assert stock(frame.get('storage',[]))==stock(storage)
            if frame['phase']!='closed':
                assert frame['rows']==(6 if role==0 else 3)
                assert stock(items(server_frame['player']))+stock(storage)==aggregate
            with Image.open(folder/f"screenshots/saber-storage-{frame['phase']}.png") as image:
                assert min(image.size)>=400 and len(image.getcolors(image.width*image.height))>100
        log=(folder/'logs/latest.log').read_text(encoding='utf-8-sig')
        assert 'Development window display=3' in log and f'ADVENTURE_{artifact.upper()}_CLIENT_PASS' in log
        assert 'CLIENT_FAILED' not in log
    assert len(ids)==4 and len(pids)==3
    if recovery:
        assert proof['storage']==original_storage, 'Same stored targets and endpoint identities must survive restart and round trip'
    assert not any(e['id']=='minecraft:item' and e.get('Item',{}).get('id')==MOD+'lightsaber' for e in prep.entities(world))
    return {'run':run,'recovery':recovery,'pids':sorted(pids),'uniqueSabers':len(ids),'storedSabers':len(stored),'finalRevisions':[revision(p) for p in proof['after']],'nativeRegionsAndPlayers':True,'screen3':True}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('run');parser.add_argument('--source',required=True);parser.add_argument('--recovery',action='store_true');args=parser.parse_args()
    for value in (args.run,args.source):assert 1<=len(value)<=12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in value)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent,args.run,args.source,args.recovery),indent=2))
