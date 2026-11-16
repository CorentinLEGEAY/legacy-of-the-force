# P4-03 — Borne native des PV et groupe

Statut : tranche validée ; P4 et V1.0 restent EN COURS.

La spécification BOSS-08 exige des plafonds stricts et ne verrouille pas10000PV. Minecraft1.21.1 définit MAX_HEALTH dans [1;1024] ; getMaxHealth est final. L'ancien réglage acceptait10000 tout en plafonnant déjà sa valeur effective à1024. La configuration annonce désormais40..1024 et le calcul de groupe borne la base écrite dans l'attribut, sans patch global de Minecraft ni conversion des dégâts.

Formule : min(1024, base × (1 + supplément × (participants−1))),1..16participants. Défaut220PV et35% : solo220, duo297, quatre joueurs451 ; seize joueurs au cap1024. La population est relevée à la fin de l'introduction, comme auparavant. Une arrivée tardive ne recalcule pas la santé et ne soigne pas. Le correcteur NeoForge21.1.265 ramène les anciens10000 à1024, pas à220 ; le test exerce son vrai ValueSpec.correct. Les nombres non finis sont rejetés par le domaine ; l'adaptateur runtime utilise les valeurs par défaut pour un réglage non fini et journalise une seule alerte.

## Preuves

- boss-health-before : test JUnit rouge sur l'ancien validateur qui accepte1025 ; même assertion verte après correction.
- Trois JUnit : validateur/migration des anciennes valeurs, groupes et plafond, NaN/infinis/bornes/nombres de participants invalides.
- `test-bosses.ps1 -Run hp1 -OnlyHealth`, puis `check-boss-health.py hp1` : PID19252, deux participants natifs au cap1024, vraie attaque de128→896PV. Troisième participant rejoint sans soin ; vingt ticks plus tard santé896 et phase1. Attribut natif global inchangé, base persistée1024 (pas2048 simplement masqués par sanitizeValue), trois identités et zéro récompense.
- Le scénario laisse le boss vivant et son IA active dans le monde isolé, retire ses acteurs puis arrête normalement le serveur avant le délai d'abandon ; le checker lit donc aussi le vrai boss encore stocké dans la région d'entités. Il ne s'agit pas d'un snapshot seulement en mémoire. Ce monde de test n'est pas réutilisé comme monde de joueur.
- posthp1 PID21300 : dix scénarios dédiés passent, cinq checkers boss/loot/lifecycle/fluides/health passent. Après le correctif transversal d'I/O décrit dans CORE_ATOMIC_REPLACE.md, la suite posthp2 est rejouée intégralement ; résultats finaux dans le journal.

```powershell
./scripts/test-bosses.ps1 -Run posthp2
python scripts/check-bosses.py posthp2 --natural
python scripts/check-boss-loot.py posthp2
python scripts/check-boss-lifecycle.py posthp2
python scripts/check-boss-fluids.py posthp2
python scripts/check-boss-health.py posthp2
```

Limites : acteurs embarqués pour cette borne ; pas de nouveau duel graphique à1024PV. Les combats par défaut en solo/deux vrais clients ont leurs preuves distinctes, leurs valeurs ne changent pas. Paramètres restants de prototype, intégrations tierces et équilibre complet restent à suivre avant la clôture globale.
