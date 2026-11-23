# Développement V1.0 par lots et campagnes groupées

Consigne du concepteur du23novembre2026 : regrouper les développements, puis effectuer de grosses campagnes de tests et corriger les défauts. Les exigences de V1.0 et décisions00 restent entières. L'implémentation n'est pas une validation ; les statuts de phase restent dans03.

## Lots de développement

1. Compléments du socle P2–P4 : atelier sabre complet (catalogue, modification, démontage, entretien, plans, aperçu/statistiques), progression du cristal et intégration des interfaces ; conserver les systèmes déjà jouables et la correction de sauvegarde. Rassembler les frontières de persistance restantes dans la campagne de sécurité.
2. Approfondissement P5 : compétences et pouvoirs communs/lumineux/obscurs, groupes de vrais blocs et projection ; variantes/postures/combat/rituels des sabres ; catégories, modules, passagers, stockage et dégâts des speeders. Paramètres serveur et sauvegardes versionnés, interfaces client projetées.
3. Contenu P6, planètes une à une dans l'ordre du cahier des charges : Tatooine, Endor, Mustafar, Dagobah, Jedha. Pour chacune, terrain/biomes et structures, ressource/cristal/composant, deux boss avec comportements propres, coordonnées/voyage et indices locaux. Ne pas présenter douze entrées de catalogue ou des variantes de couleur comme douze combats achevés.
4. Finition P7 : design approfondi des items/véhicules/effets originaux, UI et accessibilité, FR/EN, équilibre et budgets de charge, guide joueur/installateur, changelog/crédits/JAR. Fenêtres de développement sur écran3.

## Campagnes après intégration

- Compilation complète, tests unitaires et GameTests de tous les modules ; corriger les régressions, puis relancer le groupe concerné.
- JAR installé : serveur dédié, deux démarrages/sauvegardes, absence de classes client et harnais de test dans la distribution.
- Parcours solo et deux clients : progression, atelier/cristaux/combat, conception/conduite/réparation de speeders, six voyages/retours, douze boss et holocrons. Recouper projections, sauvegardes et UUID/composants.
- Sécurité/persistance : paquets invalides/rejoués, permissions/claims, inventaires pleins, menus, mort selon gamerule, dimension/chunk, hoppers et objets rares ; déconnexion, redémarrage, kills et refus réels de fichiers aux frontières pertinentes.
- Charge/profiling, variété des résolutions et réglages graphiques ; noter les vérifications humaines ou impossibles à automatiser.

Les processus lancés avant cette consigne sont menés à leur terme et leurs preuves conservées. Le défaut sio1 de publication du curseur après sortie ne sera ni effacé ni présenté comme une réussite. Aucun passage de phase ou version1.0 avant preuves couvrant les fonctionnalités correspondantes.

## État courant

Développement groupé commencé ; aucune phase supplémentaire validée, campagnes globales à venir.

- Atelier : actions serveur de modification avec restitution, démontage avec même cristal, entretien/condition, plans réutilisables, statistiques projetées et aperçu tournant codés. Recette/FR/EN et réduction des animations intégrées. Catalogue actuellement limité aux pièces déjà enregistrées ; élargir types/variantes et rendu modulaire.
- Compétences/Force :17 définitions de pouvoirs, arbre à trois branches, apprentissages versionnés et sauvegardés avec les points, suspension selon alignement sans oubli ; commandes et touchesI/K/O. Contrôleurs serveur de mobilité, perception, protection/soin/soutien, contrôles et canalisations drain/éclairs codés. Les groupes/rotation/projection de vrais blocs sont désormais codés dans un journal distinct ; maîtrise individuelle des17 pouvoirs apprise et projetée, défis serveur/plafonds persistants codés. Restent primitives historiques, affinage des contrôles et sécurité en charge.
- Compilations d'intégration Java ponctuelles réussies ; aucune campagne exécutée pour ces nouveaux lots. Ne pas les présenter comme fonctionnels et vérifiés sur cette base.
- Speeders : dix pièces, effets serveur, grandes grilles, classes calculées, invitations et sièges passagers, HUD/recettes et première refonte mécanique des dix modèles codés. Stockage embarqué et finition restent ouverts. Types/rituels/combat de sabres, cinq autres planètes et dix autres boss, contenu local, suite du design et finition restent à implémenter. L'état précis et les limites sont consignés dans03.

## Matrice assemblages à inclure dans la campagne

Paliers15/35/60/85/100 :1/3/8/16/32 légers, masse pierre réduisant le nombre, composante trop grande refusée entière. Vérifier deux acteurs, même source, limite de dimension partagée avec bloc simple, claims source/trajet/arrivée, chunks non chargés et bordure. Déplacer un groupe non symétrique, quatre rotations et comparaison exacte des states ; refus du balayage obstrué et des endpoints recouvrants. Projeter sur mobs autorisés/immunisés/protégés et terrain, contrôler dégâts attribués/plafond/non-rejeu. Déconnexion/mort/dimension/arrêt/unload, puis kills aux phases PREPARED/HELD/DESTINATION_STAGED/COMMITTED et écritures partielles entre chunks ; compter les blocs natifs et les projections après chaque nouvelle JVM. Refus IO et schéma futur : journal conservé, pas de restitution partielle silencieuse. Ces scénarios sont prévus, pas exécutés.

## Matrice speeders étendus à inclure dans la campagne

Anciens plans et nouvelles grilles jusqu'à128 modules ; redimensionnement refusé sans perte, inconnus/futurs readonly, tailles/coordonnées/paquets hors limites, matériaux manquants/inventaire plein et reprises d'assemblage sur grandes enveloppes. Comparer masse, propulsion vectorielle, stabilisateurs, casse/entretien et blindage plafonné, impact réel des catégories. Monter à deux puis quatre joueurs, propriétaire en premier/dernier, invité sans pilotage, non-invité et liste pleine, départ pilote/guest, rejoin et restart, sièges détruits, murs/têtes/chunks/claims. Inviter/révoquer puis refus réels de sauvegarde et relecture native des UUID sans rollback aveugle. Vérifier les corps/positions, HUD et dix modèles proches/en main/au sol, peintures et réductions de grille aux petites résolutions. Ces scénarios sont prévus, pas exécutés.

## Matrice maîtrise à inclure dans la campagne

Seuils10/30/75 et économie5%/rang sans dégâts supplémentaires ; valeurs configurées et chaînes drain/éclairs. Zéro gain sur cible invalide/protégée, soin plein, buff lancé à vide, mouvement nul/obstrué, téléportation et dimension. Défi mobilité : menace réelle, déplacement mesuré, saut avec montée puis atterrissage ; défense : nouvelle blessure hostile sous résistance, refus des dégâts contournant celle-ci. Contrôles, projection, perception nouvelle, soin et absorption significatifs. Même cible partagée entre pouvoirs, plafond12/10minutes, intervalle10secondes, limites au redémarrage et à la mort, horloge antérieure conservatrice, refus IO réel et nouvel agrégat joueur, futur/malformé conservé/inactif. Arbre : rang/compteur/seuil maximum/coût projetés, suspension d'alignement sans perte. Profilage du coût de sauvegarde avec plusieurs acteurs. Scénarios prévus, pas exécutés.
