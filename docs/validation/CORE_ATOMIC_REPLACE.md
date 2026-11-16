# Core — Remplacement atomique et refus de partage Windows

Deux régressions ont échoué sur un remplacement.tmp→journal d'entretien speeder : recyclage dans boss-fluids-regression, puis peinture dans boss-health-regression. Les journaux financiers ont été retenus ; ces suites n'ont pas été déclarées réussies. Les anciens logs ne contiennent que le message, sans type/stack : l'origine exacte de leurs refus n'est pas établie et aucun logiciel hôte n'est accusé.

Un verrou de partage Windows réel, FileChannel READ/NOSHARE_DELETE, reproduit une AccessDeniedException sur le même type de renommage. Le writer réessaie désormais **uniquement AccessDeniedException**, trois fois, pauses demandées2/8/20ms. Il garde fsync avant publication, ATOMIC_MOVE+REPLACE_EXISTING et lecture de contrôle. Aucune copie/suppression de repli, modification de permissions ou désactivation de protection système. Un refus permanent reste une erreur ; une interruption du sommeil conserve le drapeau d'interruption et devient InterruptedIOException. La durée d'I/O/scheduling elle-même n'est pas une garantie de latence.

Le diagnostic de SpeederRechargeService conserve désormais le type et la stack si une erreur persiste. Le journal reste récupérable ; l'essai borné n'autorise ni double paiement ni écriture non atomique.

## Tests réels

Six tests JUnit de la primitive :

- Verrou Windows permanent sur le writer public : erreur attendue, ancien agrégat lisible/intact, écriture suivante possible après fermeture.
- Premier renommage natif refusé, fermeture du vrai handle au point de reprise, second renommage atomique réussi et source déplacée. Aucun timing probabiliste de thread utilisé pour ce verrou.
- Verrou maintenu : budget de trois reprises épuisé, ancien et nouveau fichiers intacts, erreur propagée.
- Source/target sur providers fichier/ZIP distincts : AtomicMoveNotSupportedException propagée sans reprise ni fallback, deux contenus intacts.
- Payload hors budget : aucune mutation ni fichier temporaire restant.
- **1000 remplacements natifs** avec lecture concurrente : chaque lecture voit un agrégat cohérent (version/check associés), dernière version1000 relue, aucun temporaire restant.

Les trois cas de partage Windows sont conditionnels à cet OS ; sur cette machine ils sont réellement exécutés, aucun skip. Les autres sont portables.

```powershell
./scripts/gradle.ps1 test --tests com.legacyoftheforce.core.operation.AtomicNbtFileTest
./scripts/gradle.ps1 test runGameTestServer build
```

Après correction : deux régressions générales successives passent. La finale compte **121JUnit exécutés sans échec/erreur/skip,98GT requis verts05:02:17**, build38s. Dix scénarios dédiés et une nouvelle interruption réelle PLAYER_DURABLE de collecte sont également vérifiés ; détails/PID dans le journal. Le JAR modifié est redémarré dans NeoForge installé avec le smoke test de distribution.

Limites : cause passée exacte non identifiée ; ce test de partage ne simule pas une panne électrique, un disque défectueux ni toutes les sémantiques de systèmes de fichiers. AtomicMoveNotSupported reste un vrai blocage financier à résoudre par le support du stockage, jamais par une publication affaiblie.

## Course de lecture observée lors de P4-04
La première régression des quêtes reproduit un épuisement du budget de reprises pendant le stress à1000écritures/lecture concurrente (AccessDeniedException au move). Le succès précédent reste une observation datée, pas une preuve d'absence de course. Les lectures, écritures et relectures internes sont désormais coordonnées par64moniteurs réentrants de chemins absolus normalisés (casse Windows normalisée). Cache borné, aucune attente de reprise externe supplémentaire ni fallback. Cela sérialise également deux writers internes du même chemin ; les alias externes/processus tiers restent hors de ce verrou. Le scénario de1000publications et les vrais verrous permanents sont rejoués dans la régression P4-04. Cause exacte des deux anciens incidents sans stack toujours inconnue.

## Journal DurableOperationStore — régression P0 réseau

La régression générale suivant cp2 reproduit un AccessDeniedException natif au remplacement d'un intent RPG dans DurableOperationStore. Ce writer ancien utilisait encore directement Files.move, sans reprises ni coordination du writer commun. Le premier refus suspend correctement les récompenses ; cinq GT ultérieurs échouent. Log rouge conservé .tools/core-protocol-regression.log, aucune suppression de journal pour masquer la panne.

DurableOperationStore délègue désormais lecture/publication à AtomicNbtFile avec le même budget262144, mêmes enveloppes/phases et refus de liens. Fsync/publication atomique conservés, relecture de contrôle ajoutée ; mêmes reprises strictement bornées uniquement pour AccessDeniedException. Aucun assouplissement du refus financier. Un nouveau test réel NOSHARE_DELETE maintient le journal verrouillé : transition refusée, anciens octets et phase identiques, aucun temporaire ; une nouvelle instance reprend correctement après fermeture du handle jusqu'à CLOSED. Il complète les tests déterministes de libération après premier refus et d'épuisement de budget de la primitive.

Régression après correction : build39s,127JUnit0échec/erreur/skip et102GT requis verts10:34:00, dans le même environnement de tests avec récupération du journal retenu. Log .tools/core-protocol-regression-fixed.log. La cause du handle externe n'est pas identifiée ; aucun logiciel hôte n'est accusé. Le correctif partage la politique existante sans garantir la réussite de tout refus de stockage.
