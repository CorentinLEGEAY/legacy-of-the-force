# Legacy of the Force — Dossier de conception pour Codex

> Dossier GDD 0.4 — 11 novembre 2026, avec un **mod de développement exécutable**. V1.0 en cours ; validations et limites dans docs/03_ETAT_DU_PROJET.md.

Développement groupé en cours : le concepteur a demandé d'implémenter les systèmes par grands lots, puis de mener des campagnes de tests globales. Plan et réserves : `docs/09_LOTS_V1.md` ; état réel : `docs/03_ETAT_DU_PROJET.md`. Les nouveaux lots ne sont pas encore validés et la V1.0 n'est pas terminée.

L'intégration actuelle ajoute les actions d'atelier Modifier/Démonter/Entretenir/Enregistrer/Charger un plan, ainsi qu'un arbre de compétences accessible avec I. K sélectionne un pouvoir, O le lance ; touches reconfigurables. Commandes équivalentes : `/lotf skill menu`, `/lotf skill list`, `/lotf skill learn sense` et `/lotf force power sense`. Points, prérequis, alignement, coûts et actions restent contrôlés par le serveur. Ces ajouts attendent la campagne de gameplay, d'interface et de persistance. La maîtrise des17 pouvoirs appris progresse maintenant sur des interactions significatives mesurées par le serveur, avec plafonds persistants et trois rangs d’économie énergétique ; l’arbre affiche usages, seuil et coût. Ce registre et ses défis attendent aussi la campagne globale ; les primitives historiques ne sont pas encore raccordées.

Le lot suivant ajoute les assemblages connectés avec `/lotf force group`, `/lotf force rotate`, `/lotf force throw_blocks` ; V dépose et G restitue. Les trois nouvelles actions ont des touches configurables sans affectation initiale. Le garage propose quinze pièces, une liste paginée et trois tailles de grille, jusqu'à128 modules ; sa catégorie dépend de la masse, de l'encombrement occupé et des sièges. Un propriétaire peut enregistrer jusqu'à trois invités avec `/lotf speeder guests allow <joueur>`, puis révoquer avec `revoke <joueur>` ou `revoke_uuid <UUID>` : véhicule vide, à l'arrêt et proche. Seul le propriétaire pilote. Les nouveaux modèles mécaniques, sièges, blindage et contrôles de persistance attendent aussi la campagne ; le stockage embarqué est désormais codé, en attente de campagne. Un coffre apporte neuf cases, jusqu’à27 par véhicule : s’accroupir et interagir avec son speeder arrêté pour ouvrir le stockage. Clic gauche : pile entière ; clic droit : un objet. Les transferts sont sauvegardés sans curseur ; le contenu ajoute de la masse et reste récupérable après casse. Le recyclage demande une cargaison vide. Une ancienne configuration serveur peut conserver son plafond de masse initial à20 : voir `legacyoftheforce-speeder.toml` pour le modifier.

Combat sabre en cours d’intégration : **Y** change de style (défensif/équilibré/agressif), **B** prépare une garde, **M** tente un contre après parade sur l’adversaire visé, **Alt gauche** esquive en arrière. Commandes `/lotf saber style`, `/lotf saber counter`, `/lotf saber dodge back` (également `left`, `right` ou `forward`). Les combos exigent des impacts réels ; la garde peut dévier des flèches vanilla avec coût supplémentaire. Touches reconfigurables. Ces ajouts compilent mais attendent la campagne de gameplay/réseau ; les types avancés et rituels sont maintenant codés, tandis que le double wield reste à compléter.

Le nouvel atelier propose **25 pièces et cinq types** : simple, court, courbe, double et garde. Cliquer une famille fait défiler ses pièces compatibles ; **Type** choisit une structure et **Deuxième émetteur** complète le double. Les plans avancés doivent être lus avant fabrication ; leur distribution naturelle reste à intégrer aux planètes. Les pièces choisies composent désormais le rendu et les statistiques serveur ; le double paie et restitue bien ses deux émetteurs. Cette intégration compile, mais sa jouabilité, son rendu et sa persistance attendent la campagne globale.

