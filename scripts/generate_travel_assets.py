"""Original terminal geometry, recipes and translations; reference vanilla textures only."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"


def write(path, value):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


DATA = "data/legacyoftheforce/"
ASSETS = "assets/legacyoftheforce/"
write(DATA + "recipe/galactic_terminal.json", {
    "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["IGI", "CRC", "III"],
    "key": {"I": {"item": "minecraft:iron_ingot"}, "G": {"item": "minecraft:glass"},
            "C": {"item": "minecraft:copper_ingot"}, "R": {"item": "minecraft:redstone"}},
    "result": {"id": "legacyoftheforce:galactic_terminal", "count": 2},
})
write(DATA + "recipe/ilum_coordinates.json", {
    "type": "minecraft:crafting_shapeless", "category": "misc",
    "ingredients": [{"item": "minecraft:" + item} for item in ("compass", "paper", "amethyst_shard", "lapis_lazuli")],
    "result": {"id": "legacyoftheforce:ilum_coordinates", "count": 1},
})
write(DATA + "loot_table/blocks/galactic_terminal.json", {
    "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "legacyoftheforce:galactic_terminal"}],
                                         "conditions": [{"condition": "minecraft:survives_explosion"}]}],
})
path = "data/minecraft/tags/block/mineable/pickaxe.json"
previous = json.loads((ROOT / path).read_text(encoding="utf-8"))
if "legacyoftheforce:galactic_terminal" not in previous["values"]:
    previous["values"].append("legacyoftheforce:galactic_terminal")
write(path, previous)
write(ASSETS + "blockstates/galactic_terminal.json", {"variants": {"": {"model": "legacyoftheforce:block/galactic_terminal"}}})
elements = []
for low, high, texture in [([0, 0, 0], [16, 3, 16], "metal"), ([5, 3, 5], [11, 11, 11], "metal"),
                           ([1, 10, 4], [15, 16, 12], "metal"), ([2, 11, 3.8], [14, 15, 4], "screen")]:
    elements.append({"from": low, "to": high, "faces": {side: {"texture": "#" + texture} for side in ("up", "down", "north", "south", "east", "west")}})
write(ASSETS + "models/block/galactic_terminal.json", {
    "parent": "minecraft:block/block", "textures": {"metal": "minecraft:block/iron_block", "screen": "minecraft:block/sea_lantern", "particle": "minecraft:block/iron_block"},
    "elements": elements,
})
write(ASSETS + "models/item/galactic_terminal.json", {"parent": "legacyoftheforce:block/galactic_terminal"})
write(ASSETS + "models/item/ilum_coordinates.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/map"}})
texts = {
    "en_us": {
        "block.legacyoftheforce.galactic_terminal": "Galactic navigation terminal", "item.legacyoftheforce.ilum_coordinates": "Ilum star chart",
        "decode": "Read chart", "depart": "Go to Ilum", "return": "Return to base", "ilum": "Ilum · rare blue crystals", "locked": "Ilum · chart required",
        "cost": "Cells: %s · departure + return", "ready": "Take a second terminal for the return.", "discovered": "Ilum discovered. Chart consumed.",
        "return_reserved": "Return already paid · no cell needed", "ready_return": "Ready to return to your saved base.",
        "arrived": "Journey completed.", "known": "Ilum is already known.", "coordinates_required": "Read an Ilum chart first.", "chart_required": "An Ilum star chart is required.",
        "fuel_required": "Not enough energy cells.", "portable_required": "Carry a second terminal for the return.", "no_return": "No return journey available here.",
        "wrong_origin": "Depart for Ilum from the Overworld.", "occupied": "Dismount before travelling.", "combat": "Wait until combat ends.", "cooldown": "Navigation is recharging.",
        "clear_cursor": "Put away the item held by the cursor.", "permission": "Navigation is not permitted here.", "no_safe_arrival": "Arrival blocked: no payment taken.",
        "unavailable": "Navigation data unavailable.", "pending": "Journey retained for recovery. Please reconnect.",
        "help": "Craft two terminals. Place one at home and take the other to Ilum. Read a star chart to unlock Ilum. Departure pays for the return; place your portable terminal there to return. No rare crystal is needed to craft these supplies.",
    },
    "fr_fr": {
        "block.legacyoftheforce.galactic_terminal": "Terminal de navigation galactique", "item.legacyoftheforce.ilum_coordinates": "Carte stellaire d’Ilum",
        "decode": "Lire la carte", "depart": "Vers Ilum", "return": "Retour à la base", "ilum": "Ilum · cristaux bleus rares", "locked": "Ilum · carte requise",
        "cost": "Cellules : %s · aller + retour", "ready": "Emportez un terminal pour le retour.", "discovered": "Ilum découverte. Carte consommée.",
        "return_reserved": "Retour déjà payé · aucune cellule", "ready_return": "Prêt à revenir à votre base enregistrée.",
        "arrived": "Voyage effectué.", "known": "Ilum est déjà connue.", "coordinates_required": "Lisez d’abord une carte d’Ilum.", "chart_required": "Une carte stellaire d’Ilum est requise.",
        "fuel_required": "Cellules énergétiques insuffisantes.", "portable_required": "Emportez un terminal pour le retour.", "no_return": "Aucun retour disponible ici.",
        "wrong_origin": "Partez vers Ilum depuis le monde normal.", "occupied": "Descendez de votre monture.", "combat": "Attendez la fin du combat.", "cooldown": "La navigation se recharge.",
        "clear_cursor": "Rangez l’objet tenu par le curseur.", "permission": "Navigation interdite ici.", "no_safe_arrival": "Arrivée bloquée : aucun paiement.",
        "unavailable": "Données de navigation indisponibles.", "pending": "Voyage conservé pour reprise. Reconnectez-vous.",
        "help": "Fabriquez deux terminaux. Posez-en un à la base et emportez l’autre sur Ilum. Lisez une carte stellaire pour débloquer Ilum. Le départ finance le retour : posez le terminal transporté sur place pour revenir. Aucun cristal rare n’est requis pour ces recettes.",
    },
}
for lang, entries in texts.items():
    path = ASSETS + f"lang/{lang}.json"
    previous = json.loads((ROOT / path).read_text(encoding="utf-8"))
    previous.update({key if key.startswith(("block.", "item.")) else "travel.legacyoftheforce." + key: value for key, value in entries.items()})
    write(path, previous)
