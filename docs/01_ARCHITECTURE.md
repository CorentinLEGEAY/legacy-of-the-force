# 01 — Architecture, interfaces et sauvegardes

## Contraintes techniques
- **Un mod Java NeoForge** : un seul projet Gradle, un seul `modid=legacyoftheforce`, un seul JAR distribué. Pas de microservices ni de modules Gradle séparés au MVP.
- Packages Java `com.legacyoftheforce.{core,rpg,force,lightsaber,speeder,planets,bosses,quests,client,content}`.
- API et événements partagés : `com.legacyoftheforce.core.api`. Les modules y dépendent **vers l'intérieur**, jamais l'inverse.
- Documents dans `modules/<module>/SPEC.md` ; le code source réel suivra `src/main/java/...` et `src/main/resources/...` après création du squelette.
- Java 21 et Minecraft Java 1.21.1/NeoForge 21.1.x (à épingler dans Gradle). Vérifier API dans la documentation de cette version.

## Diagramme logique
```text
                             client (HUD, écrans, rendu)
                                        ↓ lecture
core (config, registres, API, réseau, permissions, événements, sauvegardes)
 ├─ rpg (XP, alignement, points, maîtrise)
 ├─ force (moteur de cibles, énergie, capacités) ← rpg
 ├─ lightsaber (assemblage, cristal, combat) ← rpg + core.api
 ├─ speeder (atelier, physique simplifiée, dégâts) ← core.api
 ├─ planets (dimensions, worldgen, voyages) ← core.api
 ├─ bosses (combat, arènes, butin) → APIs rpg/force/lightsaber/planets
 ├─ quests (quêtes, holocrons, événements) → APIs rpg/planets/bosses
 └─ content (data packs, recettes, items, blocs) → registries core
```
Les flèches représentent l'usage **d'API publiques** ; n'autoriser aucun cycle d'import. Les intégrations transverses passent par un service/registre, événement ou contrat commun, pas des imports réciproques.

## Contrats métier à définir dans `core.api`
Ce sont des **signatures conceptuelles**, pas des API NeoForge existantes. Codex doit les adapter en Java réel avec tests :

```java
interface PlayerProgressService {
  PlayerProgressSnapshot get(UUID playerId);
  XpAwardResult awardKillXp(UUID playerId, KillContext kill);
  AlignmentResult recordAlignmentAction(UUID playerId, AlignmentEvent event);
}
interface ForceTargetAdapter {
  TargetId id();
  double effectiveMass();
  boolean canManipulate(ServerPlayer actor, ForceAction action);
  ForceActionResult apply(ForceAction action, ForceTransform transform);
}
interface ProtectedWorldService {
  boolean mayModify(ServerPlayer actor, ServerLevel level, BlockPos pos, ModificationKind kind);
}
interface VehicleCatalog {
  Optional<VehicleSnapshot> find(UUID vehicleId);
  boolean acceptForceAction(UUID vehicleId, ForceAction action, UUID playerId);
}
interface RewardEventSink {
  void onBossDefeated(BossDefeatEvent event);
  void onCrystalBondChanged(CrystalBondEvent event);
}
```
`PlayerProgressSnapshot`, `KillContext`, `AlignmentEvent` etc. sont des **types à créer** dans le dépôt et à faire évoluer sans exposer des entités/mutations internes.

## Responsabilités serveur / client
### Serveur (autorité)
- XP, niveau, alignement, énergie, pouvoirs débloqués, dégâts.
- Sélection des cibles et validation distance/masse/ligne de visée/permis ; déplacement réel des blocs et mobs.
- Validation de toutes les recettes/assemblages ; consommation de composants ; création de sabre/speeder.
- Physique de speeder, dégâts, carburant, collision, stockage et état du véhicule.
- Voyages, génération, boss, butins et quêtes.
- Sauvegardes, cooldowns, limitations anti-exploit.
### Client (présentation)
- Entrées clavier/souris ; envoie requêtes à faible fréquence avec séquence ; aucun dommage/apparition/XP décidés localement.
- HUD, écrans d'atelier, prévisualisations 3D, effets, animations, sons ; interpolation des positions validées par serveur.

## Messages réseau (exemples)
| Intention C→S | Champs | Validation S | Réponse |
|---|---|---|---|
| `request_force_grab` | target ID/pos, sequence | niveau, cible, portée, permission, cooldown | action acceptée/refusée + état |
| `request_force_move` | session ID, transform souhaitée | session valide, vitesse max, budget | position validée |
| `request_saber_assemble` | plan, atelier | inventaire, compatibilité, recette | item ou erreur |
| `request_speeder_assemble` | modules, atelier | dimensions, connectivité, composants | véhicule ou erreur |
| `request_travel` | destination, terminal | planète débloquée, énergie, permission | transfert ou erreur |
| `request_power_cast` | ID pouvoir, cible | alignement, XP, énergie, cooldown | effet + nouvel état |

