# MODULE FORCE — Physique, télékinésie et pouvoirs

**Mission :** fournir une interaction physique avec blocs, mobs et certains véhicules, liée à la puissance du joueur. La Force est **un système manipulable**, pas une simple série d'effets visuels. **Priorités : P1, puis P5**.

## FORCE-01 — Cibles manipulables
Cinq types de cibles abstraites :
1. **Objet léger / item lâché** : attraction et déplacement immédiats dès les premiers niveaux.
2. **Bloc simple** : terre, bois, pierre, etc., sans conteneur ni donnée sensible.
3. **Assemblage de blocs** : groupe de blocs connectés, valide et borné ; avancé.
4. **Entité vivante** : mob passif, mob agressif, élite ; mass/résistance ; boss partiellement immunisés.
5. **Véhicule** : adaptateur exposé par module `speeder`; pas d'import circulaire ; restrictions masse, pilote, propriétaire.

Chaque type fournit via `ForceTargetAdapter` : identifiant, masse efficace, type, position, résistance, permissions, actions autorisées, méthode serveur de déplacement.

## FORCE-02 — Machine à états d'une session télékinétique
`IDLE → TARGETING → GRABBING → HOLDING → MOVING → {DROPPING | THROWING | CANCELLED} → IDLE`.
- **Viser** : raycast serveur (position cible + ligne de visée) ; revalidation après requête client.
- **Saisir** : vérifier niveau, nombre, masse, distance, énergie, permissions, dimensions, cooldown, budget de blocs mobiles ; verrouiller la cible.
- **Maintenir** : consommer énergie à fréquence serveur ; suivre un point de visée filtré et limité en vitesse ; interrompre automatiquement quand conditions perdues.
- **Déplacer** : collider avec monde ; aucune traversée de bedrock ou zone protégée.
- **Déposer** : uniquement sur positions libres valides ; vérifier support/orientation et restauration des block states compatibles.
- **Projeter** : générer vitesse/impulsion limitée par puissance, coût, masse ; dégâts calculés côté serveur et bornés.
- **Annuler** : compensation sûre, restauration de cible/état avant prise, récupération si chunk inaccessible.
- Un joueur ne possède qu'une session de télékinésie active à la fois au MVP.

## FORCE-03 — Masse, portée et niveaux
- Niveaux <15 : objets lâchés légers uniquement. Niveau 15 : 1 bloc léger ; 35 : 3 ; 60 : 8 ; 85 : 16 ; 100 : **32 blocs légers maximum**.
- La masse de blocs moyens/lourds réduit le nombre réellement déplaçable ; une masse trop grande rend la manipulation impossible.
- Portée prototype : niveau 1=4 ; 15=6 ; 35=10 ; 60=15 ; 85=20 ; 100=24 blocs ; entre paliers, interpolation facultative.
- Classes de masse : très légère 0,5 ; légère 1 ; moyenne 2,5 ; lourde 5 ; extrême 9 (config).
- Coût initial 4 + 2×masse ; maintien 0,8×masse énergie/s ; lancer 7 + 3×masse ; un modificateur de distance s'applique au contrôle (valeurs configurables).
- Ne pas autoriser au niveau 15 l'obsidienne simplement parce que `maxBlocks >= 1` ; valider **budget massique + catégorie débloquée**.
- Les boss et véhicules ont une masse/résistance spécifique, non calquée automatiquement sur le nombre de blocs.

## FORCE-04 — Déplacer le **vrai monde**
### MVP : bloc simple uniquement
- Liste blanche initiale de blocs non dangereux et sans `BlockEntity` ; refuser coffres, conduits, machines, spawners, portails, bedrock et blocs à conséquences non maîtrisées.
- Retirer le bloc seulement après création d'une **transaction** et réservation de sa position.
- Représenter le bloc par une entité mobile temporaire ou un objet de simulation serveur ; conserver son `BlockState` et métadonnées compatibles.
- À dépôt, tester placement et appeler une action atomique : succès → supprimer représentation ; échec → conserver maintien OU restituer en zone sûre.
- Sauvegarde, crash, déconnexion, téléportation et unload chunk ne doivent causer ni perte ni duplication.
### Avancé : groupe de blocs
- Sélection par volume ou connected component, **max 32** blocs légers (ou moins si masse), bounding box et nombre de données bornés.
- Exclure contenants/blocs complexes et éviter le déplacement des constructions d'autrui ; groupe validé en une transaction.
- Rotation par incréments 90° si la forme et les block states le permettent ; compatibilité avec les blocs directionnels limitée et explicitement testée.
- Collider le groupe via une forme simplifiée ; ne pas simuler chaque bloc par un corps physique indépendant.
- La vitesse max décroît avec la masse ; budgets de sessions par dimension/serveur.

