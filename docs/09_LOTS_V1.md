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
- Compétences/Force :17 définitions de pouvoirs, arbre à trois branches, apprentissages versionnés et sauvegardés avec les points, suspension selon alignement sans oubli ; commandes et touchesI/K/O. Contrôleurs serveur de mobilité, perception, protection/soin/soutien, contrôles et canalisations drain/éclairs codés. Restent notamment maîtrise, groupes/rotation/projection de vrais blocs, affinage des contrôles et sécurité en charge.
- Compilations d'intégration Java ponctuelles réussies ; aucune campagne exécutée pour ces nouveaux lots. Ne pas les présenter comme fonctionnels et vérifiés sur cette base.
- Compléments speeders, types/rituels/combat de sabres, cinq autres planètes et dix autres boss, contenu local, design approfondi et finition restent à implémenter. L'état précis et les limites sont consignés dans03.
