# P2-01 — interruptions natives du retrait depuis le stockage

23 novembre2026. Périmètre : retrait d'un sabre identifié depuis le double coffre de sn1, pair connecté sans transfert. Aucun item, XP, changement de position ni restauration par la fixture. Les quatre frontières sont celles du coordinateur de production : PREPARED, PLAYER_DURABLE, STORAGE_DURABLE et CLOSED.

## Méthode

`SaberStorageCrashValidation` et `SaberStorageCrashClientDriver` sont exclusivement sous gameTest. Deux sockets non-OP ouvrent et observent les conteneurs existants. Après leurs deux observations, seul le premier client envoie le vrai clic vanilla de retrait. Le listener lit le journal, les deux playerdata natifs et les trois BlockEntity dans la région ; il n'effectue pas de sauvegarde de gameplay supplémentaire à la frontière. Le checkpoint est écrit dans un fichier de validation distinct. Le lanceur vérifie le PID Java, le monde isolé, le port loopback qu'il possède et la date de lancement avant de tuer ce seul serveur pendant une pause bornée.

La nouvelle JVM utilise le coordinateur normal pour reprendre hors ligne. Deux nouveaux clients observent ensuite leurs inventaires et les conteneurs ; ils ne déplacent pas d'objet. Le vérificateur indépendant recoupe fichiers, composants, reçus, identités de bloc, projections clientes, sorties et absence de journal résiduel. PREPARED attend le reçu3 et le sabre encore au coffre. Les trois frontières payées attendent reçu4, sabre chez le propriétaire après récupération normale du curseur externe et aucune copie au coffre. Le pair garde son reçu3 et son sabre stocké.

## Exécutions conservées

- sc1 PREPARED : serveur10056 réellement tué ; clients26284/27120 ont constaté la coupure et écrit leurs preuves. Lecture du checkpoint : journalPREPARED, reçus3/3, deux sabres stockés. Compilation/build du harnais : `.tools/saber-storage-crash-build1.log`, réussite29s.
- scr1 première reprise : le journal est vidé et le premier client produit sa capture, mais le second reste sans menu ni capture. Essai non validé, deux clients de test17072/21204 arrêtés explicitement après vérification de leur ligne de commande ; lanceur laisse son serveur s'arrêter et conserve les résultats d'échec. L'état local du bloc au moment du premier clic n'avait pas été enregistré : l'hypothèse de chargement tardif n'est pas une cause démontrée. Le nouveau pilote attend un vrai bloc chargé et borne à cinq les demandes d'ouverture, avec log bloc/résultat/numéro d'essai. Aucune modification de production.
- scr1b : nouvelle copie de sc1, jamais de réutilisation de scr1 modifié ; serveur14904, clients27028/5332, trois sorties0 sans arrêt forcé. Vérificateur indépendant PASS : reçu3, cible encore stockée, quatre UUID uniques, contenus/régions et profils RPG conservés. Les deux nouvelles demandes d'ouverture atteignent un bloc chargé et réussissent au premier essai ; cela ne reconstitue pas l'état local manquant de scr1.
- sc2 PLAYER_DURABLE : serveur8856 réellement tué, clients18012/18752 ont constaté la coupure. scr2 nouvelle JVM27380, clients15300/15236, trois sorties0 sans arrêt forcé. Vérificateur indépendant PASS : reçu4, cible récupérée dans l'inventaire par le chemin normal du curseur externe, coffre sans copie, pair inchangé, quatre UUID uniques et composants/profils conservés. La double image native temporaire (joueur payé et ancien coffre) est bien présente au checkpoint, puis réconciliée.
- sc3 STORAGE_DURABLE : serveur19468 réellement tué ; clients14652/9208 ont constaté la coupure. scr3 nouvelle JVM17352, clients18868/10552, trois sorties0 sans arrêt forcé. Vérificateur indépendant PASS : reçu4, cible chez le propriétaire, coffre sans copie, autres contenus/identités et profil RPG conservés, quatre UUID uniques.
- sc4 CLOSED : serveur16744 réellement tué ; clients21924/1196 ont constaté la coupure. scr4 nouvelle JVM14700, clients5860/20180, trois sorties0 sans arrêt forcé. Vérificateur indépendant PASS : journal fermé nettoyé par la production, reçu4, cible chez le propriétaire, coffre sans copie, pair inchangé et quatre UUID/composants conservés.

Les quatre paires ont été revérifiées avec le vérificateur final : il contrôle aussi le payload complet du journal, les avant/après des deux endpoints et la conservation aux frontières, pas seulement les compteurs finaux. Chaque crash possède deux preuves clientes de coupure ; chaque reprise possède deux preuves de menus réels et un arrêt serveur normal. Les mondes/crashs sont conservés séparément.

Capture de `run-adventure-client-one-scr2/screenshots/saber-storage-checkpoint-observed.png` réellement inspectée : grand coffre vide et deux sabres chez le joueur après récupération payée. Notifications vanilla superposées ; ce contrôle d'état ne vaut pas validation artistique finale. Les fenêtres des deux clients sont confirmées sur DISPLAY3 par leurs logs.

## Reproduction

Utiliser des identifiants neufs, conserver les mondes précédents. Python3 avec Pillow est utilisé par les vérificateurs.

```powershell
.\scripts\test-adventure-preparation.ps1 -Run sc1 -Mode SaberStorageCrash -PreparationRun sn1 -TransferStage PREPARED
.\scripts\test-adventure-preparation.ps1 -Run scr1b -Mode SaberStorageCheckpointRecover -PreparationRun sc1
python scripts/check-saber-storage-checkpoints.py sc1 scr1b
```

Les paires restantes s'obtiennent avec les mêmes commandes : sc2/scr2 à PLAYER_DURABLE, sc3/scr3 à STORAGE_DURABLE et sc4/scr4 à CLOSED, toujours avec sn1 comme source des crashs. Le parcours normal et le redémarrage propre sont documentés séparément dans `P2-01_STORAGE.md`.

## Régression et portée

Ajout d'un GameTest de coffre double réellement placé sur deux chunks : retrait de la première moitié, redépôt de la même identité dans la seconde, conservation de sept lingots voisins, reçus1→2 et relecture séparée des deux régions/identités de bloc. Acteur de test à connexion factice ; ce cas n'est pas une nouvelle preuve à deux sockets. Régression `.tools/saber-storage-checkpoints-regression1.log` : BUILD SUCCESSFUL1m6s,131 GameTests requis réussis à21:43:57.133JUnit,0échec/erreur/ignoré ; tâche JUnit à jour, résultats XML relus, sans prétendre une réexécution inutile.

Aucun changement de production. Le vérificateur de JAR installé st2 a été relancé avec les nouvelles sources de tests : PASS, aucun harnais distribué, neuf mixins, empreinte A9DA80B03BF2A34A68C32360BF87C5B46D146139797E322E1F4A89AAB2441BC6 identique. Les anciens démarrages st2 restent les preuves d'installation de ce même binaire ; ils ne sont pas présentés comme de nouveaux lancements.

## Limites

Ces essais ne couvrent pas encore le sens dépôt, l'interruption entre deux écritures de chunks d'un double coffre, les refus d'écriture natifs avec sortie finale du joueur, les hoppers automatiques, autres menus, mortfalse ou cristaux libres. Ne pas annoncer P2-01 ou la V1.0 achevés sur ce seul scénario.
