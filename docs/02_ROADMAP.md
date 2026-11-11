# 02 — Roadmap pour Codex (tickets atomiques)

> Chaque ticket est **fini** uniquement après compilation, tests minimaux et mise à jour du journal. P0 à P4 = **MVP vertical jouable** ; P5 à P7 = version complète envisagée.

## P0 — Amorçage technique (prérequis à tout)
- **P0-01** Initialiser MDK NeoForge 1.21.1 et Gradle avec version NeoForge 21.1.x **vérifiée** ; Java 21 ; mod ID ; client et serveur de dev ; JAR construit.
- **P0-02** Créer les packages/modules internes ; registre d'événements, configuration, réseau versionné, journal de logs.
- **P0-03** Ajouter tests unitaires purs (formules), smoke test serveur dédié, pipeline de build local ; fichier d'état du projet.

## P1 — Progression et Force minimale
- **P1-01** Modèle `PlayerProgress` sauvegardé ; migration/synchro ; énergie de Force ; HUD basique.
- **P1-02** XP mobs passifs/agressifs/élites, validation attribution des kills, anti-fermes ; niveau 1–100.
- **P1-03** Alignement en dixièmes (-1000..1000), ±0,1 mobs, ±25 boss, seuils et éligibilité ; tests.
- **P1-04** Télékinésie d'un **seul bloc non-conteneur**, saisir et reposer ; transaction + crash/déconnexion ; protections.
- **P1-05** Poussée/attraction d'un mob ; distance, cooldown, résistance, synchronisation serveur.

## P2 — Sabre et cristal minimal
- **P2-01** Item de sabre composé de pièces (modèle versionné), validation de compatibilité, recette atelier basique.
- **P2-02** Un cristal bleu Ilum : stats de base, persistance, remplacement sécurisé, rendu de lame colorée.
- **P2-03** Combat simple sabre : dégâts, parade, endurance/cooldown ; tests d'attribution XP.
- **P2-04** Lien du cristal (niveaux 0–4) et premier bonus plafonné ; UI minimale.

## P3 — Speeder minimal
- **P3-01** Plans de modules (châssis, moteur, batterie, siège), schéma données, validation et UI grille minimale.
- **P3-02** Speeder monoplace assemblé : déplacement, lévitation proche sol, collision simple, énergie, garage.
- **P3-03** Statistiques calculées par composants, peintures, sauvegarde/recharge, dommages et réparation élémentaires.
- **P3-04** Interaction Force avec speeder inoccupé : ralentissement/stabilisation, caps et permissions.

## P4 — Première boucle RPG complète
- **P4-01** Dimension Ilum, biome, mine/grotte à cristaux et structures de test ; génération reproductible.
- **P4-02** Terminal de voyage, coût en ressources, sauvegarde de destination et retour sûr.
- **P4-03** Boss Jedi et Sith d'Ilum, arène protégée, trois phases basiques, XP + alignement + récompenses.
- **P4-04** Holocron/quête optionnelle menant au premier cristal.
- **P4-05** Test de bout en bout à deux joueurs : monde normal → Ilum → cristal → boss → retour → personnalisation.

## P5 — Approfondissement systèmes
- Force : poids et classes de blocs, groupes connectés 3/8/16/32, projection, coût/charge, pouvoirs lumière/obscur, éclairs de haut niveau.
- Sabres : 7 emplacements, choix de poignée/émetteur, types simple/double/courbe/garde, postures et combos, corruption/purification.
- Speeders : classes léger/moyen/lourd, plusieurs sièges, modules 3D, palette, dégâts localisés, plans partageables.
- Boss : immunités partielles, usage du décor, attaques téléguidées évitables, loot équilibré.

## P6 — Contenu planétaire
- Ajouter Tatooine, Endor, Mustafar, Dagobah, Jedha **une par une**.
- Pour chaque planète : dimension stable + biome(s), cristaux, composant unique, structures, **1 boss Jedi + 1 boss Sith**, voyage aller-retour, tests de génération et d'équilibre.
- Ajouter quêtes/holocrons locaux et progression de découverte sans bloquer la liberté de jeu.

## P7 — Finition V1.0
- Multijoueur chargé, protections, sauvegarde/crash, profiling, réglages graphiques, contrôles personnalisables, accessibilité.
- Équilibrage 1–100, anti-exploit, butin, générateur de cristaux, compatibilité mods de protection.
- Passes de modèles 3D/animations/sons originaux, traduction FR/EN, guide joueur, release notes et packaging.

## Ordre des dépendances
P0 → P1 → P2/P3 (par étapes indépendantes) → P4 → P5 → P6 → P7. **Ne pas développer six planètes avant que P4 fonctionne.**

## Grille d'acceptation d'un ticket
- [ ] Compilable, pas de dépendances inventées.
- [ ] Test solo et serveur dédié si action réseau.
- [ ] Test de redémarrage/mort/déconnexion quand pertinent.
- [ ] Pas de duplication, pas de modification de zone non autorisée.
- [ ] Logs propres, valeur configurable, UI minimale si nécessaire.
- [ ] `docs/03_ETAT_DU_PROJET.md` actualisé.
