"""Independent native reconciliation of arena bucket, dispenser and fluid-boundary protection."""
import argparse,importlib.util,json,math
from pathlib import Path
from read_nbt import load
spec=importlib.util.spec_from_file_location('network',Path(__file__).with_name('check-boss-network.py'))
n=importlib.util.module_from_spec(spec);spec.loader.exec_module(n)

def verify(repo,run):
    world=repo/f'run-boss-{run}/world';proof=load(world/'validation/boss-fluids.nbt');assert proof['schemaVersion']==1
    assert {a['action'] for a in proof['actions']}=={'water_bucket','lava_bucket','pickup_water','pickup_powder'} and len(proof['actions'])==4
    assert all(a['before']==a['after'] for a in proof['actions'])
    assert len(proof['controls'])==2 and {a['action'] for a in proof['controls']}=={'outside_water_pickup','outside_powder_pickup'}
    for action in proof['controls']:
        item='minecraft:water_bucket' if action['action']=='outside_water_pickup' else 'minecraft:powder_snow_bucket'
        assert n.count(action['before'],'minecraft:bucket')==1 and n.count(action['after'],item)==1 and n.count(action['after'],'minecraft:bucket')==0
    assert all(proof[k]==1 for k in ('waterloggingRefused','dispenserRefused','flowBlocked','flowResumed'))
    native=proof['nativePlayer'];owner=n.ilum.uid(native['UUID']);saved=load(world/f'playerdata/{owner}.dat')
    assert saved['Inventory']==native['Inventory'] and n.profile(saved)['totalXpEarned']==n.profile(native)['totalXpEarned']==0
    assert saved['Dimension']=='legacyoftheforce:ilum' and saved['playerGameType']==0 and n.loot_receipt(saved)['revision']==0
    tile=proof['altarNative'];x,y,z=(tile[k] for k in ('x','y','z'));chunk=n.ilum.chunk(world,x//16,z//16)
    assert next(t for t in chunk['block_entities'] if (t['x'],t['y'],t['z'])==(x,y,z))==tile
    assert n.count(tile['Items'],'legacyoftheforce:ilum_boss_relic')==1 and n.count(tile['Items'],'legacyoftheforce:speeder_energy_cell')==4 and 'pendingClaim' not in tile
    arena_id=n.boss.name_uuid(f"legacyoftheforce:ilum/{proof['altar']}/GUARDIAN");arena=load(world/f'data/legacyoftheforce/bosses/arenas/{arena_id}.nbt')
    assert arena['state']=='COOLDOWN' and arena['encounter']==proof['encounter']
    assert len(proof['samples'])==4
    for sample in proof['samples']:
        a,b,c=sample['pos'];assert sample['block']=='minecraft:water' and n.ilum.block(n.ilum.chunk(world,a//16,c//16),a,b,c)=='minecraft:water'
    assert len(proof['ejected'])==2 and {e['Item']['id'] for e in proof['ejected']}=={'minecraft:water_bucket','minecraft:lava_bucket'}
    for entity in proof['ejected']:
        a,_,c=entity['Pos'];identity=n.ilum.uid(entity['UUID'])
        found=[e for dx in (-1,0,1) for dz in (-1,0,1) for e in n.boss.entity_chunk(world,math.floor(a)//16+dx,math.floor(c)//16+dz) if 'UUID' in e and n.ilum.uid(e['UUID'])==identity]
        assert len(found)==1 and found[0]['Item']==entity['Item'] and entity['Item']['count']==1
    encounter=n.ilum.uid(proof['encounter'])
    assert all(n.ilum.uid(e['UUID'])!=encounter for dx in (-1,0,1) for dz in (-1,0,1) for e in n.boss.entity_chunk(world,x//16+dx,z//16+dz) if 'UUID' in e)
    log=(repo/f'.tools/boss-{run}-server.log').read_text(encoding='utf-8-sig');assert 'BOSS_GAME_TEST_PASS kind=FLUIDS' in log and 'All dimensions are saved' in log and 'failed at' not in log
    return {'serverPid':proof['pid'],'nativeBucketRefusals':4,'outsidePickups':2,'waterloggingRefused':True,'nativeDispenserRefusals':2,'filledBucketsEjected':2,'scheduledFlowBlocked':True,'nativeFlowResumedAfterReset':True,'loadedAltarLootPreserved':True}
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('run');args=parser.parse_args();assert 1<=len(args.run)<=12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent,args.run),indent=2))
