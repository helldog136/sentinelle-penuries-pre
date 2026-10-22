# Sentinelle

Service de signalement des pénuries de médicaments de l'**Agence Fédérale Fictive de Santé Numérique (AFFSN)** de la Valdarie.

> **Avertissement.** Tout est fictif : l'agence, le pays, les régions, les identifiants, les registres et les nomenclatures. Aucune donnée patient, aucun secret, aucun nom réel d'organisme. Si une ressemblance avec la réalité vous frappe, c'est de la paperasse qui se ressemble partout.

## Contexte

En Valdarie, une boîte de médicament peut disparaître des étagères sans prévenir. Les distributeurs agréés déclarent les ruptures ; les pharmaciens et les prescripteurs veulent savoir ce qui manque, et depuis quand. Sentinelle reçoit ces déclarations, les expose, garde une trace infalsifiable de qui a fait quoi, et calcule chaque nuit un **indice de tension** par région :

- Haute-Paperasse
- Basse-Validation
- Plateau du Legacy
- Côte du Timeout
- Marais de la Recette
- Province du Ticket Ouvert

Une rupture qui dure s'alourdit avec le temps. Une rupture qui touche plusieurs régions compte dans chacune d'elles. Au-delà d'un seuil configurable, la région est signalée.

## Architecture

Un seul module Maven, structuré en architecture hexagonale. Le domaine et l'application ne dépendent d'aucun package Spring, et un test ArchUnit veille à ce que cela reste vrai.

- Décision d'architecture : [ADR-001, architecture hexagonale](docs/adr/ADR-001-architecture-hexagonale.md)
- Authentification et autorisation : [ADR-002, JWT signé et rôles](docs/adr/ADR-002-authentification-autorisation.md)
- Journal d'audit immuable et indice de tension : [ADR-003](docs/adr/ADR-003-audit-immuable-et-indice-de-tension.md)

En bref : les distributeurs écrivent en **gRPC**, les pharmaciens et prescripteurs lisent en **REST**, un job **Spring Batch** calcule l'indice de nuit, et chaque opération sensible laisse une entrée dans un **journal append-only à chaînage de hachage**.

## Contraintes techniques

- **Java 21**
- **Spring Boot 3**
- **Maven**, via le wrapper fourni (`./mvnw`) ; pas besoin d'installer Maven
- **gRPC** pour la surface d'écriture (distributeurs)
- **REST** pour la surface de lecture (pharmaciens, prescripteurs)
- **Spring Batch** pour le calcul nocturne de l'indice de tension
- **Flyway** pour les migrations de schéma
- **PostgreSQL** en cible ; **H2** pour les tests

## Démarrer

Construire et vérifier le projet, tests et contrôles d'architecture compris (attention : tant que l'évolution « produits essentiels » n'est pas implémentée, 181 tests d'acceptation sont rouges *volontairement*, voir « État du projet ») :

```bash
./mvnw verify
```

Le rapport de couverture JaCoCo est généré après les tests. Ouvrez-le ici :

```
target/site/jacoco/index.html
```

Hors profil `test`, la clé publique de vérification des jetons, l'émetteur et l'audience doivent être fournis par variables d'environnement. Sans eux, le service refuse de démarrer, comme il se doit (voir ADR-002).

Pour lancer la démo locale, générer les clés de démonstration et activer les adaptateurs : [docs/DEMARRAGE.md](docs/DEMARRAGE.md).

## État du projet

> **Avertissement : phase rouge.** L'évolution « produits essentiels » est committée en tests seuls : 181 tests rouges, aucune ligne d'implémentation. **`./mvnw verify` échoue volontairement** tant qu'elle n'est pas faite. Les 942 tests historiques restent verts.

Le service de base (ingestion gRPC, consultation REST, job batch de l'indice de tension, journal d'audit à chaînage de hachage, sécurité JWT) est livré et testé. Les migrations Flyway vont de V1 à V4 ; V4 crée le référentiel `produits_essentiels`, pour l'instant sans code qui s'en sert. Les adaptateurs REST et gRPC et le batch sont **désactivés par défaut** : ils s'activent par `sentinelle.<adaptateur>.enabled` (voir [DEMARRAGE](docs/DEMARRAGE.md)).

L'évolution réglementaire « produits essentiels » (ADR-004) rend l'alternative obligatoire pour une rupture de plus de 30 jours, et applique une pondération ×2 à l'indice de tension pour ces ruptures. Son état : ADR-004, contrats additifs et tests d'acceptation sont committés ; reste l'implémentation. Voir [ADR-004](docs/adr/ADR-004-produits-essentiels-et-alternative-obligatoire.md).

Au dernier relevé, la suite compte 1 175 tests : les 942 tests historiques, qui restent verts (dont un ignoré, qui exige des droits non-root), et 233 de plus, dont **181 rouges attendus**. Ils couvrent le domaine, la sécurité, gRPC, REST, la persistance, le batch, l'audit, et des scénarios de bout en bout. Avant l'évolution essentiels, la couverture hors code généré était d'environ 97,7 % des lignes et 92,9 % des branches. Les tests de persistance tournent sur H2 : un PostgreSQL réel n'a pas encore été testé.

