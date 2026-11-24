# 01 — Architecture, interfaces et sauvegardes

### Curseur externe et clics coffre/tonneau — 23 novembre 2026

Cette section actualise le contrat personnel ci-dessous. `legacyoftheforce:personal_menu` est maintenant en version2 ; la lecture1 reste compatible. Les emplacements0–4 gardent le curseur personnel et les quatre entrées2×2. L'emplacement5 contient le curseur externe ou un objet en récupération différée après fermeture pleine. Un objet différé revient dans une place principale libre après sauvegarde native vérifiée ; tant qu'il reste sans place, les nouvelles manipulations externes sont refusées. Les formats inconnus restent conservés, sans migration destructive.

`SaberStorageTransactions` encadre les clics vanilla de `ChestMenu` visant un coffre simple/double ou un tonneau, lorsque l'agrégat contient un sabre. Avant mutation : image joueur et conteneurs, identités persistantes des blocs, sauvegardes natives vérifiées. Après mutation : conservation de tous les stacks et composants, intention PREPARED sous `data/legacyoftheforce/saber_storage`, reçu `saber_storage_receipt` avec l'image joueur, HELD, écriture native des conteneurs puis CLOSED. Un clic de double coffre possède deux endpoints et un seul reçu. Aucun endpoint réseau supplémentaire.

Une reprise attend que le propriétaire soit absent de PlayerList, choisit avant/après d'après son reçu natif, contrôle son agrégat sans réécrire son ancien inventaire, puis réconcilie les conteneurs identifiés. Un bloc remplacé, un contenu divergent, une version inconnue ou une erreur de stockage conserve le journal et le verrou. Les accès vanilla/capabilities et les protections de casse/explosion refusent les modifications d'un conteneur verrouillé. Les transferts automatiques entre conteneurs ne disposent pas encore de leur propre journal.

Un dépôt au sol depuis le coffre effectue d'abord le retrait durable vers la récupération personnelle, puis utilise le journal sabre/sol existant avec la même ItemEntity. Une erreur après validation du retrait ne restaure jamais l'ancien coffre. `core.api.SaberStorageCheckpoint` est une notification immuable des frontières, non annulable et sans promesse de livraison unique. Six mixins de sabres sont requis, en plus des trois mixins d'arène. Preuves et limites : `validation/P2-01_STORAGE.md` ; les parcours natifs à deux clients et les interruptions de ce nouveau stockage restent à valider.

### Transferts sabre/sol — correctif du 23 novembre 2026

Actualisation inventaire personnel : `SaberMenuState` ajoute `legacyoftheforce:personal_menu` schemaVersion1 dans le même playerdata natif que l'inventaire/reçu. Seuls le curseur personnel et les quatre entrées2×2 sont enregistrés ; jamais le résultat de recette. Les images chargées non vides sont conservées même si un save précède leur restauration au login. Format futur/corrompu : original opaque conservé, connexion refusée, pas de conversion destructive. Ancien fichier sans ce champ : menu vide compatible.

`SaberGuiDrops` encadre le clic vanilla personnel et retient la même ItemEntity jusqu'au retour du caller. Avant-image complète sauvegardée/vérifiée, état final remis en mémoire, puis journal/reçu existant. Échec avant intention : état local avant clic rétabli ; après intention : déconnexion et reprise depuis les fichiers, sans restauration d'ancien inventaire. Le comptage de reprise inclut les cinq emplacements temporaires. Pickup personnel avec curseur/grille non vides : image temporaire et paiement sauvés ensemble, refus si la même identité existe déjà au curseur/grille.

Fermeture personnelle vivante : si vanilla veut jeter un débordement, les publications sont retenues et les objets restent au curseur/grille dans l'agrégat sauvegardé, avec message et récupération en rouvrant l'inventaire. Si tout rentre, déplacement vanilla vers l'inventaire puis vérification native. La fermeture fonctionne sans effacer les objets d'un inventaire plein. Aucun changement de politique des acteurs morts ni garantie ajoutée sur les menus externes. Trois mixins personnels requis, en configuration séparée de ceux des arènes ; aucun code client chargé par le dédié. Validation : `validation/P2-01_PERSONAL_MENU.md`. Les limitations historiques plus bas sont remplacées pour le seul menu personnel.

