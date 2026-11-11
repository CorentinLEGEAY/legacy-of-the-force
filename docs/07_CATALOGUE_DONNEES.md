# 07 — Modèles de données et catalogues

> Les exemples ci-dessous sont des **formats conceptuels** pour les contrats et fichiers JSON du mod. Ils ne sont pas encore des registres NeoForge ni des fichiers directement chargeables. Codex définit leur codec et leur validation après P0.

## Identifiants
Tous les IDs uniques sont namespacés : `legacyoftheforce:force_push`, `legacyoftheforce:ilum`, `legacyoftheforce:crystal_blue`, etc. Les instances vivantes portent des UUID si nécessaire.

## Profil joueur — schéma conceptuel
```json
{
  "schemaVersion": 1,
  "level": 1,
  "xpInLevel": 0,
  "alignmentTenths": 0,
  "forceEnergy": 100,
  "skillPoints": 0,
  "knownPowers": ["legacyoftheforce:force_sense"],
  "powerMastery": {},
  "unlockedPlanetIds": [],
  "bossRewardCooldowns": {}
}
```
Ne pas confondre niveau, énergie, alignement et maîtrise des pouvoirs. L'alignement est en dixièmes d'un point.

## Cristal / sabre
```json
{
  "schemaVersion": 1,
  "crystalId": "uuid-placeholder",
  "originPlanet": "legacyoftheforce:ilum",
  "baseKind": "blue",
  "visibleColor": "blue",
  "purity": 0.71,
  "stability": 0.84,
  "resonance": "defense",
  "bondLevel": 1,
  "bondProgress": 0,
  "state": "natural"
}
```
```json
{
  "schemaVersion": 1,
  "saberId": "uuid-placeholder",
  "type": "single",
  "parts": {
    "emitter": "legacyoftheforce:emitter_basic",
    "neck": "legacyoftheforce:neck_basic",
    "grip": "legacyoftheforce:grip_basic",
    "control": "legacyoftheforce:switch_basic",
    "circuit": "legacyoftheforce:circuit_basic",
    "pommel": "legacyoftheforce:pommel_basic"
  },
  "crystalId": "uuid-placeholder",
  "visualPalette": { "main": "silver", "accent": "black" }
}
```
Le cristal peut être incorporé dans les données persistées de l'item, mais l'UUID et les règles d'unicité doivent rester cohérents.

## Plan de speeder
```json
{
  "schemaVersion": 1,
  "blueprintId": "uuid-placeholder",
  "name": "Eclaireur",
  "gridSize": [5, 3, 3],
  "modules": [
    {"kind": "legacyoftheforce:chassis_light", "at": [2, 1, 1], "rotation": 0},
    {"kind": "legacyoftheforce:seat_pilot", "at": [2, 2, 1], "rotation": 0},
    {"kind": "legacyoftheforce:engine_standard", "at": [1, 1, 1], "rotation": 0},
    {"kind": "legacyoftheforce:battery_small", "at": [3, 1, 1], "rotation": 0}
  ],
  "paint": {"primary":"#343A40", "secondary":"#C7463D"}
}
```
Le partage d'un plan ne transfère **aucun matériau**, et l'atelier doit contrôler l'intégralité des modules avant assemblage.

Implémentation P3-01 : ce JSON illustre le contenu métier ; le composant ItemStack emploie une enveloppe NBT schéma 1. Les UUID sont des tableaux UUID NBT (`blueprintId`, `authorId`), `gridSize` et `at` des tableaux d'entiers, `modules` une liste de composés et les couleurs des entiers RGB bornés `primary`/`secondary` à la racine (normalisation binaire du champ `paint` illustré ci-dessus). `name` est borné à 40 caractères sans contrôle. Le plan ne contient ni charge restante, ni inventaire, ni UUID d'instance ; chaque véhicule P3-02 devra recevoir sa propre identité. Le prototype de l'éditeur utilise 5×3×3 et quatre IDs physiques ; les formats/classes non pris en charge restent intacts en lecture seule. Les données réelles de modules sont sous `data/legacyoftheforce/speeder_modules/` ; voir `validation/P3-01.md` pour les bornes et validations actuelles.

## État d'instance de speeder (P3-02 en cours)

`VehicleState`, enveloppe NBT schéma 1, distincte du composant de plan : `vehicleId` et `ownerId` UUID ; `blueprint` copie complète du design ; `moduleHealth` tableau d'entiers 0–100 dans le même ordre que les modules ; `dimension` ID ; `x/y/z`, `speed/verticalSpeed/yaw/energy` doubles finis et bornés ; `garaged` octet booléen strict. Budget 64 Kio ; pose horizontale ±30 millions de blocs, verticale ±2048 ; yaw [-180,180]. Les types absents ou incorrects sont refusés. Schéma futur conservé sans mutation ; ID de module inconnu conservé structurellement, sans autoriser sa simulation. Le plan reste partageable et ne reçoit jamais la charge ou l'identité de l'instance. `assemblyId`, UUID temporaire de publication, immobilise l'instance tant que le coordinateur n'a pas prouvé et activé son assemblage. Pas de cargo implémenté dans cette première enveloppe ; ne pas prétendre un inventaire de véhicule livré. Paiement, création et sauvegarde monde sont intégrés ; validations complètes restent en cours.

Reçu joueur `legacyoftheforce:speeder_assembly_receipt`, dans `neoforge:attachments` : `schemaVersion=1` entier, `revision` long ≥0, `operation` UUID obligatoire quand révision >0. Schéma futur conservé, mutation refusée ; budget 1 Kio. Journal par joueur : `schemaVersion=1`, `owner`/`operation` UUID, `phase` string connue, `before`/`after` (Inventory native + receipt), `vehicle` initial gelé ; budget 256 Kio. Révision après = avant +1 et UUID après = opération. Ces images sont des preuves de paiement ; après DONE, seules les données d'entité donnent l'état vivant. Voir `validation/P3-02_ASSEMBLY.md`.

## Définition de planète
```json
{
  "schemaVersion": 1,
  "id": "legacyoftheforce:ilum",
  "displayName": "Ilum",
  "dimensionId": "legacyoftheforce:ilum",
  "primaryCrystal": "legacyoftheforce:crystal_blue",
  "rareSpeederPart": "legacyoftheforce:cryogenic_thruster",
  "bosses": ["legacyoftheforce:ilum_jedi", "legacyoftheforce:ilum_sith"]
}
```

## Définition de boss
```json
{
  "schemaVersion": 1,
  "id": "legacyoftheforce:ilum_sith",
  "planet": "legacyoftheforce:ilum",
  "alignmentRewardTenths": 250,
  "xpReward": 1000,
  "phases": [0.70, 0.30],
  "specialActions": ["legacyoftheforce:force_push", "legacyoftheforce:ice_projectile"],
  "arena": "legacyoftheforce:ilum_sith_arena"
}
```

## Validation des schémas
- IDs connus, `schemaVersion` entier, couleurs et valeurs bornées, pas de null là où un champ est requis.
- Recette/plan impossible → refus avec motif sans consommation d'objets.
- Ne pas accepter un UUID fourni par un client comme preuve de propriété.
- Les tables de loot/recettes/worldgen réels doivent utiliser les systèmes data-driven de Minecraft quand c'est possible.
