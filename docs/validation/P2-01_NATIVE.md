# P2-01 — ateliers natifs, deux variantes et conservation

Tranche réseau du23 novembre2026 : sw2 et reprise sr1 réussis. P2-01 reste partiel pour le crash et les transferts ; aucune garantie de durabilité inter-fichiers déduite de l'essai ci-dessous.

## Parcours et fixtures

```powershell
.\scripts\test-adventure-preparation.ps1 -Run sw2 -Mode SaberWorkshop -PreparationRun ap4
.\scripts\test-adventure-preparation.ps1 -Run sr1 -Mode SaberRecover -PreparationRun sw2
```

ap4 avait déjà fabriqué une poignée et placé un atelier par joueur. sw2 copie ce monde arrêté, sans toucher à l'original. Les deux joueurs non OP fabriquent chacun un bouton de pierre puis les six pièces d'une seconde poignée, via les vrais menus/recettes vanilla et les matériaux restants. Aucun ajout d'objet ou d'XP dans cette tranche. Le premier choisit l'émetteur standard, le second fabrique puis sélectionne l'émetteur à griffes par le vrai bouton de l'atelier. keepInventory=true et respawn forcé au camp sont les seules règles de lifecycle de fixture.

Les deux écrans sont attendus puis rendus avant capture ; le checker exige le vrai fond du panneau. Choix et aperçu ne consomment rien. Deux pressions immédiates sur Assembler transmettent deux requêtes natives : un seul sabre créé et six pièces consommées, puis statut COOLDOWN. Après attente, une nouvelle pression retourne MISSING sans consommation ni troisième sabre. Le premier subit ensuite une vraie mort, clique Respawn sur le DeathScreen vanilla, retrouve les deux poignées et ferme normalement sa socket. Le pair reste jusqu'à la preuve serveur. Les captures death-screen et respawned ont des noms distincts.

## sw2 — vérification achevée

Serveur **12328**, clients **5760/26308** : trois sorties0, aucun arrêt forcé. `scripts/check-saber-workshop-network.py sw2` PASS :14 recettes natives recoupées avec les entrées serveur, les clics et inventaires clients et les définitions JSON ; deux assemblages payés, deux rejoués refusés et deux demandes sans pièces refusées. Les anciens composants restent strictement identiques. Quatre UUID distincts au total :

- `35b9d36d-7e3c-40d3-93cb-e6064873fdee`
- `b27e6162-8adb-40de-a00e-11dcf48b0d3a`
- `eccd7f57-8687-4ec8-b45f-9a03fa29ee54`
- `f10eb6d0-c2a5-40b6-8df9-346874dcdbe6`

Chaque nouveau sabre a les six composants choisis, schéma1 et type single, sans cristal inventé. Les playerdata, preuves de fin, clients et bilans matériau par matériau concordent. Aucun gain RPG, aucun sabre au sol. Vraie mort/respawn et logout du premier vérifiés, inventaire conservé. Images selected du second et assembled-replay du premier réellement inspectées : panneau français, compteurs, émetteur à griffes et aperçu correspondant, puis compteurs consommés/statut d'attente sur l'autre client. L'aperçu est encore statique ; sa rotation et l'atelier complet restent le périmètre d'approfondissement.

## sr1 — nouvelle JVM et nouvelles connexions

Serveur **11400**, clients **19768/2328**, trois sorties0 sans force, fin16:45:15UTC. Aucune nouvelle recette, attribution ou assemblage en reprise. Deux nouveaux écrans et deux refus MISSING sans mutation. Inventaires complets strictement identiques à sw2, composants/UUID inclus, profils RPG stables. `scripts/check-saber-workshop-network.py sw2 --recovery sr1` PASS : six PID distincts au total, bilans source/fichiers/clients et fonds des douze panneaux concordants.

Capture reloaded du premier inspectée : le vrai atelier est présent ; les notifications vanilla de connexion offline recouvrent la partie droite de l'écran. Cette vue ne certifie pas la lisibilité simultanée de tous les boutons ; les vues selected/assembled de sw2, sans ces superpositions, restent les preuves visuelles correspondantes. Aucun contournement de fenêtre système ou activation Windows utilisé.

## Incident conservé

sw1 échoue dans la fixture : ap4 n'avait plus de bouton de pierre, déjà consommé lors de son premier assemblage. Le NativeCraftingDriver refuse l'ingrédient manquant de control_basic ; serveur et deux clients quittent0, observed=false, aucune réussite complète déclarée. Correction du parcours par une vraie recette vanilla stone_button supplémentaire par joueur, jamais par attribution directe ou suppression d'assertion. sw2 contient donc14 recettes, dont12 pièces et2 intermédiaires, pas12 recettes prétendues.

## Limites restantes

L'assemblage courant modifie un unique inventaire synchroniquement, puis attend les sauvegardes vanilla ordinaires. Ce test de sauvegarde propre ne prouve pas un crash entre assemblage, drop et sauvegardes des joueurs/chunks, ni un échange entre joueurs avec panne. Le prochain travail doit mesurer ce risque avant de fermer P2-01. Les modèles/ateliers complets, modification/démontage, stats et plans restent les tickets d'approfondissement selon la roadmap ; cristaux/combat/lien conservent P2-02/03/04. V1.0 non terminée.

Régression après essais : `scripts/gradle.ps1 build runGameTestServer --console=plain`, BUILD SUCCESSFUL41s, **131JUnit exécutés, zéro échec/erreur/ignoré** (rapports réécrits17:45:56), **102GameTests requis verts17:46:18**. Log `.tools/saber-workshop-sw2-regression.log`. Checker sw2 --recovery sr1 relancé après renforcement de la comparaison des inventaires de chaque frame de reprise : PASS. Parseur des deux scripts PowerShell et diff PASS. JAR inchangé SHA256 `64A65E2DF3BB1D37FADA65F010F9681ADA426B58CAC451F30B4C2731342ED628` ; relecture `scripts/check-packaged-server.py jarmr1` PASS, aucun harnais distribué. Les boots installés de jarmr1 sont les preuves antérieures du même binaire, pas de nouveaux démarrages prétendus.
