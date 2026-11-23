"""Verify actual Windows sharing refusal proof against native playerdata and entity regions."""
import argparse
import importlib.util
import json
from pathlib import Path
import re
from read_nbt import load

spec=importlib.util.spec_from_file_location('prep',Path(__file__).with_name('check-adventure-preparation.py'))
prep=importlib.util.module_from_spec(spec)
spec.loader.exec_module(prep)
MOD,uid=prep.MOD,prep.ilum.uid


def receipt(actor):
    return actor['neoforge:attachments'].get(MOD+'saber_transfer_receipt',{'schemaVersion':1,'revision':0})


def item_identity(stack):
    return stack.get('components',{}).get(MOD+'saber_assembly',{}).get('saberId')


def without_slot(stack):
    return {k:v for k,v in stack.items() if k!='Slot'}


def verify(log_path):
    log=log_path.read_text(encoding='utf-8-sig')
    matches=re.findall(r'SABER_WINDOWS_IO_PASS cases=9 proof=([^\r\n]+)',log)
    assert len(matches)==1 and 'BUILD SUCCESSFUL' in log
    proof_path=Path(matches[0].strip());proof=load(proof_path);world=Path(proof['world']).resolve()
    assert proof_path.resolve().is_relative_to(world/'validation/saber-windows-io')
    assert proof['schemaVersion']==1 and proof['platform'].startswith('Windows') and not proof['nativeSockets']
    expected={('DROP','BEFORE_IMAGE')}|{(d,b) for d in ('DROP','PICKUP') for b in ('PREPARED','PAYMENT_DURABLE','HELD','CLOSED')}
    assert len(proof['cases'])==9 and {(c['direction'],c['boundary']) for c in proof['cases']}==expected
    entities=list(prep.entities(world));ids=set();actors=set();results=[]
    stages=['PREPARED','PAYMENT_DURABLE','HELD','ENTITY_DURABLE','CLOSED']
    for case in proof['cases']:
        direction,boundary=case['direction'],case['boundary'];drop=direction=='DROP';initial=boundary=='BEFORE_IMAGE'
        paid=boundary in ('PAYMENT_DURABLE','HELD','CLOSED');world_copy=drop==paid
        actor=uid(case['actor']);entity=uid(case['entity']);identity=case['saber'];assert actor not in actors and uid(identity) not in ids
        actors.add(actor);ids.add(uid(identity));item=case['item'];assert item_identity(item)==identity and item['count']==1
        assert case['lockedFileUnchanged'] and case['secondCoordinatorUnchanged'] and case['pendingEmpty']
        assert re.match(r'java\.nio\.file\.(AccessDeniedException|FileSystemException): ',case['nativeWriteDenial'])
        journal=Path(case['journal']).resolve();assert journal.is_relative_to(proof_path.parent)
        assert not list(journal.glob('*.nbt'))
        locked=Path(case['lockedFile']).resolve()
        assert locked==(world/f'playerdata/{actor}.dat' if boundary in ('BEFORE_IMAGE','PREPARED') else journal/(uid(case['operation'])+'.nbt'))
        expected_stages=[] if initial else stages[:1 if boundary=='PREPARED' else 2 if boundary=='PAYMENT_DURABLE' else 4 if boundary=='HELD' else 5]
        assert case['stages']==expected_stages
        if not initial:
            during=case['journalDuring'];assert uid(during['operationId'])==uid(case['operation'])
            assert during['phase']==('PREPARED' if boundary in ('PREPARED','PAYMENT_DURABLE') else boundary)
            assert during['payload']['direction']==direction and during['payload']['saberId']==identity
        assert receipt(case['before'])['revision']==0 and receipt(case['during'])['revision']==int(paid)
        if paid:assert receipt(case['during'])['operation']==case['operation']
        assert case['during']==case['after']
        native=load(world/f'playerdata/{actor}.dat');assert native==case['after']
        inventory=[s for s in native['Inventory'] if item_identity(s)==identity]
        assert len(inventory)==(0 if world_copy else 1)
        if inventory:assert without_slot(inventory[0])==item
        saved=[e for e in entities if e['id']=='minecraft:item' and item_identity(e['Item'])==identity]
        assert len(saved)==len(case['storedAfter'])==int(world_copy)
        if saved:
            assert uid(saved[0]['UUID'])==entity and saved[0]['Item']==case['storedAfter'][0]['Item']==item
        assert len(inventory)+len(saved)==1
        expected_during_world=not drop if boundary in ('BEFORE_IMAGE','PREPARED','PAYMENT_DURABLE') else drop
        assert len(case['storedDuring'])==int(expected_during_world)
        results.append({'direction':direction,'boundary':boundary,'nativeWriteDenied':True,'receiptRevision':int(paid),'durableCopiesAfterRecovery':1,'secondCoordinatorUnchanged':True})
    return {'proof':str(proof_path),'nativeServerPid':proof['pid'],'platform':proof['platform'],'cases':results,'nativeInventoryAndRegionsVerified':True,'nativeSocketsCertified':False}


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('log',type=Path);args=parser.parse_args()
    print(json.dumps(verify(args.log),indent=2))
