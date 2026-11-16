"""Read-only death/drop/respawn, dimension and logout checks against native player/entity storage."""
import argparse,importlib.util,json,math
from pathlib import Path
from read_nbt import load
spec=importlib.util.spec_from_file_location('network',Path(__file__).with_name('check-boss-network.py'))
n=importlib.util.module_from_spec(spec);spec.loader.exec_module(n)

def verify(repo,run):
    world=repo/f'run-boss-{run}/world';proof=load(world/'validation/boss-lifecycle.nbt');assert proof['schemaVersion']==1
    assert len(proof['cases'])==4 and {c['mode'] for c in proof['cases']}=={'DEATH_DROP','DEATH_KEEP','DIMENSION','LOGOUT'}
    log=(repo/f'.tools/boss-{run}-server.log').read_text(encoding='utf-8-sig');assert 'BOSS_GAME_TEST_PASS kind=LIFECYCLE cases=4' in log and 'All dimensions are saved' in log and 'Boss arenas paused' not in log and 'failed at' not in log
    cases=[];owners=set();operations=set();encounters=set()
    for case in proof['cases']:
        mode=case['mode'];owner=n.ilum.uid(case['owner']);owners.add(owner);native=load(world/f'playerdata/{owner}.dat');before=case['before'];after=case['native'];drop=mode=='DEATH_DROP'
        assert n.ilum.uid(native['UUID'])==n.ilum.uid(before['UUID'])==n.ilum.uid(after['UUID'])==owner
        assert native['Inventory']==after['Inventory'] and n.loot_receipt(native)==n.loot_receipt(after)==n.loot_receipt(before) and n.loot_receipt(native)['revision']==1
        operations.add(n.ilum.uid(n.loot_receipt(native)['operation']))
        assert native['Dimension']==after['Dimension']==('legacyoftheforce:ilum' if mode=='LOGOUT' else 'minecraft:overworld')
        assert n.profile(native)['totalXpEarned']==n.profile(after)['totalXpEarned']==n.profile(before)['totalXpEarned']==0
        assert n.profile(native)['alignmentTenths']==n.profile(after)['alignmentTenths']==0
        assert native['Health']>0 and native['playerGameType']==0
        for item,quantity in {'minecraft:diamond':7,'legacyoftheforce:ilum_boss_relic':1,'legacyoftheforce:speeder_energy_cell':4}.items():
            assert n.count(before['Inventory'],item)==quantity
            assert n.count(native['Inventory'],item)==(0 if drop else quantity)
            assert n.count(case['ground'],item)==(quantity if drop else 0)
        if not drop: assert native['Inventory']==before['Inventory']
        assert bool(case['groundEntities'])==drop
        for entity in case['groundEntities']:
            x,_,z=entity['Pos'];identity=n.ilum.uid(entity['UUID'])
            found=[e for dx in (-1,0,1) for dz in (-1,0,1) for e in n.boss.entity_chunk(world,math.floor(x)//16+dx,math.floor(z)//16+dz) if 'UUID' in e and n.ilum.uid(e['UUID'])==identity]
            assert len(found)==1 and found[0]['Item']==entity['Item']
        journal=load(world/f'data/legacyoftheforce/bosses/loot/{owner}.nbt');assert journal==case['journal'] and journal['phase']=='DONE' and journal['after']['Inventory']==before['Inventory'] and journal['after']['receipt']==n.loot_receipt(native)
        assert n.ilum.uid(journal['owner'])==owner and journal['operation']==n.loot_receipt(native)['operation']
        packed=case['altar'];arena_id=n.boss.name_uuid(f'legacyoftheforce:ilum/{packed}/GUARDIAN');arena=load(world/f'data/legacyoftheforce/bosses/arenas/{arena_id}.nbt')
        assert arena==case['arena'] and arena['state']=='COOLDOWN' and arena['encounter']==case['encounter']
        tile=case['altarNative'];x,y,z=(tile[k] for k in ('x','y','z'));chunk=n.ilum.chunk(world,x//16,z//16)
        assert next(t for t in chunk['block_entities'] if (t['x'],t['y'],t['z'])==(x,y,z))==tile
        assert tile['Items']==[] and 'pendingClaim' not in tile and tile['lastClaim']==n.loot_receipt(native)['operation']
        encounter=n.ilum.uid(case['encounter']);encounters.add(encounter)
        assert all(n.ilum.uid(e['UUID'])!=encounter for dx in (-1,0,1) for dz in (-1,0,1) for e in n.boss.entity_chunk(world,x//16+dx,z//16+dz) if 'UUID' in e)
        cases.append({'mode':mode,'receiptRevision':1,'rewardsFromAbandonment':0,'inventoryPolicy':'dropped' if drop else 'kept','closedJournalPreserved':True})
    assert len(owners)==len(operations)==len(encounters)==4
    assert not list((world/'data/legacyoftheforce/bosses/victories').glob('*.nbt'))
    assert all(n.ilum.uid(load(p)['owner']) not in owners for p in (world/'data/legacyoftheforce/rewards/receipts').rglob('*.nbt'))
    return {'serverPid':proof['pid'],'cases':cases}
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('run');args=parser.parse_args();assert 1<=len(args.run)<=12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent,args.run),indent=2))
