# MODULE CONTENT — Objets, blocs, recettes, minerais et loot

**Mission :** fournir le contenu matériel qui relie survie vanilla, Force, sabres, speeders et planètes. Les objets et recettes doivent soutenir les systèmes, non les remplacer. **Priorités : P0, P2, P3, P4, P6**.

## CONTENT-01 — Catégories d'objets
1. **Équipements de Force** : holocrons, amulettes modestes, outils d'étude (pas d'item obligatoire pour toute capacité).
2. **Sabres** : poignée, émetteur, col, circuit, bouton, pommeau, cristal, item sabre assemblé, outillage d'entretien.
3. **Speeders** : châssis, propulseurs, batteries, stabilisateurs, sièges, plaques, couleurs, plans.
4. **Exploration** : coordonnées galactiques, composants de terminal, cartes, artefacts de temples.
5. **Technologie** : lingots/allages, circuits, cellules énergétiques, mécanismes, unités de stockage.
6. **Décoration** : blocs de temple, panneaux, éclairages, revêtements, bannières de factions, support de sabre.

## CONTENT-02 — Blocs fonctionnels
| Bloc | Fonction et interaction |
|---|---|
| Établi de sabres | Ouvre atelier 3D ; tient session de fabrication serveur |
| Garage de speeders | Ouvre constructeur sur grille ; zone de sortie validée |
| Terminal galactique | Accès aux six planètes ; coût de voyage |
| Socle d'holocron | Lire/apprendre un enseignement ; contenu versionné |
| Support de cristal | Présenter/examiner pureté et couleur |
| Chargeur de véhicules | Convertir ressources d'énergie en charge de batterie |
| Porte/activateur Force | Énigme activée par une vraie interaction de Force |
| Blocs de ruines | Décoration/destruction autorisée selon zone |

## CONTENT-03 — Recettes de survie et progression
- Début jeu : alliages simples à base de fer, cuivre, redstone, quartz ; atelier sabre rudimentaire ; aucun matériau exotique requis pour la première progression Force.
- Premier sabre : composants de base craftables ; **cristal rare** nécessaire pour l'activer, trouvé en exploration planétaire ou voie d'introduction équilibrée explicitement documentée.
- Premier speeder : châssis léger, petit moteur, batterie, siège et peinture basique craftables sans matériau planétaire.
- Avancés : moteurs, circuits, stabilisateurs et cristaux nécessitent expéditions, boss et plans.
- Éviter boucle bloquante : pour voyager vers Ilum il faut un terminal accessible **avant d'avoir un sabre cristallisé** ; les combats sans sabre doivent être possibles.
- Tous les objets rares doivent être obtenables en solo ; aucun objet n'exige obligatoirement commerce entre joueurs.

## CONTENT-04 — Ressources planétaires
| Planète | Cristal principal | Ressource/composant |
|---|---|---|
| Ilum | Bleu | propulseur cryogénique |
| Tatooine | Jaune | moteur de course |
| Endor | Vert | châssis forestier |
| Mustafar | Orange | moteur haute puissance |
| Dagobah | Cyan | stabilisateur |
| Jedha | Violet | circuit avancé |
- Les cristaux proviennent de structures/cavernes rares selon `planets/SPEC.md` ; pas de recette vanilla pour en créer artificiellement.
- Les composants rares peuvent être fabriqués en combinant matériaux du lieu, plans et artisanat pour permettre de construire plusieurs véhicules.

## CONTENT-05 — Loot et progression
- Tables de loot séparées par structure, planète, rareté, boss ; distribution data-driven.
- Butin de boss majeur attribué une fois par événement éligible avec cooldown, sans duplication par rattrapage client.
- Les objets cosmétiques rares n'affectent pas nécessairement la puissance ; matériaux de craft restent utiles.
- Conserver les avantages des planètes *horizontaux* : vitesse, autonomie, contrôle, puissance/consommation, pas « tout supérieur ».

## CONTENT-06 — Modélisation et localisation
- Chaque ID possède un nom français/anglais et éventuellement description courte ; registre de ressources en namespace `legacyoftheforce`.
- Textures de terrain simples mais soignées ; pièces de sabre et speeders en modèles originaux ; audio original.
- Catégories de tags pour cristaux, pièces, métaux, blocs manipulables, blocs interdits.
- Compatibilité avec packs de données pour loot/recettes lorsque permis par Minecraft.

## CONTENT-07 — Intégration au gameplay Minecraft
- Récolte et craft vanilla demeurent utiles. Ne pas rendre le fer, redstone ou quartz obsolètes.
- Le minage par Force ne duplique pas un bloc ni sa loot table ; si un bloc est déplacé sans minage, aucune loot n'est générée.
- Transporter les ressources dans les speeders ajoute de la masse réelle ; les coffres de véhicule respectent propriété et sécurité.
- Un cristal inséré dans un sabre n'est plus utilisable simultanément comme item libre.

## Critères d'acceptation
- [ ] Chaque recette a un chemin d'obtention en solo, sans boucle de dépendances.
- [ ] Atelier sabre, garage et terminal galactique sont craftables et fonctionnels.
- [ ] Chacune des six planètes fournit son cristal et sa pièce unique.
- [ ] Aucune ressource ne se duplique via atelier, télékinésie ou loot de boss.
- [ ] Textes localisés et données accessibles/validées.
