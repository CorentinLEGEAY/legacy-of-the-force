# MODULE SPEEDER — Constructeur de véhicules modulaires

**Mission :** proposer une ingénierie accessible : le joueur construit réellement forme, taille, couleur, composants et capacités de ses speeders. **Priorités : P3, P5**.

## SPEED-01 — Garage et interface de conception
- Bloc « atelier de speeders » donnant accès à une **grille 3D** ; taille max selon rang de l'atelier ou schéma de véhicule.
- Trois modes : **placer / retirer des modules**, **peindre/décorer**, **simuler et fabriquer**.
- Les modules sont des pièces 3D de jeu, sur grille abstraite (ne pas confondre automatiquement avec un bloc Minecraft plein).
- Aperçu 3D tournant avec placement, symétrie optionnelle, grille, points d'attache, flèches de propulsion, centre de masse, stats.
- Valider **les matériaux réels** dans l'inventaire avant fabrication, puis les consommer une seule fois.
- Un plan est un schéma réutilisable et partageable : **aucun matériau inclus**.

## SPEED-02 — Catégories de modules
| Famille | Fonctions | Paramètres |
|---|---|---|
| Châssis | Structure reliée, classe de taille | masse, résistance, connecteurs |
| Moteur | Puissance motrice | poussée, consommation, chaleur |
| Propulseur | Direction/accélération/stabilité | position, poussée, axe |
| Batterie | Énergie disponible | capacité, poids, risque de panne |
| Stabilisateur | Contrôle de lévitation/virages | stabilité, consommation |
| Poste de pilotage | Place pilote, commandes | orientation, accessibilité |
| Siège passager | Transport d'alliés/PNJ | place, protection |
| Carrosserie | Formes extérieures | masse, résistance, aspect |
| Blindage | Réduction dégâts/masse accrue | protection, poids |
| Coffre | Inventaire transportable | capacité, masse variable |
| Éclairage/décor | Personnalisation | couleur, coût, aspect |
| Armement | **Hors MVP** : module futur | restrictions et sécurité |
- Les types et variantes sont data-driven ; les pièces rares viennent des planètes.

## SPEED-03 — Validation d'un véhicule
Avant assemblage, vérifier **dans cet ordre** :
1. Plan parsable, version et **taille/nombre de modules bornés** ; pas d'IDs inconnus.
2. Toutes les pièces à l'intérieur de la grille, sans chevauchement non autorisé.
3. Au moins un châssis, un moteur, une batterie et un siège pilote.
4. Tous les modules attachés au châssis via graphe de connecteurs compatibles (parcours BFS/DFS) ; aucune pièce flottante.
5. Siège accessible, orientation de pilotage définie ; propulsion utile dans la direction sélectionnée.
6. Énergie et puissance suffisantes pour soulever la masse ; stabilité minimale et collision viable.
7. Atelier et inventaire valides, droits de fabrication et zone de spawn disponible.
8. **Transaction atomique** : consommer composants, créer **un seul** véhicule, sauvegarder UUID/état.
- Le joueur reçoit une liste précise des erreurs ; un plan invalide ne consomme rien.

