# P3-02 — Recharge payée de l'instance existante

## Implémentation du 2026-11-11

Références : SPEED-06/07/10, invariants de propriété et sauvegarde de l'architecture, tests de sécurité. Tranche : cellule craftable, paiement durable et recharge du véhicule existant, intégration au diagnostic. Fichiers : `RechargeTransaction`, `SpeederRechargeService`, reçus dans `SpeederContent`, lifecycle, entité/menu/écran/configuration, recette/modèle/langues et tests.

- Cellule : une unité issue d'un lingot de cuivre et de quatre redstones, recette shapeless nécessitant la table 3×3. Modèle minimal utilisant la texture vanilla de redstone par référence, aucun asset extrait ni téléchargement.
- `garage.energyPerCell` : 250 par défaut, borné 1–1 000 000. Une cellule par clic ; énergie finale plafonnée à la capacité. Excédent perdu, annoncé dans l'infobulle. Batterie déjà pleine = aucun débit. `garage.minimumRechargeIntervalTicks` : 40 par défaut, borné 1–1200, par joueur ; nettoyage au logout et à l'arrêt.
- Le bouton Recharger est dans le diagnostic. L'UUID est celui sélectionné par le serveur ; aucune énergie proposée par le client. Revalidation du propriétaire, garage/dimension/portée/protections aux deux positions, absence de pilote et de mouvement, curseur vide, schéma/catalogue compatibles. Cellule prise dans une copie de l'inventaire natif, y compris main secondaire ; round-trip exact requis avant mutation. HP, plan, UUID et extensions opaques conservés.

## Paiement et reprise

Journal borné à 256 Kio par joueur sous `data/legacyoftheforce/speeder_recharge/<uuid>.nbt`, écrit atomiquement avec fsync/relecture. Reçu versionné `legacyoftheforce:speeder_recharge_receipt` dans le fichier joueur, même codec immuable que le reçu d'assemblage mais attachment et chaîne de révision distincts ; copie à la mort. Aucune incrémentation de la révision d'assemblage.

PREPARED → gel de l'entité par `rechargeId` et sauvegarde native vérifiée → inventaire/reçu payés sauvegardés et relus → PAID → énergie cible absolue appliquée, gel conservé, sauvegarde native vérifiée → APPLIED → dégel sauvegardé → DONE. Exécution sur le thread serveur. Pas de tick de conduite entre ces étapes. Un état gelé refuse monte et entrées et ne consomme pas d'énergie.

Reprise au login et avant une opération : PREPARED sans paiement durable restaure l'inventaire antérieur prouvé, dégèle puis clôture ABORTED ; paiement durable prouvé termine la même opération et le même UUID. Une énergie cible absolue évite de cumuler deux fois un crédit. DONE/ABORTED ne contrôlent que leur reçu, sans réappliquer charge, pose, plan ou inventaire anciens. Aucune création d'entité lors de la reprise d'une recharge. Dimension/chunk de l'instance localisés par ticket temporaire natif borné si nécessaire, sans scan global ni forceload permanent. Fichier, reçu, véhicule ou catalogue incohérents : données conservées et connexion refusée si la reprise reste ambiguë.

## Tests réellement exécutés

- `./scripts/gradle.ps1 build runGameTestServer --console=plain` : réussite finale à 21:13:36, 83 JUnit et 60 GameTests requis. Rapport local `.tools/test-speeder-recharge-final.log`.
- Trois JUnit nouveaux : propriétaire/reçu/débit/instance en attente cohérents ; énergie finie, types NBT, taille et schémas stricts ; conservation des données inconnues, immutabilité et impossibilité de rouvrir DONE.
- Trois GameTests nouveaux : recharge réelle à partir de la main secondaire, charge875→1000 avec une cellule consommée, HP/UUID/extensions inchangés ; fichier natif joueur relu, reçu recharge1 et reçu assemblage0 ; rejeu rapide refusé ; nouvelle charge dépensée5 sauvegardée puis reprise de DONE sans remise à1000 ni second débit.
- Dix checkpoints injectent un `Error` après PREPARED, FROZEN, PAYMENT_MEMORY, PAYMENT_DURABLE, PAID, ENERGY_MEMORY, ENERGY_DURABLE, APPLIED, ACTIVATED et DONE. Nouvelle instance du service, double reprise, vérification des journaux, paiements et présence unique du même UUID dans le vrai stockage Minecraft. Les trois premiers restent impayés : deux cellules/charge100 ; les autres donnent une cellule/charge350. Après reprise, verrou absent et phase ABORTED ou DONE selon paiement prouvé.
- Refus sans consommation : batterie pleine avec deux cellules, protection, mouvement, cellule absente et curseur occupé. Tous ces cas exécutés sur le serveur GameTest avec les vrais inventaires et barrières de sauvegarde natives.
- Démarrage dédié à 21:14:08, client ForceTesterOne connecté à 21:14:22. Avant toute nouvelle entrée graphique, Computer Use a signalé un arrêt par Échap physique. Aucune action graphique de recharge ni fabrication de cellule effectuée. Serveur arrêté proprement à21:14:45, toutes dimensions sauvegardées ; client déconnecté par le serveur et laissé ouvert. Ne pas présenter cette connexion comme une preuve du nouveau parcours UI.
- JAR `build/libs/legacyoftheforce-0.1.0-dev.jar`, SHA256 `03A560C6B13869B3821CE0FC172AFFC43DC753F02D95F6190F172A16DF6432BC`.

