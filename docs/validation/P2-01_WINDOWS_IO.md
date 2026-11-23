# P2-01 — refus réels de partage Windows

23 novembre2026. **Neuf scénarios dédiés hors ligne vérifiés ; ce premier groupe ne certifie pas deux sockets natives. P2-01/P2/V1.0 restent ouverts.**

## Mécanisme réellement exercé

`SaberTransferWindowsIoValidation` ajoute un GameTest utilisant les vrais coordinateurs et writers. Neuf acteurs ServerPlayer distincts ne sont pas inscrits dans PlayerList ; leurs connexions de test acceptent l'appel de déconnexion sans produire de socket. Leurs playerdata sont néanmoins enregistrés par le writer vanilla et leurs ItemEntity par les fichiers natifs de région.

Un FileChannel READ avec NOSHARE_WRITE/NOSHARE_DELETE refuse réellement l'ouverture en écriture et le remplacement du fichier choisi. Une ouverture indépendante WRITE doit lever FileSystemException/AccessDeniedException ; le test conserve le message natif et compare les octets du fichier verrouillé. Aucun writer n'est substitué, aucune exception fabriquée ni ACL modifiée. Le handle est fermé en finally. Sous un autre OS, le GameTest annonce explicitement NOT_EXERCISED ; le vérificateur exige Windows et les neuf preuves, sans prétendre une validation portable de ces règles de partage.

| Chemin | Fichier verrouillé / frontière | Attendu après libération et nouveau coordinateur |
|---|---|---|
| DROP | playerdata avant avant-image | Sabre rendu au joueur, aucun journal/objet publié, reçu0 |
| DROP et PICKUP | playerdata à PREPARED | Paiement refusé, ancien playerdata conservé ; annulation DROP / source PICKUP conservée |
| DROP et PICKUP | journal à PAYMENT_DURABLE | PREPARED reste lisible mais reçu1 réellement payé ; compléter le sens voulu une seule fois |
| DROP et PICKUP | journal à HELD | Publication/retrait natif effectué ; fermeture refusée ; reprise sans second crédit |
| DROP et PICKUP | journal à CLOSED | Suppression du fichier fermé refusée ; nouveau coordinateur nettoie sans rejouer |

Les quatre copies monde après reprise sont conservées sans gravité et sans expiration dans la fixture ; cinq copies restent dans les playerdata. Ce placement de test n'est pas un parcours de jeu physique. Un second coordinateur ne change ni les fichiers joueur ni l'entité stockée. Identité, ItemStack complet et reçu sont comparés ; les journaux finaux sont vides.

## Résultats et limites

Premier build `.tools/saber-windows-io-regression1.log` :113GT requis passent, mais vérificateur indépendant en échec : les quatre entités conservées étaient ensuite effacées par le nettoyage des volumes d'une autre vague de GameTests. Les sources épinglées de StructureUtils confirment le nettoyage jusqu'à20blocs au-dessus du template. Les acteurs/objets de cette fixture sont désormais64blocs au-dessus, avec les mêmes writers et mêmes assertions ; aucun contrôle du vérificateur n'a été assoupli.

Deuxième build `.tools/saber-windows-io-regression2.log` : **BUILD SUCCESSFUL50s,113GT requis passent19:43:44**. Preuve native `run-gametest/world/validation/saber-windows-io/b3560647-78b6-42ff-b113-598896192d98/proof.nbt`. Les neuf fichiers joueur, les identités dans toutes les régions et les neuf journaux ont été relus par :

```powershell
./scripts/gradle.ps1 build runGameTestServer --console=plain
& $validationPython scripts/check-saber-windows-io.py .tools/saber-windows-io-regression2.log
```

PASS : `.tools/saber-windows-io-check2.json`, nativeInventoryAndRegionsVerified=true, nativeSocketsCertified=false. Les copies de preuve de chaque exécution ont des UUID distincts et restent conservées. Les133JUnit précédents restent verts ; aucune modification de production/assets dans cette tranche, donc même JAR installé F996E7EF déjà vérifié. Une déconnexion réelle peut aussi déclencher une sauvegarde vanilla de sortie : ce cas doit être exercé avec deux clients avant de fermer la gate réseau des IO.