`lightsaber.SaberItemTransfers` coordonne le Q, les dépôts personnels encadrés ci-dessus et le pickup de sabres identifiés avec `core.operation.DurableOperationStore` et les writers natifs vérifiés. Intention PREPARED, reçu `saber_transfer_receipt` enregistré dans le même playerdata que l'inventaire/menu personnel, HELD après vérification, puis publication/retrait durable de l'ItemEntity avant fermeture. Aucun nouvel endpoint client. Reprise bornée serveur/offline à partir du reçu et du nombre d'identités, sans restaurer une ancienne image d'inventaire. Inconnus/conflits restent conservés ; un objet engagé ne ticke pas tant que le journal reste ouvert. Configuration de budget/retry dans le TOML sabre. Les menus externes ne disposent pas encore de ce chemin transactionnel.

Le correctif ferme la duplication sd1 sur sa répétition sd2 ; rapport `validation/P2-01_TRANSFERS.md`. Cinq frontières DROP et cinq PICKUP ont ensuite subi des kills réels et des reprises à deux clients : `validation/P2-01_CHECKPOINTS.md`. Les autres transferts (mortfalse, conteneurs/hoppers, cristaux libres) restent à exercer avant garantie globale des objets rares.

`core.api.SaberTransferCheckpoint` notifie synchroniquement les frontières PREPARED, PAYMENT_DURABLE, HELD, ENTITY_DURABLE et CLOSED, après les écritures correspondantes. Événement immuable, non annulable, sans objet joueur ni payload NBT exposé ; aucune nouvelle requête réseau. CLOSED peut être notifié à nouveau pendant une reprise : ce contrat n'est pas une livraison exactement une fois ni une source de récompenses. Le harnais de crash est exclusivement dans gameTest et n'est pas distribué.

La reprise conserve au maximum un ticket de chunk dédié à l'opération en attente du chargement asynchrone des entités. Elle réessaie au tick pendant ce bail, puis libère le ticket à fermeture, échec ou arrêt. `transfer.chunkLoadTimeoutTicks` vaut200 par défaut (20–2400) ; à expiration, le journal reste conservé et les autres opérations peuvent être traitées. Le ticket UNKNOWN temporaire de getChunkAt ne suffisait pas à cette attente sans joueur ; le cas réel str3 et sa reprise corrigée str3b sont tracés dans le rapport.

Refus de partage Windows : neuf barrières DROP/PICKUP avec vrais handles et relecture native, puis Q à deux sockets avant/après paiement et reprise dans une nouvelle JVM ; `validation/P2-01_WINDOWS_IO.md`. Le propriétaire incertain doit être absent de PlayerList avant la reprise : PlayerLoggedOutEvent seul précède encore sa dernière sauvegarde vanilla et ne prouve pas qu'elle soit terminée. Le coordinateur existant attend bien cette absence et utilise le reçu réellement stocké, même si une dernière sauvegarde finit le paiement. Les fixtures n'entrent pas dans le JAR et ne remplacent pas les gates GUI/mortfalse/conteneurs/hoppers.

Avant-image de dépôt récente : le stack retiré par Q est temporairement reconstitué dans une place libre, agrégat natif sauvegardé/vérifié avant PREPARED, puis état postQ exact retrouvé. Un refus avant cette frontière garde le stack. Pour un pickup non payé, la reprise préserve aussi la source depuis le journal si sa première sauvegarde d'entité n'avait pas eu lieu ; elle ne ferme pas une intention en perdant cette seule source. Preuves et nouveau serveur/collecte : `validation/P2-01_PICKUP_RECOVERY.md` (sd4→sp2).

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

P3-04 : les entités de véhicule publient `VehicleForceTargetProvider` qui fournit un `ForceTargetAdapter`. Le contrôleur Force ne dépend d'aucune classe interne speeder. Actions `STABILIZE`/`BRAKE` distinctes de PUSH/PULL ; l'adaptateur ne prend en charge que les opérations qu'il autorise via canManipulate. Les méthodes d'impulsion/amortissement non prises en charge échouent explicitement. Le module speeder décide de la mutation de sa vitesse autoritaire et valide de nouveau propriétaire, occupation, verrou, stockage et droits de l'enveloppe ; Force choisit la cible visible, vérifie niveau/masse/énergie et paie le pouvoir RPG partagé. Opération ponctuelle sans canal sauvegardé, ni déplacement arbitraire ni émission de dégâts. Cooldown `legacyoftheforce:vehicle_control` sauvegardé dans le profil RPG ; état d'instance schéma1 reste inchangé.
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

