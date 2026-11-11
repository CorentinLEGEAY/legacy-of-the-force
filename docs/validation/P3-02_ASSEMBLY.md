# P3-02 — assemblage durable

**EN COURS**, 11 novembre 2026, 19:54. Le vrai bouton Créer est implémenté et vérifié par GameTests ; parcours graphique et arrêt brutal réel à compléter. Garage d'instance/recharge et validations de phase restent ouverts.

Mini-plan : reçu versionné dans l'attachment du joueur, images natives avant/après du devis, journal durable par joueur et coordinateur idempotent ; barrière joueur, publication gelée puis activation de la même entité, branchement du menu et tests. Références : AGENTS, décisions, architecture, SPEED-01/03/05/06/10, équilibrage et sécurité. Sources épinglées de PlayerDataStorage, attachments et gestionnaire d'entités lues avant implémentation.

## Transaction réelle

`AssemblyReceipt` est immuable, schéma 1, révision monotone et UUID de dernière opération ; copie à la mort. Schéma inconnu conservé et refus de mutation. `AssemblyTransaction` conserve images natives Inventory + reçu et instance initiale avec `assemblyId`. Un seul dernier journal par joueur, sans parcours global ; NBT borné 256 Kio, format/types/identités/phases/chaîne de reçus vérifiés. Données inconnues préservées dans le reçu et le journal, formats futurs conservés sans réécriture.

1. Revalider acteur, atelier, plan effectivement tenu et sauvegardé, matériaux et spawn. Le curseur doit être vide. Chercher l'un de quatre côtés du garage, sol proche et solide sans fluide, empreinte libre/chargée/dans les droits. Aucun matériau consommé en cas de refus.
2. Sauvegarder le joueur par le writer vanilla, attendre le fichier, fsync puis relire Inventory/reçu identiques à l'avant-image. Quatrième champ rendu accessible, toujours final : `PlayerList.playerIo`. Les hooks natifs de sauvegarde restent actifs ; pas d'écriture alternative du fichier joueur.
3. Écrire PREPARED avant toute consommation. Appliquer l'après-image validée par un aller-retour des codecs natifs, avancer le reçu dans **le même agrégat joueur**, sauvegarder/fsync/relire ; puis journal PAID.
4. Créer/retrouver exactement le véhicule initial gelé par `assemblyId`, propriétaire/plan/UUID inchangés. Barrière native d'entité, puis journal PUBLISHED. Le véhicule gelé refuse le pilotage et ne simule pas sa conduite.
5. Retirer ce verrou du même véhicule, sauvegarder et relire son entité, puis journal DONE. Toutes ces étapes sont exécutées sur le thread serveur sans tick de gameplay intermédiaire. Si le serveur disparaît avant DONE, la reprise termine la même opération ; elle ne crée pas une nouvelle identité et ne redébite pas.

PREPARED avec fichier joueur avant paiement et aucune entité : rétablir l'avant-image seulement si la mémoire correspond à une des deux images connues, puis ABORTED. Après paiement durable : retrouver/publier/activer la même instance. Après DONE, le reçu reste vérifié mais l'inventaire peut évoluer normalement ; le journal ne ressuscite jamais un véhicule détruit. Divergence d'inventaire/reçu, stockage indisponible, format inconnu ou ambiguïté : journal conservé, aucun écrasement arbitraire ni nouveau véhicule ; reprise tentée puis déconnexion explicite si elle ne peut pas être établie, pour empêcher des transferts pendant une incertitude de paiement. Les restaurations manuelles partielles de sauvegardes ne sont pas réparées silencieusement.

Au besoin, reprise du seul chunk d'assemblage avec ticket vanilla temporaire, statut observé du vrai LevelChunk, flush/processus normal des chargements d'entités. Pas de flag forceload persistant, de scan de dimension ou de rétrogradation d'un chunk actif à un statut artificiel. La cadence globale serveur limite les barrières coûteuses : 40 ticks par défaut, configurable. Nouvelle batterie consommée : charge initiale 100 % par défaut, fraction configurable ; pas de carburant gratuit récurrent ni de remboursement de pièces livré ici.

## Interface

Enregistrer le plan, puis Créer. Messages FR/EN de succès, plan à enregistrer, curseur à libérer, pièces, permissions/espace/cadence ou reprise indisponible. Le plan et son UUID restent dans la main ; le véhicule a son identité et sa propriété propres. Le compteur possédé inclut aussi la main secondaire comme le devis. Une tentative réussie ou en reprise ferme la fabrication dans ce menu : rouvrir pour une autre fabrication volontaire. Un nouveau plan ou bouton rejoué ne peut pas consommer à nouveau dans l'ancien menu.

## Vérifications automatiques

`./scripts/gradle.ps1 build runGameTestServer --console=plain` : **80 JUnit, 53 GameTests requis réussis**, BUILD SUCCESSFUL à 19:52:13, `.tools/test-speeder-assembly-final.log`.

- Cinq nouveaux JUnit, sept checkpoints interrompus avec réinstanciation du coordinateur et relecture de vrais fichiers NBT : avant/après paiement, PAID, publication durable, PUBLISHED, activation et DONE ; reprise répétée sans paiement/instance supplémentaires. Mutation mémoire avant écriture du joueur, divergence et schéma futur conservés, inventaire normal après clôture, destruction sans respawn automatique, remplacement d'une opération pendante et reçu malformé refusés.
- Quatre nouveaux GameTests : paiement par le véritable writer joueur et stockage d'entités (dont pièce en main secondaire), plan inchangé, DONE lu et conduite effective après publication ; interruption après barrière native d'entité puis reprise sur le même UUID ; bouton de garage branché au service enregistré et nouvelle tentative du même menu refusée même avec pièces rechargées ; refus curseur/pièces/plan/protection/zone sans consommation ni journal de paiement.
- Les interruptions injectées ne sont pas encore une preuve d'arrêt brutal du processus Java. Le parcours complet client réel et le redémarrage de l'assemblage payé restent à consigner.

Point de reprise : code prêt au contrôle graphique sur dédié, fixture additive `assembly_setup` sur monde de validation uniquement, au-dessus du toit du Nether. Réutiliser le plan et les quatre pièces de ForceTesterOne ; ne pas invoquer la fixture gratuite `lotfvalidation speeder` comme preuve de fabrication. Prochaine suite après ce parcours : panne de processus pendant assemblage, garage/recharge, solo et lifecycle complet. P3-02 et la V1.0 ne sont pas déclarés validés.
