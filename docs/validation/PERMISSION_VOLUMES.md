# Volumes de permissions — correctif du 15 novembre 2026

Le test lave de P4-02 a révélé que BlockPos.betweenClosedStream réutilise une MutableBlockPos. Collecter toList sans copie puis parcourir la liste examine plusieurs fois le dernier emplacement. Recherche ciblée : trois anciennes occurrences supplémentaires, BlockMoveCoordinator.pathClear, MobForceController.pathPermissionsAllowed, adaptateur Force de SpeederEntity.canManipulate.

Mini-plan : ajouter un scénario par chemin où un seul emplacement non final est interdit, reproduire sur le code existant, remplacer uniquement le parcours et rejouer build/régression. Aucun changement d'équilibrage, coût, volume ou droits accordés.

Preuve avant correction : `.tools/partial-claims-before-fix.log`, 94 GameTests exécutés, exactement trois échecs19:44:40 :

- Bloc : GRAB/PLACE autorisés, MOVE_THROUGH refusé sur le premier emplacement source de la trajectoire vers la case voisine. L'ancien code déposait malgré le refus.
- Mob : seule une case haute du volume balayé de la vache est interdite ; son emplacement central reste autorisé. L'ancien code appliquait la poussée.
- Speeder : coin minimal du volume interdit, centre et dernier coin autorisés. L'ancien code stabilisait le véhicule.

Correction : itérateur examiné immédiatement, sans stocker les références mutables. Assertions conservées : bloc tenu sans publication puis retour de l'unique bloc à sa source lors de l'annulation, aucune énergie dépensée/aucune vitesse changée pour les impulsions refusées. Régression `test runGameTestServer build` : **100 JUnit et94 GameTests requis verts**, BUILD SUCCESSFUL19:45:33, `.tools/partial-claims-after-fix.log`. Aucun test désactivé. Recherche des autres parcours de production effectuée ; les boucles directes de conduite/fabrication étaient déjà immédiates.

JAR SHA256004F76C6814FB7F43FE0749F640C192C8F3ECA125C40E65BDBC64E325D9CD05D. Adaptateurs simulés : pas une validation d'un mod de claims tiers ni une validation complète P1/P3/P4.
