# P1-05 — impulsions sur serveur dédié et deux clients natifs

Statut : tranche réseau/cycle de vie vérifiée le 22 novembre 2026 ; ticket et phase P1 encore partiels. La reprise pendant un cooldown et le crash réel d'une impulsion active restent à vérifier. Ce rapport complète P1-05.md, dont les anciennes indications générales de progression sont historiques.

## Exécution et périmètre

Commande exécutée depuis le dépôt :

```powershell
.\scripts\test-adventure-preparation.ps1 -Run mi2 -Mode Impulse -PreparationRun ap4
```

Copie isolée de la préparation ap4 arrêtée ; les deux inventaires proviennent de vraies fabrications déjà vérifiées. Console de fixture : +1232 XP par joueur, niveau 15 ; configuration TOML réellement chargée : régénération d'énergie 0 ; keepInventory vrai. Plateforme jetable, vache persistante à IA active avec vitesse de marche nulle pour isoler son déplacement par la Force. Aucun changement du code de production pour cet essai.

Serveur PID 26376, AdventureOne 10348, AdventureTwo 8528, trois processus distincts et sorties 0, aucun arrêt forcé. Fin du lanceur 22:09:22 UTC. Les intentions passent par les commandes de production envoyées par les connexions natives, la marche d'approche par les entrées vanilla, le respawn par le bouton vanilla. Le pilote de test serveur prépare l'arène et les interruptions, sans remplacer le contrôleur de Force. Les pilotes résident exclusivement dans gameTest.

## Résultats relus indépendamment

`scripts/check-mob-impulse-network.py mi2` : PASS. Le checker relit les preuves NBT serveur et clients, les playerdata, les régions natives d'entités, les journaux et le résultat du lanceur.

- Refus NoAI, résistance maximale, protection et mur : énergie des deux joueurs toujours 100, cible immobile, aucun événement d'impulsion. Le pair tente une attraction pendant l'immunité de la cible : retour cooldown, énergie 100 et aucun engagement supplémentaire. Protection testée via un vrai hook du contrat ; aucune compatibilité avec un mod tiers revendiquée.
- Six engagements réels sur la même vache UUID `036f6c6e-030b-489b-9304-5a3bc8b13f42`. Masse mesurée 1,744615298 ; coût exact 8 + 2×masse pour pousser et 10 + 2×masse pour attirer. Cooldown acteur et cible 40 ticks ; aucun XP ou alignement gagné par ces essais.
- Poussée : z 254,5 → 256,262947747293. Attraction : z 256,262947747293 → 255,08764924909764. La trajectoire par tick contient un vrai mouvement et une brève élévation de 0,035164 bloc suivie du retour au sol ; aucune simple vitesse assignée présentée comme déplacement.
- Révocation : interruption à +3 ticks ; dimension : +2 ; mort : +2 ; déconnexion de socket : +3. Toutes précèdent la durée normale de 8 ticks, mettent active à faux et annulent la vitesse horizontale en conservant l'immunité. Les trois premières sont déclenchées après constat de l'état actif à +2 ticks. Le client confirme l'arrivée dans son ClientLevel Nether, puis le retour ; la vraie mort crée un nouveau ServerPlayer après le bouton Respawn. Énergie remise à 100 par la règle de mort, puis le dernier push coûte de nouveau 11,489230596.
- Six observations concordantes du premier client et sept du pair, tolérance 0,2 bloc, même UUID et santé 10. Le pair reste connecté pour voir l'arrêt après déconnexion du détenteur. La sauvegarde contient exactement cette vache, inactive, sans NoAI, au même emplacement final. Inventaires, composants et champs de progression conservés ; énergie finale détenteur 88,5107694038978, pair 100.

Captures locales : `run-adventure-client-{one,two}-mi2/screenshots/impulse-*.png`. Inspection visuelle réelle de push et death du premier client : vache, pair, inventaire et HUD visibles. **impulse-death.png est la vue après respawn** : l'observation finale a remplacé la capture du DeathScreen qui utilisait le même nom. Le passage par le vrai bouton est attesté par le pilote et sa preuve, mais aucune image distincte du DeathScreen n'est revendiquée. Ces vues statiques ne prouvent pas à elles seules l'animation ; les positions des deux ClientLevel et les trajectoires serveur la complètent.

## Incidents et limites

mi1 n'est pas une réussite : le mapping du lanceur serveur utilisait par erreur adventureBlock pour Impulse, alors que les clients attendaient adventureImpulse. Le client détenu par ce run a été arrêté après vérification de ses arguments ; serveur et pair arrêtés ensuite, résultat observed false conservé. Mapping corrigé avant mi2. Aucun test ni assertion de production désactivé.

Le premier passage du nouveau checker supposait à tort y constant et un ordre fixe des joueurs dans la frame de mort. Relecture des échantillons et du code ForceImpulseMath : élévation de production attendue, ordre non contractuel ; vérification corrigée pour borner l'élévation et identifier le joueur par UUID. Aucune donnée de test modifiée.

Restent : nouveau JVM sur le cooldown sauvegardé, crash effectif pendant une impulsion active et reprise sans contrôle orphelin, déchargement natif de cible/chunk pendant contrôle et régression transverse finale avant clôture P1. Le contrôle NBT final après arrêt propre ne prouve pas ces parcours. P1 global et V1.0 ne sont pas déclarés terminés.

Régression du 23 novembre : `scripts/gradle.ps1 build runGameTestServer --console=plain`, BUILD SUCCESSFUL en 46 s, 102 GameTests requis réussis. Rapports JUnit : 131 tests, zéro échec/erreur/ignoré (tâche à jour, sans prétendre une nouvelle exécution des JUnit inchangés). Log `.tools/impulse-mi2-regression.log`, fin 17:20:56. Parseur des deux lanceurs PowerShell PASS. JAR SHA256 `64A65E2DF3BB1D37FADA65F010F9681ADA426B58CAC451F30B4C2731342ED628` inchangé ; relecture `scripts/check-packaged-server.py jarmr1` PASS, aucun harnais distribué. Les deux boots installés de jarmr1 restent les preuves antérieures du même JAR, pas de nouveaux démarrages prétendus.
