# P1-05 — déchargement natif du chunk d'entités

Essai miu1 du 23 novembre 2026 : réussi, avec relecture indépendante. Il complète les essais solo et réseau de P1-05.md/P1-05_NATIVE.md et le crash réel de P1-05_CRASH.md.

```powershell
.\scripts\test-adventure-preparation.ps1 -Run miu1 -Mode ImpulseUnload -PreparationRun ap4
```

Copie arrêtée ap4, jamais de mutation de la source. Serveur **3540**, clients **24204/23340**, trois sorties 0 sans arrêt forcé. Fin du lanceur 16:34:13 UTC. Les fixtures gardent la même déclaration que le scénario crash : niveau15 par console1232XP, régénération0 et cooldown1200 configurés par TOML réel, durée8 et coût inchangés, vache à IA active avec attribut de marche0, plateforme jetable et deux connexions natives non OP.

## Chemin natif exercé

Après deux observations baseline, le premier client envoie la commande de production push. Engagement2845, déplacement réel au2847 : z254,5→255,55342150176918, active1 et vitesse horizontale non nulle. Le harnais demande explicitement `Visibility.HIDDEN` au `PersistentEntitySectionManager` de la version épinglée, puis appelle son tick. Ce chemin effectue le stockage natif via EntityStorage, retire l'entité avec la raison `UNLOADED_TO_CHUNK` et la supprime de l'index du niveau. **Le harnais n'appelle pas setRemoved et ne réinjecte aucun NBT.**

Au2848, le cleanup normal de production a arrêté l'ancien objet : active0 et vitesse horizontale nulle. Le harnais demande alors TICKING au gestionnaire. Le chargement asynchrone de production émet au2849 un véritable `EntityJoinLevelEvent` avec `loadedFromDisk=true` pour un **nouvel objet**, même UUID `48fc580e-771e-4f72-8d90-34ebd4f13623`. L'observateur HIGHEST voit active1 et le Motion sauvegardé avant le retrait ; LOWEST, après le listener de production onReload, voit active0 et Motion horizontal nul. L'immunité4045 n'est pas remise à zéro par l'arrêt de l'ancien objet ni par la reprise.

Les deux ClientLevel observent la cible reloaded à sa position sauvegardée, santé10. Au2895 et2908, le détenteur puis le pair obtiennent cooldown sans nouvelle dépense. À l'échéance naturelle4045, le harnais autorise une nouvelle intention ; engagement4057, nouvelle poussée payée et arrêt normal, z257,31636924906223. Cooldown suivant5257 ; énergie finale77,0215388077956/100. Inventaires, composants, XP/alignement et autres champs RPG préservés. Les deux clients voient reused, et la région finale contient une seule cible inactive.

## Vérification indépendante et limites

`scripts/check-mob-impulse-unload.py miu1` : PASS. Relecture des preuves serveur, événements avant/après production, retrait effectif et identité de la cible, profils/inventaires natifs, région d'entités finale, deux preuves clientes, PNG et journaux/code de sortie. Trois vues concordantes par client : baseline/reloaded/reused. Capture reloaded du pair inspectée : vache visible, HUD énergie100 et inventaire cohérent.

Le délai ordinaire d'éviction des tickets dépasse l'impulsion courte. La fixture pilote donc explicitement la visibilité du **gestionnaire de chunks d'entités** ; le terrain et les joueurs restent chargés. L'essai ne prétend pas une éviction naturelle du terrain, un test de charge ou une compatibilité de claims tiers. Il vérifie précisément le stockage/unload/reload qui peut interrompre la cible, sans remplacer les fonctions de Force et de récupération de production.

Régression finale BUILD SUCCESSFUL36s,102GT requis verts à17:35:20,131JUnit0échec/erreur/skip (tâche à jour). Même JAR64A65E2DF3BB1D37FADA65F010F9681ADA426B58CAC451F30B4C2731342ED628, relecture jarmr1 PASS/aucun harnais distribué. Consolidation et clôture du socle P1-05/P1 dans P1_PHASE.md.