Kyber : six familles et leurs affinités, corruption rouge et purification blanche sont intégrées au code. **Il faut désormais éveiller le cristal à l’atelier avant d’activer la lame**, y compris pour les anciens sabres dormants :4 améthystes+2 lapis par défaut. Les rituels avancés demandent niveau/alignement et matériaux de Mustafar/Jedha ; ils conservent l’UUID, l’origine et le lien. Tatooine possède désormais des sources codées de cristal jaune, silice et plans court/courbe ; les autres matériaux/cristaux attendent leur acquisition naturelle dans P6. Rendu, persistance et équilibrage du lot restent en attente de campagne.

Navigation étendue en cours : le terminal sélectionne désormais six destinations et conserve les anciens voyages Ilum. Tatooine dispose de terrains dunes/canyons, mines de cristal jaune/silice, établissements et épaves dans les nouvelles données ; ses deux boss à3 phases, leurs temples, le moteur de course, le renouvellement via combats et la chaleur modérée sont maintenant codés. Carnet d’expédition : indices facultatifs sur les mines et les contres des boss. Les autres dimensions ne sont pas encore disponibles. Ce lot compile mais son chargement et son gameplay attendent la campagne globale.

Endor est désormais intégré au code : forêt géante/clairières, grottes à cristal vert/résine, tours et ruines, Gardienne de la Nature et Assassin Nocturne à3 phases, châssis forestier plus stable et plus lourd, plans de sabre double et carnet d’indices. La brume est réglable via planetAtmosphere/endorFogDistance côté client. Les leurres de l’Assassin sont dissipables ; la perception révèle le vrai adversaire. Ce lot compile mais attend sa campagne native/graphique/persistance, comme les autres ajouts récents. Mustafar est maintenant intégré au code ; Dagobah est maintenant intégré au code ; Jedha reste à développer.

Mustafar est intégré au code : coulées de lave/basalte/cavernes, raffineries et chambre à cristal orange/scories, Protecteur des Ruines et Seigneur des Flammes à3 phases, moteur haute puissance et plan de sabre à garde. Les plateformes et couvertures répondent aux zones de chaleur ; les braises peuvent être contrées avec la Force. L’eau s’évapore : prévoir des protections contre le feu et des abris. Le carnet donne des indices facultatifs. Compilation réussie, chargement réel/graphismes/jeu/réseau/persistance encore en attente de campagne.

Dagobah est intégré au code : marais/mangroves/grotte scellée, cristal cyan et essence, Ermite de la Force/Maître des Illusions à3 phases et stabilisateur apportant davantage de portance au prix de masse/stabilité. La boue ralentit les pieds nus (configurable), les bottes aident ; la brume est réglable via dagobahFogDistance. Le Sens distingue les vrais adversaires/tirs des leurres ; le carnet explique les contres. Les sources d’essence alimentent désormais la concentration du cristal lié dans le code. Compilation réussie ; campagne de chargement, jeu/rendu/réseau/persistance encore ouverte.

## Objectif
Construire **un mod Minecraft Java unique**, organisé en modules internes, mêlant survie Minecraft, RPG Star Wars, combats, Force physique, sabres laser personnalisés et évolutifs, speeders construits sur mesure et exploration de six planètes. Direction artistique : **monde Minecraft + équipements et effets cinématographiques**.

## Premier démarrage avec Codex
1. Copiez le contenu de ce dossier à la racine d'un **dépôt Git**. Ouvrez ce dépôt dans Codex.
2. Faites lire à Codex **`AGENTS.md`**, puis **`docs/00_DECISIONS.md`**, **`docs/01_ARCHITECTURE.md`**, **`docs/02_ROADMAP.md`** et la fiche du module concerné.
3. Commencez par **l'étape P0** : amorcer le projet Gradle/NeoForge, lancer client et serveur de développement et vérifier que le JAR se construit.
4. Suivez la cadence autorisée du projet : développements par lots, puis campagnes de tests groupées et corrections ; ne validez aucune phase sans preuves.
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

