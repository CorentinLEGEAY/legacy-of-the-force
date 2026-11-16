# P4-03 — Arrêts forcés pendant publication et victoire

Statut : seize arrêts forcés/reprises ciblés validés ; P4-03 et V1.0 restent EN COURS.

## Protocole

BossArenaService expose des checkpoints package-private, consommateurs sans effet dans le lancement normal. Le harnais gameTest console `lotfbossarenacrash` les arme seulement dans des mondes isolés run-boss-* avec le flag bossDedicated. Il crée une plateforme/autel de fixture, un vrai ServerPlayer sur canal embarqué et sept diamants conservés. Chargement du stockage/visibilité entity-ticking attendu de manière asynchrone.

Pour les étapes de victoire, après l'introduction native, le joueur inflige trois dégâts réels pour satisfaire la contribution. Après vingt-et-un ticks, seule la fixture réduit les PV à1 puis inflige un coup natif de2 : le vrai chemin de mort/victoire s'exécute. Ce montage ne constitue pas un combat graphique ; les combats réels sont vérifiés séparément sous P4-03_NETWORK.md et P4-03_SOLO.md.

Au checkpoint choisi, le thread serveur écrit une preuve bornée et reste suspendu. L'orchestrateur contrôle PID, heure de naissance et commande de la JVM native, ouvre son handle puis l'arrête brutalement. Exit natif −1 et wrapper1 obligatoires ; aucun `stop` ni sauvegarde finale sur cette JVM. Le monde interrompu est copié et préservé avant redémarrage.

Une nouvelle JVM charge le même monde, puis le joueur natif revient. Le vrai coordinateur reconcilie les fichiers d'arène et les intentions de victoire ; le RPG restaure ses checkpoints et reçus. Aucun hook d'interruption n'est réarmé. Après cent ticks, le harnais exige ancien boss absent, arène COOLDOWN et aucune victoire en attente. Sans intention de victoire :0XP/0alignement/aucun lot. Avec intention :800XP/−250dixièmes et un lot non collecté dans l'autel (une relique/quatre cellules), inventaire des sept diamants inchangé. Arrêt normal et toutes dimensions sauvegardées.

Le checker indépendant lit les copies interrompues, playerdata, journaux d'arène/victoire, reçus RPG et régions natives d'entités/blocs. Il vérifie aussi l'état exact au checkpoint, le changement de PID et les sorties anormales/normales, la présence du boss dans le stockage interrompu à partir de SPAWN_DURABLE, puis son absence après reprise. Les snapshots de preuve doivent correspondre aux vrais fichiers ; pas de réussite fondée seulement sur un log.

```powershell
./scripts/test-boss-arena-crashes.ps1 -Run ac1 -Checkpoints SPAWN_INTENT,SPAWN_DURABLE,VICTORY_PREPARED
python scripts/check-boss-arena-crashes.py ac1
./scripts/test-boss-arena-crashes.ps1 -Run ac2 -Checkpoints SPAWN_MEMORY,ACTIVE,SETTLING,REWARD_GRANTED,REWARDS_DURABLE,LOOT_MEMORY,LOOT_DURABLE,LOOT_STAGED,COOLDOWN,EVENT,COMMITTED,CLOSED,REMOVED
python scripts/check-boss-arena-crashes.py ac1 ac2 --all-checkpoints
```

## Résultats

- ac1 : trois vraies JVM arrêtées anormalement, trois reprises normales. Checker indépendant PASS pour SPAWN_INTENT, SPAWN_DURABLE et VICTORY_PREPARED. Aucune récompense aux deux premières étapes ; exactement un règlement à la troisième.
- ac2 : treize autres vrais arrêts/reprises, tous PASS. Vérification combinée `ac1 ac2 --all-checkpoints` : **16/16** checkpoints couverts. Tous les exits natifs −1, wrappers1, nouvelles JVM de reprise exit0 avec sauvegarde de toutes dimensions. Dernière reprise02:56:29 le16novembre2026.
- REWARD_GRANTED et REWARDS_DURABLE avaient encore **0XP dans le fichier joueur interrompu** ; le checkpoint RPG/réconciliation native produit800XP exactement à la reprise. Le test ne suppose pas que le dernier profil en mémoire ait déjà été enregistré par vanilla.

| Checkpoint | JVM interrompue | JVM reprise | XP finale | Reliques dans autel |
|---|---:|---:|---:|---:|
| SPAWN_INTENT | 368 | 25336 | 0 | 0 |
| SPAWN_DURABLE | 3236 | 24064 | 0 | 0 |
| VICTORY_PREPARED | 11092 | 25204 | 800 | 1 |
| SPAWN_MEMORY | 17800 | 7344 | 0 | 0 |
| ACTIVE | 15572 | 5556 | 0 | 0 |
| SETTLING | 12172 | 12536 | 800 | 1 |
| REWARD_GRANTED | 19644 | 19160 | 800 | 1 |
| REWARDS_DURABLE | 9832 | 3028 | 800 | 1 |
| LOOT_MEMORY | 3040 | 9212 | 800 | 1 |
| LOOT_DURABLE | 15532 | 7200 | 800 | 1 |
| LOOT_STAGED | 22960 | 16696 | 800 | 1 |
| COOLDOWN | 3080 | 23640 | 800 | 1 |
| EVENT | 22992 | 11540 | 800 | 1 |
| COMMITTED | 16680 | 22740 | 800 | 1 |
| CLOSED | 23660 | 12828 | 800 | 1 |
| REMOVED | 12248 | 12704 | 800 | 1 |


## Régression et artefact

- `./scripts/test-bosses.ps1 -Run postar1` : sept scénarios natifs PASS (deux duels naturels, sécurité, cooldown, collecte onze checkpoints/concurrence, limite et chargement). Checkers boss/loot PASS ; preuves natives limite/chargement également relues et vérifiées. Serveur PID8392, arrêt normal et toutes dimensions sauvegardées02:58:31.
- `./scripts/gradle.ps1 test runGameTestServer build` : **112JUnit exécutés,0échec/erreur/skip**, **98GT requis passent02:59:55**, BUILD SUCCESSFUL30s.
- JAR `build/libs/legacyoftheforce-0.1.0-dev.jar`, SHA256 **E9B1B1792CBDB6438B62C4286A1EA65BCA124E283DA69691E9C4FCA0C1D15AFC**. Aucun harnais Validation/GameTests/ClientDriver dans le ZIP. Seul daemon Gradle8144 reste actif après contrôle ; toutes les JVM de scénario sont terminales.

## Limites

Ces arrêts de processus ne sont pas des coupures d'alimentation, erreurs de fsync/disque plein ni corruptions arbitraires. Un contributeur natif de fixture ; pas de nouvelle preuve graphique/réseau, ni d'un cristal équipé à travers une rediffusion d'événement. Les consommateurs de BossDefeated doivent dédupliquer son UUID stable. Le boss Jedi est utilisé pour ces frontières communes au coordinateur ; les deux comportements complets ont leurs tests de combat distincts. Reconnexion hors ligne pendant une victoire à plusieurs participants, mort/changement de dimension sous erreur, récupération opérateur, claims tiers, protection seaux/flux, valeurs extrêmes et profiling restent ouverts.
