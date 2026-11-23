# P2-01 — refus réels de partage Windows

23 novembre2026. **Neuf scénarios dédiés hors ligne vérifiés ; ce premier groupe ne certifie pas deux sockets natives. P2-01/P2/V1.0 restent ouverts.**

## Mécanisme réellement exercé

`SaberTransferWindowsIoValidation` ajoute un GameTest utilisant les vrais coordinateurs et writers. Neuf acteurs ServerPlayer distincts ne sont pas inscrits dans PlayerList ; leurs connexions de test acceptent l'appel de déconnexion sans produire de socket. Leurs playerdata sont néanmoins enregistrés par le writer vanilla et leurs ItemEntity par les fichiers natifs de région.

Un FileChannel READ avec NOSHARE_WRITE/NOSHARE_DELETE refuse réellement l'ouverture en écriture et le remplacement du fichier choisi. Une ouverture indépendante WRITE doit lever FileSystemException/AccessDeniedException ; le test conserve le message natif et compare les octets du fichier verrouillé. Aucun writer n'est substitué, aucune exception fabriquée ni ACL modifiée. Le handle est fermé en finally. Sous un autre OS, le GameTest annonce explicitement NOT_EXERCISED ; le vérificateur exige Windows et les neuf preuves, sans prétendre une validation portable de ces règles de partage.

| Chemin | Fichier verrouillé / frontière | Attendu après libération et nouveau coordinateur |
|---|---|---|
| DROP | playerdata avant avant-image | Sabre rendu au joueur, aucun journal/objet publié, reçu0 |
| DROP et PICKUP | playerdata à PREPARED | Paiement refusé, ancien playerdata conservé ; annulation DROP / source PICKUP conservée |
| DROP et PICKUP | journal à PAYMENT_DURABLE | PREPARED reste lisible mais reçu1 réellement payé ; compléter le sens voulu une seule fois |
| DROP et PICKUP | journal à HELD | Publication/retrait natif effectué ; fermeture refusée ; reprise sans second crédit |
| DROP et PICKUP | journal à CLOSED | Suppression du fichier fermé refusée ; nouveau coordinateur nettoie sans rejouer |

Les quatre copies monde après reprise sont conservées sans gravité et sans expiration dans la fixture ; cinq copies restent dans les playerdata. Ce placement de test n'est pas un parcours de jeu physique. Un second coordinateur ne change ni les fichiers joueur ni l'entité stockée. Identité, ItemStack complet et reçu sont comparés ; les journaux finaux sont vides.

## Résultats et limites

Premier build `.tools/saber-windows-io-regression1.log` :113GT requis passent, mais vérificateur indépendant en échec : les quatre entités conservées étaient ensuite effacées par le nettoyage des volumes d'une autre vague de GameTests. Les sources épinglées de StructureUtils confirment le nettoyage jusqu'à20blocs au-dessus du template. Les acteurs/objets de cette fixture sont désormais64blocs au-dessus, avec les mêmes writers et mêmes assertions ; aucun contrôle du vérificateur n'a été assoupli.

Deuxième build `.tools/saber-windows-io-regression2.log` : **BUILD SUCCESSFUL50s,113GT requis passent19:43:44**. Preuve native `run-gametest/world/validation/saber-windows-io/b3560647-78b6-42ff-b113-598896192d98/proof.nbt`. Les neuf fichiers joueur, les identités dans toutes les régions et les neuf journaux ont été relus par :

```powershell
./scripts/gradle.ps1 build runGameTestServer --console=plain
& $validationPython scripts/check-saber-windows-io.py .tools/saber-windows-io-regression2.log
```

PASS : `.tools/saber-windows-io-check2.json`, nativeInventoryAndRegionsVerified=true, nativeSocketsCertified=false. Les copies de preuve de chaque exécution ont des UUID distincts et restent conservées. Les133JUnit précédents restent verts ; aucune modification de production/assets dans cette tranche, donc même JAR installé F996E7EF déjà vérifié. Une déconnexion réelle peut aussi déclencher une sauvegarde vanilla de sortie : ce cas doit être exercé avec deux clients avant de fermer la gate réseau des IO.

## Extension réellement exécutée avec deux sockets

`SaberIoNetworkValidation` et son pilote client ajoutent le mode SaberIo aux lanceurs existants. Chaque essai part d'une nouvelle copie arrêtée de sw2, sans attribution d'objet, profil ou position. Deux clients non-OP confirment leur préfixe, le premier sélectionne le second sabre fabriqué puis envoie le vrai Q. Le verrou est posé par observation de la frontière durable réelle ; une ouverture WRITE indépendante confirme le refus. La production échoue et déconnecte le propriétaire avec son message de récupération. Le pair reste connecté jusqu'à la fin et constate présence/absence de l'objet dans son propre ClientLevel ; un hook de fixture bloque uniquement le pickup de la cible pendant l'observation.

