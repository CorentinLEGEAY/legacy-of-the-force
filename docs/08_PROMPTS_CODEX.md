# 08 — Prompts prêts à copier dans Codex

## Prompt de démarrage (P0-01)
```text
Tu travailles sur le projet Minecraft Java « Legacy of the Force ».
Lis d'abord AGENTS.md, README.md, docs/00_DECISIONS.md, docs/01_ARCHITECTURE.md et docs/02_ROADMAP.md.
Exécute UNIQUEMENT P0-01 : crée un squelette Java 21 / Minecraft 1.21.1 / NeoForge 21.1.x en vérifiant la version patch de NeoForge, avec modid legacyoftheforce, Gradle wrapper et configuration client/serveur.
Vérifie gradlew build (ou explique un blocage matériel réel), ajoute une procédure reproductible, puis renseigne docs/03_ETAT_DU_PROJET.md.
Ne développe pas encore les pouvoirs, sabres, speeders ou planètes.
```

## Prompt de module (remplacer NOM_MODULE et TICKET)
```text
Lis AGENTS.md, docs/00_DECISIONS.md, docs/01_ARCHITECTURE.md,
docs/02_ROADMAP.md, docs/04_EQUILIBRAGE.md,
docs/05_TESTS_ET_SECURITE.md et modules/NOM_MODULE/SPEC.md.
Travaille UNIQUEMENT sur le ticket TICKET.
Avant de coder, détaille les dépendances, les fichiers à créer et les critères de validation.
Implémente une tranche verticale fiable, avec la logique métier côté serveur et des tests.
Compile et vérifie le serveur dédié si nécessaire.
Termine en mettant à jour docs/03_ETAT_DU_PROJET.md et en listant les limites connues.
```

## Prompt de revue d'intégration
```text
Revois le code pour détecter : dépendances cycliques, APIs NeoForge inventées,
confusion client/serveur, sauvegardes non versionnées, exploit de duplication,
contournement des permissions, coût de tick excessif et tests manquants.
Lis AGENTS.md, docs/01_ARCHITECTURE.md et docs/05_TESTS_ET_SECURITE.md.
Propose d'abord les problèmes classés par gravité, puis corrige uniquement les bloquants vérifiables avec des tests.
```

## Prompt de première expérience complète (P4-05)
```text
Vérifie le parcours : naissance en Overworld → XP/Force → premier sabre →
construction/pilotage du speeder → voyage Ilum → cristal → boss Jedi/Sith →
retour Overworld → amélioration d'équipement.
Exécute ou écris les scénarios de tests solo et serveur 2 joueurs.
Rapporte toute rupture de sauvegarde, règle d'alignement, capacité de combat,
triche client ou duplication. Ne considère pas l'expérience terminée si un écran
est une maquette sans logique de gameplay.
```
