# Récompenses RPG — reprise après crash

## Problème et solution

Le ledger borné du joueur et le champ `paid` du mob ne suffisaient pas : Minecraft sauvegarde joueurs et entités séparément. Un crash pouvait conserver une seule des deux mutations, ou faire réapparaître une ancienne entité après éviction de son UUID du ledger.

`RewardTransactions` calcule d'abord sur une copie du profil. Une intention versionnée et bornée est persistée avant mutation du profil vivant. Puis viennent le point de reprise par joueur (avec sa révision), le reçu de l'événement, la clôture du journal et enfin l'application au profil vivant. Chaque fichier est écrit via temporaire, fsync, déplacement atomique et relecture. Après interruption, le journal est rejoué avant de nouvelles récompenses. Au chargement du joueur, un attachment plus ancien est rétabli depuis le dernier point de reprise ; une révision égale préserve les changements ultérieurs d'énergie ou de mort.

Les kills de mobs utilisent leur UUID comme clé globale de reçu. Les boss utilisent une clé dérivée de l'événement ET du participant pour permettre le partage futur. Une victoire pendant le cooldown qui est mémorisée comme refusée est aussi persistée : elle ne devient pas payable en rejouant le même événement plus tard. La commande administrateur d'XP passe par le même mécanisme.

Le profil migré conserve `schemaVersion=1` avec `rewardRevision` facultatif, long positif ou nul, défaut 0. Les formats futurs/invalides ne sont pas écrasés. En cas d'erreur, le profil concerné est suspendu et l'opération conservée. `/lotf rpg recover`, OP 2 ou console, réessaie la récupération puis resynchronise les joueurs. Le reçu reste après nettoyage du journal temporaire ; l'historique de gameplay dans le profil reste borné à 256 événements.

## Vérifications réellement exécutées le 11 novembre 2026

- `./scripts/gradle.ps1 build runGameTestServer --console=plain` : **40 JUnit et 21 GameTests requis réussis**, BUILD SUCCESSFUL, fin 15:17:18. Log local `.tools/test-reward-transactions-final.log`.
- Sept tests unitaires supplémentaires : interruption après chacune des quatre étapes, reprise idempotente, refus du même mob pour un autre destinataire, reçu conservé après éviction de l'historique, énergie de mort préservée à révision égale, ancienne opération ne rétrogradant pas un checkpoint récent, formats inconnus intacts, révision saturée/refus sans mutation et victoire de boss refusée non rejouable. Un même test peut couvrir plusieurs assertions/scénarios.
- Nouveau GameTest : vrai kill de zombie, restauration d'un profil d'avant kill et recréation d'un mob non payé du même UUID ; total restant à 5 XP/+0,1. Deuxième acteur : 0 XP. Les autres tests de combat, Force, chute causale et blocs restent verts.
- `./scripts/smoke-server.ps1` : PASS, dédié démarré et arrêté proprement, toutes dimensions sauvegardées. Log `.tools/smoke-reward-transactions.log`.

## Arrêt brutal réel du dédié

1. Serveur loopback, vrai client ForceTesterTwo, niveau 100 de test. `save-all flush` à 15:19:13 ; NBT serveur et fichier joueur : `totalXpEarned=1000000`, `rewardRevision=0`.
2. Copie du fichier joueur conservée localement, puis `/execute as ForceTesterTwo run lotf debug add_xp 7`. À 15:19:31, profil vivant : total 1000007, révision 1. Cette commande teste le même coordinateur durable que les kills ; aucun kill réel n'est prétendu dans cet arrêt de processus.
3. PID **8680** identifié par sa ligne de commande `serverRunProgramArgs`, puis terminé brutalement avec Stop-Process -Force. Aucun autre processus arrêté. Échec du run Gradle attendu ; client déconnecté par reset de connexion.
4. Fichier joueur toujours à total 1000000/révision 0 ; SHA-256 identique à sa copie d'avant récompense : `E2C22C195A07857156B77286612C098F20AAC282F5F2E0D874CD6526D30AA98C`. Checkpoint durable : total 1000007/révision 1, reçu présent, journal temporaire vide.
5. Redémarrage du même monde, reconnexion réelle à 15:21:10. Requêtes serveur à 15:21:24 : total **1000007**, révision **1**. `/lotf rpg recover` exécuté, puis total encore **1000007**. Pas de second paiement.
6. `save-all flush`, arrêt propre : fichier joueur à total 1000007/révision 1 ; dossier `active` vide. Extraits : `P1_RPG_CRASH_EVIDENCE.txt`. Logs complets locaux `.tools/reward-crash-server-before.log`, `.tools/reward-crash-server-after.log`, `.tools/client-reward-crash.log`.

L'inspection locale initiale des tags longs utilisait une recherche de chaîne dépendante de la culture et a produit des nombres faux. Elle a été corrigée en comparaison ordinale ; les valeurs ci-dessus sont aussi confirmées par les requêtes NBT natives du serveur. Aucun fichier joueur n'a été modifié par cet inspecteur. Le même défaut de recherche constitue une cause probable de l'ancienne préparation manuelle du monde solo refusée EOF (P1-05) ; le monde original n'avait jamais été touché.

## Limites et suite

- Le crash réel couvre une récompense complètement journalisée avant la sauvegarde vanilla. Les quatre étapes intermédiaires sont couvertes par interruptions injectées, pas par quatre kills de processus distincts. Pas de promesse de tolérance à une panne électrique du système de fichiers.
- Le checkpoint restaure l'après-image du dernier événement si le profil vanilla est plus ancien ; il ne journalise pas chaque tick d'énergie ni chaque action vanilla. Une révision égale n'écrase jamais les mutations ultérieures. Les sauvegardes complètes, morts, dimensions et cooldowns restent à tester au fil des systèmes.
- Un reçu petit mais permanent par événement, répertoires répartis ; croissance disque et coût des écritures synchrones à profiler sous charge en P7. Le dossier est une partie de la sauvegarde du monde, pas un cache à effacer.
- Sauvegardes/imports partiels mélangeant profils d'un monde et reçus d'un autre hors procédure ne sont pas garantis. En cas de données inconnues/corrompues, les fichiers restent préservés et une intervention est nécessaire.
- Réseau prolongé à deux clients, télékinésie de bloc en solo et unload naturel pendant contrôle restent ouverts. Cette correction ne valide pas P1 globalement et n'implémente pas les fonctionnalités P2–P7.
