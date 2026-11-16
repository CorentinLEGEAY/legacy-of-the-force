"""Read-only native integrated combat / stopped-world owner reload reconciliation."""
import argparse, importlib.util, json
from pathlib import Path
from read_nbt import load
spec=importlib.util.spec_from_file_location('network',Path(__file__).with_name('check-boss-network.py'))
network=importlib.util.module_from_spec(spec);spec.loader.exec_module(network)

def verify(repo,run):
    result=json.loads((repo/f'.tools/boss-solo-{run}-result.json').read_text(encoding='utf-8-sig'))
    if not result['recover']: return network.verify(repo,run,solo=True)
    assert result['exitCode']==0 and not result['forced']
    source=result['templateRun'];report=network.verify(repo,source,solo=True)
    original=repo/f'run-boss-client-solo-{source}/saves/BossValidation';world=repo/f'run-boss-client-solo-{run}/saves/BossValidation'
    proof=load(world/'validation/boss-solo-recovered.nbt');client=load(Path(result['profile'])/'validation/boss-solo-recovery-client.nbt')
    assert proof['schemaVersion']==client['schemaVersion']==1 and proof['pid']==client['pid']==result['pid'] and proof['pid']!=proof['previousPid']==report['serverPid']
    owner=network.ilum.uid(proof['owner']);before=load(original/f'playerdata/{owner}.dat');saved=load(world/f'playerdata/{owner}.dat');embedded=load(world/'level.dat')['Data']['Player']
    assert network.ilum.uid(client['lastPlayer']['owner'])==network.ilum.uid(embedded['UUID'])==owner
    assert before['Inventory']==saved['Inventory']==embedded['Inventory']==proof['native']['Inventory']==client['lastPlayer']['Inventory']
    for tag in (saved,embedded,proof['native']):
        assert network.loot_receipt(tag)==network.loot_receipt(before)
        assert tag['Dimension']==before['Dimension']=='legacyoftheforce:ilum'
        for key in ('totalXpEarned','alignmentTenths','level'): assert network.profile(tag)[key]==network.profile(before)[key]
    assert client['lastPlayer']['dimension']=='legacyoftheforce:ilum'
    checkpoint='legacyoftheforce:save_checkpoint'
    assert saved['neoforge:attachments'][checkpoint]==embedded['neoforge:attachments'][checkpoint]==proof['native']['neoforge:attachments'][checkpoint]
    assert saved['neoforge:attachments'][checkpoint]['generation']>before['neoforge:attachments'][checkpoint]['generation']
    # Receipts/arena journals must be byte-for-byte unchanged; reload cannot award or collect again.
    for relative in ('data/legacyoftheforce/bosses','data/legacyoftheforce/rewards/receipts'):
        a={p.relative_to(original/relative):p.read_bytes() for p in (original/relative).rglob('*.nbt')}
        b={p.relative_to(world/relative):p.read_bytes() for p in (world/relative).rglob('*.nbt')}
        assert a==b,relative
    for battle in load(original/'validation/boss-network-server.nbt')['battles']:
        tile=battle['altarNative'];x,y,z=(tile[k] for k in ('x','y','z'));chunk=network.ilum.chunk(world,x//16,z//16)
        assert next(t for t in chunk['block_entities'] if (t['x'],t['y'],t['z'])==(x,y,z))==tile
        assert all(network.ilum.uid(e['UUID'])!=network.ilum.uid(battle['encounter']) for dx in (-1,0,1) for dz in (-1,0,1) for e in network.boss.entity_chunk(world,x//16+dx,z//16+dz) if 'UUID' in e)
    log=(repo/f'.tools/boss-solo-{run}-console.log').read_text(encoding='utf-8-sig')
    assert 'BOSS_SOLO_RECOVERY_PASS' in log and 'BOSS_SOLO_RECOVERY_CLIENT_PASS' in log and 'All dimensions are saved' in log and 'BOSS_NETWORK_SERVER_FAILED' not in log
    return {'recovered':True,'pid':proof['pid'],'previousPid':proof['previousPid'],'owner':owner,'inventoryPreserved':True,'embeddedOwnerPreserved':True,'journalsUnchanged':True,'sourceCombat':report}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('run');args=parser.parse_args()
    assert 1<=len(args.run)<=12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent,args.run),indent=2))
