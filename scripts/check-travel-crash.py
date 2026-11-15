"""Independent read-only verification of actual travel checkpoints, journals and native player files."""
import argparse
import json
from pathlib import Path
import uuid
from read_nbt import load


def uid(values):
    return str(uuid.UUID("".join(f"{value & 0xffffffff:08x}" for value in values)))


def atlas(player):
    return player["neoforge:attachments"]["legacyoftheforce:travel_state"]


def verify(repo, run):
    assert 1 <= len(run) <= 32 and all(c in "abcdefghijklmnopqrstuvwxyz0123456789_-" for c in run)
    world = repo / "run-travel-crash/world"
    prefix = world / "validation/travel-crash" / run
    fixture, paused, proof = [load(Path(str(prefix) + suffix + ".nbt")) for suffix in ("", "-paused", "-verified")]
    assert fixture["schemaVersion"] == 1 and fixture["run"] == paused["run"] == proof["run"] == run and proof["verified"] == 1
    owner = uid(fixture["owner"])
    journal = load(world / "data/legacyoftheforce/travel" / (owner + ".nbt"))
    player = load(world / "playerdata" / (owner + ".dat"))
    initial, checkpoint = paused["journal"], fixture["checkpoint"]
    assert initial["owner"] == player["UUID"] == fixture["owner"] and initial["kind"] == fixture["kind"]
    unpaid = checkpoint in ("PREPARED", "PAYMENT_MEMORY")
    active = checkpoint in ("ACTIVE_DURABLE", "DONE")
    expected_at_pause = initial["before" if unpaid else "after"]
    pause_atlas = dict(expected_at_pause["atlas"])
    if active:
        pause_atlas.pop("pending")
    assert paused["nativePlayer"]["Inventory"] == expected_at_pause["Inventory"] and atlas(paused["nativePlayer"]) == pause_atlas
    expected = initial["before" if unpaid else "after"]
    final_atlas = dict(expected["atlas"])
    if not unpaid:
        assert final_atlas.pop("pending") == initial["operation"]
    phase = "ABORTED" if unpaid else "DONE"
    assert dict(initial, phase=phase) == journal == proof["journal"]
    assert proof["recoveredPlayer"]["Inventory"] == expected["Inventory"] and atlas(proof["recoveredPlayer"]) == final_atlas
    assert player["Inventory"] == proof["laterPlayer"]["Inventory"] and atlas(player) == final_atlas == atlas(proof["laterPlayer"])
    diamonds = [s for s in player["Inventory"] if s["id"] == "minecraft:diamond"]
    assert len(diamonds) == 1 and diamonds[0]["count"] == 9
    recovered_diamonds = [s for s in proof["recoveredPlayer"]["Inventory"] if s["id"] == "minecraft:diamond"]
    assert recovered_diamonds[0]["count"] == 7
    assert "pending" not in final_atlas and "RootVehicle" not in player
    anchor = initial["source" if unpaid else "target"]
    assert player["Dimension"] == proof["recoveredPoint"]["dimension"] == anchor["dimension"]
    assert abs(player["Pos"][0] - anchor["x"]) <= 9 and abs(player["Pos"][2] - anchor["z"]) <= 9
    assert player["Pos"] == proof["recoveredPlayer"]["Pos"] == proof["laterPlayer"]["Pos"]
    assert player["Rotation"] == proof["laterPlayer"]["Rotation"] != proof["recoveredPlayer"]["Rotation"]
    if checkpoint == "TELEPORTED":
        assert paused["nativePlayer"]["Dimension"] == initial["source"]["dimension"]
        assert paused["livePoint"]["dimension"] == initial["target"]["dimension"], "Must actually cut between live teleport and native destination save"
    if fixture["kind"] == "RETURN":
        assert initial["cost"] == 0 and final_atlas["returnCredit"] == int(unpaid)
    else:
        assert initial["cost"] == 2 and final_atlas["returnCredit"] == int(not unpaid)
    killed = paused["mode"] == "KILL"
    if killed:
        evidence = repo / ".tools" / ("travel-crash-" + run)
        read_json = lambda suffix: json.loads(Path(str(evidence) + suffix).read_text(encoding="utf-8-sig"))
        meta, termination = read_json("-meta.json"), read_json("-termination.json")
        assert termination["processId"] == paused["pausePid"] == fixture["preparationPid"]
        assert termination["exitCode"] == -1 and not termination["shutdownCommand"]
        assert meta["run"] == termination["run"] == run and meta["checkpoint"] == checkpoint == termination["checkpoint"]
        assert load(Path(str(evidence) + "-journal.nbt")) == initial and load(Path(str(evidence) + "-player.dat")) == paused["nativePlayer"]
        kill_log = Path(str(evidence) + "-server.log").read_text(encoding="utf-8-sig")
        assert "VALIDATION_TRAVEL_PAUSED run=" + run in kill_log
        assert "VALIDATION_TRAVEL_PAUSE_EXPIRED" not in kill_log and "Stopping server" not in kill_log
        recover_log = Path(str(evidence) + "-recover-console.log").read_text(encoding="utf-8-sig")
        assert "TRAVEL_CRASH_RECOVER_PASS run=" + run in recover_log and "All dimensions are saved" in recover_log
    return dict(run=run, kind=fixture["kind"], checkpoint=checkpoint, killedJava=killed,
                phase=phase, revision=final_atlas["revision"], dimension=player["Dimension"], laterDiamonds=9)


