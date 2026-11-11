"""Read-only cross-check of kill evidence, payment journal/player data and the native entity region."""
import argparse
import gzip
import io
import json
import math
from pathlib import Path
import struct
import uuid
import zlib
from read_nbt import load


def uid(values):
    return str(uuid.UUID("".join(f"{value & 0xffffffff:08x}" for value in values)))


def region_entities(world, pose):
    cx, cz = math.floor(pose["x"]) // 16, math.floor(pose["z"]) // 16
    region = world / "entities" / f"r.{cx // 32}.{cz // 32}.mca"
    with region.open("rb") as source:
        source.seek(4 * ((cx & 31) + (cz & 31) * 32))
        location = struct.unpack(">I", source.read(4))[0]
        sector, sectors = location >> 8, location & 255
        assert sector >= 2 and sectors > 0, "Entity chunk is not stored"
        source.seek(sector * 4096)
        length = struct.unpack(">I", source.read(4))[0]
        assert 1 < length <= sectors * 4096 - 4, "Invalid region record length"
        compression = source.read(1)[0]
        payload = source.read(length - 1)
        assert len(payload) == length - 1 and compression == 2, "Expected bounded inline zlib entity chunk"
    decoder = zlib.decompressobj()
    raw = decoder.decompress(payload, 4 * 1024 * 1024 + 1)
    assert len(raw) <= 4 * 1024 * 1024 and decoder.eof, "Entity chunk exceeds budget or is truncated"
    return load(io.BytesIO(gzip.compress(raw)))["Entities"]


def verify(repo, run):
    assert run and len(run) <= 32 and all(c in "abcdefghijklmnopqrstuvwxyz0123456789_-" for c in run)
    prefix = repo / ".tools" / ("recharge-crash-" + run)
    read_json = lambda suffix: json.loads(Path(str(prefix) + suffix).read_text(encoding="utf-8-sig"))
    meta, termination = read_json("-meta.json"), read_json("-termination.json")
    fixture = load(Path(str(prefix) + "-fixture.nbt"))
    before = load(Path(str(prefix) + "-journal.nbt"))
    native_before = load(Path(str(prefix) + "-player.dat"))
    assert termination["run"] == run and termination["checkpoint"] == meta["checkpoint"] == fixture["checkpoint"]
    assert termination["processId"] == fixture["preparationPid"] and termination["exitCode"] == -1 and not termination["shutdownCommand"]
    world = repo / "run-recharge-crash" / "world"
    verified = load(world / "validation/recharge-crash" / (run + "-verified.nbt"))
    after = load(world / "data/legacyoftheforce/speeder_recharge" / (meta["owner"] + ".nbt"))
    native_after = load(world / "playerdata" / (meta["owner"] + ".dat"))
    unpaid = meta["checkpoint"] == "PAYMENT_MEMORY"
    assert before["phase"] == {"PAYMENT_MEMORY": "PREPARED", "ENERGY_DURABLE": "PAID", "ACTIVATED": "APPLIED"}[meta["checkpoint"]]
    phase = "ABORTED" if unpaid else "DONE"
    expected = before["before" if unpaid else "after"]
    previous = dict(before)
    previous["phase"] = phase
    assert after == previous == verified["journal"], "Transaction changed beyond its phase"
    key = "legacyoftheforce:speeder_recharge_receipt"
    for player in (native_before, native_after, verified["nativePlayer"]):
        assert player["Inventory"] == expected["Inventory"]
        assert player["neoforge:attachments"][key] == expected["receipt"]
        assert "RootVehicle" not in player and uid(player["UUID"]) == meta["owner"]
    assert expected["receipt"]["revision"] == (0 if unpaid else 1)
    assert uid(after["owner"]) == meta["owner"] and uid(after["vehicle"]["vehicleId"]) == meta["vehicle"]
    matches = [entity for entity in region_entities(world, after["vehicle"]) if uid(entity["UUID"]) == meta["vehicle"]]
    assert len(matches) == 1, "Paid UUID is missing or duplicated in its native chunk"
    state = matches[0]["vehicleState"]
    assert uid(state["vehicleId"]) == meta["vehicle"] and uid(state["ownerId"]) == meta["owner"]
    assert state["blueprint"] == after["vehicle"]["blueprint"] and state["moduleHealth"] == after["vehicle"]["moduleHealth"]
    assert "rechargeId" not in state and "assemblyId" not in state and state["energy"] == (100 if unpaid else 7)
    result = dict(run=run, checkpoint=meta["checkpoint"], killedPid=termination["processId"], phase=phase,
                  owner=meta["owner"], vehicle=meta["vehicle"], nativeUuidCount=len(matches),
                  receiptRevision=expected["receipt"]["revision"], finalCharge=state["energy"], noConnectedClient=True)
    for path, suffix in [(world / "validation/recharge-crash" / (run + "-verified.nbt"), "-recovered.nbt"),
                         (world / "data/legacyoftheforce/speeder_recharge" / (meta["owner"] + ".nbt"), "-after-journal.nbt"),
                         (world / "playerdata" / (meta["owner"] + ".dat"), "-after-player.dat")]:
        Path(str(prefix) + suffix).write_bytes(path.read_bytes())
    Path(str(prefix) + "-verified.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run")
    args = parser.parse_args()
    verify(Path(__file__).resolve().parent.parent, args.run)
