# P4-03 — Deux clients natifs, duels et collecte

16 novembre 2026. **Validation réseau de cette tranche réalisée ; P4-03 et V1.0 restent en cours.**

## Parcours effectivement exécuté

`test-boss-network.ps1 -Run bn6` lance un dédié NeoForge et deux JVM Minecraft séparées. Serveur PID13984 ; ForceTesterOne PID10560 et ForceTesterTwo PID4520. Deux vraies sockets loopback distinctes, mêmes objets ServerPlayer et connexions maintenus durant le parcours. Aucune connexion embedded dans ce test.

Le serveur trouve le temple Jedi et le sanctuaire Sith réellement générés, sans modifier leurs sols, piliers, lanternes ou toitures. Les joueurs y sont placés avant chaque duel. Fixtures déclarées : progression initiale Force15/1232XP, sabres bleus éveillés, armures diamant et seize pommes dorées chacun. Les spawns ordinaires sont désactivés pour isoler les boss et l'attente du chargement des clients ; difficulté normale conservée. Ni soin serveur, ni dégâts injectés, ni téléportation des joueurs pendant les combats. Les déplacements, attaques, consommations et collectes viennent des contrôles/API du vrai client : touches de mouvement, attaque/usage vanilla, commandes publiques de parade et de Force, clics de menu natifs. La nourriture est consommée normalement, pas remplacée par une augmentation de santé serveur.

Les drivers observent les données réellement synchronisées : entités et santé, phases, préparation d'attaque, glace, menus et inventaires. Ils attendent l'apparition du décor et la libération de la signature avant de terminer le boss. Leur visée automatisée et l'équipement fourni ne constituent pas une mesure de difficulté pour un joueur débutant.

| Résultat serveur | Gardien Jedi | Seigneur du Givre |
|---|---:|---:|
| Contributeurs qualifiés |2|2|
| Phases observées serveur et chaque client |1,2,3|1,2,3|
| Parades réellement résolues |4|4|
| Contres de Force payés sur le boss |2|3|
| Coups réellement reçus par la glace temporaire |12|5|
| Signature libérée |Frappe lourde|Onde glacée|
| Récompense personnelle |800XP/−25|800XP/+25|

Gardien validé à01:46:36, Sith à01:48:30 ; preuve finale serveur01:48:35. Chaque joueur termine à2832XP, soit+1600, avec alignement0 après les deux variations. Deux reliques et huit cellules au total, **partagées**, source de chaque autel vide/déverrouillée. Deux UUID de collecte distincts ; reçus joueur cohérents, aucun journal de victoire encore ouvert. Identités des sabres/cristaux et pièces initiales conservées, pratique Kyber réelle acquise. Aucun nouveau sabre/cristal livré par le harnais durant le combat.

Après la preuve, les deux clients restent connectés quinze secondes supplémentaires. Arrêt normal du serveur et des deux clients, tous exit0. Les deux clients écrivent chacun une preuve autonome après observation des deux combats et réception de l'inventaire final. Aucun processus de test laissé actif.

## Recoupement indépendant

`check-boss-network.py bn6` passe. Le script lit séparément résultat du lanceur, preuve serveur, preuves des deux JVM, journaux clients/serveur, playerdata final, reçus RPG, derniers journaux de collecte, régions de blocs et d'entités et départs de structures. Il compare UUID/identités/socket/PID, inventaires client/serveur/fichier, XP/alignement, deux reçus personnels par boss, quantité partagée, révisions de collecte, autels estampillés sans verrou, absence des boss morts dans les régions natives, et géométrie des salles non modifiée. Les états client ne servent jamais à attribuer des dégâts ou de l'XP.

Preuves locales ignorées par Git : `run-boss-bn6/world/validation/boss-network-server.nbt`, `run-boss-network-client-{one,two}-bn6/validation/boss-network-client.nbt`, `.tools/boss-network-bn6-result.json` et logs associés. Aucun profil joueur ni inventaire de test publié.

## Défauts de gameplay reproduits et corrigés

