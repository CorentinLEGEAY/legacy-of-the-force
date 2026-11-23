# P2-01 — coupures réelles aux frontières DROP

23 novembre 2026. **Tranche validée ; P2-01, P2 et V1.0 restent partiels.** Ce rapport couvre cinq frontières de dépôt Q hors menus et cinq frontières de ramassage de sabres identifiés. Les refus de fichiers Windows, dépôts GUI, mort avec keepInventory=false, conteneurs/hoppers et cristaux libres restent à vérifier/sécuriser.

Actualisation : le sens PICKUP est désormais également exercé aux cinq frontières, voir la section ci-dessous. Les refus Windows et autres chemins de transfert restent ouverts.

## Méthode

Chaque crash part d'une nouvelle copie arrêtée de sw2. Deux clients Minecraft natifs se connectent ; le premier envoie le vrai Q. Le coordinateur de production émet un événement immuable à chaque frontière durable. Le harnais lit le journal, les playerdata et les entités natives sans les resauvegarder, écrit sa preuve, puis attend au maximum15secondes. Le lanceur vérifie monde, PID Java, démarrage, arguments et propriétaire du port loopback avant le kill système. Le processus serveur ne reçoit pas stop ; les deux clients constatent la vraie déconnexion et sortent normalement. Le runner serveur termine1 comme attendu après le kill.

Une nouvelle copie de chaque monde crashé démarre avec un nouveau serveur et deux nouveaux clients. Aucun profil, inventaire, item ou position n'est réinjecté. La production réconcilie le journal à partir des reçus natifs. La fixture refuse seulement le pickup de l'UUID cible pendant l'observation : cette aide évite qu'un ramassage ultérieur transforme le résultat de reprise. Chaque ClientLevel confirme présence/absence et composants, puis produit sa capture framebuffer. Le serveur sauvegarde et s'arrête normalement ; trois sorties0, sans force.

Le vérificateur indépendant relit également les fichiers MCA, playerdata, journaux et preuves des six processus par paire. Il exige un seul exemplaire natif de l'identité cible, quatre identités uniques au total, composants complets conservés, pair inchangé et journal final vide. Les mondes sources sont conservés.

## Résultats sur le code courant

| Frontière | Crash → reprise | Journal au kill | Reçu payé | Entité déjà stockée | Serveurs crash / reprise | Clients crash ; reprise |
|---|---|---|---|---|---|---|
| PREPARED | stc1 → str1b | PREPARED | Non | Non | 22208 / 25608 | 22540,25624 ; 24192,23308 |
| PAYMENT_DURABLE | stc2 → str2b | PREPARED | Oui | Non | 3724 / 21908 | 21076,23392 ; 26260,10652 |
| HELD | stc3 → str3b | HELD | Oui | Non | 14528 / 3248 | 21364,15124 ; 4416,2960 |
| ENTITY_DURABLE | stc4 → str4 | HELD | Oui | Oui | 6516 / 17704 | 11344,20436 ; 17148,4904 |
| CLOSED | stc5 → str5 | CLOSED | Oui | Oui | 2328 / 24112 | 22068,19400 ; 13900,22576 |

Les cinq vérifications passent. PREPARED impayé conserve l'objet dans le playerdata ; les quatre autres reconstruisent ou conservent exactement une entité avec le même UUID et les mêmes composants, sans remettre l'objet dans le joueur. PAYMENT_DURABLE exerce réellement un reçu payé avec un journal encore PREPARED. CLOSED exerce un fichier fermé encore présent avant son nettoyage.

Commande exécutée avec le Python du runtime local :

```powershell
& $validationPython scripts/check-saber-transfer-checkpoints.py stc1 str1b stc2 str2b stc3 str3b stc4 str4 stc5 str5 --all-drop
```

Résultat conservé : `.tools/saber-checkpoints-all-drop-check.json`, `allFiveDropBoundariesCertified=true`, `pickupMidCrashCertified=false`. Logs des lanceurs : `.tools/saber-checkpoint-<run>-launch.log`. Preuves : `run-adventure-<crash>/world/validation/saber-transfer-checkpoint.nbt`, `run-adventure-<reprise>/world/validation/adventure-sabercheckpointrecover-server.nbt`, et deux dossiers `run-adventure-client-{one,two}-<run>/validation`. Ces ressources volumineuses restent locales, non distribuées.

Pour reproduire toute la matrice sur un préfixe neuf, avec un monde de préparation sw2 existant et arrêté :

```powershell
.\scripts\test-saber-transfer-checkpoints.ps1 -Run nouveau -PreparationRun sw2 -PythonPath 'C:\chemin\vers\python.exe'
```

Le runner refuse tout monde cible déjà présent. Il enchaîne les mêmes commandes natives puis le vérificateur ; son parseur PowerShell a été exécuté avec succès. La matrice ci-dessus a été exécutée par les commandes constitutives avant création de ce wrapper ; aucune exécution supplémentaire du wrapper complet n'est revendiquée.

## Défaut révélé et correction

Les premières reprises str1/str2 passent, mais str3 reste HELD sans entité pendant19secondes ; la protection de login déconnecte le premier client au lieu de laisser utiliser son profil avant réconciliation. Le pilote échoue, serveur stop0 ; le lanceur force le pair à l'échéance. str3 est conservé comme échec, sans validation fictive.

