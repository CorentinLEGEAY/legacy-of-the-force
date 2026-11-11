# 03 — Journal d'avancement

**Statut : EN COURS — infrastructure P0 opérationnelle, V1.0 non validée.**

## Configuration vérifiée (2026-11-11)
- Minecraft 1.21.1 ; NeoForge 21.1.265 ; ModDevGradle 2.0.148 ; Gradle 9.2.1.
- JDK local Temurin 21.0.13+9 x64 dans `.tools/java` (non versionné).
- Dépôt initial : uniquement spécifications, sans `.git`, ni Java dans PATH.
- Dépôt Git initialisé ; origin : https://github.com/CorentinLEGEAY/legacy-of-the-force.git ; distant vide constaté par `git ls-remote`.
- Commit de conservation des spécifications : `3d5334a`. Identité technique des commits : Codex (aucune identité personnelle présumée).

## Suivi
| Ticket | Statut | Réalisation / preuves | Limites |
|---|---|---|---|
| P0-01 | VALIDÉ (amorçage) | `build` réussi ; JAR 0.1.0-dev ; client observé au menu et mod à l'état loaded ; serveur Done, stop et sauvegarde | Aucun gameplay encore implémenté |
| P0-02 | Implémenté, validation réseau partielle | Packages, configurations serveur/client séparées, événement métier immuable, protocole 1, intention diagnostic bornée, contrôle OP/anti-rejeu/taux et nettoyage connexion | Paquets malveillants et protocole à deux clients à tester en situation ; contrats métiers ajoutés au fil des tickets |
| P0-03 | VALIDÉ (socle de tests) | 3 tests JUnit anti-rejeu/taux/bornes ; script smoke dédié, arrêt propre et redémarrage du même monde | Pas de tests de gameplay tant que systèmes absents |
| P1-01 | Implémenté, validation partielle | Profil, NBT, HUD et énergie vérifiés sur dédié, mort, dimension, redémarrage | Solo et deux clients stables ouverts |
| P1-02 / P1-03 | Implémentés, validation partielle | 19 JUnit + 4 GameTests : attribution, XP, alignement, anti-farm, cooldown | Solo, clients stables et crash disque ouverts |
| P1-04 | Implémenté, validation partielle | Bloc réel, journal durable, 12 GameTests cumulés, essai V et crash HELD réel | Voir rapport P1-04 ; solo et validations transverses ouverts |
| P1-05 | Implémenté, validation partielle | R/H sur dédié et solo ; 33 JUnit et 19 GameTests cumulés | Deux clients prolongés, claims tiers et cycle de vie réseau à compléter |
| P2 à P7 | À faire | Voir roadmap officielle | Aucune phase gameplay validée globalement |

## Mini-plans et fichiers
- P0 : `build.gradle`, `gradle.properties`, wrapper, métadonnées, `LegacyOfTheForce`, `core/{config,network,security,api}`, `client/ClientBootstrap`, `scripts/`, tests et procédures sous `docs/validation`.
- P1-01 : `rpg/PlayerProgress`, attachment versionné, service serveur, snapshot `core/api`, payload S→C et HUD sous `client`. Tests de sérialisation, refus schéma futur, énergie, mort/reconnexion/dimension. Les systèmes de combat ne seront pas anticipés dans ce ticket.

## Vérifications réellement exécutées
- `./scripts/gradle.ps1 build --console=plain` : succès, 3 tests, 0 échec.
- Première erreur réseau : `ByteBufCodecs.LONG` absent en 1.21.1 ; corrigé en `VAR_LONG`, recompilation réussie.
- `./scripts/gradle.ps1 runServer --console=plain` : mod chargé, serveur prêt ; second lancement puis `stop` : toutes dimensions sauvegardées. Entrée standard JavaExec explicitement reliée à Gradle après constat que stop n'était pas transmis par défaut.
- `./scripts/smoke-server.ps1` : PASS, monde déterministe isolé, écoute 127.0.0.1:25575, arrêt propre. Relancé sur le même monde.
- `./scripts/gradle.ps1 runClient --console=plain` : client démarré et fermé par Quit Game ; écran Mods observé avec Legacy of the Force, modid correct, état loaded.
- Test deux clients, crash de sauvegarde RPG et permissions des futurs pouvoirs : NON EXÉCUTÉS.