Le schéma3 `kind=paint` du même journal ajoute une cible de plan dont **une seule** zone primaire/secondaire diffère. UUID/auteur/nom/grille/modules/extensions sont comparés exactement ; HP et énergie restent inchangés. Une teinture choisie parmi les16 couleurs vanilla est payée côté serveur, au tarif configurable `garage.dyePerPaint`. Le plan papier n'est pas modifié par une peinture d'instance ; sa palette se règle séparément dans le brouillon puis Enregistrer. La couleur du plan s'applique à la fabrication neuve, dont la facture reste celle des modules. Le repeint d'une instance utilise le paiement durable commun, sans journal parallèle. Les lecteurs1/2 restent pris en charge, sans réécriture de leurs formats.

Le schéma4 `kind=recycle` utilise ce même reçu pour retirer une épave : aucun item présent ne peut être retiré/remplacé et seuls les36 emplacements principaux reçoivent de nouvelles matières. Un butin nul est possible, avec inventaire inchangé et reçu incrémenté. Le serveur fige le devis, gèle l'épave, sauvegarde/relit le joueur crédité, puis écrit une archive immuable par UUID sous `data/legacyoftheforce/retired_speeders`. Cette archive schéma1 contient transaction PAID et sérialisation native entière gelée, comparées exactement à l'état initial. Après archive durable, suppression réelle et barrière `VerifiedEntitySaveBarrier.persistAbsence` sur le writer Minecraft, puis APPLIED/DONE. Une reprise ne recrée jamais l'épave ni ne recrédite le joueur.

Le journal d'entretien par propriétaire peut être remplacé lors d'un entretien suivant ; les archives d'UUID restent donc permanentes et font partie de la sauvegarde du monde. Une ancienne copie chargée d'un UUID retiré ne peut être pilotée/entretenue et est supprimée sans butin ; une archive inconnue/incohérente préserve l'entité en lecture seule. Cache par entité invalidé par un compteur d'archives du serveur : pas de parcours global ni d'accès disque par tick. État véhicule ou stockage/attachments inconnus refusés avant crédit. Inventaire plein : aucune opération, aucun jet d'items au sol. Les coffres/modules futurs doivent ajouter leur propre transfert avant d'être recyclables. Tous les formats historiques1–3 restent lisibles.

`SpeederColors` est exclusivement client : piles d'items temporaires munies du composant vanilla `DYED_COLOR`, faces `tintindex=0` sur les corps des modèles et gestionnaire de couleurs client. Châssis/siège utilisent la primaire, moteur/batterie la secondaire ; connecteurs et accents métalliques gardent leur texture. Ces piles ne sont jamais insérées dans l'inventaire. Le monde projette son propre plan de l'instance ; aucune valeur RGB client n'est acceptée comme état autoritaire. Le rendu est branché mais pas encore observé graphiquement.

### Évolution de schéma
Chaque structure enregistrée inclut `schemaVersion`. Lire anciennes versions avec migrations idempotentes ; en cas de format inconnu, refuser l'action dangereuse et conserver les données originales. Jamais supprimer silencieusement un sabre ou véhicule.

### Reprise des récompenses RPG (P1)
Le profil attachment reste l'état de gameplay du joueur. `rewardRevision` ordonne ses récompenses ; les profils antérieurs sans ce champ migrent à la révision 0. Sous `data/legacyoftheforce/rewards`, une intention temporaire précède toute mutation, un point de reprise par joueur conserve l'après-image de la dernière décision, et un reçu immuable par événement empêche un double paiement même après éviction de l'historique borné. Les reçus sont répartis dans 4096 répertoires ; aucune recherche globale n'est faite lors d'un kill.

Le point de reprise n'est pas un second profil modifiable : il n'est appliqué qu'à un attachment de révision plus ancienne. À révision égale, énergie, cooldowns et changements ultérieurs sont conservés. Ce mécanisme couvre aussi le chargement solo depuis `level.dat`, sans modifier les fichiers vanilla hors de leurs propres sauvegardes. Une copie/sauvegarde de monde doit donc conserver le dossier de reprise avec les données joueur. Les écritures sont bornées, fsync, remplacées atomiquement et relues ; les formats inconnus restent intacts et bloquent la récompense dangereuse. Profiling et croissance disque des reçus restent des sujets P7.

