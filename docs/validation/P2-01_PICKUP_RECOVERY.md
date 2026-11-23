# P2-01 — reprise du dépôt interrompu, pickup natif et avant-image

Tranche du23 novembre2026 : sd3→sp1 puis correctif d'avant-image et sd4→sp2 réussis. P2-01 reste partiel pour les coupures intermédiaires, les erreurs d'accès réelles et les autres chemins de transfert.

## Nouveau serveur et vrai ramassage

```powershell
.\scripts\test-adventure-preparation.ps1 -Run sp1 -Mode SaberPickup -PreparationRun sd3
.\scripts\test-adventure-preparation.ps1 -Run sd4 -Mode SaberCrash -PreparationRun sw2
.\scripts\test-adventure-preparation.ps1 -Run sp2 -Mode SaberPickup -PreparationRun sd4
```

Chaque source est une copie arrêtée, verrou de session sondé et JVM source absente. Aucun sabre, inventaire, XP, position joueur ou position d'item réinitialisé. Le drop peut suivre sa physique vanilla après chargement. Un hook de fixture refuse temporairement le pickup du **seul UUID cible**, jusqu'à deux observations indépendantes dans les ClientLevel ; puis il libère les règles natives. Le premier marche via la touche de mouvement de sa JVM et entre dans la portée de collecte. Ni appel serveur de pickup ni modification directe d'inventaire par le harnais.

sp1 : serveur **15100**, clients **25504/21480**, trois sorties0 ; fin17:38:26UTC. Le serveur est distinct de celui réellement tué dans sd3. Deux confirmations de présence, un seul événement natif de pickup, reçu1→2 dans le fichier du receveur, une entité retirée durablement. Les quatre identités/composants et les autres objets sont conservés ; inventaire et reçu du pair inchangés, aucun gain RPG. `scripts/check-saber-pickup-recovery.py sp1` PASS. Capture collected du premier inspectée : HUD/inventaire partiel visibles, caméra levée et notifications offline ; aucune vue artistique complète ni lecture des36slots déduite de cette image.

Le pilote de collecte réoriente ensuite sa vue après ramassage pour les captures suivantes. Il s'agit d'un changement de harnais, pas d'un déplacement de fixture ou d'une correction du contrôle serveur.

## Avant-image et source de pickup non payée

Le coordinateur de dépôt reconstitue temporairement le stack retiré par Q dans une place libre et sauvegarde/vérifie cet agrégat **avant PREPARED**. Il rétablit ensuite exactement les emplacements postQ et poursuit le journal/reçu. Un échec de cette première barrière garde le sabre chez le joueur et ne publie rien. Cette étape ferme le cas d'un objet très récent absent du dernier autosave : une intention non payée retrouve bien l'objet dans son avant-image native.

Inversement, une intention de pickup PREPARED non payée ne doit pas être fermée en perdant un item dont la première sauvegarde d'entité n'avait jamais eu lieu. La reprise garde le receveur impayé et assure la présence durable de la source, via l'entité existante ou sa reconstruction depuis le payload. Le même UUID et les mêmes composants sont exigés, aucun ancien inventaire réinjecté.

Deux GameTests supplémentaires exercent ces frontières avec les vrais writers : ancienne image joueur vide puis journal devenu indisponible par une entrée volontairement malformée, refus de dépôt avec sabre avant-image natif/reçu0 ; intention pickup seule persistée, receveur vide/reçu0 et source jamais sauvegardée, reconstruction native unique sans paiement. **L'indisponibilité de ce test est un refus du journal pour corruption de fixture, pas un verrou Windows ou une panne de processus revendiqués.**

## Répétition avec le correctif

sd4 : serveur **24900** tué à17:43:15.104UTC après contrôle PID/args/start/monde/port ; clients **7160/19644** vraie coupure puis sorties0, runner1 attendu. `check-saber-drop-crash.py sd4` PASS : un exemplaire natif du sabre après le même ordre de sauvegarde et kill que sd1. Le monde original sd1 reste double et conservé.

sp2 : nouvelle JVM **10044**, clients **19652/22752**, trois sorties0 ; nouveau coordinateur, un seul vrai pickup, reçu du receveur1→2, pair inchangé, quatre identités uniques et entité cible absente de toutes les régions natives finales, journal vide. `scripts/check-saber-pickup-recovery.py sp2 --source sd4` PASS : six processus distincts, playerdata et preuves des deux ClientLevel, ItemStack complet et bilans de stock concordants ; source crashée conservée avec son drop. Capture collected du premier inspectée : table/terrain, HUD et hotbar visibles ; notifications vanilla offline recouvrent le haut droit, sans certifier toute l'interface.

## Régression et distribution

`scripts/gradle.ps1 build runGameTestServer --console=plain` : BUILD SUCCESSFUL38s, **110GameTests requis verts18:41:55**, log `.tools/saber-beforeimage-regression.log`. Le harnais de caméra est recompilé et exercé par sp2 après cette suite ; aucun changement de production ultérieur.

Rapports JUnit : **133tests, zéro échec/erreur/ignoré**. Parseur des deux lanceurs PowerShell et `git diff --check` PASS. Checker sd4→sp2 relancé : PASS.

JAR SHA256 **20772FD715A0C4DC41046F3ABC0C5044BC4D8B55FD535D989FE7BA30CF7B1111**. Test installé jarst3 : deux nouvelles JVM **18668/6264**, sorties0 sans force ; `scripts/check-packaged-server.py jarst3` **PASS**, aucun harnais distribué, trois mixins requis appliqués, Ilum chargé, flux natif conservé entre les boots. Aucun nouveau parcours client déduit du smoke installé. Au dernier contrôle, seul daemon Gradle25844 reste.

Restent : vrais kills pendant PREPARED/paiement/publication/retrait/fermeture, accès Windows refusés, dépôts GUI, mortfalse/conteneurs/hoppers et échanges. Ces parcours ne sont pas remplacés par les frontières injectées ni par un redémarrage après journal déjà clos. P2-01/P2/V1.0 non terminés.
