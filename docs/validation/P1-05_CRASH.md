# P1-05 — arrêt brutal avec impulsion active

Résultat du 23 novembre 2026 : crash et reprise réussis, vérifiés indépendamment. Le déchargement natif du chunk d'entités est ensuite vérifié dans P1-05_UNLOAD.md ; clôture du socle P1-05/P1 dans P1_PHASE.md. Les faits ci-dessous ne valident aucune autre phase.

## Fixture et arrêt effectif

```powershell
.\scripts\test-adventure-preparation.ps1 -Run mic1 -Mode ImpulseCrash -PreparationRun ap4
.\scripts\test-adventure-preparation.ps1 -Run mir1 -Mode ImpulseRecover -PreparationRun mic1
```

La première commande copie ap4 arrêté ; aucune écriture dans ce monde source. Deux sockets natives non OP, niveau 15 attribué explicitement par la console de fixture (+1232 XP), inventaires de fabrication conservés. Vache à IA active, marche naturelle neutralisée par son attribut de vitesse. Régénération 0 et cooldown 1200 ticks lus dans les TOML du monde ; durée 8 ticks et coût inchangés. Le cooldown étendu permet aux clients de se reconnecter avant son expiration dans la nouvelle JVM.

Après observation indépendante de la cible par les deux ClientLevel et leurs confirmations de préparation au kill, le premier client envoie la commande de production push. Engagement au tick 2855 ; au tick 2857, la cible est réellement déplacée et active. Le harnais appelle saveEverything avec flush, écrit le checkpoint, puis **bloque son propre thread serveur pendant au maximum quinze secondes** pour donner au lanceur le temps de vérifier et tuer le processus. Cette pause artificielle de test empêche l'expiration de huit ticks avant le kill ; ce comportement ne fait pas partie du mod distribué.

Le lanceur vérifie le chemin absolu du monde, le PID Java, ses arguments serverRun, son heure de création et son écoute sur 127.0.0.1:25586. Seule cette JVM, PID **24876**, est tuée à **16:26:09.754 UTC**. Aucune commande stop. Le runner quitte avec 1, attendu ; clients 25584/12708 constatent une vraie coupure de socket avec motif et quittent normalement (0, aucun kill client).

Le monde interrompu est conservé. Relecture indépendante de sa région native d'entités : une seule vache du même UUID, position/Motion/attachments identiques au checkpoint, active=1, cooldownUntil=4055, vitesse horizontale z=0,33855308006290225. level.dat sauvegarde Time=2857. Ce sont des octets de sauvegarde vanilla, pas une simple simulation NBT d'un mob inventé.

## Reprise et contrôles

La seconde commande doit copier le monde interrompu arrêté, avec son verrou de session sondé, sans réinitialiser la cible, les profils, les inventaires ou le temps. Production onReload doit annuler active et le mouvement horizontal tout en conservant les immunités. Les clients utilisent ensuite les commandes de production : refus du détenteur et du pair sans paiement pendant le cooldown, attente de l'expiration naturelle puis nouvel usage payé et déplacement visible des deux côtés.

Le checker indépendant `scripts/check-mob-impulse-crash.py mic1 mir1` est exécuté : **PASS**. Il exige les résultats des lanceurs, six PID distincts, les régions originales encore actives, les playerdata avant/après, les deux vues clientes, les refus non payés et la réutilisation après expiration.

mir1 : nouveau serveur **5760**, clients **23236/12680**, trois sorties normales 0, aucun processus forcé. Fin du lanceur 16:28:23 UTC. Aucune nouvelle attribution d'XP, téléportation ou création de cible en reprise. La cible UUID `14e933ca-920c-4203-b8e4-b6e7368e8640` est inactive au chargement, vitesse horizontale nulle et position conservée z=255,55342150176918. Le timestamp d'immunité 4055 et le cooldown du détenteur survivent exactement ; le détenteur et le pair reçoivent cooldown sans nouvelle dépense. La marche naturelle de la fixture reste neutralisée, sans NoAI.

Au tick **4067**, après expiration naturelle (aucune modification du temps), une intention native du premier client engage la nouvelle poussée : z=257,31636924906223. Cooldown suivant 5267, nouvel engagement unique, énergie finale détenteur 77,0215388077956 et pair100. La cible reste vivante, santé10, inactive après la durée normale ; inventaires et progression identiques sauf les deux dépenses et le cooldown attendu. Les régions natives du monde interrompu conservent toujours l'état actif original ; celles de la reprise contiennent la seule cible finale inactive. Les deux clients ont une vue recovered et reused concordant avec le serveur. Capture reused du pair réellement inspectée : vache et HUD100 visibles.

Restent distincts : véritable déchargement natif d'une cible/chunk pendant contrôle, adaptateurs tiers de claims et charge P7. Ce scénario de panne ne certifie aucun de ces cas.

Régression exécutée après les essais : `scripts/gradle.ps1 build runGameTestServer --console=plain` : BUILD SUCCESSFUL en35s, 102 GameTests requis réussis à17:29:17, log `.tools/impulse-crash-mic1-regression.log`. Rapports JUnit131, zéro échec/erreur/ignoré ; tâche déjà à jour, pas une nouvelle exécution des JUnit inchangés. Parseurs des deux scripts PowerShell PASS. Relecture du serveur installé jarmr1 PASS sur le même JAR SHA256 `64A65E2DF3BB1D37FADA65F010F9681ADA426B58CAC451F30B4C2731342ED628`, aucun harnais distribué ; aucun nouveau boot installé revendiqué.
