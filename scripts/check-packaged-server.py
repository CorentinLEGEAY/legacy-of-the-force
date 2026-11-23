"""Verify the distributed JAR, two standalone server boots and native persisted water flow."""
import argparse,hashlib,importlib.util,json,zipfile
from pathlib import Path
from read_nbt import load
spec=importlib.util.spec_from_file_location('ilum',Path(__file__).with_name('check-ilum-worlds.py'))
ilum=importlib.util.module_from_spec(spec);spec.loader.exec_module(ilum)

def verify(repo,run):
    result=json.loads((repo/f'.tools/packaged-{run}-result.json').read_text(encoding='utf-8-sig'));root=Path(result['serverRoot']);world=Path(result['world'])
    assert len(result['phases'])==2 and len({p['pid'] for p in result['phases']})==2 and all(p['exitCode']==0 and not p['forced'] for p in result['phases'])
    jar=repo/'build/libs/legacyoftheforce-0.1.0-dev.jar';digest=hashlib.sha256(jar.read_bytes()).hexdigest().upper()
    assert digest==result['jarSha256']==hashlib.sha256((root/'mods'/jar.name).read_bytes()).hexdigest().upper()
    with zipfile.ZipFile(jar) as archive:
        names=archive.namelist();assert not any(any(t in f for t in ('GameTests','ClientDriver','Validation')) for f in names)
        test_root=repo/'src/gameTest/java'
        test_stems=[p.relative_to(test_root).with_suffix('').as_posix() for p in test_root.rglob('*.java')]
        assert not any(name==stem+'.class' or name.startswith(stem+'$') for name in names for stem in test_stems), 'Test-only class distributed'
        assert 'com/legacyoftheforce/quests/QuestLifecycle.class' in names
        assert json.loads(archive.read('data/legacyoftheforce/quests/echo_of_ilum.json'))['rewards']=={'xp':0,'alignmentTenths':0}
        assert json.loads(archive.read('data/legacyoftheforce/worldgen/structure/echo_ruin.json'))['type']=='legacyoftheforce:echo_ruin'
        config=json.loads(archive.read('legacyoftheforce.mixins.json'));assert config['required'] and config['injectors']['defaultRequire']==1 and len(config['mixins'])==3
        for mixin in config['mixins']: assert config['package'].replace('.','/')+'/'+mixin+'.class' in names
        assert 'config="legacyoftheforce.mixins.json"' in archive.read('META-INF/neoforge.mods.toml').decode()
        personal=json.loads(archive.read('legacyoftheforce.lightsaber.mixins.json'));assert personal['required'] and personal['injectors']['defaultRequire']==1 and set(personal['mixins'])=={'PlayerMenuStateMixin','PersonalMenuDropMixin','PersonalMenuCloseMixin'}
        for mixin in personal['mixins']:assert personal['package'].replace('.','/')+'/'+mixin+'.class' in names
        assert 'config="legacyoftheforce.lightsaber.mixins.json"' in archive.read('META-INF/neoforge.mods.toml').decode()
        for language in ('fr_fr','en_us'):
            language_data=json.loads(archive.read(f'assets/legacyoftheforce/lang/{language}.json'))
            assert language_data['boss.legacyoftheforce.fluid_locked'] and language_data['quest.legacyoftheforce.archive_unavailable']
            assert language_data['saber.legacyoftheforce.menu_recovery'] and language_data['saber.legacyoftheforce.menu_retained']
    for phase in (1,2):
        log=(repo/f'.tools/packaged-{run}-phase{phase}.log').read_text(encoding='utf-8-sig')
        assert 'mods folder locator' in log and 'Legacy of the Force 0.1.0-dev (legacyoftheforce)' in log and 'infrastructure loaded' in log
        assert 'Loaded 1 optional quest guides' in log
        assert f'LOTF_JAR_FLOW_PASS_{phase}' in log and 'Reward journal recovered; profiles synchronized.' in log and 'All dimensions are saved' in log
        assert 'MixinApplyError' not in log and 'InvalidInjectionException' not in log and "'/legacyoftheforce:ilum" in log
        if result.get('mixinVerbose'):
            console=(repo/f'.tools/packaged-{run}-phase{phase}-console.log').read_text(encoding='utf-8-sig')
            assert all(mixin in console for mixin in ('ArenaFluidFlowMixin','ArenaBucketMixin','ArenaBucketPickupMixin'))
            assert all(mixin in console for mixin in ('PlayerMenuStateMixin','PersonalMenuDropMixin','PersonalMenuCloseMixin'))
    level=load(world/'level.dat')['Data'];assert 'Player' not in level
    chunk=ilum.read_chunk(world/'region',0,0);assert ilum.block(chunk,0,100,0)==ilum.block(chunk,1,100,0)=='minecraft:water'
    assert ilum.block(chunk,0,99,0)==ilum.block(chunk,1,99,0)=='minecraft:stone'
    return {'jarSha256':digest,'standaloneServerPids':[p['pid'] for p in result['phases']],'distributedHarnessClasses':0,'requiredMixins':6,'nativeFlowSavedAcrossRestart':True,'ilumLoaded':True}
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('run');args=parser.parse_args();assert 1<=len(args.run)<=12 and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.run)
    print(json.dumps(verify(Path(__file__).resolve().parent.parent,args.run),indent=2))
