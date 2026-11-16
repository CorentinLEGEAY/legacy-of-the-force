"""Reconcile real native arena spawn/victory JVM interruptions with restarted storage."""
import argparse,importlib.util,json
from pathlib import Path
from read_nbt import load
spec=importlib.util.spec_from_file_location('network',Path(__file__).with_name('check-boss-network.py'))
n=importlib.util.module_from_spec(spec);spec.loader.exec_module(n)
ORDER=['SPAWN_INTENT','SPAWN_MEMORY','SPAWN_DURABLE','ACTIVE','VICTORY_PREPARED','SETTLING','REWARD_GRANTED','REWARDS_DURABLE','LOOT_MEMORY','LOOT_DURABLE','LOOT_STAGED','COOLDOWN','EVENT','COMMITTED','CLOSED','REMOVED']

def verify(repo,run):
    results=json.loads((repo/f'.tools/boss-arena-{run}-kills.json').read_text(encoding='utf-8-sig'))
    if isinstance(results,dict): results=[results]
    reports=[]
    for result in results:
        assert result['nativeExitCode'] not in (None,0) and result['wrapperExitCode']!=0 and result['recoveryExitCode']==0
        name=result['run'];world=repo/f'run-boss-{name}/world';interrupted=repo/f'run-boss-{name}-interrupted/world'
        fixture=load(interrupted/'validation/arena-fixture.nbt');crash=load(interrupted/'validation/arena-crash.nbt');recovered=load(world/'validation/arena-recovered.nbt')
        assert fixture['schemaVersion']==crash['schemaVersion']==recovered['schemaVersion']==1
        cp=result['checkpoint'];assert fixture['checkpoint']==crash['checkpoint']==cp and fixture['pid']==crash['pid']==result['killedPid']!=recovered['pid']
        victory=ORDER.index(cp)>=ORDER.index('VICTORY_PREPARED');assert bool(recovered['victory'])==victory
        owner=n.ilum.uid(fixture['owner']);native=load(world/f'playerdata/{owner}.dat');initial=fixture['initialPlayer'];proof=recovered['nativePlayer']
        assert native['Inventory']==initial['Inventory']==proof['Inventory'] and n.count(native['Inventory'],'minecraft:diamond')==7
        assert n.profile(native)['totalXpEarned']==n.profile(proof)['totalXpEarned']==(800 if victory else 0)
        assert n.profile(native)['alignmentTenths']==n.profile(proof)['alignmentTenths']==(-250 if victory else 0)
        assert n.profile(initial)['totalXpEarned']==0 and n.loot_receipt(native)['revision']==0
        interrupted_arena=crash['arena']
        expected_state='SPAWNING' if ORDER.index(cp)<3 else 'ACTIVE' if ORDER.index(cp)<5 else 'SETTLING' if ORDER.index(cp)<11 else 'COOLDOWN'
        assert interrupted_arena['state']==expected_state
        pending=list((interrupted/'data/legacyoftheforce/bosses/victories').glob('*.nbt'))
        if not victory or cp=='REMOVED': assert not pending
        else:
            phase='PREPARED' if ORDER.index(cp)<7 else 'HELD' if ORDER.index(cp)<10 else 'DESTINATION_STAGED' if ORDER.index(cp)<13 else 'COMMITTED' if cp=='COMMITTED' else 'CLOSED'
            assert len(pending)==1 and load(pending[0])['phase']==phase
        assert load(interrupted/f'playerdata/{owner}.dat')==crash['nativePlayer']
        arena=recovered['arena'];encounter=n.ilum.uid(crash['arena']['encounter']);assert arena['encounter']==crash['arena']['encounter'] and arena['state']=='COOLDOWN'
        packed=fixture['altar'];arena_id=n.boss.name_uuid(f'legacyoftheforce:ilum/{packed}/GUARDIAN')
        assert load(world/f'data/legacyoftheforce/bosses/arenas/{arena_id}.nbt')==arena
        assert load(interrupted/f'data/legacyoftheforce/bosses/arenas/{arena_id}.nbt')==crash['arena']
        tile=recovered['nativeAltar'];x,y,z=(tile[k] for k in ('x','y','z'));chunk=n.ilum.chunk(world,x//16,z//16)
        assert next(t for t in chunk['block_entities'] if (t['x'],t['y'],t['z'])==(x,y,z))==tile and 'pendingClaim' not in tile
        original_chunk=n.ilum.chunk(interrupted,x//16,z//16)
        assert next(t for t in original_chunk['block_entities'] if (t['x'],t['y'],t['z'])==(x,y,z))==crash['nativeAltar']
        assert n.count(tile['Items'],'legacyoftheforce:ilum_boss_relic')==(1 if victory else 0) and n.count(tile['Items'],'legacyoftheforce:speeder_energy_cell')==(4 if victory else 0)
        if victory:
            assert arena['readyAt']==(load(pending[0])['payload']['arenaReadyAt'] if pending else crash['arena']['readyAt'])
            assert tile['lootVictory']==crash['arena']['encounter'] and arena['lootOwners']==[owner]
            rid=n.boss.name_uuid(f'boss/{encounter}/{owner}');receipt=load(world/f'data/legacyoftheforce/rewards/receipts/{rid[:3]}/{rid}.nbt')
            assert receipt['accepted']==1 and n.ilum.uid(receipt['owner'])==owner and n.ilum.uid(receipt['event'])==rid
        receipts=list((world/'data/legacyoftheforce/rewards/receipts').rglob('*.nbt'));assert len(receipts)==int(victory)
        assert not list((world/'data/legacyoftheforce/bosses/victories').glob('*.nbt'))
        assert all(n.ilum.uid(e['UUID'])!=encounter for dx in (-1,0,1) for dz in (-1,0,1) for e in n.boss.entity_chunk(world,x//16+dx,z//16+dz) if 'UUID' in e)
        if ORDER.index(cp)>=ORDER.index('SPAWN_DURABLE'):
            assert any(n.ilum.uid(e['UUID'])==encounter for e in n.boss.entity_chunk(interrupted,x//16,z//16) if 'UUID' in e)
        log=(repo/f'.tools/boss-arena-{name}-recovered.log').read_text(encoding='utf-8-sig')
        assert f'BOSS_ARENA_CRASH_RECOVERY_PASS checkpoint={cp}' in log and 'All dimensions are saved' in log and 'BOSS_ARENA_CRASH_FAILED' not in log and 'Boss arenas paused' not in log
        crash_log=(repo/f'.tools/boss-arena-{name}-interrupted.log').read_text(encoding='utf-8-sig')
        assert f'BOSS_ARENA_CRASH_PAUSED checkpoint={cp}' in crash_log and 'All dimensions are saved' not in crash_log and 'BOSS_ARENA_CRASH_FAILED' not in crash_log
        reports.append({'checkpoint':cp,'killedPid':result['killedPid'],'recoveryPid':recovered['pid'],'nativeExitCode':result['nativeExitCode'],'personalXp':800 if victory else 0,'sharedRelics':int(victory),'pendingVictories':0})
    return reports
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('runs',nargs='+');parser.add_argument('--all-checkpoints',action='store_true');args=parser.parse_args()
    assert all(1<=len(run)<=9 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in run) for run in args.runs)
    reports=[report for run in args.runs for report in verify(Path(__file__).resolve().parent.parent,run)]
    if args.all_checkpoints: assert {r['checkpoint'] for r in reports}==set(ORDER),'Full checkpoint coverage required'
    print(json.dumps(reports,indent=2))
