# Première refonte mécanique et lancement écran3

Demande du concepteur23novembre2026 : approfondir fortement le design des items vers des objets crédibles, et ouvrir les instances sur l'écran3. **Première famille retravaillée, pas une validation artistique finale de toute la V1.0.** Cristaux, speeders, autres items et affinage des silhouettes/matières restent à poursuivre.

## Ressources réellement intégrées

`scripts/generate_saber_design.py` génère onze modèles JSON : sept pièces mécaniques (deux émetteurs, col, poignée, commande, circuit, pommeau) et les quatre sabres standard/griffes, éteint/allumé. Colliers à marches chanfreinées, bagues cuivre, grip nervuré, boîtier de commande, bouton et témoin distincts, contacts du circuit et griffes de protection. Les proportions et dimensions de raccord sont communes aux deux variantes. Les sept textures32×32 alliage/arête/acier sombre/grip/cuivre/témoin/bouton sont originales, déterministes et sans entrée externe. Les modèles ne référencent plus les textures de blocs de fer/cuivre/béton pour leurs poignées. Les deux volumes lumineux et leurs données NeoForge sont conservés depuis les modèles de lame existants ; aucun changement de combat ni de composants sauvegardés.

Générateur exécuté deux fois : empreintes identiques. Onze modèles parseables, chaque volume a des dimensions positives dans les limites natives, références de textures résolues, sept PNG32×32 RGB. Les crédits enregistrent la provenance. Le rendu reste celui de Minecraft sans shader obligatoire ni promesse de matériaux PBR. Il faut encore approfondir les profils arrondis, les assemblages et visseries, les vues en main/lame active et le reste du catalogue ; ces onze modèles sont une première passe, non le niveau de finition final demandé.

## Vérifications effectuées

`scripts/gradle.ps1 build runGameTestServer --console=plain` : BUILD SUCCESSFUL56s,112GameTests requis passent19:31:40 ;133JUnit, zéro échec/erreur. Log `.tools/saber-design-regression.log`.

sv1 : deux ateliers natifs sur nouvelle copie sw2, serveur et deux clients sorties0, inventaires/profils identiques à la source. `check-saber-workshop-network.py sw2 --recovery sv1` PASS. Les deux captures reloaded sont réellement inspectées : nouveaux modèles en inventaire, interface utilisable, mais notifications vanilla hors ligne recouvrant l'aperçu. Pas de certification visuelle du grand aperçu à partir de ces images.

sv2 : ajout d'une galerie de contrôle explicitement réservée aux tests. Elle affiche les vrais modèles chargés avec des stacks de prévisualisation purement clients, sans les insérer dans les inventaires serveur. Capture réellement inspectée : détails mécaniques visibles, mais titres/cartes/poignées se chevauchent à l'échelle GUI2 et notifications vanilla au-dessus. Le test n'est pas présenté comme un rendu de mise en page validé. Correction du placement proportionnel à la hauteur et vidage des seules notifications du client de test avant la galerie ; nouvelle exécution sv3 prévue. La galerie n'est pas distribuée dans le JAR et ne remplace pas le rendu réel en main/sol à vérifier.

sv3 exécuté après ces corrections : clients22780/17528 et serveur sortent0, sans force. Vérificateur `check-saber-workshop-network.py sw2 --recovery sv3` PASS : deux ateliers réels/refus sans crédit, inventaires/progression identiques à sw2. Deux galeries natives réellement inspectées : modèles, matières et sept pièces visibles, cartes et libellés sans recouvrement, notifications absentes. Captures `run-adventure-client-{one,two}-sv3/screenshots/saber-design-gallery.png`. Les deux logs confirment DISPLAY3/position2453,276/zone1920,0,1920,1032. Aucun objet de prévisualisation créé côté serveur. La première capture est proposée au concepteur comme résultat de cette passe, sans prétendre son approbation artistique finale.

Reproduction : `scripts/test-adventure-preparation.ps1 -Run <neuf> -Mode SaberRecover -PreparationRun sw2 -DesignGallery`. Le switch est réservé à cette reprise isolée. Les snapshots classiques reloaded/reloaded-refused restent inchangés dans le contrat du vérificateur ; la galerie a sa capture distincte. Compilation du harnais et exécution native après la régression complète ; aucune modification de production/assets depuis le build F996E7EF. Les deux géométries de lame et leurs métadonnées comparées explicitement au commit428d229 sont exactement identiques.

JAR SHA256 **F996E7EF43355CF8E978079675DFA016EC41210F9D0C5C9B48E7903EE5F231BD**. jarsv1 : deux nouveaux serveurs installés13220/25676, sorties0, vérificateur PASS/aucun harnais distribué/3mixins/Ilum/flux natif sauvegardé puis repris. Les ajustements de galerie ultérieurs restent dans gameTest et ne changent pas ce binaire.

## Placement de développement

Les runs client/clientTwo fournissent `lotf.dev.display=3`, remplaçable par `-PlotfDisplay=2`. Le client cible le nom d'adaptateur Windows, centre sa propre fenêtre dans la zone de travail et relit sa position ; aucune activation ni saisie simulée. Sans paramètre (installation ordinaire), aucun placement imposé. Écran absent : avertissement, position conservée. Code exclusivement sous client, dédié installé vérifié.

Premier essai pur4/pur5 : avertissement écran indisponible ; le nom de moniteur comporte un suffixe Monitor0 et ne correspond pas au nom d'adaptateur. Correction après lecture de la [documentation GLFW officielle](https://www.glfw.org/docs/latest/group__native.html) : glfwGetWin32Adapter. sv1 confirme les deux fenêtres sur DISPLAY3, position2453,276, zone1920,0,1920,1032. Le chemin standard `scripts/gradle.ps1 runClient --console=plain` bénéficie aussi du choix.
