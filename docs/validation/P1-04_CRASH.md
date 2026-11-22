# P1-04 — Arrêt brutal HELD du code actuel

22 novembre2026. Preuve du socle mono-bloc ; pas une panne électrique ni une certification de tous stades transactionnels.

## Kill réellement effectué

bk1 reprend une copie arrêtée de ap4, avec le même montage de test que fb2 (P1-04_NETWORK). Deux sockets non-OP et personnages de fixture niveau15/1232XP par commande console native, regen0. Après cinq refus non payés et une vraie saisie par le client, les deux ClientLevel ont attesté le même UUID, position et BlockRenderState de terre. Chaque client écrit un préfixe avant de confirmer sa préparation à l’arrêt.

Le serveur appelle la sauvegarde vanilla complète avec flush **pendant HELD** : ni stop ni cancel. Le journal reste HELD, le chunk contient l’ancre et les régions d’entités contiennent la projection. Une preuve bornée, fsync/atomique, annonce le PID et le dossier. Le lanceur vérifie ce dossier, le processus java.exe/startTime/arguments serverRun et l’écoute127.0.0.1:25586 du même PID, puis termine exclusivement ce JVM. Aucun autre Java arrêté.

Serveur25144 tué à21:47:50.160UTC, runner Gradle sort1 attendu ; clients5144/1140 observent une vraie remise à zéro de connexion, écrivent la preuve de déconnexion puis sortent0 sans kill forcé. Aucun log Stopping server/Stopping the server : ce n’est pas une fermeture propre. Le log de sauvegarde avant kill est intentionnel et ne doit pas être confondu avec un stop.

NeoForge écrit une stack/rapport de déconnexion et «Negative index in crash report handler» côté client durant la fermeture de socket. Les deux JVM clients sortent0 ; ces messages ne sont pas masqués ni présentés comme un plantage du mod ou comme une fermeture serveur normale.

## Reprise native sans mutation de préparation

br1 est une copie complète du monde crashé, verrou de session vérifié libre. Le mode BlockRecover ne remet aucun bloc, ne donne aucun XP/objet et ne téléporte aucun joueur. Le coordinateur de production s’ouvre, récupère le journal et charge le chunk ; le hook de chargement retire les projections sauvegardées. Le test attend deux vraies connexions avant d’observer quoi que ce soit.

Nouveau serveur14936, nouveaux clients17424/11640 ; trois sorties0. Avant toute mutation de test : source terre, destination air, aucun journal ni projection. Chaque client confirme/capture le même état. Les inventaires et profils chargés concordent avec les fichiers vanilla sauvegardés avant le kill. Énergie sauvegardée du détenteur91.60000000000001/100, pair100 ; niveau15/1232XP/révision1/alignement0 conservés. Il s’agit de la dernière image native sauvegardée, pas d’une promesse de conserver les ticks d’énergie volatils entre le flush et le kill.

Le checker indépendant relit :

- Code d’arrêt non nul et PID réellement visé, deux déconnexions natives observées, préfixes avant kill et six PID distincts.
- Journal HELD original inchangé, ancre source réelle avec le même UUID et BlockDisplay **effectivement présent dans sa région d’entités avant kill**, bloc de terre rendu.
- Dans le nouveau monde : terre à la source, air à destination, autres blocs de refus intacts, journal fermé, aucune projection/item de terre, aucun gain d’inventaire.
- Même profil complet et inventaire dans le checkpoint natif, les deux nouvelles connexions et les fichiers finaux. Les captures du détenteur après reprise ont été inspectées.
- Le monde crashé et sa preuve sont conservés à l’identique ; la récupération n’est pas certifiée à partir d’un monde artificiellement réparé ou réinitialisé.

check-block-crash bk1 br1 : PASS. Les autres phases de journal gardent leurs tests unitaires/GameTests d’interruption ; on ne les transforme pas en autres kills de processus qui n’ont pas eu lieu.

## Reproduction

```powershell
# Sur une préparation neuve/arrêtée, jamais sur une sauvegarde personnelle.
./scripts/test-adventure-preparation.ps1 -Run killneuf -Mode Block -PreparationRun ap4 -KillHeld
./scripts/test-adventure-preparation.ps1 -Run repriseneuve -Mode BlockRecover -PreparationRun killneuf
python scripts/check-block-crash.py killneuf repriseneuve
```

Choisir des identifiants neufs. Aucun Gradle parallèle. Le lecteur de checkpoint du lanceur découvre le Python du runtime installé de l’application ; option -PythonPath <exécutable> pour un Python3.11+ déjà installé ailleurs. Les assertions empêchent tout kill si identité/port/dossier ne correspondent pas. Arrêt brut attendu seulement avec -KillHeld et Mode Block ; aucun changement au mode joueur normal.

Artefacts locaux ignorés : .tools/adventure-block-bk1-result.json, .tools/adventure-blockrecover-br1-result.json/logs ; world/validation/block-crash-held.nbt dans les deux mondes, adventure-block-recovered-server.nbt dans br1 ; validation/adventure-block-crash-prefix-client.nbt puis adventure-block-client.nbt pour les clients du kill, adventure-block-recovered-client.nbt pour ceux de reprise. Images force-block-held/recovered.png dans leurs profils.

## Consolidation

Régression courante build38s :131JUnit sans échec/erreur/skip et102GT requis verts22:52:23. JAR64A65E2DF3BB1D37FADA65F010F9681ADA426B58CAC451F30B4C2731342ED628 inchangé, checker jarmr1 PASS et nouveaux pilotes absents du JAR. Aucun changement de production nécessaire, seuls pilotes/orchestration/checker/documentation.

P1-04 validé comme socle mono-bloc par consolidation : solo/rechargement et lifecycle/unload/inter-chunks dans P1-04.md, réseau stable/refus/verrou/protection dans P1-04_NETWORK.md, crash actuel ici, conflits/formats/points intermédiaires en tests automatisés. Le profilage de charge, les groupes/projections avancées et la compatibilité de vrais mods de claims restent leurs obligations P5/P7 ; P1 global et V1.0 demeurent ouverts. Prochaine tâche : P1-05 réseau et lifecycle des impulsions.
