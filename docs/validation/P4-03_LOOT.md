# P4-03 — Collecte durable du butin partagé

16 novembre 2026. Tranche de robustesse du butin ; **P4-03 et V1.0 restent en cours**.

Actualisation02:02 : la nouvelle collecte a ensuite été utilisée avec succès par deux vrais clients lors des deux duels ; voir `P4-03_NETWORK.md`. Les mentions de validations réseau encore ouvertes plus bas décrivent l'état au terme de la tranche de crashes. Le solo et les autres limites restent ouverts.

## Fonctionnement et protocole

L'autel conserve son conteneur natif visible. Un clic ou Maj-clic sur un emplacement de butin collecte **tout** le contenu vers l'inventaire principal. Curseur vide, place suffisante, proximité, survie, participation récompensée et droits requis. Aucune récompense portée par le curseur, insertion, échange de barre rapide, jet, clonage, glissement ou double-clic de ramassage. Un clic répété n'accorde rien. Les deux bénéficiaires du boss partagent les mêmes objets, sans multiplier le coffre par joueur. Messages FR/EN, titre « Butin partagé · clic ». L'appréciation de cette nouvelle interaction avec un vrai client reste à réaliser.

`BossLootService` travaille uniquement sur le thread serveur. Limite d'une tentative par10ticks,1024 acteurs maximum, entrées retirées à la déconnexion. Les systèmes de claims ont un contrat distinct `mayUseContainer`/`allowsContainer`, refus par défaut et sur exception ; autoriser un accès ne permet pas de modifier les blocs. L'arène vérifie toujours l'éligibilité. Les adaptateurs externes doivent implémenter ce nouveau contrat explicitement.

Ordre de transfert :

1. Sauvegarder et relire l'avant-image native du joueur.
2. Écrire le journal PREPARED par joueur, avant/après d'inventaire, révision/UUID de reçu, victoire et source exacte.
3. Verrouiller l'autel par UUID et sauvegarder/relire son chunk natif.
4. Livrer les objets et le reçu suivant dans le même fichier natif joueur, avec le checkpoint core de sauvegarde solo.
5. Journal DELIVERED, puis vider l'autel en conservant le verrou et l'UUID de collecte ; sauvegarder/relire le chunk.
6. Journal DONE, puis déverrouiller et sauvegarder/relire le chunk.

Au login, le fichier natif du joueur tranche une opération ouverte : avant-image durable = ABORTED, après-image durable = terminer la vidange. Une phase fermée vérifie uniquement le reçu, sans restaurer un ancien inventaire. Un verrou restant après DONE/ABORTED est retiré de manière idempotente. Une ambiguïté/version inconnue conserve les données et refuse l'action/connexion concernée ; corriger le stockage puis reconnecter le joueur. Si le propriétaire ne revient pas, un verrou interrompu reste réservé ; récupération opérateur hors ligne encore à concevoir.

Cas de concurrence important : avant la sauvegarde du verrou, un autre participant peut collecter pendant l'absence du premier. L'ancien reçu natif prouve que le premier n'a rien reçu ; son intention est annulée **sans recréer le contenu ni modifier la collecte du pair**. Source modifiée sous un verrou appartenant encore à l'opération = ambiguïté refusée.

Les formats inconnus de l'autel restent inertes, protégés et conservés. Le bloc rempli ou verrouillé ne peut pas être cassé, déplacé par piston ou détruit par explosion via les événements couverts. Hoppers refusés. Autres vecteurs et compatibilité réelle avec des mods de claims restent dans la matrice générale.

## Preuves exécutées

- Trois nouveaux JUnit : transfert exact et copies défensives ; refus de quantités/composants/emplacements/équipement/reçus/schémas falsifiés ; conservation des reçus futurs et refus de dépassement de révision.
- `loot1`, puis `loot2` PID19576 : deux vrais combats dans les structures naturelles, reset/protections et seconde victoire sous cooldown passent. Le menu serveur de collecte est réellement utilisé, les modes de clic non autorisés n'altèrent aucun inventaire. Les participants restent des ServerPlayer de fixture à connexion embedded ; ce ne sont pas deux fenêtres Minecraft gagnant le duel.
- Scénario dédié de collecte : **11 interruptions Error**, une à chaque checkpoint PREPARED, LOCK_MEMORY, LOCK_DURABLE, PLAYER_MEMORY, PLAYER_DURABLE, DELIVERED, EMPTY_MEMORY, EMPTY_DURABLE, DONE, UNLOCK_MEMORY, UNLOCK_DURABLE. Abandon de la mémoire puis relecture du joueur et du chunk natifs. Sept diamants préexistants conservés ; avant livraison,0relique/0cellule joueur et1/4 dans l'autel ; après livraison,1/4 joueur et0dans l'autel. Deux reprises ne doublent rien, aucun verrou résiduel. Des gains ultérieurs de trois émeraudes ne sont pas rembobinés par un reçu fermé.
- Le second acteur est refusé pendant un verrou ; il peut au contraire collecter après une interruption antérieure au verrou durable, puis l'ancien propriétaire reprend sans gain et sans refill. Inventaire plein, curseur occupé, accès refusé, adaptateur de claim non implémenté/en erreur, journal futur et autel futur couverts. `check-boss-loot.py loot2` recoupe les images et le scénario de concurrence ; `check-bosses.py loot2 --natural` recoupe aussi les combats, reçus RPG et régions natives.

