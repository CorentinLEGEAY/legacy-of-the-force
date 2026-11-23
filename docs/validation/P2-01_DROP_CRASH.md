# P2-01 — duplication reproduite sur un dépôt interrompu

**ÉCHEC DE L'INVARIANT PRODUIT**, reproduction sd1 du23 novembre2026. P2-01 non validé. Aucun correctif de production n'est encore appliqué par cette tranche.

```powershell
.\scripts\test-adventure-preparation.ps1 -Run sd1 -Mode SaberCrash -PreparationRun sw2
```

Copie arrêtée de sw2, jamais d'écriture dans la source. Deux vrais clients possèdent leurs deux poignées réellement payées. Le serveur de fixture sauvegarde les deux inventaires avec le writer natif vérifié avant le dépôt. Les clients confirment leur attente de coupure ; le premier sélectionne sa seconde poignée par SWAP vanilla/hotbar puis appelle LocalPlayer.drop(false), qui envoie le paquet vanilla DROP_ITEM. Le pilote ne retire pas directement l'item côté serveur.

L'observateur ItemTossEvent retient l'ItemEntity réellement créée ; au tick suivant, il exige sa publication et un seul sabre restant dans l'inventaire vivant. Le harnais sauvegarde **seulement les entités**, via VerifiedEntitySaveBarrier et le stockage vanilla, puis écrit le checkpoint et pause au maximum15s pour le kill. Aucun second save joueur ni stop. Cet ordre de sauvegarde est volontairement contrôlé pour exercer la séparation des fichiers ; il ne représente pas une panne électrique ni une fréquence estimée du défaut.

Le lanceur vérifie monde normalisé, Java/PID/arguments/startTime et écoute loopback25586. JVM serveur **6800** tuée à **17:04:04.248 UTC** ; runner1 attendu. Clients **11256/25872** : coupure de socket constatée avec motif, sorties0 et aucun kill client. Les rapports Netty de reset sont conservés ; aucun crash JVM client revendiqué.

Relecture indépendante des octets du monde interrompu :

- `saberId f10eb6d0-c2a5-40b6-8df9-346874dcdbe6` dans le playerdata natif : **1**.
- Même identité dans l'inventaire vivant après le dépôt : **0**.
- Même identité dans la région native d'entités : **1**, ItemEntity UUID `df28bf30-d0b7-4320-b9de-809ad92de91e`.
- Total persisté : **2 exemplaires d'une seule identité**. Les sources sw2/ap4 et sd1 restent conservées.

Le checker `scripts/check-saber-drop-crash.py sd1` doit échouer sur cette conservation. L'option `--expect-duplicate` vérifie uniquement la reproduction du défaut et retourne explicitement productInvariantPassed=false ; elle ne valide jamais le produit. Aucun nouveau serveur de reprise de sd1 n'est encore certifié : cette preuve porte sur les deux copies réellement persistées après le kill.

## Cause et suite requise

La seule atomicité de l'inventaire pendant l'assemblage ne coordonne pas son transfert vers une entité. Les sauvegardes joueur et chunk sont indépendantes ; un fichier joueur ancien peut conserver l'objet dont l'entité est déjà durable. Une simple sauvegarde anticipée du joueur éviterait cette duplication mais ouvrirait une fenêtre de perte avant publication de l'entité.

Correction à concevoir : journal durable du transfert avec identité de l'objet et de l'entité, reçu sauvegardé dans le même agrégat que l'inventaire, barrières natives et récupération idempotente. Vérifier aussi le sens pickup, les refus d'écriture et une vraie reprise après kill. Ne pas « corriger » en effaçant arbitrairement un doublon ou en annulant seulement ItemTossEvent : son API épinglée précise que l'annulation ne rend pas l'objet retiré au joueur.

Incident de compilation du harnais : ensureHasSentCarriedItem est privé en21.1.265. Source épinglée consultée : MultiPlayerGameMode.tick appelle déjà cette synchronisation ; appel privé retiré, délai16ticks de sélection conservé, compilation réussie avant sd1. Aucun accès réflexif ou patch d'API ajouté.

Régression exécutée après reproduction : `scripts/gradle.ps1 build runGameTestServer --console=plain`, BUILD SUCCESSFUL38s,131JUnit0échec/erreur/ignoré et102GameTests requis verts18:06:27. Log `.tools/saber-drop-sd1-regression.log`. Le nouveau contrôle de conservation **reste rouge** ; les tests généraux verts ne le remplacent pas. Parseurs PowerShell/diff PASS. JAR inchangé64A65E2DF3BB1D37FADA65F010F9681ADA426B58CAC451F30B4C2731342ED628, relecture jarmr1 PASS/0harnais ; aucun nouveau boot installé revendiqué.
