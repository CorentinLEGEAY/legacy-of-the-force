# P0-02 — Protocole diagnostic natif, 16 novembre 2026

## Portée et méthode

Deux clients Minecraft natifs simultanés, connexions TCP loopback distinctes, profils de lancement isolés. Le compte ProtocolAdmin reçoit temporairement OP via PlayerList ; ProtocolGuest reste non-OP. Aucun inventaire, niveau, dégât ni énergie de fixture. Monde dédié neuf, survie/paisible, offline uniquement sur 127.0.0.1:25584.

Le serveur de test observe le véritable événement DiagnosticRequested et son thread, puis les données natives du joueur. Les pilotes sont exclusivement dans src/gameTest et absents du JAR. Aucun handler de production n'est remplacé. Pour les deux fautes de wire, le pilote encode l'identifiant de paquet avec le codec PLAY réel, puis contourne seulement l'encodeur de payload : cadrage, compression et socket restent natifs. Chaque faute fait moins de128octets, sur loopback seulement. Le faux snapshot contient niveau99/total999999 ; il n'est jamais appliqué au serveur.

Le client Mismatch remplace uniquement ses deux enregistrements requis de ce protocole par la version999, après enregistrement normal. Le serveur garde la version1. Il s'agit d'une faute de test lors de la négociation NeoForge, pas d'une option distribuée de contournement.

## Assertions indépendantes

- Séquence1, replay1, séquence0 décroissante, négatifs -1/MIN, rafale2..65 : un seul diagnostic initial autorisé. Les séquences croissantes limitées par cadence sont consommées ;65 reste refusée même après attente,66 est acceptée après au moins20ticks serveur.
- Non-OP : un refus compréhensible et aucun événement métier. Révocation effective de l'opérateur, séquence67 : refus sans événement ; restauration OP ensuite.
- Long.MAX_VALUE accepté une fois, duplicata/MAX-1 refusés. Arrêt normal du premier client puis nouveau JVM, même UUID et autre port TCP : séquence0 acceptée, preuve du nettoyage réel au logout.
- Faux snapshot S→C expédié vers serveur : avertissement NeoForge refusant le mauvais sens ; arrivée réelle sous forme DiscardedPayload, sans appel de codec métier ni mutation du profil.
- VarLong à11octets de continuation : DecoderException / VarLong too big côté serveur et vraie déconnexion côté client ; rien n'atteint le handler de paquet.
- Version999 : écran natif ModMismatchDisconnectedScreen et messages pour les deux canaux, aucun joueur client ni entrée PLAY. Le texte français natif inverse les rôles client/serveur dans son explication ; l'injection est bien côté client uniquement. NeoForge produit également une stack de fermeture réseau et un rapport de déconnexion, sans arrêt anormal du JVM ni panne du serveur.
- Liste exacte des événements serveur [1,66,Long.MAX_VALUE,0], uniquement pour l'opérateur, sur thread serveur. Deux profils complets Inventory/RPG/game mode/invulnérabilité identiques avant/après, mêmes profils dans les fichiers vanilla finaux. Aucune récompense ni révision RPG, aucune élévation client de droits.
- Six sorties de clients0 sans kill forcé, serveur0 et toutes dimensions sauvegardées ; captures framebuffer des six cas présentes. Écrans de refus malformed/version inspectés visuellement sur cp2.

Le checker ne certifie pas un test de charge, l'ensemble des paquets des autres modules, tous les malformations possibles, ni la résistance à une machine externe. Les gates de sécurité propres à chaque fonctionnalité et P7 restent obligatoires.

## Reproduction et artefacts

```powershell
./scripts/test-core-protocol.ps1 -Run identifiantneuf
python scripts/check-core-protocol.py identifiantneuf
```

Installer les dépendances de validation de scripts/validation-requirements.txt si nécessaire. Ne pas lancer un autre Gradle simultanément. Les profils et mondes précédents sont préservés ; choisir un nouvel identifiant. Les scripts attendent les processus natifs et enregistrent les codes, sans déduire la réussite d'un simple envoi.

Preuves locales ignorées : .tools/core-protocol-<run>-result.json, logs console, run-core-protocol-<run>/world/validation/core-protocol-server.nbt, fichiers playerdata vanilla ; run-core-protocol-client-<Case>-<run>/validation/core-protocol-client.nbt et screenshots/core-protocol-<Case>.png. Le checker relit indépendamment ces artefacts, logs natifs, séquences, UUIDs, PID, ports, versions de protocole et états finaux.

cp1 est refusé : le contrôle d'isolation du pilote lisait le nom relatif «.», corrigé par normalisation absolue ; attente de démarrage rendue explicite. Trois sorties normales, aucune validation. cp2 passe intégralement avant le correctif de journal atomique décrit dans CORE_ATOMIC_REPLACE.md ; six PID clients11256/14984/23984/23324/23892/7556. Le protocole métier n'a pas été changé par ce correctif.

cp3 répète intégralement après correction de DurableOperationStore : serveur et six clients sortent0 sans force, mêmes assertions et checker PASS. Clients4368/9564/9028/13144/19988/17888 ; clôture10:36:28 environ (horodatage exact dans result.json). Build39s,127JUnit0échec/erreur/skip,102GT requis verts. Nouveau JAR F1F94340A2CE2BE9775962530E6E8324FDE974DAAE2B17F722ACAD2EB82FCBDC ; jarcp1 deux démarrages serveur installé2328/4456 sorties0, checker de distribution PASS, aucun harnais distribué. P0-02 et phase P0 validés pour leur périmètre de socle ; P1–P7 et V1.0 ne le sont pas par cette preuve.
