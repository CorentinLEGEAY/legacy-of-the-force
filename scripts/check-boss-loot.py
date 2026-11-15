"""Read-only checks of native shared-loot snapshots and genuine interrupted/restarted server worlds."""
import argparse
import importlib.util
import json
from pathlib import Path
from read_nbt import load

spec=importlib.util.spec_from_file_location("ilum",Path(__file__).with_name("check-ilum-worlds.py"))
ilum=importlib.util.module_from_spec(spec);spec.loader.exec_module(ilum)
CHECKPOINTS=['PREPARED','LOCK_MEMORY','LOCK_DURABLE','PLAYER_MEMORY','PLAYER_DURABLE','DELIVERED','EMPTY_MEMORY','EMPTY_DURABLE','DONE','UNLOCK_MEMORY','UNLOCK_DURABLE']
KEY='legacyoftheforce:boss_loot_receipt'

def counts(inventory):
    result={}
    for item in inventory: result[item['id']]=result.get(item['id'],0)+item['count']
    return result

def receipt(player):
    return player.get('neoforge:attachments',{}).get(KEY,{'schemaVersion':1,'revision':0})

def check_case(checkpoint,crash_player,crash_altar,recovered_player,recovered_altar,journal,later=False):
    delivered=CHECKPOINTS.index(checkpoint)>=CHECKPOINTS.index('PLAYER_DURABLE')
    expected={'minecraft:diamond':7}
    if delivered: expected.update({'legacyoftheforce:ilum_boss_relic':1,'legacyoftheforce:speeder_energy_cell':4})
    assert counts(crash_player['Inventory']) == expected
    if later: expected['minecraft:emerald']=3
    assert counts(recovered_player['Inventory']) == expected
    assert journal['schemaVersion']==1 and journal['phase']==('DONE' if delivered else 'ABORTED')
    assert journal['owner']==crash_player['UUID']==recovered_player['UUID']
    assert journal['victory']==crash_altar['lootVictory']==recovered_altar['lootVictory']
    assert receipt(crash_player)['revision']==receipt(recovered_player)['revision']==int(delivered)
    assert crash_player['Inventory']==journal['after' if delivered else 'before']['Inventory']
    source_emptied=CHECKPOINTS.index(checkpoint)>=CHECKPOINTS.index('EMPTY_DURABLE')
    assert counts(crash_altar['Items'])==({} if source_emptied else {'legacyoftheforce:ilum_boss_relic':1,'legacyoftheforce:speeder_energy_cell':4})
    locked=CHECKPOINTS.index('LOCK_DURABLE')<=CHECKPOINTS.index(checkpoint)<CHECKPOINTS.index('UNLOCK_DURABLE')
    assert ('pendingClaim' in crash_altar)==locked
    if locked: assert crash_altar['pendingClaim']==journal['operation']
    if delivered: assert receipt(recovered_player)['operation']==journal['operation']
    assert 'pendingClaim' not in recovered_altar
    assert counts(recovered_altar['Items'])==({} if delivered else {'legacyoftheforce:ilum_boss_relic':1,'legacyoftheforce:speeder_energy_cell':4})
    if delivered: assert recovered_altar['lastClaim']==journal['operation']
    assert crash_player['Dimension']==recovered_player['Dimension']=='legacyoftheforce:ilum'
    # No rewards were fabricated by the failure-injection fixture.
    assert recovered_player['neoforge:attachments']['legacyoftheforce:player_progress']['totalXpEarned']==0
    return delivered

def injected(repo,run):
    world=repo/f'run-boss-{run}/world';proof=load(world/'validation/loot/proof.nbt')
    log=(repo/f'.tools/boss-{run}-server.log').read_text(encoding='utf-8-sig')
    assert proof['schemaVersion']==proof['refusals']==1 and 'BOSS_GAME_TEST_PASS kind=LOOT checkpoints=11' in log and 'All dimensions are saved' in log
    cases=proof['checkpoints'];assert [c['checkpoint'] for c in cases]==CHECKPOINTS
    for case in cases:
        delivered=check_case(case['checkpoint'],case['crashPlayer'],case['crashAltar'],case['recoveredPlayer'],case['recoveredAltar'],case['journal'],True)
        assert delivered==bool(case['delivered'])
    if 'concurrency' in proof:
        concurrent=proof['concurrency'];assert counts(concurrent['owner']['Inventory'])=={'minecraft:diamond':7}
        assert counts(concurrent['peer']['Inventory'])=={'legacyoftheforce:ilum_boss_relic':1,'legacyoftheforce:speeder_energy_cell':4}
        assert concurrent['ownerJournal']['phase']=='ABORTED' and concurrent['peerJournal']['phase']=='DONE'
        assert concurrent['altar']['Items']==[] and 'pendingClaim' not in concurrent['altar'] and concurrent['altar']['lastClaim']==concurrent['peerJournal']['operation']
    return {'injectedCheckpoints':len(cases),'pendingClaimsAfterRecovery':0,'refusals':True,'peerBeforeOwnerRecovery':'concurrency' in proof}