Lecture des sources épinglées21.1.265 : getChunkAt donne un ticket UNKNOWN d'un tick. Sans joueur, il ne garantit pas le maintien du chunk pendant son chargement asynchrone d'entités entre deux reprises espacées de20ticks. Le coordinateur utilise désormais un ticket UUID dédié, un seul chunk à la fois, réessaie au tick pendant son bail configurable, puis libère à fermeture/erreur/arrêt. Il ne tourne pas vers une autre entrée avant d'avoir laissé ce chargement aboutir ou expirer. Le timeout conserve le journal et rend la main aux autres entrées.

str3b repart du stc3 original, sans modification d'inventaire : succès. str1b/str2b répètent également les premiers cas sur le code corrigé. stc4/stc5 et leurs reprises utilisent ce même code.

## Régression

Deux GameTests ajoutés : ordre des cinq notifications et relecture des vrais fichiers à chaque frontière ; reprise dans un chunk initialement non chargé sans joueur en ligne. Le premier essai du second test échoue par timeout de fixture : le même Runnable se réinscrivait dans une map dont le planificateur supprimait ensuite la clé. Remplacement par succeedWhen, sans modifier le timeout ni neutraliser d'assertion.

`scripts/gradle.ps1 build runGameTestServer --console=plain` : **BUILD SUCCESSFUL37s, 112GameTests requis passent à19:10:27**. Log `.tools/saber-checkpoint-lease-regression2.log`. **133JUnit, zéro échec/erreur** dans33rapports XML. Vérification installée du JAR courant et référence Git consignées dans le journal du projet après exécution.

## Extension : cinq frontières PICKUP

Chaque crash puc1–5 part d'une copie sd4 arrêtée : reçu1, un sabre natif au sol, un chez le premier joueur, deux chez le pair. Les deux ClientLevel observent la source et ses composants, produisent une capture/prefix d'armement et accusent réception. La fixture libère alors le pickup ; le premier client marche réellement vers l'entité. Le harnais interrompt uniquement la frontière PICKUP demandée. Le kill est toujours un vrai arrêt de processus avec les gardes d'identité du lanceur ; aucun transfert n'est fabriqué par la fixture.

| Frontière | Crash → reprise | Journal au kill | Copies natives au kill | Serveurs crash / reprise | Clients crash ; reprise |
|---|---|---|---|---|---|
| PREPARED | puc1 → pur1 | PREPARED | 1 | 23984 / 15756 | 7368,10316 ; 10696,4136 |
| PAYMENT_DURABLE | puc2 → pur2 | PREPARED | 2 | 23412 / 10160 | 24556,6892 ; 10236,6044 |
| HELD | puc3 → pur3 | HELD | 2 | 22524 / 18556 | 19024,25720 ; 13984,22140 |
| ENTITY_DURABLE | puc4 → pur4 | HELD | 1 | 19676 / 11148 | 11444,22904 ; 11688,24964 |
| CLOSED | puc5 → pur5 | CLOSED | 1 | 18576 / 15044 | 4344,7128 ; 2784,14716 |

Les cinq paires passent. Avant paiement, la reprise conserve la source au sol et le reçu1. Après paiement, elle conserve uniquement le sabre dans le joueur, reçu2, et retire l'entité native. Les deux copies physiques intermédiaires à PAYMENT_DURABLE/HELD sont accompagnées du journal ouvert ; la reprise les réconcilie avant de permettre le login du propriétaire. Il n'y a ni second crédit ni restauration d'ancien inventaire. Quatre UUID de sabres uniques et composants complets conservés dans chaque reprise, pair inchangé, journaux finaux vides.

Le runner complet est cette fois réellement exécuté :

```powershell
.\scripts\test-saber-transfer-checkpoints.ps1 -Run pu -PreparationRun sd4 -Direction PICKUP
```

Le vérificateur a ensuite relu les dix paires dépôt/ramassage :

```powershell
& $validationPython scripts/check-saber-transfer-checkpoints.py stc1 str1b stc2 str2b stc3 str3b stc4 str4 stc5 str5 puc1 pur1 puc2 pur2 puc3 pur3 puc4 pur4 puc5 pur5 --all-drop --all-pickup
```

PASS : `.tools/saber-all-ten-checkpoints-check.json`, les deux certifications true. Le contrôle cherche également une autre entité portant la même identité de sabre avec un UUID différent. Log du runner `.tools/saber-pickup-checkpoint-matrix.log`. Kills : runners nonzero attendus, deux clients0 ; reprises : serveur/deux clients0 sans force. Captures/prefix des clients et preuves natives sont conservés dans les dossiers isolés puc/pur. Capture avant pickup puc2 réellement inspectée : poignée grise/noire et commande rouge suspendue au-dessus de la table, terrain/HUD/hotbar ; notifications vanilla offline visibles. Cette image précède la nouvelle refonte artistique.

Aucune correction de gameplay nécessaire pendant cette matrice. Régression préalable :112GT requis passent19:20:55, BUILD SUCCESSFUL42s, `.tools/saber-pickup-checkpoint-regression.log`. Les derniers clients de cette matrice incluent le premier essai de préférence d'écran, dont le nom d'adaptateur est corrigé ensuite ; la preuve de placement figure avec la validation artistique. Les gates IO Windows/GUI/mortfalse/conteneurs/hoppers ne sont pas fermées par ces dix kills.