Limiter taille, fréquence et nombre d'éléments de chaque paquet. Ne jamais permettre au client de décider des dégâts ou de transmettre une entité arbitraire non accessible.

## Données persistantes (ne pas stocker deux fois la vérité)
| Élément | Stockage recommandé (conceptuel) | Identifiant |
|---|---|---|
| Profil RPG | attachment persistant sur joueur ; gestion mort/respawn explicite | UUID joueur |
| Assemblage sabre/cristal | data component de l'ItemStack, format versionné | ID instance du sabre/cristal |
| Speeder en monde | données entité + modules sérialisés | UUID véhicule |
| Plan sauvegardé | données joueur/monde avec limites | UUID du plan |
| Boss, arènes, voyages et réapparitions | `SavedData` par monde/level selon besoin | dimension + ID d'arène |
| Factions/quêtes | profil/monde, selon propriété | UUID joueur, ID faction/quête |
| Blocs en mouvement | transaction temporaire résolue avant suppression | ID transaction/monde/positions |

### Véhicule en monde (P3-02, intégration en cours)
L'entité de speeder et son enveloppe `VehicleState` restent dans le stockage des entités de sa dimension. Le speeder désactive le mécanisme vanilla `RootVehicle` qui déplace habituellement la sauvegarde d'une monture monoplace dans le fichier du joueur : le hook virtuel `hasExactlyOnePlayerPassenger` est volontairement faux pour cette politique de stockage, sans autoriser plusieurs sièges. La vraie garde de siège vérifie séparément propriétaire et passager unique. Le logout fait descendre le pilote. Ainsi un autosave du joueur ne conserve pas une deuxième copie mutable de son véhicule, et le départ du pilote ne supprime pas l'entité du monde. Les coordonnées vanilla de l'entité guident le chargement ; le checkpoint de pose de l'enveloppe est actualisé par le serveur après chaque déplacement.

`VerifiedEntitySaveBarrier` utilise le gestionnaire vanilla, attend son `saveAll`/flush puis relit son `SimpleRegionStorage` pour vérifier exactement l'entité/UUID sérialisée, une seule fois dans le chunk. Trois champs restent final et deviennent accessibles par AT dans la version épinglée ; aucun writer parallèle de région ni remplacement du gestionnaire. C'est une barrière de transaction rare, pas une sauvegarde par tick ; son coût sur un serveur chargé reste à profiler. Cette primitive ne constitue pas encore une transaction d'assemblage complète : fichier joueur, paiement, publication et reprise doivent être coordonnés par un journal avant activation de la fabrication.

### Paiement/publication de speeder (P3-02, intégré à `69d32fd`)
Le coordinateur d'assemblage conserve un journal borné par UUID joueur sous `data/legacyoftheforce/speeder_assembly/`. `AssemblyReceipt`, attachment immuable/copier-à-la-mort du joueur, contient un schéma, une révision monotone et l'UUID de dernière opération, sauvegardés dans le même fichier natif que son inventaire. Le journal conserve les images avant/après pour PREPARED → PAID → PUBLISHED → DONE ; PREPARED non payé et sans instance peut devenir ABORTED. Le writer vanilla joueur reste utilisé, avec fsync et lecture de contrôle ; seul le champ final `PlayerList.playerIo` devient aussi accessible par AT. L'entité publiée porte temporairement `assemblyId` et reste immobilisée jusqu'à preuve de sa publication puis activation. Le journal DONE est un reçu, pas un second état de charge/position : il ne doit jamais recréer un véhicule détruit ou remettre à zéro sa batterie. Réconciliation ambiguë refusée et journal préservé ; voir `validation/P3-02_ASSEMBLY.md` et `validation/P3-02_CRASH.md` pour les arrêts normaux et trois checkpoints d'arrêt forcé vérifiés. Ces points ne couvrent pas toutes les pannes d'écriture ni les crashs en mouvement.

### Entretien et dégâts de speeder (P3-03, intégration partielle)
`SpeederPerformance` calcule les sorties des modules depuis leurs HP persistés : masse conservée, poussée/portance/capacité/stabilité proportionnelles à la santé. Le facteur moteur réduit aussi le plafond de vitesse ; une perte de moteur freine progressivement sans dépasser le plafond serveur. La masse configurée du pilote est ajoutée pendant la simulation. Plus aucun châssis fonctionnel = épave persistante ; siège détruit = pilotage refusé. L'entité n'est pas supprimée, son UUID et ses données restent réparables.

