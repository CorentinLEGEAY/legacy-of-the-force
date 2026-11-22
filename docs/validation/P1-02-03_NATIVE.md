# P1-02/03 — Attribution native, anti-ferme et seuils

16 novembre 2026. P1-02/03 validés pour le socle XP/alignement ; P1 global et V1.0 restent ouverts.

## Fonctionnalité ajoutée

Les bandes étaient codées mais fixes et absentes du profil. AlignmentThresholds valide quatre entiers ordonnés autour de zéro ; configuration serveur rpg.alignmentThresholds, défauts−500/−200/200/500. Validation agrégée NeoForge : longueurs/types/ordre/bornes invalides font corriger la liste entière. `/lotf profile` affiche maintenant l’orientation traduite FR/EN. Aucun nouveau paquet ni schéma, aucun changement des gains confirmés ou conditions de pouvoirs. Quatre tests supplémentaires couvrent frontières asymétriques, configurations invalides, correction native ModConfigSpec et invariance du score/éligibilité.

Les essais finaux mrr2/ms4 chargent réellement la surcharge du monde serverconfig/legacyoftheforce-server.toml : seuils−100/−10/10/100 et fenêtre anti-ferme7200ticks. Le validateur exige les valeurs chargées et n’appelle plus de setter. Le seuil20 et multiplicateur0.25 restent les défauts. Le fichier de base est config/legacyoftheforce-server.toml pour cette version ; le readme natif de serverconfig explique sa priorité de surcharge. Cette preuve n’est pas un test de hot reload en plein combat.

## Fixtures et limites

Copie complète arrêtée de ap4, contrôlée via session.lock, avec AdventureOne/Two et leurs épées/armures réellement fabriquées. RPG initial1/0XP/0alignement ; aucune attribution d’XP, niveau ou arme par le harnais. Originaux et essais précédents conservés. Cibles ordinaires finies : NoAI, résistance au recul1, santé native20/10/50. Casque de cuir sans drop sur zombies pour éviter brûlure solaire ; piglins immunisés à la zombification. Ces cibles isolent l’attribution : elles ne mesurent ni difficulté, ni spawns naturels, ni rendement de combat public. Difficulté normale explicite.

Chaque client utilise l’épée via sélection/clic d’inventaire et gameMode.attack, swing et cooldown réels. Courte approche par touches normales si nécessaire ; aucun dégât accordé pour les éliminations récompensées. Série21zombies puis10vaches puis1piglin brute par joueur. Fenêtre7200 choisie pour conserver toute la série native, pas pour modifier le nombre20 de kills normaux. Aucun masquage des refus serveur.

L’arène de concurrence est une petite plateforme de fixture dans une nouvelle cellule : placement avant combat des deux joueurs, puis un vrai premier coup du primaire et les coups fatals du pair. La nouvelle cellule évite de confondre attribution avec la pénalité de ferme des anciennes cellules. Puis un coup réel du primaire sur un autre zombie, suivi de dégâts génériques de fixture : mort environnementale sans paiement. Enfin vrai FakePlayer de machine et mort d’une vache : aucun gain. Les kills environnement/machine sont des contrôles négatifs explicitement injectés côté serveur, pas des attaques humaines prétendues. Les notifications répétées de mort sont des rejouements synthétiques **après santé nulle**, distincts des vraies éliminations natives.

## Résultats dédiés et reprise

mr3 : dédié5880, clients2084/25168, trois sorties0. Deux suites locales simultanées, puis victime partagée et contrôles négatifs :67NBT de mobs morts,65récompenses/UUID/reçus uniques. Chaque reçu durable est recoupé avec événement MobKillCredited, vrais dégâts LivingDamageEvent.Post, propriétaire et profils/fichiers vanilla.

| Étape par joueur | XP totale | Alignement en dixièmes |
|---|---:|---:|
|10hostiles|50|10|
|21hostiles dans la cellule|101|20|
|Puis10passifs|111|10|
|Puis élite|151|15|

Le21ehostile ne donne que1XP et0alignement. Victime partagée : seul le dernier responsable gagne5XP/+1. Fin primaire151XP/+15/révision32, pair156XP/+16/révision33 ; niveau4, XP courante4/9. Rejouements : aucun paiement ou historique supplémentaire. Mort générique et machine : aucun reçu/événement, profil final inchangé après le partagé. Épées réellement usées ; drops vanilla et XP vanilla distincts de la Force.

mrr1, puis mrr2 avec vrai TOML : nouveaux serveurs/JVM et deux clients, mêmes profils/historiques chargés avant tout kill. Une nouvelle victime dans chaque ancienne cellule : seulement1XP/0alignement, donc pénalité persistante. Fin152/157XP, +15/+16, révisions33/34,67reçus et aucun journal actif. mrr2 clients4972/2980, trois sorties0. Le checker compare les préfixes complets d’événements et d’historique, le même type/dimension/cellule et les ticks dans7200, puis fichiers natifs/checkpoints et projections finales. Aucun nouveau gain par simple connexion.

