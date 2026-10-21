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

Construire et vérifier le projet, tests et contrôles d'architecture compris :

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

Le domaine, les services d'application, les migrations, la sécurité JWT, les adaptateurs gRPC et REST, le job batch de l'indice de tension et le journal d'audit sont dans le code. Les adaptateurs REST et gRPC et le batch sont **désactivés par défaut** : ils s'activent par `sentinelle.<adaptateur>.enabled` (voir [DEMARRAGE](docs/DEMARRAGE.md)).

Le projet compte 942 tests au dernier `./mvnw verify` (dont un ignoré, qui exige des droits non-root), pour environ 97 % de couverture de lignes hors code généré. Ils couvrent le domaine, la sécurité, gRPC, REST, la persistance, le batch, l'audit, et des scénarios de bout en bout. Les tests de persistance tournent sur H2 : un PostgreSQL réel n'a pas encore été testé.

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
| `src/main/resources/db/` | Migrations Flyway `V1` à `V3` (schéma) ; `db/demo/V1000` (données de démonstration). |
| `src/main/proto/` | Contrat gRPC d'ingestion. |
| `docs/` | ADR, contrat OpenAPI, guide de démarrage, catalogue de démonstration, suivi de délégation. |

Les tests sont dans `src/test/java`, dans les mêmes paquets que le code qu'ils vérifient, plus `integration/` (bout en bout) et `ArchitectureTest` (ArchUnit).

## Une démo de développement agentique

Ce dépôt sert aussi de démonstration. Le **tech lead** ne code pas tout seul : il découpe le travail et le délègue à des sous-agents, chacun dimensionné à sa tâche.

| Surnom | Modèle | Rôle typique |
|---|---|---|
| **Stagiaire survitaminé** | haiku | Tâches mécaniques et bornées, scaffolding, documentation. Rapide, et il ne demande pas de café. |
| **Senior pragmatique** | sonnet | Implémentation courante, décisions locales. Il lit la doc avant de la contredire. |
| **Architecte qui a tout vu** | opus | Décisions structurantes, contrats, tests qui fixent le comportement. Il a vu passer trois réorganisations de la paperasse. |

Le suivi vague par vague, avec la légende complète, est dans [docs/DELEGATION.md](docs/DELEGATION.md).

## Responsabilité

Projet de démonstration, fictif, sans valeur opérationnelle. Il ne doit être ni déployé pour de vrai, ni utilisé pour prendre une décision de santé.
