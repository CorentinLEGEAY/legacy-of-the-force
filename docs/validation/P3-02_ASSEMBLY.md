# P3-02 — assemblage durable

**EN COURS**, 11 novembre 2026, actualisé à 20:05. Le vrai bouton Créer est implémenté, vérifié par GameTests et par un client réel sur dédié, avec redémarrage. Arrêt brutal réel, garage d'instance/recharge et validations de phase restent ouverts.

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
- Les interruptions injectées ne sont pas une preuve d'arrêt brutal du processus Java. Le parcours client réel et le redémarrage de l'assemblage payé sont détaillés ci-dessous.

## Parcours graphique et redémarrage réellement exécutés

Code `69d32fd`. Client ForceTesterOne sur dédié, un seul client isolé. Fixture `assembly_setup` ajoute une nouvelle plateforme au-dessus du toit du Nether sans nettoyer les anciennes constructions ; plan et quatre pièces préexistants du joueur, aucune invocation de la fixture de véhicule gratuit. Menu affichant la facture et les bonnes statistiques : clic **Build** → **Vehicle assembled**, quatre pièces disparues et papier toujours présent. Le véhicule apparaît près du garage, sur `(1002.5,240,1005.5)`.

Lecture NBT : journal DONE, révision joueur 1, inventaire réel égal à l'après-image et reçu égal au reçu attendu, papier avant/après inchangé. UUID payé **`bba25dcf-edfa-4861-8f06-5596db06ad35`**. Déplacement du joueur près du véhicule pour le test, puis **clic droit normal** pour monter, HUD à 1000 ; touche locale Z : position serveur `(1002.5,242.00002011090183,1005.9257480516825)` et charge 972,4239999999991. Ce véhicule provient du vrai paiement, pas de la commande de fixture.

Fermeture normale du client en selle à 20:00:47, arrêt du serveur/sauvegardes propres. Fichier joueur : une entrée d'inventaire (le plan), révision 1, aucun RootVehicle. Redémarrage du même monde, reconnexion à 20:02:29 : même UUID, propriétaire/design/quatre intégrités, pose stationnée `(1002.5,240,1006.5364141425248)`, charge restante **904,2639999999965**, aucun retour à la charge initiale. Sélecteur de tous les speeders à 20 blocs de la plateforme : une seule réponse, cet UUID. Après nouvelle fermeture/sauvegarde : journal complet inchangé, inventaire toujours égal à l'après-image et reçu toujours identique, sans RootVehicle. Cela vérifie les arrêts normaux ; ce n'est pas une panne de processus.

Traces locales : `.tools/speeder-paid-first-dedicated.log`, `.tools/speeder-paid-reload-dedicated.log`, `.tools/speeder-assembly-client.log`, `.tools/speeder-paid-reload-client.log`, `.tools/assembly-{journal,player}-after-{build,reload}.json`. Une comparaison JSON ordinale initiale était non probante à cause de l'ordre/encapsulation des objets ; comparaisons structurelles Python effectuées ensuite et vraies. Archive inspectée, classes de validation/GameTests absentes. JAR SHA-256 : `37E282FF67BC6E9515DFA49B8304C67782A6EBB3F40BF8C5768123201E3B2DFD`.

Point de reprise : tout fermé proprement, plan ForceTesterOne payé une fois, quatre pièces consommées, reçu révision 1 et véhicule ci-dessus sur la plateforme. Ne pas redonner des pièces pour prétendre une reprise du même paiement ni invoquer `lotfvalidation speeder` comme preuve. Prochaine tranche : vraie panne de processus pendant assemblage, puis garage/recharge, solo et lifecycle complet. P3-02 et la V1.0 ne sont pas déclarés validés.