Le dépôt contient un socle NeoForge, la progression RPG, la Force minimale, un atelier de sabres avec cristal/lame/combat/lien initiaux, un speeder modulaire avec entretien/recyclage, Ilum et son terminal de voyage, et deux boss Ilum en cours de validation ; **la V1.0 n'est pas terminée**. Le journal `docs/03_ETAT_DU_PROJET.md` donne le statut réel. Le JAR `build/libs/legacyoftheforce-0.1.0-dev.jar` est une version de développement. Les cinq autres planètes, dix autres boss et les validations complètes solo/réseau/crash restent à réaliser. Les rapports sous `docs/validation/` distinguent les essais graphiques réellement observés des contrôles automatisés.

Prérequis : JDK 21 x64, Git. Le wrapper télécharge Gradle et les dépendances NeoForge 21.1.265. Aucun Gradle global nécessaire.

```powershell
./scripts/gradle.ps1 build --console=plain
./scripts/smoke-server.ps1
./scripts/gradle.ps1 runClient --console=plain
```

Avec JAVA_HOME configuré, les commandes standard `./gradlew.bat build` (Windows) ou `./gradlew build` (Linux/macOS) fonctionnent également. Le JDK local dans `.tools/java` est détecté par le script PowerShell.

Les clients de développement ciblent l'écran Windows3. Changer temporairement ce choix avec `./scripts/gradle.ps1 runClient -PlotfDisplay=2 --console=plain`. Le placement intervient une fois au démarrage de la fenêtre de jeu et ne déplace aucune autre application. Si l'écran demandé est absent, le client conserve sa position et écrit un avertissement. Les lancements installés ordinaires n'imposent aucun écran.

Le smoke utilise exclusivement `127.0.0.1:25575`, un monde isolé conservé entre lancements, et vérifie démarrage + arrêt + sauvegarde. Les configurations de dev ne remplacent pas un serveur de production authentifié.

Profil : `/lotf profile` affiche aussi l’orientation calculée par le serveur. Dans la section `[rpg]` de `config/legacyoftheforce-server.toml`, `alignmentThresholds = [-500, -200, 200, 500]` règle les quatre seuils de présentation en dixièmes (obscur confirmé/tendance obscure/tendance lumineuse/lumineux confirmé). Garder quatre entiers strictement ordonnés autour de zéro ; une liste invalide est corrigée intégralement aux défauts par NeoForge. Une surcharge par monde est possible sous `world/serverconfig/legacyoftheforce-server.toml`. Cela ne change ni le score sauvegardé, ni les conditions des pouvoirs, ni la couleur du cristal.

Profil : `/lotf profile`. Test administrateur : `/lotf debug spend_energy 50`. Diagnostic de protocole : `/lotf_probe 1` (sonde client, vérifiée par le serveur). Procédures et limites dans `docs/validation/`.

Les récompenses RPG disposent d'un journal de reprise et de reçus durables sous le dossier du monde `data/legacyoftheforce/rewards`. Sauvegarder ce dossier avec le monde et les profils joueur. En cas d'erreur de stockage, les actions de progression concernées sont suspendues et les données conservées ; `/lotf rpg recover` (administrateur) réessaie la récupération après correction du problème. Voir `docs/validation/P1_RPG_REPRISE.md`.


La télékinésie minimale de blocs est désormais jouable : niveau 15 pour terre/planches/laine, 35 pour pierre. V saisit/dépose, G restitue ; touches reconfigurables. Voir `docs/validation/P1-04.md` pour les règles, les preuves et les limites. Les autres systèmes de la V1.0 restent à développer.

