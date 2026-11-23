# P2-01 — curseur externe et échanges coffre/tonneau

Date : 23 novembre 2026. Périmètre en cours ; cette tranche ne valide pas globalement P2-01 ni la V1.0.

## Implémentation

- Image personnelle version2 compatible1 : curseur externe distinct du curseur personnel, récupération différée persistante après fermeture pleine, crédit dans une place libre et sauvegarde vérifiée. Les nouvelles manipulations externes attendent cette récupération.
- Clics joueur↔coffre simple/double et tonneau avec sabre : avant-images natives, identité des blocs, vérification de conservation complète, journal, reçu joueur, barrières natives et reprise hors ligne. Une écriture incertaine déconnecte le propriétaire et conserve les preuves.
- Pendant une reprise, verrou des lectures/insertions/extractions normales et protection de casse/explosion. Reprise refuse un type/UUID de bloc remplacé ou un contenu qui ne correspond à aucune image du journal.
- Dépôt depuis un coffre : retrait durable vers le joueur, puis publication par le journal sabre/sol. Aucune restauration du coffre après validation de ce premier retrait.
- Corrections de relecture : accès interne au conteneur pendant le clic encadré, garde de réentrance et absence de rollback du coffre après retrait engagé. Ces changements ne sont pas chargés à chaud dans une instance déjà lancée.

## Vérifications

Les tests utilisent de vrais BlockEntity, clicked vanilla et fichiers natifs. Les ServerPlayer sont des acteurs de test avec connexion factice : **ce ne sont pas deux clients réseau**.

`SaberExternalCursorGameTests` couvre migration1→2, deux curseurs avec quatre entrées et inventaire plein, fermeture pleine/reprise, refus Windows réel pendant crédit différé. Régression initiale `.tools/saber-external-cursor-regression1.log` :124 GameTests requis réussis.

`SaberStorageGameTests` couvre aller-retour coffre/curseur et reçus1→2, shift-clic tonneau dans les deux sens en conservant le fer voisin, double coffre, THROW depuis coffre et ses deux reçus, métadonnées futures conservées, verrou Windows réel après paiement du joueur et reprise par le tick normal. Le verrou partage Windows provoque un vrai refus de remplacement du journal, sans modifier les ACL.

Deux premiers passages ont révélé des erreurs du nouveau test tonneau : il comptait tous les stacks du conteneur au lieu des sabres, puis supposait une case de destination précise pour le shift-clic vanilla. Assertions corrigées pour vérifier identité, quantité et composants sans imposer une case. Logs conservés : `.tools/saber-storage-isolated1.log` et `isolated2.log`. Aucun échec masqué ni test désactivé.

Build isolé pour préserver l'instance du concepteur et ses fichiers chargés :

```powershell
.\scripts\gradle.ps1 --project-cache-dir .tools/gradle-validation-cache --init-script scripts/validation-isolated.init.gradle build runGameTestServer --console=plain
.\scripts\test-packaged-server.ps1 -Run st1 -BuildDirectory .tools/build-validation
```

Résultats finaux : `.tools/saber-storage-isolated3.log`, BUILD SUCCESSFUL1m26s,130 GameTests requis réussis à21:15:28 ;133 tests unitaires réussis (33 suites,0 échec/erreur). Le dernier changement Java de production était déjà présent dans ce build.

JAR SHA256 `A9DA80B03BF2A34A68C32360BF87C5B46D146139797E322E1F4A89AAB2441BC6`, sous `.tools/build-validation/libs/legacyoftheforce-0.1.0-dev.jar`. Serveur installé st1 : deux sorties normales, mais contrôle strict incomplet car le scénario ne chargeait pas CompoundContainer. Ajout d'un double coffre et hopper vides au monde de smoke (fixture explicite, aucun transfert rare prétendu), puis st2 : deux nouvelles JVM21616/3440, sorties0 sans kill, sauvegardes natives relues, Ilum chargé, neuf mixins appliqués, aucun harnais de test distribué. `check-packaged-server.py st2` PASS. Le flux d'eau et les blocs coffre/hopper sont conservés après redémarrage.