## Actions atomiques et prévention de duplication
### Arènes d'Ilum P4-03 (validation en cours)
`BossArenaSites` fournit les autels à worldgen sans import interne de bosses. `WorldProtectionHooks` compose les politiques globales avec les adaptateurs existants ; exceptions refusées. `MeleeParried` et `ForceImpulseResistance` permettent le contre sans couplage combat/IA. Rendu et modèles restent sous client.

`BossArenaService` coordonne états d'arène et `DurableOperationStore` de victoires : intention, reçus RPG individuels, publication estampillée dans le conteneur natif avec barrière de chunk, cooldown. Les nouveaux reçus RPG enregistrent le booléen accepted ; les anciens formats restent lisibles, sans inventer un verdict manquant. Lecture des reçus hors ligne, mutation RPG toujours sur joueur serveur. `BossDefeated` porte un UUID stable ; une reprise peut rediffuser cet événement, les consommateurs doivent dédupliquer.

Le retrait passe par `BossLootMenu` et `BossLootService` : collecte intégrale vers l'inventaire principal, sans curseur ni insertion. Avant-image joueur native → journal PREPARED → verrou d'autel durable → inventaire/reçu joueur durables → DELIVERED → autel vidé durablement, encore verrouillé → DONE → déverrouillage durable. Reprise fondée sur le reçu natif, jamais sur un booléen client ; reçu fermé ne réapplique aucun inventaire. L'attachment `boss_loot_receipt` est copié à la mort. Avant verrou durable, la collecte légitime d'un pair n'est pas rembobinée lors de l'annulation de l'ancien propriétaire. Onze checkpoints et sept vrais kills/reprises vérifiés en dédié, duels/collectes avec deux vrais clients et en solo intégré, puis copie solo arrêtée rechargée, également vérifiés. Pannes OS restantes ouvertes : validation/P4-03_LOOT.md, validation/P4-03_NETWORK.md et validation/P4-03_SOLO.md.

L'accès au conteneur utilise `mayUseContainer`/`allowsContainer`, distinct de l'édition de blocs et de l'arrivée. Adaptateur manquant/en erreur = refus ; les intégrations de claims doivent implémenter explicitement cette permission. La protection des arènes autorise ce contrat général, tandis que le service de boss vérifie les bénéficiaires de la victoire et les verrous.

Avant de créer une intention de spawn, l'autel exige stockage d'entités chargé et position entity-ticking. Des blocs présents ne suffisent pas à garantir la publication d'une entité native ; en attente, refus temporaire avec message, sans fichier d'arène ni suspension globale. `IlumBossEntity.move` limite le déplacement demandé au volume de son arène via `ArenaMovement` et annule seulement les composantes bloquées ; aucune restauration de santé ou téléportation. Pour les mobs poussés, le test d'obstacle initial rejoint celui de beforeTick : la descente de gravité est résolue par vanilla, le sweep complet de permissions reste vérifié. Preuves réseau et régressions : validation/P4-03_NETWORK.md.

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
### Checkpoint natif du propriétaire solo
Chaque `VerifiedPlayerSaveBarrier` avance l'attachment core `save_checkpoint` (schéma1, génération long monotone), sauvegarde par vanilla, force le fichier et vérifie cette génération relue. Ce compteur ne remplace ni inventaire ni reçu métier ; épuisement/schéma invalide refusés. Données antérieures sans compteur = génération0. Copie à la mort, sans projection client nécessaire.

Le chargeur vanilla privilégie le propriétaire embarqué dans `level.dat`. Sur `LoadFromFile` avant placement, core compare les générations : fichier natif du même UUID strictement supérieur → recharger l'agrégat complet avec DataFixTypes.PLAYER, puis mettre à jour le compound que PlayerList retourne pour Dimension/Pos. Génération égale/inférieure → sélection vanilla conservée. Ni comparaison des heures fichiers (level.dat est réécrit durant l'ouverture), ni seconde écriture d'inventaire. Fichier borné, identité et schéma vérifiés ; erreur = refus de réconciliation, pas retour silencieux à un checkpoint financier ancien. Dédié et invités LAN gardent leur chargeur natif. Ce mécanisme protège les nouvelles barrières ; il ne reconstruit pas l'ordre des anciennes sauvegardes sans compteur. Preuves/limites : validation/SOLO_OWNER_SAVE.md.

