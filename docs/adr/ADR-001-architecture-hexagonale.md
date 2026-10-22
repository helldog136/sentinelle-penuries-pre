# ADR-001 : Architecture hexagonale en un seul module Maven

- **Statut** : Accepté
- **Date** : 2026-10-21

## Contexte

Sentinelle reçoit les déclarations de ruptures de stock de médicaments émises par les distributeurs agréés, les expose en lecture aux pharmaciens et aux prescripteurs, produit un journal d'audit immuable et calcule chaque nuit un indice de tension par région. Le service doit rester testable sans infrastructure (base de données, transport réseau, horloge système) et ses règles métier ne doivent pas dépendre de la façon dont on les expose ou les persiste.

Les contraintes techniques sont : Java 21, Spring Boot 3, gRPC pour l'écriture par les distributeurs, REST pour la lecture, Spring Batch pour le calcul nocturne, Flyway pour le schéma, PostgreSQL en cible et H2 pour les tests.

La taille du service est modeste : un domaine restreint, quelques cas d'usage, une équipe réduite. Le projet doit donc garder un coût de build et de navigation faible.

## Décision

Le service est structuré en **architecture hexagonale** (ports et adaptateurs) dans **un seul module Maven**. Les packages racine sont :

| Package | Rôle |
|---|---|
| `domain` | Entités, objets de valeur, règles métier pures. |
| `application` | Cas d'usage (services applicatifs) et **ports** (interfaces) entrants et sortants. |
| `adapter.in.grpc` | Adaptateur entrant : services gRPC d'écriture (déclarations de rupture). |
| `adapter.in.rest` | Adaptateur entrant : API REST de lecture. |
| `adapter.out.persistence` | Adaptateur sortant : persistance JPA/JDBC, migrations Flyway. |
| `adapter.out.audit` | Adaptateur sortant : écriture dans le journal d'audit (voir ADR-003). |
| `batch` | Jobs Spring Batch (indice de tension nocturne). |
| `security` | Authentification JWT, autorisation, intercepteur gRPC, filtre REST (voir ADR-002). |
| `config` | Câblage Spring : beans, propriétés, activation des adaptateurs. |

Règles de dépendance :

- `domain` et `application` **ne dépendent d'aucun package Spring** (ni `org.springframework.*`, ni annotations Spring).
- Les adaptateurs dépendent de `application` (et de `domain`), jamais l'inverse.
- Les adaptateurs ne se dépendent pas entre eux.

Cette dernière règle est **vérifiée automatiquement par un test ArchUnit** qui échoue au build (`./mvnw verify`) si une dépendance interdite apparaît. Le test couvre au minimum : absence de Spring dans `domain` et `application`, absence de dépendance d'un adaptateur vers un autre, absence de dépendance d'un port vers un adaptateur.

## Alternatives écartées

**Multi-module Maven** (un module par couche ou par adaptateur). Le découpage en modules imposerait les frontières à la compilation, ce qui est plus strict que ArchUnit. Le coût est toutefois disproportionné pour un service de cette taille : configuration de plusieurs `pom.xml`, versions à synchroniser, temps de build plus long, friction pour chaque changement transverse. Le gain de rigueur ne compense pas ce coût ; ArchUnit donne la même garantie sur les dépendances pour une fraction de la complexité.

**Architecture en couches classique** (contrôleur, service, repository, avec les entités JPA traversant toutes les couches). Elle est plus familière, mais elle couple la règle métier à la persistance et au transport. Les règles de propriété et de calcul d'indice deviendraient difficiles à tester sans base de données, et rien ne garantit qu'un contrôleur ne contourne pas un service.

## Conséquences

- Le domaine et les cas d'usage se testent en pur Java, sans contexte Spring ni base de données. Les tests rapides sont la norme, les tests d'intégration (H2, puis PostgreSQL via Testcontainers si retenu) restent limités aux adaptateurs.
- La séparation est garantie par un test, non par le compilateur. Un développeur qui contourne ArchUnit par une annotation ou une dépendance indirecte peut introduire une fuite ; la revue de code reste nécessaire.
- Un seul artefact Maven simplifie le déploiement et la gestion des versions, au prix d'une discipline de packages plus exigeante.
- Les ports sortants sont les points de substitution pour les tests (dépôts en mémoire, horloge contrôlée, journal d'audit simulé).