## Solo intégré

ms3 première preuve de données ; ms4 preuve finale depuis vrai TOML, JVM25004, sortie0. Même personnage AdventureOne de ap4,32morts natives/32reçus,151XP/+15, niveau4/XP4, révision32. Le vrai IntegratedServer est exigé, connexion mémoire native du propriétaire ; le driver n’accède jamais au serveur. Captures et snapshots S2C réels. Le propriétaire embarqué dans level.dat et playerdata concordent intégralement pour RPG/inventaire ; le fichier de l’autre joueur hors ligne est inchangé octet pour octet.

Images mr3/mrr1/ms3 et confirmation de monde inspectées : niveaux4, scores4/9/10 et catégories FR visibles ; particules de mort et notifications vanilla temporaires, sans promesse de qualité graphique avancée. +1.5/+1.6 deviennent «Tendance lumineuse» avec la configuration de test, alors que les défauts les classeraient neutres : preuve d’un calcul configuré réellement affiché. Les exigences propres aux pouvoirs restent indépendantes.

## Reproduction

```powershell
./scripts/test-adventure-preparation.ps1 -Run reseau -Mode Rewards -PreparationRun ap4
./scripts/test-adventure-preparation.ps1 -Run repriseneuve -Mode RewardsRecovery -PreparationRun reseau
python scripts/check-mob-rewards.py reseau --recovery repriseneuve --file-config
./scripts/test-mob-rewards-solo.ps1 -Run soloneuf -Source ap4
python scripts/check-mob-rewards.py soloneuf --solo --file-config
```

Runs neufs, aucun Gradle simultané. Artefacts ignorés : .tools/adventure-rewards/rewardsrecovery-<run>-result.json et logs ; validation/adventure-rewards[-recovery]-server.nbt, fichiers joueurs/checkpoints/reçus dans le monde ; validation/adventure-rewards[-recovery]-client.nbt et screenshots/mob-rewards-final.png dans chaque profil. Le solo conserve la copie complète dans saves/RewardsValidation et son résultat .tools/mob-rewards-solo-<run>-result.json. Python3.11+ (tomllib) et dépendances de validation existantes.

## Échecs conservés

mr1 termine les séries locales151/151 puis échoue sur portée3.013>3 du pilote ; courte marche native ajoutée sans étendre la portée. mr2 tente d’équiper avant réception d’inventaire ; attente de l’item S2C corrigée. Trois sorties normales pour chaque essai, aucune preuve complète revendiquée. L’oracle initial comparait les slots au moment du chargement, alors que le SWAP8↔29 avait déjà équipé une épée ; il vérifie maintenant tous composants/quantités et tous autres slots strictement.

ms1 ne charge pas le monde, confirmation expérimentale non traitée ; JVM1692 possédé explicitement arrêté après vérification des args. Le pilote traite maintenant uniquement BackupConfirmScreen expérimental/personnalisé connu, sur copie isolée, avec capture et bouton traduit vanilla. ms2, JVM456 arrêté après échec : copie ap4 en difficulté paisible, code natif Mob.checkDespawn discard sans mort ; le contrôle !isAlive était trop large. Avant toute notification de rejeu, santé nulle/isDeadOrDying désormais exigées. Difficulté normale explicite et preuve partielle/message d’arrêt sur échec ajoutés. Aucun paiement de ce scénario refusé ne sert de preuve.

## Consolidation et régression

Boss Jedi/Sith réellement combattus avec deux clients : bn6, P4-03_NETWORK (−250/+250, deux gains800, alignement final0), et ad4 légitime. Caps/niveau100 neutre/suspension d’éligibilité/overflow : tests unitaires déjà verts. Reprise après vraie coupure et ancienne image joueur : P1_RPG_REPRISE et SOLO_OWNER_SAVE ; quatre interruptions transactionnelles injectées plus récupération idempotente restent automatisées. Ce rapport ne les transforme pas en quatre nouvelles coupures de processus.

Régression générale du même code de production :131JUnit sans échec/erreur/skip,102GT requis verts11:06:13, build38s. Derniers changements limités aux pilotes/orchestration, compilation et parcours natifs supplémentaires réussis, build final17s. JAR64A65E2DF3BB1D37FADA65F010F9681ADA426B58CAC451F30B4C2731342ED628, jarmr1 : deux serveurs installés18824/7636, exits0, checker PASS et zéro harnais distribué.

P1-02/03 clôturés pour attribution, XP/alignement, anti-ferme et seuils de présentation. Le profiling/volume disque sous charge relève encore de P7 ; pas de preuve de panne électrique, de tous mods tiers ou de toutes combinaisons de pannes. Les arbres de compétences/pouvoirs exclusifs concrets restent P5. P1-04/05 et phase P1 globale restent à valider.
