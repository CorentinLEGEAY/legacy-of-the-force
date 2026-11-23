# P2-01 — dépôt natif et paiements du stockage

23 novembre2026. Tranche de validation du dépôt ; P2–P7/V1.0 restent ouverts.

## Source et méthode

Source arrêtée scr2, déjà vérifiée par le parcours de retrait payé : le propriétaire possède deux sabres dont la cible récupérée, son reçu vaut4 et son double coffre est vide. Le pair garde un sabre en inventaire, un dans le tonneau et son reçu3. Le mode `StorageDeposit` ajoute seulement une sélection de la cible existante puis son dépôt par de vrais clics vanilla. Aucun grant, modification de profil ou téléportation par la fixture.

Les quatre frontières restent PREPARED, PLAYER_DURABLE, STORAGE_DURABLE et CLOSED. Le nouveau fichier `saber-storage-deposit-checkpoint.nbt` est distinct du checkpoint de retrait copié ; celui-ci reste conservé octet pour octet. Les trois conteneurs natifs de départ sont lus dans la région et enregistrés, afin de ne pas réutiliser à tort l'ancien contenu sn1. Le lanceur vérifie monde/PID/port/date avant de tuer uniquement son serveur isolé. Les deux clients constatent la coupure ; les reprises utilisent une nouvelle JVM et deux nouveaux clients observateurs.

Avant paiement, le reçu reste4 et la restauration native du curseur conserve la cible chez le joueur, coffre vide. Après paiement, le reçu5 engage la cible au coffre, sans seconde copie chez le joueur. Le vérificateur compare le contenu complet, les composants, les identités persistantes, les inventaires/profils, les reçus et les projections clientes. Il confirme la conservation par rapport à la première preuve de stockage, pas seulement par rapport au checkpoint qu'il vérifie.

## Exécutions

- Build du harnais réussi29s : `.tools/saber-storage-deposit-build1.log`. Aucun changement de production. Le vérificateur étendu a été réexécuté sur les quatre anciennes paires de retrait : toutes PASS.
- dc1 PREPARED : serveur16080 réellement tué ; clients13068/24724 ont observé la coupure. dcr1 nouvelle JVM27048, clients27580/15392, trois sorties0 sans arrêt forcé. Vérificateur PASS : reçu4, cible chez le propriétaire, coffre vide, quatre identités uniques et composants/profils conservés.
- dc2 PLAYER_DURABLE : serveur21320 réellement tué, clients23744/11176 ont observé la coupure. dcr2 nouvelle JVM18712, clients16468/24964, trois sorties0 sans arrêt forcé. Vérificateur PASS : reçu5, cible stockée, plus de copie chez le propriétaire, quatre identités/composants et profils conservés. Au checkpoint payé, l'ancien coffre natif est encore vide : la reprise restitue la cible depuis l'après-image durable du journal, sans la faire perdre.
- dc3 STORAGE_DURABLE : serveur8572 réellement tué, clients4304/9444 ont observé la coupure. dcr3 nouvelle JVM15324, clients17032/19308, trois sorties0 sans arrêt forcé. Vérificateur PASS : reçu5, cible stockée, quatre UUID/composants et profils conservés.
- dc4 CLOSED : serveur18576 réellement tué, clients9800/13492 ont observé la coupure. dcr4 nouvelle JVM7612, clients7028/20020, trois sorties0 sans arrêt forcé. Vérificateur PASS : même résultat acquis au coffre, journal fermé nettoyé par la production, pair inchangé.

Les quatre nouvelles paires ont été revérifiées avec le vérificateur final et `--deposit` : PASS. La validation exige aussi l'égalité de conservation avec le premier stockage sn1, les deltas complets joueur/conteneurs et la préservation exacte du checkpoint de retrait historique. Aucune assertion du serveur seul n'est utilisée comme preuve suffisante.

Capture `run-adventure-client-one-dcr2/screenshots/saber-storage-checkpoint-observed.png` réellement inspectée : cible dans le grand coffre et autre sabre chez le joueur, curseur vide. Les notifications vanilla recouvrent une partie du menu ; aucun jugement artistique final n'est déduit de ce contrôle d'état.

Reproduction avec des identifiants neufs :

```powershell
.\scripts\test-adventure-preparation.ps1 -Run dc1 -Mode SaberStorageCrash -PreparationRun scr2 -TransferStage PREPARED -StorageDeposit
.\scripts\test-adventure-preparation.ps1 -Run dcr1 -Mode SaberStorageCheckpointRecover -PreparationRun dc1 -StorageDeposit
python scripts/check-saber-storage-checkpoints.py dc1 dcr1 --deposit
```

Pour les trois autres cas, remplacer la frontière et utiliser une nouvelle paire, toujours avec scr2 comme source du crash. Les anciennes preuves restent lisibles sans `--deposit`. Tous les clients de développement ciblent l'écran3 ; le vérificateur contrôle leurs logs.

## Refus Windows avant paiement — contrôle dédié

Le nouveau GameTest `windowsPlayerDenialBeforePaymentRestoresOnlyTheUnpaidChest` a été exécuté : un vrai handle Windows interdit le remplacement du playerdata après PREPARED. Le fichier avant reste intact et le coffre reste verrouillé. Après libération, le tick de reprise restaure seulement le coffre impayé ; une nouvelle instance joueur chargée depuis son fichier ne reçoit ni sabre ni crédit différé. Le premier acteur est un stand-in non enregistré, explicitement abandonné : sa connexion factice ne constitue pas une vraie déconnexion.

`.tools/saber-storage-deposit-regression1.log` : BUILD SUCCESSFUL46s,132 GameTests requis réussis à21:59:05. Les refus de fichier attendus et leur pile d'appel confirment l'exécution du nouveau test Windows, sans ignorer un cas.133JUnit,0échec/erreur/ignoré dans les XML relus ; tâche JUnit à jour, pas de réexécution inventée.

JAR A9DA80B03BF2A34A68C32360BF87C5B46D146139797E322E1F4A89AAB2441BC6 inchangé. Aucun changement de production ni nouvelle installation annoncée ; le contrôle du JAR installé st2 reste valide pour ce même binaire et a été relancé contre les nouveaux fichiers source de test.

## Limites

Les refus Windows de stockage avec deux sockets et sauvegarde finale de sortie restent à exercer ; les kills n'en tiennent pas lieu. Interruption entre écritures des chunks, transferts automatiques par hopper, autres menus, mortfalse, cristaux libres et finition de l'atelier restent ouverts. Les anciens rapports de retrait et de JAR gardent leur portée ; aucune nouvelle installation ni régression complète n'est déduite de ce seul ajout de harnais.
