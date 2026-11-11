# P3-02 — Trois terminaisons réelles pendant une recharge

## Portée et matériel de test

2026-11-11, code de production `e5d9b51`, parcours recette/menu vérifié séparément à `93ac874`. Références : SPEED-06/10, transactions de l'architecture et exigences de reprise de `docs/05_TESTS_ET_SECURITE.md`. Mini-plan : harnais de console de développement `SpeederRechargeCrashValidation`, lancement isolé, terminaison ciblée et vérificateur indépendant des fichiers natifs, puis régression/journal/Git. Aucun correctif du code de production requis par ces trois coupures.

Le serveur dédié de développement écoute seulement `127.0.0.1:25585`, monde distinct `run-recharge-crash/world`. Aucun client connecté. Le harnais crée pour chaque cas un profil `ServerPlayer` synthétique, avec une connexion factice pour les callbacks natifs, deux cellules et une instance de véhicule explicitement fournie comme fixture à énergie100. Ce véhicule de fixture n'est pas une preuve d'assemblage payé ; la cellule n'est pas fabriquée par ce harnais. Les tests de recette et d'assemblage ont leurs preuves distinctes.

Le service de recharge, ses protections vanilla, son journal et les writers natifs sont ceux de production. Le profil est sauvegardé par `VerifiedPlayerSaveBarrier`. Après redémarrage, un nouveau ServerPlayer est chargé depuis son vrai fichier natif avant l'appel au service de reprise ; double reprise exécutée. Cela ne vérifie pas l'événement réseau de connexion ni le rendu d'un client.

## Résultats réellement obtenus

| Cas | Pause / état durable avant terminaison | Java terminé | Reprise après nouveau démarrage | UUID du véhicule |
|---|---|---|---|---|
| `memory1` | 21:28:12, PAYMENT_MEMORY, journal PREPARED ; débit seulement en mémoire, fichier joueur encore à deux cellules/reçu0 | PID26976, exit−1, aucune commande stop | 21:28:42, ABORTED, deux cellules, charge100, reçu0, UUID natif unique | `8c02cca3-a035-43bb-9466-17f6b4d0ba91` |
| `energy1` | 21:28:58, ENERGY_DURABLE, journal PAID ; une cellule/reçu1 et charge350 gelée déjà sauvegardés | PID2404, exit−1, aucune commande stop | 21:29:25, DONE, une cellule, charge350 ; après dépense de test et nouvelle reprise, charge7 conservée | `a5d0b0ef-bcc4-4a3a-b392-93d5aa2e0aed` |
| `active1` | 21:29:45, ACTIVATED, journal APPLIED ; paiement, charge350 et dégel sauvegardés, DONE non écrit | PID4856, exit−1, aucune commande stop | 21:30:41, DONE, une cellule, charge350 ; dépense de test à7 puis reprise sans reset | `6515ea70-dace-46ae-adb9-11110fffd31b` |

La dépense à7 est une mutation de fixture suivie d'une sauvegarde native, pas une conduite observée. Le harnais l'utilise pour vérifier que DONE ne restaure pas la charge350. La conduite réelle est prouvée séparément dans P3-02, mais pas dans cette campagne sans client.

`scripts/check-recharge-recovery.py` compare ensuite les snapshots antérieurs au kill, le journal récupéré, le fichier joueur et l'enveloppe native des entités. Il lit directement le fichier de région Minecraft `entities/r.*.*.mca` (en-tête de localisation, record zlib borné, NBT), indépendamment du lookup en mémoire du serveur. Pour chaque UUID attendu : exactement une occurrence dans son chunk, même propriétaire/plan/HP, aucun `rechargeId`/`assemblyId`, énergie finale100/7/7. Inventaires et reçus natifs égaux aux images justifiées, UUID du fichier joueur correct, aucun RootVehicle. Les journaux n'ont changé que de phase.

Les métadonnées de terminaison prouvent le PID, le checkpoint, l'exit−1 et l'absence de commande d'arrêt. La sortie Gradle de chacune des trois terminaisons signale bien la fin du Java avec valeur−1 ; ce sont les échecs attendus du processus tué, pas des échecs de régression. Dernier serveur arrêté normalement à21:31:31, toutes dimensions sauvegardées à21:31:32.

## Reproduction

