"""Independent read-only checks of dedicated Ilum boss fights, personal receipts and native altar loot."""
import argparse
import gzip
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import struct
import uuid
import zlib
from read_nbt import load

spec = importlib.util.spec_from_file_location("ilum_reader", Path(__file__).with_name("check-ilum-worlds.py"))
ilum = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ilum)


def name_uuid(text):
    return str(uuid.UUID(bytes=hashlib.md5(text.encode("utf-8")).digest(), version=3))


def entity_chunk(world, x, z):
    path = world / f"dimensions/legacyoftheforce/ilum/entities/r.{x//32}.{z//32}.mca"
    if not path.exists() or path.stat().st_size == 0:
        return []
    with path.open("rb") as source:
        source.seek(4*((x & 31)+(z & 31)*32)); value = struct.unpack(">I",source.read(4))[0]
        if value == 0:
            return []
        sector, sectors = value >> 8, value & 255
        assert sector >= 2 and sectors > 0
        source.seek(sector*4096); length=struct.unpack(">I",source.read(4))[0]
        assert 1 < length <= sectors*4096-4 and source.read(1) == b"\x02"
        compressed=source.read(length-1)
    decoder=zlib.decompressobj(); raw=decoder.decompress(compressed,4*1024*1024+1)
    assert decoder.eof and len(raw) <= 4*1024*1024
    return load(io.BytesIO(gzip.compress(raw))).get("Entities",[])


