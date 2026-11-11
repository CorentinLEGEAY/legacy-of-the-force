# MODULE QUESTS — Holocrons, PNJ, quêtes et réputation

**Mission :** donner une raison d'explorer sans transformer le mod en scénario obligatoire. Les quêtes relient temples, sabres, speeders, pouvoirs et boss. **Priorités : P4, P6, P7**.

## QUEST-01 — Philosophie de progression
- Les quêtes sont **des pistes et des récompenses secondaires**, pas la seule méthode pour progresser en Force.
- Le joueur garde la possibilité de découvrir une planète/cristal par exploration ou coordonnées ; pas de verrou de scénario arbitraire.
- Ne pas donner assez d'XP par quêtes pour contourner la volonté du concepteur : XP majoritairement par combats et boss.
- Les joueurs peuvent être lumineux, obscurs ou neutres ; les dialogues se modifient, mais tout joueur peut accéder à une aventure viable.

## QUEST-02 — Holocrons
- Items/blocs rares avec identifiant, origine, texte/son original, indice de planète, plan, capacité ou quête.
- Interaction sur serveur : joueur proche, contenu connu, récompense unique selon règles, événement `HolocronDiscovered` idempotent.
- Un holocron peut révéler : coordonnées approximatives, recette de composant, technique de Force, lieu de cristal, histoire du temple.
- Interface de collection : holocrons découverts, indices consultables ; aucun besoin d'inventaire plein pour garder une connaissance déjà acquise.

## QUEST-03 — PNJ
- Types au départ : maître Jedi, ermite, Sith, explorateur, marchand de composants, pilote, chasseur de primes.
- Dialogues textuels ramifiés limités, conditions par réputation/alignement/quêtes ; pas d'IA générative en jeu dans le MVP.
- PNJ critiques (guide de départ) : ne pas pouvoir être tués définitivement si cela bloque le mod ; prévoir remplaçant/réapparition encadrée.
- PNJ Jedi/Sith spéciaux peuvent devenir boss **uniquement après dialogue/provocation selon fiches de boss**.

## QUEST-04 — Types de quêtes
| Type | Exemple | Validation serveur |
|---|---|---|
| Découverte | trouver une ruine ou une planète | événement dimension/structure |
| Exploration | localiser un cristal rare | collecte valide/crystal UUID |
| Artisanat | assembler un premier sabre | événement atelier |
| Ingénierie | fabriquer un speeder avec batterie/moteur | événement garage |
| Force | déplacer un bloc pour ouvrir une porte | action ciblée autorisée |
| Combat | vaincre un boss Sith/Jedi | événement de récompense éligible |
| Choix moral | aider un temple ou provoquer un duel | action confirmée + réputation |

## QUEST-05 — Chaîne de départ (proposition)
1. « Un écho dans la roche » : découvrir une ruine Overworld avec holocron.
2. « Première résonance » : réveiller Force, apprendre perception ou poussée, combattre des mobs.
3. « L'établi du voyageur » : assembler l'atelier sabre.
4. « Coordonnées gelées » : obtenir l'accès à Ilum et fabriquer un terminal de navigation.
5. « Le sanctuaire enseveli » : récupérer un cristal d'Ilum et affronter facultativement Jedi ou Sith.
6. « Retour à la forge » : installer le cristal, améliorer le sabre, concevoir son premier speeder.
Les six étapes servent de **guide** ; permettre de les accomplir dans le désordre si les prérequis métier sont satisfaits.

## QUEST-06 — Réputation distincte de l'alignement
- **Alignement** est moral (+/-) et dépend fortement des boss, très peu des mobs.
- **Réputation** est relationnelle, propre à une faction/planète ; aider un marchand ou un sanctuaire peut donner réputation sans changer nécessairement le côté de Force.
- Exemples de factions MVP : gardiens Jedi, cercle Sith, voyageurs indépendants ; les véritables guerres de factions/ordres sont hors MVP.
- Éviter de transformer « tuer un mob hostile » en points de réputation universels ; les reputations proviennent d'actions ciblées.

## QUEST-07 — Données, sauvegarde, UI
- Définitions de quêtes data-driven : ID, étapes, conditions, récompenses, textes de localisation, dépendances, répétable oui/non.
- Progression par joueur : étapes validées, ID événements payés, dialogues découverts, réputation par faction.
- Sauvegarder après une étape critique et limiter double déclenchement ; les quêtes « collectionner » ne consomment pas des objets automatiquement sans confirmation.
- Journal des quêtes : journal détaillé, filtres, indices non spoilants, marqueurs discrets paramétrables.

## QUEST-08 — Multijoueur
- Chaque joueur a sa progression et son alignement ; combat de boss partagé seulement selon éligibilité de l'arène.
- Quête de groupe facultative : participants et contributions validées serveur ; un membre absent ne reçoit pas automatiquement toutes les récompenses.
- Rejoindre tardivement n'efface pas les quêtes d'un joueur ; laisser rejoindre des expéditions sans forcer même quête active.

## Critères d'acceptation
- [ ] Holocron débloque une information une seule fois, sauvegardée après redémarrage.
- [ ] Chaîne de départ accompagne le joueur vers Ilum sans bloquer l'exploration libre.
- [ ] Quête de boss ne donne pas double alignement (le RPG récompense via boss une seule fois).
- [ ] Réputation et alignement restent deux données distinctes.
- [ ] Quête multijoueur utilise événements serveur, pas de confiance dans clics/paquets clients.