R pousse un mob dès le niveau 5 ; H l'attire dès le niveau 15. Viser une cible à moins de 6 blocs, sans mur. Énergie, masse, résistance, permissions et cooldown partagé sont calculés côté serveur. Les joueurs, montures occupées, animaux d'autrui et mobs désactivés par `NoAI` sont refusés ; les boss résistent, sauf fenêtre de contre explicitement ouverte par leur IA. Touches reconfigurables ; commandes équivalentes : `/lotf force push` et `/lotf force pull`. Voir `docs/validation/P1-05.md` et `docs/validation/P4-03.md`.

Atelier de sabres : fabriquer le bloc et ses pièces via les recettes vanilla débloquées avec du fer, puis ouvrir l'atelier. Conserver dans l'inventaire principal un émetteur, un col, une poignée, un interrupteur, un circuit et un pommeau. Choisir l'émetteur et cliquer « Assembler » ; le serveur consomme une pièce de chaque famille et livre une poignée avec UUID et données persistantes. Deux modèles d'émetteur sont disponibles. Détails et limites : `docs/validation/P2-01.md`.

Pour installer un cristal, tenir le sabre en main principale, ouvrir l'atelier et cliquer « Cristal ». Le cristal en main secondaire est prioritaire, sinon le premier de l'inventaire principal. En remplacement, l'ancien cristal revient dans cet emplacement avec son identité conservée. Le clic droit hors atelier allume/éteint la lame bleue, avec délai serveur et sons originaux. Les mines rares d'Ilum produisent maintenant un cristal bleu identifié, à récolter avec une pioche en fer ou meilleure ; le terminal de voyage est disponible (voir plus bas). Les commandes créatives restent des outils de développement. Preuves et limites : `docs/validation/P2-02.md` et `docs/validation/P4-01.md`.

Combat initial : attaquer normalement avec la lame active consomme de l'endurance et exige la recharge de l'attaque. B (reconfigurable) ouvre une parade de 6 ticks, réduisant un seul coup de mêlée frontal ; elle ne protège pas des tirs ni de la chute. Les coûts et délais sont calculés par le serveur. L'endurance apparaît dans la barre d'action lors d'une attaque/parade ou via `/lotf saber status`. Les récompenses suivent le système RPG existant. Vérifications et limites de P2-03 : `docs/validation/P2-03.md`.

Lien initial : tenir le sabre avec cristal dans l'atelier puis « Lier ». Le tooltip indique les matières de l'éveil (4 améthystes et 2 lapis par défaut). La pratique variée contre des ennemis éligibles permet l'harmonisation et un petit bonus de parade ; les quotas et l'historique restent attachés au cristal. Les paliers supérieurs dépendent notamment du futur contenu sanctuaire/boss. Le rituel et le bonus sont testés, mais le parcours de progression réel reste en cours de validation : `docs/validation/P2-04.md`.

Speeders : fabriquer le garage et un plan papier (huit papiers autour d'une poche d'encre), tenir le plan et ouvrir le garage. Éditer les trois couches de la grille, ou charger Base ; rotation 0=nord. Un châssis, moteur, batterie et pilote reliés sont requis. Enregistrer conserve le même papier et son schéma ; fermer sans enregistrer abandonne le brouillon. **Créer** consomme les pièces réelles une seule fois et place le véhicule sur l'un des côtés libres du garage ; garder le curseur vide et enregistrer le plan avant fabrication. Le plan reste réutilisable, rouvrir le menu pour fabriquer un autre véhicule avec de nouvelles pièces. Monter par clic droit en tant que propriétaire, avancer/reculer pour accélérer/freiner, gauche/droite pour tourner et accroupir pour descendre ; le HUD affiche vos touches. Énergie et physique côté serveur, véhicule conservé dans sa dimension après déconnexion. Preuves et limites : `docs/validation/P3-01.md`, `docs/validation/P3-02_ENTITY.md` et `docs/validation/P3-02_ASSEMBLY.md`.

