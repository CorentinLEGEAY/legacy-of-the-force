# Scénario jetable : transfert de bloc en solo

Ce datapack de test ne s'exécute pas automatiquement. **L'utiliser seulement dans une copie jetable du monde** : `setup` élimine les vaches du Nether chargé et remplace la zone x=-3..4, y=98..103, z=1..7. Il n'entre pas dans le JAR du mod.

1. Minecraft 1.21.1/NeoForge épinglé, mod de développement actuel, profil de test niveau ≥15, commandes autorisées dans la copie.
2. Copier ce dossier entier sous `saves/<copie>/datapacks/lotf_validation`, avant de charger la copie. Pack de données format 48, vérifié dans les sources Minecraft 1.21.1.
3. `/function lotf_validation:setup` : place le joueur et un bloc de terre, libère la destination. Attendre `SOLO_FIXTURE_READY`.
4. Appuyer V ; `/function lotf_validation:held` doit annoncer `SOLO_BLOCK_HELD` (vérification de la vraie ancre, pas de la seule projection).
5. `/function lotf_validation:turn` : change uniquement position/rotation du joueur dans la même dimension. Attendre une seconde que le bloc atteigne le point de visée.
6. Appuyer V pour déposer. `/function lotf_validation:verify` doit annoncer `SOLO_BLOCK_TRANSFER_PASS` et `SOLO_NO_ORPHAN_DISPLAY`. La condition vérifie la source (0,99,3) vide et la destination (2,99,2) en terre.
7. Sauvegarder/quitter, recharger la même copie, exécuter seulement `verify` : mêmes deux succès, journal `data/legacyoftheforce/block_moves` vide.
8. Visée conservée sur la terre déposée : V puis G. Exécuter `verify` : mêmes succès après restitution.

Les fonctions de préparation ne déplacent aucun bloc par la Force à la place du joueur. Les saisies/dépôts/annulations sont déclenchés par les touches réelles. Elles isolent le terrain, positionnent la caméra et lisent le résultat serveur.

## Atelier P2-01

Avec le binaire contenant P2-01, `/function lotf_validation:saber_setup` appelle la même préparation jetable puis place un atelier et donne deux pièces de chaque type, dont les deux émetteurs. Ouvrir l'atelier par clic droit ; assembler la variante standard puis choisir Griffes et assembler. Les pièces communes passent de 2 à 0, les deux émetteurs de 2 à 1, et deux poignées sont livrées. Une demande supplémentaire doit afficher le manque de pièces. Ne pas rejouer la préparation pour vérifier la persistance : sauvegarder/quitter, recharger, comparer `/data get entity @s Inventory`. Voir `P2-01.md` pour le périmètre exact.

## Cristal P2-02

Après assemblage, `kyber_setup` replace le joueur devant l'atelier et donne deux cristaux ; elle ne nettoie pas l'inventaire et doit être appelée une seule fois pour ce test. Tenir le sabre, ouvrir l'atelier puis cliquer Cristal deux fois avec un délai : installé, puis échangé. Fermer et appeler `kyber_turn` pour viser hors atelier. Utiliser allume la lame ; comparer l'inventaire serveur avant/après sauvegarde/rechargement sans rejouer la préparation. Le cristal retiré doit être libre, l'autre embarqué, chacun avec son UUID propre. Voir `P2-02.md`.

## Combat P2-03

Tenir un sabre actif. Sur la copie jetable uniquement, `combat_setup` passe en survie, positionne le joueur, place un petit sol de pierre et invoque une vache immobile à 10 PV. Attendre la charge complète puis attaquer normalement : `/data get entity @e[tag=lotf_saber_target,limit=1,sort=nearest] Health` doit afficher 2,5 avec les valeurs par défaut. B ouvre la parade et coûte 12 points. Inspecter `/data get entity @s "neoforge:attachments"."legacyoftheforce:saber_endurance"` avant/après sauvegarde ; tenir compte du temps de régénération. Ne pas rejouer la préparation pour comparer un rechargement, sinon une deuxième cible serait créée. Voir `P2-03.md` pour les réglages temporaires utilisés et les tests non encore clos.

## Rituel P2-04

`bond_setup` replace le joueur devant l'atelier en Nether et donne exactement quatre améthystes et deux lapis, sans toucher au cristal. Tenir un sabre avec cristal dormant, ouvrir l'atelier, cliquer Lier : matériaux consommés, cristal éveillé, UUID inchangés. Ne pas répéter la fixture pour tester la persistance ; les matières supplémentaires ne constituent pas une récompense de gameplay. Sauvegarder/quitter et inspecter le même composant après reprise. Seule une copie jetable doit recevoir cette préparation.
