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
- Fermer une vraie socket pendant PAID/TELEPORTED/ACTIVE_DURABLE à l'aller, reconnecter la même identité pendant que son pair reste connecté ; paiement unique, inventaires natifs/atlas/reçus et retour corrects. Si le pair occupe sa place, relocaliser localement sans restaurer l'ancien inventaire, conserver regard/bookmark et refuser sans mutation s'il n'existe aucun site autorisé. Ne pas rembobiner une exploration ultérieure. Preuves et portée : validation/P4-02_DISCONNECT.md.
- Une structure ne se génère pas dans une zone déjà construite par le joueur.
- Boss non dupliqué par rechargement de chunk ; récompense une fois par victoire éligible.
- Victoire partagée : seules les personnes ayant participé selon les règles obtiennent récompenses.
- Déconnexion pendant un combat, boss despawn, arène interrompue → récupération cohérente.
- Deux sockets clientes réelles : deux duels d'Ilum, trois phases/décor/signature observés par chaque JVM, contribution récente de chacun, parades/impulsions payées et collecte par clics vanilla ; recouper inventaires des clients, fichiers joueurs, reçus et régions. Fixture d'équipement/niveau explicite, pas de soin/dégât/téléportation pendant combat. Preuves : validation/P4-03_NETWORK.md.
- Mob résistant au sol avec gravité verticale négative : poussée autorisée et payée, mouvement horizontal réel, collision vanilla avec le sol intact ; permissions du volume complet conservées. Boss près d'une limite : aucun déplacement natif du corps hors arène ni reset malgré un participant présent. Autel avec entités cachées/non prêtes : aucune intention ni spawn ; publication possible dès readiness, sans suspendre le service.

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
### Reprise d'une arrivée déjà sauvegardée — P4-02
Après ARRIVED et ACTIVE_DURABLE, changer les droits ou placer un danger de fixture avant reprise : refuser de clore sans site autorisé, préserver fichier joueur/journal si rien n'est possible, sinon relocaliser sans modifier le danger ni repayer. Refuser home et voisinage pour vérifier le secours au spawn avec bookmark conservé. Cas reproduits sur dédié et contrôlés indépendamment ; vingt checkpoints Error et six kills Java du transfert réel après correction. Harnais/scripts et limites dans validation/P4-02_CRASH.md ; ces tests ne valident pas deux connexions réseau réelles.

### Régression des volumes de permission — 2026-11-15
Pour les blocs, mobs, véhicules et arrivées, refuser un seul emplacement du volume qui ne soit ni son centre ni le dernier coin parcouru ; vérifier refus avant paiement/publication. Les itérateurs BlockPos.betweenClosed réutilisent une MutableBlockPos : ne pas collecter leurs références via toList avant examen. Parcours immédiat ou copie immutable obligatoire si une position doit être conservée. Trois tests Force supplémentaires ont reproduit les contournements avant correction, puis passent après parcours immédiat. Détails : validation/PERMISSION_VOLUMES.md.

Ne pas publier si : duplication de ressources persistante ; crash lors d'une déconnexion en télékinésie ; perte de véhicules ou d'inventaires ; téléportation interdimensionnelle avec effacement d'objets ; privilèges client permettant d'attribuer XP ou dégâts arbitraires.

P4-03 solo : exécuter test-boss-solo.ps1 puis check-boss-solo.py, et une seconde fois avec -Recover depuis la copie arrêtée. Exiger vrais IntegratedServer/connexion mémoire et deux duels, inventaires client/playerdata/level.dat concordants, checkpoint core cohérent, récompenses/reçus stables et journaux byte identiques après reload. Aucun rééquipement ni nouvelle attribution en reprise. Protocole et essai refusé conservé : validation/P4-03_SOLO.md.

P4-03 publications/victoires : test-boss-arena-crashes.ps1 arme chaque frontière, tue la JVM native (handle/PID/start/commande contrôlés), conserve la copie interrompue puis reprend dans une nouvelle JVM. check-boss-arena-crashes.py ac1 ac2 --all-checkpoints exige couverture16/16, étapes durables attendues, fichiers/régions natifs concordants et zéro ou un règlement selon l'existence de l'intention de victoire. Exit normal ne vaut pas interruption. Résultats/limites : validation/P4-03_ARENA_CRASH.md.

P4-03 cycle de vie : bosses.lifecycle collecte d'abord un vrai lot de fixture puis teste quatre participants natifs : mort avec/sans conservation, dimension de fixture autorisée, logout/login. Exiger reçu/WAL préservés, respect des drops vanilla suivis par UUID, reprise fermée sans réinjection, boss abandonné absent et zéro nouvelle récompense. test-bosses.ps1 -OnlyLifecycle et check-boss-lifecycle.py, aussi inclus dans la suite complète. Acteurs embarqués et absence de panne simultanée explicités dans validation/P4-03_LIFECYCLE.md.

P4-03 fluides : vrais appels natifs de seaux eau/lave, pickup eau/poudre et dispenseurs. Refuser sans consommation ; compter le repli vanilla en seaux pleins éjectés. Deux témoins extérieurs et flux programmé extérieur doivent fonctionner, flux vers ACTIVE refusé puis reprise automatique après reset. Relire inventaire/blocs/ItemEntity natifs avec check-boss-fluids.py. Dans deux vrais clients et le solo, exiger eau/seau vide réellement prédits puis annulation/restitution serveur, avant les deux duels/collectes ; bnf3/bsf1 et rechargement bsf2 passent. Rapports validation/P4-03_FLUIDS.md et PACKAGED_SERVER.md ; installateur hash vérifié, JAR isolé, deux JVM autonomes normales et mixins réellement appliqués. Les preuves de développement ne remplacent pas le test de distribution, et celui-ci ne prétend pas un nouveau parcours avec clients.

P4-03 santé : test-bosses.ps1 -OnlyHealth puis check-boss-health.py. Deux participants natifs au cap1024, dégâts128→896, troisième arrivant sans soin et base sauvegardée1024. JUnit vérifie migration10000→1024, formules et non-finis. Core : AtomicNbtFileTest couvre vrais refus de partage Windows temporaires/permanents, absence de fallback entre providers, budgets et 1000 publications avec lecteur concurrent. Voir validation/P4-03_HEALTH.md et CORE_ATOMIC_REPLACE.md.

P4-04 : `test-quests.ps1 -Run <neuf>` crée un vrai monde normal loopback25586, deux acteurs natifs embarqués, ruine/mine réellement générées, minage/pickup puis seconde JVM/login. `check-quests.py` réconcilie régions et fichiers joueur. Quatre GameTests standards couvrent inventaire plein/deux propriétaires/répétitions, schéma futur/portée/spectateur, cristal identifié en main secondaire sans consommation, pièces de structure opaques. Cinq JUnit couvrent bornes/idempotence/ordre des jalons et définition distribuée sans récompense. Affichage réel français, défilement et clic holocron observés avec Computer Use ; second client arrêté par violation d’accès GLFW pendant le focus, affichage de ce client non validé. Solo intégré, mort et pannes combinées propres au journal restent à exercer. Rapport validation/P4-04.md.
