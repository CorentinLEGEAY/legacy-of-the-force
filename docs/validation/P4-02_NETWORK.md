# P4-02 — Clients natifs et serveur intégré

## Périmètre vérifié le 2026-11-15

`net3` : deux processus Minecraft natifs, ForceTesterOne PID23456 et ForceTesterTwo PID2564, connectés simultanément par sockets loopback au serveur dédié25587. Tous les processus se ferment normalement, code0 ; le serveur sauvegarde ses quatre dimensions à20:59:38. Les clients restent connectés quinze secondes supplémentaires après les preuves. Pas de crash natif dans ces profils.

`solo4` : vrai client PID24124 et son serveur intégré, connexion mémoire native ; même parcours, fermeture normale code0 et dimensions sauvegardées à21:22:01. Le contrôle indépendant compare aussi le joueur embarqué de `level.dat` au fichier `playerdata` après cet arrêt propre. Ce contrôle ne prouve pas la reprise après crash solo.

Dans les deux modes : menu réel synchronisé, paquets vanilla de boutons depuis les clients, découverte par carte, aller payé vers Ilum, retour prépayé, menus et inventaires projetés. Les boutons99/−1 et un ancien identifiant de conteneur ne changent rien. Les doubles demandes de découverte, départ et retour ne redébitent pas : carte1→0, cellules2→0, révision atlas0→1→2→3, crédit aller vrai puis retour faux, sept diamants conservés. Le reçu final est RETURN/DONE, coût0, sans opération pendante.

Sur dédié, chaque client observe au moins deux joueurs à destination et au retour ; les volumes d'arrivée serveur ne se chevauchent pas. Les deux propriétaires, endpoints et PID sont distincts. Les instantanés natifs à l'arrivée/au retour, fichiers joueurs après arrêt, journaux et cinq étapes de chaque client sont recoupés par `check-travel-network.py`, sans modifier les preuves.

## Harnais et reproduction

Les classes `TravelNetworkValidation` et `TravelNetworkClientDriver` sont exclusivement dans `src/gameTest`, activées par propriété JVM et limitées à des profils isolés. Le serveur fournit explicitement plateforme, terminaux et inventaire de fixture ; il place le terminal porté par le vrai `BlockItem` à destination. Les clients envoient exclusivement les intentions de découverte/voyage ; le harnais ne téléporte pas à leur place pour ces actions. Ce n'est pas une preuve de récolte/craft/pose manuelle complète.

```powershell
./scripts/test-travel-network.ps1 -Run exemple
python scripts/check-travel-network.py exemple
./scripts/test-travel-solo.ps1 -Run exemplesolo -TemplateRun exemple
python scripts/check-travel-network.py exemplesolo --solo
```

Toujours utiliser un identifiant neuf, préserver les anciens mondes et ne pas lancer deux Gradle simultanément. Le solo copie seulement `level.dat` du monde dédié indiqué pour ses paramètres de génération ; le script vérifie l'absence de joueur embarqué. Aucun chunk, joueur, reçu ni résultat n'est copié. Il utilise l'action native « Créer une sauvegarde et charger » du monde expérimental/customisé, uniquement dans ce profil isolé. Aucune confirmation système n'est manipulée.

Les lanceurs dérivent classpath, JVM/providers et environnement du `RunGameTask` de ModDevGradle2.0.148 ; ils ne réutilisent pas un manifeste d'un ancien test. Les assets viennent de `prepareClientTwoRun`. `onboardAccessibility:false` concerne uniquement les profils de test neufs ; distance de simulation client5, limite30FPS et heaps bornés. Deux JRE clients isolés permettent d'identifier les processus. Le serveur reste hors authentification uniquement sur127.0.0.1. Preuves NBT sous `world/validation` et `profile/validation`, logs/résultats de lancement sous `.tools` ; ces données locales restent ignorées par Git.

Prérequis Windows : PowerShell7, JDK21 local et deux copies de ce JDK sous `.tools/java-client-one` et `.tools/java-client-two` (non distribuées). Après installation du JDK utilisé ici, préparer uniquement les copies absentes :

```powershell
foreach ($name in 'java-client-one','java-client-two') {
    if (-not (Test-Path -LiteralPath ".tools/$name")) {
        Copy-Item -LiteralPath '.tools/java/jdk-21.0.13+9' -Destination ".tools/$name" -Recurse
    }
}
```

Adapter le chemin source si un autre JDK21 est installé. Les checkers demandent Python3 ; le lanceur solo accepte `-PythonPath` pour remplacer le runtime Python local par défaut. Les tests ne modifient aucune installation Java système.

## Essais non validés et limites

`net1` : classpath vide avant exécution du RunGameTask, corrigé via `classpathProvider`. `net2` : écran d'accessibilité initial empêchant QuickPlay ; options isolées corrigées. Aucun de ces essais ne compte comme réussite. `solo1`/`solo2`/`solo3` restent non validés : confirmation expérimentale non traitée ; contrôle du profil corrigé avec chemin absolu normalisé (`gameDirectory` vaut « . »). Le bouton natif sauvegarde/chargement est effectivement observé dans `solo4`. Le test ne désactive aucune assertion de gameplay.

Pas de coupure réseau pendant le transfert dans ces parcours, pas de panne disque, de crash du serveur intégré, de mod de claims tiers ni de charge prolongée. La stabilité de ces fenêtres natives ne permet pas d'attribuer ou déclarer résolus les anciens incidents GLFW. Les preuves automatiques de menus/états ne valident pas l'appréciation graphique du siège ou des nuanciers. P4-02 demeure partiel et la V1.0 reste EN COURS.

Compléments distincts ultérieurs : checkpoint et crash solo dans SOLO_OWNER_SAVE.md ; coupures socket réelles à trois étapes avec relocalisation sûre à la reconnexion dans P4-02_DISCONNECT.md. Les périmètres ci-dessus restent ceux de net3/solo4.
