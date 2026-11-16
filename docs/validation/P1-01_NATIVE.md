# P1-01 — Mort et reprise de profils RPG non triviaux

## Preuve du 16 novembre 2026

Source arrêtée ad4, copie complète sous session.lock : deux vrais personnages du parcours P4-05, niveau11, total800XP, XP courante85, alignement+250, révision1 et inventaires/composants effectivement fabriqués. Aucun nouveau niveau, objet ou gain accordé. Le monde source est conservé.

Fixture de cycle de vie déclarée : keepInventory=true, capacité137, régénération0 et fraction de mort0.37 dans le JVM de test uniquement. Ces valeurs non standard permettent de distinguer une vraie application de configuration d'un retour silencieux aux100points. Le serveur débite réellement17.75points : énergie82.25. Les deux clients reçoivent le snapshot S2C réel ; un observateur Netty transmet les messages sans remplacer le handler HUD.

rp1 : serveur19064, clients18256/21580, trois sorties0 sans force. Chaque client capture son HUD puis envoie seulement un accusé de lecture. Le serveur provoque de vrais dégâts genericKill létaux. DeathScreen affiché, attente du bouton activé puis onPress du bouton vanilla «Ressusciter», qui émet la requête native. Le nouvel objet ServerPlayer doit différer de l'ancien ; l'UUID reste identique. Le numéro d'entité réseau peut rester identique selon vanilla : il n'est pas utilisé comme preuve de remplacement.

Dans les deux vrais événements PlayerRespawnEvent, après le handler RPG : énergie50.69, capacité137. Aucun niveau/XP/alignement/historique/cooldown/révision perdu ; inventaires complets identiques sous keepInventory. Les snapshots reçus par les deux interfaces contiennent niveau11/800XP/+250 et50.69/137. Le HUD arrondit l'énergie à51, comme son format d'affichage existant. Barrière native puis fichiers vanilla relus ; checker indépendant PASS.

rr1 : copie arrêtée de rp1, nouveau serveur22004, nouveaux clients23732/19332, trois sorties0. Configurations de test identiques, aucune nouvelle dépense ni mort. Dès la vraie connexion, avant accusé : le profil complet et l'inventaire chargé doivent être égaux au checkpoint après mort de rp1. Nouveau snapshot reçu50.69/137, même état dans playerdata final ; checker PASS avec --recovery. Pas de récompense rejouée ni de révision RPG nouvelle.

## Reproduire

```powershell
# Source : parcours complet arrêté ad4 ou équivalent ayant conservé les preuves.
./scripts/test-adventure-preparation.ps1 -Run mortneuve -Mode Progress -PreparationRun ad4
./scripts/test-adventure-preparation.ps1 -Run repriseneuve -Mode ProgressRecovery -PreparationRun mortneuve
python scripts/check-progress-lifecycle.py mortneuve --source ad4 --recovery repriseneuve
```

Noms de run neufs, aucun Gradle parallèle. Préfixes run-adventure-*, profils natifs et preuves sous validation/ ; résultats .tools/adventure-progress-<run>-result.json et adventure-progressrecovery-<run>-result.json. Les scripts refusent une source encore verrouillée, ne suppriment pas les anciennes preuves et gardent codes de sortie/logs. Pilotes uniquement gameTest, hors distribution.

Images framebuffer spent/death/respawned de rp1 et reloaded de rr1 inspectées : niveau11/XP85, énergie82 puis51, écran natif et mêmes slots. Résolution854×480, FR, GUI2. Les notifications vanilla de connexion offline recouvrent temporairement la partie droite du HUD ; ce test n'atteste donc pas la lisibilité de tous ses détails simultanément. Cette limite visuelle reste une finition P7 ; aucun faux snapshot n'est déduit du rendu.

## Consolidation de P1-01

Migration/format futur/corrompu et roundtrip non trivial : tests PlayerProgress déjà exécutés ; solo intégré et rechargement au niveau100 : P1-04 et P1_SOLO_BLOCK_EVIDENCE ; véritable sélection du checkpoint propriétaire après kill/reprise : SOLO_OWNER_SAVE. Réseau et sens des snapshots : P0_NETWORK. Le scénario rp1→rr1 ferme la lacune de vrais profils non triviaux après mort/reconnexion à deux clients, sans répéter les anciens incidents d'activation Windows.

P1-01 validé pour le modèle persistant, migration/synchronisation, énergie et HUD minimal. Ce scénario est explicitement keepInventory=true : il ne certifie pas les drops sous règle false, chaque système d'objets et ses pannes, tous les mods tiers ni une charge publique. Aucun gain de gameplay n'est obtenu par ces commandes de test hors du harnais. P1 global, arbres de compétences et V1.0 restent ouverts.

Le code de production n'a pas changé pendant cette tranche : build final réussi, JAR F1F94340A2CE2BE9775962530E6E8324FDE974DAAE2B17F722ACAD2EB82FCBDC identique à jarcp1 ; sa vérification de distribution relancée avec les nouveaux noms de harnais confirme leur absence (PASS). La régression de référence du même code de production compte127JUnit et102GT requis verts ; compilation des deux nouveaux pilotes et exécution native supplémentaire réussies.
