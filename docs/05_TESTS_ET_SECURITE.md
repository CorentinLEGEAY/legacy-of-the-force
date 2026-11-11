# 05 — Tests, robustesse et protection des mondes

## Règle absolue
Tester le gameplay **sur un serveur dédié**, même si le développement se déroule d'abord en solo. Client et serveur possèdent des responsabilités distinctes ; un écran 3D réussi n'est pas une fonction serveur validée.

## Jeux de tests minimaux
### RPG
- `alignmentTenths = 0` ; tuer volontairement 10 mobs agressifs → +10 dixièmes = +1,0 ; 10 mobs passifs → -1,0 ; 1 boss Sith → +25,0.
- Clamp -1000..1000 ; morts environnementales et machines → 0 ; un mob ne paie qu'une fois ; attribution multi-joueurs contrôlée.
- XP normale/cooldown/anti-fermes ; niveau jamais >100 ; sauvegarde identique après redémarrage.
- Une mort du joueur ne remet pas arbitrairement le niveau à zéro ; préciser politique perte d'XP séparément.
### Force / blocs
- Saisie d'un bloc de pierre autorisé ; déplacement ; dépôt sans duplication ni perte.
- Refuser coffre/portail/bedrock/claim ; refus avec motif intelligible ; aucune modification du monde.
- Déconnexion pendant levitation, changement de dimension, arrêt brutal → bloc récupérable ou restauré.
- Deux joueurs saisissent le même bloc → seul un verrou réussit.
- Collision avec une position occupée → dépôt refusé, cible reste contrôlée ou revient au point sûr.
- Mob immunisé, boss, entité protégée, cible hors portée : cas refusés.
### Sabres
- Assemblage valide consomme chaque composant une fois ; échec de validation consomme zéro.
- Sauvegarde/rechargement conserve les pièces et le cristal ; corruption/purification ne duplique pas.
- Modèle invalide/corrompu → traitement sûr, pas de crash ni d'objet effacé.
- Le client ne peut pas forcer des dégâts ou un bonus de cristal non acquis.
### Speeders
- Plan valide/invalide (sans batterie, modules détachés, trop grand) ; pas de consommation si erreur.
- Spawn, conduite, collision, siège pilote, déconnexion, redémarrage, changement de chunk.
- Détruire une pièce n'efface pas tout l'inventaire du véhicule ; réparation exige matériaux.
- Deux joueurs ne peuvent pas piloter le même siège simultanément.
- Modules ou plan falsifiés par paquet → refus.
### Planètes/boss/quests
- Voyage aller/retour avec inventaire intact et sans doubles transferts.
- Une structure ne se génère pas dans une zone déjà construite par le joueur.
- Boss non dupliqué par rechargement de chunk ; récompense une fois par victoire éligible.
- Victoire partagée : seules les personnes ayant participé selon les règles obtiennent récompenses.
- Déconnexion pendant un combat, boss despawn, arène interrompue → récupération cohérente.

## Matrice de plateforme
| Scénario | Solo | Serveur dédié | Deux clients | Redémarrage |
|---|---:|---:|---:|---:|
| Progression RPG | Oui | Oui | Oui | Oui |
| Télékinésie bloc | Oui | Oui | Oui | Oui |
| Sabre personnalisé | Oui | Oui | Oui | Oui |
| Speeder piloté | Oui | Oui | Oui | Oui |
| Voyage planète | Oui | Oui | Oui | Oui |
| Boss / récompenses | Oui | Oui | Oui | Oui |

## Sécurité / anti-exploit
- Client envoie des **intentions**, pas de valeurs « j'ai gagné 1000 XP ».
- Valider dimension, position, portée, ligne de visée, cooldown, énergie, inventaire, droits et états à chaque action.
- Limiter taille/taux des paquets et taille des plans de construction (anti-DoS).
- Transactions de blocs/objets idempotentes ; UUID de transaction et verrou anti-double usage.
- Traces d'audit minimales pour violations de règles sans inclure données personnelles.
- Respecter spawn protection et systèmes de claim si présents ; proposer points d'extension pour intégration future.

## Performance (objectifs indicatifs)
- Aucun tick serveur ne parcourt toute la dimension pour chercher des cibles.
- Les pouvoirs/entités distantes se désactivent proprement après déchargement de chunk.
- Fixer un budget de blocs mobiles actifs global et par joueur ; profiler un scénario avec deux joueurs et 32 blocs mobiles.
- Le rendu de tous les effets doit être désactivable/simplifiable côté client.

## Critères d'arrêt de release
Ne pas publier si : duplication de ressources persistante ; crash lors d'une déconnexion en télékinésie ; perte de véhicules ou d'inventaires ; téléportation interdimensionnelle avec effacement d'objets ; privilèges client permettant d'attribuer XP ou dégâts arbitraires.
