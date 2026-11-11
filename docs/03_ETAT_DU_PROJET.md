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
| P1-04 | Implémenté, validation partielle | Bloc réel, journal durable ; solo/dédié, crash HELD, mort/dimension/logout et transfert inter-chunks vérifiés | Réseau prolongé, unload pendant contrôle, claims tiers et profiling ouverts |
| P1-05 | Implémenté, validation partielle | R/H sur dédié et solo ; 33 JUnit et 19 GameTests cumulés | Deux clients prolongés, claims tiers et cycle de vie réseau à compléter |
| P2-01 | Implémenté, validation partielle | Atelier, six familles, deux émetteurs, consommation serveur, UUID et inventaire conservés après reload ; 44 JUnit/25 GameTests cumulés | Réseau graphique, crash/transferts et compléments P2 ouverts ; lame inactive |
| P2-02 à P7 | À faire | Voir roadmap officielle | Aucune phase gameplay validée globalement |

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

## Régression transverse P1 — 2026-11-11, 14:59
- P1-05 publié sur GitHub par push Git natif : `8e227bf`. L'authentification annoncée par l'utilisateur fonctionne désormais ; plus de blocage de publication.
- Test solo enregistré/fermé proprement à 14:57:24, les trois dimensions sauvegardées. Essai de préparation de bloc via inventaire créatif interrompu après manipulation de caméra ; **pas de validation du transfert de bloc en solo**. Seule la copie jetable du monde a été utilisée.
- Mini-plan réalisé : `BlockMoveGameTests.java` et gabarit `empty_cross_chunk.nbt` ; dépôt sur une vraie frontière de chunk, transfert inverse interrompu après sauvegarde des deux chunks et double récupération idempotente. Preuves dans `docs/validation/P1-04.md`.
- Dernière régression : build réussi, **33 JUnit et 20 GameTests requis réussis**, à 14:59:12. Pas de modification du code de production depuis le binaire P1-05 vérifié en solo.
- Limites restantes et prochaine tâche inchangées : réseau prolongé, bloc solo, unload naturel et robustesse des récompenses aux crashes avant validation globale de P1. Ne pas présenter les injections contrôlées comme un arrêt brutal du processus.

## P1-04 — cycle de vie dédié final (2026-11-11, 15:05)
- Test inter-chunks publié : `2f5299a`. Aucun changement du code de production depuis P1-05.
- Dernier binaire, vrai client ForceTesterTwo : saisie validée par raycast serveur, puis changement de dimension → source restaurée/projection supprimée ; nouvelle saisie et mort effective → même résultat ; réapparition → niveau/XP/alignement conservés.
- Nouvelle saisie et déconnexion réelle : session libérée, journal vide. Arrêt puis redémarrage dédié, chargement du chunk source : terre présente, aucune ancre ni projection. Ticket de forceload de test retiré, dédié et client arrêtés proprement.
- Rapport et extraits : `docs/validation/P1-04.md`, `docs/validation/P1_LIFECYCLE_EVIDENCE.txt`. Les premiers contrôles trop précoces (mort pendant transfert et condition immédiate après kick) sont explicitement écartés ; seules les preuves effectives sont retenues.
- Prochaine priorité : rendre l'attribution RPG robuste entre la sauvegarde joueur et celle du mob lors d'un crash, puis valider le bloc en solo et le réseau prolongé. Une simple marque « déjà payé » et un ledger joueur borné ne prouvent pas l'atomicité entre deux fichiers. Reprendre par lecture de `rpg/CombatRewards`, `KillCredit`, `PlayerProgress`, `core/operation/DurableOperationStore` et des exigences RPG ; mini-plan et tests d'interruption avant modification. Les autres limites P1 restent tracées ; P2–P7 non commencées.