### Vrais arrêts forcés

`test-boss-loot-crashes.ps1 -Run lk2` suspend la vraie méthode de collecte dans un dédié complet, puis termine son **processus Java** sans commande stop. Le monde interrompu est copié avant redémarrage ; la même identité revient via PlayerList et la récupération de production PlayerLoggedInEvent. Aucun writer alternatif de playerdata.

| Checkpoint | JVM interrompue | Nouvelle JVM | Résultat |
|---|---:|---:|---|
| LOCK_DURABLE |3424|23840|Source conservée, joueur non livré, ABORTED|
| PLAYER_MEMORY |11876|8024|Source conservée, joueur non livré, ABORTED|
| PLAYER_DURABLE |8504|22160|Une livraison, source vide, DONE|
| EMPTY_MEMORY |7536|2084|Une livraison, source vide, DONE|
| EMPTY_DURABLE |4640|17984|Une livraison, source vide, DONE|
| DONE |19684|14224|Une livraison, verrou retiré, DONE|

Les six wrappers Gradle quittent1 après Stop-Process et attente de terminaison ; aucune trace d'arrêt normal avant le kill. **Le code de sortie natif Windows n'était pas disponible dans ces six résultats (null), il n'est pas annoncé −1.** Le lanceur conserve maintenant un handle natif ouvert ; contrôle supplémentaire `lh1-1`, PLAYER_DURABLE, JVM4164→23348 : code natif−1 observé, wrapper1, reprise/arrêt normal0. Le vérificateur indépendant lit les copies interrompues et les régions après reprise, fichiers joueurs/reçus/autels/UUID/PID et logs : sept kills/reprises au total vérifiés, sans additionner une simple exception au nombre de kills.

Essai `lk1-1` non retenu : le garde de PID cherchait forgeserverdev dans la ligne de commande alors que le lanceur passe `serverRunProgramArgs.txt`. La JVM2220 est restée suspendue ; elle a été identifiée puis terminée explicitement, et les fichiers conservés. Garde corrigé pour le vrai argument de lancement. Cet essai n'est pas compté parmi les sept reprises vérifiées.

## Reproduction

```powershell
./scripts/gradle.ps1 test runGameTestServer build --console=plain
./scripts/test-bosses.ps1 -Run nouveau
python scripts/check-bosses.py nouveau --natural
python scripts/check-boss-loot.py nouveau
./scripts/test-boss-loot-crashes.ps1 -Run nouveauk
python scripts/check-boss-loot.py nouveauk --kills
```

Identifiants neufs, monde isolé et données de test exclusivement. Ne pas lancer Gradle en parallèle. `-OnlyLoot` exécute uniquement le scénario de collecte sur un dédié complet, pas les combats. Harnais de crash dans gameTest, commandes console uniquement, drapeau dédié opt-in, répertoire run-boss-* obligatoire ; aucune classe de validation dans le JAR distribué.

## Portée et limites

Régression finale : `test runGameTestServer build`, **110 JUnit sans échec/erreur/skip,97GameTests requis verts00:45:25**, BUILD SUCCESSFUL33s. Scénario final `loot3 -OnlyLoot` passe après ajout de la borne de cadence ; checker PASS avec concurrence. JAR dev SHA256 **9373B05554D9BAE91876AE7F1E1F34FE336E6008A3B7120FA4E23F4157F1392E**, aucune classe GameTests/Validation/ClientDriver distribuée. Processus de test arrêtés ; seul daemon Gradle après contrôle.

Cette preuve couvre le transfert partagé via les fichiers natifs et des crashes de processus sur ce disque ; pas une coupure électrique/OS, une perte du disque, ni un administrateur/mod tiers réécrivant les fichiers hors protocole. Les kills précèdent le correctif permettant la collecte du pair avant reprise du premier ; ce cas supplémentaire est vérifié par le scénario dédié avec images natives. Solo intégré et deux vrais clients sur la nouvelle interface restent ouverts. Mort/dimension pendant les erreurs, croissance/coût disque, récupération opérateur et autres phases de victoire restent à éprouver. Conserver ensemble régions de blocs/entités, playerdata, level.dat et data/legacyoftheforce ; ne pas restaurer isolément un ancien reçu/inventaire.
