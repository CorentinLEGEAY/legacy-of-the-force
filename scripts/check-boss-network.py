"""Independent read-only reconciliation of two native client fights, shared loot and saved server state."""
import argparse
import importlib.util
import json
from pathlib import Path
from read_nbt import load

spec=importlib.util.spec_from_file_location('boss_reader',Path(__file__).with_name('check-bosses.py'))
boss=importlib.util.module_from_spec(spec);spec.loader.exec_module(boss)
ilum=boss.ilum

def nbt_bucket_count(player): return count(player['Inventory'],'minecraft:water_bucket')
def count(inventory,item): return sum(stack['count'] for stack in inventory if stack['id']==item)
def profile(player): return player['neoforge:attachments']['legacyoftheforce:player_progress']
def loot_receipt(player): return player.get('neoforge:attachments',{}).get('legacyoftheforce:boss_loot_receipt',{'schemaVersion':1,'revision':0})
def saber(player):
    found=[stack['components']['legacyoftheforce:saber_assembly'] for stack in player['Inventory'] if stack['id']=='legacyoftheforce:lightsaber']
    assert len(found)==1 and found[0]['active']==1
    return found[0]

def verify(repo,run,solo=False):
    n=1 if solo else 2
    if solo:
        world=repo/f'run-boss-client-solo-{run}/saves/BossValidation';prefix=repo/f'.tools/boss-solo-{run}'
        result=json.loads(Path(str(prefix)+'-result.json').read_text(encoding='utf-8-sig'))
        assert result['exitCode']==0 and not result['forced'] and not result['recover']
        result['clients']=[{'profile':result['profile'],'pid':result['pid'],'name':'ForceTesterSolo'}]
        log=Path(str(prefix)+'-console.log').read_text(encoding='utf-8-sig')
    else:
        world=repo/f'run-boss-{run}/world';prefix=repo/f'.tools/boss-network-{run}';result=json.loads(Path(str(prefix)+'-result.json').read_text(encoding='utf-8-sig'))
        assert result['observed'] and result['serverExitCode']==0 and len(result['clients'])==2 and all(c['exitCode']==0 for c in result['clients'])
        log=Path(str(prefix)+'-server.log').read_text(encoding='utf-8-sig')
    proof=load(world/'validation/boss-network-server.nbt');assert proof['schemaVersion']==1
    assert f'BOSS_NETWORK_SERVER_PASS clients={n} battles=2' in log and 'All dimensions are saved' in log and 'BOSS_NETWORK_SERVER_FAILED' not in log and 'Boss arenas paused' not in log
    initial={ilum.uid(p['UUID']):p for p in proof['initialPlayers']};players={ilum.uid(p['owner']):p for p in proof['players']};assert len(initial)==len(players)==n and initial.keys()==players.keys()
    if solo:
        assert proof['solo']==1 and proof['serverClass']=='net.minecraft.client.server.IntegratedServer' and all(p['memoryConnection']==1 for p in players.values())
    else:
        assert len({p['endpoint'] for p in players.values()})==2 and all(p['endpoint'].startswith('/127.0.0.1:') for p in players.values())
    relics=cells=revisions=0;client_pids=set();client_clicks=0;crystals=set();sabers=set();clients={}
    for launch in result['clients']:
        client=load(Path(launch['profile'])/'validation/boss-network-client.nbt');assert client['schemaVersion']==1 and client['pid']==launch['pid'] and (client['pid']==proof['serverPid'] if solo else client['pid']!=proof['serverPid'])
        client_log=(Path(launch['profile'])/'logs/latest.log').read_text(encoding='utf-8-sig');assert 'BOSS_NETWORK_CLIENT_PASS' in client_log and 'BOSS_NETWORK_CLIENT_FAILED' not in client_log
        client_pids.add(client['pid']);identity=ilum.uid(client['lastPlayer']['owner']);assert identity in players and players[identity]['name']==launch['name'];clients[identity]=client
        if proof.get('fluids'):
            prediction=client['fluidPrediction'];assert all(prediction[k]==1 for k in ('predictedWater','predictedEmptyBucket','waterRolledBack','bucketRestored'))
            tile=proof['battles'][0]['altarNative'];x,y,z=tile['x']+2,tile['y'],tile['z']-3
            packed=((x&0x3ffffff)<<38)|((z&0x3ffffff)<<12)|(y&0xfff);packed=packed if packed<2**63 else packed-2**64
            assert prediction['target']==packed and nbt_bucket_count(initial[identity])==1
            assert nbt_bucket_count(players[identity]['native'])==1 and count(players[identity]['native']['Inventory'],'minecraft:bucket')==0
            assert ilum.block(ilum.chunk(world,x//16,z//16),x,y,z) not in ('minecraft:water','minecraft:lava')
        native=players[identity]['native'];saved=load(world/f'playerdata/{identity}.dat');origin=initial[identity]
        assert saved['Inventory']==native['Inventory']==client['lastPlayer']['Inventory']
        if solo:
            embedded=load(world/'level.dat')['Data']['Player']
            assert ilum.uid(embedded['UUID'])==identity and embedded['Inventory']==saved['Inventory']
            assert embedded['Dimension']==saved['Dimension'] and loot_receipt(embedded)==loot_receipt(saved)
            assert embedded['neoforge:attachments']['legacyoftheforce:save_checkpoint']==saved['neoforge:attachments']['legacyoftheforce:save_checkpoint']==native['neoforge:attachments']['legacyoftheforce:save_checkpoint']
            for key in ('totalXpEarned','alignmentTenths','level'): assert profile(embedded)[key]==profile(saved)[key]
        assert native['Dimension']==saved['Dimension']==client['lastPlayer']['dimension']=='legacyoftheforce:ilum' and native['playerGameType']==saved['playerGameType']==0
        assert profile(origin)['level']==15 and profile(origin)['totalXpEarned']==proof['initialXp'] and profile(origin)['alignmentTenths']==0
        assert profile(saved)['totalXpEarned']==profile(native)['totalXpEarned']==proof['initialXp']+1600 and profile(saved)['alignmentTenths']==profile(native)['alignmentTenths']==0
        a,b=saber(origin),saber(saved);assert a['saberId']==b['saberId'] and a['parts']==b['parts'] and a['crystal']['crystalId']==b['crystal']['crystalId']
        assert a['crystal']['bondProgress']==0 and b['crystal']['bondProgress']>0
        sabers.add(ilum.uid(b['saberId']));crystals.add(ilum.uid(b['crystal']['crystalId']))
        assert count(origin['Inventory'],'minecraft:golden_apple')==16 and count(saved['Inventory'],'minecraft:golden_apple')<=16
        assert count(origin['Inventory'],'legacyoftheforce:ilum_boss_relic')==count(origin['Inventory'],'legacyoftheforce:speeder_energy_cell')==0
        relics+=count(saved['Inventory'],'legacyoftheforce:ilum_boss_relic');cells+=count(saved['Inventory'],'legacyoftheforce:speeder_energy_cell')
        receipt=loot_receipt(saved);revisions+=receipt['revision'];assert receipt==loot_receipt(native)
        if receipt['revision']:
            journal=load(world/f'data/legacyoftheforce/bosses/loot/{identity}.nbt');assert journal['phase']=='DONE' and ilum.uid(journal['owner'])==identity and journal['operation']==receipt['operation'] and journal['after']['receipt']==receipt
        assert client['attacks']>0 and client['guards']>0;client_clicks+=client['menuClicks']
    assert len(client_pids)==len(clients)==len(sabers)==len(crystals)==n and relics==2 and cells==8 and revisions==2 and client_clicks>=2
    assert [b['kind'] for b in proof['battles']]==['GUARDIAN','FROST_LORD'];reports=[];claims=set()
    for index,battle in enumerate(proof['battles']):
        encounter=ilum.uid(battle['encounter']);kind=battle['kind'];assert {1,2,3}<=set(battle['phases']) and battle['ice']==battle['signature']==1 and battle['iceHits']+battle['iceForces']>0
        if index==0: assert battle['parries']>0 and battle['counters']>0
        assert {ilum.uid(p['owner']) for p in battle['players']}==players.keys()
        for actor in battle['players']:
            identity=ilum.uid(actor['owner']);assert actor['damage']>=2 and profile(actor['native'])['totalXpEarned']==proof['initialXp']+800*(index+1)
            assert profile(actor['native'])['alignmentTenths']==(-250 if index==0 else 0)
            rid=boss.name_uuid(f'boss/{encounter}/{identity}');reward=load(world/f'data/legacyoftheforce/rewards/receipts/{rid[:3]}/{rid}.nbt')
            assert reward['accepted']==1 and ilum.uid(reward['owner'])==identity and ilum.uid(reward['event'])==rid
            observed=next(b for b in clients[identity]['battles'] if ilum.uid(b['encounter'])==encounter)
            assert observed['kind']==kind and {1,2,3}<=set(observed['phases']) and observed['ice']==observed['signature']==observed['defeated']==1
        tile=battle['altarNative'];x,y,z=(tile[k] for k in ('x','y','z'));chunk=ilum.chunk(world,x//16,z//16)
        actual=next(t for t in chunk['block_entities'] if (t['x'],t['y'],t['z'])==(x,y,z));assert actual==tile
        assert tile['lotfSchemaVersion']==1 and tile['Items']==[] and tile['lootVictory']==battle['encounter'] and 'pendingClaim' not in tile
        claim=ilum.uid(tile['lastClaim']);claims.add(claim)
        assert any(loot_receipt(p['native']).get('operation')==tile['lastClaim'] for p in battle['players'])
        packed=((x&0x3ffffff)<<38)|((z&0x3ffffff)<<12)|(y&0xfff);packed=packed if packed<2**63 else packed-2**64
        arena_id=boss.name_uuid(f'legacyoftheforce:ilum/{packed}/{kind}');arena=load(world/f'data/legacyoftheforce/bosses/arenas/{arena_id}.nbt')
        assert arena['state']=='COOLDOWN' and arena['encounter']==battle['encounter'] and set(arena['lootOwners'])==players.keys()
        site=battle['generatedSite'];start=ilum.chunk(world,*site['startChunk'])['structures']['starts'][site['id']]
        assert len(start['Children'])==1 and start['Children'][0]['BB']==site['bounds'];a,b,c,_,_,_=site['bounds'];assert (x,y,z)==(a+5,b+1,c+8)
        cache={}
        def state(bx,by,bz):
            key=bx//16,bz//16
            if key not in cache: cache[key]=ilum.chunk(world,*key)
            return ilum.block(cache[key],bx,by,bz)
        assert all(state(a+dx,b,c+dz)=='minecraft:polished_andesite' for dx in range(11) for dz in range(11))
        pillar='minecraft:quartz_pillar' if index==0 else 'minecraft:polished_blackstone_bricks'
        assert all(state(a+dx,b+dy,c+dz)==pillar for dx in (2,8) for dz in (2,8) for dy in range(1,5))
        assert all(state(a+dx,b+5,c+dz)=='minecraft:sea_lantern' for dx in (2,8) for dz in (2,8))
        assert state(a+5,b+6,c+5)==('minecraft:quartz_bricks' if index==0 else 'minecraft:deepslate_bricks')
        assert all(ilum.uid(e['UUID'])!=encounter for dx in (-1,0,1) for dz in (-1,0,1) for e in boss.entity_chunk(world,x//16+dx,z//16+dz) if 'UUID' in e)
        reports.append({'kind':kind,'contributors':n,'parries':battle['parries'],'paidCounters':battle['counters'],'iceHits':battle['iceHits'],'iceCounters':battle['iceForces']})
    assert len(claims)==2 and not list((world/'data/legacyoftheforce/bosses/victories').glob('*.nbt'))
    return {'nativeClients':len(clients),'serverPid':proof['serverPid'],'clientPids':sorted(client_pids),'battles':reports,'personalXpDelta':1600,'netAlignment':0,'sharedRelics':relics,'sharedCells':cells,'pendingVictories':0,'nativeBucketRollbacks':len(clients) if proof.get('fluids') else 0}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('run');args=parser.parse_args();assert 1<=len(args.run)<=12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent,args.run),indent=2))