### Voyages P4-02
À la reconnexion, `recoverLogin` réconcilie d'abord le reçu. Un reçu DONE ne restaure aucune ancienne image ; si le volume courant est occupé près de la dernière destination, hors monture, une recherche locale autour de la position actuelle peut relocaliser et sauvegarder l'image actuelle sans débit ni modification du reçu/bookmark. Sans place autorisée, refus ; hors zone ou sans occupation, ni déplacement ni écriture supplémentaire. Cela couvre un voyage achevé pendant la fermeture socket, dont l'emplacement a ensuite été pris par un autre arrivant. Cas et limites dans validation/P4-02_DISCONNECT.md.

L'atlas personnel est un attachment immuable copié à la mort. Le journal atomique par joueur sous data/legacyoftheforce/travel coordonne le fichier natif d'inventaire/atlas avec la téléportation native ; pas de writer concurrent de playerdata. PREPARED/PAID/ARRIVED/DONE ou ABORTED impayé ; versions/budgets/débits et images vérifiés. Une arrivée ARRIVED encore ouverte est revérifiée avant clôture, y compris après activation native durable : terrain/droits périmés imposent une nouvelle arrivée sûre et durable avec la même image payée, sinon journal/fichier joueur conservés. DONE ne restaure aucun ancien inventaire/emplacement. Arrivée sans terrain modifié, contrat core.api mayArrive/allowsArrival distinct de mayModify ; adaptateur non implémenté = refus. Événements post-commit PlanetDiscovered/PlanetTravelCompleted avec opération UUID, sans garantie de livraison exactement une fois ; consommateurs durables à réconcilier avec l'atlas. Détails et gates : validation/P4-02.md et validation/P4-02_CRASH.md.

- https://docs.neoforged.net/docs/1.21.1/gettingstarted/
- https://docs.neoforged.net/docs/1.21.1/items/datacomponents/
- https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/
- https://docs.neoforged.net/docs/1.21.1/networking/

P4-03 : checkpoints du coordinateur d'arène via consommateur package-private, no-op par défaut. Seize vrais kills/reprises des publications/victoires vérifiés ; la reprise distingue absence d'intention (reset sans récompense) et intention durable (règlement idempotent). Un fichier joueur encore ancien est restauré par le checkpoint RPG avant le contrôle final, sans répéter les gains. Preuves/protocole et limites OS : validation/P4-03_ARENA_CRASH.md.

### Protection des fluides et prédictions — P4-03
FluidPlaceBlockEvent n’intercepte pas les chemins ordinaires de BucketItem/FlowingFluid dans21.1.265. Trois mixins communs requis refusent avant la mutation : emptyContents, spreadTo et pickupBlock (eau/poudre). La requête d’arène est serveur seulement ; le client garde sa prédiction vanilla, corrigée par les paquets autoritaires de bloc/inventaire au refus. Ce dernier envoi est indispensable : useItem retourne tôt lorsque le stack serveur est inchangé. Message limité, pas de nouveau writer d’objet. Les handlers existants restent pour conversions, explosions/pistons et interactions de blocs.
À la levée du verrou, planifier seulement les fluides existants dans l’arène/halo chargés (968positions max). Réveil une fois au reset/victoire et quand un COOLDOWN d’un ancien boot est chargé ; réutilisation du bootUUID, schéma1 compatible, aucune restauration de blocs ni I/O par tick. Preuves et limites dans validation/P4-03_FLUIDS.md ; JAR/mixins réellement chargés hors environnement de développement dans validation/PACKAGED_SERVER.md.

### Remplacement atomique sur stockage Windows
AtomicNbtFile conserve fsync, ATOMIC_MOVE+REPLACE_EXISTING et relecture bornée. Seul AccessDeniedException déclenche trois reprises (pauses demandées 2/8/20 ms) ; refus permanent ou déplacement atomique indisponible restent des erreurs. Aucun repli par copie. InterruptedIOException conserve le drapeau d'interruption. Tests de vrais handles Windows et lecture concurrente : validation/CORE_ATOMIC_REPLACE.md.

