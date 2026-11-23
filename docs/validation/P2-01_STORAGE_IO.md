# P2-01 — refus Windows du stockage et sauvegarde finale de sortie

23 novembre2026. Deux sockets réelles, aucune simulation de succès d'écriture. La validation est en cours.

## Méthode

`SaberStorageIoNetworkValidation` utilise les conteneurs/sabres des sources arrêtées sn1 ou scr2, sans grant/XP/téléportation. Deux clients non-OP observent leur menu ; seul le premier transfère une cible déjà présente. À PREPARED, un vrai handle NOSHARE_WRITE/NOSHARE_DELETE interdit le remplacement du playerdata. À PLAYER_DURABLE, il bloque celui du journal. Une tentative WRITE indépendante doit être refusée par Windows. Les ACL ne sont pas modifiées.

La production doit suspendre l'opération et déconnecter le propriétaire. Le handle reste ouvert pendant PlayerLoggedOutEvent **et** sa sauvegarde vanilla ultérieure ; seul un tick voyant le propriétaire absent de PlayerList peut le libérer, après comparaison des octets du fichier verrouillé. Le pair reste connecté, observe de nouveau, puis serveur et pair s'arrêtent normalement. Une nouvelle JVM/deux nouveaux clients relisent l'état ; le harnais ne restaure pas les inventaires.

Le vérificateur `check-saber-storage-checkpoints.py --io` contrôle les vrais refus, la pile de sauvegarde finale, l'ordre des marqueurs, les empreintes avant/après sortie, le motif de déconnexion FR, les reçus complets et opérations, les régions, les UUID/composants, le profil RPG et les observations avant/après du même pair. Le champ historique `crashedPid` du harnais de reprise désigne ici le PID précédent : aucun kill n'est demandé en mode IO.

## Défaut réellement révélé

sio1 PREPARED et sior1 nouvelle JVM ont produit leurs accusés de harnais, mais le vérificateur indépendant a échoué : un sabre supplémentaire apparaît chez le joueur après reprise. Lecture native : avant/checkpoint/sortie/après IO, le fichier joueur ne contenait qu'un sabre et le reçu3 ; le coffre impayé avait été restauré. La région d'entités de sio1 contenait aussi une ItemEntity de la cible. À la connexion suivante, elle est ramassée normalement, créant une seconde copie à côté du coffre.

Sources épinglées NeoForge21.1.265 relues : `PlayerList.remove` sauvegarde le joueur avant son retrait du niveau ; `Player.remove` ferme ensuite l'inventaire personnel et le menu externe. `AbstractContainerMenu.removed` jette directement le curseur d'un joueur déconnecté. Cet appel direct échappait au scope ItemTossEvent utilisé par l'ancien wrapper. **sio1/sior1 ne sont pas validés et restent conservés, sans nettoyage de la duplication.**

Correction de production : la fermeture d'un joueur déjà déconnecté ou engagé dans une transaction stockage n'appelle plus le chemin vanilla qui publie les objets temporaires. Cette garde précède celle d'acteur vivant : `Player.remove` marque déjà l'acteur retiré avant les fermetures. Le curseur externe reste dans la garde personnelle différée ; curseur personnel et entrées2×2 sont conservés. Aucune ancienne image d'inventaire n'est réinjectée : le reçu et le playerdata réellement écrits restent l'autorité de reprise. Le nouveau test repart de sn1 intact, pas d'une copie du monde dupliqué.

## Vérifications de la correction

Deux GameTests ajoutés : fermeture externe après sauvegarde/déconnexion sans publication, restauration unique du curseur ; fermeture personnelle déconnectée gardant curseur et quatre entrées sans entité au sol. `.tools/saber-storage-logout-fix-regression1.log` : BUILD SUCCESSFUL49s,134 GameTests requis réussis22:13:20. Les tests ont ensuite été renforcés pour appeler `Player.remove` complet, au lieu de sa seule fermeture de menu ; leur régression finale reste à exécuter.

- sio2→sior2 PREPARED retrait : serveur6440 puis nouvelle JVM9176 ; clients26448/2676 puis5348/19104. Toutes les sorties0, aucun kill/arrêt forcé, propriétaire déconnecté par la production, pair continuellement en ligne. Vérificateur indépendant PASS : reçu3, source restée stockée, aucune copie joueur/sol, quatre UUID/composants et profils conservés.
- sio3→sior3 PLAYER_DURABLE retrait : serveur15816 puis nouvelle JVM1524 ; clients26464/22400 puis9608/23464. Même sortie propre et contrôle indépendant PASS : reçu4, cible chez le joueur après récupération native du curseur, coffre vide et aucune ItemEntity supplémentaire.
- sio4→sior4 PREPARED dépôt : serveur19468 puis nouvelle JVM1552 ; clients25376/15392 puis11132/12392. Sorties0 sans kill/arrêt forcé, fichier joueur conservé sous verrou jusqu'à sortie, reçu4/cible toujours chez le propriétaire ; quatre identités conservées et vérificateur indépendant PASS.
- sio5→sior5 PLAYER_DURABLE dépôt : serveur18688 puis nouvelle JVM10744 ; clients5692/18768 puis18892/14280. Même protocole de sortie et vérificateur PASS : reçu5/cible stockée, aucune copie au joueur ou au sol. Les quatre parcours IO ont bien été exécutés, avec motif de déconnexion natif FR et seconde observation du même pair.

Le JAR85E4EBC8026A5EA6E629BA05F17C7995A9574AC35C7DF44D23E76C5BE1052109 contenant la correction a été installé et relancé dans sl1 : JVM5632/8608 sorties0, sauvegardes natives/Ilum, neuf mixins réellement appliqués, aucun harnais distribué ; vérificateur PASS. Les quatre anciennes paires de crash retrait et quatre de dépôt restent PASS avec les contrôles renforcés du reçu complet. Ce sont des relectures de preuves existantes, pas huit nouveaux lancements. Ces preuves précèdent les nouveaux lots atelier/compétences : elles ne valident pas ces développements ultérieurs.

Pendant ces exécutions, le concepteur a demandé de regrouper les développements puis les tests. Aucun nouveau scénario isolé n'est lancé après achèvement des processus en cours. Les tests renforcés avec Player.remove et les développements suivants attendent la campagne globale décrite dans09_LOTS_V1.md.

## Reproduction

```powershell
.\scripts\test-adventure-preparation.ps1 -Run sio2 -Mode SaberStorageIo -PreparationRun sn1 -TransferStage PREPARED
.\scripts\test-adventure-preparation.ps1 -Run sior2 -Mode SaberStorageCheckpointRecover -PreparationRun sio2
python scripts/check-saber-storage-checkpoints.py sio2 sior2 --io
```

Utiliser des identifiants neufs ; PLAYER_DURABLE couvre le paiement déjà durable. `-StorageDeposit` et `--deposit` permettent le sens dépôt depuis scr2. La présence de ces options ne prouve pas leur exécution. Tous les clients sont lancés sur écran3.

## Limites

Les menus externes autres que coffre/tonneau, automatisation/hoppers, mortfalse, cristaux libres, changements de dimension avec objets temporaires, pannes entre écritures des chunks et charge restent ouverts. La correction évite la publication des curseurs et de la grille personnelle après déconnexion ; elle ne garantit pas les entrées internes de tous les menus moddés. P2–P7/V1.0 ne sont pas achevés.
