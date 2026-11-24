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
### Boss Ilum P4-03 — valeurs de développement
Configuration serveur bosses : santé220, dégâts5, vitesse0,24, contribution minimale2 dégâts ou2 soutiens récents ; XP800, alignement±25 verrouillé. Cooldown récompense par joueur/boss72000ticks, arène12000ticks, reset sans participant100ticks ; quatre participants par défaut, plafond16. Santé supplémentaire35% par membre présent à la fin de l'introduction40ticks. Phases à70%/30%, télégraphe minimum16ticks. Glace10HP, durée160ticks, vitesse0,28 et deux entités maximum par boss ; vulnérabilité après parade40ticks puis immunité de contrôle80ticks. Portées/coûts/délais/multiplicateurs des attaques configurables. Reliquaire partagé : une relique et quatre cellules ; relique+fer donnent quatre kits de réparation via recette native.

Valeurs non équilibrées en survie complète. PV configurables40..1024, plafond natif explicitement appliqué à la base après multiplication de groupe : min(1024,base×(1+supplément×(n−1))). Anciens réglages10000 corrigés à1024 par NeoForge ; NaN/infinis normalisés au défaut dans l’adaptateur runtime avec alerte ciblée. Preuves : validation/P4-03_HEALTH.md. Seuils de phase, plafond d'énergie, géométrie et quelques durées restent constants ; mise en configuration à poursuivre. Les participants tardifs ne recalculent pas la santé initiale. Voir validation/P4-03.md pour limites et preuves.


### Ilum P4-01 — valeurs initiales

Un ensemble de structures, espacement16chunks/séparation8, sel172851 ; poids mine3, templeJedi1, sanctuaireSith1, ruines1. Une candidature peut être refusée sous le niveau marin+2 ou sur une variation de terrain>8blocs. Un seul gisement par mine ; pas de veine de minerai commune. Pioche fer minimum, Fortune/Toucher de soie ne multiplient pas le cristal. Récolte exclusivement dans Ilum. Purité uniforme dans [0,55;0,90], stabilité [0,60;0,95], valeurs configurables `ilum.minedPurityMinimum/Maximum` et `ilum.minedStabilityMinimum/Maximum` dans configuration sabre ; bornes inversées triées, chaque valeur limitée0–1. Aucun changement des valeurs de création créative existantes.

La feature JSON de givre traite chaque chunk ; crête de glace chance1/5, hauteur3–7 (codec borné chance1–64 et hauteur2–16). Biome température−0,7, précipitations0,7 ; neige/glace et faune/monstres vanilla. Aucun malus de froid scripté dans cette tranche. Espacement, poids, géométrie et tables sont des données/code originaux ; le réglage en situation de jeu reste à mesurer. Renouvellement de ressource sans régénérer les chunks à implémenter dans les systèmes d'expédition/loot, pas par un reset du monde.
- Un type principal de cristal rare par planète ; rareté associée à la structure plutôt qu'à un taux de minerai omniprésent.
- 1 boss Jedi et 1 boss Sith par planète ; arènes persistantes, réinvocation encadrée.
- Cristaux : sources renouvelables **sans régénération destructrice des blocs placés par les joueurs** ; préférer structures/systèmes d'expédition/loot réinitialisables par événement.

## Principes de validation
### Voyage Ilum P4-02
Configuration serveur `legacyoftheforce-travel.toml` : `outboundEnergyCells=1`, `prepaidReturnEnergyCells=1` (chacun1–32), `requirePortableTerminal=true`, `arrivalSearchRadius=8` (2–16), `returnVerticalSearch=4` (1–16), `minimumActionIntervalTicks=40` (10–1200), `combatLockTicks=200` (0–1200,0désactive). Le retour consomme une réservation payée à l'aller, pas une cellule supplémentaire transportée. Réservation préservée à la mort ; nouveau départ depuis Overworld avec réservation existante ne paie que l'aller. Boutons du menu espacés de10ticks. Coûts et images figés dans le journal : changer la configuration ne repaie pas une opération déjà préparée.

Recettes JSON : deux terminaux pour fer5/cuivre2/verre1/redstone1 ; carte pour boussole1/papier1/améthyste1/lapis1. Carte consommée à la première découverte, pas aux voyages suivants. Terminal transporté posé par le joueur, réutilisable ; recherche d'arrivée n'en génère aucun. Ancre Ilum stable XZ0,5/0,5, retour au départ sauvegardé puis spawn Overworld si nécessaire. Carte graphique et équilibrage de difficulté/boss à compléter avec P4-03 ; pas de coordonnées de cristal révélées.

- Un joueur sans sabre doit pouvoir commencer et progresser, mais le sabre reste un objectif marquant.
- Le niveau 100 est un objectif de long terme, pas atteignable en quelques heures de ferme à mobs.
- Un boss n'est pas une source infinie de ±25 instantanément.
- Les meilleurs véhicules restent soumis à la taille, au poids, à la consommation et aux collisions.

