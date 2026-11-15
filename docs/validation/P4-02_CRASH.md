# P4-02 — Reprise de voyage dimensionnel et arrivée persistée

**Validation ciblée exécutée, V1.0/P4 non validées.** Travail du 15 novembre 2026, suite du terminal décrit dans P4-02.md.

## Périmètre du harnais

Mini-plan : TravelCrashValidation dans gameTest uniquement ; scripts run-travel-crash-server.ps1, test-travel-crash.ps1, kill-paused-travel.ps1 et check-travel-crash.py. Monde isolé run-travel-crash/world, graine20261115, serveur127.0.0.1:25586, flag JVM lotf.validation.travelCrash obligatoire. Commandes console OP4, aucun client graphique ou socket de joueur réel. Chaque acteur est un ServerPlayer avec connexion EmbeddedChannel liée à ce même acteur, inscrit via PlayerList.placeNewPlayer ; pas de FakePlayer pour le transfert. Le JAR distribué ne doit contenir aucun de ces harnais.

Les objets de fixture (carte1, terminal1, cellules2, diamants7) et plateforme réutilisable sont explicites. Déblocage Ilum par la vraie consommation de carte ; attente réelle du cooldown40ticks. Chaque cas de retour réalise d'abord un aller payé et pose effectivement le portable par BlockItem. L'ancre et le terrain d'Ilum sont réels, pas une dimension factice de GameTestServer. Le chunk du terminal est chargé au début de l'action, comme il le serait avec un client présent : les premières tentatives sans cette étape étaient correctement refusées par le service après unload du chunk synthétique. Le harnais refuse d'écraser les preuves d'un run existant.

## Checkpoints et arrêts forcés

Dix étapes : PREPARED, PAYMENT_MEMORY, PAYMENT_DURABLE, PAID, TELEPORTED, ARRIVAL_DURABLE, ARRIVED, ACTIVATION_MEMORY, ACTIVE_DURABLE, DONE. Série sweep1 : **vingt interruptions Error passent**, dix par direction, avec journaux/fichiers natifs/positions relus indépendamment. Avant paiement durable : ABORTED, cellules ou crédit de retour conservés, bonne dimension source. Après paiement durable : DONE et bonne destination, sans second débit. TELEPORTED prouve réellement la fenêtre position mémoire dans la destination / fichier natif encore dans la dimension source. Deux reprises consécutives, puis gain de fixture7→9diamants et rotation+15° : le reçu clos ne restaure ni l'ancien inventaire ni l'ancienne orientation.

Six vrais kills de première série, sans commande stop ni sauvegarde préalable au kill :

| Run | Sens | Checkpoint | Java tué | Reprise native |
|---|---|---|---|---|
| outmemory1 | aller | PAYMENT_MEMORY | 20468 | ABORTED, révision1, Overworld |
| outtele1 | aller | TELEPORTED | 7560 | DONE, révision2, Ilum |
| outactive1 | aller | ACTIVE_DURABLE | 22064 | DONE, révision2, Ilum |
| retmemory1 | retour | PAYMENT_MEMORY | 8416 | ABORTED, révision2, Ilum |
| rettele1 | retour | TELEPORTED | 25548 | DONE, révision3, Overworld |
| retactive1 | retour | ACTIVE_DURABLE | 12496 | DONE, révision3, Overworld |

Le script vérifie le processus Java local unique, l'argument JVM d'opt-in, son PID égal à celui de la pause native et l'âge de la pause. Il copie journal/fichier joueur/preuves avant le kill ; handle natif, Kill/WaitForExit, exitCode−1 et shutdownCommand=false enregistrés. La pause est bornée45secondes ; une pause expirée ne compte pas comme preuve de kill. Redémarrage puis login par le chemin PlayerList réel, reprise du lifecycle de production, contrôle supplémentaire idempotent et gain ultérieur conservé. Les six résultats ont été confirmés par le lecteur Python indépendant, `.tools/travel-six-kills-before-arrival-recheck.json`. Ces profils synthétiques ne prouvent pas une reconnexion réseau réelle.

## Défaut supplémentaire trouvé et corrigé

Après sauvegarde de la destination, la phase ARRIVED activait/clôturait sans revérifier terrain et droits. Deux tests ont échoué sur le code précédent : claimold-0 refus de droits devenu favorable à tort, et lavaold-0 joueur laissé sur une arrivée devenue dangereuse. Logs des essais sous .tools/travel-crash-{claimold,lavaold}-safety-console.log. Aucune assertion neutralisée.

