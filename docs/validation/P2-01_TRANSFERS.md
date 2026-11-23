# P2-01 — correctif du dépôt et du ramassage de sabres

Correctif implémenté le23 novembre2026. Le scénario de duplication sd1 est fermé sur sa répétition sd2 ; P2-01 reste partiel pour les coupures intermédiaires natives, les refus d'écriture et les autres chemins de transfert.

Suite vérifiée : [P2-01_PICKUP_RECOVERY.md](P2-01_PICKUP_RECOVERY.md) ajoute sd3→sp1 puis correctif d'avant-image récente et sd4→sp2, deux vraies nouvelles connexions/collecte/reçu2 sans duplication. Les résultats datés ci-dessous restent les preuves de leur propre binaire.

## Contrat de production

`SaberItemTransfers` intercepte le Q hors menus et le ramassage des sabres identifiés, côté serveur, quand l'agrégat d'inventaire est prêt à sauvegarder. Le même sabre peut contenir un cristal : son ItemStack complet est conservé. Les cristaux libres, conteneurs, drops de mort et autres systèmes de déplacement ne sont pas implicitement pris en charge par ces deux hooks.

Le dépôt natif retire déjà le stack avant ItemTossEvent. Le hook annule uniquement la publication vanilla, puis écrit une intention PREPARED sous `world/data/legacyoftheforce/saber_transfers`. Payload versionné : acteur, opération, identité du sabre/de l'entité, dimension, snapshot natif de l'entité et prochain reçu. Le nouveau reçu `saber_transfer_receipt` est sauvegardé avec l'inventaire dans le même playerdata natif, fsync et relecture vérifiés. Ensuite HELD, publication de la même ItemEntity et barrière native d'entités, fermeture CLOSED et suppression du journal clos. Un refus avant préparation restitue le stack et resynchronise l'inventaire ; l'annulation d'ItemTossEvent seule n'aurait pas suffi.

Le ramassage conserve les règles natives de délai et propriétaire ainsi que les refus d'autres listeners. Il demande une place libre et refuse une identité déjà détenue, prépare le journal avant insertion, sauvegarde le reçu et le seul item reçu, puis retire durablement l'entité avant fermeture. Notifications de ramassage et synchronisation après commit. Aucun client ne fournit de coût, d'ItemStack ou de reçu.

Curseur, grille2×2 ou autre menu ouvert : pas de pickup transactionnel et pas de sauvegarde forcée excluant ces stacks temporaires. Les dépôts depuis une interface restent **vanilla et non sécurisés par ce coordinateur** : le caller peut vider le curseur après ItemTossEvent, rendant dangereuse une restitution immédiate dans un inventaire plein. Le chemin GUI doit encore recevoir son propre protocole et sa preuve avant clôture P2-01 ; il n'est pas déclaré sûr ni désactivé par ce correctif.

Sur écriture incertaine, aucune restauration aveugle d'un ancien inventaire : le joueur est déconnecté avec un message traduit. La reprise attend sa sauvegarde finale native puis lit le reçu du fichier. Elle abandonne une intention non payée seulement si l'inventaire natif confirme l'ancien côté ; un reçu payé achève publication/retrait. Reçu, nombre d'identités, schéma ou composants contradictoires : journal conservé, pas d'écrasement. Le traitement ne remplace jamais l'inventaire d'un joueur par une vieille image. Pendant une opération ouverte, les nouveaux transferts de cet acteur et le ramassage de son entité sont bloqués ; le tick de cette entité est suspendu pour éviter un déplacement vers un autre chunk.

Budget configurable `transfer.maximumPending=256` (1..1024, redémarrage), `transfer.retryTicks=20` (1..1200), une opération traitée par reprise. Les échecs répétés sont logués au plus toutes200ticks. Les entrées inconnues restent intactes ; aucun ancien doublon de sd1 n'est supprimé arbitrairement.

## Preuves obtenues

