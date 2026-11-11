# P3-02 — entité, réseau et stockage monde

**EN COURS**, 11 novembre 2026, 19:27. L'entité est réellement pilotable avec une fixture de développement ; **la fabrication et le garage d'instance ne sont pas livrés**, P3-02 non validé. Cette fiche actualise les étapes internes antérieures de `P3-02.md` qui annonçaient encore l'entité à implémenter.

## Mini-plan et code

Références : décisions, architecture, roadmap P3, SPEED-03/05/06/10, équilibrage et sécurité. Fichiers : `SpeederEntity`, `SpeederInput/Network`, `SpeederContent`, amorçages commun/client, `client/SpeederControls/Renderer/Hud`, générateur/ressources originales, `core/operation/VerifiedEntitySaveBarrier`, AT et GameTests. Sources locales Minecraft 1.21.1/NeoForge 21.1.265 consultées pour les paquets de véhicule, interpolation, dimensions, attachement du passager, `ServerPlayer/PlayerList`, gestionnaire et stockage des entités. Format d'AT vérifié dans la [documentation NeoForge 1.21–1.21.1](https://docs.neoforged.net/docs/1.21.1/advanced/accesstransformers/) et découverte automatique du chemin par ModDevGradle vérifiée dans son plugin local.

## Comportement réel

- Une entité MISC sans spawn naturel, œuf ni `/summon`. Initialisation réservée au coordinateur serveur et aux tests, avec plan/catalogue/charge/dimension validés. Propriétaire unique et un seul pilote ; formats inconnus immobilisés et conservés. Sauvegarde vanilla de l'enveloppe entière et UUID cohérent.
- Le pilote transmet seulement ID d'entité, séquence, accélération et direction, paquet borné à 23 octets. Serveur vérifie acteur/passager/propriétaire/dimension, valeurs finies [-1,1], séquence et cadence de deux ticks. Une commande sans nouvelle entrée expire après huit ticks : freinage et descente sans autre dépense. Logout : descente du pilote et stationnement, aucun moteur qui recharge sa batterie à vide. Aucune position/vitesse/charge reçue du client.
- `getControllingPassenger()` reste null : la branche vanilla acceptant une position C→S de véhicule n'autorise jamais le pilote. L'entité produit le déplacement, le fait passer par `move(MoverType.SELF, ...)` et conserve le résultat réel de collision. Interpolation côté client des positions reçues du serveur.
- Lévitation mesurée par raycast de collision à six blocs maximum, cible configurable à deux blocs. Sans sol ou sans énergie : descente ; aucune capacité de vol libre. Empreinte calculée depuis les modules, enveloppe carrée conservatrice et hauteur réelle, cellule abstraite de 0,5 bloc. Pas de traversée du mur testé. Vérification des chunks chargés, bordure et **toutes** les cellules de l'AABB cible, maximum 64, via le service de protection ; pas seulement les coins. Aucun chargement forcé par le tick de conduite. Le plan de siège décentré et sa rotation déterminent réellement l'attache du pilote. Sécurité du vide : instance conservée au plancher de dimension ; récupération ergonomique/garage à terminer.
- L'entité monde est la vérité de sauvegarde. Le hook vanilla de monture monoplace est neutralisé pour éviter la copie `RootVehicle` dans le joueur et le retrait automatique du véhicule à la déconnexion. Ce choix ne change pas le garde d'occupation du siège. `VerifiedEntitySaveBarrier` fait écrire/flush le gestionnaire vanilla puis relit le stockage actif, avec égalité NBT exacte et UUID présent une fois. Trois champs accessibles mais toujours final dans `META-INF/accesstransformer.cfg`, sans écriture alternative ni réflexion. Coût global du flush rare à profiler en P7 ; pas d'appel dans le tick de déplacement.
- Rendu des vrais modules/cellules/rotations avec géométries cuboïdes originales, connecteurs visibles et matériaux vanilla référencés. Correction de l'échelle : contexte de rendu NONE, sans la réduction supplémentaire du modèle d'item FIXED. HUD de vitesse/énergie et seconde ligne de commandes avec les vraies touches localisées/configurées ; appuis courts gardés jusqu'au prochain échantillon. Peinture, intégrité projetée et détails graphiques avancés restent à compléter.

## Tests automatiques

`./scripts/gradle.ps1 build runGameTestServer --console=plain` : **75 JUnit et 49 GameTests requis réussis**, BUILD SUCCESSFUL, fin 19:26:12, `.tools/test-speeder-entity-seat-final.log`.

JAR de développement `build/libs/legacyoftheforce-0.1.0-dev.jar`, SHA-256 `7E63D4D61A525108D2AC35C713CD15ED6B8AAE8DA1AC6D7087416384DDB94075`. Archive inspectée : entité/AT présents, commandes de validation et GameTests absents. Ce JAR n'est pas une release V1.0.

Six GameTests supplémentaires :