### Connaissances optionnelles P4-04
Quests ne dépend d’aucune classe interne de planets/lightsaber/RPG. Le port core.api CrystalIdentityAccess lit l’identité validée d’un vrai item cristal/sabre, fourni par lightsaber ; aucune mutation. État personnel immuable quest_knowledge, copie à la mort, révisions monotones et schéma1. Possession réconciliée au login/changement de dimension/ouverture ; pickup natif met en attente une réconciliation en fin de tick, après retrait de l’entité. La quête ne change aucun objet et ne rend pas la collecte vanilla transactionnelle face à une panne pendant le pickup. Sauvegarde native VerifiedPlayerSaveBarrier avant notification stable HolocronDiscovered ; consommateurs durables doivent réconcilier l’état (fenêtre crash entre sauvegarde et événement). Pas de garantie de livraison exactement une fois.
Le journal est un snapshot serveur borné, transporté par le menu étendu NeoForge natif (≤48lignes de clés≤128caractères). Aucun bouton serveur n’accorde de progression ; navigation locale seulement. `/lotf journal` est limité en fréquence et ne requiert ni objet ni proximité ; la découverte initiale exige bloc réel, proximité et permission mayUseContainer.

Reload des guides : un nouveau snapshot utilise une seule image immuable du catalogue. Définitions absentes/invalides : information d'archive indisponible, IDs personnels conservés, clic sans nouvelle découverte. Aucun menu déjà ouvert ne devient autorité de progression ; rouvrir actualise les textes et jalons. Protocole et preuve native qc2 : validation/P4-04.md.

DurableOperationStore partage également AtomicNbtFile pour lecture et publication des enveloppes de journal (budget262144, phases et validation conservées). Il ne possède plus une seconde voie de renommage direct sans reprises. Refus permanent : journal antérieur conservé, coordinateur métier suspendu/récupérable.

P1-03 : AlignmentThresholds est une valeur immuable pure de core.api. ServerConfig valide ensemble les quatre seuils de présentation ; ProgressCommands classe uniquement sur le serveur et transmet une clé de traduction imbriquée dans le message vanilla. Aucun nouveau paquet, champ de sauvegarde ou writer RPG ; PowerRequirement garde ses bornes propres indépendantes.

## Extension planétaire et rencontres — lots de développement en attente de campagne

TravelState et TravelTransaction lisent les formats historiques1 ; les nouveaux atlas/journaux2 portent les découvertes/destination parmi les six IDs contractuels. La préparation historique ne réécrit pas les images d’une opération Ilum en attente. BossLootTransaction1 reste scellé sur Ilum ; le2 porte explicitement la dimension prise en charge par BossKind et la reprise recherche son autel dans cette dimension. Les coordonnées identiques de deux planètes ne désignent pas le même coffre. Les reçus restent monotones par propriétaire et les inventaires sont projetés/préflightés avant publication native.

Les six premières rencontres utilisent la machine de rencontre/attribution commune, avec stratégies TatooineCombat/EndorCombat et sous-états versionnés dans lotfBoss. Les identités Ilum et anciens ordinaux d’attaque restent stables ; nouvelles actions ajoutées à la fin. Les futures données de boss/pièces sont conservées et inactives. L’objet d’arène historique arena_ice conserve lotfArenaIce1 et ajoute2 pour les matériaux scellés grès/bois ; arena_illusion utilise lotfIllusion1, sans vie ni gain de mort. Ces objets sont temporaires/non exportables et rattachés à un vrai propriétaire de rencontre, pas à des blocs gratuits.

Le marqueur core.api.EncounterBoss exclut les boss des récompenses RPG de mort ordinaire, même si un datapack change boss_mobs. Les récompenses dédiées restent attribuées par événement de victoire/acteur/boss, avec refroidissements. Les fabriques core.api.SaberDesigns et PlanetaryCrystals sont installées par la racine de composition : les plans ne contiennent ni cristal ni identité possédée ; un cristal d’expédition reçoit une UUID déterministe liée à la victoire, ses qualités sont bornées par la configuration. Les producteurs de butin ne dépendent pas des internes de lightsaber. Les preuves de tests antérieures aux lots ne couvrent pas ces extensions ; campagnes définies dans09 et états dans03.