## P1 RPG — récompenses durables (2026-11-11, 15:21)
- Tour précédent = progrès réel : code publié, preuves inter-chunks/cycle de vie. État initial de ce tour vérifié propre à `75f2a9f`.
- Mini-plan réalisé : `core/operation/AtomicNbtFile`, `rpg/RewardTransactions`, `RewardLifecycle`, révision additive dans `PlayerProgress` ; routage combat/boss/debug via la transaction ; messages FR/EN, commande opérateur et tests. Architecture documentée sans changer les règles d'XP/alignement.
- L'intention durable précède la mutation ; checkpoint par joueur et reçu par événement avant clôture. Rejeu idempotent ; ancien profil chargé en solo ou dédié rétabli selon révision. Reçus persistants indépendants du ledger de gameplay borné. Refus de boss pendant cooldown également conservé. Formats inconnus intacts ; erreurs suspendent la progression concernée et réclament récupération.
- **40 JUnit, 21 GameTests requis réussis**, build à 15:17:18 ; smoke dédié PASS ; JAR sans classes/structures de tests. Nouveau GameTest recrée le profil et le mob d'avant kill : un seul paiement, aucun paiement à un second acteur.
- Arrêt brutal réel du seul serveur PID 8680 : récompense de test +7 persistée au checkpoint révision 1 tandis que le fichier vanilla reste révision 0 (hash identique avant/après crash). Redémarrage + reconnexion : 1000007 XP cumulés et révision 1 ; récupération répétée sans double paiement ; sauvegarde finale correcte. Ce crash utilise la commande debug passant par la même transaction, pas un kill réel prétendu. Détails/extraits dans `docs/validation/P1_RPG_REPRISE.md` et `P1_RPG_CRASH_EVIDENCE.txt`.
- Limites : étapes intermédiaires couvertes par injections, pas par quatre interruptions de processus ; pas de preuve de panne électrique. Coût des fsync et croissance des reçus à profiler en P7. Le checkpoint ne journalise pas chaque tick d'énergie/Vanilla.
- **EN COURS**. Prochaine tâche : télékinésie de bloc en solo avec scénario reproductible, puis réseau prolongé et unload naturel/claims. P0/P1 non déclarées globalement validées, P2–P7 toujours non commencées. Reprise : `./scripts/gradle.ps1 build runGameTestServer --console=plain`, `./scripts/smoke-server.ps1` ; rapport ci-dessus pour les données de reprise.

## P1-04 — parcours solo fermé (2026-11-11, 15:31)
- Correction RPG publiée sur main : `7e7074a`. Arbre propre vérifié avant les essais suivants, aucune mutation de production durant ces essais.
- Mini-plan réalisé : fixture jetable de positionnement/contrôle, essai V réel, nouvelle visée, V dépôt, sauvegarde/rechargement, V/G restitution, preuves et procédure versionnées dans `docs/validation/fixtures/solo-block`, rapport P1-04 et `P1_SOLO_BLOCK_EVIDENCE.txt`.
- Sur serveur intégré : ancre HELD réellement détectée, source air + destination terre confirmées, projection absente et journal vide. Après rechargement du même monde, mêmes succès ; puis restitution G vérifiée. Énergie consommée pendant maintien, régénération après dépôt. Trois dimensions sauvegardées et client arrêté proprement.
- L'essai utilise une nouvelle copie isolée du monde ; activation des commandes correcte cette fois (recherche de tag ordinale), aucun EOF. La fixture modifie uniquement le terrain de cette copie, aucun monde d'origine.
- Derniers tests de code : **40 JUnit, 21 GameTests requis**, zéro échec ; smoke dédié PASS. Pas de nouvelle compilation nécessaire pour les seuls documents/fixtures copiés depuis le test déjà exécuté, exclus du JAR.
- Prochaine tâche : contrôle prolongé à deux clients et unload naturel pendant contrôle, puis intégration des protections. Éviter de répéter la même activation de fenêtre ayant causé les crashes natifs ; nouvelle approche justifiée ou validation humaine explicitement tracée. P0/P1 restent partielles ; ne pas annoncer la phase ni V1 terminée. P2–P7 non commencées.