Les dix erreurs injectées sont des simulations de coupure du flux Java sur le vrai stockage, **pas des terminaisons de processus**. Les trois vrais kills documentés dans P3-02_CRASH concernent l'assemblage. La campagne suivante [P3-02_RECHARGE_CRASH.md](P3-02_RECHARGE_CRASH.md) ajoute trois vraies terminaisons pendant cette recharge sur un dédié isolé sans client connecté ; elle complète ces tests sans valider le rendu ni le solo.

## Reprise manuelle reproductible restant à exécuter

### Complément automatisé du 2026-11-11 à21:19:43

`vanillaCraftedCellRechargesThroughTheGarageMenu` utilise les vrais menus vanilla et leurs clics serveur (pas de résultat de recette construit par le test). Inventaire initial : cuivre1/redstone4, aucune cellule. Clics PICKUP vers la grille3×3 : cuivre et seulement trois redstones ne donnent aucun résultat ; la quatrième redstone expose exactement une cellule. Clic QUICK_MOVE sur le vrai ResultSlot : ingrédients consommés, grille/résultat vides, une seule cellule dans l'inventaire. Rejeu du clic sur résultat vide : toujours une seule cellule.

Fermeture du menu de craft, ouverture de `SpeederGarageMenu` sans plan, bouton59 sélectionnant le UUID existant, attente de11ticks puis bouton60 : résultat CHARGED, énergie100→350 et cellule1→0. Rejeu immédiat refusé. Journal de production du serveur GameTest relu DONE avec même UUID, inventaire natif joueur égal à l'après-image. Après sauvegarde d'une énergie dépensée7, nouvelle instance du service et reprise : énergie7 conservée, aucune cellule recréée.

`./scripts/gradle.ps1 build runGameTestServer --console=plain` : 83 JUnit/61 GameTests requis réussis, BUILD SUCCESSFUL ; `.tools/test-speeder-recharge-crafting.log`. Aucune modification du code de production ni du JAR. Les APIs CraftingMenu/ResultSlot/CraftingInput ont été lues dans les sources NeoForge21.1.265 épinglées avant ce test. Cette preuve vérifie la recette et la logique des menus sur un vrai serveur Minecraft ; elle ne constitue pas une observation du rendu graphique, une reconnexion de client ou un redémarrage de processus.

La tentative graphique précédente à21:17 a été arrêtée par détection d'Échap dès l'activation, sans nouveau clic dans Minecraft ; serveur arrêté/sauvegardé proprement à21:17:42. Pas de répétition automatique de cette boucle de contrôle graphique dans le complément ci-dessus.

1. Relancer le dédié et reconnecter le client déjà ouvert ; un seul client graphique. ForceTesterOne est près du garage Nether `(1005,240,1065)`, emplacement sélectionné vide, papier du plan conservé en hotbar0. Son véhicule payé `32392c62-c238-4986-bfce-605b19b70ba3` conserve la charge873.7359999999953 au dernier diagnostic vérifié. Vérifier la valeur courante avant le test.
2. Ajouter une table de craft dans un emplacement libre et fournir uniquement les matériaux de test (1 cuivre, 4 redstones). Fabriquer réellement la cellule avec la recette 3×3 et constater le résultat. Aucun matériau/cellule n'a encore été fourni pour ce test graphique.
3. Garage → Diagnostic → vérifier le UUID/charge → Recharger. Attendre le résultat et contrôler le débit unique, la charge plafonnée1000 et le reçu recharge1 distinct du reçu assemblage5. Contrôler aussi le refus à batterie pleine.
4. Rouler normalement pour dépenser de la charge, fermer le client et arrêter le serveur proprement, redémarrer/reconnecter. Même UUID, charge dépensée conservée, aucun second paiement. Vérifier inventaire/reçus/journal/fichier natif, pas seulement le HUD.
5. Tester ensuite le solo, les transferts/mort/logout, les vraies terminaisons du Java à plusieurs étapes, le chargement du chunk de reprise et les refus sous protections tierces. Deux clients prolongés et profiling restent ouverts.

P3-02 et V1.0 restent EN COURS. Les dégâts/réparations P3-03 et le stockage virtuel de véhicule ne sont pas implémentés par cette tranche.