## FORCE-05 — Physique différenciée
- **Gravité** : sable/gravier reposés se comportent normalement ; en transit, simulation simplifiée.
- **Explosion** : un bloc de TNT non amorcé ne s'enflamme pas spontanément ; un TNT réellement amorcé reste soumis à la logique de TNT en fonction de règles sûres.
- **Projectiles** : dégâts = fonction masse × vitesse, caps serveur ; pas de traversée de claims.
- **Protections** : levée de blocs autorisée seulement si source, trajectoire/zone d'atterrissage et destination sont permises.
- **Construction** : repositionnement de blocs à faible vitesse sans les « crafter » gratuitement.
- **Énigmes** : blocs spéciaux détectant une présence/charge télékinétique ; une porte ne peut être contournée par simple destruction non autorisée.

## FORCE-06 — Interaction avec mobs
| Pouvoir | Niveau initial | Comportement serveur |
|---|---:|---|
| Poussée | 5 | Impulsion courte selon masse et résistance |
| Attraction | 15 | Tire la cible vers le joueur avec plafond de vitesse |
| Immobilisation | 25 | Ralentit brièvement, immunité/réduction sur boss |
| Soulèvement | 35 | Suspend un mob non boss pendant durée maximale |
| Projection | 45 | Projette la cible, dégâts plafonnés |
| Onde de choc | 65 | Repousse plusieurs cibles dans rayon borné |
| Domination télékinétique | 85 | Contrôle temporaire de cibles résistantes, pas de perma-stun |
- Ne jamais permettre de lancer un mob protégé ou joueur PvP désactivé ; friendly fire configurable.
- Les boss ont une jauge de **résistance** ; la télékinésie peut interrompre une attaque mais ne les immobilise pas indéfiniment.
- Tous les dégâts/éliminations doivent être attribuables au joueur pour l'XP et l'alignement via `rpg`.

## FORCE-07 — Pouvoirs de lumière/obscur
- Communs : perception, saut renforcé, vitesse, poussée, attraction, bouclier de débris.
- Lumineux : protection, soin limité, purification du cristal (rituel en module sabre), soutien.
- Obscurs : éclairs (niveau min 55, alignement ≤ -50 prototype), étranglement, peur, drain.
- **Éclairs** : ciblage direct visible, dépense continue, un seul ennemi initialement ; chaînes au niveau 80 ; dégâts/cooldown configurables ; la version « exception lumineuse rare » est **hors MVP**.
- Si l'alignement change, un pouvoir exclusif devient inactif sans détruire les points d'apprentissage (règle RPG).

## FORCE-08 — Interaction avec les speeders
- Niveau 25 : stabilisation ponctuelle d'un véhicule léger ; niveau 45 : ralentissement.
- Niveau 70 : soulèvement **d'un speeder léger inoccupé** ; niveau 95 : projection de petite masse ou arracher une pièce déjà très endommagée (fonctionnalité future).
- Refuser les véhicules de joueurs sans autorisation et les véhicules avec pilote/passagers pour les actions dangereuses au MVP.
- Le module `speeder` expose uniquement un adaptateur, le moteur Force ne connaît pas la structure interne des modules.

## FORCE-09 — Contrôles et effets
- Touches proposées (reconfigurables) : maintenir V = mode Force ; clic gauche saisir ; molette ajuster portée ; R rotation ; clic droit déposer ; action d'attaque = projeter.
- Afficher cible valide/invalide, masse estimée, portée, énergie et motif de refus.
- La simulation serveur peut utiliser mouvements interpolés client ; l'effet lumineux ne remplace jamais la vérification de cible.

## Critères d'acceptation
- [ ] Un bloc autorisé est déplacé puis reposé avec **exactement un bloc** au total.
- [ ] Coffre, bedrock, claim et bloc déjà saisi sont refusés sans modification.
- [ ] Déconnexion / changement de dimension / fermeture serveur pendant maintien : restauration ou récupération contrôlée.
- [ ] Puissance, portée, nombre, masse, énergie et droits sont vérifiés côté serveur.
- [ ] Projection d'un mob attribue correctement les dégâts et le kill ; boss non perma-stun.
- [ ] Deux joueurs voient la même position ; limite de charge 32 blocs respectée ; profiling fait.
