# P4-02 — Coupures socket pendant le voyage — 2026-11-15

## Parcours réels vérifiés

Deux vrais clients Minecraft par sockets loopback25587. Le premier envoie les boutons vanilla du terminal ; le service réel est suspendu au checkpoint choisi, puis le lanceur tue uniquement ce client possédé, sans logout (code−1). La socket serveur est constatée fermée tandis que le joueur est encore enregistré et que le service est suspendu. Le second client reste dans le même processus et connecté. Après reprise du service et retrait natif du joueur déconnecté, un nouveau processus reconnecte la même identité au même serveur. Les deux joueurs finissent l'aller puis le retour réel prépayé.

| Run | Checkpoint | Client interrompu | Client reconnecté | Second client conservé |
|---|---|---:|---:|---:|
| drop3 | PAID | 8980 | 6832 | 12952 |
| droptele1 | TELEPORTED | 5936 | 9304 | 4900 |
| dropactive1 | ACTIVE_DURABLE | 25328 | 2836 | 3992 |

Les trois checkers indépendants passent. PAID : mémoire/fichier joueur encore Overworld avec paiement durable et atlas2 pending. TELEPORTED : mémoire Ilum, fichier encore Overworld/pending. ACTIVE_DURABLE : fichier et mémoire Ilum/atlas2 actif, journal ARRIVED. Dans tous les cas, fermeture socket avant fin de l'opération, même opération à l'arrivée, deux cellules consommées une seule fois, terminal porté conservé puis posé par la fixture, sept diamants conservés par joueur, retour final atlas3/DONE/créditfalse. Les PID, endpoints avant/après, UUID, inventaires natifs et journaux sont recoupés. Après les preuves, les deux clients restent connectés quinze secondes, puis clients et serveur se ferment normalement code0 et les quatre dimensions sont sauvegardées.

`TravelDisconnectValidation` est exclusivement gameTest : il câble le constructeur Probe existant dans le service réellement utilisé par les menus (réflexion sur la map privée du lifecycle, priorité LOWEST après démarrage). Aucune route de test n'est distribuée. Monde neuf/propriété JVM/checkpoints autorisés obligatoires, suspension bornée20s. Le lanceur attend réellement le retrait serveur avant de reconnecter. Le préfixe de trois étapes validées est préservé sur le client interrompu, puis complété par les accusés d'arrivée/retour du nouveau processus ; ses ticks sont une séquence logique reprise, pas une horloge murale continue. Aucun test ne simule une déconnexion en appelant seulement le handler logout.

## Défaut réel et correctif

`drop2` échoue sur « Actual arrival unsafe » : après retrait du premier joueur, le second peut choisir son ancien emplacement. Le premier se reconnecte dans le même volume ; les deux fichiers natifs montrent Ilum/(0.5,63,0.5), atlas2. Ce défaut dépend de l'ordre des arrivées et n'est pas un échec de débit.

`TravelService.recoverLogin`, appelé par le lifecycle après réconciliation financière : pour un voyage clos non DISCOVER, près de sa destination et hors monture, détecter l'occupation du volume debout courant. Rechercher localement une place sûre et autorisée à partir de la position **courante**, téléporter nativement puis sauvegarder l'image actuelle. Ne pas restaurer celle de l'ancien reçu ; préserver gains ultérieurs, regard, bookmark et reçu DONE. Sans site disponible, refuser sans déplacer ni effectuer une nouvelle écriture transactionnelle. Hors zone de la dernière arrivée ou sans occupant, aucune correction de pose ni sauvegarde supplémentaire. Les montures restent sous leur politique native.

Trois nouveaux GameTests : relocalisation locale idempotente avec gains/rotation et reçu conservés ; claims refusant tout site, état live/fichier/journal conservés ; pose libre et exploration ultérieure non rembobinées. Première régression97GT verte, puis une répétition a révélé une fixture distante dans un chunk invisible : correction par ticket/chargement, attente de visibilité et ajout de l'occupant directement à son emplacement final. L'assertion d'occupation reste obligatoire. Régression finale97GT requise verte.

Les runs réseau corrigés observent la relocalisation sans paiement. `droptele1` et `dropactive1` incluent les instantanés natifs/live avant et après le callback login ; inventaires/atlas et journal DONE inchangés, position sûre distincte. `dropactive1` conserve aussi les deux AABB : le checker calcule indépendamment leur intersection avant puis leur séparation après. Aucun autre déplacement de fixture n'est ajouté pour forcer l'occupation.

`drop1` n'est pas retenu comme preuve finale : le premier harnais cherchait le journal du joueur ciblé avant sa création pendant l'action de son pair. Garde d'existence corrigée, reprise de Track explicitement marquée ; `drop2` expose ensuite le vrai défaut ci-dessus. Aucun essai échoué n'est compté comme validation, aucune assertion de sécurité retirée.

## Reproduction et portée

Régression finale `test runGameTestServer build` : **105 JUnit, zéro échec/erreur/skip et 97 GameTests requis verts**, BUILD SUCCESSFUL22:08:14. JAR SHA256 **110ED84B015459B8C0BC3CDE8D165B7F3FDA116E981FCFC9B810D6A0B15518BE**, aucune classe Validation/GameTests/ClientDriver distribuée. Nouvelle reprise solo `solotriprc2` PID22584 à partir du monde interrompu `solotripkill` : checker solo PASS sur le code corrigé, Ilum/génération19/reçuDONE/inventaire conservés, fermeture normale. Seul daemon Java8144 actif après contrôles ; aucun client/dédié laissé ouvert.

```powershell
./scripts/test-travel-network.ps1 -Run coupure -DropCheckpoint PAID
python scripts/check-travel-disconnect.py coupure
# Autres checkpoints autorisés : TELEPORTED, ACTIVE_DURABLE ; toujours un nouveau Run.
```

Préparation JDK/clients/Python décrite dans P4-02_NETWORK.md. Les preuves locales restent ignorées : world/validation/disconnect-*.nbt, préfixe client, logs/résultat du lanceur sous .tools. Les instantanés avant/après login sont supplémentaires aux cinq étapes et aux fichiers finaux de chaque client.

Ce sont trois coupures **à l'aller** ; le service termine l'opération malgré la socket fermée, puis le joueur se reconnecte. Cela ne prouve pas une combinaison de coupure réseau et panne disque/serveur, toutes les étapes/sens, le profiling prolongé ou les adaptateurs de claims tiers. Les six kills dédiés et le kill solo ont leurs preuves séparées. La fixture fournit les objets et pose le terminal à destination : pas un parcours de récolte/craft manuel. Le défaut de position a été corrigé, pas masqué par une téléportation du harnais. P4-02 et V1.0 demeurent partiels ; aucune appréciation graphique du siège n'est déduite de ces tests.
