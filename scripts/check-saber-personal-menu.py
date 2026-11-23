"""Reconcile real personal-menu clicks, process kills and native cursor/grid recovery."""
import argparse
from collections import Counter
import importlib.util
import json
from pathlib import Path
from read_nbt import load

spec=importlib.util.spec_from_file_location('prep',Path(__file__).with_name('check-adventure-preparation.py'))
prep=importlib.util.module_from_spec(spec);spec.loader.exec_module(prep)
uid,MOD=prep.ilum.uid,prep.MOD
KEY=MOD+'personal_menu'


def items(player):
    return player['Inventory']+player.get(KEY,{'schemaVersion':1,'items':[]})['items']


def stock(values):
    result=Counter()
    for value in values:
        result[json.dumps({k:v for k,v in value.items() if k not in ('Slot','count')},sort_keys=True)]+=value['count']
    return result


def identity(stack):
    return stack.get('components',{}).get(MOD+'saber_assembly',{}).get('saberId')


def receipt(player):
    return player['neoforge:attachments'].get(MOD+'saber_transfer_receipt',{'schemaVersion':1,'revision':0})


def verify(repo,crash,recovery):
    source=repo/f'run-adventure-{crash}/world';world=repo/f'run-adventure-{recovery}/world'
    point=load(source/'validation/saber-transfer-checkpoint.nbt');proof=load(world/'validation/adventure-sabercheckpointrecover-server.nbt')
    a=json.loads((repo/f'.tools/adventure-sabercrash-{crash}-result.json').read_text(encoding='utf-8-sig'));b=json.loads((repo/f'.tools/adventure-sabercheckpointrecover-{recovery}-result.json').read_text(encoding='utf-8-sig'))
    assert a['observed'] and a['killed'] and a['killedPid']==point['pid'] and a['serverExitCode']!=0 and a['killUtc']
    assert b['observed'] and not b['killed'] and b['serverExitCode']==0
    assert point['schemaVersion']==proof['schemaVersion']==1 and point['direction']=='DROP' and point['stage'] in ('PREPARED','HELD')
    assert Path(point['world']).resolve()==source.resolve() and proof['crashedPid']==point['pid'] and proof['pid']!=point['pid']
    assert (source/'validation/saber-transfer-checkpoint.nbt').read_bytes()==(world/'validation/saber-transfer-checkpoint.nbt').read_bytes()
    stage=point['stage'];paid=stage=='HELD';entity=uid(point['entity']);target=point['saber'];operation=point['operation']
    journal=load(source/f'data/legacyoftheforce/saber_transfers/{uid(operation)}.nbt');assert journal==point['journal'] and journal['phase']==stage
    payload=journal['payload'];item=payload['entity']['Item'];assert identity(item)==target and uid(payload['entityId'])==entity
    assert payload['receipt']=={'schemaVersion':1,'revision':1,'operation':operation}
    assert point['storedEntities']==[] and not point['liveEntityPresent']
    assert not any(e['id']=='minecraft:item' and identity(e['Item'])==target for e in prep.entities(source))
    assert not list((world/'data/legacyoftheforce/saber_transfers').glob('*.nbt'))
    source_workshop=load(source/'validation/adventure-saberworkshop-server.nbt')['after'];ids=[];pids={point['pid'],proof['pid']}
    for role,previous in enumerate((point['savedActor'],point['savedPeer'])):
        owner=uid(previous['UUID']);saved_source=load(source/f'playerdata/{owner}.dat');assert saved_source['Inventory']==previous['Inventory'] and saved_source[KEY]==previous[KEY]
        expected=stock(items(previous))
        if role==0 and paid:expected+=stock([item])
        assert expected==stock(items(source_workshop[role]))
        assert previous[KEY]['schemaVersion'] in (1,2)
        if role==0:
            entries=previous[KEY]['items'];assert len(entries)==(1 if paid else 2)
            grid=[s for s in entries if s['Slot']==1];assert len(grid)==1 and grid[0]['id']=='minecraft:iron_ingot' and grid[0]['count']==1
            cursors=[s for s in entries if s['Slot']==0];assert len(cursors)==int(not paid)
            if cursors:assert identity(cursors[0])==target
            assert receipt(previous)==({'schemaVersion':1,'revision':1,'operation':operation} if paid else {'schemaVersion':1,'revision':0})
        before,after=proof['before'][role],proof['after'][role];native=load(world/f'playerdata/{owner}.dat')
        # Vanilla may return cursor/grid to main slots while disconnecting. Compare the full aggregate.
        assert stock(items(previous))==stock(items(before))==stock(items(after))==stock(items(native))
        assert receipt(previous)==receipt(before)==receipt(after)==receipt(native)
        assert before['neoforge:attachments'][MOD+'player_progress']==after['neoforge:attachments'][MOD+'player_progress']==previous['neoforge:attachments'][MOD+'player_progress']
        for stack in items(native):
            if stack['id']==MOD+'lightsaber':assert stack['count']==1;ids.append(uid(identity(stack)))
        for run,result,artifact in ((crash,a,'sabercrash'),(recovery,b,'sabercheckpointrecover')):
            entry=result['clients'][role];folder=repo/f'run-adventure-client-{["one","two"][role]}-{run}';client=load(folder/f'validation/adventure-{artifact}-client.nbt')
            assert entry['exitCode']==0 and not entry['forced'] and client['pid']==entry['pid'] and client['pid'] not in pids
            pids.add(client['pid']);assert uid(client['owner'])==owner and client['role']==role and Path(entry['profile']).resolve()==folder.resolve()
            if run==crash:
                assert client['disconnectObserved'] and client['disconnectReason'] and stock(client['Inventory'])==stock(items(source_workshop[role]))
                if role==0:
                    assert client['personalMenuDrop'] and client['personalMenuBefore']['schemaVersion']==1
                    assert stock(client['personalInventoryBefore']+client['personalMenuBefore']['items'])==stock(client['Inventory'])
                    assert any(s['Slot']==0 and identity(s)==target for s in client['personalMenuBefore']['items'])
                    assert (folder/'screenshots/saber-personal-cursor-before.png').read_bytes()[:8]==b'\x89PNG\r\n\x1a\n'
            else:
                assert stock(client['Inventory']+client['menu']['items'])==stock(items(previous))
                observation=client['observation'];assert stock(observation['Inventory']+observation['menu']['items'])==stock(items(previous)) and observation['entityPresent']==paid
                if paid:assert uid(observation['entity'])==entity and observation['Item']==item
                assert (folder/'screenshots/saber-checkpoint-recovered.png').read_bytes()[:8]==b'\x89PNG\r\n\x1a\n'
            log=(folder/'logs/latest.log').read_text(encoding='utf-8');assert f'ADVENTURE_{artifact.upper()}_CLIENT_PASS' in log and f'ADVENTURE_{artifact.upper()}_CLIENT_FAILED' not in log and 'Development window display=3 ' in log
    entities=[e for e in prep.entities(world) if e['id']=='minecraft:item' and identity(e['Item'])==target];assert len(entities)==int(paid) and proof['entityPresent']==paid
    if paid:assert uid(entities[0]['UUID'])==entity and entities[0]['Item']==item;ids.append(uid(target))
    assert len(ids)==len(set(ids))==4
    log=(source.parent/'logs/latest.log').read_text(encoding='utf-8');assert f'SABER_TRANSFER_CHECKPOINT_READY stage={stage}' in log and 'Stopping server' not in log
    log=(world.parent/'logs/latest.log').read_text(encoding='utf-8');assert 'ADVENTURE_SABERCHECKPOINTRECOVER_SERVER_PASS' in log and 'All dimensions are saved' in log
    return {'crash':crash,'recovery':recovery,'stage':stage,'nativeProcesses':sorted(pids),'realPersonalCursorClick':True,'nativeCraftingInputPreserved':True,'durableTargetCopies':1,'uniqueSaberIds':4,'finalReceipt':int(paid)}


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('crash');parser.add_argument('recovery');args=parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parents[1],args.crash,args.recovery),indent=2))
