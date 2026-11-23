"""Reconcile real JVM kills at transfer boundaries with native receipt/inventory/entity recovery."""
import argparse
from collections import Counter
import importlib.util
import json
from pathlib import Path
from read_nbt import load

spec=importlib.util.spec_from_file_location('prep',Path(__file__).with_name('check-adventure-preparation.py'))
prep=importlib.util.module_from_spec(spec)
spec.loader.exec_module(prep)
uid,MOD=prep.ilum.uid,prep.MOD
STAGES=['PREPARED','PAYMENT_DURABLE','HELD','ENTITY_DURABLE','CLOSED']


def receipt(actor):
    return actor['neoforge:attachments'].get(MOD+'saber_transfer_receipt',{'schemaVersion':1,'revision':0})


def stock(items):
    return Counter(json.dumps({k:v for k,v in item.items() if k!='Slot'},sort_keys=True) for item in items)


def saber_count(items,identity):
    return sum(item['count'] for item in items if item.get('components',{}).get(MOD+'saber_assembly',{}).get('saberId')==identity)


def verify(repo,crash,recovery):
    original=repo/f'run-adventure-{crash}/world'
    world=repo/f'run-adventure-{recovery}/world'
    point=load(original/'validation/saber-transfer-checkpoint.nbt')
    proof=load(world/'validation/adventure-sabercheckpointrecover-server.nbt')
    killed=json.loads((repo/f'.tools/adventure-sabercrash-{crash}-result.json').read_text(encoding='utf-8-sig'))
    resumed=json.loads((repo/f'.tools/adventure-sabercheckpointrecover-{recovery}-result.json').read_text(encoding='utf-8-sig'))
    assert point['schemaVersion']==proof['schemaVersion']==1 and point['stage'] in STAGES and point['direction']=='DROP'
    stage=point['stage'];paid=stage!='PREPARED';stored=stage in ('ENTITY_DURABLE','CLOSED')
    assert killed['observed'] and killed['killed'] and killed['killedPid']==point['pid'] and killed['serverExitCode']!=0 and killed['killUtc']
    assert resumed['observed'] and not resumed['killed'] and resumed['serverExitCode']==0
    assert proof['pid']!=point['pid'] and proof['crashedPid']==point['pid'] and proof['stage']==stage
    assert Path(point['world']).resolve()==original.resolve()
    assert (original/'validation/saber-transfer-checkpoint.nbt').read_bytes()==(world/'validation/saber-transfer-checkpoint.nbt').read_bytes()
    operation=uid(point['operation']);entity=uid(point['entity']);actor=uid(point['actor']);identity=point['saber']
    journal=load(original/f'data/legacyoftheforce/saber_transfers/{operation}.nbt')
    assert journal==point['journal'] and journal['schemaVersion']==1 and uid(journal['operationId'])==operation
    assert journal['phase']==('PREPARED' if stage in STAGES[:2] else 'CLOSED' if stage=='CLOSED' else 'HELD')
    payload=journal['payload'];expected_receipt=payload['receipt'];item=payload['entity']['Item']
    assert payload['schemaVersion']==1 and payload['direction']=='DROP' and payload['saberId']==identity
    assert uid(payload['actor'])==actor and uid(payload['entityId'])==entity and uid(payload['entity']['UUID'])==entity
    assert expected_receipt=={'schemaVersion':1,'revision':1,'operation':point['operation']}
    saved=load(original/f'playerdata/{actor}.dat')
    assert saved['Inventory']==point['savedActor']['Inventory'] and receipt(saved)==receipt(point['savedActor'])
    assert receipt(saved)==(expected_receipt if paid else {'schemaVersion':1,'revision':0})
    assert saber_count(saved['Inventory'],identity)==(0 if paid else 1)
    assert saber_count(point['liveActor']['Inventory'],identity)==0
    entities=[value for value in prep.entities(original) if uid(value['UUID'])==entity]
    assert len(entities)==len(point['storedEntities'])==(1 if stored else 0) and bool(point['liveEntityPresent'])==stored
    if stored:
        assert entities[0]==point['storedEntities'][0] and entities[0]['Item']==item
    assert len(proof['before'])==len(proof['after'])==len(killed['clients'])==len(resumed['clients'])==2
    assert proof['entityPresent']==paid
    final_entities=[value for value in prep.entities(world) if uid(value['UUID'])==entity]
    assert len(final_entities)==(1 if paid else 0)
    if paid:
        assert final_entities[0]['Item']==proof['entity']['Item']==item
    assert not list((world/'data/legacyoftheforce/saber_transfers').glob('*.nbt'))
    pids={point['pid'],proof['pid']};all_ids=[]
    for role,source in enumerate((point['savedActor'],point['savedPeer'])):
        owner=uid(source['UUID']);before,after=proof['before'][role],proof['after'][role]
        assert uid(before['UUID'])==uid(after['UUID'])==owner
        assert source['Inventory']==before['Inventory']==after['Inventory']
        assert receipt(source)==receipt(before)==receipt(after)
        for current in (before,after):
            assert current['neoforge:attachments'][MOD+'player_progress']==source['neoforge:attachments'][MOD+'player_progress']
        native=load(world/f'playerdata/{owner}.dat');assert native['Inventory']==after['Inventory'] and receipt(native)==receipt(after)
        for stack in after['Inventory']:
            if stack['id']==MOD+'lightsaber':
                assert stack['count']==1;all_ids.append(uid(stack['components'][MOD+'saber_assembly']['saberId']))
        for run,result,artifact in ((crash,killed,'sabercrash'),(recovery,resumed,'sabercheckpointrecover')):
            entry=result['clients'][role];folder=repo/f'run-adventure-client-{["one","two"][role]}-{run}'
            assert entry['exitCode']==0 and not entry['forced'] and Path(entry['profile']).resolve()==folder.resolve()
            client=load(folder/f'validation/adventure-{artifact}-client.nbt')
            assert client['schemaVersion']==1 and client['pid']==entry['pid'] and client['pid'] not in pids
            pids.add(client['pid']);assert client['role']==role and uid(client['owner'])==owner
            if run==crash:
                assert client['disconnectObserved'] and client['disconnectReason']
                expected=stock(source['Inventory'])
                if paid and role==0:expected+=stock([item])
                assert stock(client['Inventory'])==expected
            else:
                assert client['Inventory']==after['Inventory']==client['observation']['Inventory']
                observation=client['observation'];assert observation['entityPresent']==paid
                if paid:assert uid(observation['entity'])==entity and observation['Item']==item
                assert (folder/'screenshots/saber-checkpoint-recovered.png').read_bytes()[:8]==b'\x89PNG\r\n\x1a\n'
            log=(folder/'logs/latest.log').read_text(encoding='utf-8')
            assert f'ADVENTURE_{artifact.upper()}_CLIENT_PASS' in log and f'ADVENTURE_{artifact.upper()}_CLIENT_FAILED' not in log
    if paid:all_ids.append(uid(identity))
    assert len(all_ids)==len(set(all_ids))==4
    log=(original.parent/'logs/latest.log').read_text(encoding='utf-8')
    assert f'SABER_TRANSFER_CHECKPOINT_READY stage={stage}' in log and 'Stopping server' not in log and 'SERVER_FAILED' not in log
    log=(world.parent/'logs/latest.log').read_text(encoding='utf-8')
    assert 'ADVENTURE_SABERCHECKPOINTRECOVER_SERVER_PASS' in log and 'All dimensions are saved' in log and 'SERVER_FAILED' not in log
    return {'crash':crash,'recovery':recovery,'stage':stage,'nativeProcesses':sorted(pids),'paid':paid,'journalAtKill':journal['phase'],'entityStoredAtKill':stored,'nativeSavedCopiesAfterRecovery':1,'uniqueSaberIds':4,'pickupGatedDuringObservation':True}


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('runs',nargs='+');parser.add_argument('--all-drop',action='store_true');args=parser.parse_args()
    assert len(args.runs)%2==0
    repo=Path(__file__).resolve().parents[1]
    results=[verify(repo,args.runs[i],args.runs[i+1]) for i in range(0,len(args.runs),2)]
    if args.all_drop:assert len(results)==5 and {result['stage'] for result in results}==set(STAGES)
    print(json.dumps({'dropBoundaries':results,'allFiveDropBoundariesCertified':args.all_drop,'pickupMidCrashCertified':False},indent=2))
