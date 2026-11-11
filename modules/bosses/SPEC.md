# MODULE BOSSES — 12 Jedi/Sith, IA, arènes et récompenses

**Mission :** créer douze affrontements différents, un Jedi et un Sith pour chaque planète, avec combats au sabre, pouvoirs de Force, arènes interactives et conséquences RPG. **Priorités : P4, P5, P6**.

## BOSS-01 — Règles fondamentales
- Six planètes × **un boss Jedi + un boss Sith = 12 boss** en V1.
- **Victoire contre Sith = +25 lumineux ; contre Jedi = -25 obscur** (récompense éligible seulement, via RPG).
- Les boss donnent beaucoup d'XP (proposition 800–3000), plans rares, matériaux ou holocrons ; aucune récompense ne provient d'une simple animation de mort client.
- Un boss Jedi peut être **neutre** avant provocation/interaction ; un Sith peut proposer un dialogue dangereux ; éliminer ces PNJ affecte réellement l'alignement.
- Réapparition sur cooldown par arène et récompenses sur cooldown **par joueur et par boss** ; éviter ±25 répétés indéfiniment.
- L'arène reste protégée pendant l'affrontement et peut inclure des éléments destructibles/temporaires spécifiques (pas les constructions de joueurs).

## BOSS-02 — Patron d'intelligence artificielle
Machine à états : `DORMANT → DIALOGUE/AGGRO → PHASE_1 → PHASE_2 → PHASE_3 → DEFEATED/RESET`.
- `DORMANT` : garde une zone sans attaquer automatiquement si conception Jedi.
- `PHASE_1` : duel au sabre, approche, parade et esquives ; enseigner pattern de combat.
- `PHASE_2` : introduit Force/pouvoir de planète à environ 70 % de PV.
- `PHASE_3` : combo signature et terrain dynamique à environ 30 % PV ; lisible, interruptible selon design.
- Chaque action a `windUp`, animation/télégraphe, fenêtre d'esquive, dégâts, portée, coût/cooldown, résistance/contre.
- Ne jamais donner au boss accès à un pouvoir infini ou à une prise télékinétique sur le joueur sans possibilité de réponse.

## BOSS-03 — Résistance à la Force
- Un boss peut **bloquer, réduire ou contrer** les poussées, attractions et soulèvements en fonction de sa posture et jauge de résistance.
- Une bonne parade/rupture de posture ou utilisation du décor peut ouvrir une courte fenêtre de vulnérabilité.
- Empêcher boucles de stun / projection infinie ; invulnérabilité temporaire au même contrôle après un échec.
- Dégâts de projectiles blocs **bornés** ; le boss peut renvoyer certains blocs dans sa phase avancée.

## BOSS-04 — Catalogue des 12 boss
| Planète | Boss Jedi (combat) | Boss Sith (combat) |
|---|---|---|
| **Ilum** | Gardien des Cristaux — défense, blocs de glace en protection, coup de grâce par parade | Seigneur du Givre — attraction, salves de pierres/glace, zone de ralentissement |
| **Tatooine** | Chevalier Errant — mobilité, contre-attaque, combat autour des dunes | Exilé du Désert — tempête de sable, poussées, attaques circulaires |
| **Endor** | Gardienne de la Nature — sauts, protection, immobilisation ponctuelle | Assassin Nocturne — dash, illusions courtes, attaque depuis couverture |
| **Mustafar** | Protecteur des Ruines — parades robustes, usage des plateformes, contrôle de terrain | Seigneur des Flammes — attaques agressives, projectiles enflammés, zones de lave évitables |
| **Dagobah** | Ermite de la Force — télékinésie contrôlée, contres, épreuves de perception | Maître des Illusions — clones illusoires, peur, projectile trompeur |
| **Jedha** | Gardien du Temple — duels techniques, poussée/onde, boucliers | Seigneur des Reliques — éclairs avancés, drain limité, phases de reliques |

Les noms sont **des noms de travail**. Chaque boss doit avoir : modèle original, ensemble de 4–6 attaques lisibles, une attaque signature, 3 phases, ligne de dialogue facultative, récompenses et arène dédiée.