## Présentation de l’alignement — P1-03
Dans `[rpg]` du fichier `config/legacyoftheforce-server.toml` (surcharge native possible dans le `serverconfig` du monde), `alignmentThresholds = [-500, -200, 200, 500]` donne les quatre frontières en dixièmes. Liste complète validée : exactement quatre entiers, −1000≤obscur<tendance obscure<0<tendance lumineuse<lumineux≤1000. Côté obscur, la frontière est incluse dans la bande la plus sombre ; côté lumineux, elle est incluse dans la bande la plus lumineuse. Les bandes de présentation n’ajustent jamais les gains confirmés, le score, les prérequis d’un pouvoir ni le cristal. `/lotf profile` affiche l’orientation en FR/EN ; défauts identiques à RPG-03. Config incorrecte : NeoForge corrige la liste entière aux défauts, sans réécrire les profils. Preuves de chargement TOML et affichage natif : validation/P1-02-03_NATIVE.md.


### Jedha — réglages initiaux du lot C, non équilibrés en campagne
Gardien310PV, Seigneur300PV, dégâts de base6, XP1700, trois poussières, cristal violet25% par victoire éligible partagée. Garde60ticks, multiplicateur0,4, pas1,6bloc, poussée0,2 ; contre140ticks. Canal36ticks avec impact toutes12ticks (premier au début), coûte30énergie ou40 pour tempête et reste exclusif des autres attaques. Drain tenté une seule fois par phase2/3, soin ≤3PV et ≤PV effectivement retirés. Tous ces nombres sont dans la configuration serveur bosses ; ±25 reste une décision verrouillée. Les limites techniques2 objets, masque de phases et absence d’objets exportables sont des invariants.


Ilum, complément du lot C : minerai fixe3 par nouvelle caverne,1fragment par bloc sans bonus Fortune/Silk,1alliage par fonte (200ticks four/100 haut-fourneau, XP vanilla0,5). Victoire éligible : alliages3 configurables et chance25% de cristal bleu configurée, sans toucher aux ±25 verrouillés. Propulseur cryogénique2,5kg/poussée8/portance14/stabilité0,08, vectoriel1,5/10/8/0,1. JSON et recette serveur ; chiffres non validés en campagne.


Soulèvement de véhicule70 : défaut60ticks prépayés, montée1,2bloc à0,08/tick, masse maximale20 (et limite globale existante), léger vide uniquement. Coût Force20+masse×(vehicle.massCost+durée/20×0,8) puis maîtrise/affinité ; cooldown maximum entre délai habituel et durée. Les limites de bail sont configurables dans Force, avec plafonds absolus contrôlés par spec. Pas de dépense/recharge batterie, pas de remboursement anticipé, pas de projection95. Valeurs non validées en jeu.


Formes du lot B, non équilibrées en campagne : Précision25 dégâts0,95/coût attaque0,9/garde1/parade+0,03 ; Protection30 dégâts0,9/coût attaque1,05/garde0,85/parade+0,05 ; Élan40 dégâts1,08/coût attaque1,18/garde1,15/parade−0,02 ; Deux lames35 dégâts1,04/coût attaque1,2/garde1,25/parade+0,01. Paramètres serveur `forms.*` bornés, fondamentale neutre. Deuxième coup explicite : facteur coût1,6 et dégâts1,1, délai partagé et cap global ; rien n’est doublé simplement en équipant la seconde lame. Holocron consommé une fois pour apprendre, sélection sous délai80ticks ; chance25% de coffre et un enseignement physique de victoire sous cooldown.


FORCE-01/03 — lot non testé : portées4/6/10/15/20/24 sans interpolation par défaut, niveaux1/15/35/60/85/100. Le range6 historique représente le prototype, pas le plafond des nouveaux paliers ; toute valeur historique différente de6 est conservée comme cap, `tiers.maximumRange` nouveau cap par défaut24. Classes0,5/1/2,5/5/9 déblocages15/15/35/60/85, budget massique basé sur1/3/8/16/32 masses légères ; le nombre peut être plafonné séparément par le serveur. Maintien `moveStep/max(1,mass)^exposant` (0,5), distance au-delà de3 : ×min(2,1+0,04×surplus). Ces nouvelles sélections ne changent pas une masse de journal existant.

Attraction d’items : niveau1,100ticks maximum,0,25bloc/tick, un acteur/cible et128sessions serveur. Masse0,25+0,75×fraction de pile ; coût2+masse×(grabCostPerMass+duréeSecondes×0,8), affinité/maîtrise/distance appliquées une fois. Prépayé et non remboursé à une interruption, cooldown au moins100ticks. Pratique après1,5bloc de déplacement réel, une clé par type d’item dans la fenêtre commune ; collecte/fusion/lifetime natifs, aucun gain d’XP ni génération d’objet. Paramètres dans Force, performances/valeurs à équilibrer en campagne.
