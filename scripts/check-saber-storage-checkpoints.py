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


def verify(repo,crash,recovery,deposit=False,io=False):
    source=repo/f'run-adventure-{crash}/world';world=repo/f'run-adventure-{recovery}/world'
    checkpoint='saber-storage-deposit-checkpoint.nbt' if deposit else 'saber-storage-checkpoint.nbt'
    point=load(source/'validation'/checkpoint);proof=load(world/'validation/adventure-saberstoragecheckpointrecover-server.nbt')
    assert bool(point.get('deposit',False))==deposit
    first_artifact='saberstorageio' if io else 'saberstoragecrash'
    a=json.loads((repo/f'.tools/adventure-{first_artifact}-{crash}-result.json').read_text(encoding='utf-8-sig'))
    b=json.loads((repo/f'.tools/adventure-saberstoragecheckpointrecover-{recovery}-result.json').read_text(encoding='utf-8-sig'))
    assert a['observed']
    if io:assert not a['killed'] and a['serverExitCode']==0 and not a['killUtc']
    else:assert a['killed'] and a['killedPid']==point['pid'] and a['serverExitCode']!=0 and a['killUtc']
    assert b['observed'] and not b['killed'] and b['serverExitCode']==0
    assert point['schemaVersion']==proof['schemaVersion']==1 and proof['crashedPid']==point['pid'] and proof['pid']!=point['pid']
    assert Path(point['world']).resolve()==source.resolve() and proof['stage']==point['stage']
    stage=point['stage'];paid=stage!='PREPARED';assert stage in ('PREPARED','PLAYER_DURABLE','STORAGE_DURABLE','CLOSED')
    assert (source/'validation'/checkpoint).read_bytes()==(world/'validation'/checkpoint).read_bytes()
    if deposit:assert (source/'validation/saber-storage-checkpoint.nbt').read_bytes()==(world/'validation/saber-storage-checkpoint.nbt').read_bytes()
    io_proof=load(source/'validation/adventure-saberstorageio-server.nbt') if io else None
    operation=s.prep.ilum.uid(point['operation']);journal=point['journal'] if io else load(source/f'data/legacyoftheforce/saber_storage/{operation}.nbt')
    assert journal==point['journal'] and journal['phase']=={'PREPARED':'PREPARED','PLAYER_DURABLE':'PREPARED','STORAGE_DURABLE':'HELD','CLOSED':'CLOSED'}[stage]
    payload=journal['payload'];assert payload['actor']==point['actor']==point['before'][0]['UUID']
    assert payload['receipt']=={'schemaVersion':1,'revision':5 if deposit else 4,'operation':point['operation']}
    assert len(payload['containers'])==2 and len(point['nativeStorage'])==len(proof['nativeStorage'])==3
    target=point['saber'];previous=load(source/'validation/adventure-saberstorage-server.nbt')
    old_items=[e for image in previous['storage'] for e in image['items']]
    held=[e for e in old_items if s.saber(e)==target];assert len(held)==1 and held[0]['count']==1
    baseline=previous['storage']
    if deposit:
        assert len(point['initialStorage'])==3
        baseline=[]
        for original,be in zip(previous['storage'],point['initialStorage']):
            assert (be['x'],be['y'],be['z'])==s.xyz(original['pos']) and be['id']==original['type']
            assert be['NeoForgeData'][s.MOD+'saber_storage']=={'schemaVersion':1,'id':original['identity']}
            baseline.append({**original,'items':be.get('Items',[])})
    original_items=[e for image in baseline for e in image['items']]
    assert s.stock([e for player in point['before'] for e in s.items(player)]+original_items)==s.stock([e for player in previous['after'] for e in s.items(player)]+old_items)
    assert payload['schemaVersion']==1 and payload['dimension']=='minecraft:overworld'
    assert s.stock(s.items(payload['beforePlayer']))==s.stock(s.items(point['before'][0]))
    expected_after=s.stock(s.items(point['before'][0]))
    expected_after=expected_after-s.stock(held) if deposit else expected_after+s.stock(held)
    assert s.stock(s.items(payload['afterPlayer']))==expected_after
    originals={image['pos']:image for image in baseline}
    for image in payload['containers']:
        original=originals[image['pos']]
        assert image['identity']==original['identity'] and image['type']==original['type'] and image['items']==original['items']
        assert [e for e in image['after'] if s.saber(e)!=target]==[e for e in original['items'] if s.saber(e)!=target]
    before_endpoints=s.stock([e for image in payload['containers'] for e in image['items']])
    after_endpoints=s.stock([e for image in payload['containers'] for e in image['after']])
    assert after_endpoints==(before_endpoints+s.stock(held) if deposit else before_endpoints-s.stock(held))
    native_target=[e for be in point['nativeStorage'] for e in be.get('Items',[]) if s.saber(e)==target]
    storage_durable=stage in ('STORAGE_DURABLE','CLOSED')
    assert len(native_target)==int(storage_durable if deposit else not storage_durable)
    if io:
        assert stage in ('PREPARED','PLAYER_DURABLE') and io_proof['checkpoint']==point and io_proof['pid']==point['pid']
        assert io_proof['logoutUnderLock'] and io_proof['boundary']==stage and bool(io_proof['deposit'])==deposit
        assert io_proof['lockedBeforeSha256']==io_proof['lockedAfterLogoutSha256'] and len(io_proof['lockedBeforeSha256'])==64
        assert 'FileSystemException' in io_proof['nativeWriteDenial'] or 'AccessDeniedException' in io_proof['nativeWriteDenial']
        assert s.revision(io_proof['logout'])==((4 if deposit else 3)+int(paid))
        if not paid:assert io_proof['logout']==point['players'][0]
        for be in io_proof['nativeStorage']:assert region_endpoint(source,be)==be
        log=(source.parent/'logs/latest.log').read_text(encoding='utf-8-sig')
        assert 'SABER_STORAGE_NATIVE_IO_LOCKED' in log and 'SABER_STORAGE_NATIVE_IO_LOGOUT_FINISHED' in log
        assert 'Saber storage transaction pending; disconnecting owner' in log and 'ADVENTURE_SABERSTORAGEIO_SERVER_PASS' in log
        if not paid:assert log.index('SABER_STORAGE_NATIVE_IO_LOCKED')<log.index('PlayerList.remove')<log.index('SABER_STORAGE_NATIVE_IO_LOGOUT_FINISHED')
        assert not list((source/'data/legacyoftheforce/saber_storage').glob('*.nbt'))
        assert not any(e['id']=='minecraft:item' and e.get('Item',{}).get('id')==s.MOD+'lightsaber' for e in s.prep.entities(source)), 'IO logout published an extra world saber'
    else:
        for be in point['nativeStorage']:assert region_endpoint(source,be)==be
    operations={image['pos']:image for image in payload['containers']}
    for be,expected in zip(proof['nativeStorage'],baseline):
        assert region_endpoint(world,be)==be
        assert be['id']==expected['type'] and be['NeoForgeData'][s.MOD+'saber_storage']=={'schemaVersion':1,'id':expected['identity']}
        assert (be['x'],be['y'],be['z'])==s.xyz(expected['pos'])
        endpoint=operations.get(expected['pos']);final_items=endpoint['after' if paid else 'items'] if endpoint else expected['items']
        assert be.get('Items',[])==final_items
        if io:
            previous_be=next(value for value in io_proof['nativeStorage'] if (value['x'],value['y'],value['z'])==(be['x'],be['y'],be['z']))
            assert previous_be.get('Items',[])==final_items and previous_be['NeoForgeData']==be['NeoForgeData']
    pids={point['pid'],proof['pid']};all_after=[]
    for role in range(2):
        initial,native_before,after=point['before'][role],point['players'][role],proof['players'][role];owner=s.prep.ilum.uid(initial['UUID'])
        saved_source=load(source/f'playerdata/{owner}.dat')
        if io:
            assert saved_source['Inventory']==io_proof['after'][role]['Inventory'] and saved_source[s.MENU]==io_proof['after'][role][s.MENU]
        else:assert saved_source==native_before
        assert s.revision(native_before)==s.revision(after)==((4 if deposit else 3)+int(paid) if role==0 else 3)
        expected_receipt=payload['receipt'] if paid and role==0 else initial['neoforge:attachments'][s.RECEIPT]
        assert native_before['neoforge:attachments'][s.RECEIPT]==after['neoforge:attachments'][s.RECEIPT]==saved_source['neoforge:attachments'][s.RECEIPT]==expected_receipt
        actual=load(world/f'playerdata/{owner}.dat');assert actual['Inventory']==after['Inventory'] and actual[s.MENU]==after[s.MENU]
        assert actual['neoforge:attachments'][s.RECEIPT]==after['neoforge:attachments'][s.RECEIPT]
        expected=s.stock(s.items(initial))
        if paid and role==0:expected=expected-s.stock(held) if deposit else expected+s.stock(held)
        assert s.stock(s.items(native_before))==s.stock(s.items(after))==expected
        if io:assert s.stock(s.items(saved_source))==s.stock(s.items(io_proof['after'][role]))==expected
        assert initial['neoforge:attachments'][s.MOD+'player_progress']==after['neoforge:attachments'][s.MOD+'player_progress']
        all_after+=s.items(after)
        for run,result,artifact in ((crash,a,first_artifact),(recovery,b,'saberstoragecheckpointrecover')):
            item=result['clients'][role];folder=repo/f'run-adventure-client-{"one" if role==0 else "two"}-{run}';client=load(folder/f'validation/adventure-{artifact}-client.nbt')
            assert item['exitCode']==0 and not item['forced'] and client['pid']==item['pid'] and client['pid'] not in pids;pids.add(client['pid'])
            assert client['schemaVersion']==1 and client['role']==role and client['observation']['owner']==initial['UUID']
            assert bool(client.get('deposit',False))==deposit and bool(client['disconnectObserved'])==(run==crash and (not io or role==0))
            assert bool(client['sentWithdrawal'])==(run==crash and role==0 and not deposit)
            assert bool(client.get('sentDeposit',False))==(run==crash and role==0 and deposit)
            if run==recovery:
                assert s.stock(client['observation']['Inventory']+client['observation']['menu']['items'])==expected
                assert len(client['observation']['storage'])==(int(paid) if deposit and role==0 else (0 if paid and role==0 else 1))
            if io and run==crash:
                assert s.stock(client['initialObservation']['Inventory']+client['initialObservation']['menu']['items'])==s.stock(s.items(initial))
                assert client['observations']==(1 if role==0 else 2)
                if role==0:
                    language=json.loads((repo/'src/main/resources/assets/legacyoftheforce/lang/fr_fr.json').read_text(encoding='utf-8'))
                    assert client['disconnectReason']==language['saber.legacyoftheforce.transfer_pending']
                else:
                    assert s.stock(client['observation']['Inventory']+client['observation']['menu']['items'])==expected
                    with Image.open(folder/'screenshots/saber-storage-io-recovered.png') as image:assert min(image.size)>=400
            log=(folder/'logs/latest.log').read_text(encoding='utf-8-sig');assert 'Development window display=3' in log and f'ADVENTURE_{artifact.upper()}_CLIENT_PASS' in log
            with Image.open(folder/'screenshots/saber-storage-checkpoint-observed.png') as image:assert min(image.size)>=400
    all_after += [e for be in proof['nativeStorage'] for e in be.get('Items',[])]
    sabers=[e for e in all_after if e['id']==s.MOD+'lightsaber'];assert len(sabers)==4 and len({s.prep.ilum.uid(s.saber(e)) for e in sabers})==4
    assert s.stock(all_after)==s.stock([e for player in point['before'] for e in s.items(player)]+original_items)
    assert not list((world/'data/legacyoftheforce/saber_storage').glob('*.nbt'))
    assert not any(e['id']=='minecraft:item' and e.get('Item',{}).get('id')==s.MOD+'lightsaber' for e in s.prep.entities(world))
    return {'direction':'DEPOSIT' if deposit else 'WITHDRAW','fault':'WINDOWS_IO' if io else 'KILL','stage':stage,'paid':paid,'previousPid':point['pid'],'recoveryPid':proof['pid'],'uniqueSabers':4,'nativeStorageAndPlayers':True,'screen3':True}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('crash');parser.add_argument('recovery');parser.add_argument('--deposit',action='store_true');parser.add_argument('--io',action='store_true');args=parser.parse_args()
    for value in (args.crash,args.recovery):assert 1<=len(value)<=12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in value)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent,args.crash,args.recovery,args.deposit,args.io),indent=2))
