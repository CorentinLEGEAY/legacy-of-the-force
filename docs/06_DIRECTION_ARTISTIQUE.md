# 06 — Direction artistique, sons, UX, licences

## Cible visuelle
**Minecraft + réalisme cinématographique maîtrisé** : biomes et blocs lisibles comme Minecraft, personnages aux silhouettes cubiques, mais sabres/speeders plus détaillés, animations agréables, lumières/particules et effets sonores de qualité. Pas de photoréalisme plaqué sur des textures Minecraft.

## Référentiel graphique
| Élément | Style | Priorité |
|---|---|---|
| Blocs, minerais, temples | 16×16/32×32 cohérent Minecraft ; palettes biomes distinctes | P4 |
| Sabre | Modules mécaniques 3D clairement visibles ; lame animée et translucide | P2 |
| Cristaux | Formes facettées différenciées, couleur + symboles accessibles | P2 |
| Speeders | Silhouette hybride cubique/mécanique ; modules visibles et peintures | P3 |
| Force | Léger halo, lignes de mouvement, particules discrètes | P1 |
| Boss | Silhouette reconnaissable, télégraphie attaques, sons uniques | P4 |
| UI | Hologrammes sobres, lisibilité et navigation clavier | P1 |

## Trois profils graphiques
- **Vanilla** : effets légers, faible densité de particules, pas de postprocessing, silhouettes simples.
- **Amélioré** : particules et animations plus riches, matériaux plus détaillés.
- **Cinématographique** : effets lumineux/sonores supplémentaires *facultatifs*, ne modifiant ni dégâts ni hitbox.

## UX essentielle
- Les informations nécessaires au gameplay (énergie, cible valide, portée, impact, état d'un composant) ne doivent **jamais** dépendre uniquement de la couleur.
- Prévoir mode daltonien, sous-titres, animation réduite et réglage de secousse caméra.
- Raccourcis reconfigurables ; résoudre les conflits de touches.
- Dans les constructeurs : aperçu, liste des pièces, validation des contraintes, coûts, annulation et message d'erreur.

## Qualité des modèles et sons
- Modèles originaux (ex. Blockbench ou pipeline équivalent) ; chaque module de sabre doit s'assembler sans clipping majeur.
- Système de LOD/simplification des speeders lointains ; les modules inutiles ne déclenchent pas des rendus coûteux.
- Sons originaux ou licenciés explicitement, pas d'extraits de films/jeux.
- Textes en français et anglais via fichiers de localisation.

## Propriété intellectuelle
Le projet est un fan-mod inspiré de Star Wars. Les noms des planètes et des concepts sont utilisés ici **comme références de design**, sans autorisation présumée de Disney/Lucasfilm. Avant toute publication, distribution commerciale ou usage de logos/musiques/visuels, évaluer les droits et politiques applicables. Ne pas recopier ni extraire des assets officiels ou d'autres mods ; des références ne sont pas des permissions de réutilisation.