def verify_safety(repo, run):
    world = repo / "run-travel-crash/world"
    proof = load(world / "validation/travel-crash" / (run + "-safety.nbt"))
    assert proof["verified"] == 1 and proof["run"] == run
    initial, final = proof["initialJournal"], proof["finalJournal"]
    assert initial["phase"] == "ARRIVED" and dict(initial, phase="DONE") == final
    before, after = proof["initialPlayer"], proof["finalPlayer"]
    assert before["Inventory"] == after["Inventory"] == initial["after"]["Inventory"]
    expected = dict(atlas(before)); expected.pop("pending", None)
    assert atlas(after) == expected and "pending" not in atlas(after)
    player = load(world / "playerdata" / (uid(proof["owner"]) + ".dat"))
    assert player["Inventory"] == after["Inventory"] and atlas(player) == atlas(after)
    assert player["Pos"] == after["Pos"] == [proof["to"][axis] for axis in ("x", "y", "z")]
    assert player["Dimension"] == proof["to"]["dimension"] == after["Dimension"]
    journal = load(world / "data/legacyoftheforce/travel" / (uid(proof["owner"]) + ".nbt"))
    assert journal == final
    if proof["hazard"] == "CLAIM":
        assert proof["blockedJournal"] == initial and proof["blockedPlayer"] == before
    elif proof["hazard"] == "FALLBACK":
        home, fallback, target = atlas(before)["home"], initial["fallback"], initial["target"]
        assert proof["kind"] == "RETURN" and atlas(after)["home"] == home
        assert abs(proof["to"]["x"] - fallback["x"]) <= 9 and abs(proof["to"]["z"] - fallback["z"]) <= 9
        assert max(abs(proof["to"]["x"] - target["x"]), abs(proof["to"]["z"] - target["z"])) > 9
    else:
        assert proof["hazard"] == "LAVA" and proof["from"] != proof["to"]
    return dict(run=run, kind=proof["kind"], checkpoint=proof["checkpoint"], hazard=proof["hazard"], phase="DONE", inventoryUnchanged=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run")
    parser.add_argument("--sweep")
    parser.add_argument("--safety")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    if args.safety:
        print(json.dumps([verify_safety(repo, f"{args.safety}-{index}") for index in range(2)], indent=2))
    elif args.sweep:
        cases = [f"{args.sweep}-{kind}-{index}" for kind in ("outbound", "return") for index in range(10)]
        print(json.dumps([verify(repo, run) for run in cases], indent=2))
    else:
        assert args.run, "Choose --run or --sweep"
        print(json.dumps([verify(repo, args.run)], indent=2))
