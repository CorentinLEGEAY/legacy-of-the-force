"""Independently verify abrupt solo-owner checkpoint recovery, without editing evidence."""
import argparse
import json
import uuid
from pathlib import Path
from read_nbt import load

def uid(values):
    return str(uuid.UUID("".join(f"{v & 0xffffffff:08x}" for v in values)))


def atlas(tag):
    return tag["neoforge:attachments"]["legacyoftheforce:travel_state"]


def profile(repo, run):
    assert 1 <= len(run) <= 12 and all(c in "abcdefghijklmnopqrstuvwxyz0123456789_-" for c in run)
    return repo / f"run-travel-client-solo-{run}"


def inventory(tag, diamonds):
    assert tag["Inventory"] == [{"Slot": 3, "id": "minecraft:diamond", "count": diamonds}]


def generation(tag):
    value = tag.get("neoforge:attachments", {}).get("legacyoftheforce:save_checkpoint")
    if value is None:
        return 0
    assert value["schemaVersion"] == 1 and value["generation"] >= 0
    return value["generation"]


def verify(repo, kill, recover):
    source = profile(repo, kill) / "saves/TravelValidation"
    destination = profile(repo, recover) / "saves/TravelValidation"
    checkpoint = load(source / "validation/solo-owner-checkpoint.nbt")
    proof = load(destination / "validation/solo-owner-save.nbt")
    stopped = json.loads((repo / f".tools/travel-solo-{kill}-result.json").read_text(encoding="utf-8-sig"))
    resumed = json.loads((repo / f".tools/travel-solo-{recover}-result.json").read_text(encoding="utf-8-sig"))
    assert checkpoint["schemaVersion"] == proof["schemaVersion"] == 1
    assert checkpoint["mode"] == "Kill" and proof["mode"] == "Recover" and proof["passed"] == 1
    assert stopped["forced"] and stopped["exitCode"] == -1 and stopped["pid"] == checkpoint["pid"]
    assert not resumed["forced"] and resumed["exitCode"] == 0 and resumed["pid"] == proof["pid"]
    assert resumed["pid"] != stopped["pid"]
    for tag in [checkpoint["before"], checkpoint["embedded"], checkpoint["levelFile"]["Data"]["Player"], load(source / "level.dat")["Data"]["Player"]]:
        inventory(tag, 7); assert generation(tag) == 0
    actor_id = uid(checkpoint["durable"]["UUID"])
    native_source = load(source / f"playerdata/{actor_id}.dat")
    assert native_source == checkpoint["durable"]
    inventory(native_source, 9); assert generation(native_source) == 1
    # The reopened world's file was still stale while native owner placement already used generation1.
    inventory(proof["levelFile"]["Data"]["Player"], 7)
    for tag in [proof["before"], proof["embedded"], load(destination / f"playerdata/{actor_id}.dat"), load(destination / "level.dat")["Data"]["Player"]]:
        inventory(tag, 9); assert generation(tag) == 1
        assert uid(tag["UUID"]) == actor_id and atlas(tag) == atlas(native_source)
        assert tag["Dimension"] == native_source["Dimension"] and tag["Pos"] == native_source["Pos"]
    kill_log = (repo / f".tools/travel-solo-{kill}-console.log").read_text(encoding="utf-8-sig")
    recovery_log = (repo / f".tools/travel-solo-{recover}-console.log").read_text(encoding="utf-8-sig")
    assert "SOLO_OWNER_SAVE_PAUSED_AFTER_BARRIER" in kill_log
    assert "Stopping server" not in kill_log and "All dimensions are saved" not in kill_log
    assert "Recovered newer native solo player checkpoint before placement" in recovery_log
    assert "SOLO_OWNER_SAVE_RECOVER_PASS" in recovery_log and "All dimensions are saved" in recovery_log
    return {"abruptSoloJvmPid": stopped["pid"], "restartedSoloJvmPid": resumed["pid"], "staleEmbeddedDiamonds": 7, "recoveredDiamonds": 9, "checkpointGeneration": 1}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kill"); parser.add_argument("recover")
    args = parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.kill, args.recover), indent=2))
