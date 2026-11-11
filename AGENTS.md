# Instructions permanentes pour Codex — Legacy of the Force

Tu participes au développement du mod Java **`legacyoftheforce`**. Travaille **depuis les spécifications de ce dépôt**, sans réinventer les règles décidées par le concepteur.

## Sources de vérité, par priorité
1. `docs/00_DECISIONS.md` — décisions fermes du concepteur.
2. `docs/01_ARCHITECTURE.md` — conventions techniques et contrats.
3. `modules/<module>/SPEC.md` — comportements du module.
4. `docs/04_EQUILIBRAGE.md` — chiffres initiaux configurables.
5. `docs/02_ROADMAP.md` — ordre d'implémentation et validations.
6. `docs/05_TESTS_ET_SECURITE.md` — tests et exigences de sécurité.
7. `docs/06_DIRECTION_ARTISTIQUE.md` / `docs/07_CATALOGUE_DONNEES.md` — ressources et formats.

Si deux documents paraissent contradictoires, ne tranche pas silencieusement : signale-le dans `docs/03_ETAT_DU_PROJET.md`, choisis la solution la moins destructive pour la tâche en cours et propose une mise à jour explicite des documents.

## Procédure obligatoire pour chaque tâche
1. Lire les fichiers de référence correspondant au ticket.
2. Écrire un mini-plan et nommer les fichiers que tu comptes modifier.
3. Implémenter **la plus petite tranche verticale jouable** possible, avec un code clair et des noms explicites.
4. Ajouter un test automatisé (quand faisable), sinon une procédure reproductible de test manuel.
5. Exécuter les tests et la compilation disponibles ; décrire clairement les échecs non résolus.
6. Mettre à jour `docs/03_ETAT_DU_PROJET.md` : réalisé, tests, limite connue, prochaine tâche.
7. Ne pas annoncer « terminé » si une fonctionnalité est seulement simulée ou visuelle.

## Conventions de projet
- Un **seul mod** NeoForge, pas une collection de JARs indépendants ; packages internes : `core`, `rpg`, `force`, `lightsaber`, `speeder`, `planets`, `bosses`, `quests`, `client`, `content`.
- Package Java racine proposé : `com.legacyoftheforce` ; mod ID et préfixe de ressources : `legacyoftheforce`.
- Éviter les dépendances cycliques. Les événements/services partagés résident sous `core.api` ; les modules n'accèdent pas aux classes internes des autres modules.
- Garder le code client (`client.*`, rendu, touches, écrans) **hors du chargement du serveur dédié**.
- Aucun calcul d'XP, changement d'alignement, dégâts, déplacement de bloc ou fabrication validé par le seul client.
- Toutes les données sauvegardées ont une version de schéma ; ne jamais stocker de références directes aux objets Java dans des sauvegardes.
- Les états de gameplay sont gérés par le serveur et projetés vers le client en lecture seule.
- Ajouter des logs ciblés (aucun spam par tick), des limites de portée/taux, et des messages d'erreur compréhensibles.
- Toutes les valeurs de gameplay doivent être configurables ou provenir de données chargées ; commentaires sur les hypothèses, pas sur l'évidence du code.
- Pas de code inventé à partir d'anciennes APIs Forge : consulter la documentation **de la version épinglée**, puis compiler.
- Les ressources (textures, sons, modèles) doivent être originales ou dûment autorisées : ne pas extraire d'assets de jeux/films.

## Définition de « terminé »
- Fonctionnement en solo **et** serveur dédié + deux clients si la fonctionnalité est réseau.
- Pas de duplication ni perte lors de sauvegarde, déconnexion, mort, dimension ou redémarrage.
- Permissions vérifiées et valeurs hors limites rejetées.
- Cas d'erreur et charge raisonnable couverts par tests.
- Interface minimale utilisable ; qualité graphique avancée peut être reportée, mais le report doit être tracé.

## Hors périmètre tant que P0–P4 ne sont pas achevés
Ne pas développer au hasard des vaisseaux spatiaux pilotables, du PvP de factions, des apprentis autonomes, des guerres planétaires ou un moteur physique universel. Ce sont des extensions futures.

## Début de tâche préféré
« Lis AGENTS.md, docs/00_DECISIONS.md, docs/01_ARCHITECTURE.md, docs/02_ROADMAP.md et modules/<module>/SPEC.md ; réalise uniquement le ticket <ID> ; tests puis mise à jour du journal. »
