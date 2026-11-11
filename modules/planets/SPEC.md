# MODULE PLANETS — Dimensions, voyages, cristaux et exploration

**Mission :** construire six petites planètes-dimensions persistantes, distinctes, explorables, avec ressources et boss locaux, sans retirer la liberté de construction Minecraft. **Priorités : P4, P6**.

## PLAN-01 — Monde principal et voyages
- Monde principal vanilla (Overworld) : base, survie, progression et artisanat.
- Le joueur construit un **ordinateur/terminal de navigation galactique** et un point de départ sécurisé, puis débloque des destinations en découvrant des coordonnées, holocrons ou objets.
- Interface de carte : planètes découvertes, ressources dominantes, difficulté conseillée, boss vaincus ; ne pas afficher les coordonnées exactes des cristaux rares.
- Voyage avec coût énergétique et **validation serveur** : terminal existant et chargé, dimension débloquée, point d'arrivée sûr, pas en combat si règle activée.
- Destination : point d'atterrissage/génération stable ; retour vers terminal/base enregistrée ou point de secours protégé.
- Si un point d'atterrissage est occupé/dangereux, rechercher à courte distance un emplacement sûr ou utiliser une plateforme de secours réservée ; jamais spawn dans la lave.
- Éviter doubles demandes/duplicaton d'inventaire via transaction ; gérer déconnexion en transition et réapparition à la mort.

## PLAN-02 — Six dimensions initiales
| ID / planète | Biomes et ambiance | Cristal rare principal | Pièce speeder rare | Boss Jedi / Sith |
|---|---|---|---|---|
| `ilum` | glaces, tempêtes, cavernes cristallines | bleu, parade | propulseur cryogénique | gardien des cristaux / seigneur du givre |
| `tatooine` | dunes, canyons, villages/épaves | jaune, endurance | moteur de course | chevalier errant / exilé du désert |
| `endor` | forêts géantes, brume, ruines | vert, régénération Force | châssis forestier | gardienne de la nature / assassin nocturne |
| `mustafar` | volcans, rivières de lave, forteresses | orange, puissance offensive | moteur haute puissance | protecteur des ruines / seigneur des flammes |
| `dagobah` | marais, grottes, végétation dense | cyan, télékinésie | stabilisateur de lévitation | ermite / maître des illusions |
| `jedha` | déserts sacrés, falaises, sanctuaires | violet, pouvoirs avancés | circuit énergétique avancé | gardien du temple / seigneur des reliques |

Une « petite planète » signifie **zones de découverte et contenu denses**, pas la promesse de sphères physiques traversables ; les dimensions Minecraft restent techniquement des mondes plats/infinis ou avec limites configurables. Proposer taille ou frontière de monde par serveur si besoin.

## PLAN-03 — Génération et structures
- Définir clés de dimensions/biomes, fonctionnalités de génération et structures comme données/registries adaptées à NeoForge 1.21.1.
- Au minimum par planète : points d'arrivée, formations naturelles, cavernes/mine rare, **temple Jedi**, **sanctuaire Sith**, ruines secondaires et lieux d'expédition.
- Variété de surfaces, palette de blocs, faune/monstres ; difficulté locale adaptée (éviter progression 100 % linéaire).
- La génération d'une structure doit vérifier contraintes de terrain ; pas de réécriture des chunks déjà bâtis.
- Les arènes de boss sont des zones protégées durant le combat ; hors arène, la construction reste permise selon règles serveur.
- Les chunks de dimension se sauvegardent normalement ; ne pas régénérer une dimension déjà explorée à chaque connexion.

## PLAN-04 — Cristaux rares
- Un type principal de cristal par planète, basé sur l'élément rare défini ; pas de veines abondantes réparties dans tous les souterrains.
- Méthodes : cavernes de cristaux, formations scellées, anciennes mines ou chambres de temple ; l'obtention demande exploration et parfois télékinésie/énigmes.
- Pureté et stabilité individuelles tirées d'une plage bornée au moment de la collecte par serveur ; conserver provenance planétaire.
- La perception de Force haut niveau peut signaler une zone générale, **sans pointer exactement le bloc ni garantir un cristal**.
- En multijoueur, renouveler la ressource **sans détruire constructions/chunks** : expéditions/événements de gisement, coffre spécialisé en structure protégé, ou systèmes d'instances explicitement limités. Ne pas remplacer arbitrairement des blocs de joueurs.
- Définir plafond par période d'une structure et règles de partage, éviter duplication de cristaux d'une même UUID.

## PLAN-05 — Pièces technologiques planétaires
- Chaque planète possède au moins une pièce de speeder unique, obtenue via exploration/boss/butin rare et reproductible après investissement.
- Recettes avancées nécessitent des matériaux vanilla + composants planétaires : rendre chaque expédition utile sans rendre un véhicule basique impossible à fabriquer.
- Favoriser des **avantages de style** plutôt que l'évolution permanente vers « le dernier monde est forcément le meilleur ».

## PLAN-06 — Environnement et survie
- Ilum : visibilité et froid simulés avec effets modérés (pas de mort arbitraire sans équipement possible).
- Tatooine : poussière, chaleur/soleil, villages à explorer.
- Endor : forêt dense, obstacles, chemins de speeder ; verticalité.
- Mustafar : relief dangereux, lave, abris ; protections nécessaires pour zones extrêmes.
- Dagobah : boue, visibilité réduite, faune et végétation.
- Jedha : ruines sacrées, puzzles de temples et ressources historiques.
- Les effets environnementaux doivent être configurables et ne pas détruire la jouabilité vanilla.

## PLAN-07 — Statuts persistants
- Découverte/déblocage par joueur et coordonnées enregistrées ; arène par monde ; timers et rares ressources par structure.
- Exploitation minière suit les règles de blocs vanilla ; la Force respecte les permissions du module `core`.
- Event `PlanetDiscovered`/`PlanetTravelCompleted` consommé par quêtes/progression, sans gain de niveau obligatoire.
- Les voyages des speeders ne sont **pas disponibles dans le MVP** si véhicule en monde : il reste dans la dimension ; envisager une solution de transport sécurisée dans un ticket futur.

## PLAN-08 — Ordre d'implémentation
- P4 : **Ilum uniquement** pour prouver la boucle cristal → boss → retour.
- P6 : Tatooine, Endor, Mustafar, Dagobah, Jedha, une à une ; chaque planète donne ses deux boss et ressources avant la suivante.
- Mesurer temps de génération, chargement client/serveur et absence de crash si un joueur quitte pendant transfert.

## Critères d'acceptation
- [ ] Le voyage aller-retour fonctionne après redémarrage sans perte d'items/données.
- [ ] Ilum génère cavernes rares, cristal bleu, sanctuaire Jedi et Sith et point d'arrivée sûr.
- [ ] Une dimension déjà modifiée par le joueur n'est jamais régénérée arbitrairement.
- [ ] Une structure/loot ne peut pas être dupliqué par changement de chunk ou rechargement.
- [ ] Les six planètes ont chacune cristal, composant, biome(s) et **deux** boss avant V1.
