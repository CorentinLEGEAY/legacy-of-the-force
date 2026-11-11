# 04 — Valeurs de gameplay initiales (configurables)

> Les colonnes « confirmées » ne doivent pas changer sans décision explicite. Les autres chiffres sont des **valeurs de prototype** à ajuster après tests ; les stocker dans des configs/données.

## Décisions confirmées
| Règle | Valeur |
|---|---:|
| Niveau de Force | 1 à 100 |
| Alignement min/max | -100 / +100 |
| Mob passif (mort volontaire) | -0,1 alignement |
| Mob agressif (mort volontaire) | +0,1 alignement |
| Boss Sith | +25 alignement |
| Boss Jedi | -25 alignement |
| Planètes de base | 6 |
| Boss par planète | 1 Jedi + 1 Sith |

Conserver alignement **en entier représentant des dixièmes** dans les données serveur : -1000…1000 ; pas de dérive flottante. Attribution au joueur responsable et compensation de l'alignement uniquement sur événement éligible.

## XP initiale (proposition)
| Événement | XP |
|---|---:|
| Mob passif | 1 |
| Mob agressif normal | 5 |
| Mob agressif élite | 40 |
| Mini-boss | 200 |
| Boss principal | 800–3 000 selon planète |

Formule d'essai de coût pour **passer du niveau L au niveau L+1** :
`xpNext(L) = round(40 + 4*L + 0.25*L*L)` pour L = 1..99. La progression doit se mesurer sur des sessions réelles. L'XP est cumulative mais le compteur du niveau peut être stocké séparément ; ne pas dépasser 100. Règle : l'XP de Force provient principalement des éliminations, pas d'un système de quêtes contournant le combat.

### Anti-farm
- Élimination par machine sans participation directe crédible : 0 XP et 0 alignement.
- Mob ordinaire spawné répétitivement dans la même zone : rendement réduit selon une fenêtre glissante configurable, sans rendre impossible l'entraînement normal.
- Boss : récompense complète après une victoire **éligible** ; les nouvelles récompenses sont verrouillées par un délai par boss et par joueur. Une victoire pendant ce délai ne donne ni ±25 ni butin majeur.
- La progression de l'alignement n'est pas augmentée par les quêtes répétables ou les mobs d'entraînement créés artificiellement.

## Force — proposition
- Énergie maximale initiale : 100 ; régénération : 2 points/s hors pouvoir actif ; améliorations par compétences.
- Une action requiert une puissance, un nombre de cibles, une portée, une énergie suffisante et parfois une affinité.
- Télékinésie : niveau <15 = objets légers seulement ; 15 = 1 bloc ; 35 = 3 blocs ; 60 = 8 ; 85 = 16 ; 100 = 32. C'est le **nombre maximal de blocs légers**, réduit par masse.
- Portée indicative : 4 / 6 / 10 / 15 / 20 / 24 blocs selon paliers.
- Masse indicative : laine/feuilles 0,5 ; terre/bois 1 ; pierre 2,5 ; métal 5 ; obsidienne 9.
- Coût saisie = base 4 + masse totale × 2 ; maintien = 0,8/s × masse totale ; lancer = 7 + 3 × masse totale ; plafonds configurables.
- Éclairs de Force : niveau requis 55, alignement recommandé ≤ -50, coût continu 8 énergie/s + cooldown 8 s, cible visible, maximum 1 cible au départ, chaines au niveau 80. Les effets et dégâts finaux restent à équilibrer.
- Les boss ont une résistance à la Force ; pas de lévitation ou stun infinis.

## Sabres — proposition
- Sept emplacements de composant (émetteur, col/corps, poignée, contrôle, circuit, cristal, pommeau).
- Une seule instance de cristal principale par sabre initial ; simple/double/garde/courbe nécessitent schémas compatibles.
- Lien cristal 0–4 ; progression par actions significatives et paliers, pas par simple compteur de clics.
- Bonus majeur d'un cristal évolué ≤ 15 % sur la statistique ciblée dans le prototype ; aucun bonus ne doit cumuler exponentiellement.
- Durabilité du cristal ? **Non** : éviter la disparition d'un objet rare ; le circuit/l'arme peut nécessiter maintenance.

## Speeders — proposition
- Hauteur de lévitation 1,5 à 3 blocs au-dessus d'un sol valide (pas de vol libre).
- Limites de plan de prototype : léger ≤ 5×3×3 cellules ; polyvalent ≤ 9×5×4 ; lourd ≤ 12×7×5 ; chaque cellule = unité de grille abstraite (pas nécessairement 1 bloc Minecraft).
- Un moteur + une batterie + un châssis connecté + un siège pilote requis.
- Vitesse, rayon de braquage, accélération, autonomie, blindage : calculés sur serveur ; formules centralisées.
- Déplacement Force possible uniquement selon masse, occupation du véhicule, résistance et niveau ; limiter au freinage/stabilisation au MVP.

