"""Cross-check actual Windows refusal, real owner logout, online peer and optional fresh JVM recovery."""
import argparse
from collections import Counter
import importlib.util
import json
from pathlib import Path
import re
from read_nbt import load

spec=importlib.util.spec_from_file_location('prep',Path(__file__).with_name('check-adventure-preparation.py'))
prep=importlib.util.module_from_spec(spec);spec.loader.exec_module(prep)
uid,MOD=prep.ilum.uid,prep.MOD


def receipt(actor):
    return actor['neoforge:attachments'].get(MOD+'saber_transfer_receipt',{'schemaVersion':1,'revision':0})


def stock(items):
    return Counter(json.dumps({k:v for k,v in s.items() if k!='Slot'},sort_keys=True) for s in items)


def identity(stack):
    return stack.get('components',{}).get(MOD+'saber_assembly',{}).get('saberId')


def result(repo,mode,run):
    value=json.loads((repo/f'.tools/adventure-{mode}-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert value['observed'] and not value['killed'] and value['serverExitCode']==0
    assert len(value['clients'])==2 and all(c['exitCode']==0 and not c['forced'] for c in value['clients'])
    return value


def verify(repo,run,recovery):
    world=repo/f'run-adventure-{run}/world';proof=load(world/'validation/adventure-saberio-server.nbt')
    state=load(world/'validation/saber-transfer-checkpoint.nbt');native=result(repo,'saberio',run)
    assert proof['schemaVersion']==state['schemaVersion']==1 and Path(proof['world']).resolve()==world.resolve()
    assert state==proof['checkpoint'] and state['pid']==proof['pid'] and state['direction']=='DROP'
    boundary=proof['boundary'];assert boundary==state['stage'] and boundary in ('PREPARED','PAYMENT_DURABLE','HELD','CLOSED')
    paid=boundary!='PREPARED';entity=uid(state['entity']);saber=state['saber'];operation=state['operation'];item=state['journal']['payload']['entity']['Item']
    workshop=load(world/'validation/adventure-saberworkshop-server.nbt')
    assert workshop['pid']!=proof['pid'] and len(workshop['after'])==2
    assert identity(item)==saber and state['journal']['payload']['receipt']=={'schemaVersion':1,'revision':1,'operation':operation}
    assert state['journal']['phase']==('PREPARED' if boundary in ('PREPARED','PAYMENT_DURABLE') else boundary)
    assert proof['logoutUnderLock'] and re.match(r'java\.nio\.file\.(AccessDeniedException|FileSystemException): ',proof['nativeWriteDenial'])
    assert not list((world/'data/legacyoftheforce/saber_transfers').glob('*.nbt'))
    pids={proof['pid']};all_ids=[]
    assert len(proof['before'])==len(proof['after'])==2
    for role,(before,after) in enumerate(zip(proof['before'],proof['after'])):
        assert before['Inventory']==workshop['after'][role]['Inventory']
        owner=uid(before['UUID']);assert uid(after['UUID'])==owner
        saved=load(world/f'playerdata/{owner}.dat');assert saved['Inventory']==after['Inventory'] and receipt(saved)==receipt(after)
        assert after['neoforge:attachments'][MOD+'player_progress']==before['neoforge:attachments'][MOD+'player_progress']
        expected=stock(after['Inventory'])
        if role==0 and paid:expected+=stock([item])
        assert stock(before['Inventory'])==expected
        for s in after['Inventory']:
            if s['id']==MOD+'lightsaber':assert s['count']==1;all_ids.append(uid(identity(s)))
        folder=repo/f'run-adventure-client-{["one","two"][role]}-{run}';entry=native['clients'][role]
        assert Path(entry['profile']).resolve()==folder.resolve()
        client=load(folder/'validation/adventure-saberio-client.nbt');prefix=load(folder/'validation/saber-io-prefix.nbt')
        assert client['schemaVersion']==1 and client['pid']==prefix['pid']==entry['pid'] and client['pid'] not in pids
        pids.add(client['pid']);assert client['role']==role and uid(client['owner'])==owner
        assert prefix['Inventory']==before['Inventory']
        if role==0:
            french=json.loads((repo/'src/main/resources/assets/legacyoftheforce/lang/fr_fr.json').read_text(encoding='utf-8'))
            assert client['disconnectObserved'] and client['disconnectReason']==french['saber.legacyoftheforce.transfer_pending']
            assert proof['logout']['Inventory']==after['Inventory']==state['savedActor']['Inventory']
            assert receipt(after)==receipt(proof['logout'])==({'schemaVersion':1,'revision':1,'operation':operation} if paid else {'schemaVersion':1,'revision':0})
        else:
            assert not client['disconnectObserved'] and not client['disconnectReason']
            assert client['Inventory']==client['observation']['Inventory']==before['Inventory']==after['Inventory']
            assert client['observation']['entityPresent']==paid
            if paid:assert uid(client['observation']['entity'])==entity and client['observation']['Item']==item
            assert (folder/'screenshots/saber-io-recovered.png').read_bytes()[:8]==b'\x89PNG\r\n\x1a\n'
        log=(folder/'logs/latest.log').read_text(encoding='utf-8');assert 'ADVENTURE_SABERIO_CLIENT_PASS' in log and 'ADVENTURE_SABERIO_CLIENT_FAILED' not in log
        assert 'Development window display=3 ' in log
    entities=[e for e in prep.entities(world) if e['id']=='minecraft:item' and identity(e['Item'])==saber]
    assert len(entities)==int(paid)
    if paid:
        assert uid(entities[0]['UUID'])==entity and entities[0]['Item']==proof['entity']['Item']==item
        all_ids.append(uid(saber))
    assert len(all_ids)==len(set(all_ids))==4
    log=(world.parent/'logs/latest.log').read_text(encoding='utf-8')
    for marker in ('SABER_NATIVE_IO_LOCKED','Saber transfer pending; player disconnected','SABER_NATIVE_IO_LOGOUT_OBSERVED','SABER_NATIVE_IO_LOGOUT_FINISHED','ADVENTURE_SABERIO_SERVER_PASS','All dimensions are saved'):assert marker in log
    assert 'ADVENTURE_SABERIO_SERVER_FAILED' not in log
    if recovery:
        new_world=repo/f'run-adventure-{recovery}/world';resumed=result(repo,'sabercheckpointrecover',recovery)
        new=load(new_world/'validation/adventure-sabercheckpointrecover-server.nbt')
        assert new['crashedPid']==proof['pid'] and new['pid'] not in pids and new['stage']==boundary
        assert (new_world/'validation/saber-transfer-checkpoint.nbt').read_bytes()==(world/'validation/saber-transfer-checkpoint.nbt').read_bytes()
        pids.add(new['pid']);assert new['entityPresent']==paid
        for role,(before,after) in enumerate(zip(new['before'],new['after'])):
            source=proof['after'][role];assert before['Inventory']==after['Inventory']==source['Inventory']
            assert receipt(before)==receipt(after)==receipt(source)
            owner=uid(source['UUID']);saved=load(new_world/f'playerdata/{owner}.dat')
            assert uid(before['UUID'])==uid(after['UUID'])==owner and saved['Inventory']==after['Inventory'] and receipt(saved)==receipt(after)
            assert before['neoforge:attachments'][MOD+'player_progress']==after['neoforge:attachments'][MOD+'player_progress']==source['neoforge:attachments'][MOD+'player_progress']
            folder=repo/f'run-adventure-client-{["one","two"][role]}-{recovery}';client=load(folder/'validation/adventure-sabercheckpointrecover-client.nbt')
            assert client['pid']==resumed['clients'][role]['pid'] and client['pid'] not in pids;pids.add(client['pid'])
            assert client['role']==role and uid(client['owner'])==owner
            assert client['Inventory']==client['observation']['Inventory']==after['Inventory'] and client['observation']['entityPresent']==paid
            if paid:assert uid(client['observation']['entity'])==entity and client['observation']['Item']==item
            assert (folder/'screenshots/saber-checkpoint-recovered.png').read_bytes()[:8]==b'\x89PNG\r\n\x1a\n'
            log=(folder/'logs/latest.log').read_text(encoding='utf-8');assert 'ADVENTURE_SABERCHECKPOINTRECOVER_CLIENT_PASS' in log and 'ADVENTURE_SABERCHECKPOINTRECOVER_CLIENT_FAILED' not in log and 'Development window display=3 ' in log
        entries=[e for e in prep.entities(new_world) if e['id']=='minecraft:item' and identity(e['Item'])==saber]
        assert len(entries)==int(paid)
        if paid:assert uid(entries[0]['UUID'])==entity and entries[0]['Item']==item
        assert not list((new_world/'data/legacyoftheforce/saber_transfers').glob('*.nbt'))
        log=(new_world.parent/'logs/latest.log').read_text(encoding='utf-8');assert 'ADVENTURE_SABERCHECKPOINTRECOVER_SERVER_PASS' in log and 'All dimensions are saved' in log
    return {'run':run,'boundary':boundary,'nativeProcesses':sorted(pids),'nativeWindowsDenial':True,'productionOwnerDisconnect':True,'nativeLogoutUnderLock':True,'continuousNativePeer':True,'durableCopiesAfterRecovery':1,'uniqueSaberIds':4,'freshJvmRecovery':recovery,'serverKilled':False}


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('run');parser.add_argument('--recovery');args=parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parents[1],args.run,args.recovery),indent=2))
