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

Recyclage P3-03 : champ optionnel `salvage` dans chaque JSON `speeder_modules`, liste de0–8 objets `{"item":"minecraft:iron_nugget","count":9}`. Item enregistré non air ; quantité entière1–64. Absence du champ = module non recyclable, pas un rendement inventé. Rendement final arrondi vers le bas par pièce selon ses HP. Les données initiales ne rendent que fer/cuivre/cuir. Le devis est figé dans l'image d'inventaire du journal d'entretien schéma4 ; une reprise ne le recalcule pas à partir d'un datapack éventuellement modifié. Archives natives d'UUID retirés sous `data/legacyoftheforce/retired_speeders`, schéma1, à conserver avec la sauvegarde ; détail dans `validation/P3-03_RECYCLE.md`.

`VehicleState`, enveloppe NBT schéma 1, distincte du composant de plan : `vehicleId` et `ownerId` UUID ; `blueprint` copie complète du design ; `moduleHealth` tableau d'entiers 0–100 dans le même ordre que les modules ; `dimension` ID ; `x/y/z`, `speed/verticalSpeed/yaw/energy` doubles finis et bornés ; `garaged` octet booléen strict. Budget 64 Kio ; pose horizontale ±30 millions de blocs, verticale ±2048 ; yaw [-180,180]. Les types absents ou incorrects sont refusés. Schéma futur conservé sans mutation ; ID de module inconnu conservé structurellement, sans autoriser sa simulation. Le plan reste partageable et ne reçoit jamais la charge ou l'identité de l'instance. `assemblyId`, UUID temporaire de publication, immobilise l'instance tant que le coordinateur n'a pas prouvé et activé son assemblage. Pas de cargo implémenté dans cette première enveloppe ; ne pas prétendre un inventaire de véhicule livré. Paiement, création et sauvegarde monde sont intégrés ; validations complètes restent en cours.

Reçu joueur `legacyoftheforce:speeder_assembly_receipt`, dans `neoforge:attachments` : `schemaVersion=1` entier, `revision` long ≥0, `operation` UUID obligatoire quand révision >0. Schéma futur conservé, mutation refusée ; budget 1 Kio. Journal par joueur : `schemaVersion=1`, `owner`/`operation` UUID, `phase` string connue, `before`/`after` (Inventory native + receipt), `vehicle` initial gelé ; budget 256 Kio. Révision après = avant +1 et UUID après = opération. Ces images sont des preuves de paiement ; après DONE, seules les données d'entité donnent l'état vivant. Voir `validation/P3-02_ASSEMBLY.md`.

## Définition de planète

P4-01 Ilum : `dimension/ilum.json`, `dimension_type/ilum.json`, biome `worldgen/biome/ilum_glacial_fields.json`, feature `ilum_frost` et structure set pondéré `ilum_sites`. Générateur de bruit vanilla référencé par ID, givre et plans de sites originaux. Les quatre structures utilisent le type `legacyoftheforce:ilum_site` avec `site_kind`0mine/1templeJedi/2sanctuaireSith/3ruines ; pièce native11×16×11 sauvegardée avec `schemaVersion=1` et `siteKind`. Champs opaques conservés ; schéma futur conservé en lecture seule sans génération. Les blocs ordinaires et métadonnées de structure suivent les chunks Minecraft, sans hook de régénération au login/unload. Butin du gisement : fonction serveur `identify_ilum_crystal` créant le composant kyber existant, schéma1, avec UUID et qualités bornées. La table exige dimension Ilum et pioche ; le tier fer est imposé par tags vanilla.
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
### Collecte du butin P4-03
Attachment joueur `legacyoftheforce:boss_loot_receipt`, schéma1 : revision long≥0, UUID operation obligatoire après révision0, extensions conservées ; même agrégat natif que l'inventaire, copie à la mort. Journal `data/legacyoftheforce/bosses/loot/<owner>.nbt` schéma1≤262144octets : owner/operation/victory UUID, dimension Ilum, altar long, phase PREPARED/DELIVERED/DONE/ABORTED, images before/after (Inventory natif + receipt), loot de neuf emplacements maximum. Delta exact contrôlé avec composants opaques, aucune modification d'armure/main secondaire. Format futur refusé/conservé. Autel schéma1 étendu par UUID optionnels pendingClaim/lastClaim ; ancien format sans ces champs toujours lisible. Journal, reçu joueur et régions font partie de la même sauvegarde du monde ; ne pas restaurer un seul de ces fichiers isolément. Protocole/preuves : validation/P4-03_LOOT.md.

### Boss Ilum P4-03 — formats implémentés
IDs réels : entités `ilum_guardian`, `ilum_frost_lord`, `arena_ice` ; autels blocs/items `jedi_trial_altar`, `sith_trial_altar` ; objet `ilum_boss_relic`. Les exemples de boss ci-dessus sont des propositions de catalogue, pas les IDs enregistrés de cette tranche.

