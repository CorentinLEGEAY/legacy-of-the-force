"""Verify genuine two-client reload after the complete P4-05 adventure, without replaying any rewards."""
import argparse
import importlib.util
import json
from pathlib import Path
from read_nbt import load

spec = importlib.util.spec_from_file_location('complete', Path(__file__).with_name('check-adventure-complete.py'))
complete = importlib.util.module_from_spec(spec)
spec.loader.exec_module(complete)
prep, MOD = complete.prep, complete.MOD


def verify(repo, run, source, preparation, rendered=False):
    complete.verify(repo, source, preparation)
    world = repo / f'run-adventure-{run}/world'
    old_world = repo / f'run-adventure-{source}/world'
    previous = load(old_world / 'validation/adventure-journey-server.nbt')
    paint = load(old_world / 'validation/adventure-personalization-stage.nbt')
    proof = load(world / 'validation/adventure-recovery-server.nbt')
    result = json.loads((repo / f'.tools/adventure-recovery-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert result['observed'] and result['serverExitCode'] == 0 and len(result['clients']) == 2
    assert proof['schemaVersion'] == 1 and proof['sourcePid'] == previous['pid'] and proof['pid'] != previous['pid']
    assert len(proof['players']) == len(proof['vehicles']) == 2
    pids = [proof['pid']]
    for index, actor in enumerate(proof['players']):
        owner = prep.ilum.uid(actor['UUID'])
        before = paint['players'][index]
        assert actor['UUID'] == before['UUID'] and actor['Inventory'] == before['Inventory'] and actor['Pos'] == before['Pos']
        for key in ['player_progress','travel_state','speeder_assembly_receipt','speeder_recharge_receipt','boss_loot_receipt']:
            assert actor['neoforge:attachments'][MOD + key] == before['neoforge:attachments'][MOD + key]
        client = result['clients'][index]
        assert client['name'] == ['AdventureOne','AdventureTwo'][index] and client['exitCode'] == 0 and not client['forced']
        profile = repo / f'run-adventure-client-{["one", "two"][index]}-{run}'
        assert Path(client['profile']).resolve() == profile.resolve()
        cp = load(profile / 'validation/adventure-recovery-client.nbt')
        assert cp['schemaVersion'] == 1 and prep.ilum.uid(cp['owner']) == owner and cp['pid'] == client['pid']
        assert cp['dimension'] == 'minecraft:overworld' and cp['Inventory'] == actor['Inventory']
        if rendered:
            from PIL import Image
            assert cp['captureVersion']==2
            vehicle=next(v for v in paint['vehicles'] if prep.ilum.uid(v['vehicleState']['ownerId'])==owner)
            assert cp['seenVehicle']==vehicle['UUID']
            with Image.open(profile/'screenshots/adventure-recovered-speeder.png') as image:
                assert image.width>=800 and image.height>=450
                image.verify()
        pids.append(cp['pid'])
        saved = load(world / f'playerdata/{owner}.dat')
        assert saved['Inventory'] == actor['Inventory'] and saved['Pos'] == actor['Pos']
        assert saved['neoforge:attachments'][MOD + 'player_progress'] == actor['neoforge:attachments'][MOD + 'player_progress']
    assert len(set(pids)) == 3
    vehicles = [e for e in prep.entities(world) if e['id'] == MOD + 'speeder']
    assert len(vehicles) == 2 and {prep.ilum.uid(v['UUID']) for v in vehicles} == {prep.ilum.uid(v['UUID']) for v in paint['vehicles']}
    for vehicle in vehicles:
        old = next(v for v in paint['vehicles'] if v['UUID'] == vehicle['UUID'])
        assert vehicle['vehicleState'] == old['vehicleState']
    crystal = load(old_world / 'validation/adventure-crystal-stage.nbt')
    x,y,z = crystal['source']
    for bx,by,bz in [(x,y,z),(x,y,z-1),(x,y+1,z-1)]:
        assert prep.ilum.block(prep.ilum.chunk(world,bx//16,bz//16),bx,by,bz) in ('minecraft:air','minecraft:cave_air')
    wx,wy,wz=crystal['workshop']
    assert prep.ilum.block(prep.ilum.chunk(world,wx//16,wz//16),wx,wy,wz)==MOD+'saber_workshop'
    for actor in previous['actors']:
        tx,ty,tz=actor['terminal']
        assert prep.ilum.block(prep.ilum.chunk(world,tx//16,tz//16),tx,ty,tz)==MOD+'galactic_terminal'
    duel = load(old_world / 'validation/adventure-duel-stage.nbt')
    ax,ay,az = duel['altarPosition']
    altar = next(b for b in prep.ilum.chunk(world,ax//16,az//16)['block_entities'] if [b['x'],b['y'],b['z']] == [ax,ay,az])
    assert altar.get('Items',[]) == [] and altar['lootVictory'] == duel['deadBoss']['UUID']
    log = (repo / f'run-adventure-{run}/logs/latest.log').read_text(encoding='utf-8-sig')
    assert 'ADVENTURE_RECOVERY_SERVER_PASS' in log and 'ADVENTURE_RECOVERY_SERVER_FAILED' not in log and 'All dimensions are saved' in log
    return {'pids':pids,'ownersReloaded':2,'sameInventoriesAndReceipts':True,'samePaintedVehicles':2,'mineStillEmpty':True,'lootNotReissued':True}


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run')
    parser.add_argument('--source',required=True)
    parser.add_argument('--preparation',default='ap4')
    parser.add_argument('--rendered',action='store_true',help='Require native side-view artifacts; inspect their actual pixels separately.')
    args=parser.parse_args()
    for value in [args.run,args.source,args.preparation]:
        assert 1<=len(value)<=12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in value)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent,args.run,args.source,args.preparation,args.rendered),indent=2))