Les dégâts élémentaires sont produits sur serveur par les coups directs du propriétaire à portée/ligne de vue autorisées et par les impacts horizontaux pendant une conduite autorisée. Le module le plus proche du côté du frappeur prend le coup ; c'est une approximation de l'enveloppe commune, pas un raycast par face de module. Les impacts atteignent un châssis encore fonctionnel. Les permissions de modification, le taux, les bornes et les verrous d'entretien sont vérifiés. Les dégâts sur batterie plafonnent immédiatement sa charge restante ; une réparation ne crée jamais d'énergie. Pas encore de dégâts environnementaux généralisés, de démontage ni de butin de recyclage.

Le journal/reçu historiquement nommés `speeder_recharge` restent stables. Le schéma1 continue de représenter une recharge, sans réécriture de migration. Le schéma2 `kind=repair` ajoute un après-état de HP : exactement un module augmenté, charge inchangée. Les deux opérations partagent un seul reçu monotone par joueur et le même protocole gel/paiement/après-état durable/dégel/clôture ; aucun deuxième journal concurrent pour une réparation. Le plus abîmé est choisi côté serveur, égalités par ordre de modules du plan. DONE ne restaure ni une ancienne charge ni d'anciens HP après usage ultérieur. Le schéma2 est refusé sans mutation par un ancien lecteur ne connaissant que le schéma1.

Le HUD/rendu reçoivent les HP en lecture seule via les données synchronisées de l'entité. Les modules abîmés utilisent une surimpression rouge ; ils restent visibles, y compris les pièces détruites de l'épave. Aucun module n'est créé ou supprimé par la seule représentation client. Validation visuelle et réseau prolongée restent ouvertes.

### Évolution de schéma
Chaque structure enregistrée inclut `schemaVersion`. Lire anciennes versions avec migrations idempotentes ; en cas de format inconnu, refuser l'action dangereuse et conserver les données originales. Jamais supprimer silencieusement un sabre ou véhicule.

### Reprise des récompenses RPG (P1)
Le profil attachment reste l'état de gameplay du joueur. `rewardRevision` ordonne ses récompenses ; les profils antérieurs sans ce champ migrent à la révision 0. Sous `data/legacyoftheforce/rewards`, une intention temporaire précède toute mutation, un point de reprise par joueur conserve l'après-image de la dernière décision, et un reçu immuable par événement empêche un double paiement même après éviction de l'historique borné. Les reçus sont répartis dans 4096 répertoires ; aucune recherche globale n'est faite lors d'un kill.

Le point de reprise n'est pas un second profil modifiable : il n'est appliqué qu'à un attachment de révision plus ancienne. À révision égale, énergie, cooldowns et changements ultérieurs sont conservés. Ce mécanisme couvre aussi le chargement solo depuis `level.dat`, sans modifier les fichiers vanilla hors de leurs propres sauvegardes. Une copie/sauvegarde de monde doit donc conserver le dossier de reprise avec les données joueur. Les écritures sont bornées, fsync, remplacées atomiquement et relues ; les formats inconnus restent intacts et bloquent la récompense dangereuse. Profiling et croissance disque des reçus restent des sujets P7.

## Actions atomiques et prévention de duplication
- **Bloc soulevé** : validation → enregistrement transaction → retrait contrôlé du monde → création représentation mobile → déplacement → dépôt à un emplacement libre OU restauration/sécurisation au point source → clôture transaction. Prévoir crash/reconnexion.
- **Sabre/vehicule créé** : validation de toutes les pièces → consommation **une seule fois** sur serveur → création unique de l'item/entité → résultat de transaction ; jamais fabrication depuis un aperçu client.
- **Voyage** : interdire les doubles requêtes pendant un transfert, préserver inventaire et état RPG.

## Performance et cadence
- Budget configurable de blocs mobiles **par joueur** et **par dimension** ; mise à jour de physique limitée, interpolation client.
- Collisions simplifiées pour groupes de blocs (ne pas faire 32 appels de physique lourde à chaque tick).
- Aucune recherche globale de mobs/blocs dans chaque tick ; limiter rayon et fréquence.
- La génération de planète utilise registres/worldgen data-driven ; ne pas scanner intégralement une dimension.
- Les écrans 3D sont facultatifs côté faible configuration : aperçu simplifié possible.

## Dépendances externes
N'ajouter une bibliothèque (animation, rendu, permissions, intégration) que si elle a une version confirmée compatible avec la version cible, et documenter : licence, fonction, version, alternative sans dépendance. Aucun mod tiers obligatoire par défaut.

## Liens de référence versionnés
- https://docs.neoforged.net/docs/1.21.1/gettingstarted/
- https://docs.neoforged.net/docs/1.21.1/items/datacomponents/
- https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/
- https://docs.neoforged.net/docs/1.21.1/networking/