def verify(repo, run, require_natural=False):
    assert 1 <= len(run) <= 12 and all(c in "abcdefghijklmnopqrstuvwxyz0123456789_-" for c in run)
    world=repo/f"run-boss-{run}/world"; log=(repo/f".tools/boss-{run}-server.log").read_text(encoding="utf-8-sig")
    assert "All dimensions are saved" in log and "Boss arenas paused" not in log and "failed at" not in log and "Encountered an unexpected exception" not in log
    reports=[]; previous_guardian=None; owners=set(); encounter_ids=set(); server_pids=set(); natural_count=0
    for scenario in ["guardian","frost_lord","cooldown"]:
        proof=load(world/f"validation/boss-{scenario}.nbt")
        assert proof["schemaVersion"] == proof["passed"] == 1
        assert proof["iceSeen"] == proof["signatureReleased"] == proof["actualDamage"] == 1 and {1,2,3} <= set(proof["phases"])
        assert f"BOSS_GAME_TEST_PASS kind={scenario.upper()}" in log
        server_pids.add(proof["serverPid"]); encounter=ilum.uid(proof["encounter"]); assert encounter not in encounter_ids; encounter_ids.add(encounter)
        kind=proof["kind"]; boss_id="legacyoftheforce:ilum_guardian" if kind == "GUARDIAN" else "legacyoftheforce:ilum_frost_lord"
        delta=proof["expectedXpDelta"]; assert delta == (0 if scenario == "cooldown" else 800)
        arena=proof["arena"]; assert arena["state"] == "COOLDOWN" and arena["encounter"] == proof["encounter"] and arena["kind"] == kind and arena["dimension"] == "legacyoftheforce:ilum"
        arena_id=name_uuid(f"legacyoftheforce:ilum/{proof['altar']}/{kind}")
        assert load(world/f"data/legacyoftheforce/bosses/arenas/{arena_id}.nbt") == arena
        current_owners=set(); relics=0; cells=0
        for actor in proof["players"]:
            identity=ilum.uid(actor["owner"]); current_owners.add(identity); native=actor["native"]
            profile=native["neoforge:attachments"]["legacyoftheforce:player_progress"]
            assert profile["totalXpEarned"] == actor["initialXp"]+delta
            assert profile["alignmentTenths"] == actor["initialAlignment"]+(0 if delta == 0 else -250 if kind == "GUARDIAN" else 250)
            assert actor["status"] == ("AWARDED" if delta else "DECLINED") and profile["bossRewardCooldowns"][boss_id] > 72000
            receipt_id=name_uuid(f"boss/{encounter}/{identity}")
            receipt=load(world/f"data/legacyoftheforce/rewards/receipts/{receipt_id[:3]}/{receipt_id}.nbt")
            assert ilum.uid(receipt["owner"]) == identity and ilum.uid(receipt["event"]) == receipt_id and receipt["accepted"] == bool(delta)
            saved=load(world/f"playerdata/{identity}.dat")
            final_profile=saved["neoforge:attachments"]["legacyoftheforce:player_progress"]
            assert saved["Inventory"] == native["Inventory"] and final_profile["totalXpEarned"] == profile["totalXpEarned"] and final_profile["alignmentTenths"] == profile["alignmentTenths"]
            assert native["Dimension"] == saved["Dimension"] == "legacyoftheforce:ilum" and "RootVehicle" not in saved
            relics+=sum(i["count"] for i in native["Inventory"] if i["id"] == "legacyoftheforce:ilum_boss_relic")
            cells+=sum(i["count"] for i in native["Inventory"] if i["id"] == "legacyoftheforce:speeder_energy_cell")
        assert len(current_owners) == 2 and relics == 1 and cells == 4
        if scenario == "guardian":
            previous_guardian=current_owners
        elif scenario == "cooldown":
            assert current_owners == previous_guardian and arena["lootOwners"] == []
        else:
            assert current_owners.isdisjoint(previous_guardian)
        if delta:
            assert set(arena["lootOwners"]) == current_owners
        tile=proof["altarNative"]; x,y,z=tile["x"],tile["y"],tile["z"]
        actual=ilum.chunk(world,x//16,z//16)
        container=next(e for e in actual["block_entities"] if (e["x"],e["y"],e["z"]) == (x,y,z))
        assert container["lotfSchemaVersion"] == 1 and container["lootVictory"] == proof["encounter"] and container["Items"] == []
        assert ilum.block(actual,x,y,z) == ("legacyoftheforce:jedi_trial_altar" if kind == "GUARDIAN" else "legacyoftheforce:sith_trial_altar")
        if scenario != "cooldown" and require_natural:
            assert "generatedSite" in proof, "Natural structure proof required"
        if "generatedSite" in proof:
            site=proof["generatedSite"]; start=ilum.chunk(world,*site["startChunk"])["structures"]["starts"][site["id"]]
            assert len(start["Children"]) == 1 and start["Children"][0]["BB"] == site["bounds"]
            a,b,c,_,_,_=site["bounds"]; assert (x,y,z) == (a+5,b+1,c+8)
            assert site["id"] == ("legacyoftheforce:ilum_jedi_temple" if kind == "GUARDIAN" else "legacyoftheforce:ilum_sith_sanctum")
            chunks={}
            def state(bx,by,bz):
                key=(bx//16,bz//16)
                if key not in chunks: chunks[key]=ilum.chunk(world,*key)
                return ilum.block(chunks[key],bx,by,bz)
            assert all(state(a+dx,b,c+dz) == "minecraft:polished_andesite" for dx in range(11) for dz in range(11))
            pillar="minecraft:quartz_pillar" if kind == "GUARDIAN" else "minecraft:polished_blackstone_bricks"
            assert all(state(a+dx,b+dy,c+dz) == pillar for dx in (2,8) for dz in (2,8) for dy in range(1,5))
            assert all(state(a+dx,b+5,c+dz) == "minecraft:sea_lantern" for dx in (2,8) for dz in (2,8))
            assert state(a+5,b+6,c+5) == ("minecraft:quartz_bricks" if kind == "GUARDIAN" else "minecraft:deepslate_bricks")
            natural_count+=1
        entities=[e for dx in (-1,0,1) for dz in (-1,0,1) for e in entity_chunk(world,x//16+dx,z//16+dz)]
        assert all(e.get("UUID") != proof["encounter"] for e in entities), "Defeated boss remains in native entity store"
        owners |= current_owners; reports.append({"scenario":scenario,"contributors":2,"personalXpDelta":delta,"sharedRelicsTotal":relics,"sharedCellsTotal":cells})
    safety=load(world/"validation/boss-safety.nbt")
    assert safety["schemaVersion"] == safety["passed"] == 1 and "BOSS_GAME_TEST_PASS kind=SAFETY" in log
    for key in ["canceledDeath","nativeHopperBlocked","nativeBreakBlocked","pistonBlocked","staleEpochRetired","resetCooldown","futureSchemaPreserved","outsideDamageRefused"]:
        assert safety[key] == 1
    assert safety["player"]["neoforge:attachments"]["legacyoftheforce:player_progress"]["totalXpEarned"] == 0
    assert not list((world/"data/legacyoftheforce/bosses/victories").glob("*.nbt")) and len(server_pids) == 1 and len(owners) == 4
    return {"dedicatedServerPid":next(iter(server_pids)),"naturalStructures":natural_count,"nativeScenarios":reports,"safetyAssertions":8,"pendingVictories":0}


if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("run");parser.add_argument("--natural",action="store_true");args=parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parent.parent,args.run,args.natural),indent=2))
