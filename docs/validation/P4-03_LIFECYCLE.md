# P4-03 — Mort, dimension et déconnexion des participants

Statut : quatre variantes natives et régression dédiée complète validées. P4-03 et V1.0 restent EN COURS.

## Scénario reproductible

```powershell
./scripts/test-bosses.ps1 -Run life3 -OnlyLifecycle
python scripts/check-boss-lifecycle.py life3
# La suite dédiée complète inclut désormais aussi bosses.lifecycle :
./scripts/test-bosses.ps1 -Run postlife1
python scripts/check-bosses.py postlife1 --natural
python scripts/check-boss-loot.py postlife1
python scripts/check-boss-lifecycle.py postlife1
```

Quatre vrais ServerPlayer sur canaux embarqués, quatre arènes de fixture distinctes. Chaque joueur reçoit sept diamants ; un lot de fixture (relique/quatre cellules) est publié dans l'autel. Un vrai BossLootService retire ce lot avec sa transaction native et son journal DONE ; la permission d'accès est fournie explicitement par la fixture. Ce montage n'attribue aucun XP et ne prétend pas tester une nouvelle victoire/éligibilité.

Après collecte, chaque vrai boss est invoqué et atteint sa phase1. Les variantes sont :

- DEATH_DROP : vraie mort vanilla/NeoForge, keepInventory=false, puis vrai PlayerList.respawn remplaçant le corps. Les sept diamants/relique/quatre cellules sont suivis par UUID dans les ItemEntity natives ; le nouvel inventaire reste vide.
- DEATH_KEEP : même mort/réapparition avec keepInventory=true ; inventaire exact conservé, aucun drop. La règle précédente est restaurée dans finally.
- DIMENSION : déplacement de fixture vers l'Overworld, opérateur uniquement pendant cette transition puis dé-OP. Le guard Ilum est conservé et la dimension réellement changée est exigée. Ce test n'autorise pas un voyage ordinaire en plein combat.
- LOGOUT : vrai PlayerList.remove, puis réinscription native avec le même profil et une nouvelle connexion embarquée après le reset ; chargement de playerdata et hooks de login.

Le boss abandonné doit disparaître après le délai configuré, sans récompense, et son arène devenir COOLDOWN. La reprise du service de collecte est rejouée deux fois : reçu de révision1 conservé, journal byte pour byte inchangé, aucun inventaire ancien réappliqué. Le transfert initial est donc réellement effectué, pas remplacé par un reçu synthétique.

Le checker indépendant relit playerdata, autel dans le chunk MCA, journal et arène, puis les régions d'entités. Il exige un exemplaire de chaque drop avec UUID et contenu conservés, ancien boss absent, aucun reçu RPG pour ces participants, aucun XP/alignement d'abandon, et inventaires conformes à la politique vanilla.

## Résultats et refus conservés

- life1 : refus. Les deux morts atteignent COOLDOWN, mais le déplacement non-OP brut hors Ilum est correctement annulé par TravelLifecycle ; le joueur reste dans son arène. Fixture corrigée pour utiliser l'autorisation opérateur pendant ce seul placement et vérifier la destination. Aucune protection de production changée.
- life2 : quatre comportements natifs passent, arrêt normal0 ; checker refuse le snapshot d'autel en mémoire, qui manque le champ vanilla keepPacked présent dans le chunk. Le harnais relit désormais le vrai MCA après barrière via nativeAltar. Comparaison exacte conservée ; aucun succès global annoncé pour life2.
- **life3 PID20456 : quatre variantes PASS**, puis checker indépendant PASS. Arrêt normal, toutes dimensions sauvegardées03:13:36 le16novembre2026. Reçu/WAL réels de collecte conservés sans réinjection, inventaire/drops natifs conformes, zéro gain d'abandon. Aucun correctif de gameplay requis.

## Régression

- postlife1 PID16604 : **huit scénarios dédiés PASS**, dont les quatre variantes de lifecycle. Checkers indépendants boss/naturel, collecte et cycle de vie PASS. Sauvegarde finale de toutes dimensions03:16:13.
- `test runGameTestServer build` : **112JUnit exécutés,0échec/erreur/skip**, **98GT requis passent03:17:09**, BUILD SUCCESSFUL30s. JAR inchangé **E9B1B1792CBDB6438B62C4286A1EA65BCA124E283DA69691E9C4FCA0C1D15AFC**, harnais gameTest exclus. Seul daemon8144 reste actif après contrôle.

## Limites

Acteurs embarqués, pas de nouvelles interfaces graphiques. Ces cas sont des cycles de vie normaux sans panne simultanée ; les interruptions de processus et combats graphiques ont leurs rapports séparés. La sortie de dimension est une fixture opérateur, le voyage normal en combat reste refusé. Pas de nouvelle preuve de mort avec un cristal équipé ni de victoire à plusieurs participants partiellement hors ligne. Claims tiers, seaux/flux, opérateur/récupération manuelle, valeurs extrêmes, profiling et appréciation humaine restent ouverts.