## Décisions et limites
- Les exemples de prompts limités à un ticket dans docs/08 ne remplacent pas la mission utilisateur explicite de poursuite autonome.
- Gradle génère un avertissement de dépréciation pour Gradle 10 ; version 9.2.1 épinglée et fonctionnelle.
- Les runs de développement NeoForge désactivent l'authentification Minecraft ; smoke limité à loopback. Ce mode n'est pas une configuration de production.
- Les options budgets Force sont une infrastructure ; aucun pouvoir n'est présenté comme implémenté.
- Aucun asset de film/jeu ajouté ; aucun mod tiers obligatoire.

## Reprise
1. `./scripts/gradle.ps1 build --console=plain`
2. `./scripts/smoke-server.ps1`
3. Fermer les validations transverses P0/P1 détaillées dans les dernières entrées et `docs/validation/P1-05.md`, puis P2-01. Ne pas déclarer une phase entièrement validée avant ses preuves.

## Avancement P1-01 — 2026-11-11
- Commit P0 : `a340dac`.
- P1-01 : implémenté et testé sur serveur dédié, en attente de validation complète solo/deux clients. Fichiers : attachment RPG versionné, snapshot immuable, S→C, HUD, configurations, commandes, tests NBT/protocole. Voir `docs/validation/P1-01.md`.
- 11 tests automatisés passent ; démarrage serveur et smoke passent. Vérification en jeu : 100→50 énergie puis régénération, refus sans OP, mort/respawn, Nether, sauvegarde/redémarrage/reconnexion à 37/100.
- Deux connexions simultanées réelles obtenues ; second client crash natif GLFW deux fois. Test prolongé à deux clients NON validé. Rapports locaux conservés ; ne pas boucler sans nouvelle hypothèse.
- Push GitHub demandé par l'utilisateur tenté : en attente d'authentification Git Credential Manager. Le distant reste vide au dernier contrôle. Demande d'intervention envoyée ; le travail local continue.
- P0 demeure partiellement validée pour le réseau ; aucune phase P1–P7 validée globalement.
- Prochaine tranche développable : P1-02 (XP, attribution, anti-farm), puis P1-03 (alignement), tout en gardant les validations transverses ouvertes. Aucun saut vers P2–P7.


## Avancement P1-02 / P1-03 — 2026-11-11
- Commit P1-01 : `d7a322b`.
- XP et alignement réellement attribués sur événements serveur, pas par un paquet client. Cas mob volontaire, passif, élite, mort automatique et deux attaquants couverts par quatre GameTests Minecraft.
- Régression complète : `build runGameTestServer` réussit, 19 JUnit et 4 GameTests requis réussis. Rapport `docs/validation/P1-02-03.md`.
- Bornes niveau/alignement, éligibilité de pouvoirs, historique borné, anti-farm et cooldown boss sauvegardés ; JAR inspecté sans classes de tests.
- Limite de robustesse : atomicité des deux sauvegardes joueur/mob lors d'un arrêt brutal non prouvée. À traiter avant validation de phase/release ; historique borné ne suffit pas seul à garantir déduplication infinie.
- Prochain ticket : **P1-04**. Avant implémentation, définir un transfert de bloc avec marqueurs de transaction et barrières de sauvegarde : un simple SavedData séparé du chunk ne prouve pas l'absence de duplication après crash. Préserver coffres/claims/spawn ; interface de permission refusant par défaut les cas inconnus.
- Push GitHub toujours en attente d'authentification au dernier contrôle ; utilisateur a indiqué connecter Git. Aucun push déclaré réussi.

## Point de contrôle GitHub et P1-04 — 2026-11-11
- Code publié par connecteur GitHub autorisé : `693661b27abeed2ecd05be99c79ab85fe4fb2634` sur main. Fetch vérifié : même code que a6e3085 plus le document d'initialisation. Historiques réunis localement par `27d2044`, sans effacer les commits de tickets.
- Ancienne tentative de push Git native encore en attente de son authentification ; elle visait un ancien commit et pourra être refusée sans effet destructif. Après connexion, pousser le HEAD courant, jamais forcer.
- P1-04 : socle permissions/journal réalisé et testé, télékinésie non implémentée, ticket non validé. Point de reprise : `docs/validation/P1-04_REPRISE.md`.
- Dernier build : succès avec 24 JUnit, 0 échec. Dernier GameTest serveur : 5 requis réussis. Smoke dédié : PASS.
- Global : EN COURS ; validations complètes P0 réseau et P1 ouvertes ; aucun sabre, speeder, planète ou boss implémenté.
- Prochain travail : ancre/coordinateur de transfert P1-04, puis P1-05. Reprise : `./scripts/gradle.ps1 build runGameTestServer --console=plain`, puis procédure P1-04_REPRISE.

