# 00 — Vision et décisions de conception

## Pitch
**Legacy of the Force** est un mod unique Minecraft Java, hybride survie + RPG inspiré de Star Wars. Le joueur conserve la liberté de construire, miner et explorer, tout en apprenant la Force, fabriquant un sabre laser entièrement modulaire, construisant son speeder et découvrant des planètes avec cristaux rares, ressources et boss Jedi/Sith.

## Décisions prises par le concepteur — ne pas modifier sans accord
| Sujet | Décision |
|---|---|
| Packaging | Un mod unique, modules internes séparés |
| Direction visuelle | Blocs/personnages Minecraft ; sabres, véhicules et effets cinématographiques |
| Progression | Niveau de Force 1 à 100 ; XP principalement via éliminations de mobs/boss |
| Alignement | -100 obscur / 0 neutre / +100 lumineux ; **séparé** du niveau |
| Mob passif tué volontairement | **-0,1** d'alignement |
| Mob agressif tué volontairement | **+0,1** d'alignement |
| Mob hostile élite tué volontairement | **+0,5** d'alignement (proposition de détail) |
| Boss Sith vaincu | **+25** d'alignement lumineux |
| Boss Jedi vaincu | **-25** d'alignement obscur |
| Force | Déplacement/projection de blocs et de mobs à puissance croissante ; éclairs uniquement à haut niveau |
| Sabres | Assemblage physique de pièces ; grande liberté de design ; cristaux rares et évolutifs |
| Speeders | Construction modulaire des formes, tailles, couleurs, composants et performances |
| Planètes initiales | Six petites dimensions : Ilum, Tatooine, Endor, Mustafar, Dagobah, Jedha |
| Cristaux | Un **type principal rare** associé à chaque planète ; certains types transformables par rituel |
| Boss | **Un Jedi et un Sith par planète**, soit 12 boss |
| Expérience joueur | Exploration non linéaire, survie vanilla et quêtes facultatives |
| Plateforme privilégiée | Minecraft Java / NeoForge, version technique fixée dans README |

## Mécaniques signatures
1. **Force Physics** — manipuler le vrai décor, pas des objets purement cosmétiques : saisir, déplacer, reposer, projeter, résoudre des énigmes et créer des protections ; mêmes principes pour mobs et véhicules sous restrictions.
2. **Living Kyber** — le cristal a pureté, résonance et lien avec son porteur ; il évolue avec le style de jeu, et peut être corrompu/purifié.
3. **Speeder Forge** — construction par grille 3D de véhicules fonctionnels, performances liées aux pièces, personnalisables, réparables, plans réutilisables.

## Boucle de jeu
Survivre → combattre → gagner XP/niveau → explorer/créer → fabriquer sabre/speeder → voyager → trouver cristaux et pièces → combattre boss → améliorer équipement/Force → retour à la base.

## Relations entre sous-systèmes
- **RPG → Force** : niveau, alignement, énergie et points de compétences conditionnent les pouvoirs.
- **Force → Monde** : les blocs/mobs peuvent être déplacés ; les permissions et sauvegardes sont respectées.
- **Force ↔ Sabres** : résonance du cristal influence certaines caractéristiques de pouvoirs ; le combat octroie XP validée côté serveur.
- **Force ↔ Speeders** : stabilisation, freinage et soulèvement limité selon masse et état du véhicule.
- **Planètes → Sabres/Speeders** : cristaux et composants de technologie exclusifs.
- **Boss → RPG/Planètes** : XP significative et ±25 d'alignement ; récompenses ; état persistant des arènes.
- **Quêtes → Exploration** : donnent des indices et des objectifs, mais ne sont pas la seule voie d'accès au contenu.

## Ce qui n'est PAS promis en V1
Vaisseaux pilotables dans l'espace, mondes de taille illimitée, simulation complète de chaque bloc mobile, armées de PNJ persistantes, éditeur de modèles 3D sans limites. Il s'agit de futurs axes d'extension ; **ne pas les coder dans le MVP**.

## Principes d'équilibrage
- Les mobs ordinaires influencent **très peu** l'alignement ; les boss le transforment profondément.
- La Force doit être puissante sans rendre l'usage du sabre ou du véhicule inutile.
- Les équipements personnalisés offrent des différences tactiques, sans bonus exponentiels ni combinaison dominant toutes les autres.
- Les ressources rares sont difficilement obtenables mais renouvelables de manière encadrée en multijoueur.
- Toute mécanique de monde ouvert doit respecter les constructions des joueurs.

## Points ouverts
Les valeurs non mentionnées comme « décisions » sont des estimations modifiables après tests : formules d'XP, coût des pouvoirs, métriques de speeders, taux d'apparition, temps de recharge, coût des recettes, portée des boss. Les **types de cristal et noms de planète** sont provisoires si l'équipe souhaite une identité plus originale ou une diffusion publique sans dépendance au nom de la licence.