## BOSS-05 — Exemples de phases détaillées (prototype Ilum)
### Ilum Jedi — Gardien des Cristaux
- **Phase 1** : garde une position, attaque légère 2 coups, feinte, parade visible ; n'agresse pas un joueur pacifique hors entrée d'arène/provocation.
- **Phase 2 (≤70 %)** : soulève 1–2 blocs de glace **de l'arène** pour bloquer une attaque ; le joueur peut les saisir s'il possède le niveau suffisant.
- **Phase 3 (≤30 %)** : séquence d'attaque lente mais puissante, suivie d'une courte vulnérabilité si parée ; offre une contre-stratégie sans équipement parfait.
### Ilum Sith — Seigneur du Givre
- **Phase 1** : offensive au sabre, saut latéral ; télégraphe avant charge.
- **Phase 2** : attire des pierres/glaces scriptées avec limite de projectiles actifs ; le joueur peut contrer par Force ou esquive.
- **Phase 3** : zone glacée, projection en chaîne limitée et puissante attaque unique nécessitant esquive/abri ; aucune destruction définitive des zones de joueurs.

## BOSS-06 — Construction des arènes
- Les arènes possèdent **leurs propres objets interactifs** : piliers, projectiles temporaires, mécanismes d'ouverture, socles cristallins.
- Terrain interactif : utiliser un état « temporaire détruit » avec restauration contrôlée **sans écraser des changements joueurs autorisés**.
- Réinitialisation après combat, échec, déconnexion ou cooldown ; objets d'arène ne doivent pas être exportables gratuitement.
- Couche `core.ProtectedWorldService` empêche d'utiliser la télékinésie pour voler un bloc indestructible de sanctuaire.
- Boss ne poursuivent pas un joueur à l'infini au-delà de leur zone ; retour et régénération s'il n'y a plus de joueurs éligibles.

## BOSS-07 — Attribution des récompenses
- À la mort validée du boss, générer `BossDefeated` **une fois** avec UUID d'événement.
- Éligibilité : joueur ayant contribué au combat (dégâts, soin/soutien, télékinésie défensive) dans un intervalle/zone donnés ; éviter « dernier coup seulement ».
- Récompenses par joueur éligible : XP personnelle, alignement +25/-25, progression de quête ; butin partagé ou individuel selon objet, choisi dans config.
- Récompense à nouveau seulement après cooldown de boss pour ce joueur ; ne jamais compter deux fois le même event ID ; régler la durée dans config.
- Le boss n'augmente pas le « niveau de Force du bon/mauvais côté » : il change **l'alignement** et ajoute de l'XP de Force générale.

## BOSS-08 — Variantes et difficulté
- Vie, dégâts et nombre de capacités configurés par planète ; échelle de difficulté selon nombre de joueurs en arène (caps stricts, éviter boss invincibles).
- Modes normal/difficile optionnels ; mécaniques identiques mais chiffres ajustés.
- Les mobs ordinaires de l'arène ne doivent pas permettre de cultiver de l'alignement artificiellement à grande vitesse.
- Certains boss ont résistances à des éléments, pas immunités totales à toutes les stratégies.

## BOSS-09 — État persisté et synchronisation
- Statut par arène : `idle`, `active`, `cooldown`, `resetting` ; ID de boss, timers, participants, loot déjà attribué.
- En cas de crash/déconnexion, l'arène retrouve un état stable et ne duplique pas un boss.
- Le client reçoit phase, animations et avertissements ; le serveur calcule ses attaques, trajectoires et dégâts.

## Critères d'acceptation
- [ ] 2 boss d'Ilum ont comportements **différents**, 3 phases et arène fonctionnelle.
- [ ] Les combats utilisent au moins un élément du décor, pouvant être contré avec une mécanique Force ou esquive.
- [ ] Un boss Jedi vaincu attribue -25, Sith +25, **une fois par récompense éligible**.
- [ ] Aucun boss n'immobilise/perma-stun le joueur ; pas de destruction persistante hors arène.
- [ ] Un boss ne se duplique pas après unload/reconnexion ; butin/XP non doublés.
- [ ] À V1, les 12 fiches de boss sont implémentées et testées.
