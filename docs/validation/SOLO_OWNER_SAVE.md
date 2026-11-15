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

## Voyage solo interrompu à ACTIVE_DURABLE

Extension gameTest `SoloTravelSaveValidation` : copie arrêtée de `solo5`, fixture explicite neuf diamants/deux cellules/un terminal porté, vrai `TravelService.request` depuis le terminal existant. Il se suspend uniquement au checkpoint ACTIVE_DURABLE de l'aller. La carte était déjà connue ; aucune découverte simulée. Cette variante vérifie le service réel et la reprise native, sans prétendre que le clic initial provient de l'interface.

`solotripkill` PID20228 tué sans stop, exit−1 : joueur embarqué Overworld/révision atlas3/génération14/sept diamants, mais fichier natif Ilum/révision4/génération19/crédit vrai/neuf diamants/terminal1/cellules0. Journal OUTBOUND/ARRIVED, coût2. `solotriprec` PID25528 charge réellement Ilum depuis le fichier plus récent, clôt le même reçu DONE, conserve exactement inventaire/atlas/position sans redébit. Le client attend sa vraie ClientLevel Ilum et écrit un accusé avant l'arrêt normal ; les deux fichiers joueurs finaux ont le même état19/Ilum. La metadata lue avant arrêt final était encore Overworld14/sept diamants : la preuve ne dépend pas d'une sauvegarde propre antérieure à la reprise.

`check-solo-travel-save.py solotripkill solotriprec` PASS : PID/codes, absence de stop côté kill, images natives/embarqué, cinq générations de barrières supplémentaires, journal identique sauf clôture, et dimension effectivement reçue par le client. Le lanceur refuse de réutiliser un ancien marqueur de kill pour une nouvelle interruption.

```powershell
./scripts/test-travel-solo.ps1 -Run voyagekill -TemplateRun basesolo -OwnerSaveProbe -OwnerSaveMode KillTravel
./scripts/test-travel-solo.ps1 -Run voyagerepris -TemplateRun voyagekill -OwnerSaveProbe -OwnerSaveMode RecoverTravel
python scripts/check-solo-travel-save.py voyagekill voyagerepris
```

Cette variante attend le cooldown réel avant départ. Le template doit avoir terminé le parcours solo initial et ne contenir aucun ancien résultat owner-save ; aucune sauvegarde existante n'est écrasée. Le checker versionné utilise cette fixture précise (atlas initial3, quatre barrières de voyage et une de fixture). Une autre tranche de gameplay requiert un autre scénario explicite.

## Limites

Régression après correction : `test runGameTestServer build` réussi21:35:38, **105 JUnit sans échec/erreur/skip et 94 GameTests requis verts**. Cinq tests ciblés couvrent sélection sans réécriture, indépendance des heures fichiers, génération embarquée égale/supérieure, identité/NBT corrompu, migration et schéma futur/négatif. JAR SHA256 **6755AB65715CB24BC83FBDBF80B2CDF0328F6737C716AA8881F8DC3B8F51B260**, aucun harnais distribué. Parcours réseau complet `net4` avec deux clients PID2292/PID20308 et solo complet `solo5` PID24192 repassent avec les compteurs : checkers indépendants PASS, arrêts normaux et quatre dimensions sauvegardées. Aucun ancien résultat réseau n'est utilisé comme preuve de ce nouveau JAR.

Les deux preuves solo ciblent une barrière core et ACTIVE_DURABLE à l'aller. Les autres checkpoints/sens solo et tous les points de panne de sabres/speeders/RPG restent ouverts ; les six kills dimensionnels antérieurs concernent le dédié. Pas de panne disque/OS, de coupure socket en cours de transfert ni d'autre mod modifiant l'ordre de chargement. Un ancien fichier sans compteur ne permet pas d'inférer l'ordre d'anciens checkpoints ; migration0 uniquement, aucune récupération rétroactive inventée. Le compteur ne garantit pas la sauvegarde de tous les chunks ni les changements vanilla postérieurs à une barrière. Conserver ensemble tous les fichiers lors d'une restauration de monde.
