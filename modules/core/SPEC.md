# MODULE CORE — Infrastructure et contrats partagés

**Mission :** fournir un socle fiable à tous les modules. Core n'implémente ni pouvoirs ni speeders ; il expose des outils de sécurité, réseau, configuration, registres et persistance. **Priorité : P0**.

## Fonctionnalités à développer

### CORE-01 — Initialisation du mod
- Point d'entrée `LegacyOfTheForce` enregistré auprès de NeoForge ; identifiant constant `legacyoftheforce`.
- Enregistrer les items, blocs, entités, menus, data components, attachments, effets et types utiles via méthodes NeoForge appropriées à la version choisie.
- Initialiser modules dans un ordre stable, sans dépendance circulaire ; aucune classe client chargée sur serveur dédié.
- Centraliser la journalisation : démarrage, échecs de validation et migrations, sans spam de tick.

### CORE-02 — Configuration data-driven
- Séparer paramètres **serveur** (XP, dégâts, budgets, permissions, spawn) de **client** (particules, HUD, son).
- Les valeurs confirmées sont celles de `docs/00_DECISIONS.md` ; proposer un profil « équilibrage par défaut ».
- Charger et valider valeurs à l'initialisation, avec défauts sûrs si données manquantes ; rejeter valeurs absurdes (`NaN`, négatives, trop grandes, nombres de blocs > plafond).
- Prévoir surcharge par config de serveur et (plus tard) data packs pour définitions de pouvoirs, cristaux, pièces, boss et planètes.

### CORE-03 — Événements métier
Créer des événements **après validation serveur**, sans exposer d'objet mutable :
- `MobKillCredited(playerUUID, mobType, category, context, eventUUID)`.
- `BossDefeated(bossId, arenaId, eligiblePlayers, eventUUID)`.
- `ForceActionCommitted(playerUUID, targetId, action, energySpent)`.
- `SaberAssembled(playerUUID, saberId)` ; `CrystalBondProgressed(playerUUID, crystalId, newLevel)`.
- `SpeederAssembled(playerUUID, vehicleUUID)` ; `PlanetTravelCompleted(playerUUID, planetId)`.
Les consommateurs doivent tolérer un événement de même ID reçu deux fois (idempotence).

### CORE-04 — Réseau
- Versionner le protocole ; inscrire les paquets C→S et S→C dans le mécanisme NeoForge adapté à **1.21.1**.
- Chaque paquet C→S est une **intention** avec payload limité : type d'action, référence de cible, paramètres bornés, nonce/numéro de séquence.
- La validation s'exécute sur le fil serveur de niveau approprié ; pas de modification du monde depuis un thread réseau.
- Messages de refus structurés : `not_enough_force`, `out_of_range`, `protected_area`, `invalid_plan`, `cooldown`.
- Réponses S→C : delta d'état, snapshot minimal, confirmation/rejet, données de rendu. Le client ne peut pas synchroniser le score RPG en écriture.

### CORE-05 — Permissions et propriété
- `ProtectedWorldService`: `mayModifyBlock(actor, pos, action)` + `mayMoveEntity(actor, target)` + `mayUseVehicle(actor, vehicle)`.
- Respecter règles vanilla (spawn protection, permissions d'opérateur), frontières de dimensions et régions réservées du mod (arènes, temples).
- Préparer un adaptateur facultatif pour des mods de claims, **sans en dépendre obligatoirement**.
- En monde solo, les règles restent actives pour éviter de déplacer les blocs dangereux.

### CORE-06 — Transactions
- Créer un système de `OperationId/TransactionId` pour actions dangereuses (déplacer des blocs, assembler véhicule/sabre, voyager).
- API : préparer → valider → verrouiller sources → appliquer → confirmer ou compenser → libérer/verrou.
- Dupliquer une requête réseau avec le même nonce ne doit pas consommer deux fois les matériaux.
- Les blocs manipulés ont une transaction persistante ou un mécanisme de restitution garanti ; ne pas « supprimer puis espérer reposer ».
- En cas de crash, l'état doit être déterministe, quitte à remettre le bloc au point sûr ou le placer dans un conteneur de récupération administré.

### CORE-07 — Sauvegardes et compatibilité
- Inclure `schemaVersion` dans objets personnalisés ; migrations pour sauvegardes anciennes.
- Un format inconnu déclenche un message/log et **ne détruit jamais les données originales**.
- Distinguer données monde, joueur, item, entité ; ne pas sérialiser d'instances Java directes.
- Le serveur envoie un snapshot initial après connexion et changement de dimension ; synchronise seulement les deltas nécessaires.

### CORE-08 — Diagnostics et outils de développement
- Commandes **administrateur** (debug non accessibles aux joueurs) : afficher profil, forcer sauvegarde, attribuer XP test, inspecter transaction, retirer véhicule test.
- Traces pour durée de tick, nombre de blocs mobiles, véhicules actifs, boss en combat.
- Tests de sérialisation, réseau, permissions, configuration et classes chargées sur serveur.

## Interfaces publiques à exposer
- `PlayerProgressService` (implémenté par rpg), `ProtectedWorldService` (core).
- `ForceTargetAdapter` / `ForceTargetRegistry` pour blocs, mobs, véhicules.
- `ItemAssemblyValidator` ou services d'inventaire transactionnel.
- `GameEventBus`/events métier, `ServerConfig`, `OperationCoordinator`.

## Données / identifiants
- Uniquement ID namespacés ; UUID d'instances ; transactionId ; version protocole.
- Journal transactions : ID, dimension, acteur, objets/verrous, état, timestamps de progression, éventuels points de restitution.

## Interactions
- RPG publie gains et niveaux après attribution serveur.
- Force consomme énergie RPG et utilise permissions, transactions et adaptateurs.
- Lightsaber / Speeder utilisent inventaires atomiques et data components.
- Planets / Bosses / Quests consomment événements, registres et world save.
- Client présente messages et snapshots, jamais la vérité serveur.

## Dépendances et limites
- Dépend uniquement de NeoForge/Minecraft et, si indispensable, de bibliothèques vérifiées.
- **Ne doit pas** importer de détails internes de `force`, `speeder` ou `lightsaber`.
- Priorité robustesse avant optimisation avancée.

## Critères d'acceptation
- [ ] `gradlew build`, `runClient`, `runServer` fonctionnent avec la version fixée.
- [ ] Un joueur se connecte avec un profil serveur synchronisé.
- [ ] Paquet invalide/hors portée refusé sans crash.
- [ ] Transaction idempotente et rechargement des données.
- [ ] Le serveur dédié démarre sans classes de rendu client.
- [ ] Module utilisable par les suivants sans dépendance cyclique.
