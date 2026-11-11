# P1-04 — point de reprise (11 novembre 2026)

**Note historique du socle. La télékinésie est désormais implémentée ; voir P1-04.md pour les preuves et validations ouvertes.**

Réalisé : `core.api.{ProtectedWorldService,ProtectionPolicy,ModificationKind}`, `core.security.VanillaProtectedWorldService`, `core.operation.{DurableOperationStore,StoredOperation,OperationPhase}`.

Permissions : fil serveur, acteur vivant dans la bonne dimension, droits de construction, frontière/hauteur, chunk déjà chargé, spawn protection vanilla. Tout refus ou exception d'un adaptateur bloque l'action. Aucun vrai mod de claims encore validé.

Journal : NBT versionné par UUID, payload en copie défensive, écriture temporaire + force disque + remplacement atomique, quota 256 Kio/entrée et budget borné. Schémas inconnus conservés et bloqués. Suppression seulement après CLOSED. Une instance par monde, uniquement sur le fil serveur ; ce n'est pas un verrou distribué.

## Preuves
- `build runGameTestServer` : 23 JUnit à ce moment-là et 5 GameTests requis réussis.
- Ajout du cas écriture .tmp interrompue/corruption, puis `build` : **24 JUnit réussis**, aucun échec.
- `./scripts/smoke-server.ps1` : PASS, mod chargé, serveur prêt, arrêt propre, toutes dimensions sauvegardées.
- GameTest permissions : position valide permise, refus d'adaptateur et exception bloqués, position hors monde refusée.
- Journal : réouverture/rejeu, phases interdites, refus de suppression non résolue, schéma futur préservé octet pour octet, quota/taille/conflit, copies défensives, interruption temporaire, NBT corrompu conservé.

Le journal n'est pas encore relié aux chunks. Son remplacement atomique ne prouve pas l'absence de duplication Minecraft. Aucun crash de jeu pendant une saisie testé.

## Prochaine tranche à coder
Mini-plan/fichiers : `force/BlockMoveCoordinator`, ancre de transaction et BlockEntity sous `content`, projection BlockDisplay vanilla, configuration/coûts, intentions réseau ou commandes/touches minimales, GameTests sous `src/gameTest/.../force`, journal.

1. Un seul bloc whitelisté non-conteneur/joueur ; niveau, masse, portée, LOS, énergie, source, trajet, destination et budgets serveur. Niveau 15 réservé aux légers ; pierre moyenne à un palier massique adapté. Pas d'obsidienne via seul test du nombre.
2. Remplacer la source par une ancre unique portant état original et UUID ; protéger ancre contre minage, explosions et pistons. Préserver et signaler toute corruption OP au lieu d'écraser des blocs inattendus.
3. PREPARED → HELD → DESTINATION_STAGED → COMMITTED → CLOSED. Les marqueurs source/destination doivent franchir des barrières de sauvegarde prouvées AVANT COMMITTED. Inspecter ServerLevel.save / MinecraftServer.saveEverything 1.21.1 : SavedData et chunks peuvent persister dans des ordres différents.
4. Avant COMMITTED : compensation vers source et retrait conditionnel de l'ancre destination. Après COMMITTED : retrait conditionnel source et finalisation destination. Ne pas recréer un bloc parce que la destination est devenue air après commit durable : il peut avoir été légitimement miné.
5. Marqueur manquant/étranger : préserver les données, refuser toute réécriture destructive, état bloqué administrable.
6. Projections sans items gratuits, nettoyage des orphelines. Gérer mort, déconnexion, dimension, stop, unload.
7. Tester chaque interruption entre journal/chunks/fermeture, deux joueurs sur la même source, destination occupée, permission retirée, énergie, coffre/bedrock. Profiler les barrières ; aucun flush complet par tick.

Ces notes sont un plan à vérifier, pas une preuve de télékinésie fonctionnelle. P1-04 reste non validé.
