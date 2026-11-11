# MODULE RPG — Progression, alignement et compétences

**Mission :** gérer la progression serveur du joueur sans confondre puissance, alignement et spécialisation. **Priorité : P1**.

## Données persistantes du joueur
- `level` entier **1..100**, `xpInLevel`, `totalXpEarned` (informatif), `skillPoints` non négatif.
- `alignmentTenths` entier **-1000..1000** (visible à l'écran comme -100,0..+100,0).
- `forceEnergy` et `maxForceEnergy`, cooldowns, pouvoirs connus, rangs de maîtrise individuels.
- Progression découverte : planètes débloquées, holocrons trouvés ; soit via API distincte pour ne pas rendre RPG responsable de la planète.
- Choix de persistance à la mort : **niveau, alignement, compétences et découverte conservés** ; énergie réinitialisée selon configuration, pas de perte d'XP par défaut.

## RPG-01 — XP et niveaux
- Source principale : **mobs tués volontairement et boss vaincus**. Les mini-objectifs/quêtes peuvent donner une très petite XP facultative, sans remplacer les combats.
- Table initiale `passif=1`, `agressif=5`, `elite=40`, `miniBoss=200`, `boss=800..3000`.
- Formule configurable `xpNext(L) = round(40 + 4L + 0.25L²)` ; L=1..99 ; respecter le niveau 100 et les débordements de stockage.
- Attribution à partir de l'événement de mort crédite par serveur, et non d'un paquet client. Interdire paiement multiple via `eventUUID`.
- Enregistrer historique **limité** des récompenses anti-spam et identifiants de boss pour cooldown.
- Récompenses de boss partagées aux joueurs éligibles ayant réellement participé (voir `bosses/SPEC.md`).
- La récompense de quête est plafonnée et visible séparément pour préserver le caractère combat du projet.

## RPG-02 — Alignement moral (règle centrale confirmée)
| Action éligible | Stockage (dixièmes) | Affichage |
|---|---:|---:|
| Mob passif tué volontairement | -1 | -0,1 |
| Mob agressif tué volontairement | +1 | +0,1 |
| Mob hostile élite tué volontairement | +5 | +0,5 |
| Boss Sith vaincu | +250 | +25,0 |
| Boss Jedi vaincu | -250 | -25,0 |
- Toujours borner aux extrêmes ±1000.
- Mort accidentelle (lave, chute, autre mob) : **aucun changement**, sauf si l'action du joueur a été causalement attribuée avec une règle explicite (tir, projectile ou Force projetée).
- Les fermes automatiques et les mobs fictifs d'entraînement ne changent pas l'alignement.
- Gagner un niveau n'impose **aucun côté de la Force** ; le joueur peut être neutre au niveau 100.
- Victoire de boss pendant cooldown de récompense : 0 XP, 0 alignement, 0 loot majeur jusqu'à expiration. Hors cooldown : ±25 confirmés.

## RPG-03 — Catégories et seuils
Seuils initiaux **présentation uniquement**, ajustables :
- -100..-50 : obscur confirmé ; -49,9..-20 : tendance obscure ; -19,9..+19,9 : neutre ; +20..+49,9 : tendance lumineuse ; +50..+100 : lumineux confirmé.
- Les pouvoirs ont leur propre `requiredLevel` et condition `minAlignment` ou `maxAlignment`.
- Un joueur qui change de camp ne perd jamais son niveau de Force, mais ses capacités incompatibles deviennent **inactives** ; les points investis restent comptabilisés ou un respec limité est offert pour éviter les soft locks.
- Les seuils ne changent **jamais** la couleur d'un cristal instantanément : voir transformation volontaire dans `lightsaber/SPEC.md`.

## RPG-04 — Arbres de compétences
- **Commun** : télékinésie, poussée, attraction, sauts, perception, réflexes.
- **Lumineux** : soin, protection, purification, résistance, soutien.
- **Obscur** : éclairs, étranglement, peur, drain, amplification offensive.
- Points de compétences attribués selon niveau (prototype : 1 point/niveau, bonus rares via holocrons) ; valider prérequis de niveau, d'alignement et de branche.
- Capacité passive ≠ pouvoir actif. Chaque pouvoir actif a énergie, portée, cooldown, cible autorisée et rang de maîtrise.
- Les rangs de maîtrise se développent par des utilisations **significatives** et des défis, pas par spam à l'infini ; plafonds par fenêtre de temps.
- Interface arborescente avec chemins, prérequis et description des conséquences.

## RPG-05 — Énergie de Force
- Réserve initiale 100 ; régénération prototype 2 pts/s hors canalisation ; modifiée par compétences et certains cristaux.
- Serveur décide dépense, maintien, interruption et récupération ; ne pas admettre un état énergie négative.
- Pauses : mort, déconnexion, changement de dimension, immobilisation par un boss ou canalisation selon règles.
- Clamper `forceEnergy` à `[0, maxForceEnergy]` à chaque mise à jour.

## RPG-06 — Anti-exploits
- Mesure d'attribution de kill : dernier dégât significatif et responsabilité du joueur (arme, projectile, Force), avec délai court, pas simple proximité.
- Détection de mobs produits en boucle : gestion configurable, n'empêche pas le combat normal ; mortalité automatique non rentable.
- Boss sur cooldown par ID et joueur ; la répétition du même combat ne donne pas ±25 plusieurs fois immédiatement.
- Aucune modification d'XP/alignement directement depuis UI ou commande non-administrateur.

## API exposée
- `getProgress(UUID) -> immutable snapshot` ; `awardKillXp(KillContext)` ; `applyAlignment(AlignmentEvent)` ; `trySpendForceEnergy(UUID, amount)` ; `hasPower(UUID, id)` ; `getEffectivePowerLevel(UUID,id)`.
- Événements : niveau gagné, seuil d'alignement franchi, pouvoir débloqué, score modifié.

## Interfaces joueur
- HUD : niveau + jauge d'XP, barre Force, petit marqueur d'alignement.
- Écran profil : niveau, points, orientation, historique des boss, compétences et rangs.
- Pas d'information sensible décidée par le client.

## Critères d'acceptation
- [ ] 10 mobs hostiles volontaires donnent +1 alignement ; 10 passifs donnent -1.
- [ ] Boss Sith +25/Jedi -25 seulement si récompense éligible ; cap ±100.
- [ ] Aucun XP pour un kill automatique non attribuable ; un événement ne paie pas deux fois.
- [ ] Aucun niveau supérieur à 100, pas d'XP négative ni de perte de niveau après reconnexion.
- [ ] Force et capacités sont serveur-only et HUD reflète un snapshot.
- [ ] Changement de camp suspend les pouvoirs exclusifs sans supprimer le niveau.