- **Contact du sol et Force.** Un mob résistant conserve une vitesse verticale de gravité négative ; le test d'obstacle initial incluait alors le sol et refusait l'impulsion horizontale. Nouveau GameTest échoue avant correction avec blocked_path ; la correction utilise le même sweep d'obstacle que beforeTick, sans descente, en conservant le sweep complet de permissions. Vanilla résout toujours la collision avec le sol. Le test prouve énergie débitée, déplacement horizontal réel, hauteur préservée et sol inchangé.
- **Limite de l'arène.** Avant correction, un déplacement natif après une Force acceptée peut faire franchir la limite au volume du boss, provoquant une réinitialisation malgré des joueurs présents. `bound3` reproduit l'échec avant correction. `ArenaMovement` borne maintenant le déplacement natif du corps entier ; il n'augmente aucune composante, ne téléporte pas et ne soigne pas. Les composantes de vitesse bloquées sont annulées et synchronisées. Le reset d'une véritable pose déjà invalide reste présent. Deux JUnit,500vecteurs et cas hors zone/non finis ; test dédié avec posture préparée, vraie dépense de Force et mouvement natif, puis autorité conservée. La préparation de posture dans ce test n'est pas comptée comme une parade réelle supplémentaire.
- **Activation pendant chargement.** `bn3` atteint addFreshEntity avant que la visibilité/le stockage des entités de la salle soient prêts ; la barrière native refuse et les arènes se suspendent. L'autel attend désormais `areEntitiesLoaded` et `isPositionEntityTicking` avant toute intention de spawn, avec message de chargement et possibilité de réessayer. La barrière de sauvegarde reste stricte. Le test dédié masque explicitement la visibilité native : aucun boss/fichier d'arène créé ; après retour TICKING, le même service publie un seul boss durable. Les vrais clients de bn6 passent aussi cette préparation.

## Essais non retenus et corrections du harnais

- `bn1` : reset observé durant le premier duel ; pas de preuve de victoire complète. Ce reset a motivé la régression isolée de déplacement, sans prétendre disposer d'une capture de sa cause exacte.
- `bn2` : victoire Jedi avec deux contributeurs, quatre parades et un contre ; le driver reste au mur hors portée de l'autel. Retour à pied ajouté. Essai partiel arrêté par fermeture normale d'un client, puis arrêt du serveur et du pair, tous0. Capture graphique du temple/sabre/HUD et données conservées ; pas un succès du parcours entier.
- `bound1/2` : préparation de limite refusée à cause de la gravité négative avant d'atteindre l'assertion de déplacement. Cas isolés : vitesse initiale nulle dans la fixture de limite, et test indépendant de contact du sol. Aucune assertion affaiblie.
- `bn3` : vraie course de disponibilité d'entité décrite ci-dessus ; pas de réussite revendiquée.
- `bn4` : deux boss vaincus et première collecte réussie, mais un client mange en continu, cesse de contribuer trop longtemps et perd légitimement son éligibilité de600ticks. Le driver relâche maintenant l'usage après une pomme ou40ticks ; **la règle d'éligibilité n'a pas changé**. Le listener d'observation ne jette plus d'exception dans l'événement métier ; les assertions restent dans le coordinateur de test, sans interrompre la transaction de production.
- `bn5` : premier client tué par un slime dans l'Overworld pendant le chargement du second, avant préparation du duel. Spawns ordinaires désactivés dans la seule fixture réseau ; second client encore en chargement arrêté par le nettoyage, exit−1 tracé, essai non validé.
- `postnet1` : une attente synchrone des chunks de fixture bloque l'avancement nécessaire de leurs tickets/visibilité. Remplacée par une séquence GameTest qui laisse passer les vrais ticks serveur et attend les mêmes conditions que le produit ; aucune manipulation de statut pour rendre les combats « prêts ». Le seul statut masqué/restauré explicitement concerne le test négatif de chargement.

## Régression et reproduction

`postnet2` PID23880 : **sept scénarios dédiés passent** — deux combats naturels, sécurité, cooldown, collecte à onze checkpoints/concurrence, limite et chargement. Checkers boss/loot PASS ; preuves de limite et de chargement relues et contrôlées. Régression finale `test runGameTestServer build` : **112 JUnit sans échec/erreur/skip,98GameTests requis passent02:01:39, build31s**. Harnais exclus du JAR.

JAR de développement SHA256 **54C18F0EA2DE48180B2D1BC28D82BAA09CA1715FEEB07950914033138A7ACFCE**.

```powershell
./scripts/test-boss-network.ps1 -Run nouveaunet
python scripts/check-boss-network.py nouveaunet
./scripts/test-bosses.ps1 -Run nouveaureg
python scripts/check-bosses.py nouveaureg --natural
python scripts/check-boss-loot.py nouveaureg
./scripts/gradle.ps1 test runGameTestServer build --console=plain
```

Toujours un identifiant neuf. Ne pas lancer Gradle en parallèle. Le test utilise uniquement localhost25588 et deux JRE clients déjà installés ; les profils sont séparés. Aucune classe de driver/validation distribuée.

## Restant avant P4/V1

Solo intégré avec ces duels et collecte, aventure complète depuis le monde normal sans équipement fourni, autres checkpoints de spawn/victoire et cycles mort/dimension, récupération opérateur/hors ligne, claims tiers et autres vecteurs de protection, configurations extrêmes et profiling. Les limites chiffrées/valeurs encore constantes et l'appréciation humaine du combat/son/accessibilité restent tracées. Les tests précédents de kills du butin restent distincts de cette session réseau continue ; aucune coupure réseau ou panne disque supplémentaire n'est revendiquée ici. Dix boss/cinq planètes et la quête/boucle P4-04/05 restent à développer.
