# P2-01 — dépôts du curseur et persistance de la grille personnelle

23 novembre2026. **Tranche implémentée et vérifiée ; P2-01/P2/V1.0 restent ouverts.** Portée : inventaire personnel, curseur et grille2×2. Les menus de conteneurs/ateliers, cristaux libres, mortfalse et transferts hoppers restent à sécuriser ; pas de garantie globale des objets rares.

## Changement jouable

Le dépôt extérieur du curseur et le THROW d'un slot personnel contenant un sabre passent par le vrai clicked vanilla. La publication de l'ItemEntity réelle est différée jusqu'après le retour du caller, qui peut alors avoir réellement vidé son curseur. Une avant-image complète inventaire/menu est sauvegardée, puis état final et reçu sont engagés par le coordinateur existant. Aucun endpoint client ajouté. Un refus avant intention remet exactement les mêmes slots/cursor/grille ; après intention, la reprise utilise le reçu natif et compte aussi les sabres présents au curseur/grille sauvegardés.

L'image versionnée `legacyoftheforce:personal_menu` contient une liste d'items : Slot0 curseur, Slot1–4 entrées de grille. Le résultat dérivé d'une recette n'est jamais sauvegardé. Le reader conserve les images chargées jusqu'à restauration après validation complète au login ; une sauvegarde intermédiaire ne les remplace pas par une grille vide. Format futur/invalide : image opaque conservée et login refusé, sans effacer de stack. Anciennes sauvegardes sans image compatibles.

La fermeture d'un menu personnel vivant conserve ses débordements dans les emplacements personnels au lieu de les publier sans garantie. Le reste de l'interface se ferme normalement ; l'utilisateur peut retrouver ces objets en rouvrant son inventaire. Si tout rentre, vanilla les remet dans l'inventaire principal. Les deux dispositions ont une sauvegarde native vérifiée. Les autres menus et les acteurs morts gardent leur comportement antérieur : ils ne sont pas revendiqués sécurisés.

## Tests et correction d'implémentation

Première compilation : Inventory.load attend uniquement ListTag dans la version épinglée ; appel corrigé, compilation suivante réussie. Une réécriture des langues par le Python Windows avait aussi altéré l'encodage de valeurs existantes ; les valeurs d'origine ont été rétablies depuis Git et comparées clé par clé en UTF8. Seuls les deux nouveaux messages restent ajoutés.

Six GameTests initiaux : clic extérieur avec inventaire plein/cursor identifié et grille non vide ; nouveau chargement natif avec save avant restauration ; schéma futur conservé et transfert refusé ; vrai refus de partage Windows puis nouveau clic réussi ; reprise d'un DROP non payé dont la seule copie est au curseur ; pickup avec matériaux temporaires préservés. Les anciennes attentes qui interdisaient un curseur/grille non vides sont remplacées par ces vérifications de conservation, sans supprimer le test plein/futur.

Un septième test ferme réellement le menu d'un inventaire plein, vérifie aucun sabre publié, sauvegarde immédiate du débordement et nouveau chargement/restauration. La marge de fermeture est apparue pendant la revue des sources InventoryMenu.removed/ServerPlayer.doCloseContainer ; elle est couverte par un troisième mixin personnel. Ce cas plein est vérifié par GameTest ; son parcours graphique réseau complet n'est pas déduit des autres essais.

Régression finale `.tools/saber-personal-menu-close-regression.log` : **BUILD SUCCESSFUL1m2s,120GameTests requis passent20:24:01**. JUnit133, zéro échec/erreur/ignoré. Les deux régressions précédentes119GT sont conservées sous `.tools/saber-personal-menu-regression{1,2}.log`.

## Vrais clics, kills et nouveaux clients

Copies arrêtées sw2, aucun objet/inventaire/position attribué par la fixture. Deux clients non-OP s'arment ; le premier ouvre son véritable écran personnel, déplace un lingot de son inventaire vers la grille par clics vanilla, prend le sabre identifié au curseur puis clique à l'extérieur. Sa projection client du curseur et de la grille est enregistrée avant l'envoi. À PREPARED ou HELD, le lanceur vérifie monde/PID/démarrage/arguments/port avant le vrai kill OS. Deux clients observent le reset et sortent0 ; runner serveur nonzero attendu. Pas de stop/save artificiel avant le kill.

| Essai → reprise | Frontière | Résultat |
|---|---|---|
| gm1 → gmr1 | PREPARED | Reçu0, source retrouvée au curseur, aucune publication |
| gm2 → gmr2 | HELD | Reçu1, seule entité retrouvée, curseur vide |
| gm3 → gmr3 | HELD, code final | Même conservation avec fermeture protégée et langues corrigées |

Chaque reprise utilise une nouvelle JVM et deux nouveaux clients, sans réinjection. Le callback de login restaure les entrées personnelles, les deux clients confirment leurs propres états et présence/absence de l'entité. Une aide de fixture empêche seulement le pickup de la cible pendant l'observation. Sauvegarde/arrêt normaux, trois sorties0 sans force. Vérificateurs `.tools/saber-personal-gm{1,2,3}-check.json` PASS : six processus distincts par paire, quatre identités originales uniques, un seul exemplaire cible, même lingot/recettes/components et reçu0 ou1, journal final vide. Les comparaisons portent sur inventaire + menu : le regroupement vanilla des stacks lors de la fermeture peut changer les slots sans changer les quantités/composants. Le résultat de recette n'est pas compté comme un objet possédé.

Capture gm3 avant dépôt réellement inspectée : écran d'inventaire natif, lingot dans la grille, poignée détaillée au curseur et autre poignée dans l'inventaire, HUD français correct. Notifications vanilla hors ligne masquent une partie du haut droit ; aucune finition artistique globale déduite. Clients sur DISPLAY3, vérifiés dans chaque log par le checker.

```powershell
.\scripts\test-adventure-preparation.ps1 -Run <neuf> -Mode SaberCrash -PreparationRun sw2 -PersonalMenu -TransferStage HELD
.\scripts\test-adventure-preparation.ps1 -Run <reprise> -Mode SaberCheckpointRecover -PreparationRun <neuf>
& $validationPython scripts/check-saber-personal-menu.py <neuf> <reprise>
```

Répéter avec PREPARED. Le switch PersonalMenu est limité au SaberCrash isolé. gm1–3 et gmr1–3 restent conservés ; la fermeture du caller n'est ni simulée ni remplacée par une attribution serveur.

## Distribution

JAR SHA256 **2D023419B71352AA29E3DDFB112FADB01E84828B06DA5BE84EBE6AD4CD57C341**. jarpm1 : deux serveurs installés11420/26768, sorties0 sans force. `scripts/check-packaged-server.py jarpm1` PASS : six mixins requis présents/chargés, aucun harnais distribué, Ilum et flux natif conservé entre boots. Le checker exige les trois mixins d'arène et les trois nouveaux personnels ; aucun contrôle d'arène retiré.

Finition/test solo intégré avec les nouvelles images, fermeture pleine graphique à deux clients, autres menus et cycle de mort restent des validations supplémentaires. Cette tranche n'achève pas la phase P2 ni la V1.0.