Entretien : ouvrir le garage puis **Diagnostic**, même sans plan tenu, pour sélectionner votre véhicule le plus proche. Véhicule arrêté et vide, curseur libre : **Recharger** consomme une cellule (cuivre1/redstone4) pour ajouter jusqu'à250énergie, sans dépasser la capacité ; **Réparer** consomme une trousse (fer2/cuivre1/redstone1) pour restaurer jusqu'à25HP sur le module le plus abîmé. Valeurs serveur configurables. Batterie déjà pleine ou modules tous intacts : aucun débit. Un moteur abîmé réduit la conduite, une batterie abîmée réduit sa capacité ; la réparer ne redonne pas l'énergie perdue. Les coups directs du propriétaire et les impacts contre le terrain peuvent endommager les pièces. Châssis ou siège détruit : réparer pour piloter de nouveau ; l'épave conserve son UUID. Preuves de stockage et limites graphiques/réseau : `docs/validation/P3-02_RECHARGE.md`, `docs/validation/P3-02_RECHARGE_CRASH.md` et `docs/validation/P3-03.md`.

Peinture : colonne de droite du garage, **Teinte** parcourt16 couleurs ; **Zone1** vise châssis/siège, **Zone2** moteur/batterie. En édition, **Plan** colore le brouillon gratuitement : Enregistrer pour garder la palette, appliquée à une fabrication neuve. Dans Diagnostic, **Peindre** change la zone de votre véhicule contre une teinture sélectionnée (tarif serveur configurable), sans modifier son plan papier, ses HP ni sa charge. Une couleur déjà identique ne coûte rien. Deux teintes, recharge, réparation et conduite ont été observées sur dédié avec un client ; limites et preuves : `docs/validation/P3-03_PAINT.md` et `docs/validation/P3-03_NATIVE.md`.

Recyclage : dans Diagnostic, **Recycler** puis **Valider** sous5 secondes retire définitivement une épave possédée, vide et arrêtée. Les parties survivantes rendent des matières selon leurs HP ; prévoyez la place dans l'inventaire. Véhicule intact, stockage inconnu ou place insuffisante : aucun retrait. Le papier reste réutilisable. Sauvegardez le monde entier, notamment `data/legacyoftheforce/retired_speeders` qui empêche de réutiliser un UUID retiré. Test serveur automatisé du bouton et des reprises, observation graphique encore ouverte : `docs/validation/P3-03_RECYCLE.md`.

Force et speeder : viser votre véhicule léger **inoccupé**, **J** stabilise ponctuellement sa vitesse verticale (niveau25), **N** freine sa course horizontale (niveau45). Touches reconfigurables ; commandes `/lotf force stabilize` et `/lotf force brake`. Coût de Force selon masse et délai commun, validation de portée/visibilité/droits sur serveur. Aucun effet sur un véhicule immobile, étranger, occupé ou en entretien ; aucune recharge/réparation ni lévitation gratuite. Tests et limites : `docs/validation/P3-04.md`.

Ilum P4-01 : dimension `legacyoftheforce:ilum`, biome glaciaire, relief enneigé, mines scellées avec escalier, temples Jedi, sanctuaires Sith et ruines. Une mine contient un seul gisement à cristal ; Fortune et Toucher de soie rendent toujours un seul cristal, jamais un bloc source réutilisable. UUID, provenance et qualités sont attribués par le butin serveur. Les nouveaux temples et sanctuaires accueillent désormais leurs autels de boss ; les chunks existants ne sont pas régénérés. Procédures et preuves : `docs/validation/P4-01.md` et `docs/validation/P4-03.md`.

