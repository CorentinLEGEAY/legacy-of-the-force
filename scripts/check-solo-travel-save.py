"""Read-only proof of native solo dimension recovery at ACTIVE_DURABLE."""
import argparse
import importlib.util
import json
from pathlib import Path
from read_nbt import load

# Reuse independent NBT/identity checks from the adjacent CLI, whose filename has dashes.
spec = importlib.util.spec_from_file_location("owner_checker", Path(__file__).with_name("check-solo-owner-save.py"))
owner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(owner)


def verify(repo, kill, recover):
    source = owner.profile(repo, kill) / "saves/TravelValidation"
    target_profile = owner.profile(repo, recover)
    target = target_profile / "saves/TravelValidation"
    cut = load(source / "validation/solo-owner-checkpoint.nbt")
    proof = load(target / "validation/solo-owner-save.nbt")
    stopped = json.loads((repo / f".tools/travel-solo-{kill}-result.json").read_text(encoding="utf-8-sig"))
    resumed = json.loads((repo / f".tools/travel-solo-{recover}-result.json").read_text(encoding="utf-8-sig"))
    assert cut["mode"] == "KillTravel" and proof["mode"] == "RecoverTravel" and proof["passed"] == 1
    assert stopped["forced"] and stopped["exitCode"] == -1 and stopped["pid"] == cut["pid"]
    assert not resumed["forced"] and resumed["exitCode"] == 0 and resumed["pid"] == proof["pid"] and resumed["pid"] != stopped["pid"]
    baseline = owner.generation(cut["before"])
    for tag in [cut["before"], cut["embedded"], load(source / "level.dat")["Data"]["Player"], proof["levelFile"]["Data"]["Player"]]:
        owner.inventory(tag, 7)
        assert tag["Dimension"] == "minecraft:overworld" and owner.atlas(tag)["revision"] == 3 and owner.generation(tag) == baseline
    native = cut["durable"]; identity = owner.uid(native["UUID"])
    assert native == load(source / f"playerdata/{identity}.dat") and native["Inventory"] == cut["memory"]["Inventory"]
    assert owner.generation(native) == baseline + 5
    expected_inventory = [{"Slot": 1, "id": "legacyoftheforce:galactic_terminal", "count": 1}, {"Slot": 3, "id": "minecraft:diamond", "count": 9}]
    for tag in [native, cut["memory"], proof["before"], proof["embedded"], load(target / f"playerdata/{identity}.dat"), load(target / "level.dat")["Data"]["Player"]]:
        assert tag["Inventory"] == expected_inventory and owner.uid(tag["UUID"]) == identity
        assert owner.generation(tag) == baseline + 5 and tag["Dimension"] == "legacyoftheforce:ilum" and tag["Pos"] == native["Pos"]
        atlas = owner.atlas(tag)
        assert atlas == owner.atlas(native) and atlas["revision"] == 4 and atlas["returnCredit"] == 1 and "pending" not in atlas
    before = cut["journal"]; after = proof["journal"]
    assert before["phase"] == "ARRIVED" and after["phase"] == "DONE" and before["kind"] == after["kind"] == "OUTBOUND"
    assert before["cost"] == after["cost"] == 2 and before["operation"] == after["operation"]
    assert before["after"] == after["after"] and before["before"] == after["before"]
    assert after == load(target / f"data/legacyoftheforce/travel/{identity}.nbt")
    ack = load(target_profile / "validation/solo-travel-client.nbt")
    assert ack["schemaVersion"] == 1 and ack["pid"] == resumed["pid"] and owner.uid(ack["owner"]) == identity and ack["dimension"] == "legacyoftheforce:ilum"
    cut_log = (repo / f".tools/travel-solo-{kill}-console.log").read_text(encoding="utf-8-sig")
    resumed_log = (repo / f".tools/travel-solo-{recover}-console.log").read_text(encoding="utf-8-sig")
    assert "SOLO_TRAVEL_PAUSED_ACTIVE_DURABLE" in cut_log and "Stopping server" not in cut_log and "All dimensions are saved" not in cut_log
    assert "Recovered newer native solo player checkpoint before placement" in resumed_log and "SOLO_TRAVEL_CLIENT_ILUM_ACK" in resumed_log and "All dimensions are saved" in resumed_log
    return {"abruptSoloJvmPid": stopped["pid"], "restartedSoloJvmPid": resumed["pid"], "restoredDimension": "legacyoftheforce:ilum", "paidCells": 2, "diamonds": 9, "journal": "DONE", "generation": baseline + 5}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kill"); parser.add_argument("recover")
    args = parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.kill, args.recover), indent=2))
