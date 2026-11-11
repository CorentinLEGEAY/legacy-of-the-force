# MODULE CLIENT — HUD, écrans 3D, effets et contrôles

**Mission :** présenter les systèmes sous forme d'une expérience Minecraft cinématographique **sans influer sur leur autorité serveur**. **Priorité : P1 → P7**.

## CLIENT-01 — Règles générales
- Le client est **une projection visuelle** de données serveur ; ne jamais calculer de récompenses, stats finales, dégâts ou permission uniquement sur client.
- Toutes les classes de rendu/menus de client uniquement sont isolées du chargement serveur dédié.
- Traductions FR/EN ; affichage sans texte tronqué ; fenêtre 16:9 et écrans petits.
- Profils graphiques vanilla/amélioré/cinématique ; réduire particules, animation, lumière, secousse caméra indépendamment.

## CLIENT-02 — HUD gameplay
- En mode général : niveau et XP Force, énergie Force, indicateur d'alignement et raccourcis de pouvoirs ; HUD vanilla reste visible.
- En mode télékinésie : réticule de cible, nombre/masse du groupe, portée, coût d'énergie, statut permission et raison de refus.
- En combat au sabre : lame activée, posture, parade/recharge, énergie, état du cristal (sans spam).
- En speeder : vitesse, batterie, résistance, nombre de passagers et icônes de dégâts critiques.
- En arène : nom boss, barre vie, phase, télégraphe d'attaque et statut d'arène.
- Les couleurs ne sont jamais le seul indicateur ; icônes et textes alternatifs.

## CLIENT-03 — Écrans d'atelier de sabre
- Panneau gauche = catégories de pièces/variantes ; centre = modèle 3D tournable/zoomable ; droite = stats/coût/compatibilité.
- Parcours : ouvrir atelier → choisir type → placer pièces → sélectionner cristal → choisir peintures → prévisualiser → valider → requête serveur.
- Bouton fabriquer activable seulement si l'aperçu local estime un plan valide ; **le serveur recalcule toujours tout**.
- Échec : conserver la configuration d'aperçu et afficher motif ; jamais retirer visuellement des items avant succès confirmé.
- Tooltip cristal : type, planète d'origine, pureté, stabilité, résonance, lien et statut naturel/corrompu/purifié.

## CLIENT-04 — Écrans d'atelier de speeder
- Grille 3D avec modes ajouter/retirer/peindre/inspecter ; afficher attaches et collisions de modules.
- Afficher centre de gravité simulé, direction de propulsion et statistiques comparables avant/après modification.
- Mise en évidence : module non connecté, chevauchement, batterie absente, énergie insuffisante, taille dépassée.
- Validation serveur puis animation d'assemblage, sans inventer de véhicule avant réponse confirmée.
- Sauvegarder/charger/nommer un plan ; export facultatif sous format sûr (fichier uniquement si projet accepte, sinon item de plan).

## CLIENT-05 — Carte galactique
- Lister les six destinations avec biomes, difficulté suggérée, ressources connues, boss vaincus ; masquer les détails non découverts.
- Bouton voyage soumis au serveur, avec confirmations coût et point de retour ; ne pas déclencher le voyage seulement par sélection d'une carte.
- Afficher refus : coordonnées manquantes, énergie insuffisante, terminal indisponible, combat en cours, destination non sécurisée.

## CLIENT-06 — Menus RPG
- Profil : niveau, XP, alignement, énergie max, points, talents débloqués, journal cristaux/holocrons.
- Arbre de compétences commun/lumineux/obscur avec seuils visibles et raisons d'impossibilité.
- Si le joueur change de côté, montrer pouvoirs incompatibles « temporairement inactifs » et possibilité de réorientation.
- Ne pas autoriser des valeurs libres pour gagner un pouvoir : clic = requête `unlockPower` serveur.

## CLIENT-07 — Contrôles
Proposition **modifiable** :
| Action | Touche suggérée | Attention |
|---|---|---|
| Mode Force | V maintenu | Interaction avec mouvement normal |
| Sélection cible | V + clic gauche | Intercepte attaque seulement en mode Force |
| Ajuster distance | Molette en mode Force | Ne change pas hotbar en mode Force |
| Tourner objet | R en mode Force | Respecte raccourcis joueurs |
| Déposer | V + clic droit | Refus visible si destination impossible |
| Projeter | Attaque en mode maintien + modificateur | Éviter ambiguïté avec clic saisir |
| Pouvoir suivant | Touche assignable | Paramétrable |
| Ouvrir profil | Touche assignable | Paramétrable |
| Mode speeder | Contrôles de monture adaptés | Pas de conflit vanilla |
- L'input exact doit être testable et documenté ; **ne pas figer des commandes ambiguës** (ex. relâcher V et attaquer simultanément). Choisir une séquence non ambiguë lors de P1.
- Support manette facultatif, pas requis au MVP.

## CLIENT-08 — Animation, son et lisibilité
- Sabres : lames fluides, activation/arrêt, parades, sons originaux, variantes liées aux cristaux.
- Force : trajectoire visuelle discrète vers les cibles, effets de flottement, bruits d'impact, pas d'écran saturé.
- Speeders : suspension, jets de propulseurs, poussière/glace, bruit moteur modulé ; distances/LOD.
- Boss : télégraphie au moins 0,5–1,5 seconde selon attaques ; phases visuellement claires, avertissement audio/sous-titré.
- Option de réduction secousse de caméra, flashs, intensité de lumière, particules.

## CLIENT-09 — Synchronisation et corrections
- Les positions de blocs/véhicules affichées client sont interpolées depuis snapshots ; corrections serveurs progressives sauf collision grave.
- À un refus de Force/atelier, retirer animation provisoire et garder le monde dans l'état autoritaire.
- S'assurer qu'une UI fermée pendant transaction ou déconnexion ne laisse pas d'objets « fantômes ».

## Critères d'acceptation
- [ ] Client n'ajoute pas de récompenses sans paquet serveur.
- [ ] Écrans restent utilisables en français et dans des résolutions différentes.
- [ ] Atelier sabre et speeder affichent coûts, invalidités et résultat de fabrication authentique.
- [ ] Serveur dédié démarre sans chargement de classes client.
- [ ] Profils graphiques et options accessibilité fonctionnent ; touches reconfigurables.
