"""Original cuboid geometry and prototype data; vanilla textures are referenced, never copied."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ASSETS = ROOT / "assets/legacyoftheforce"
DATA = ROOT / "data/legacyoftheforce"

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

textures = {"iron": "minecraft:block/iron_block", "copper": "minecraft:block/copper_block",
            "dark": "minecraft:block/black_concrete", "red": "minecraft:block/redstone_block", "blue": "minecraft:block/light_blue_concrete"}

def cuboid(start, end, material):
    return {"from": start, "to": end, "faces": {face: {"texture": "#" + material, "uv": [0, 0, 16, 16]} for face in ["up", "down", "north", "south", "east", "west"]}}

geometry = {
    "chassis_light": [cuboid([1, 4, 1], [15, 8, 15], "iron"), cuboid([2, 8, 6], [14, 10, 10], "dark")],
    "engine_standard": [cuboid([3, 3, 3], [13, 11, 13], "iron"), cuboid([2, 5, 2], [14, 9, 14], "copper"), cuboid([5, 5, 13], [11, 9, 16], "dark")],
    "battery_small": [cuboid([4, 2, 4], [12, 12, 12], "dark"), cuboid([4, 12, 4], [12, 14, 12], "red"), cuboid([6, 14, 6], [10, 15, 10], "copper")],
    "seat_pilot": [cuboid([3, 3, 3], [13, 7, 13], "dark"), cuboid([3, 7, 10], [13, 14, 13], "dark"), cuboid([6, 0, 6], [10, 3, 10], "iron")],
}
for name, elements in geometry.items():
    if name != "seat_pilot":
        elements += [cuboid([0, 7, 7], [16, 9, 9], "iron"), cuboid([7, 0, 7], [9, 16, 9], "iron"), cuboid([7, 7, 0], [9, 9, 16], "iron")]
    write(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:block/block", "textures": dict(textures, particle=textures["iron"]), "elements": elements})
write(ASSETS / "models/block/speeder_garage.json", {"parent": "minecraft:block/block", "textures": dict(textures, particle=textures["iron"]),
      "elements": [cuboid([0, 0, 0], [16, 12, 16], "iron"), cuboid([0, 12, 0], [16, 16, 16], "dark"), cuboid([3, 15.9, 3], [13, 16, 13], "blue")]})
write(ASSETS / "models/item/speeder_garage.json", {"parent": "legacyoftheforce:block/speeder_garage"})
write(ASSETS / "blockstates/speeder_garage.json", {"variants": {"": {"model": "legacyoftheforce:block/speeder_garage"}}})
write(ASSETS / "models/item/speeder_blueprint.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/paper"}})

connectors = ["up", "down", "north", "south", "east", "west"]
for name, family, mass, thrust, lift, capacity, stability in [
    ("chassis_light", "chassis", 3, 0, 0, 0, .6), ("engine_standard", "engine", 2, 25, 24, 0, .2),
    ("battery_small", "battery", 2, 0, 0, 1000, 0), ("seat_pilot", "pilot", 1, 0, 0, 0, .2),
]:
    write(DATA / f"speeder_modules/{name}.json", {"schemaVersion": 1, "family": family, "mass": mass, "thrust": thrust, "lift": lift,
          "capacity": capacity, "stability": stability, "connectors": ["down"] if family == "pilot" else connectors})

ingredients = {"I": "iron_ingot", "C": "copper_ingot", "R": "redstone", "L": "leather", "T": "crafting_table", "P": "paper", "K": "ink_sac"}
recipes = {"chassis_light": ["III", " C ", "III"], "engine_standard": [" C ", "IRI", " C "], "battery_small": [" I ", "CRC", " I "],
           "seat_pilot": ["L L", "LLL", " I "], "speeder_garage": ["III", "CTC", "IRI"], "speeder_blueprint": ["PPP", "PKP", "PPP"]}
for name, pattern in recipes.items():
    used = set("".join(pattern)) - {" "}
    write(DATA / f"recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "group": "legacyoftheforce_speeder",
          "pattern": pattern, "key": {key: {"item": "minecraft:" + ingredients[key]} for key in sorted(used)}, "result": {"id": "legacyoftheforce:" + name, "count": 1}})
write(DATA / "advancement/recipes/speeder_parts.json", {"criteria": {"iron": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": "minecraft:iron_ingot"}]}}},
      "requirements": [["iron"]], "rewards": {"recipes": ["legacyoftheforce:" + name for name in recipes]}})
write(DATA / "loot_table/blocks/speeder_garage.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "legacyoftheforce:speeder_garage"}],
      "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
for name in ["mineable/pickaxe", "needs_stone_tool"]:
    path = ROOT / f"data/minecraft/tags/block/{name}.json"
    tag = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
    if "legacyoftheforce:speeder_garage" not in tag["values"]: tag["values"].append("legacyoftheforce:speeder_garage")
    write(path, tag)
english = {
    "entity.legacyoftheforce.speeder": "Modular speeder",
    "message.legacyoftheforce.speeder_unsupported": "Speeder unavailable: unsupported modules or data. Saved design preserved.",
    "hud.legacyoftheforce.speeder": "Speeder %s blocks/s | Energy %s",
    "hud.legacyoftheforce.speeder_controls": "%s/%s drive/brake | %s/%s steer | %s dismount",
    "block.legacyoftheforce.speeder_garage": "Speeder garage", "item.legacyoftheforce.speeder_garage": "Speeder garage",
    "item.legacyoftheforce.speeder_blueprint": "Speeder blueprint", "item.legacyoftheforce.chassis_light": "Light chassis",
    "item.legacyoftheforce.engine_standard": "Standard engine", "item.legacyoftheforce.battery_small": "Small battery", "item.legacyoftheforce.seat_pilot": "Pilot seat",
    "layer": "Layer %s/3", "rotate": "Rotate", "rotation_help": "Rotation: 0 north, 1 east, 2 south, 3 west. Place modules on adjacent cells; pilot seat attaches below.",
    "example": "Base", "example_help": "Load a four-part example into the draft. No materials are included.", "save": "Save plan", "erase": "Erase",
    "stats": "Mass %s | thrust %s", "bill": "Owned %s / required %s", "power": "Lift %s | battery %s", "stability": "Stability %s",
    "plan_details": "%s modules — grid %s x %s x %s", "no_materials": "Design only: no materials or vehicle included.",
    "editing": "Unsaved design", "saved": "Plan saved", "hold_blueprint": "Open while holding a blueprint", "read_only": "Unsupported plan: read only",
    "changed": "Held blueprint changed: reopen", "too_far": "Garage unavailable", "cooldown": "Please wait",
    "error_none": "Valid design — save to keep it", "error_format": "Invalid blueprint data", "error_too_large": "Plan exceeds prototype limits",
    "error_unknown_module": "Unknown module definition", "error_outside": "Module outside the grid", "error_overlap": "Overlapping modules",
    "error_required": "Need chassis, engine, battery, pilot", "error_pilots": "Exactly one pilot seat required", "error_detached": "Disconnected module or connector",
    "error_seat_blocked": "Pilot seat access blocked", "error_propulsion": "Pilot/engine must face north; clear front", "error_too_heavy": "Vehicle is too heavy",
    "error_lift": "Not enough lift for the mass", "error_energy": "No usable battery capacity", "error_stability": "Insufficient stability",
}
french = {
    "entity.legacyoftheforce.speeder": "Speeder modulaire",
    "message.legacyoftheforce.speeder_unsupported": "Speeder indisponible : modules ou données non pris en charge. Plan sauvegardé conservé.",
    "hud.legacyoftheforce.speeder": "Speeder %s blocs/s | Énergie %s",
    "hud.legacyoftheforce.speeder_controls": "%s/%s accélérer/freiner | %s/%s tourner | %s descendre",
    "block.legacyoftheforce.speeder_garage": "Garage de speeders", "item.legacyoftheforce.speeder_garage": "Garage de speeders",
    "item.legacyoftheforce.speeder_blueprint": "Plan de speeder", "item.legacyoftheforce.chassis_light": "Châssis léger",
    "item.legacyoftheforce.engine_standard": "Moteur standard", "item.legacyoftheforce.battery_small": "Petite batterie", "item.legacyoftheforce.seat_pilot": "Siège pilote",
    "layer": "Couche %s/3", "rotate": "Rotation", "rotation_help": "Rotation : 0 nord, 1 est, 2 sud, 3 ouest. Modules adjacents ; siège attaché par-dessous.",
    "example": "Base", "example_help": "Charger un exemple de quatre pièces dans le brouillon. Aucune matière incluse.", "save": "Enregistrer", "erase": "Retirer",
    "stats": "Masse %s | poussée %s", "bill": "Possédé %s / requis %s", "power": "Portance %s | batterie %s", "stability": "Stabilité %s",
    "plan_details": "%s modules — grille %s x %s x %s", "no_materials": "Plan seul : sans matières ni véhicule.",
    "editing": "Brouillon non enregistré", "saved": "Plan enregistré", "hold_blueprint": "Ouvrir avec un plan en main", "read_only": "Format inconnu : lecture seule",
    "changed": "Plan tenu modifié : rouvrir", "too_far": "Garage indisponible", "cooldown": "Patienter",
    "error_none": "Plan valide — enregistrer", "error_format": "Données du plan invalides", "error_too_large": "Limites du prototype dépassées",
    "error_unknown_module": "Module inconnu", "error_outside": "Module hors de la grille", "error_overlap": "Modules superposés",
    "error_required": "Châssis, moteur, batterie, pilote requis", "error_pilots": "Un seul siège pilote requis", "error_detached": "Module ou connecteur détaché",
    "error_seat_blocked": "Accès au siège bloqué", "error_propulsion": "Pilote/moteur vers le nord, avant libre", "error_too_heavy": "Véhicule trop lourd",
    "error_lift": "Portance insuffisante", "error_energy": "Batterie sans capacité", "error_stability": "Stabilité insuffisante",
}
for lang, strings in [("en_us", english), ("fr_fr", french)]:
    path = ASSETS / f"lang/{lang}.json"
    entries = json.loads(path.read_text(encoding="utf-8"))
    entries.update({key if ".legacyoftheforce." in key else "speeder.legacyoftheforce." + key: value for key, value in strings.items()})
    write(path, entries)
print("Generated 4 module definitions, 6 recipes, original garage/module geometry and FR/EN labels.")