Limite connue de l'indice de tension : il lit le statut courant des ruptures, il ne rejoue pas leur historique (voir [ADR-003](docs/adr/ADR-003-audit-immuable-et-indice-de-tension.md)).

## Structure du dépôt

| Emplacement | Contenu |
|---|---|
| `src/main/java/.../domain/` | Modèle métier, règles, indice de tension. Sans Spring. |
| `src/main/java/.../application/` | Ports (entrants et sortants) et services d'application. Sans Spring. |
| `src/main/java/.../adapter/in/grpc/` | Ingestion des signalements par les distributeurs (gRPC), conditionnée par `sentinelle.grpc.enabled`. |
| `src/main/java/.../adapter/in/rest/` | Consultation par les pharmaciens et prescripteurs (REST), conditionnée par `sentinelle.rest.enabled`. |
| `src/main/java/.../adapter/out/persistence/` | Persistance JPA des ruptures. |
| `src/main/java/.../adapter/out/audit/` | Journal d'audit à chaînage de hachage et vérification d'intégrité. |
| `src/main/java/.../batch/` | Job Spring Batch `calculerTensionNocturne`, conditionné par `sentinelle.batch.enabled`. |
| `src/main/java/.../security/` | Vérification des jetons JWT, rôles, chaîne de sécurité REST et gRPC. |
| `src/main/java/.../config/` | Câblage des services et des horloges. |
| `src/main/resources/config/` | `security.yml`, `grpc.yml`, `batch.yml` : références à l'environnement, sans secret. |
| `src/main/resources/db/` | Migrations Flyway `V1` à `V4` (schéma ; `V4` pour le référentiel `produits_essentiels`) ; `db/demo/V1000` (données de démonstration). |
| `src/main/proto/` | Contrat gRPC d'ingestion. |
| `docs/` | ADR, contrat OpenAPI, guide de démarrage, catalogue de démonstration, suivi de délégation. |
| `docs/donnees-bilan/` | Données brutes et scripts derrière les chiffres du [bilan](docs/BILAN-DEMO.md). Voir son [README](docs/donnees-bilan/README.md). |

Les tests sont dans `src/test/java`, dans les mêmes paquets que le code qu'ils vérifient, plus `integration/` (bout en bout) et `ArchitectureTest` (ArchUnit).

## Une démo de développement agentique

Ce dépôt sert aussi de démonstration. Le **tech lead** ne code pas tout seul : il découpe le travail et le délègue à des sous-agents, chacun dimensionné à sa tâche.

| Surnom | Modèle | Rôle typique |
|---|---|---|
| **Stagiaire survitaminé** | haiku | Tâches mécaniques et bornées, scaffolding, documentation. Rapide, et il ne demande pas de café. |
| **Senior pragmatique** | sonnet | Implémentation courante, décisions locales. Il lit la doc avant de la contredire. |
| **Architecte qui a tout vu** | opus | Décisions structurantes, contrats, tests qui fixent le comportement. Il a vu passer trois réorganisations de la paperasse. |

Le suivi vague par vague, avec la légende complète, est dans [docs/DELEGATION.md](docs/DELEGATION.md).

## Pour les lecteurs du dépôt

- **Bilan** de la démo (vagues, modèles, tests, sécurité, coût, limites) : [docs/BILAN-DEMO.md](docs/BILAN-DEMO.md).
- **Note de présentation** de la démo : [docs/DEMO-DELEGATION-AGENTIQUE.md](docs/DEMO-DELEGATION-AGENTIQUE.md).
- **Journal de délégation**, tâche par tâche : [docs/DELEGATION.md](docs/DELEGATION.md).
- **Données du bilan** (rapports, JSON, scripts) : [docs/donnees-bilan/](docs/donnees-bilan/README.md). Les scripts décrivent la méthode, mais ne se relancent pas tels quels.
- **Décisions d'architecture** : [docs/adr/](docs/adr/), ADR-001 à ADR-004.

### Qui a fait quoi

Les commits issus d'une délégation portent un trailer `Delegated-to:`. Pour voir, commit par commit, quel agent a fait quoi :

```bash
git log --format='%h %s%n    %(trailers:key=Delegated-to,valueonly)'
```

### Un cycle TDD, du rouge au vert

Un lot TDD se lit en trois commits : squelettes, tests rouges, implémentation verte.

- Adaptateur gRPC d'ingestion : `cf51593` (squelette), `2725e38` (tests `[red]`), `5754a94` (vert).
- Domaine : `6ac844b` (squelette), `a01c74d` (tests `[red]`), `f7bd6e5` (vert).

Le lot « produits essentiels » s'arrête au rouge : ses commits `[red]` sont là (`3370668`, `13c050c`, `39aed38`, `f1cde3a`, `813f744`), pas encore les verts.

## Responsabilité

Projet de démonstration, fictif, sans valeur opérationnelle. Il ne doit être ni déployé pour de vrai, ni utilisé pour prendre une décision de santé.
