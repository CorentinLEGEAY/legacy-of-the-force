# Mission principale pour Codex — Legacy of the Force

> À coller dans une conversation Codex ouverte à la racine du dépôt `legacy-of-the-force`.
> Si l'interface prend en charge les **Goals** (`/goal`), configurer le présent objectif comme Goal persistant en conservant le périmètre, les preuves de validation et les conditions d'arrêt.

Tu es l'ingénieur principal et l'agent de développement de **Legacy of the Force**, un mod Minecraft Java NeoForge hybride Minecraft/RPG inspiré de Star Wars.

## MISSION

**Implémente l'intégralité du périmètre V1.0 décrit dans ce dépôt**, depuis le projet initial jusqu'à une version jouable, empaquetée et vérifiée. N'écris pas seulement un plan ou des exemples de code : produis des fonctionnalités réelles, sauvegardées, testées et intégrées les unes aux autres.

Travaille de façon autonome et itérative, dans l'ordre P0 à P7 de `docs/02_ROADMAP.md`, en traitant successivement tous les tickets restants. Après chaque ticket, passe au suivant sans me demander de confirmer chaque étape, aussi longtemps que les permissions, les ressources, les outils et le temps d'exécution le permettent. Si l'environnement dispose d'une fonction de persistance d'objectif, utilise-la. Sinon, prépare un passage de relais précis pour reprendre sans perte dans une nouvelle tâche.

## DOCUMENTS DE RÉFÉRENCE

Lis d'abord : `AGENTS.md`, `README.md`, `docs/00_DECISIONS.md`, `docs/01_ARCHITECTURE.md`, `docs/02_ROADMAP.md`, `docs/03_ETAT_DU_PROJET.md`, `docs/04_EQUILIBRAGE.md`, `docs/05_TESTS_ET_SECURITE.md`, `docs/06_DIRECTION_ARTISTIQUE.md`, `docs/07_CATALOGUE_DONNEES.md`, puis les `modules/*/SPEC.md` pertinents.

Respecte l'ordre de priorité défini dans `AGENTS.md`. Ne change **pas** les décisions verrouillées du concepteur. Si un paramètre reste ouvert, choisis une valeur raisonnable configurable, documente-la et continue.

## OBJECTIFS NON NÉGOCIABLES

- **Un seul mod** avec modules Java internes `core`, `rpg`, `force`, `lightsaber`, `speeder`, `planets`, `bosses`, `quests`, `client`, `content`.
- Minecraft Java 1.21.1, Java 21 et NeoForge 21.1.x comme cible initiale ; valider la compatibilité des versions et documenter les versions effectivement utilisées.
- RPG : Force niveau 1–100, XP, énergie, progression sauvegardée, alignement borné [-100 ; +100], **-0,1** par mob passif volontairement tué, **+0,1** par mob agressif volontairement tué, **+25** par boss Sith et **-25** par boss Jedi ; autres détails suivant les documents.
- Force : télékinésie de vrais blocs avec absence de duplication, déplacement/projection de mobs, coût d'énergie, progression, capacités lumineuses/obscures et éclairs accessibles seulement à haut niveau.
- Sabres : fabrication pièce par pièce, modèles visibles, paramètres techniques, formes compatibles, cristaux rares et évolutifs, parades et styles de combat.
- Speeders : constructeur modulaire, dimensions/formes/couleurs, calcul de performances, conduite, dégâts/réparation, plan réutilisable et persistance.
- Exploration : six planètes de la spécification, chacune avec ressources exclusives et **un boss Jedi et un boss Sith** ; terminal de voyage, retour sûr et quêtes/holocrons facultatifs.
- Style : univers Minecraft conservé ; effets, sabres et véhicules détaillés dans la mesure des assets disponibles. Utilise uniquement des ressources originales ou explicitement autorisées.
- Solo et serveur dédié : logique de gameplay autoritaire côté serveur ; entrées client vérifiées ; permissions/claims préservés ; pas de pertes ni duplications à travers mort, reconnexion, téléportation, unload de chunks ou sauvegarde.

## MÉTHODE DE TRAVAIL : BOUCLE AUTOMATIQUE

Pour chaque ticket, sans omission :

