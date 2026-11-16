# JAR installé — NeoForge21.1.265 / Java21

Statut : smoke test de distribution validé, sans revendiquer une V1.0 ni un nouveau parcours complet dans le serveur installé.

L'[installateur officiel21.1.265](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.265/neoforge-21.1.265-installer.jar) est téléchargé depuis Maven NeoForged et comparé à son SHA256 publié : **A547CCC30A80A675D1C61D04B55D10C45C39A86971E09731482C50A24399D1A2**. Installation CLI --installServer dans .tools/neoforge-prod-21.1.265, Java21 local. Aucun fichier d'installation/monde de test versionné.

```powershell
./scripts/gradle.ps1 build
./scripts/test-packaged-server.ps1 -Run jarfluid2
python scripts/check-packaged-server.py jarfluid2
```

Le script copie uniquement le JAR construit dans le dossier mods de cette installation dédiée isolée. Le lancement utilise le win_args.txt de l'installateur, **sans ModDevGradle, sourceSets ou classes gameTest**. Deux démarrages successifs, même monde neuf jar-smoke-*, localhost25589, sans RCON. EULA et propriétés de ce serveur de test sont fournies comme pour les autres serveurs isolés. Aucun monde préexistant écrasé.

Premier démarrage : canal de pierre/eau de fixture via console, propagation native hors protection réellement constatée par execute if block ; dimension Ilum chargée et commande de récupération RPG présente. Arrêt normal et sauvegarde. Deuxième JVM : même eau toujours présente, même propagation/sauvegarde et mod chargé. Le checker relit les régions Overworld, level.dat sans propriétaire solo, hash du JAR source/installé, metadata, classes d'injection requises et absence des harnais. Ceci n'est pas un test d'arène avec clients sur le serveur installé.

- jarfluid1 : JVM24584 puis2500, deux exits0 ; checker PASS.
- jarfluid2 : JVM21616 puis8084, deux exits0 ; checker PASS. Mode mixin.debug.verbose : les journaux constatent réellement ArenaBucketPickupMixin dans LiquidBlock et PowderSnowBlock, ArenaFluidFlowMixin dans FlowingFluid et ArenaBucketMixin dans BucketItem. Les trois mixins sont donc appliqués dans le runtime distribué, sans simple hypothèse fondée sur le fichier JSON.
- JAR0.1.0-dev SHA256 **6BF4F6E2575BC5B3170D5BE5CD6704B025C4C3569458706816BDE617E5FBDAB9**. Hash identique au JAR installé, metadata/config/FR/EN valides et aucun GameTests/Validation/ClientDriver distribué. Sauvegardes de toutes dimensions et aucun échec d'injection. Rapports/logs/résultats restent sous .tools.

Limites : pas de nouveau client sur cette installation, ni de certification d'autres versions NeoForge, OS ou modpacks. Les vrais clients dédiés/solo de développement ont leurs preuves distinctes dans P4-03_FLUIDS.md ; le test de distribution ne les remplace pas. V1.0 toujours EN COURS.
