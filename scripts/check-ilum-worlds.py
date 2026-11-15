"""Read-only independent checks of Ilum region blocks, structures, mined crystal and seeded replica."""
import argparse
import gzip
import io
import json
from pathlib import Path
import struct
import uuid
import zlib
from read_nbt import load


def uid(values):
    return str(uuid.UUID("".join(f"{value & 0xffffffff:08x}" for value in values)))


def chunk(world, x, z):
    region = world / "dimensions/legacyoftheforce/ilum/region" / f"r.{x // 32}.{z // 32}.mca"
    with region.open("rb") as source:
        source.seek(4 * ((x & 31) + (z & 31) * 32))
        location = struct.unpack(">I", source.read(4))[0]
        sector, sectors = location >> 8, location & 255
        assert sector >= 2 and sectors > 0, "Missing native chunk"
        source.seek(sector * 4096)
        length = struct.unpack(">I", source.read(4))[0]
        assert 1 < length <= sectors * 4096 - 4, "Invalid region record"
        assert source.read(1) == b"\x02", "Expected bounded inline zlib chunk"
        compressed = source.read(length - 1)
        assert len(compressed) == length - 1
    decoder = zlib.decompressobj()
    raw = decoder.decompress(compressed, 4 * 1024 * 1024 + 1)
    assert len(raw) <= 4 * 1024 * 1024 and decoder.eof, "Chunk exceeds budget or is truncated"
    return load(io.BytesIO(gzip.compress(raw)))


def block(native, x, y, z):
    section = next(s for s in native["sections"] if s["Y"] == y // 16)
    states = section["block_states"]
    palette = states["palette"]
    if len(palette) == 1:
        return palette[0]["Name"]
    bits = max(4, (len(palette) - 1).bit_length())
    per_long = 64 // bits
    index = (y & 15) * 256 + (z & 15) * 16 + (x & 15)
    selected = (states["data"][index // per_long] >> (index % per_long * bits)) & ((1 << bits) - 1)
    return palette[selected]["Name"]


def verify(world, restarted):
    proof = load(world / "validation/ilum-proof.nbt")
    assert proof["schemaVersion"] == 1 and len(proof["sites"]) == 4
    if restarted:
        confirmed = load(world / "validation/ilum-verified.nbt")
        expected = dict(proof, restartVerified=1)
        assert confirmed == expected, "Native restart verification differs from original evidence"
    for site in proof["sites"]:
        x, _, z = site["located"]
        native = chunk(world, x // 16, z // 16)
        stored = native["structures"]["starts"]["legacyoftheforce:" + site["id"]]
        assert stored["Children"] == [site["piece"]], "Native structure piece differs"
        assert any("legacyoftheforce:ilum_glacial_fields" in section.get("biomes", {}).get("palette", []) for section in native["sections"]), "Ilum biome missing from real chunk"
        if site["id"] == "ilum_crystal_mine":
            a, b, c, dx, dy, dz = site["piece"]["BB"]
            assert block(native, a + 7, b + 1, c + 8) in ("minecraft:air", "minecraft:cave_air"), "Mined node regenerated"
            marker = proof["marker"]
            assert block(native, *marker) == "minecraft:diamond_block", "Player edit lost"
            assert all(block(native, bx, by, bz) != "legacyoftheforce:ilum_crystal_cluster"
                       for bx in range(a, dx + 1) for by in range(b, dy + 1) for bz in range(c, dz + 1)), "Source duplicated after mining"
    player = load(world / "playerdata" / (uid(proof["harvester"]) + ".dat"))
    assert player["Inventory"] == proof["Inventory"] and "RootVehicle" not in player
    crystals = [dict(stack) for stack in player["Inventory"] if stack["id"] == "legacyoftheforce:ilum_crystal"]
    assert len(crystals) == 1
    crystals[0].pop("Slot")
    assert crystals[0] == proof["crystal"] and crystals[0]["count"] == 1
    data = crystals[0]["components"]["legacyoftheforce:kyber"]
    assert data["originPlanet"] == "legacyoftheforce:ilum" and data["baseKind"] == "blue"
    assert .55 <= data["purity"] <= .90 and .60 <= data["stability"] <= .95
    return proof


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path)
    parser.add_argument("--replica", type=Path)
    args = parser.parse_args()
    primary = verify(args.world, True)
    result = {"restartVerified": True, "sites": len(primary["sites"]), "seed": primary["seed"], "nativeCrystalCount": 1, "minedNodeCount": 0}
    if args.replica:
        replica = verify(args.replica, False)
        assert replica["seed"] == primary["seed"] and replica["sites"] == primary["sites"], "Fresh seeded generation differs"
        assert primary["crystal"]["components"]["legacyoftheforce:kyber"]["crystalId"] != replica["crystal"]["components"]["legacyoftheforce:kyber"]["crystalId"], "Independent crystals must have distinct identities"
        result["freshSeedReplicaMatches"] = True
    print(json.dumps(result))