Boss Ilum P4-03 : en survie, interagir avec l'autel et entrer dans la salle pour participer. Gardien Jedi : duel/parade ; seigneur du givre : assauts, attraction et glace à briser ou renvoyer. Trois phases, attaques annoncées et reset si la salle reste vide. PV de base configurables40–1024 ; groupe plafonné à1024, sans soin lors d’une arrivée tardive. Chaque contributeur éligible reçoit800XP et −25/Jedi ou +25/Sith ; délai personnel une heure, arène dix minutes par défaut. Ouvrir ensuite l'autel pour partager une relique et quatre cellules : clic sur le butin pour **tout collecter vers l'inventaire**, curseur vide et place suffisante. Relique+fer donnent quatre trousses. Les fluides et seaux ne peuvent pas modifier une arène protégée ; un refus conserve le contenu initial du seau côté client. Les autels sont aussi fabricables selon `QIQ/ADA/QQQ` (Q=quartz pour Jedi, briques d'ardoise pour Sith ; I=glace bleue, A=éclat d'améthyste, D=diamant), sur Ilum avec sol solide9×9 centré trois blocs au nord. Deux duels complets et collectes vérifiés avec deux vrais clients ; parades, Force et récompenses personnelles constatées. Collecte également éprouvée par onze checkpoints et sept kills/reprises dédiés. Autres validations de reprise et de protection encore ouvertes. Sauvegarder le monde entier, notamment `data/legacyoftheforce/bosses/loot` avec les profils et régions. Rapports : `docs/validation/P4-03.md`, `docs/validation/P4-03_LOOT.md` et `docs/validation/P4-03_NETWORK.md`. Solo intégré : les deux duels/collectes et le rechargement natif du propriétaire passent aussi (`docs/validation/P4-03_SOLO.md`) ; Seize arrêts/reprises de publication et victoire passent également (`docs/validation/P4-03_ARENA_CRASH.md`) ; Mort avec/sans conservation, sortie de dimension autorisée et reconnexion natives sont vérifiées avec reçu de collecte conservé (`docs/validation/P4-03_LIFECYCLE.md`) ; les protections et autres combinaisons de panne restent ouvertes.

Voyage P4-02 : fabriquez deux terminaux avec la recette `IGI / CRC / III` (I=fer, G=verre, C=cuivre, R=redstone). Posez-en un à la base et emportez l'autre. Une carte d'Ilum se fabrique sans ordre avec boussole, papier, éclat d'améthyste et lapis ; **Lire la carte** dans le terminal la consomme pour débloquer Ilum. Le premier départ consomme deux cellules énergétiques, dont une réserve le retour. Sur Ilum, posez le terminal transporté et choisissez **Retour à la base**. Départ et retour cherchent un emplacement sûr sans effacer de terrain ; pas de transport des véhicules. Refus en combat, en monture, sans autorisation ou sans arrivée sûre. Réglages serveur dans `legacyoftheforce-travel.toml`. Aller-retour observé sur dédié avec un vrai client ; deux redémarrages supplémentaires vérifiés sans client graphique. Solo, deux clients et vrais arrêts forcés ont depuis été éprouvés ; les combinaisons restantes sont tracées dans `docs/validation/P4-02_NETWORK.md`, `docs/validation/P4-02_CRASH.md` et `docs/validation/P4-02_DISCONNECT.md`.

Protection des fluides et correction des prédictions éprouvées avec deux clients et en solo : `docs/validation/P4-03_FLUIDS.md`. Le JAR0.1.0-dev a aussi démarré/redémarré dans NeoForge installé hors environnement de développement, injections réellement appliquées : `docs/validation/PACKAGED_SERVER.md`. Ces contrôles ne constituent pas une validation globale de la V1.0.

Un holocron rare apparaît dans les nouvelles ruines de pierre moussue de l’Overworld. Le lire près de son socle conserve un guide facultatif vers Ilum et son premier cristal. `/lotf journal` permet ensuite de relire les indices sans objet en inventaire ; visite et cristal identifié peuvent être acquis avant l’holocron. Aucun objet consommé ni gain d’XP/alignement par cette quête. Journal vérifié en solo avec rechargement du propriétaire et sur deux vrais clients réseau ; recharge des données et autres limites encore suivies dans docs/validation/P4-04.md. P4 et V1.0 restent en cours.
