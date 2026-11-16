# P4-03 — Seaux, fluides et correction des prédictions clientes

Statut : tranche validée en dédié, deux vrais clients et solo intégré. P4-03 et V1.0 restent EN COURS.

## Défauts reproduits et correction

Le seau vanilla écrit directement via BucketItem.emptyContents ; la propagation ordinaire passe par FlowingFluid.spreadTo. Dans les sources épinglées Minecraft1.21.1/NeoForge21.1.265, ces chemins ne passent pas par FluidPlaceBlockEvent. Ce dernier reste conservé pour les conversions/placements qu'il couvre.

Trois mixins communs, sans import client, refusent avant mutation : placement de seau, propagation vers une destination protégée, prélèvement par LiquidBlock/PowderSnowBlock. Seau joueur et chemin de dispenseur utilisent la même garde. Hors protection, les méthodes vanilla s'exécutent ; aucun remplacement de blocs par restauration a posteriori. Configuration required/defaultRequire1 : une injection incompatible ne doit pas devenir silencieusement facultative.

Sources : archive neoforge-21.1.265-sources.jar produite par ModDevGradle, méthodes précitées et ServerPlayerGameMode.useItem ; déclaration de configuration selon le [MDK officiel1.21.1](https://github.com/NeoForgeMDKs/MDK-1.21.1-NeoGradle/blob/main/src/main/resources/META-INF/neoforge.mods.toml), également présent sous .tools/mdk ; callbacks selon la [documentation primaire Mixin](https://github.com/SpongePowered/Mixin/wiki/Advanced-Mixin-Usage---Callback-Injectors). Aucune nouvelle bibliothèque téléchargée dans le mod : Mixin est fourni par l'environnement NeoForge.

Une propagation refusée peut épuiser son tick programmé. À la levée du verrou, BossArenaService reprogramme les fluides déjà présents dans les blocs chargés de l'arène et de son halo :968positions maximum, une fois au reset/victoire ou au chargement d'un ancien COOLDOWN par boot. Aucun chunk forcé, aucune boucle par tick et aucune écriture directe du décor.

Une seconde erreur a été **reproduite dans les deux clients** : le serveur refuse l'eau et conserve WaterBucket, l'ACK annule l'eau prédite, mais l'inventaire client conserve Bucket. Le retour anticipé vanilla sur stack serveur inchangé évite son envoi d'inventaire. Le refus envoie désormais bloc et inventaire autoritaires au joueur ; aucune nouvelle mutation d'objet serveur. Message FR/EN compréhensible, limité10ticks/acteur et1024acteurs, cache nettoyé au logout. Un dispenseur sans joueur conserve son comportement vanilla de repli : il peut éjecter le seau plein, jamais créer du fluide ni perdre/dupliquer ce seau.

## Essais natifs dédiés

```powershell
./scripts/test-bosses.ps1 -Run fluid5 -OnlyFluids
python scripts/check-boss-fluids.py fluid5
./scripts/test-bosses.ps1 -Run postfluid2
python scripts/check-bosses.py postfluid2 --natural
python scripts/check-boss-loot.py postfluid2
python scripts/check-boss-lifecycle.py postfluid2
python scripts/check-boss-fluids.py postfluid2
```

Fixture : vraie arène ACTIVE, IA du boss gelée pour isoler la physique des fluides ; équipements/poses et blocs initiaux de contrôle explicitement fournis. Appels natifs ServerPlayerGameMode.useItem, ticks du vrai DispenserBlock et ticks fluides ordinaires du serveur. Les comparaisons ne remplacent pas la simulation par des événements fabriqués.

- Eau/lave au seau refusées sans consommation ; escalier non waterloggé ; prélèvement d'eau/poudre refusé.
- Témoins de prélèvement extérieur réellement autorisés. Deux dispenseurs successifs refusés, avec un exemplaire de chaque seau plein dans les ItemEntity natives.
- Eau extérieure en canal bloquée à la frontière ACTIVE, autre canal extérieur qui coule réellement. Après reset, reprise automatique sans réécriture/manipulation manuelle de la source.
- Autel chargé après reset intact ; sauvegarde native de son lot, échantillons de blocs et objets éjectés recoupés dans les régions MCA et playerdata.
- fluid5 PID1564 : PASS + checker indépendant. **postfluid2 PID17524 : neuf scénarios dédiés PASS**, tous les checkers précités PASS.

## Vrais clients, solo et reprises

```powershell
./scripts/test-boss-network.ps1 -Run bnf3 -Fluids
python scripts/check-boss-network.py bnf3
./scripts/test-boss-solo.ps1 -Run bsf1 -TemplateRun bnf3 -Fluids
python scripts/check-boss-solo.py bsf1
./scripts/test-boss-solo.ps1 -Run bsf2 -TemplateRun bsf1 -Recover
python scripts/check-boss-solo.py bsf2
./scripts/test-boss-arena-crashes.ps1 -Run fc1 -Checkpoints SPAWN_DURABLE,LOOT_DURABLE,COMMITTED
python scripts/check-boss-arena-crashes.py fc1
```

- **bnf3** : dédié PID14588, sockets distinctes, clients13132/25428. Chacun voit réellement l'eau et le seau vide prédits, puis leur annulation/restitution serveur (preuve exportée par chaque JVM, pas seulement un log). Les deux duels et collectes restent obligatoires après ce contrôle. Gardien : quatre parades/trois contres payés/dix coups sur glace ; Sith : quatre parades/trois contres/cinq coups sur glace et une poussée de glace. +1600XP chacun, alignement−25puis0, deux reliques/huit cellules partagées. Preuves rapprochées client/serveur/fichiers/reçus/régions, checker PASS. Tous exits0, dimensions sauvegardées **03:54:04** le16novembre2026.
- **bsf1 PID4500**, vraie connexion mémoire/IntegratedServer : même rollback prédit puis les deux duels/collectes. Gardien trois parades/trois contres/dix coups sur glace ; Sith quatre parades/deux contres/une poussée de glace. Preuves serveur/client04:04:24, fermeture normale0 ; checker PASS, propriétaire embarqué/playerdata/inventaire/cristal concordants.
- **bsf2 PID14200** : copie arrêtée complète, nouveau chargement sans équipements/gains supplémentaires. Checker PASS : inventaire et propriétaire embarqué conservés, journaux byte identiques, boss morts absents, aucun gain répété.
- **fc1** : trois vrais kills après modification du coordinateur de réveil (SPAWN_DURABLE,LOOT_DURABLE,COMMITTED). Exits natifs−1, wrappers1 et reprises normales0 ; checker PASS. Zéro récompense au spawn interrompu, exactement800XP/−25 et un lot aux deux étapes de victoire. Les seize frontières précédemment prouvées restent documentées dans P4-03_ARENA_CRASH.md ; elles n'ont pas toutes été rejouées pour cette tranche.

## Refus et diagnostics conservés

- fluid1 échoue avant correction : vraie eau placée et contenu du seau consommé dans l'arène.
- fluid2 passe le refus initial ; fluid3 échoue ensuite sur eau qui ne reprend pas après reset. Réveil borné ajouté, fluid4/5 passent.
- postfluid1 : premier groupe de huit scénarios passe, fixture de dispenseur échoue sur lookup immédiat dans un chunk voisin pas encore prêt. Le seau est réellement dans le MCA à l'arrêt. Dispenseur/sortie déplacés dans le chunk dont la readiness est exigée, assertion conservée ; postfluid2 passe les neuf scénarios.
- bnf1 : visée au centre du bloc de sol fait rencontrer la cellule précédente ; prédiction attendue refusée par le harnais, pas de succès revendiqué. Visée de face supérieure corrigée.
- bnf2 : vraie divergence serveur WaterBucket/client Bucket dans les deux preuves natives/clientes, eau déjà annulée. Après resync, bnf3 et bsf1 passent.
- Première régression générale :112JUnit verts, un GT de recyclage speeder échoue sur remplacement atomique .tmp→journal ; données conservées. Origine non établie. Log conservé, pas de changement aveugle du writer financier. Relance complète : **98GT requis verts04:03:09**, BUILD SUCCESSFUL22s,112JUnit0échec/erreur/skip (UP-TO-DATE à cette relance). Stress des pannes fichiers reste P7.

## Artefact installé et limites

JAR SHA256 **6BF4F6E2575BC5B3170D5BE5CD6704B025C4C3569458706816BDE617E5FBDAB9**, aucun harnais distribué. Test standalone et contrôle des injections réellement chargées : PACKAGED_SERVER.md. Les essais restent limités à la version épinglée ; lave en propagation verticale/conversions, fluides et claims tiers, abus de paquets prolongés et profiling ne sont pas nouvellement prouvés par ce scénario d'eau. Appréciation humaine/accessibilité et les autres gates P4 demeurent ouverts. Ni P4 ni V1.0 ne sont déclarées terminées.