Les sorties et mondes st1/st2 sont conservés. Cette vérification du JAR prouve son chargement et ses mixins ; les manipulations d'objets ci-dessus sont prouvées par les GameTests, pas par le smoke vide. L'instance utilisateur27400, lancée avant les derniers correctifs, reste ouverte sur écran3 et n'a pas été rechargée ou pilotée.

## Limites et prochaine validation

### Parcours natif à deux clients — sn1

Ajout de `SaberStorageNetworkValidation` et `SaberStorageNetworkClientDriver` exclusivement sous gameTest, modes SaberStorage/SaberStorageRecover des lanceurs et `check-saber-storage-network.py`. Le harnais place seulement un double coffre et un tonneau vides dans une copie arrêtée sw2. Il ne déplace pas les profils et n'attribue ni item ni XP. Les clients non-OP ouvrent les blocs et envoient les vrais clics vanilla ; les accusés de réception de test ne modifient pas les inventaires.

sn1 : serveur23984, clients8920/18604 ; trois sorties0, sans arrêt forcé, monde sauvegardé. Pour chaque acteur : dépôt de son sabre existant, retrait au curseur externe, redépôt puis fermeture. Les reçus progressent0→1→2→3. Vérificateur indépendant PASS : quatre UUID uniques et composants conservés, deux sabres dans les régions et les deux autres chez les joueurs, profils RPG identiques, aucun journal ni sabre au sol. Les projections clientes concordent avec chaque image native. Les logs confirment DISPLAY3 pour les deux fenêtres.

Capture `run-adventure-client-one-sn1/screenshots/saber-storage-withdrawn.png` réellement inspectée : grand coffre vide, sabre sur le curseur et second sabre dans l'inventaire. Les notifications vanilla recouvrent le coin supérieur droit ; l'image n'est pas présentée comme une validation artistique finale. Chaque phase possède sa capture framebuffer native. Compilation/build du harnais : `.tools/saber-storage-network-build1.log`, réussite32s.

Commandes reproductibles (identifiants neufs requis, conserver les anciennes copies) :

```powershell
.\scripts\test-adventure-preparation.ps1 -Run sn1 -Mode SaberStorage -PreparationRun sw2
python scripts/check-saber-storage-network.py sn1 --source sw2
.\scripts\test-adventure-preparation.ps1 -Run snr1 -Mode SaberStorageRecover -PreparationRun sn1
python scripts/check-saber-storage-network.py snr1 --source sn1 --recovery
```

snr1 : serveur13792, clients25376/12992 ; trois sorties0, sans arrêt forcé. Nouvelle JVM sur la copie arrêtée sn1, aucune réinjection de conteneur ou d'objet. Chaque client ouvre son stockage persistant, retire le sabre, le redépose et ferme ; reçus3→4→5. Vérificateur indépendant PASS : quatre UUID/composants, mêmes identités des trois BlockEntity, mêmes contenus des régions après le nouveau cycle, profils RPG inchangés et aucun journal résiduel. La capture `run-adventure-client-two-snr1/screenshots/saber-storage-stored.png` a été réellement inspectée : sabre dans le tonneau, autre sabre en inventaire ; notifications vanilla visibles, pas de validation artistique finale.

Les deux vérificateurs ont été exécutés après renforcement de la comparaison de conservation à chaque image et de la séquence exacte des reçus. JAR racine relu : SHA256 identique à `A9DA80B03BF2A34A68C32360BF87C5B46D146139797E322E1F4A89AAB2441BC6` ; les nouveaux harnais restent exclus. Aucun changement de production depuis la régression130GT/133JUnit et le JAR installé st2.

Le parcours normal à deux sockets et son redémarrage sont prouvés ci-dessus. Restent les reprises dans une JVM distincte après kill aux frontières, refus d'écriture avant/après paiement, déconnexion finale et reprise ; recouper inventaires, régions, reçus et UUID sans injecter de second objet. Vérifier également le double coffre à cheval sur deux chunks. Un redémarrage propre ne prouve pas ces pannes.

Les hoppers automatiques, autres menus, mort sans keepInventory, cristaux libres et autres objets rares restent des frontières à traiter. Le verrou protège un conteneur en attente ; il ne constitue pas un journal des transferts automatiques au repos. L'atelier complet et la finition artistique approfondie restent ouverts. Aucune phase supplémentaire n'est déclarée validée sur la base de ce rapport.