- Deux JUnit de reçu : copie défensive/roundtrip/révision monotone/UUID, formats futurs/malformés et overflow refusés.
- Six nouveaux GameTests sur les vrais writers natifs : dépôt puis pickup et reçu1→2, aucun second pickup ; inventaire plein/curseur/grille temporaire/reçu futur sans consommation ; intention non payée abandonnée ; intention payée de dépôt publiée sans restaurer d'inventaire ; pickup payé retiré sans nouvel item ; composants natifs contradictoires conservés avec leur journal.
- Les tests de reprise ci-dessus construisent les états durables des frontières puis recréent un coordinateur. **Ce sont des frontières injectées et des relectures natives, pas six kills de JVM.** Les pannes de processus au milieu de ces étapes restent une validation suivante.
- Répétition du même Q/ordre de sauvegarde/kill que sd1 : **sd2**, serveur2308 tué après vérification d'identité, clients15952/9068 réellement déconnectés puis sorties0. Kill17:19:37.152UTC, runner1 attendu. `scripts/check-saber-drop-crash.py sd2` PASS : **un seul exemplaire persisté**, dans la région d'entités, reçu natif révision1 et inventaire joueur débité, journal fermé. Aucun nouveau boot de reprise sd2 certifié par ce résultat.
- sd1 reste conservé et `--expect-duplicate` y atteste toujours les deux copies originales ; son invariant produit par défaut reste rouge. Aucun changement de ses fichiers pour faire passer le correctif.

## Régression et incident de fixture

Deux premières régressions échouent sur le test de menu du garage : le diagnostic enrichi révèle BUILD_BUSY, limite globale40ticks partagée avec une autre fixture. La fixture n'exige plus que le premier appel soit admis : refus BUSY seulement, inventaire inchangé exigé, trois reprises maximum après l'intervalle configuré. Tout autre refus échoue immédiatement. Le test de rejeu attend aussi l'expiration de l'intervalle global pour vérifier le verrou du menu lui-même, et non se satisfaire du refus global. Aucun cooldown de production ni assertion de paiement désactivé.

Régression intermédiaire après garde de conflit et gel du tick : `scripts/gradle.ps1 build runGameTestServer --console=plain`, BUILD SUCCESSFUL38s, **108 GameTests requis réussis18:23:23**. Log `.tools/saber-transfers-locked-regression.log`. Les échecs antérieurs sont conservés sous first/recovery-regression ; les reprises intermédiaires vertes ne sont pas la preuve du dernier binaire. JAR de cette étape SHA256 `05F22F0C48E228BD5134D03E4EC2CF8DCA096AC50E640AC4ACB995A994D0D565`, avant les gardes de curseur/grille/menus.

Rapports JUnit133, zéro échec/erreur/ignoré. `scripts/test-packaged-server.ps1 -Run jarst1` puis `scripts/check-packaged-server.py jarst1` : PASS sur ce JAR installé isolément ; deux nouvelles JVM5188/25372, sorties0 sans force, trois mixins requis appliqués, Ilum chargé et eau native sauvegardée entre les boots, aucun harnais distribué. Pas d'erreur d'ouverture du coordinateur de transfert dans les deux logs installés. Aucun nouveau parcours client déduit de ce smoke installé.

### Binaire final de cette tranche

Les gardes de curseur/grille et la frontière GUI sont ensuite vérifiés : `.tools/saber-transfers-gui-regression.log`, BUILD SUCCESSFUL36s, **133JUnit exécutés sans échec/erreur/ignoré et108GameTests requis verts18:30:34**. Nouvelle répétition native **sd3** sur ce code : serveur25732 tué17:31:48.976UTC, clients15376/23200 vraie coupure puis sorties0. `check-saber-drop-crash.py sd3` PASS, un exemplaire persisté et invariant produit vrai ; aucun redémarrage de sd3 encore revendiqué.

JAR final SHA256 **4D699ABDFA158816A0DF97C6A699CF67E9CE69D298138E4CC005141C486EB58A**. Nouveau test installé **jarst2**, deux JVM11820/10224 sorties0, checker PASS : zéro harnais distribué, trois mixins requis, Ilum et flux natif conservé au redémarrage ; répertoire du coordinateur de transfert créé dans ce vrai monde installé. Aucun code client chargé par le dédié. `git diff --check` PASS, seul daemon Gradle25844 au dernier inventaire. Les mondes sd1/sd2/sd3 et jarst1/jarst2 sont conservés.

## Validation restante

Le redémarrage avec clients/ramassage et l'avant-image récente sont traités dans P2-01_PICKUP_RECOVERY.md : nouvelle barrière avant PREPARED et reprise d'une source pickup non payée. Restent les kills au milieu de PREPARED/paiement/publication/retrait/fermeture, vrais refus d'accès aux fichiers, drops GUI, mort keepInventoryfalse, inventaires de conteneurs/hoppers et autres échanges. Ces cas doivent être traités et vérifiés avant toute garantie globale de conservation des objets rares. Les exemples sd2/sd3/sd4 ferment le défaut reproduit sur le Q payé, pas ces parcours par substitution. La V1.0 et la phase P2 restent ouvertes.
