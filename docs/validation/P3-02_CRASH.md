# P3-02 — arrêts forcés du processus pendant assemblage

**Trois scénarios vérifiés sur dédié avec un client réel**, 11 novembre 2026. P3-02 reste **EN COURS** : garage/recharge, solo, deux clients et autres cas de cycle de vie restent ouverts. Ce rapport ne transforme pas trois checkpoints en garantie de toute panne possible.

## Mini-plan et instrumentation

À partir de `0f1841d`, vérifier le vrai processus entre paiement et publication, puis après écriture d'entité et après activation. Fichiers : commande OP2 `SpeederValidationCommands` dans **gameTest seulement**, `scripts/kill-paused-assembly.ps1`, lecteur NBT borné `scripts/read_nbt.py` et contrôleur de fichiers `scripts/check-assembly-recovery.py`. Aucun code de gameplay de production changé : coordinateur/barrières de `69d32fd`. Références : architecture, règles AGENTS, SPEED-03/10 et sécurité des sauvegardes.

La commande `lotfvalidation assembly_pause <x y z> <checkpoint>` utilise le vrai service, le vrai plan tenu et de vraies pièces. Elle n'offre ni pièces ni véhicule. Le probe attend au checkpoint demandé pendant **30 secondes maximum**, puis continue normalement et écrit un marqueur EXPIRED ; cette expiration ne constitue pas un arrêt forcé. La commande et ses pauses sont absentes du JAR distribué.

Le script de terminaison vérifie : marqueur récent avec checkpoint/propriétaire, un seul Java dédié **de développement** lancé depuis le JDK de ce dépôt avec `serverRunProgramArgs.txt`, phase attendue du journal, inventaire natif payé et reçu natif à la révision attendue. Il copie les fichiers avant de terminer le processus, revalide son identité, puis utilise son handle et attend sa fin. Il ne cible ni le client, ni le daemon Gradle, ni un autre Java. Pas de commande `stop` ni de sauvegarde d'arrêt pour ces trois coupures.

## Résultats réels

Compte ForceTesterOne, même UUID joueur, un seul client isolé. Chaque ligne est **une nouvelle transaction distincte**, alimentée par quatre nouveaux composants de validation avant son démarrage. **Aucune pièce redonnée entre une coupure et sa reprise**. Plateformes additives séparées au-dessus du toit du Nether, garages x1005/y240 ; ce sont des mondes de test, pas des constructions personnelles effacées.

| Checkpoint coupé | Phase journal avant coupure | Révision native payée | Véhicule prévu et retrouvé | Login de reprise | Garage z |
|---|---|---:|---|---|---:|
| PAYMENT_DURABLE, 20:20:59 | PREPARED | 3 | `23c11d10-9f16-4e1f-8e05-0548e47de3d9` | 20:24:42 | 1025 |
| ENTITY_DURABLE, 20:27:17 | PAID | 4 | `4e31c77f-f61c-4471-b7ce-eeff8b723d59` | 20:30:09 | 1045 |
| ACTIVATED, 20:31:25 | PUBLISHED | 5 | `32392c62-c238-4986-bfce-605b19b70ba3` | 20:32:28 | 1065 |

Pour chaque coupure : le Java enfant se termine avec **−1**, tâche Gradle `runServer` en échec attendu et client en « Connection reset ». Redémarrage normal puis connexion normale, **sans probe de reprise** : journal à DONE, transaction complète inchangée sauf sa phase, inventaire égal à l'unique après-image payée, même reçu/révision, aucun RootVehicle. Commandes serveur de lecture d'état et sélecteur de tous les speeders à **six blocs** du garage : une seule réponse, l'UUID de la ligne ; quatre modules/HP100, propriétaire et plan conservés, `assemblyId` absent. Aucune nouvelle identité ni second paiement dans ces reprises.

Le cas PAID retrouve une entité déjà écrite et encore gelée ; le cas PUBLISHED accepte celle déjà activée avant la coupure. Après ce dernier cas : clic droit normal pour monter, HUD 1000, touche locale Z. À 20:34:32 le serveur lit `(1002.5,242.00002011090183,1065.9257480516824)` et énergie `982.0239999999994` : le véhicule récupéré est pilotable et continue à consommer sa charge. Client fermé en selle et serveur arrêté normalement à 20:35:29 après les preuves de crash.

## Traces et limites de la preuve

Sous `.tools/crash-<checkpoint en minuscules>-r<révision>-*` : journal/player bruts copiés au checkpoint, JSON de lecture, métadonnées et log avant coupure ; résultat `*-recovered.json` des assertions après connexion. Logs de reprise séparés `*-recovery-server.log`. Le dernier cas conserve aussi les fichiers natifs bruts relus après reprise (`*-recovered-journal.nbt`, `*-recovered-player.dat`) et le log final de conduite. Pour les cas 3/4, les comparaisons et commandes ont bien été exécutées avant que la transaction suivante remplace le dernier journal ; ne pas prétendre que leurs fichiers après reprise sont encore les fichiers courants à la révision 5.

