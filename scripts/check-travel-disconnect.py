"""Cross-check native socket interruption, reconnect and paid travel without editing evidence."""
import argparse
import importlib.util
import json
from pathlib import Path
from read_nbt import load

spec = importlib.util.spec_from_file_location("network_checker", Path(__file__).with_name("check-travel-network.py"))
network = importlib.util.module_from_spec(spec)
spec.loader.exec_module(network)


def verify(repo, run):
    report = network.verify(repo, run)
    world = repo / f"run-travel-network-{run}/world"
    paused = load(world / "validation/disconnect-paused.nbt")
    closed = load(world / "validation/disconnect-closed.nbt")
    final = load(world / "validation/travel-network-server.nbt")
    result = json.loads((repo / f".tools/travel-network-{run}-result.json").read_text(encoding="utf-8-sig"))
    cut = result["cutClient"]
    assert cut["name"] == "ForceTesterOne" and cut["exitCode"] == -1
    assert cut["checkpoint"] == paused["checkpoint"] == closed["checkpoint"]
    assert paused["schemaVersion"] == closed["schemaVersion"] == 1 and paused["serverPid"] == final["serverPid"]
    assert paused["connectedBefore"] == 1 and closed["connectedAfter"] == 0 and closed["actorStillRegistered"] == 1
    assert {k: v for k, v in closed.items() if k not in {"connectedAfter", "actorStillRegistered"}} == paused
    first = next(p for p in final["actors"] if p["name"] == "ForceTesterOne")
    assert first["owner"] == paused["owner"] and first["endpoint"] != paused["endpoint"]
    assert "127.0.0.1" in first["endpoint"] and "127.0.0.1" in paused["endpoint"]
    initial = {p["name"]: p["pid"] for p in result["initialClients"]}
    ended = {p["name"]: p["pid"] for p in result["clients"]}
    assert cut["pid"] == initial["ForceTesterOne"] != ended["ForceTesterOne"]
    assert initial["ForceTesterTwo"] == ended["ForceTesterTwo"]
    prefix = load(repo / f"run-travel-client-one-{run}/validation/travel-network-resume.nbt")
    client = load(repo / f"run-travel-client-one-{run}/validation/travel-network-client.nbt")
    assert prefix["owner"] == client["owner"] == paused["owner"] and prefix["clientPid"] == client["previousClientPid"] == cut["pid"]
    assert client["steps"][:3] == prefix["steps"]
    journal = paused["journal"]
    assert journal["kind"] == "OUTBOUND" and journal["cost"] == 2 and journal["owner"] == paused["owner"]
    assert paused["native"]["Inventory"] == journal["after"]["Inventory"]
    assert paused["native"]["Inventory"] == [{"Slot": 1, "id": "legacyoftheforce:galactic_terminal", "count": 1}, {"Slot": 3, "id": "minecraft:diamond", "count": 7}]
    atlas = network.atlas(paused["native"])
    assert atlas["revision"] == 2 and atlas["returnCredit"] == 1
    if paused["checkpoint"] in {"PAID", "TELEPORTED"}:
        assert journal["phase"] == "PAID" and paused["native"]["Dimension"] == "minecraft:overworld" and atlas["pending"] == journal["operation"]
    else:
        assert paused["checkpoint"] == "ACTIVE_DURABLE" and journal["phase"] == "ARRIVED" and paused["native"]["Dimension"] == "legacyoftheforce:ilum" and "pending" not in atlas
    assert paused["memory"]["Dimension"] == ("minecraft:overworld" if paused["checkpoint"] == "PAID" else "legacyoftheforce:ilum")
    assert paused["memory"]["Inventory"] == paused["native"]["Inventory"]
    arrived = network.atlas(first["arrival"])
    assert arrived["operation"] == journal["operation"] and "pending" not in arrived
    log = (repo / f".tools/travel-network-{run}-server.log").read_text(encoding="utf-8-sig")
    assert "Travel retained for recovery" not in log and "TRAVEL_NETWORK_SERVER_FAILED" not in log
    assert log.count("TRAVEL_DISCONNECT_SOCKET_CLOSED") == log.count("TRAVEL_DISCONNECT_REJOINED") == 1
    assert log.count("ForceTesterOne joined the game") == 2 and log.count("ForceTesterTwo joined the game") == 1
    assert log.index("TRAVEL_DISCONNECT_PAUSED") < log.index("TRAVEL_DISCONNECT_SOCKET_CLOSED") < log.index("ForceTesterOne left the game") < log.index("TRAVEL_DISCONNECT_REJOINED") < log.index("TRAVEL_NETWORK_SERVER_PASS")
    rejoin_path = world / "validation/disconnect-rejoin-before.nbt"
    if rejoin_path.exists():
        before = load(rejoin_path); after = load(world / "validation/disconnect-rejoin-after.nbt")
        assert before["owner"] == after["owner"] == paused["owner"] and before["journal"] == after["journal"] and after["journal"]["phase"] == "DONE"
        assert not after["occupied"] and after["safe"]
        for tag in [before["native"], before["live"], after["native"], after["live"]]:
            assert tag["Inventory"] == paused["native"]["Inventory"] and network.atlas(tag) == arrived and tag["Dimension"] == "legacyoftheforce:ilum"
        assert before["native"]["Pos"] == before["live"]["Pos"] and after["native"]["Pos"] == after["live"]["Pos"]
        if before["occupied"]:
            assert before["live"]["Pos"] != after["live"]["Pos"] and "Relocated occupied travel rejoin locally without payment" in log
        else:
            assert before["live"]["Pos"] == after["live"]["Pos"]
        if "standingBox" in before:
            a, b = before["standingBox"], before["peerBox"]
            overlap = all(a[i] < b[i+3] and a[i+3] > b[i] for i in range(3))
            assert overlap == bool(before["occupied"])
            a, b = after["standingBox"], after["peerBox"]
            assert not all(a[i] < b[i+3] and a[i+3] > b[i] for i in range(3))
        report["rejoinWasOccupied"] = bool(before["occupied"])
    return {**report, "socketClosedDuring": paused["checkpoint"], "cutClientPid": cut["pid"], "reconnectedClientPid": ended["ForceTesterOne"], "peerStayedConnectedPid": ended["ForceTesterTwo"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run")
    args = parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parent.parent, args.run), indent=2))