1. `./scripts/run-recharge-crash-server.ps1` : répertoire/port isolés ; lancement via le modèle de run ModDevGradle et propriété JVM `lotf.validation.rechargeCrash=true`.
2. Console dédiée : `lotfrechargecrash prepare <identifiant-neuf> PAYMENT_MEMORY` (ou ENERGY_DURABLE / ACTIVATED). Identifiant ASCII `[a-z0-9_-]`, 1–32 caractères, jamais réutilisé : preuve, fichier joueur ou journal existants font refuser la préparation.
3. Dès le marqueur `VALIDATION_RECHARGE_PAUSED`, exécuter `./scripts/kill-paused-recharge.ps1 -Run <identifiant> -Checkpoint <checkpoint>` dans les12secondes. La pause expire à30secondes : une expiration n'est pas une preuve de kill. Le script vérifie la pause récente, le journal/paiement natifs, un seul Java de développement du JDK projet, les arguments JVM opt-in générés et surtout l'égalité du PID avec celui enregistré par la fixture. Il recontrôle l'identité du processus avant Kill/WaitForExit ; ne touche ni client ni daemon Gradle.
4. Relancer le même serveur isolé, puis console : `lotfrechargecrash recover <identifiant>`. Vérifier `VALIDATION_RECHARGE_RECOVERED`, puis `stop` et attendre la fin des sauvegardes.
5. Avec Python du runtime : `python scripts/check-recharge-recovery.py <identifiant>`. Résumé dans `.tools/recharge-crash-<identifiant>-verified.json`. Le vérificateur ne modifie pas le monde ; il produit des copies de preuves sous `.tools`.

La commande de harnais est réservée à la console OP4, exige la propriété JVM opt-in et refuse tout monde dont le parent n'est pas `run-recharge-crash`. Classe et tous les GameTests absents du JAR distribuable, vérification réelle de son contenu effectuée. Le script de kill a été essayé sans pause : refus « No recent matching recharge pause. Nothing stopped. ».

## Traces et corrections du harnais

- `.tools/recharge-crash-{memory1,energy1,active1}-*` : copies avant/après des journaux/fichiers joueur, fixtures NBT, métadonnées et handles de terminaison, résultats indépendants. Snapshots récupérés complets également sous `run-recharge-crash/world/validation/recharge-crash/*-verified.nbt`.
- Journaux de reprise copiés dans `.tools/recharge-crash-<identifiant>-recovery-server.log` ; logs avant kill copiés par le script. Les données brutes locales ignorées par Git ne sont pas des artefacts de release.
- Deux erreurs de mise en place corrigées avant les scénarios : `run/eula.txt` n'existe pas en mode IDE (la version épinglée accepte automatiquement le mode IDE/GameTest) ; réglage direct du JavaExec remplacé par ModDevGradle. Le premier lancement avait donc démarré le serveur normal, sans aucune commande de fixture, puis a été arrêté proprement. L'isolation a été confirmée avant préparation. Le flag JVM étant dans le fichier d'arguments et non littéralement dans la ligne WMI, le ciblage final vérifie ce fichier et le PID natif enregistré par la fixture. Aucun kill hors pause de validation.
- Régression après campagne : `./scripts/gradle.ps1 build runGameTestServer --console=plain`, 83 JUnit/61 GameTests requis verts, BUILD SUCCESSFUL21:32:43 ; `.tools/test-recharge-crash-final.log`.
- JAR de production inchangé SHA256 `03A560C6B13869B3821CE0FC172AFFC43DC753F02D95F6190F172A16DF6432BC`.

## Limites

Ces trois coupures couvrent le débit non durable, l'énergie durable avant dégel et le dégel durable avant clôture. Elles ne couvrent pas une écriture tronquée, une panne électrique, tous les checkpoints, une reconnexion de vrai client, le solo/level.dat, deux clients, un crash pendant la conduite, ni des claims tiers. Les dix erreurs injectées précédentes restent une campagne distincte. La validation graphique de la recharge demeure ouverte. P3-02, P3-03 et V1.0 ne sont pas validés globalement.

Prochain travail autonome sans écran : compléter les refus et garanties de maintenance qui peuvent être testés côté serveur, puis la tranche P3-03 de dégâts/statistiques/réparation. Reprendre le parcours graphique dès qu'une session de contrôle est disponible, sans répéter les démarrages suivis d'un arrêt immédiat par Échap.
