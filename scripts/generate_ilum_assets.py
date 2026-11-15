"""Original Ilum worldgen definitions and block geometry; vanilla textures referenced, not copied."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"


def write(relative, value):
    path = ROOT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


DATA = "data/legacyoftheforce/"
ASSETS = "assets/legacyoftheforce/"
write(DATA + "dimension/ilum.json", {
    "type": "legacyoftheforce:ilum",
    "generator": {"type": "minecraft:noise", "settings": "minecraft:overworld",
                  "biome_source": {"type": "minecraft:fixed", "biome": "legacyoftheforce:ilum_glacial_fields"}},
})
write(DATA + "dimension_type/ilum.json", {
    "ambient_light": 0, "bed_works": True, "coordinate_scale": 1, "effects": "minecraft:overworld",
    "has_ceiling": False, "has_raids": False, "has_skylight": True, "height": 384,
    "infiniburn": "#minecraft:infiniburn_overworld", "logical_height": 384, "min_y": -64,
    "monster_spawn_block_light_limit": 0,
    "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
    "natural": True, "piglin_safe": False, "respawn_anchor_works": False, "ultrawarm": False,
})
features = [[] for _ in range(11)]
features[6] = ["minecraft:ore_coal_upper", "minecraft:ore_iron_middle", "minecraft:ore_copper"]
features[10] = ["legacyoftheforce:ilum_frost", "minecraft:freeze_top_layer"]
write(DATA + "worldgen/biome/ilum_glacial_fields.json", {
    "has_precipitation": True, "temperature": -0.7, "downfall": 0.7,
    "effects": {"fog_color": 0xB6D4E8, "sky_color": 0x83A3CE, "water_color": 0x4380A8, "water_fog_color": 0x183952},
    "carvers": {"air": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]},
    "features": features, "spawn_costs": {},
    "spawners": {
        "creature": [{"type": "minecraft:rabbit", "weight": 8, "minCount": 2, "maxCount": 3},
                     {"type": "minecraft:polar_bear", "weight": 1, "minCount": 1, "maxCount": 2}],
        "monster": [{"type": "minecraft:stray", "weight": 40, "minCount": 1, "maxCount": 3},
                    {"type": "minecraft:spider", "weight": 20, "minCount": 1, "maxCount": 2},
                    {"type": "minecraft:zombie", "weight": 10, "minCount": 1, "maxCount": 2}],
        "ambient": [{"type": "minecraft:bat", "weight": 5, "minCount": 1, "maxCount": 3}],
    },
})
write(DATA + "worldgen/configured_feature/ilum_frost.json", {
    "type": "legacyoftheforce:ilum_frost", "config": {"ridge_chance": 5, "minimum_height": 3, "maximum_height": 7},
})
write(DATA + "worldgen/placed_feature/ilum_frost.json", {
    "feature": "legacyoftheforce:ilum_frost", "placement": [{"type": "minecraft:biome"}],
})
sites = [("ilum_crystal_mine", 0, 3), ("ilum_jedi_temple", 1, 1), ("ilum_sith_sanctum", 2, 1), ("ilum_ruins", 3, 1)]
for name, kind, weight in sites:
    write(DATA + f"worldgen/structure/{name}.json", {
        "type": "legacyoftheforce:ilum_site", "site_kind": kind,
        "biomes": ["legacyoftheforce:ilum_glacial_fields"], "spawn_overrides": {},
        "step": "surface_structures", "terrain_adaptation": "none",
    })
write(DATA + "worldgen/structure_set/ilum_sites.json", {
    "structures": [{"structure": "legacyoftheforce:" + name, "weight": weight} for name, kind, weight in sites],
    "placement": {"type": "minecraft:random_spread", "salt": 172851, "spacing": 16, "separation": 8, "spread_type": "linear"},
})
write(DATA + "loot_table/blocks/ilum_crystal_cluster.json", {
    "type": "minecraft:block", "random_sequence": "legacyoftheforce:blocks/ilum_crystal_cluster",
    "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "legacyoftheforce:ilum_crystal",
               "functions": [{"function": "legacyoftheforce:identify_ilum_crystal"}]}],
               "conditions": [{"condition": "minecraft:location_check", "predicate": {"dimension": "legacyoftheforce:ilum"}},
                              {"condition": "minecraft:match_tool", "predicate": {"items": "#minecraft:pickaxes"}},
                              {"condition": "minecraft:survives_explosion"}]}],
})
for tag in ("mineable/pickaxe", "needs_iron_tool"):
    path = f"data/minecraft/tags/block/{tag}.json"
    previous = json.loads((ROOT / path).read_text(encoding="utf-8")) if (ROOT / path).exists() else {"replace": False, "values": []}
    if "legacyoftheforce:ilum_crystal_cluster" not in previous["values"]:
        previous["values"].append("legacyoftheforce:ilum_crystal_cluster")
    write(path, previous)
write(ASSETS + "blockstates/ilum_crystal_cluster.json", {"variants": {"": {"model": "legacyoftheforce:block/ilum_crystal_cluster"}}})
elements = []
for low, high, rotation in [([6, 0, 6], [10, 15, 10], None), ([3, 0, 5], [6, 10, 9], -22.5), ([10, 0, 7], [13, 9, 11], 22.5)]:
    element = {"from": low, "to": high, "faces": {side: {"texture": "#crystal"} for side in ("up", "down", "north", "south", "east", "west")}}
    if rotation is not None:
        element["rotation"] = {"origin": [8, 0, 8], "axis": "z", "angle": rotation}
    elements.append(element)
write(ASSETS + "models/block/ilum_crystal_cluster.json", {"parent": "minecraft:block/block", "textures": {"crystal": "minecraft:block/blue_ice", "particle": "minecraft:block/blue_ice"}, "elements": elements})
write(ASSETS + "models/item/ilum_crystal_cluster.json", {"parent": "legacyoftheforce:block/ilum_crystal_cluster"})
for lang, values in {
    "en_us": {"block.legacyoftheforce.ilum_crystal_cluster": "Sealed Ilum crystal cluster", "biome.legacyoftheforce.ilum_glacial_fields": "Ilum glacial fields", "dimension.legacyoftheforce.ilum": "Ilum"},
    "fr_fr": {"block.legacyoftheforce.ilum_crystal_cluster": "Gisement cristallin d’Ilum", "biome.legacyoftheforce.ilum_glacial_fields": "Étendues glaciaires d’Ilum", "dimension.legacyoftheforce.ilum": "Ilum"},
}.items():
    path = ASSETS + f"lang/{lang}.json"
    previous = json.loads((ROOT / path).read_text(encoding="utf-8"))
    previous.update(values)
    write(path, previous)