### Ajout P3-03 — dégâts et entretien configurables
- Santé d'un module : pourcentage entier sauvegardé0–100, sorties utiles multipliées par HP/100 ; la masse de la pièce ne disparaît pas avec ses HP.
- `damage.healthLossPerDamagePoint=10`, `damage.maximumModuleLossPerHit=50`, `damage.minimumIntervalTicks=10`, `damage.ownerHitRangeBlocks=3` : coups directs du propriétaire seulement au prototype, sous permissions et ligne de vue.
- `damage.minimumImpactSpeed=0.2` bloc/tick, `damage.healthLossPerImpactSpeed=100` : perte au-delà du seuil, plafonnée comme un coup, sur le châssis ; valeur0 du multiplicateur désactive ces dégâts d'impact.
- `motion.pilotMass=2` : ajout à la masse de l'instance occupée. Le plan décrit toujours la masse des modules, pas celle d'un pilote présent.
- `garage.healthPerRepairKit=25` : un module réparé par trousse (le plus abîmé, égalités dans l'ordre du plan), plafonné à100 ; pas de débit si tout est intact. Recette JSON de la trousse : fer2, cuivre1, redstone1. La batterie conserve sa charge restante ; sa réparation ne remplace pas une cellule.
- `garage.minimumRechargeIntervalTicks=40` est le délai commun recharge/réparation/peinture, conservant le nom de configuration existant. Recette et quantité restent dans les données content.
- `garage.dyePerPaint=1` (borné1–64) : teinture de la couleur choisie, par zone d'une instance entière. Paiement réparti sur les piles de l'inventaire natif si nécessaire ; couleur déjà identique = aucun coût. Palette du brouillon gratuite, fabrication initiale facturée en modules uniquement. Les deux zones suivent les16 teintures vanilla ; RGB24bits sauvegardé, sans effet sur les performances. C'est un choix d'équilibrage initial non verrouillé.
- Ces valeurs initiales ne sont pas des décisions verrouillées du concepteur. Vitesse, impact, coûts et lisibilité des dégâts restent à équilibrer en partie réelle. Recyclage, dégâts de feu/lave et PvP véhicule non définis dans cette tranche.

### Force et speeder inoccupé (P3-04)

Valeurs de configuration Force : `vehicle.stabilizeLevel=25`, `vehicle.brakeLevel=45`, `vehicle.cooldownTicks=80`, `vehicle.maximumMass=20`, `vehicle.baseCost=4`, `vehicle.costPerMass=0.5`, `vehicle.retainedMotion=0.25`. Coût4+masse×0,5 ; prototype de masse8 coûte8 points. Stabilisation : conserve25% de la vitesse verticale actuelle ; freinage : conserve25% de l'horizontale. Actions ponctuelles, gravité/collision ordinaires continuent au tick suivant. Pas de seuil de montée gratuite, charge de batterie ni HP augmentés. Résistance initiale de l'adaptateur véhicule0 ; contrat prévoit une réduction de l'effet pour une résistance supérieure. Pas d'action si la composante concernée est déjà immobile. Inoccupé et propriétaire uniquement ; masse de pilote exposée pour information mais occupation toujours refusée. Valeurs ajustables, éligibilité des classes supplémentaires et partage de droits à traiter avec leurs modules.

### Recyclage — rendements

Seulement un véhicule dont tous les châssis légers sont à0HP, au garage et sous les mêmes droits/délai que l'entretien. Deux clics à moins de100ticks, cible UUID inchangée ; aucune démolition de véhicule intact. Rendement par matière et par module : `floor(quantité JSON × HP /100)`. Valeurs initiales du champ `salvage` dans `speeder_modules/*.json`, modifiables par données : châssis27 pépites de fer ; moteur9 pépites de fer +1 lingot cuivre ; batterie9 pépites de fer +1 cuivre ; siège4 pépites de fer +2 cuirs. Une pièce détruite rend0. Pas de module rechargé, cellule, teinture ou redstone rendus par ces règles initiales. Une épave0/75/100/100 rend19 pépites,1 cuivre,2 cuirs ; plan papier conservé. Règle absente, stockage inconnu ou inventaire plein : refus intégral.

## Planètes — proposition
- Un type principal de cristal rare par planète ; rareté associée à la structure plutôt qu'à un taux de minerai omniprésent.
- 1 boss Jedi et 1 boss Sith par planète ; arènes persistantes, réinvocation encadrée.
- Cristaux : sources renouvelables **sans régénération destructrice des blocs placés par les joueurs** ; préférer structures/systèmes d'expédition/loot réinitialisables par événement.

## Principes de validation
- Un joueur sans sabre doit pouvoir commencer et progresser, mais le sabre reste un objectif marquant.
- Le niveau 100 est un objectif de long terme, pas atteignable en quelques heures de ferme à mobs.
- Un boss n'est pas une source infinie de ±25 instantanément.
- Les meilleurs véhicules restent soumis à la taille, au poids, à la consommation et aux collisions.
