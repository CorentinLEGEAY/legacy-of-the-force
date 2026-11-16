# P4-03 — Duels solo intégrés et propriétaire sauvegardé

Statut : deux duels/collectes solo et rechargement natif validés ; P4-03 et V1.0 restent EN COURS.

## Protocole reproductible

```powershell
./scripts/test-boss-solo.ps1 -Run bs3 -TemplateRun bn6
# Avec le Python 3 disponible sur la machine :
python scripts/check-boss-solo.py bs3
./scripts/test-boss-solo.ps1 -Run bs4 -TemplateRun bs3 -Recover
python scripts/check-boss-solo.py bs4
```

Le premier lancement copie seulement level.dat d'un monde dédié arrêté, sans propriétaire embarqué. Aucun chunk, joueur, reçu ni ancien butin n'est repris. Minecraft charge ce monde avec quickPlaySingleplayer ; le bouton natif de sauvegarde/chargement des données expérimentales est actionné dans le seul profil isolé. Le validateur exige la vraie classe IntegratedServer, un unique ForceTesterSolo et sa connexion mémoire native. Les mêmes deux structures naturelles, phases, glace, signatures, parades, contres payés et collectes que le test réseau restent obligatoires.

Fixtures déclarées : niveau15/1232XP, sabre bleu éveillé, armure diamant, seize pommes dorées et placement avant chaque duel. Mobs ambiants désactivés uniquement dans ce monde de validation. Aucun soin, dégât ni déplacement serveur pendant les combats. Le driver utilise les actions/paquets natifs du client ; il lit seulement le marqueur de fin serveur pour coordonner l'arrêt normal. Les harnais sont dans gameTest et exclus du JAR distribué.

Le second lancement refuse une source ouverte et copie le monde solo arrêté intégralement dans un autre profil. Même propriétaire, nouvelle JVM ; aucun équipement, XP ou placement supplémentaire. Après le chargement joueur et cent ticks, les inventaires, identités et champs de récompenses/reçus sont vérifiés, puis Minecraft ferme normalement.

Le checker indépendant recoupe les preuves serveur/client, playerdata, propriétaire embarqué dans level.dat, régions natives, autels, deux reçus personnels et journaux de collecte. Le rechargement exige les mêmes inventaires/identités, XP/alignement et reçu de collecte, et les journaux boss/RPG byte pour byte inchangés ; les boss morts doivent rester absents. Une copie arrêtée ne simule pas un crash OS.

## Résultats

Compilation du harnais réussie. Essai bs1 refusé : les deux victoires/collectes ont eu lieu, mais aucun dégât ni contre Force sur glace Sith. Arrêt normal0, sans preuve de succès globale ; monde et preuves de refus conservés. Driver corrigé : orientation native transmise avant la commande visant un projectile rapide ; attente en phase2 jusqu’au feedback de poussée ou blessure de glace, sans empêcher de frapper la glace. Le serveur continue à exiger son propre événement de dégâts/contre, aucun critère affaibli. Nouvel essai bs3 en monde neuf : PASS.

- **bs3 PID6960**, serveur intégré et client natifs dans la même JVM, connexion mémoire. Gardien PASS02:29:49 : phases1/2/3, signature, trois parades, trois contres de Force payés et douze coups réels sur glace. Sith PASS02:31:30 : phases1/2/3, nova, quatre parades, deux contres de boss et **une poussée payée sur un vrai projectile de glace**. Preuves serveur/client02:31:35 ; arrêt normal0 et toutes dimensions sauvegardées02:31:37.
- Checker indépendant bs3 PASS : 1232→2832XP (+1600), −250 dixièmes puis0, deux reliques/huit cellules, deux révisions de collecte, deux reçus personnels de victoire. Sabre/cristal/assemblage conservés, pratique acquise. Inventaires natifs/client et propriétaire embarqué identiques ; génération du checkpoint core identique dans playerdata, level.dat et dernière preuve native. Autels vides estampillés sans verrou, structures intactes, boss morts absents et aucune victoire en attente.
- **bs4 PID13804**, copie intégrale arrêtée de bs3, même propriétaire. Aucun rééquipement/attribution/placement. Serveur et client RECOVERY_PASS02:32:40, fermeture normale0, toutes dimensions sauvegardées. Checker bs4 PASS : inventaires/identités/XP/alignement/reçu conservés, génération native avancée par la barrière de contrôle et embarquée à la fermeture. Journaux boss/RPG byte pour byte inchangés, mêmes autels vides, aucun boss ressuscité.
- Régression `./scripts/gradle.ps1 test runGameTestServer build` : BUILD SUCCESSFUL35s ; **98GT requis passent02:31:33**, rapports **112JUnit,0échec/erreur/skip** (JUnit UP-TO-DATE, production inchangée). JAR SHA256 **54C18F0EA2DE48180B2D1BC28D82BAA09CA1715FEEB07950914033138A7ACFCE**, aucun harnais gameTest distribué. Ancien checker réseau bn6 repassé après généralisation solo, sans refaire les deux combats réseau. Seul daemon Gradle8144 reste actif après les essais.

 Deux captures Computer Use observées dans le vrai Minecraft Singleplayer : autel Sith après collecte Jedi, puis combat du Seigneur du Givre avec sabre bleu/HUD et endurance visibles. Ces observations ne remplacent pas les preuves natives.

## Limites

Les essais ne couvrent pas encore les étapes interrompues de publication/victoire, la mort ou le changement de dimension sous panne, tous les vecteurs de protection, les claims tiers ni le profiling. Difficulté/ergonomie et direction artistique avancée restent à apprécier par un humain. P4-04/05 et les cinq autres planètes/dix boss restent ouverts.
