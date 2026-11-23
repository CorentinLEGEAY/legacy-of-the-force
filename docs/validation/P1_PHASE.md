# P1 — audit du socle progression et Force

Audit du 23 novembre 2026, selon docs/02_ROADMAP.md, AGENTS.md, architecture, modules/rpg/SPEC.md et modules/force/SPEC.md. **Phase P1 validée pour le socle P1-01 à P1-05**, après preuves natives et régression transverse ci-dessous. Compétences avancées, sabres, véhicules, exploration complète et charge relèvent encore des phases suivantes. La V1.0 reste en cours.

| Exigence de roadmap | Preuves correspondantes | Périmètre vérifié |
|---|---|---|
| P1-01 : modèle sauvegardé, migration, synchronisation, énergie, HUD | P1-01.md, P1-01_NATIVE.md, SOLO_OWNER_SAVE.md ; PlayerProgressTest ; rp1→rr1 | Profil non trivial sur deux sockets, vraie mort/respawn et nouveau serveur, énergie et configuration, solo intégré et propriétaire sauvegardé, formats inconnus/corrompus, HUD minimal |
| P1-02 : XP, attribution, anti-ferme, niveaux 1–100 | P1-02-03.md, P1-02-03_NATIVE.md, P1_RPG_REPRISE.md ; tests RPG/journal ; mr3→mrr2, ms4 | Vraies morts et récompenses uniques, deux attaquants, environnement/FakePlayer refusés, historique anti-ferme conservé, solo, plafonds/overflow et récupération transactionnelle |
| P1-03 : dixièmes, bornes, seuils, éligibilité | P1-02-03_NATIVE.md, P4-03_NETWORK.md, P4-05.md ; AlignmentThresholdsTest et tests RPG | ±0,1 mob, ±25 boss Jedi/Sith, scores bornés, seuils TOML et traductions, éligibilité du contrat ; apprentissage des futurs pouvoirs réservé P5 |
| P1-04 : un bloc non-conteneur, saisie/dépôt, transaction, crash/logout, protections | P1-04.md, P1-04_NETWORK.md, P1-04_CRASH.md ; fb2, bk1→br1 | Bloc natif, ancre/journal/projection concordants, refus sans coût, verrou du pair, dépôt, révocation, logout, vrai kill HELD/reprise, solo et cas lifecycle/inter-chunks |
| P1-05 : poussée/attraction, distance, cooldown, résistance, synchronisation | P1-05.md, P1-05_NATIVE.md, P1-05_CRASH.md, P1-05_UNLOAD.md ; mi2, mic1→mir1, miu1 | Déplacement réel, six impulsions sur deux sockets, coûts, résistances/mur/NoAI/protection, contention, quatre interruptions, kill actif et reprise, solo historique ; déchargement/chargement natif du chunk d'entités |

Les preuves natives ne sont pas remplacées par des tests unitaires. Les checkers relisent les fichiers joueurs et régions vanilla, les preuves des JVM clientes et les résultats/logs des lanceurs. Les images mentionnées dans les rapports ont été inspectées pour leur périmètre ; aucune appréciation artistique générale n'en est déduite. Aucun lancement par activation de fenêtre Windows requis.

## Relecture transverse effectuée

Huit commandes réexécutées avec Python du runtime installé, toutes PASS :

```text
scripts/check-core-protocol.py cp3
scripts/check-progress-lifecycle.py rp1 --source ad4 --recovery rr1
scripts/check-mob-rewards.py mr3 --recovery mrr2 --file-config
scripts/check-mob-rewards.py ms4 --solo --file-config
scripts/check-block-network.py fb2
scripts/check-block-crash.py bk1 br1
scripts/check-mob-impulse-network.py mi2
scripts/check-mob-impulse-crash.py mic1 mir1
```

Ces relectures attestent que les preuves sont encore présentes et satisfont leurs assertions actuelles ; elles ne constituent pas huit nouveaux parcours dans Minecraft. Les tests automatisés courants et les nouveaux parcours natifs sont consignés séparément avec leurs résultats.

Neuvième contrôle exécuté : `scripts/check-mob-impulse-unload.py miu1`, PASS. Nouveau parcours natif miu1 : trois sorties0, stockage/retrait au2847, ancien objet arrêté au2848, nouvel objet chargé du disque au2849, immunité4045 conservée et nouvelle poussée au4057. Aucune sauvegarde ou état de cible réinjecté par le test.

Régression complète de fin de phase : `scripts/gradle.ps1 build runGameTestServer --console=plain`, BUILD SUCCESSFUL en36s, **102 GameTests requis réussis** à17:35:20, log `.tools/p1-unload-phase-regression.log`. Rapports JUnit : **131 tests, 0 échec/erreur/ignoré** ; tâche déjà à jour, sans prétendre une nouvelle exécution des JUnit inchangés. Parseur des deux scripts PowerShell PASS et diff sans erreurs d'espaces. JAR SHA256 `64A65E2DF3BB1D37FADA65F010F9681ADA426B58CAC451F30B4C2731342ED628` inchangé : `scripts/check-packaged-server.py jarmr1` PASS, deux boots installés antérieurs du même binaire, trois mixins requis appliqués et zéro harnais distribué.

P1-01/02/03/04 étaient déjà validés pour leurs tickets ; la consolidation ferme P1-05 et la phase minimale, sans transformer les restrictions des rapports en garanties universelles. Prochaine tâche non validée dans l'ordre de roadmap : **P2-01**, fermeture de l'assemblage de sabre, interfaces natives et cycle de vie/pannes, à partir des spécifications et du code déjà présents.

## Limites conservées

Pas de certification des mods tiers de claims, des pannes matérielles, de toutes les combinaisons de pannes ou d'une charge publique. Les hooks de permission sont exercés, sans prétendre une intégration tierce absente. Les fixtures administrateur, arènes jetables, paramètres particuliers et pauses de test restent explicités dans chaque rapport. Le déchargement contrôlé du gestionnaire d'entités ne prouve pas une éviction naturelle de terrain.

La finition graphique/HUD, l'évaluation humaine, les profils de charge, les groupes/projections, l'arbre complet et les pouvoirs avancés conservent leurs gates P5/P7. Aucun report ne supprime une exigence du périmètre V1.0. Même après clôture du socle P1, la V1.0 ne peut être déclarée terminée sans P2–P7, six planètes, douze boss et validation du JAR final.