Sous `data/legacyoftheforce/bosses` : arènes schéma1, identifiant stable dérivé dimension/position/type, rencontre et boot UUID, état SPAWNING/ACTIVE/SETTLING/COOLDOWN, readyAt et lootOwners ; intentions temporaires de victoire dans victories. Entités boss et glace ont des données NBT versionnées ; boss futur conservé inerte. Autel natif à neuf emplacements : lotfSchemaVersion1, inventaire vanilla et UUID de publication. Aucun objet Java direct sauvegardé. Ces fichiers, les régions de blocs/entités, playerdata et les reçus RPG doivent rester dans la même sauvegarde. Migration/recovery et comportement de la glace temporaire restent à éprouver avant validation globale.

### Atlas et journal de voyage P4-02
Attachment natif joueur `legacyoftheforce:travel_state` schéma1 : `revision`long, `operation`UUID si révision>0, `knownPlanets`liste bornée (Ilum uniquement dans cette tranche), `returnCredit`booléen, `home`point Overworld, `pending`UUID optionnel, `readyAt`/`combatUntil`longs non négatifs. Point : dimension ID, xyz doubles finis, yaw/pitch floats bornés. Extensions retenues ; atlas≤8192octets, format futur lu/conservé sans action.

Journal `data/legacyoftheforce/travel/<owner>.nbt` schéma1≤262144octets : owner/opération UUID, kind DISCOVER/OUTBOUND/RETURN, phase PREPARED/PAID/ARRIVED/DONE/ABORTED, images before/after d'inventaire natif et atlas, source/target/fallback optionnel, cost entier. Inventaire≤41emplacements natifs distincts ; débit exact d'une carte ou de cellules, aucun remplacement/ajout/changement de composant. DONE est un reçu, pas une sauvegarde à réappliquer. Le journal et le fichier joueur natif font partie de la même sauvegarde du monde.

- IDs connus, `schemaVersion` entier, couleurs et valeurs bornées, pas de null là où un champ est requis.
- Recette/plan impossible → refus avec motif sans consommation d'objets.
- Ne pas accepter un UUID fourni par un client comme preuve de propriété.
- Les tables de loot/recettes/worldgen réels doivent utiliser les systèmes data-driven de Minecraft quand c'est possible.

### Guide optionnel P4-04
`quests/echo_of_ilum.json` schéma1 : clés title/origin/clue, steps (id,text,condition HOLOCRON/ILUM/CRYSTAL), repeatable=false, dependencies=[], rewards={xp:0,alignmentTenths:0}. Ce premier interpréteur refuse dépendances/répétitions/récompenses inconnues ; il ne prétend pas gérer les futurs PNJ/factions. Catalogue≤32, fichier≤8192caractères,8étapes par guide. Erreur : catalogue inactif, connaissance conservée. Textes FR/EN distribués ; un datapack ajoutant des clés doit fournir sa localisation client.
Attachment `legacyoftheforce:quest_knowledge` schéma1≤16384octets : revision long non négatif, holocrons≤32IDs canoniques uniques≤128caractères, visitedIlum booléen, crystal UUID non nul optionnel. Champs inconnus retenus ; schéma futur intact/inactif. Identité du premier cristal possédé conservée, pas une revendication de propriété exclusive. Aucun inventaire ou alignement parallèle.
Worldgen `echo_ruin` et placement `echo_ruins` (spacing32/separation16/salt192851) en données ; une pièce7×5×7 confinée au chunk, schéma1, un holocron. Géométrie originale, textures Minecraft de cuivre/améthyste/pierre ; pas d’asset extrait d’un tiers. Journal minimal texte avec coches et navigation, sans dépendre uniquement de la couleur ; filtres avancés/marqueurs restent P7.

Reload P4-04 : JSON non parsable ignoré par SimpleJsonResourceReloadListener natif ; définition JSON parsable mais schéma/récompense invalide désactive le catalogue dans apply. Les découvertes enregistrées restent intactes. Nouvelle ouverture : clés archive_unavailable pour contenu manquant ; clic refusé avec motif, aucune nouvelle connaissance/récompense. Reload valide et désactivation d'un override rétablissent les textes sans changer les révisions personnelles (preuve qc2).

## Extensions du lot sabres : types, assemblages et plans