## SPEED-04 — Taille et classes
- **Léger** : prototype ≤5×3×3 cellules, 1 place, vitesse élevée possible, faible blindage.
- **Polyvalent** : ≤9×5×4, 1–2 places, bon transport.
- **Lourd** : ≤12×7×5, 2–4 places, forte masse et stockage.
- Déterminer la catégorie par la masse, taille et disposition, pas seulement un bouton de sélection.
- Paramètres configurables (cellule abstraite d'atelier ; dimensions effectives de collision calculées à la fabrication).
- Limiter nombre de modules, capacité d'inventaire et dimensions même sur serveur public.

## SPEED-05 — Simulation de conduite
- Un speeder **lévite à ~1,5–3 blocs du sol** ; ce n'est ni un avion ni un vaisseau spatial.
- Le serveur calcule la position/vitesse, collisions et consommation ; interpolation client pour rendre le mouvement fluide.
- Entrées joueur : accélérer, freiner, direction, action spéciale, monter/descendre. Déconnexion du pilote = sécurité/ralentissement et stationnement.
- Calcul centralisé et déterministe approximatif :
  - `totalMass = sum(moduleMass) + cargoMass + passengerMass`
  - `thrust = sum(engineThrust) * modifiers * healthFactor`
  - `acceleration = clamp(thrust / totalMass, ...)`
  - `energyUse = base + powerDraw(thrust) + stabilizerDraw + shieldDraw`
  - `turnRate = f(geometry, stabilizers, massDistribution, speed)`
- Propulseurs asymétriques provoquent une maniabilité différente, **mais pas une simulation incompréhensible** ; messages d'atelier explicatifs.
- Collisions : enveloppe/convexes simplifiées, bloque terrain, collisions avec entités selon règles ; éviter la traversée des blocs à grande vitesse.

## SPEED-06 — Énergie, endurance et environnement
- Batterie a capacité, charge et coût du moteur ; carburant/énergie rechargeable avec ressources craftables (concept, recette dans content).
- Véhicule déchargé : ralentit et s'arrête/descend sans exploser instantanément.
- Tempêtes, sable, neige, lave proche : modificateurs **légers** et annoncés ; aucune planète ne doit rendre impossible l'utilisation des véhicules sans option d'amélioration.
- Déplacements entre dimensions : véhicule normalement **reste dans sa dimension** au MVP ; voyage en planète via terminal. Transport de véhicule via système futur ou mécanisme d'emballage sûr à spécifier séparément.

## SPEED-07 — Dégâts localisés et réparation
- Pièces ont durabilité et catégorie : moteurs abîmés diminuent vitesse ; propulseurs, stabilité ; batteries, autonomie ; blindages, protection ; siège/commandes, pilotage.
- Une pièce détruite peut se désactiver/détacher visuellement, mais ne doit pas ouvrir des inventaires fantômes.
- Réparation uniquement en garage ou via trousse avec coût de ressources ; Force peut **stabiliser** ou **freiner**, pas ressusciter une batterie détruite gratuitement.
- Si le véhicule est détruit : générer un état d'épave/recyclage sous règles de butin, protéger stockage/UUID, pas d'inventaire dupliqué.
- Les autres joueurs ne peuvent pas démonter ou peindre un véhicule sans autorisation.

## SPEED-08 — Peintures, carrosserie et partage
- Plusieurs zones de couleurs : primaire, secondaire, accent, éclairage ; motifs de décoration.
- Formes multiples de plaques, ailes, capots et décorations, combinables sous contraintes ; rendu indépendant des stats si purement cosmétique.
- Enregistrer des plans versionnés et leur nom, auteur et IDs de modules ; quantité maximale de plans sauvegardés par joueur configurable.
- Plan exporté partagé = données **non privilégiées**, validées intégralement au chargement ; jamais transférer véhicule vivant ni inventaire.
- Les plans peuvent évoluer au fil des versions via migrations.

## SPEED-09 — Interaction avec la Force
- Publier un adaptateur `ForceTargetAdapter` : masse actuelle (y compris stockage/passagers), état occupé/inoccupé, propriétaire, protections, résistance, actions autorisées.
- Stabilisation niveau 25 ; ralentissement niveau 45 ; soulever léger et inoccupé niveau 70 ; projection/détachement limité niveau 95 (avancé).
- Au MVP, uniquement **freinage/stabilisation**, avec cooldown et coût ; aucun kidnapping de véhicule d'autrui.
- Seul Speeder Engine décide des dommages et collisions de véhicule ; Force émet des sollicitations validées.

## SPEED-10 — Interfaces et états persistants
- Plan `SpeederBlueprint` (grille, liste modules, rotations, palettes), état instance `VehicleState` (UUID, modules actuels/HP, énergie, position, propriétaire, inventaire).
- Le plan n'a jamais accès à l'énergie restante ou aux items stockés d'une instance.
- UID/propriété des entités stables à travers chunks et redémarrages ; charger un module inconnu ne doit pas faire disparaître toute l'entité.
- HUD de véhicule : vitesse, énergie, intégrité, nombre de places, modules endommagés ; interface diagnostic garage.

## Critères d'acceptation
- [ ] Un plan valide avec 4 familles obligatoires fabrique un speeder pilotable.
- [ ] Un module détaché / chevauchant ou un plan trop grand sont refusés **sans perte de matériaux**.
- [ ] Ajouter un blindage change masse/résistance et la vitesse selon formule serveur.
- [ ] Deux joueurs voient le même véhicule ; un seul pilote actif ; sauvegarde intacte après redémarrage.
- [ ] La casse du moteur affecte conduite, réparation consomme ressources, stockage non dupliqué.
- [ ] Peinture et plans ont des variantes visibles ; Force interagit selon masse et permissions.
