# P3-03 — Parcours graphique sur serveur dédié

## Périmètre du 2026-11-11

Minecraft 1.21.1, NeoForge 21.1.265, Java21 ; serveur de développement local et **un client graphique** ForceTesterOne. Commandes de console utilisées pour placer le joueur, fournir les matériaux de fixture et lire les données, pas pour exécuter les boutons de gameplay décrits ci-dessous. Vision nocturne temporaire pour distinguer les teintes au-dessus du Nether. Images observées dans Computer Use ; aucun fichier de capture conservé ni promis.

Véhicule déjà fabriqué et payé : `32392c62-c238-4986-bfce-605b19b70ba3`, propriétaire `9713e147-c047-3ed9-aaac-6df18d3deefa`. Garage `(1005,240,1065)`, plateforme de test et table de fabrication vanilla ajoutée en air. Les coordonnées sont celles d'une sauvegarde privée de test, pas un prérequis du mod.

## Parcours réellement observé

1. Connexion22:52:33. Diagnostic de la même instance, quatre modules100HP, charge873,736. Nouvel écran320×230 et inventaire accessibles à la résolution de fenêtre860×512.
2. Peinture par le vrai menu : primaire blanche puis secondaire orange, chacune consomme la teinture correspondante. Nuanciers et corps du châssis/siège blancs, moteur/batterie orange observés dans le monde. Papier réutilisable conservant sa palette précédente ; HP et énergie inchangés. Journal d'entretien schéma3/DONE, reçu révision2 ; inventaire et reçu natifs égaux à l'image après paiement.
3. Vraie table vanilla : cuivre1 + redstone4 produit la cellule d'énergie ; fer2 + cuivre1 + redstone1 produit la trousse. Résultats récupérés dans la barre rapide, puis inventaire confirmé par le serveur22:59:59. Aucun résultat de recette donné par commande dans cette étape.
4. Vrai bouton Recharger :873,736 →1000, cellule consommée, trousse conservée, reçu3/DONE schéma1. Nouveau clic : batterie pleine, aucun débit ni nouveau reçu.
5. Vrai coup de mêlée du propriétaire sur le moteur : HP100 →90, lecture serveur23:01:56. Diagnostic97%, un module abîmé sur quatre. Un deuxième clic a déplacé la caméra capturée et cassé une dalle de fixture : il **ne compte pas** comme un second coup sur le véhicule. Joueur créatif replacé, dalle restaurée ; aucun décès ni dommage supplémentaire du véhicule.
6. Vrai bouton Réparer : trousse consommée, moteur90 →100, énergie1000 inchangée, palette inchangée, reçu4/DONE schéma2. Nouveau clic : tous modules intacts, aucun coût. Images natives de paiement relues ; la trousse et la cellule ne sont plus dans l'inventaire.
7. Montage et pilotage : HUD vitesse/charge/intégrité100%/modules abîmés0/une place, libellés des touches AZERTY. Entrée Z puis déplacement réel : positionz1066,536 →1066,561 →1067,365 et lévitationy242 ; vitesse0,025 et charge945,6 puis853,4 lus du serveur. Une attente prolongée avec pilote a ensuite épuisé la batterie à0 : descente au soly240, sans explosion ni perte de HP.
8. Fermeture normale client/serveur puis lecture directe des fichiers natifs : exactement un UUID dans son chunk, quatre HP100, blanc/orange, charge0, aucun verrou et aucun RootVehicle dans le joueur. Inventaire/reçu égaux au journal DONE révision4. Redémarrage et connexion23:25:18 ; lecture serveur23:25:26 confirme encore charge0, HP/palette/UUID identiques. Aucun remboursement ni recharge depuis le reçu clos.

Traces locales ignorées par Git : `.tools/speeder-paint-native-client.log`, `.tools/speeder-paint-native-first-server.log`, `.tools/{paint,recharge,repair}-native-after-{journal.nbt,player.dat}`, `.tools/speeder-native-before-restart-entity.json`. Lecteur indépendant `scripts/read_nbt.py` et parseur MCA/zlib de `scripts/check-recharge-recovery.py`, appliqué à `world/DIM-1` pour cette instance.

## Corrections révélées par l'observation

Le pilote flottait au-dessus du coussin : l'ancrage utilisait le haut de la cellule abstraite. `SpeederEntity` calcule désormais le sommet du coussin du modèle original et compense l'attache vanilla et la hauteur du bassin. Sources épinglées PlayerRenderer/LivingEntityRenderer consultées : échelle0,9375 et translation1,501 ; bassin humanoïde12/16. GameTest vérifie bassin/coussin, siège décentré et rotation du véhicule.

Les libellés blancs des nuanciers clairs étaient peu lisibles : `SpeederGarageScreen` choisit maintenant une écriture sombre ou claire selon la luminance, sans ombre sur le fond coloré. Pas de changement du prix, des couleurs sauvegardées ou de la transaction.

Régression des corrections : `./scripts/gradle.ps1 build runGameTestServer --console=plain`, succès23:24:32, **90 JUnit sans échec/erreur et72 GameTests requis passent** (XML JUnit relus). Journal `.tools/speeder-native-fixes-tests.log`. JAR SHA256 `59FC9A27FB69FC11C6186DC45BD83CECED781D4F9674A022C524CE522A829694`.

**À terminer dans ce parcours :** observer graphiquement les deux corrections ; utiliser une cellule supplémentaire de fixture pour rouler brièvement, sauvegarder puis vérifier une charge dépensée positive après un second redémarrage. La cellule supplémentaire donnée par console après la première reconnexion n'est pas une seconde preuve de craft.

## Limites

Ce parcours n'est ni du solo ni deux clients simultanés. Mort, unload, protections tierces, charge réseau et vrais kills Java pendant réparation/peinture restent ouverts. Aucun succès de ces cas déduit d'une fermeture normale ou des Error injectées des GameTests. Recyclage, blindages/modules supplémentaires et les autres systèmes de la V1.0 restent à développer ; P3 et V1.0 EN COURS.