def native_altar(world,pos):
    x,y,z=pos
    chunk=ilum.chunk(world,x//16,z//16)
    return next(t for t in chunk['block_entities'] if (t['x'],t['y'],t['z'])==(x,y,z))

def killed(repo,prefix):
    results=json.loads((repo/f'.tools/boss-loot-{prefix}-kills.json').read_text(encoding='utf-8-sig'))
    if isinstance(results,dict): results=[results]
    pids=set();reports=[]
    for result in results:
        run=result['run'];checkpoint=result['checkpoint'];world=repo/f'run-boss-{run}/world';interrupted=repo/f'run-boss-{run}-interrupted/world'
        proof=load(world/'validation/loot-recovered.nbt');crash=proof['interrupted'];identity=ilum.uid(crash['owner'])
        assert proof['schemaVersion']==crash['schemaVersion']==1 and crash['checkpoint']==checkpoint
        assert result['wrapperExitCode']!=0 and result['recoveryExitCode']==0
        # Early Windows Get-Process handles did not expose ExitCode after Stop-Process.
        # Never interpret null as a numeric abnormal exit: the paused trace, completed wait,
        # Gradle failure and new JVM provide the termination evidence for these historical runs.
        assert result['nativeExitCode'] is None or isinstance(result['nativeExitCode'],int) and result['nativeExitCode']!=0
        assert result['killedPid']==crash['pid']!=proof['pid'];pids.update((crash['pid'],proof['pid']))
        interrupted_log=(repo/f'.tools/boss-loot-{run}-interrupted.log').read_text(encoding='utf-8-sig')
        assert f'BOSS_LOOT_CRASH_PAUSED checkpoint={checkpoint}' in interrupted_log and 'Stopping the server' not in interrupted_log
        recovered_log=(repo/f'.tools/boss-loot-{run}-recovered.log').read_text(encoding='utf-8-sig')
        assert f'BOSS_LOOT_CRASH_RECOVERY_PASS checkpoint={checkpoint}' in recovered_log and 'All dimensions are saved' in recovered_log and 'BOSS_LOOT_CRASH_FAILED' not in recovered_log
        actual_crash=load(interrupted/f'playerdata/{identity}.dat');actual_player=load(world/f'playerdata/{identity}.dat')
        pos=tuple(crash['nativeAltar'][key] for key in ('x','y','z'))
        ca=native_altar(interrupted,pos);ra=native_altar(world,pos)
        assert actual_crash['Inventory']==crash['nativePlayer']['Inventory'] and receipt(actual_crash)==receipt(crash['nativePlayer'])
        assert actual_player['Inventory']==proof['nativePlayer']['Inventory'] and receipt(actual_player)==receipt(proof['nativePlayer'])
        assert ca==crash['nativeAltar'] and ra==proof['nativeAltar']
        journal=load(world/f'data/legacyoftheforce/bosses/loot/{identity}.nbt');assert journal==proof['journal']
        delivered=check_case(checkpoint,actual_crash,ca,actual_player,ra,journal)
        assert bool(proof['delivered'])==delivered
        reports.append({'checkpoint':checkpoint,'killedPid':crash['pid'],'recoveredPid':proof['pid'],'nativeExitCode':result['nativeExitCode'],'delivered':delivered})
    assert len(pids)==2*len(results)
    return {'genuineJvmKills':len(results),'cases':reports}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('run');parser.add_argument('--kills',action='store_true');args=parser.parse_args()
    assert 1<=len(args.run)<=12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    repo=Path(__file__).resolve().parent.parent
    print(json.dumps(killed(repo,args.run) if args.kills else injected(repo,args.run),indent=2))