Deux difficultés du harnais sont tracées, sans les compter comme réussite de jeu :

- Premier essai à la révision 2 : recherche du Python embarqué incorrecte, refus de terminer quoi que ce soit, puis expiration de la pause. Cette transaction s'est terminée normalement avec ses propres pièces ; **elle n'est pas une preuve de crash**. Chemin Python corrigé, option `-PythonPath` disponible et lecture NBT versionnée dans `scripts/`.
- Cas ENTITY_DURABLE : terminaison effective, puis premier échantillon WMI encore périmé. La session exec **98043** est terminale (échec 1, Java enfant −1), puis une nouvelle lecture WMI ne trouve plus de dédié. Ces faits sont consignés séparément ; le PID numérique n'avait pas été conservé pour ce cas et n'est pas inventé. Le script final vérifie désormais `WaitForExit/HasExited` sur le handle ; cas ACTIVATED validé ainsi, PID20276, sortie−1. Le premier arrêt PAYMENT_DURABLE avait ciblé PID24160.

Le lecteur NBT est en lecture seule, décompression/profondeur/collections bornées et UTF modifié Java traité ; essais de chaîne NUL+emoji, troncature et budget réussis. Son JSON permet une comparaison structurelle des valeurs, **pas** une validation des types binaires de schéma ; les gardes Java et barrières natives portent ces validations. Le contrôleur de fichiers exige séparément une vérification du vrai véhicule dans le monde, effectuée par les commandes serveur ci-dessus.

Régression finale : `./scripts/gradle.ps1 build runGameTestServer --console=plain`, **80 JUnit, 53 GameTests requis réussis**, BUILD SUCCESSFUL à 20:38:04 ; `.tools/test-speeder-crash-final.log`. JAR inspecté : aucune classe de commande de validation/GameTests ni structure de test. SHA-256 inchangé `37E282FF67BC6E9515DFA49B8304C67782A6EBB3F40BF8C5768123201E3B2DFD`. Les trois `runServer FAILED` volontairement provoqués ne sont pas les résultats de cette régression.

## Reproduction encadrée

Utiliser un monde de développement jetable, un client connecté, joueur OP2 près d'un garage, plan valide enregistré tenu, curseur vide et quatre pièces disponibles. Vérifier le chemin Python **avant** de mettre en pause. Garder les contrôles de fichiers et la recherche du véhicule **avant** la prochaine transaction du même joueur : un seul dernier journal est conservé.

1. Console : `execute as ForceTesterOne at @s run lotfvalidation assembly_pause 1005 240 1025 PAYMENT_DURABLE` (adapter garage/checkpoint).
2. Immédiatement après le marqueur PAUSED : `./scripts/kill-paused-assembly.ps1 -Checkpoint PAYMENT_DURABLE -ExpectedRevision 3 -PythonPath '<python.exe>'` ; ne pas retirer les gardes si le script refuse ou si EXPIRED apparaît.
3. Vérifier la fin réelle de cette session/processus, puis relancer `./scripts/gradle.ps1 runServer --console=plain` et reconnecter le client normalement.
4. `python scripts/check-assembly-recovery.py PAYMENT_DURABLE 3`, puis lire dans le serveur l'UUID de `*-meta.json` et compter les entités de la zone : `execute in minecraft:the_nether positioned 1005 240 1025 as @e[type=legacyoftheforce:speeder,distance=..6] run data get entity @s UUID`.
5. Reprendre les deux autres checkpoints avec des nouvelles transactions/pièces et les révisions natives correspondantes ; ne pas présenter ces nouveaux paiements comme une deuxième consommation de la même transaction.

## Reprise et travail restant

Tous les processus de test sont fermés ; seul le daemon Gradle reste disponible. ForceTesterOne : reçu révision5, plan réutilisable conservé, pièces de ces transactions consommées ; dernier véhicule `32392c62-c238-4986-bfce-605b19b70ba3` sur la plateforme z1060..1072. Les plateformes précédentes et leurs véhicules de validation restent dans le monde ; cinq fabrications distinctes ont été payées au total (initiale, essai expiré, trois coupures), sans les confondre avec cinq copies d'une transaction.

Prochaine tranche : garage/recharge d'instance, puis parcours solo et autres cas du cycle de vie. Arrêt forcé **avant** paiement natif, coupure dans une écriture elle-même, panne électrique, crash en mouvement, deux clients stables et claims tiers ne sont pas prouvés par ce rapport ; certains chemins avant paiement ont seulement les tests automatisés de fichiers/ports décrits dans P3-02_ASSEMBLY. Aucune phase globale ni V1.0 validée.
