# MODULE LIGHTSABER — Sabres modulaires, cristaux vivants, duel

**Mission :** permettre de concevoir et de fabriquer **son** sabre en choisissant de nombreuses pièces et finitions, puis de créer une relation durable avec un cristal rare. **Priorités : P2, P5**.

## LIGHT-01 — Établi de construction
- Bloc de monde « atelier de sabres » craftable, ouvrant un écran serveur/client ; l'atelier vérifie la présence/position du joueur et les matériaux.
- Trois vues : **catalogue** des composants possédés/découverts ; **aperçu 3D tournant** ; **statistiques et coût** calculés côté serveur.
- Le joueur peut enregistrer un plan (sans dupliquer l'objet), fabriquer, modifier, démonter ou entretenir son sabre selon recettes.
- Prévisualiser les composants sans les retirer de l'inventaire ; valider en une seule transaction lors de « Fabriquer ».
- Messages d'erreur : manque de pièces, incompatibilité, cristal absent, atelier trop loin, inventaire plein, plan inconnu.

## LIGHT-02 — Composants physiques (7 familles)
| Emplacement | Variations visuelles | Impact (borné) |
|---|---|---|
| Émetteur | Cylindrique, griffes, anneau, évasé, garde | stabilité / profil de lame |
| Col et corps | Court/long, rainuré, multi-sections | longueur/masse |
| Poignée | Texture, diamètre, matière, relief, gravure | maniabilité |
| Module de contrôle | Bouton, gâchette, levier, switch | activation/option d'usage |
| Circuit | Standard, efficace, haute puissance | dégâts/consommation |
| Cristal | Type, pureté, résonance, évolution | couleur/affinité/bonus |
| Pommeau | Plat, conique, équilibré, connecteur | équilibre / raccord |
- Chaque pièce définit un identifiant, un modèle 3D, une taille, une catégorie, des points de montage, tags de compatibilité et des modificateurs modestes.
- Couleurs/matériaux indépendants selon zones (noir/argent/or/cuivre/peinture, etc.) et gravures cosmétiques.
- Longueur de certaines pièces via variantes discrètes (S/M/L) plutôt qu'échelle arbitraire avec géométrie instable.
- Émetteur/poignée/pommeau doivent s'assembler sans clipping ; validation automatisée des limites et orientations.

## LIGHT-03 — Types de sabres
| Type | Condition structurelle | Profil de combat |
|---|---|---|
| Simple | Une chaîne complète + un cristal | Polyvalent ; disponible au début |
| Court | Pièces courtes compatibles | Rapide, portée courte |
| Courbe | Corps/poignée courbes compatibles | Duels, parades directionnelles |
| Double | Deux émetteurs et raccord compatible, alimentation suffisante | Portée/balayage, fatigue accrue |
| Garde | Émetteur à sorties latérales et circuit adapté | Protection, maniabilité réduite |
| Double wield | **Deux items sabre simples**, capacité de combat | Combos mais forte dépense endurance |
- Types avancés déblocables par plans et ressources, sans forcer un alignement particulier sauf rituels.
- Pas de sabres prédéfinis comme système principal : des *presets* ne sont que des plans fabriqués avec les mêmes pièces.

## LIGHT-04 — Statistiques et limites
- Dégâts, vitesse d'attaque, longueur de lame, parade, stabilité, chaleur/consommation, maniabilité.
- Calculez les stats comme **base du type + somme bornée** des composants + bonus du cristal, puis clamp global ; aucun modificateur exponentiel.
- Les cristaux de haut lien améliorent le style de jeu sans annuler les choix de poignée/circuit.
- Toutes les consommations liées à l'usage sont contrôlées côté serveur ; une panne de circuit provoque une désactivation temporaire, pas la destruction irréversible du cristal.
- Lame activable/désactivable ; activation sonore/visuelle, délai court ; aucun effet serveur basé uniquement sur une animation.

## LIGHT-05 — Cristaux kyber rares
| Planète | Type initial | Couleur | Affinité de gameplay |
|---|---|---|---|
| Ilum | Cristal d'Ilum | Bleu | Parades / précision |
| Tatooine | Cristal des dunes | Jaune | Endurance |
| Endor | Cristal sylvestre | Vert | Régénération énergie Force |
| Mustafar | Cristal volcanique | Orange | Puissance offensive / consommation accrue |
| Dagobah | Cristal des marais | Cyan | Télékinésie |
| Jedha | Cristal sacré | Violet | Maîtrise de techniques avancées |
- **Une famille principale par planète**, cristaux présents en occurrences très rares dans structures spécifiques (voir planètes) ; plus d'un cristal possible sur une planète si extensions futures, mais pas de banalisation.
- Les cristaux de couleur **rouge** s'obtiennent par un rituel de corruption volontaire d'un cristal existant ; **blanc** par purification d'un cristal corrompu. Conserver l'origine historique du cristal.
- Statistiques individuelles : `purity`, `stability`, `resonance`, `bondLevel` 0..4, `bondProgress`, `state` natural/corrupted/purified.
- Un item cristal a un UUID unique. Pas de disparition aléatoire du cristal rare en cas de durabilité ou de mort.

## LIGHT-06 — Living Kyber : lien évolutif
### Étapes
0. **Dormant** : trouvé, non lié ; inspectable.
1. **Éveillé** : fabriqué en sabre + rituel de liaison ; couleur/bonus de base.
2. **Harmonisé** : expériences de jeu diverses ; premier bonus tactique.
3. **Résonant** : choix d'une spécialisation compatible ; bonus plus marqué.
4. **Légendaire** : défi d'exploration + victoire majeure ; esthétique/bonus signature modérés.
### Sources de lien valides
- Parade réussie contre une créature dangereuse, combat à niveau pertinent, utilisation réelle d'un pouvoir à coût, découverte d'un sanctuaire, boss ; plafonds par session et contrôle d'abus.
- Ne pas accorder de progrès pour coups dans le vide, attaques sur cibles invulnérables, clics répétés en boucle ou kill de mobs farmés automatiquement.
- Chaque événement fournit une catégorie `bondActivity` pour apprendre si le porteur favorise parade, télékinésie, offensif, soutien.
- Les seuils sont data-driven ; ne pas faire dépendre l'évolution d'une seule activité, sinon un joueur sans une capacité se bloque.
### Corruption/purification
- Rituel rare avec matériaux de planète et conditions d'alignement, jamais changement automatique à ±50.
- Remplacer `state`/`visibleColor` de **la même instance**, pas créer une nouvelle instance du même cristal (anti-dupe).
- Préserver `bondLevel` ou en modifier modérément les effets suivant la cérémonie, sans effacer l'historique ; sauvegarde versionnée.

## LIGHT-07 — Combat au sabre
- Modes **défensif, équilibré, agressif** ; avantages et limites de consommation d'endurance, parades, dégâts.
- Parade active : fenêtre courte ; déviation de certains projectiles ; contre-attaque avec coût et cooldown.
- Esquive et combos : timings explicites ; animations alignées avec les collisions serveur, pas de hitboxes fantômes.
- Formes de combat avancées via holocrons, sans reproduire obligatoirement le lore à l'identique.
- Combinaisons Force+sabre : attraction puis coup, protection via bloc flottant, poussée à la fin d'un combo ; nécessitent que pouvoirs et sabre soient réellement disponibles.
- PvP : respect règles serveur, latence et invulnérabilité de spawn ; pas d'auto-ciblage injuste.
- Attaques sur mobs passifs et hostiles créditées au RPG uniquement si kill volontaire et valide.

## LIGHT-08 — Économie, maintenance et restitution
- Obtenir ses premières pièces via artisanat vanilla + matériaux spéciaux ; circuit avancé dépend de planètes.
- Démonter un sabre rend ses pièces selon un taux configurable ; restituer **le même cristal UUID** sans duplications.
- Si inventaire plein, refuser ou déposer sous contrôle serveur dans un emplacement sécurisé ; pas de perte silencieuse.
- Les plans sauvegardés contiennent références aux IDs de pièces, jamais des objets gratuits.
- La mort en vanilla suit les règles de perte d'inventaire du monde (`keepInventory`), pas une disparition spéciale du cristal.

## LIGHT-09 — Rendu et interface
- Lame colorée, longueur, épaisseur, éclat et instabilité selon cristal/émetteur ; rendu basse qualité possible.
- Aperçu 3D rotatif dans l'atelier, masque de compatibilité par slot, facture d'assemblage.
- Tooltip complet : type, pureté, résonance, lien, origines, état, informations sur le bonus.
- Sons originaux d'activation, de parade, de choc ; menus traduits FR/EN.

## Données / API
- `LightsaberAssembly` versionnée dans data component item ; `KyberState` versionné et identifié ; `PartDefinition` configurable.
- `validateAssembly(plan, inventory, station)` ; `assembleAtomically(...)` ; `getSaberStats(...)` ; `recordBondActivity(...)` ; `transformCrystal(...)`.
- Exposer au module combat/boss une interface `SaberCombatProfile`, jamais la liste mutable interne des items.

## Critères d'acceptation
- [ ] Changer l'émetteur **change réellement le modèle** et peut changer une stat autorisée.
- [ ] Deux sabres identiques avec cristaux différents ont des lames et bonus distincts.
- [ ] 7 emplacements validés ; types incompatibles refusés sans consommation.
- [ ] Cristal conservé après mort/reconnexion/redémarrage ; corruption ne duplique pas.
- [ ] Lien progressera avec activité significative, pas via exploit de clics.
- [ ] Parade/attaque calculées serveur, animation cohérente sur deux clients.
