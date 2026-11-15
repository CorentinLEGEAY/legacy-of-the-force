# Checkpoint financier et propriétaire solo — 2026-11-15

## Défaut reproduit

Le vrai `PlayerList.load` de Minecraft1.21.1 préfère `WorldData.getLoadedPlayerTag()` pour le propriétaire solo, même si `playerdata/UUID.dat` contient un checkpoint transactionnel plus récent. `VerifiedPlayerSaveBarrier` écrivait bien ce fichier par vanilla et le relisait, mais cela ne suffisait pas à garantir sa sélection au redémarrage solo.

Sur une copie arrêtée de `solo4`, `owner1` sauvegarde explicitement une fixture sept→neuf diamants par la barrière, puis appelle le vrai chargeur natif sur le propriétaire : fichier durable9, compound sélectionné7, joueur rechargé7, `passed=false`. Ce résultat est une reproduction de perte, pas un succès. Aucun ancien profil utilisateur n'est modifié.

Une première correction par horodatages réussit cette relecture dans le même processus (`owner2`) mais échoue après vrai kill (`ownkill1`→`ownrec1`). Minecraft réécrit `level.dat` avant la connexion et lui donne une date plus récente sans actualiser son joueur. Cette approche a été retirée avant commit. Les essais restent localement conservés et ne comptent pas comme preuve du correctif final.

## Correction

`PlayerSaveCheckpoint` est un attachment immutable core versionné (schéma1, génération long≥0), copié à la mort. Chaque barrière avance la génération, sauvegarde par le writer vanilla, force puis vérifie le fichier relu. Valeur invalide/épuisée = refus. Les anciennes sauvegardes sans attachment partent à0.

`SoloPlayerSaveRecovery`, événement `LoadFromFile` HIGHEST : uniquement propriétaire d'un serveur intégré, même UUID, fichier natif régulier borné4MiB ; si sa génération dépasse celle du propriétaire embarqué, mise à jour vanilla DataFixTypes.PLAYER puis chargement de l'agrégat complet. Le compound retourné par PlayerList pour placement est également actualisé avant choix de dimension/position. Pas de récursion de l'événement ni de writer parallèle. Générations égales/inférieures : priorité vanilla préservée. Les horodatages ne participent pas à la sélection.

## Preuve d'arrêt brutal et réouverture réelle

- `ownkill2`, JVM client/serveur intégré PID8212 : fixture initiale7 et ancien joueur embarqué sans compteur ; après barrière, vrai fichier natif9/génération1. Le harnais se suspend, le lanceur tue uniquement son processus possédé, code−1 ; aucune commande d'arrêt, aucun log de sauvegarde finale.
- `ownrec2`, JVM native distincte PID23064 : copie complète du monde arrêté brutalement, chargement réel QuickPlay solo ; le fichier level.dat contient encore7 au moment de la preuve, mais le propriétaire placé a9/génération1. Arrêt normal code0 ; playerdata et joueur embarqué final9/génération1, mêmes UUID, atlas, dimension et position. Le monde du kill reste inchangé.
- `check-solo-owner-save.py ownkill2 ownrec2` PASS : compare snapshots, PID, codes de sortie, fichier source après kill, joueur effectivement chargé et deux fichiers finaux ; refuse une fermeture propre au lieu du kill attendu.

```powershell
# Un parcours solo arrêté proprement fournit le monde initial de fixture (sept diamants).
./scripts/test-travel-solo.ps1 -Run basesolo -TemplateRun exemplededie
./scripts/test-travel-solo.ps1 -Run killsolo -TemplateRun basesolo -OwnerSaveProbe -OwnerSaveMode Kill
./scripts/test-travel-solo.ps1 -Run reprisesolo -TemplateRun killsolo -OwnerSaveProbe -OwnerSaveMode Recover
python scripts/check-solo-owner-save.py killsolo reprisesolo
```

Le dédié de template est produit par test-travel-network.ps1. Identifiants neufs obligatoires, aucun Gradle simultané. Ne jamais tester sur une sauvegarde personnelle. Code de fixture sous gameTest, propriétés JVM explicites et dossiers isolés ; données/proofs locales ignorées. Le mode Load est le test de relecture dans le même processus, distinct de Kill/Recover.

## Limites

Régression après correction : `test runGameTestServer build` réussi21:35:38, **105 JUnit sans échec/erreur/skip et 94 GameTests requis verts**. Cinq tests ciblés couvrent sélection sans réécriture, indépendance des heures fichiers, génération embarquée égale/supérieure, identité/NBT corrompu, migration et schéma futur/négatif. JAR SHA256 **6755AB65715CB24BC83FBDBF80B2CDF0328F6737C716AA8881F8DC3B8F51B260**, aucun harnais distribué. Parcours réseau complet `net4` avec deux clients PID2292/PID20308 et solo complet `solo5` PID24192 repassent avec les compteurs : checkers indépendants PASS, arrêts normaux et quatre dimensions sauvegardées. Aucun ancien résultat réseau n'est utilisé comme preuve de ce nouveau JAR.

Cette preuve porte sur le choix d'un checkpoint d'inventaire core, après une vraie interruption du serveur intégré. Elle n'est pas un kill dimensionnel solo en cours de transaction de voyage, ni un test de tous les points de panne de sabres/speeders/RPG. Les six kills dimensionnels existants concernent le dédié. Pas de panne disque/OS ni d'autre mod modifiant l'ordre de chargement. Un ancien fichier sans compteur ne permet pas d'inférer l'ordre d'anciens checkpoints ; migration0 uniquement, aucune récupération rétroactive inventée. Le compteur ne garantit pas la sauvegarde de tous les chunks ni les changements vanilla postérieurs à une barrière. Conserver ensemble tous les fichiers lors d'une restauration de monde.