1. Entité dans le vrai niveau, déplacement, maintien à deux blocs du sol, mur bloquant et charge consommée ; dimensions et sérialisation vanilla/UUID.
2. Propriétaire étranger, second pilote, NaN et commande hors borne rejetés ; format futur immobile et NBT resauvegardé sans effacement.
3. Codec du vrai paquet, séquences rejouées, rafale et mauvaise entité refusés ; expiration réelle avec ticks du serveur puis énergie inchangée et freinage.
4. Barrière avec vrai stockage des entités : pilote en selle, fichier joueur sans `RootVehicle`, entité durable égale à la sérialisation attendue et présente une fois ; entité retirée refusée.
5. Cellule protégée au centre de l'empreinte, coins autorisés : tout déplacement bloqué.
6. Déplacer le siège au-dessus de la batterie déplace l'ancrage du pilote ; rotation du véhicule appliquée au siège comme au rendu.

La première régénération AT a échoué sur un fichier temporaire parce que deux invocations Gradle avaient été lancées en chevauchement ; attente de leurs résultats puis exécutions séquentielles. Aucun test contourné ou supprimé. Les ERROR des injections de panne P1 restent des scénarios attendus, pas des échecs de ces tests.

## Parcours dédié avec un client réel

Compte de test ForceTesterOne, un seul client Java isolé ; serveur local de développement. `SpeederValidationCommands` existe **seulement dans le source set gameTest**, réservé OP2. `execute as ForceTesterOne at @s run lotfvalidation speeder` crée une fixture sans paiement et l'annonce explicitement. Classe vérifiée absente du JAR de distribution, comme les autres GameTests ; elle ne constitue ni une recette ni une preuve de fabrication atomique.

- 19:10:05 : fixture créée, UUID `c21122ca-b096-4f16-957a-bb5e4737a04d`. Client reçoit et affiche le HUD et les modules ; serveur lit le vrai état et la baisse de charge. Première inspection révèle l'échelle FIXED trop petite, corrigée et revérifiée après redémarrage.
- 19:13:10 : arrêt/sauvegarde propres. 19:16:22 : client reconnecté au même monde ; **même véhicule et même design**, charge 672,4479999999876, pose stationnée `(0.5,100,3.5)`, quatre intégrités 100. Aucun second spawn de fixture.
- Rendu corrigé observé en troisième personne. HUD donne les touches locales **Z/S et Q/D** ; le premier essai W ne démontrait pas un déplacement sur ce clavier. Avec Z, HUD à 1,1 bloc/s et lecture serveur de la position `(0.5,102.00000265444224,3.844610742058)` après départ de z=3,5. Avec Q, orientation serveur **−8°**. S utilisé ensuite ; marche arrière couverte de façon déterministe en JUnit, freinage aussi vérifié en GameTest. Ne pas déduire d'un tap la validation d'une course prolongée à vitesse maximale.
- 19:18:08 : `save-all flush` pendant que le pilote est encore en selle ; lecture NBT du fichier joueur : **RootVehicle=False**. 19:18:22 : fermeture normale du client **sans descendre auparavant** ; fichier joueur à nouveau sans RootVehicle. La recherche d'entité immédiatement après le logout renvoie absent parce que le chunk s'est déchargé. Chargement ciblé du seul chunk `[0,0]` via la console, puis 19:18:46 : même UUID, design/HP/−8° conservés, position `(0.3557590299059503,100,3.5100863111903164)`, charge **484,1039999999807**. 19:20:36 : charge exactement identique. Aucun effacement de la monture avec le joueur, aucune régénération à l'arrêt. Ticket forcé retiré puis serveur arrêté avec toutes dimensions sauvegardées et BUILD SUCCESSFUL.

Traces locales non versionnées : `.tools/speeder-entity-first-dedicated.log`, `.tools/speeder-entity-corrected-dedicated.log`, `.tools/speeder-entity-client{,-corrected}.log`, `.tools/speeder-player-{while-mounted,after-logout}.json`. Les preuves de commandes extraites sont dans `P3-02_ENTITY_EVIDENCE.txt`. Pas de modification temporaire des touches ; aucun client/serveur de test laissé vivant.

## Limites et prochaine tranche

Le **paiement durable n'existe pas encore**, aucun bouton ne fabrique de véhicule. Relier le devis à un journal avec preuve durable du fichier joueur avant/après, puis publication unique et reprise après crash. Garder l'entité de publication immobilisée jusqu'à fermeture de cette transaction ; ne pas consommer à la seule préparation du devis. L'existence de la barrière d'entités ne suffit pas à prouver une transaction multi-fichiers.

Ensuite : intégration garage d'instance/recharge, parcours complet craftable en solo et dédié, changement de dimension/mort/déconnexion en mouvement/chunks et pannes injectées. Deux interfaces stables et vrai adaptateur de claims tiers restent ouverts. Les tests du service de protection n'attestent pas l'intégration à un plugin de claims absent. Aucune garantie de crash en mouvement ou de migration globale n'est revendiquée par les sauvegardes normales ci-dessus. Dommages/réparation, couleurs effectives et Force restent P3-03/P3-04. Aucune phase globale ni V1.0 validée.
