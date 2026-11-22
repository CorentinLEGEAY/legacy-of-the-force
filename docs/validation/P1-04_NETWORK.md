# P1-04 — Deux vues natives, déplacement et restauration

22 novembre 2026. Le scénario réseau de cette tranche passe ; P1-04 reste partiel pour les autres gates, P1 global et V1.0 ne sont pas terminés.

## Exécution observée

fb2 : serveur dédié24992, clients AdventureOne25936 et AdventureTwo25984, deux sockets loopback distinctes. Trois sorties0 sans arrêt forcé. Dossier source ap4 conservé ; copie complète d’un monde arrêté, verrou session.lock vérifié avant copie. Le sourceSet gameTest porte tous les pilotes et observateurs, absents du JAR de production.

Fixture déclarée : nouvelle plateforme vide en hauteur, cinq cibles de refus et une terre à déplacer. Les inventaires/identités des deux personnages proviennent de la préparation ap4. La console admin native accorde1232XP à chacun, niveau15, pour tester le seuil des blocs légers ; ce n’est pas un parcours de progression légitime prétendu. Les deux sockets restent non-OP. La surcharge TOML du monde met la régénération à0 pour mesurer la dépense, sans toucher aux coûts/portées/cadences de Force. Pas de mort dans ce scénario ; aucune règle d’inventaire n’est utilisée comme preuve de décès.

Les actions passent par les commandes publiques de Force, celles qu’émettent les touches du mod, avec orientation normale du vrai client et mise à jour native de sa visée. Le pilote ne touche ni au coordinateur ni aux positions de bloc. Les deux ClientLevel sont observés séparément : UUID, position, BlockRenderState rendu, états source/destination, captures framebuffer. Les messages de test ne servent qu’à coordonner et sont exclus du produit distribué.

## Résultats vérifiés indépendamment

- Coffre et bedrock : invalid_target ; pierre au niveau15 : level_required ; terre refusée par le vrai hook de protection : protected_area ; cible au-delà de6blocs : no_target. Aucune énergie dépensée et aucun journal/projection sur ces cinq refus.
- Saisie de la terre réelle : un journal HELD, une ancre source et une projection possèdent le même UUID. Le pair voit cette projection puis tente de saisir sa source : target_locked, énergie100 inchangée, aucune seconde opération.
- Visée vers destination : la même projection se déplace de plus d’un bloc ; les deux vues convergent vers la position serveur à0.15bloc près. Le BlockRenderState client doit contenir la terre effectivement rendue.
- Dépôt : source air, destination terre, aucun journal ni display. Les deux clients voient cet état.
- Nouvelle saisie, puis révocation du hook de protection de la source pendant maintien : le coordinateur restaure la terre et retire projection/journal. Les deux vues constatent la restauration. Ce hook de test exerce le port réel d’API ; il ne prouve pas la compatibilité d’un mod tiers de claims.
- Troisième saisie avec UUID distinct, attestée par les deux vues ; le vrai client détenteur écrit son préfixe puis quitte normalement. Le pair demeure connecté et constate la restitution après PlayerLoggedOutEvent, sans projection orpheline.
- Les chunks Minecraft sauvegardés contiennent exactement la terre transférée à destination et l’air à sa source. Les blocs de refus restent intacts. Aucun item de terre ni BlockDisplay dans les régions d’entités finales, aucun bloc obtenu dans les inventaires, aucun journal restant. Tous composants/inventaires identiques aux avant-images ; XP/alignement/révision admin de fixture conservés. Seul le détenteur paie la Force, le pair reste à100.

Treize états serveur, cinq vues du détenteur et six du pair sont rapprochés par check-block-network.py : mêmes UUID/positions/journaux/ancres, natives playerdata, palettes de chunks et régions d’entités. La durée des étapes est inférieure au délai de despawn d’items ; les inventaires conservés et l’absence d’items de terre empêchent de certifier par erreur un bloc plus un drop récupéré. Captures held du détenteur et moved du pair inspectées : vrai cube de terre, déplacement et HUD niveau15 visibles. Les notifications vanilla de connexion recouvrent temporairement une partie du HUD ; aucune qualité artistique complète n’est revendiquée.

## Reproduire

```powershell
# Première ligne seulement si aucune préparation native arrêtée n’est disponible.
./scripts/test-adventure-preparation.ps1 -Run prepneuf -Mode Prep
./scripts/test-adventure-preparation.ps1 -Run blocneuf -Mode Block -PreparationRun prepneuf
python scripts/check-block-network.py blocneuf --source prepneuf
```

Identifiants neufs, aucun Gradle parallèle. Python3.11+ et Pillow de scripts/validation-requirements.txt pour le checker. Preuves locales ignorées : .tools/adventure-block-fb2-result.json/logs ; run-adventure-fb2/world/validation/adventure-block-server.nbt, playerdata et régions ; run-adventure-client-{one,two}-fb2/validation/adventure-block-client.nbt et screenshots/force-block-<phase>.png. Le contrôleur compare les données réelles et ne valide pas un simple marqueur de succès.

fb1 a échoué à compiler le pilote avant lancement : getBlockState de BlockDisplay est privé en1.21.1. Source épinglée consultée, utilisation du NBT natif côté serveur et de blockRenderState().blockState() côté client, avec attente de l’état rendu. Aucun échec masqué, fb1 conservé ; aucun code de production modifié pour ce test.

## Régression et suite

build runGameTestServer : BUILD SUCCESSFUL47s,131JUnit sans échec/erreur/skip,102GT requis verts22:39:54. Les trois processus du scénario sont terminés normalement ; seul daemon Gradle22224 reste. JAR64A65E2DF3BB1D37FADA65F010F9681ADA426B58CAC451F30B4C2731342ED628 inchangé ; check-packaged-server jarmr1 vérifie la même distribution et l’exclusion des nouveaux pilotes.

Cette preuve ferme la lacune des deux interfaces stables et de la restauration réseau mesurée de cette tranche. Elle ne prétend pas tester une coupure de processus ni la totalité des stades transactionnels, ni un vrai mod de claims. Les anciens essais solo/mort/dimension/unload restent détaillés dans P1-04.md, la reprise historique HELD y est distinguée des durcissements ultérieurs. Prochaine gate : crash HELD du code actuel et reprise du même monde avec le scénario natif, avant consolidation du ticket ; ensuite P1-05. Profiling chargé et compatibilité tiers demeurent P7.