## P1 — deux connexions et unload naturel (2026-11-11, 15:38)
- Tour = progrès vérifié, arbre propre initial à `56fb839`. Nouvelle hypothèse d'isolation : deux copies Java distinctes pour les deux clients, séparées aussi du Java Gradle/serveur. Avant, seul le deuxième client était isolé. Lancements locaux `.tools/client-one-isolated-launch.json` et `.tools/client-two-launch-resolved.json`, logs `client-one-p1-network.log` / `client-two-p1-network.log`.
- Deux clients réels connectés à 15:34:50/52, encore connectés au contrôle serveur 15:37:37. Premier client activé/capturé sans crash observé mais une demande Windows de pare-feu le recouvrait. Intervention utilisateur demandée puis réponse « ok ». Au contrôle suivant, l'outil a signalé un arrêt Computer Use par touche Échap physique. Aucune autre entrée graphique effectuée après ce signal. **Validation graphique prolongée à deux clients toujours ouverte** ; ne pas conclure que l'isolation résout définitivement les crashes.
- Côté serveur : saisie de terre par le premier joueur, tentative du second après expiration du délai global, un seul verrou conservé et énergie du second à 100. Annulation du premier → terre restaurée, journal vide. L'essai immédiat dans le même batch avait rencontré la cadence et n'est pas utilisé pour prouver le verrou ; la tentative différée est celle retenue.
- Saisie confirmée HELD à 15:37:07, éloignement des DEUX joueurs vers (400,99,400) dans la même dimension. Le contrôle hors portée s'interrompt, journal vide. À 15:37:37, `execute unless loaded` confirme le **déchargement naturel du chunk source**. Rechargement contrôlé via forceload, puis à 15:38:31 : source en terre, aucune projection. Ticket forceload retiré. Les joueurs ont été replacés au terrain de test. Des WARN vanilla « moved too quickly » ont suivi les téléportations de test ; pas de crash ni preuve de dégâts liée à ces avertissements.
- Périmètre exact : arrêt du contrôle avant déchargement lorsque les joueurs s'éloignent ; pas un déchargement forcé d'un chunk encore à portée d'un joueur. Cette séquence correspond au cycle naturel et prouve la conservation du bloc à la recharge. Extraits : `docs/validation/P1_NETWORK_UNLOAD_EVIDENCE.txt`.
- Point de reprise laissé vivant pour éviter de fermer les fenêtres après l'interruption utilisateur : serveur dev exec session **40564**, clients **83862** et **17784**. Vérifier d'abord les handles/processus avant toute reprise ; ne pas relancer s'ils tournent encore. Pas d'action GUI supplémentaire pendant ce tour. P0/P1 restent partielles, objectif actif, P2–P7 non commencées.

## Reprise graphique et P2-01 — 2026-11-11, 16:06
- Tour précédent = progrès : preuve d'unload et commit `4060a1f`. Arbre propre et trois processus de test effectivement vivants vérifiés avant reprise ; aucun lancement en double.
- Deux clients encore connectés à 15:40:12. Premier client : retour au jeu et saisie V réussis. Activation du deuxième → crash natif `glfw.dll+0x101ab`, rapport local `hs_err_pid16552.log`, déconnexion à 15:41:05. Isolation des DEUX JDK insuffisante. Pas de nouvelle boucle identique ; limite exacte et validation humaine/stable requise dans `docs/validation/P1_NATIVE_CLIENT_LIMIT.md`. Annulation du maintien, sauvegarde/arrêt dédié et fermeture du client restant effectués.
- Les contrats serveur P1 nécessaires au développement sont fonctionnels et testés. Pour continuer la mission autonome malgré cette validation graphique externe ouverte, P2-01 est développé **sans cocher P0/P1** ; les validations complètes restent des gates à fermer avant achèvement/release. Aucune dépendance gameplay n'a été simulée ni remplacée par un résultat plus faible.
- Mini-plan P2-01 réalisé : `lightsaber/SaberData`, `SaberPart`, `SaberCatalog`, `SaberContent`, `SaberWorkshopBlock/Menu`, configuration, `client/SaberWorkshopScreen`, enregistrement client séparé ; JSON de sept pièces, huit recettes, géométries originales, traductions, tests. Menu virtuel de sélection : aucun composant transféré dans une grille temporaire. Six pièces consommées ensemble côté serveur, un item versionné à UUID unique livré. Deux émetteurs réellement distincts ; pièce sélectionnée consommée. Cristal/lame/combat explicitement absents à ce stade.
- **44 JUnit et 25 GameTests requis réussis**, build à 15:56:24 ; smoke dédié PASS ; JAR sans tests. Régression P1 conservée. Cas d'erreur et consommation/replay/proximité/acteur/fermeture/persistance couverts. Source d'API épinglée consultée ; une erreur de compilation d'API Font corrigée avant les succès, aucun test retiré.
- En jeu solo : menu et modèles visibles, première fabrication 2→1 de chaque famille, choix d'émetteur modifie l'aperçu, deuxième variante reçue, demande sans pièces refusée. Deux UUID/émetteurs confirmés par NBT serveur ; sauvegarde/rechargement propre → snapshots d'inventaire **strictement identiques**. Preuves, procédure et limites dans `docs/validation/P2-01.md` et `P2-01_EVIDENCE.txt`. Profil de test seulement ; liaison Utiliser temporaire K restaurée après fermeture. Trois dimensions sauvegardées, client fermé, pas de processus Minecraft de test à laisser reprendre par erreur.
- P2-01 implémenté mais pas déclaré intégralement validé : réseau graphique et scénario crash/transfert d'objet à fermer ; aperçu statique, stats/combat et systèmes avancés restent à développer. **EN COURS**, aucune phase globale/V1 terminée. Prochaine tranche développable : **P2-02**, cristal bleu Ilum à UUID et composant versionné, insertion/remplacement sûr et lame colorée ; puis P2-03 combat et P2-04 lien. Continuer à conserver les validations P1 ouvertes, sans les oublier.