Le handle reste ouvert jusqu'à ce qu'un tick voie le propriétaire retiré de PlayerList, ce qui inclut sa sauvegarde vanilla de sortie. La fixture vérifie les octets protégés, relit le playerdata final puis libère le handle. Le coordinateur de production reprend sans intervention sur les inventaires, sans restauration ni création de fixture. Sauvegarde et arrêt normaux, puis nouvelle JVM et deux nouveaux clients depuis ce même monde via SaberCheckpointRecover.

### Erreur du premier harnais, conservée et corrigée

si1/sir1 conservent un seul exemplaire après paiement, mais leur première version libérait le handle à PlayerLoggedOutEvent. si2 PREPARED échoue sur l'attente « impayé ». Lecture des sources épinglées PlayerList.remove : cet événement précède save, puis le retrait des tables joueur. Le verrou était donc libéré trop tôt. Le save final pouvait légitimement écrire le reçu payé ; la production reprenait ce reçu et publiait une seule entité. Relecture indépendante de si2 : ancien reçu0 au checkpoint, reçu1 final engagé sur la même opération, zéro copie joueur et une seule entité avec les mêmes composants. Aucun défaut de duplication de production observé ; c'est l'attente de ce harnais qui était fausse.

Correction : l'événement ne fait que constater la sortie ; la libération attend PlayerList vide pour cet UUID au tick suivant. Le vérificateur exige maintenant le marqueur LOGOUT_FINISHED. si1/sir1/si2 restent conservés, sans certification de la sauvegarde de sortie sous verrou. Tous les essais corrigés utilisent des noms et copies fraîches.

### Résultats corrigés

| Frontière | Essai → nouvelle JVM | Serveurs | Clients essai ; reprise | Résultat |
|---|---|---|---|---|
| PAYMENT_DURABLE | si3 → sir3 | 25888 / 10488 | 10840,11252 ; 22544,24940 | Reçu1, une seule entité, zéro copie joueur |
| PREPARED | si4 → sir4 | 26812 / 21476 | 26456,2932 ; 27492,25436 | Reçu0, un seul sabre chez le joueur, zéro entité |

Deux vérificateurs PASS : `.tools/saber-io-sir3-check.json` et `.tools/saber-io-sir4-check.json`. Six processus distincts par paire, six sorties0 sans force. Le propriétaire quitte par la protection de production ; le pair quitte normalement après observation. Quatre UUID et tous les composants/inventaires/profils RPG conservés, aucun paiement répété et journal vide dans les mondes d'essai comme de reprise. Les deux clients de chaque essai et reprise sont sur DISPLAY3, vérifié dans leurs logs.

Reproduction (noms neufs requis, sw2 présent et arrêté) :

```powershell
.\scripts\test-adventure-preparation.ps1 -Run <essai> -Mode SaberIo -PreparationRun sw2 -TransferStage PREPARED
.\scripts\test-adventure-preparation.ps1 -Run <reprise> -Mode SaberCheckpointRecover -PreparationRun <essai>
& $validationPython scripts/check-saber-io-network.py <essai> --recovery <reprise>
```

Répéter avec PAYMENT_DURABLE. Les modes HELD/CLOSED sont disponibles dans le harnais mais ne sont pas revendiqués à deux sockets ici. Le champ historique crashedPid de SaberCheckpointRecover porte le PID source ; **aucun serveur n'est tué dans cette série IO** (`serverKilled=false`). Les dix vrais kills précédents restent décrits séparément dans P2-01_CHECKPOINTS.

Le vérificateur confronte la source atelier sw2, les préfixes des vrais clients, le message traduit exact de déconnexion, les reçus avant/après, les playerdata et toutes les régions, les observations du pair puis des deux clients de reprise, les PID et les sorties des lanceurs. Les neuf cas GameTest hors ligne sont relus également après cette série. Aucune modification de production/assets : même JAR F996E7EF, installé/booté précédemment dans jarsv1 ; aucun nouveau boot installé n'est inventé. Le build final et la compilation du nouveau harnais passent.

Limites restantes : IO réseau du ramassage et autres combinaisons prolongées non exhaustives ; ces deux scénarios couvrent le dépôt hors menus, pas GUI/mortfalse/conteneurs/hoppers ni cristaux libres. Le sabre testé est une poignée composée, sans prétendre valider tous les cristaux. La sécurité globale et P2 restent ouvertes.