- `saber_parts/*.json` reste en schema1 : emplacements mécaniques finis, montage commun, types compatibles ; longueur0,1–8 et rayon0,1–6 unités (garde élargie), stabilité±0,25. Modificateurs facultatifs `damage`±2, `speed`±0,4, `cost`±0,3 et `parry`±0,1 ; absence=0. Les caractéristiques historiques des sept premières pièces sont conservées.
- `saber_types/{single,short,curved,double,crossguard}.json` : cinq profils schema1 complets, multiplicateurs `damage`/`speed`0,5–1,5, `reach`2–4, `cost`0,5–2 et `parry`±0,2. Profil incomplet/invalide désactive le calcul des types. Le serveur recalcule les stats ; le rendu ne fournit aucune valeur de dégâts.
- Composant `saber_assembly` : lecture schema1 simple et schema2 étendu ; UUID `saberId`, `type`, six références sous `parts`, cristal facultatif, activation et condition bornée. `secondaryEmitter` requis seulement pour double, compatible et compté séparément dans la facture. Les versions futures restent conservées/inactives. Les mutations de design conservent les identités du sabre et du cristal et désactivent la lame.
- Composant de plan : schema1 simple ou schema2 étendu ; références/type/second émetteur, aucun UUID de sabre/cristal ni objet possédé. Lecture contrôlée enseigne une connaissance structurelle ; attachement `saber_knowledge` schema1, liste bornée de types, copiedOnDeath et format futur opaque. Les plans de types avancés doivent être distribués par le contenu P6 ; ils ne sont pas gratuitement générés depuis une sélection non apprise.
- Le pipeline `generate_saber_design.py` puis `generate_saber_expansion.py` écrit modèles/recettes/catalogues cohérents. Le modèle `lightsaber` utilise le renderer composite client ; les pièces individuelles restent des modèles cuboïdes originaux avec ancrages communs. Le circuit est sous carénage. En GUI la silhouette de poignée remplit la case, tandis que la lame active apparaît en main et au sol. Compatibilité visuelle et campagnes de conservation non encore validées pour ce lot.

## Kyber schema2 et affinités

Le même item technique `ilum_crystal` conserve ses composants historiques ; le nom/rendu dépendent de la provenance. Les couples reconnus sont Ilum/blue/defense, Tatooine/yellow/endurance, Endor/green/restoration, Mustafar/orange/offense, Dagobah/cyan/telekinesis, Jedha/purple/technique. Schema1 bleu naturel encore lu ; schema2 exige origine/famille/résonance cohérentes, état natural/corrupted/purified et visibleColor correspondant (couleur d'origine/red/white), qualité0–1, lien0–4 et historique de pratique valide. Un cristal dormant n'active aucune lame.

`ritualHistory` version1 contient acteur UUID, tick long, compteurs entiers de corruptions/purifications ; corrompu : c=p+1, purifié : c=p≥1, plafond technique1million. L'origine et l'UUID ne changent jamais ; les versions inconnues restent intactes et inactives. La sauvegarde du rite contient ressources et nouvel état dans le même agrégat joueur.

`kyber_affinities/<planet>.json` forme un catalogue complet de six profils serveur schema1, aux multiplicateurs bornés : parade, endurance, régénération, télékinésie, pouvoirs avancés, dégâts, croissance par lien et compromis de concentration. Le serveur recalcule les effets ; le client montre états/description et reçoit les statistiques de l'atelier. Les matériaux partagés `ilum_frost_alloy`, `tatooine_silica`, `endor_resin`, `mustafar_slag`, `dagobah_essence`, `jedha_relic_dust` sont des items physiques sans inventaire caché ; leur acquisition naturelle doit être raccordée au contenu P6.


Jedha (lot C non validé) : `jedha_site` schema1 conserve0 chambre /1 archive /2 ruines et ajoute3 temple /4 sanctuaire ; registre d’arène `jedha_temple_guardian` et `jedha_relic_lord`. Sous-état d’entité `sacredCombat`1 : vecteurs origin/aim, garde0..100, canal/âge0..60 et somme≤60, action NONE/RELIC_LIGHTNING/RELIC_STORM, cible UUID, contre et masque de drain (bits des phases2/3 uniquement). Projection client BEAM indépendante schema1 avec extrémité bornée/coupée par le serveur ; aucun calcul de dégâts client. `lotfArenaIce`2 ajoute le matériau `relic_anchor`, ID de projection8 ; ancien format1 et matériaux2 précédents préservés.


Formes : attachement `saber_discipline` schema1, `learned` liste unique de4 IDs maximum parmi precision/ward/momentum/twin, `selected` basic ou une forme connue, `nextChange` long≥0. Limite4KiB, formats futurs conservés/inactifs. Holocrons4items techniques sans inventaire caché, pile16max, consommation+savoir dans l’agrégat natif joueur. Les paires de sabres ne sont jamais des items ni des sauvegardes : deux UUID normaux et une clé de combo runtime ordonnée, fenêtres non restaurées. Génération des appareils/pools : `scripts/generate_combat_holocrons.py`, après `generate_planet_expansion.py` complet. La forme n’est pas un composant de cristaux et ne change pas leurs UUID.

Journal d’expéditions : `quests/expedition_<planète>.json` schemaVersion2, planet explicite, conditions VISITED_PLANET/PLANET_CRYSTAL/BOSS et subject ResourceLocation canonique. Quatre `holocron_<forme>.json` restent schemaVersion1/HOLOCRON. Tous : repeatable=false, dependencies=[], rewards xp=0/alignmentTenths=0 ;8étapes maximum et fichier≤8192caractères. Génération originale par `scripts/generate_expedition_guides.py`, appelée en fin de pipeline planétaire.