TravelService.revalidateArrival contrôle maintenant le site d'une opération ARRIVED encore ouverte, même si l'activation joueur est déjà durable. Si le terrain ou les droits ont changé, il cherche une arrivée sûre autorisée (ou le secours de retour), conserve la même image payée, téléporte et relit la sauvegarde avant clôture. Sans site disponible, journal et fichier joueur restent inchangés ; la reprise pourra finir quand la situation le permettra. Le reçu DONE garde son comportement de non-restauration des gains et déplacements ultérieurs.

Huit cas passent après correction, via interruption Error dans le même Java : claims et lave, après ARRIVED et ACTIVE_DURABLE, dans les deux sens ; relecture indépendante du journal/joueur confirme inventaire inchangé. Claims : refus conserve exactement le journal ouvert et le fichier joueur, reprise autorisée clôture ensuite sans paiement. Lave de fixture : déplacement réel vers un site voisin sûr, lave laissée en place par le service (restaurée uniquement par le nettoyage du harnais). Deux cas supplémentaires passent avec toute la zone de home et de ses voisins refusée : reprise vers le spawn Overworld, home enregistré conservé, aucun nouveau débit.

## Série finale sur le code corrigé

**sweep2 : vingt checkpoints passent**, puis les six kills/redémarrages suivants :

| Run | Sens | Checkpoint | Java tué | Reprise native |
|---|---|---|---|---|
| outmemory2 | aller | PAYMENT_MEMORY | 23424 | ABORTED, révision1, Overworld |
| outtele2 | aller | TELEPORTED | 4892 | DONE, révision2, Ilum |
| outactive2 | aller | ACTIVE_DURABLE | 2824 | DONE, révision2, Ilum |
| retmemory2 | retour | PAYMENT_MEMORY | 16260 | ABORTED, révision2, Ilum |
| rettele2 | retour | TELEPORTED | 9688 | DONE, révision3, Overworld |
| retactive2 | retour | ACTIVE_DURABLE | 14320 | DONE, révision3, Overworld |

Les six reprises passent par un nouveau Java et le login natif ; journal, inventaire, atlas, position, gain et rotation ultérieurs sont relus indépendamment. Dernier serveur isolé arrêté proprement20:30:31, quatre dimensions sauvegardées. Résultats locaux : travel-final-sweep-check.json (vingt), travel-six-final-kills-check.json (six), travel-arrival-recheck.json (huit), travel-fallback-check.json (deux). Aucun test graphique ou réseau réel déduit de ces scénarios synthétiques. Régression finale test/runGameTestServer/build : **100 JUnit sans échec/erreur/skip et94 GameTests requis verts**, BUILD SUCCESSFUL20:31:37. JAR SHA2562385C0855BA05EA05C6F7A87019627826739CA16CC030FDB1EDE1D8728D64C30 ; TravelService présent et aucun GameTest/CrashValidation/ValidationCommands distribué.

## Reproduction

Chaque run doit être neuf. Le script démarre et arrête le serveur ; ne pas lancer un autre Gradle en parallèle.

```powershell
./scripts/test-travel-crash.ps1 -Mode Sweep -Run exemple
python scripts/check-travel-crash.py --sweep exemple
./scripts/test-travel-crash.ps1 -Mode Kill -Run killaller -Kind OUTBOUND -Checkpoint TELEPORTED
./scripts/test-travel-crash.ps1 -Mode Recover -Run killaller
python scripts/check-travel-crash.py --run killaller
./scripts/test-travel-crash.ps1 -Mode Safety -Run claims -Kind RETURN -Hazard CLAIM
python scripts/check-travel-crash.py --safety claims
./scripts/test-travel-crash.ps1 -Mode Safety -Run secours -Kind RETURN -Hazard FALLBACK
python scripts/check-travel-crash.py --safety secours
```

Les fichiers de preuve sont sous world/validation/travel-crash ; captures de kill et journaux sous .tools/travel-crash-*. Ils restent locaux/ignorés. Premiers essais du lanceur : EULA absent en IDE (copie rendue conditionnelle comme les autres scripts), puis tableau PowerShell réduit à une chaîne pour un checkpoint unique (corrigé en string[]). Ces essais n'ont pas tué de processus et ne sont pas comptés comme tests réussis.

## Limites

Pas une validation solo/deux vrais clients, des erreurs de disque ou d'un mod de claims tiers. Les vingt points utilisent Error dans le même processus ; les six kills ciblent trois étapes par direction, pas toutes les combinaisons de panne. Les données de fixture et positions réglées par le serveur ne constituent pas un parcours de récolte/craft complet. Renouvellement de ressources, gameplay boss, conflits de deux arrivants, profiling et autres gates de P4 restent ouverts. Ne pas déduire une garantie de sauvegarde de tous les blocs du monde d'un contrôle de transaction joueur/voyage.
