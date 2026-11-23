"""Verify an actual native storage kill and offline recovery without trusting fixture assertions."""
import argparse
import importlib.util
import json
from pathlib import Path
from PIL import Image
from read_nbt import load

spec=importlib.util.spec_from_file_location('storage',Path(__file__).with_name('check-saber-storage-network.py'))
s=importlib.util.module_from_spec(spec);spec.loader.exec_module(s)


def region_endpoint(world,be):
    x,y,z=be['x'],be['y'],be['z'];chunk=s.prep.ilum.read_chunk(world/'region',x>>4,z>>4)
    return next(value for value in chunk['block_entities'] if (value['x'],value['y'],value['z'])==(x,y,z))


def verify(repo,crash,recovery):
    source=repo/f'run-adventure-{crash}/world';world=repo/f'run-adventure-{recovery}/world'
    point=load(source/'validation/saber-storage-checkpoint.nbt');proof=load(world/'validation/adventure-saberstoragecheckpointrecover-server.nbt')
    a=json.loads((repo/f'.tools/adventure-saberstoragecrash-{crash}-result.json').read_text(encoding='utf-8-sig'))
    b=json.loads((repo/f'.tools/adventure-saberstoragecheckpointrecover-{recovery}-result.json').read_text(encoding='utf-8-sig'))
    assert a['observed'] and a['killed'] and a['killedPid']==point['pid'] and a['serverExitCode']!=0 and a['killUtc']
    assert b['observed'] and not b['killed'] and b['serverExitCode']==0
    assert point['schemaVersion']==proof['schemaVersion']==1 and proof['crashedPid']==point['pid'] and proof['pid']!=point['pid']
    assert Path(point['world']).resolve()==source.resolve() and proof['stage']==point['stage']
    stage=point['stage'];paid=stage!='PREPARED';assert stage in ('PREPARED','PLAYER_DURABLE','STORAGE_DURABLE','CLOSED')
    assert (source/'validation/saber-storage-checkpoint.nbt').read_bytes()==(world/'validation/saber-storage-checkpoint.nbt').read_bytes()
    operation=s.prep.ilum.uid(point['operation']);journal=load(source/f'data/legacyoftheforce/saber_storage/{operation}.nbt')
    assert journal==point['journal'] and journal['phase']=={'PREPARED':'PREPARED','PLAYER_DURABLE':'PREPARED','STORAGE_DURABLE':'HELD','CLOSED':'CLOSED'}[stage]
    payload=journal['payload'];assert payload['actor']==point['actor']==point['before'][0]['UUID']
    assert payload['receipt']=={'schemaVersion':1,'revision':4,'operation':point['operation']}
    assert len(payload['containers'])==2 and len(point['nativeStorage'])==len(proof['nativeStorage'])==3
    target=point['saber'];previous=load(source/'validation/adventure-saberstorage-server.nbt')
    original_items=[e for image in previous['storage'] for e in image['items']]
    held=[e for e in original_items if s.saber(e)==target];assert len(held)==1 and held[0]['count']==1
    assert payload['schemaVersion']==1 and payload['dimension']=='minecraft:overworld'
    assert s.stock(s.items(payload['beforePlayer']))==s.stock(s.items(point['before'][0]))
    assert s.stock(s.items(payload['afterPlayer']))==s.stock(s.items(point['before'][0]))+s.stock(held)
    originals={image['pos']:image for image in previous['storage']}
    for image in payload['containers']:
        original=originals[image['pos']]
        assert image['identity']==original['identity'] and image['type']==original['type'] and image['items']==original['items']
        assert image['after']==[e for e in original['items'] if s.saber(e)!=target]
    native_target=[e for be in point['nativeStorage'] for e in be.get('Items',[]) if s.saber(e)==target]
    assert len(native_target)==(1 if stage in ('PREPARED','PLAYER_DURABLE') else 0)
    for be in point['nativeStorage']:assert region_endpoint(source,be)==be
    for be,expected in zip(proof['nativeStorage'],previous['storage']):
        assert region_endpoint(world,be)==be
        assert be['id']==expected['type'] and be['NeoForgeData'][s.MOD+'saber_storage']=={'schemaVersion':1,'id':expected['identity']}
        assert (be['x'],be['y'],be['z'])==s.xyz(expected['pos'])
        assert be.get('Items',[])==[e for e in expected['items'] if not (paid and s.saber(e)==target)]
    pids={point['pid'],proof['pid']};all_after=[]
    for role in range(2):
        initial,native_before,after=point['before'][role],point['players'][role],proof['players'][role];owner=s.prep.ilum.uid(initial['UUID'])
        assert load(source/f'playerdata/{owner}.dat')==native_before
        assert s.revision(native_before)==s.revision(after)==(4 if paid and role==0 else 3)
        actual=load(world/f'playerdata/{owner}.dat');assert actual['Inventory']==after['Inventory'] and actual[s.MENU]==after[s.MENU]
        assert actual['neoforge:attachments'][s.RECEIPT]==after['neoforge:attachments'][s.RECEIPT]
        expected=s.stock(s.items(initial))+(s.stock(held) if paid and role==0 else s.Counter())
        assert s.stock(s.items(native_before))==s.stock(s.items(after))==expected
        assert initial['neoforge:attachments'][s.MOD+'player_progress']==after['neoforge:attachments'][s.MOD+'player_progress']
        all_after+=s.items(after)
        for run,result,artifact in ((crash,a,'saberstoragecrash'),(recovery,b,'saberstoragecheckpointrecover')):
            item=result['clients'][role];folder=repo/f'run-adventure-client-{"one" if role==0 else "two"}-{run}';client=load(folder/f'validation/adventure-{artifact}-client.nbt')
            assert item['exitCode']==0 and not item['forced'] and client['pid']==item['pid'] and client['pid'] not in pids;pids.add(client['pid'])
            assert client['schemaVersion']==1 and client['role']==role and client['observation']['owner']==initial['UUID']
            assert bool(client['disconnectObserved'])==(run==crash) and bool(client['sentWithdrawal'])==(run==crash and role==0)
            if run==recovery:
                assert s.stock(client['observation']['Inventory']+client['observation']['menu']['items'])==expected
                assert len(client['observation']['storage'])==(0 if paid and role==0 else 1)
            log=(folder/'logs/latest.log').read_text(encoding='utf-8-sig');assert 'Development window display=3' in log and f'ADVENTURE_{artifact.upper()}_CLIENT_PASS' in log
            with Image.open(folder/'screenshots/saber-storage-checkpoint-observed.png') as image:assert min(image.size)>=400
    all_after += [e for be in proof['nativeStorage'] for e in be.get('Items',[])]
    sabers=[e for e in all_after if e['id']==s.MOD+'lightsaber'];assert len(sabers)==4 and len({s.prep.ilum.uid(s.saber(e)) for e in sabers})==4
    assert s.stock(all_after)==s.stock([e for player in point['before'] for e in s.items(player)]+original_items)
    assert not list((world/'data/legacyoftheforce/saber_storage').glob('*.nbt'))
    assert not any(e['id']=='minecraft:item' and e.get('Item',{}).get('id')==s.MOD+'lightsaber' for e in s.prep.entities(world))
    return {'stage':stage,'paid':paid,'crashedPid':point['pid'],'recoveryPid':proof['pid'],'uniqueSabers':4,'nativeStorageAndPlayers':True,'screen3':True}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('crash');parser.add_argument('recovery');args=parser.parse_args()
    for value in (args.crash,args.recovery):assert 1<=len(value)<=12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in value)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent,args.crash,args.recovery),indent=2))