## P2-02 — cristal et lame jouables (2026-11-11, 16:39)
- P2-01 publié sur main : `c09fc5f`. Mini-plan réalisé : `KyberData/Item`, `CrystalExchange`, `SaberStats`, extensions des données/configuration/item/menu/écran, modèles et sons originaux, traductions, tests. Schéma et UUID stables, entrée inconnue conservée mais refusée. Septième emplacement cristal ; échange dans le même inventaire, ancien cristal retourné dans le slot consommé, priorité offhand et synchronisation explicite. Doublon local refusé ; remplacement éteint la lame.
- Activation autoritaire avec cooldown 10 ticks, géométries bleues lumineuses selon les deux émetteurs et sons synthétisés reproductibles. Stats calculées côté serveur mais **dégâts/parade non encore branchés** ; lien encore absent. Acquisition survie réservée à P4. Aucun son extrait ni nouvelle dépendance runtime.
- **48 JUnit et 29 GameTests requis réussis**, build final ; smoke dédié PASS ; JAR sans tests, SHA-256 dans `docs/validation/P2-02.md`. Tests d'échange en inventaire plein, offhand, doublon/format futur, replay, codec complet et activation. Régression P1 conservée.
- Solo réel : installation puis remplacement en atelier, un cristal libre et un embarqué ; allumage → lame bleue visible. Inventaire serveur avant/après sauvegarde/rechargement strictement identique (1677 caractères), UUID distincts et sabre P2-01 conservé, état actif conservé. Trois dimensions sauvegardées, client arrêté à 16:37:03 ; liaison Utiliser restaurée. Rapport, extraits et fixtures versionnés sous `docs/validation/`.
- Limites : contrôles doublons limités à l'inventaire de l'acteur, pas de ledger global ; pas de garantie de crash pour échanges entre plusieurs sauvegardes vanilla. Mort/déconnexion/dimension des objets rares, deux clients graphiques stables et appréciation sonore restent à valider. Aperçu statique, pas d'éclairage dynamique ni animation d'extension. Ces limites maintiennent le ticket et P2 globalement partiels, P0/P1 ouverts et V1 **EN COURS**.
- Prochaine tranche : **P2-03**, vrai combat serveur (dégâts, parade courte, endurance/cooldown) avec attribution RPG et tests ; puis P2-04 lien. Reprendre par références LIGHT-04/07, API RPG/protections et sources d'événements NeoForge épinglées ; mini-plan avant mutation. Commandes : `./scripts/gradle.ps1 build runGameTestServer --console=plain`, `./scripts/smoke-server.ps1`.
