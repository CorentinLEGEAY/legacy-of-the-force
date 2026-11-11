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
| P1-04 à P7 | À faire | Voir roadmap officielle | Aucune phase gameplay validée |

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
3. P1-01, puis validation réseau transverse de P0/P1 ; ne pas déclarer P0 entièrement validée avant celle-ci.

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