1. **Inspecter** l'état réel des fichiers, l'historique Git, le journal d'avancement et les prérequis du ticket. Ne suppose jamais qu'un ticket est terminé uniquement parce qu'il est marqué comme tel : vérifie les preuves.
2. **Choisir** le premier ticket non validé dont les dépendances sont satisfaites. Définir sa tranche verticale jouable et ses tests d'acceptation.
3. **Implémenter** du vrai code et les données/assets minimaux requis ; ne pas ajouter des placeholders présentés comme fonctionnalité finie. Éviter les dépendances cycliques et séparer strictement code serveur/client.
4. **Vérifier** : tests unitaires, tests d'intégration ou GameTests quand applicables, compilation Gradle, lancement serveur dédié et client de développement quand les moyens existent. Tester scénarios de sauvegarde/rechargement, erreurs réseau et duplication selon `docs/05_TESTS_ET_SECURITE.md`. Conserver les commandes utilisées et leurs résultats.
5. **Réparer** les problèmes reproductibles, puis relancer les vérifications. Rechercher la cause racine ; ne désactive jamais un test pour « faire passer » un ticket.
6. **Documenter** précisément dans `docs/03_ETAT_DU_PROJET.md` : statut, commit, fichiers modifiés, tests réellement exécutés, preuves, problèmes connus, prochaine tâche. Ajouter une trace de validation sous `docs/validation/` si utile.
7. **Enregistrer** un commit Git local identifiable pour le ticket terminé, sans effacer ou réécrire le travail préexistant. Ne pas pousser vers un dépôt distant ni publier une release sans autorisation ou procédure déjà approuvée.
8. **Continuer** immédiatement avec le prochain ticket admissible tant que tu peux agir de manière sûre et utile.

Après chaque phase P0...P7, exécute une régression transversale et contrôle que les systèmes déjà implémentés fonctionnent toujours. Ne saute pas P4 pour créer plus vite six planètes. Les tickets sont des jalons de validation, pas seulement des cases à cocher.

## CRITÈRES D'ACHÈVEMENT DE LA V1.0

La mission n'est terminée que si toutes les fonctionnalités V1.0 des documents sont implémentées à la profondeur spécifiée, que toutes les tâches P0–P7 sont validées avec preuve, et que :

- `./gradlew build` réussit avec un JAR installable contenant réellement les systèmes ;
- les tests automatiques et les tests de non-régression passent ;
- le serveur dédié démarre avec le mod et les scénarios réseau sont testés ;
- une aventure complète est jouable : monde normal → progression Force → sabre → speeder → voyage vers Ilum et autres planètes → cristal → boss → retour → amélioration ;
- les six planètes et leurs douze boss existent réellement et sont testés ;
- les sauvegardes ne perdent pas les progrès, les cristaux, les constructions, ni les véhicules ;
- les ressources visuelles manquantes, les tests impossibles à exécuter et toutes les limites sont signalés explicitement ;
- les fichiers de configuration, instructions d'installation, manuel de test, changelog, crédits et rapport final sont fournis.

Si une validation demande une intervention humaine (ex. appréciation graphique, test manuel multijoueur, autorisation de réseau), **ne la prétends pas accomplie**. Prépare une procédure reproductible et consigne l'état `À valider par humain`.

## AUTONOMIE, LIMITES ET SÉCURITÉ

- Ne m'interromps pas pour choisir une classe, un nom de méthode, une structure de fichiers ou une valeur d'équilibrage non verrouillée. Décide et documente.
- Demande mon intervention seulement en cas de **blocage réel** : permission inaccessible, compte/secret, décision de produit contradictoire, action irréversible, dépendance non disponible ou impossibilité de vérification essentielle.
- N'utilise pas de commande destructive (suppression massive, `git reset --hard`, effacement de sauvegardes), de contournement des protections ou d'accès à des comptes externes sans autorisation.
- Respecte les règles de sandbox et les demandes d'approbation de l'outil ; n'essaie pas de désactiver la sécurité pour éviter une interruption.
- Ne transforme pas cette mission en boucle infinie : si les mêmes échecs reviennent sans progrès, documente les tentatives, isole le problème, essaye une autre approche justifiée puis arrête avec un vrai rapport de blocage si rien n'est défendable.
- Si tu atteins une limite de temps/contexte/budget, sauvegarde d'abord le code et un point de reprise détaillé. Ne marque jamais `TERMINÉ` uniquement parce que le temps est écoulé.

## PREMIÈRE ACTION IMMÉDIATE

1. Vérifie si un vrai projet Java/Gradle existe déjà ; lis `docs/03_ETAT_DU_PROJET.md` et l'historique Git.
2. S'il n'existe pas, commence **P0-01** (squelette NeoForge + compilation + lancement serveur), puis continue avec **P0-02**, **P0-03**, **P1**, **P2**, **P3**, **P4**, **P5**, **P6**, **P7** selon la roadmap.
3. Fournis du code, des tests et des commits ; ne termine pas cette exécution après avoir seulement planifié le travail.
4. À la fin de l'exécution, donne le statut précis : `V1.0 VALIDÉE` uniquement avec preuves, sinon `EN COURS` ou `BLOQUÉ`, accompagné du prochain ticket et de la commande de reprise.
