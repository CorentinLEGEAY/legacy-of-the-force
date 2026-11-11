# P3-02 — Diagnostic du véhicule au garage

## Tranche du 2026-11-11

Références : SPEED-01/06/07/10, architecture des états autoritaires serveur, P3-02 avant maintenance P3-03. Mini-plan : diagnostic serveur d'une instance possédée, projection dans le menu existant, écran utilisable sans plan tenu, tests des droits et de l'identité. Fichiers : `GarageDiagnostics`, `SpeederGarageMenu`, `SpeederGarageScreen`, `SpeederConfig`, langues FR/EN, `GarageDiagnosticsGameTests`.

Le bouton Diagnostic sélectionne le véhicule possédé le plus proche dans le rayon `garage.serviceRadiusBlocks` (6 par défaut, borné 2–8). La sélection est conservée par UUID jusqu'au retour au plan ; départ du véhicule = statut hors de portée, sans remplacement silencieux. Recherche limitée aux entités déjà chargées dans cette zone, toutes les dix ticks de serveur quand la vue est ouverte. Vérification du joueur, du garage, de la dimension, de la distance et des protections vanilla au garage et au véhicule. Aucun UUID ni chiffre d'instance envoyé par le client n'est utilisé pour choisir un véhicule.

Affichage : identifiant court issu du UUID intégral projeté, énergie de l'instance et capacité validée du catalogue, intégrité moyenne entière et nombre de modules abîmés. État occupé distinct ; assemblage en attente, données/catalogue incompatibles ou charge dépassant la capacité refusés. Les données inconnues restent intactes. Aucun matériau consommé et aucune mutation de l'instance dans cette tranche. Les commandes d'édition/fabrication sont masquées et refusées côté serveur dans la vue diagnostic.

## Vérifications réellement exécutées

- `./scripts/gradle.ps1 build runGameTestServer --console=plain` : succès, 80 JUnit et 57 GameTests requis, dont quatre nouveaux GameTests de diagnostic. Première compilation : collision de nom de variable locale dans le constructeur du menu ; corrigée, puis deux exécutions réussies. Dernier résultat : 21:01:14, `.tools/test-garage-diagnostic-final.log`.
- Tests natifs : préférence du véhicule possédé même si un véhicule étranger est plus proche ; énergie 173,25 et HP `[100,40,80,100]` donnent intégrité 80 et deux modules abîmés ; conservation exacte des données opaques et de l'inventaire ; statut occupé ; identité sélectionnée conservée malgré un véhicule plus proche ; refus de protection et de portée ; projection complète du UUID dans le menu ; accès sans plan et refus d'une intention de fabrication ; assemblage en attente et schéma futur non altérés.
- Serveur dédié démarré à 20:58:31. Client graphique ForceTesterOne connecté à 20:58:53 ; ouverture réelle du garage du Nether `(1005,240,1065)` par clic droit, puis Diagnostic par clic. Affichage observé : véhicule `32392c62`, énergie `873.7 / 1000.0`, intégrité `100%`, modules abîmés `0/4`. Texte d'aide et inventaire lisibles, boutons d'édition absents dans cette vue.
- Même vérification après fermeture du menu avec E, sélection d'un emplacement vide puis réouverture : diagnostic utilisable sans plan en main, mêmes valeurs et même identité. Aucun appui sur Échap. Interface observée en anglais ; traductions françaises présentes mais non observées sur ce client.
- Requêtes console natives à 20:59:09 et 21:00:04 : énergie exacte du même UUID `32392c62-c238-4986-bfce-605b19b70ba3` = `873.7359999999953d` avant/après inspection. Ce n'est pas une recharge.
- Client fermé normalement par Alt+F4, processus terminé avec code 0 ; serveur `stop`, toutes dimensions sauvegardées, Gradle terminé avec succès. Journal dédié local : `.tools/garage-diagnostic-server.log`.

## Limites et suite

Cette tranche n'ajoute ni recharge, ni réparation, ni stockage virtuel de véhicule. Les dégâts localisés ne sont pas encore produits par le gameplay ; le GameTest charge des HP non uniformes pour vérifier le diagnostic. Le statut disponible/occupé n'est pas une autorisation de maintenance : les futures opérations devront revérifier leurs conditions et leur paiement. Validation solo et deux clients simultanés encore ouvertes. P3-02, P3-03 et V1.0 restent non validés.

Prochaine tranche : recharge craftable payée et récupérable après interruption, sans réutiliser le journal d'assemblage DONE comme source de charge. Les reçus de maintenance devront être séparés ou coordonnés explicitement pour ne pas invalider la chaîne de révisions d'assemblage.
