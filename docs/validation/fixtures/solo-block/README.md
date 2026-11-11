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
