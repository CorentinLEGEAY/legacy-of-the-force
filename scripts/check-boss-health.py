"""Independent check of native bounded boss health and a late participant without healing."""
import argparse,importlib.util,json,math
from pathlib import Path
from read_nbt import load
spec=importlib.util.spec_from_file_location('network',Path(__file__).with_name('check-boss-network.py'))
n=importlib.util.module_from_spec(spec);spec.loader.exec_module(n)

def verify(repo,run):
    world=repo/f'run-boss-{run}/world';proof=load(world/'validation/boss-health.nbt');assert proof['schemaVersion']==1
    assert proof['initialParticipants']==2 and proof['lateParticipants']==1 and proof['base']==proof['nativeMaximum']==1024 and proof['extra']==1 and proof['invalidConfigFallback']==1
    before=proof['nativeBoss'];identity=n.ilum.uid(proof['encounter']);x,_,z=before['Pos']
    found=[e for dx in (-1,0,1) for dz in (-1,0,1) for e in n.boss.entity_chunk(world,math.floor(x)//16+dx,math.floor(z)//16+dz) if 'UUID' in e and n.ilum.uid(e['UUID'])==identity]
    assert len(found)==1;boss=found[0]
    for tag in (before,boss):
        assert tag['Health']==896 and tag['lotfBoss']['phase']==1 and tag['lotfBoss']['scaled']==1 and len(tag['lotfBoss']['contributors'])==3
        health=next(a for a in tag['attributes'] if a['id']=='minecraft:generic.max_health');assert health['base']==1024
    assert len(proof['players'])==3
    owners=set()
    for player in proof['players']:
        owner=n.ilum.uid(player['UUID']);owners.add(owner);saved=load(world/f'playerdata/{owner}.dat')
        assert saved['Inventory']==player['Inventory'] and n.profile(saved)['totalXpEarned']==0 and n.profile(saved)['alignmentTenths']==0
    assert len(owners)==3
    log=(repo/f'.tools/boss-{run}-server.log').read_text(encoding='utf-8-sig');assert 'BOSS_GAME_TEST_PASS kind=HEALTH cap=1024 lateJoinWithoutHealing=true' in log and 'All dimensions are saved' in log and 'failed at' not in log
    return {'serverPid':proof['pid'],'nativeMaximumHealth':1024,'nativeSavedBase':1024,'remainingHealthAfter128Damage':896,'initialParticipants':2,'lateParticipants':1,'healedByLateJoin':False,'rewardXp':0}
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('run');args=parser.parse_args();assert 1<=len(args.run)<=12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent,args.run),indent=2))
