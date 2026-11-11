# Legacy of the Force — Dossier de conception pour Codex

> Dossier GDD 0.4 — 11 novembre 2026, avec un **mod de développement exécutable**. V1.0 en cours ; validations et limites dans docs/03_ETAT_DU_PROJET.md.

## Objectif
Construire **un mod Minecraft Java unique**, organisé en modules internes, mêlant survie Minecraft, RPG Star Wars, combats, Force physique, sabres laser personnalisés et évolutifs, speeders construits sur mesure et exploration de six planètes. Direction artistique : **monde Minecraft + équipements et effets cinématographiques**.

## Premier démarrage avec Codex
1. Copiez le contenu de ce dossier à la racine d'un **dépôt Git**. Ouvrez ce dépôt dans Codex.
2. Faites lire à Codex **`AGENTS.md`**, puis **`docs/00_DECISIONS.md`**, **`docs/01_ARCHITECTURE.md`**, **`docs/02_ROADMAP.md`** et la fiche du module concerné.
3. Commencez par **l'étape P0** : amorcer le projet Gradle/NeoForge, lancer client et serveur de développement et vérifier que le JAR se construit.
4. Avancez **une tâche et un test à la fois**. Ne demandez pas « programme tout le mod d'un coup ».
5. Faites actualiser `docs/03_ETAT_DU_PROJET.md` après chaque tâche terminée.

## Structure du dossier
```text
legacy-of-the-force/
├── AGENTS.md                          Consignes permanentes de Codex
├── README.md                          Porte d'entrée
├── docs/
│   ├── 00_DECISIONS.md                Vision, règles figées et hypothèses
│   ├── 01_ARCHITECTURE.md             Dépendances, API, sauvegardes, réseau
│   ├── 02_ROADMAP.md                  Phases et tickets ordonnés
│   ├── 03_ETAT_DU_PROJET.md           Journal des tâches (à maintenir)
│   ├── 04_EQUILIBRAGE.md              Règles chiffrées initiales
│   ├── 05_TESTS_ET_SECURITE.md        Tests de conformité et anti-exploits
│   ├── 06_DIRECTION_ARTISTIQUE.md     Style, modèles, effets, accessibilité
│   ├── 07_CATALOGUE_DONNEES.md        Schémas et exemples de données
│   └── 08_PROMPTS_CODEX.md            Prompts prêts à copier
└── modules/
    ├── core/SPEC.md                   Infrastructure commune
    ├── rpg/SPEC.md                    XP, niveaux, alignement, compétences
    ├── force/SPEC.md                  Télékinésie, pouvoirs, cibles
    ├── lightsaber/SPEC.md             Sabres, cristaux, combat
    ├── speeder/SPEC.md                Constructeur et simulation
    ├── planets/SPEC.md                Dimensions, voyages, ressources
    ├── bosses/SPEC.md                 12 boss, IA, arènes, récompenses
    ├── quests/SPEC.md                 Holocrons, PNJ, factions, quêtes
    ├── client/SPEC.md                 HUD, menus, contrôle, effets visuels
    └── content/SPEC.md                Blocs, items, recettes, équilibre
```

## Règles non négociables
- **Un seul mod**, identifiant `legacyoftheforce` ; aucun mod supplémentaire obligatoire sans décision explicite.
- **Gameplay côté serveur** : le client envoie une intention, le serveur valide, applique et synchronise.
- **Sauvegarde fiable** : jamais de duplication/perte d'objets, de blocs ou de véhicules après crash, mort, déconnexion, changement de dimension.
- **Un monde Minecraft jouable** : ne pas supprimer les mécaniques vanilla ; protéger les constructions des autres joueurs.
- **Data-driven** : valeurs d'équilibrage dans des fichiers de configuration/données, pas dispersées dans le code.
- **Fonctions de base avant effets sophistiqués** : une démonstration jouable prime sur les modèles finaux.

## Technologie de départ — décision révisable une seule fois avant P0
- **Minecraft Java 1.21.1 + NeoForge 21.1.x + JDK 21**, pour partir d'une documentation de version explicite et réduire les erreurs d'API.
- Choisir et verrouiller dans Gradle une version **réelle et disponible** de NeoForge 21.1.x avant le premier commit de code ; **ne jamais inventer un numéro de version**.
- Vérifier avant installation de bibliothèques optionnelles (animation, shaders, etc.) qu'une version compatible existe.
- Si le portage vers une version plus récente est souhaité, le faire par ticket séparé, après validation de P0.

Docs utiles : https://docs.neoforged.net/docs/1.21.1/gettingstarted/ ; https://docs.neoforged.net/docs/1.21.1/items/datacomponents/ ; https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/ .

## Ce qui reste à inventer / valider
Les chiffres, noms de capacités avancées, modèles 3D, comportements complexes des boss et recettes sont **des propositions de design**, pas des fonctionnalités déjà implémentées. Les tests décideront du calibrage final. Aucun asset officiel Star Wars n'est fourni dans ce dossier : créer des graphismes/sons originaux, et vérifier le cadre juridique avant diffusion publique.

## Développement exécutable (mise à jour du 11 novembre 2026)

Le dépôt contient un socle NeoForge, la progression RPG et la Force minimale ; **la V1.0 n'est pas terminée**. Le journal `docs/03_ETAT_DU_PROJET.md` donne le statut réel. Le JAR `build/libs/legacyoftheforce-0.1.0-dev.jar` est une version de développement ; sabres, speeders, planètes et boss restent à implémenter.

Prérequis : JDK 21 x64, Git. Le wrapper télécharge Gradle et les dépendances NeoForge 21.1.265. Aucun Gradle global nécessaire.

```powershell
./scripts/gradle.ps1 build --console=plain
./scripts/smoke-server.ps1
./scripts/gradle.ps1 runClient --console=plain
```

Avec JAVA_HOME configuré, les commandes standard `./gradlew.bat build` (Windows) ou `./gradlew build` (Linux/macOS) fonctionnent également. Le JDK local dans `.tools/java` est détecté par le script PowerShell.

Le smoke utilise exclusivement `127.0.0.1:25575`, un monde isolé conservé entre lancements, et vérifie démarrage + arrêt + sauvegarde. Les configurations de dev ne remplacent pas un serveur de production authentifié.

Profil : `/lotf profile`. Test administrateur : `/lotf debug spend_energy 50`. Diagnostic de protocole : `/lotf_probe 1` (sonde client, vérifiée par le serveur). Procédures et limites dans `docs/validation/`.


La télékinésie minimale de blocs est désormais jouable : niveau 15 pour terre/planches/laine, 35 pour pierre. V saisit/dépose, G restitue ; touches reconfigurables. Voir `docs/validation/P1-04.md` pour les règles, les preuves et les limites. Les autres systèmes de la V1.0 restent à développer.

R pousse un mob dès le niveau 5 ; H l'attire dès le niveau 15. Viser une cible à moins de 6 blocs, sans mur. Énergie, masse, résistance, permissions et cooldown partagé sont calculés côté serveur. Les joueurs, boss, montures occupées, animaux d'autrui et mobs désactivés par `NoAI` sont refusés. Touches reconfigurables ; commandes équivalentes : `/lotf force push` et `/lotf force pull`. Voir `docs/validation/P1-05.md`.