## P1-04 — intégration jouable (2026-11-11)
- Tranche réalisée : ancre versionnée, projection du vrai BlockState, coordinateur journal/chunks, barrières synchronisées et relues, validations/permissions/collisions/énergie, touches et commandes, gestion du cycle de vie.
- Fichiers principaux : `force/BlockMoveCoordinator`, `BlockMoveRecord`, `ForceModule`, `ForceConfig`, `content/Transaction*`, `WorldTransactionAnchors`, `core/operation/VerifiedChunkSaveBarrier`, `client/ForceKeys`, API partagées et tags/ressources FR/EN. RPG suspend la régénération pendant canalisation.
- Preuves : build, 25 JUnit, 12 GameTests requis ; smoke dédié PASS. En jeu : V saisit/déplace/dépose, source vide et destination terre vérifiées par serveur. Arrêt brutal vérifié du seul serveur de test pendant HELD puis redémarrage : terre restituée, aucun display orphelin, journal fermé.
- Rapport détaillé et périmètre exact : `docs/validation/P1-04.md`, extraits `P1-04_EVIDENCE.txt`. Test réel HELD avant les derniers durcissements, tests automatiques après ceux-ci ; aucun résultat inventé pour le binaire final ou d'autres phases de crash.
- P1-04 implémenté mais validation totale encore ouverte (solo, deux clients stables, vrais claims, unload/cross-chunk). P1 n'est donc pas encore validée globalement. Aucun ticket P2–P7 commencé.
- Prochaine tranche admissible : P1-05 poussée/attraction de mobs, via contrat de progression/énergie et permissions, sans import des internes RPG. Ensuite régression P1 et fermeture des validations ouvertes avant P2.

## P1-05 — impulsions jouables (2026-11-11)
- Mini-plan réalisé : `force/MobForceController`, adaptateur et état versionné, mathématique bornée ; API de permission entités et événements partagés ; `rpg/PowerCooldowns` et crédit causal de chute ; touches R/H et ressources FR/EN. Référence : `modules/force/SPEC.md` FORCE-06, architecture et exigences de sécurité P1.
- Poussée niveau 5, attraction 15 : ciblage serveur, masse/résistance/coût/cooldown partagés, budget et permissions revalidées ; anti-perma-stun et arrêt du contrôle au rechargement. Joueurs, NoAI, boss et cibles protégées refusés. Pas de nouveaux dégâts directs inventés : chute Vanilla attribuée via RPG dans une fenêtre courte.
- Preuves finales : **33 JUnit, 19 GameTests requis**, build réussi ; smoke dédié PASS ; JAR sans tests. R/H vérifiés sur dédié avant dernier durcissement et **solo sur binaire final** : recul/attraction réels, HUD/énergie et profil conservé. Rapport et commandes : `docs/validation/P1-05.md`.
- Corrections issues des essais : snapshot émis uniquement sur canal négocié ; fixture de mock conservant la connexion officielle ; contact au sol ne neutralisant pas l'impulsion horizontale ; refus sans coût des mobs NoAI. Un test sur sol déjà stabilisé complète celui de chute naturelle.
- Nouveau test deux clients : connexions simultanées et captures obtenues ; premier client arrêté pendant activation, code natif Windows -1073740791. La copie d'exécutable Java pour le second n'a pas suffi. Ne pas déclarer ce parcours validé ; ne pas boucler sur la même hypothèse. Demande de pare-feu traitée par l'utilisateur, aucun réglage de sécurité automatisé.
- Le test solo utilise une copie isolée du monde dédié. Première préparation manuelle de la copie refusée EOF ; restauration Vanilla de sa sauvegarde précédente réussie. Monde dédié original intact ; incident et périmètre documentés.
- **EN COURS**, P0/P1 partiellement validées ; P2–P7 absentes. Prochaine tâche : validations transverses de P1 (solo bloc, deux clients, cycle de vie/unload/cross-chunk, sauvegardes RPG et adaptateurs de claims) ; ensuite P2-01. Commandes de reprise : `./scripts/gradle.ps1 build runGameTestServer --console=plain`, `./scripts/smoke-server.ps1`.
