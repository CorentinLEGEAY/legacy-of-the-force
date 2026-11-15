"""Read-only cross-check of two real native clients, server snapshots, receipts and saved players."""
import argparse
import json
from pathlib import Path
import uuid
from read_nbt import load


def uid(values):
    return str(uuid.UUID("".join(f"{v & 0xffffffff:08x}" for v in values)))


def atlas(player):
    return player["neoforge:attachments"]["legacyoftheforce:travel_state"]


def inventory(player, terminal):
    expected = [{"Slot": 3, "id": "minecraft:diamond", "count": 7}]
    if terminal:
        expected.insert(0, {"Slot": 1, "id": "legacyoftheforce:galactic_terminal", "count": 1})
    assert player["Inventory"] == expected and "RootVehicle" not in player


def verify(repo, run, solo=False):
    assert 1 <= len(run) <= 12 and all(c in "abcdefghijklmnopqrstuvwxyz0123456789_-" for c in run)
    world = repo / (f"run-travel-client-solo-{run}/saves/TravelValidation" if solo else f"run-travel-network-{run}/world")
    proof = load(world / "validation/travel-network-server.nbt")
    expected = 1 if solo else 2
    assert proof["schemaVersion"] == 1 and proof["passed"] == 1 and proof["clients"] == expected
    assert {p["name"] for p in proof["actors"]} == ({"ForceTesterSolo"} if solo else {"ForceTesterOne", "ForceTesterTwo"})
    assert len({uid(p["owner"]) for p in proof["actors"]}) == expected and len({p["endpoint"] for p in proof["actors"]}) == expected
    result = json.loads((repo / (f".tools/travel-solo-{run}-result.json" if solo else f".tools/travel-network-{run}-result.json")).read_text(encoding="utf-8-sig"))
    if solo:
        assert result["exitCode"] == 0 and not result["forced"] and proof["serverPid"] == result["pid"]
    else:
        assert result["observedProofs"] and result["serverExitCode"] == 0 and len(result["clients"]) == 2
        assert all(p["exitCode"] == 0 for p in result["clients"]) and len({p["pid"] for p in result["clients"]}) == 2
    reports = []
    for actor in proof["actors"]:
        assert actor["memoryConnection"] == int(solo)
        if not solo:
            assert "127.0.0.1" in actor["endpoint"]
        inventory(actor["arrival"], True); inventory(actor["returned"], False)
        away, back = atlas(actor["arrival"]), atlas(actor["returned"])
        assert away["revision"] == 2 and away["returnCredit"] == 1 and "pending" not in away
        assert back["revision"] == 3 and back["returnCredit"] == 0 and "pending" not in back
        assert actor["arrival"]["Dimension"] == "legacyoftheforce:ilum" and actor["returned"]["Dimension"] == "minecraft:overworld"
        native = load(world / "playerdata" / (uid(actor["owner"]) + ".dat")); inventory(native, False)
        assert native["Inventory"] == actor["returned"]["Inventory"] and atlas(native) == back
        assert native["Pos"] == [actor["home"][c] for c in ("x", "y", "z")]
        if solo:
            embedded = load(world / "level.dat")["Data"]["Player"]
            assert embedded["Inventory"] == native["Inventory"] and atlas(embedded) == back
        journal = load(world / "data/legacyoftheforce/travel" / (uid(actor["owner"]) + ".nbt"))
        assert journal == actor["journal"] and journal["phase"] == "DONE" and journal["kind"] == "RETURN" and journal["cost"] == 0
        receipt = dict(journal["after"]["atlas"]); assert receipt.pop("pending") == journal["operation"] and receipt == back
        suffix = "one" if actor["name"] == "ForceTesterOne" else "two"
        directory = repo / (f"run-travel-client-solo-{run}" if solo else f"run-travel-client-{suffix}-{run}")
        client = load(directory / "validation/travel-network-client.nbt")
        assert client["passed"] == 1 and client["owner"] == actor["owner"] and client["name"] == actor["name"]
        launch = result if solo else next(p for p in result["clients"] if p["name"] == actor["name"]); assert client["clientPid"] == launch["pid"]
        assert not list(directory.glob("hs_err_pid*.log")), "Native graphics crash detected"
        steps = client["steps"]
        assert [s["label"] for s in steps] == ["initial", "invalid_ignored", "discovery_ack", "arrival_ack", "home_ack"]
        assert [(s["charts"], s["cells"], s["terminals"], s["diamonds"]) for s in steps] == [(1, 2, 1, 7), (1, 2, 1, 7), (0, 2, 1, 7), (0, 0, 0, 7), (0, 0, 0, 7)]
        assert [s["known"] for s in steps] == [0, 0, 1, 1, 1] and [s["credit"] for s in steps] == [0, 0, 0, 1, 0]
        assert steps[3]["dimension"] == "legacyoftheforce:ilum" and steps[4]["dimension"] == "minecraft:overworld"
        assert steps[3]["playersSeen"] >= expected and steps[4]["playersSeen"] >= expected
        assert steps[4]["tick"] > steps[3]["tick"] > steps[2]["tick"] > steps[1]["tick"] > steps[0]["tick"]
        reports.append({"name": actor["name"], "nativeClientPid": client["clientPid"], "actualSocket": not solo, "revision": 3, "diamonds": 7})
    server_log = (repo / (f".tools/travel-solo-{run}-console.log" if solo else f".tools/travel-network-{run}-server.log")).read_text(encoding="utf-8-sig")
    assert f"TRAVEL_NETWORK_PAIRED clients={expected}" in server_log and f"TRAVEL_NETWORK_BOTH_ARRIVED clients={expected}" in server_log
    assert f"TRAVEL_NETWORK_SERVER_PASS clients={expected}" in server_log and "All dimensions are saved" in server_log
    return {"nativeClients": expected, "integratedServer": solo, "tamperedPacketsIgnored": True, "replayedRequestsPaidOnce": True, "actors": reports}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run")
    parser.add_argument("--solo", action="store_true")
    args = parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.run, args.solo), indent=2))
