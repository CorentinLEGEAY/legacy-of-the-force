# Limite graphique à deux clients — 11 novembre 2026

Deux copies distinctes du JDK 21.0.13+9 ont été employées pour séparer les clients entre eux et du serveur/Gradle. Connexions réelles à 15:34:50 et 15:34:52, maintien des deux jusqu'à 15:41:05, soit environ six minutes ; messages et conditions serveur reçus, verrou et unload testés comme tracé dans P1-04.

Après traitement manuel de la demande de pare-feu par l'utilisateur et reprise au tour suivant, le premier client a accepté les entrées : retour au jeu puis V, saisie et énergie/HUD observés. Au passage à la fenêtre du deuxième, l'outil a répondu « failed to activate captured window » ; déconnexion du deuxième à cet instant. Rapport local `run-client-two/hs_err_pid16552.log` : **EXCEPTION_ACCESS_VIOLATION 0xc0000005, frame native glfw.dll+0x101ab**, hors JVM. Aucun rapport complet contenant les chemins/métadonnées machine n'est publié.

Cette hypothèse d'isolation n'a pas résolu le problème. Origine précise non établie ; l'association avec activation/capture de fenêtre ne démontre pas à elle seule la cause. Ne pas relancer les mêmes manipulations en boucle. La position synchronisée vue durablement par DEUX interfaces et leur interaction alternée restent **à valider sur un environnement graphique stable / par humain** avant clôture des phases concernées.

Le bloc encore tenu par le premier joueur a été annulé via le serveur, puis sauvegarde/arrêt propre du dédié à 15:41:30 et fermeture propre du premier client. Aucun de ces processus de test n'est laissé vivant après cette reprise. Le développement peut continuer sur les tranches indépendantes ; les résultats serveur ne sont pas présentés comme validation du rendu multijoueur.
