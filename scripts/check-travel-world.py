"""Read-only independent check of native travel journal, player aggregate and placed Ilum terminal."""
import argparse
import importlib.util
import json
from pathlib import Path
from read_nbt import load


def check(world):
    spec = importlib.util.spec_from_file_location("ilum_check", Path(__file__).with_name("check-ilum-worlds.py"))
    ilum = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(ilum)
    proof = load(world / "validation/travel-proof.nbt")
    assert proof["schemaVersion"] == 1 and proof["stage"] == "HOME"
    assert proof["awayRestartVerified"] == proof["homeRestartVerified"] == 1
    owner = ilum.uid(proof["owner"])
    player = load(world / "playerdata" / (owner + ".dat"))
    journal = load(world / "data/legacyoftheforce/travel" / (owner + ".nbt"))
    assert journal["schemaVersion"] == 1 and journal["phase"] == "DONE" and journal["kind"] == "RETURN"
    assert journal["cost"] == 0 and journal["owner"] == player["UUID"] == proof["owner"]
    atlas = player["neoforge:attachments"]["legacyoftheforce:travel_state"]
    assert atlas["schemaVersion"] == 1 and atlas["revision"] == 3 and atlas["operation"] == journal["operation"]
    assert atlas["knownPlanets"] == ["legacyoftheforce:ilum"] and atlas["returnCredit"] == 0 and "pending" not in atlas
    after = dict(journal["after"]["atlas"])
    assert after.pop("pending") == journal["operation"] and after == atlas
    assert player["Inventory"] == proof["homeInventory"] == journal["after"]["Inventory"] == journal["before"]["Inventory"]
    assert player["Inventory"] == [{"Slot": 3, "id": "minecraft:diamond", "count": 7}]
    assert proof["awayInventory"] == [{"Slot": 1, "id": "legacyoftheforce:galactic_terminal", "count": 1},
                                      {"Slot": 3, "id": "minecraft:diamond", "count": 7}]
    home = proof["home"]
    assert player["Dimension"] == home["dimension"] == "minecraft:overworld"
    assert player["Pos"] == [home[axis] for axis in ("x", "y", "z")]
    assert atlas["home"] == home and "RootVehicle" not in player
    away = proof["away"]
    assert away["dimension"] == "legacyoftheforce:ilum"
    chunks = {}
    terminals = []
    for x in range(int(away["x"]) - 3, int(away["x"]) + 4):
        for z in range(int(away["z"]) - 3, int(away["z"]) + 4):
            key = (x // 16, z // 16)
            if key not in chunks:
                chunks[key] = ilum.chunk(world, *key)
            for y in range(int(away["y"]) - 3, int(away["y"]) + 4):
                if ilum.block(chunks[key], x, y, z) == "legacyoftheforce:galactic_terminal":
                    terminals.append([x, y, z])
    assert len(terminals) == 1, "Exactly one paid portable terminal must exist in the actual Ilum region"
    return {"awayRestartVerified": True, "homeRestartVerified": True, "receiptRevision": 3,
            "returnCredit": False, "diamonds": 7, "charts": 0, "cells": 0, "carriedTerminals": 0, "placedIlumTerminals": len(terminals)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path)
    print(json.dumps(check(parser.parse_args().world)))
