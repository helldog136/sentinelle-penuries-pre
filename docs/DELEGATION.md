# Délégation des tâches de Sentinelle

Ce document suit la répartition du travail entre le tech lead et les sous-agents, vague par vague.

| Vague | Tâche | Surnom | Modèle | Mode (TDD / rétroactif) | Résultat |
|---|---|---|---|---|---|
| 0a | T0.1 scaffolding Maven | Stagiaire survitaminé | haiku | rétroactif | OK : `./mvnw clean verify -DskipTests` vert (Boot 3.3.13, gRPC starter 3.1.0, `javax.annotation-api` ajouté pour le code généré grpc-java) |
| 0a | T0.2 domaine + ports + tests rouges | Architecte qui a tout vu | opus | TDD (phase spec) | OK : 159 tests, 153 rouges pour la bonne raison, 6 verts (comportements hors stubs : libellés `Region`, égalité de records, `RuptureId.generer`, seuil 0, commentaire de 280 caractères) |
| 0a | T0.3 contrats proto/OpenAPI | Architecte qui a tout vu | opus | rétroactif | OK : proto compilé, OpenAPI 3.0.3 valide. Dates en `AAAA-MM-JJ`, mise à jour par remplacement complet |
| 0a | T0.4 ADR + docs | Stagiaire survitaminé | haiku | rétroactif | OK : 3 ADR, README, DELEGATION |
| 0a-bis | F1 aligner le statut `ACTIVE` sur `EN_COURS` (proto, OpenAPI, ADR-003) | Stagiaire survitaminé | haiku | rétroactif | OK : 11 remplacements, build vert. Écart détecté par le tech lead à la vérification croisée |
| 0a-bis | F2 alternatives par `CodeProduit` (port entrant et dépôt) | Senior pragmatique | sonnet | rétroactif | OK : une entrée par couple (rupture, produit alternatif), conforme au contrat. Écart détecté par le tech lead à la vérification croisée |
| 1′ | P1 implémentation du domaine (phase TDD « vert ») | Architecte qui a tout vu | opus | TDD | OK : 159/159 verts, aucun test, port ni contrat modifié. `CodeProduit`, `IdentifiantDistributeur` et `Alternative` étaient encore des stubs, implémentés avec le reste |
| 1′ | P2 schéma Flyway V1 (ruptures), V2 (journal d'audit), V3 (Spring Batch) | Senior pragmatique | sonnet | rétroactif | OK : migrations appliquées sur H2. CHECK et cascades non exercés, PostgreSQL réel non vérifié (pas de Docker) |
| 1′ | P3 test ArchUnit de l'architecture hexagonale | Senior pragmatique | sonnet | rétroactif | OK : 12 règles vertes. Seules celles sur `domain` et `application` couvrent du code existant, les autres sont préventives |
| 1′ | P4 contrats alignés sur 280 caractères (proto, OpenAPI) | Stagiaire survitaminé | haiku | rétroactif | OK : écart contrat/domaine (500 contre 280) tranché par le tech lead en faveur du domaine |

La vague 1′ est un prérequis ajouté par le tech lead : l'énoncé de la vague 2 supposait le domaine implémenté et le schéma présent, ce qui n'était pas le cas.

## Décisions à retenir pour la suite

- **Audit** : écrit dans la même transaction que l'opération métier (proposé par T0.4, non demandé). À soumettre à la revue de la vague 2.
- **Alternatives** : le use case (T1.1) trie par code du produit alternatif, puis par `ruptureId` selon la forme textuelle canonique de l'UUID (`UUID.compareTo` donne un autre ordre).
- **Clôture** : le client fournit la date de fin ; interdire une date future est une règle du service applicatif, à trancher en T1.1.
- **Batch** : le lot batch positionne `spring.batch.jdbc.initialize-schema=never` dans `config/batch.yml`, sinon l'initialiseur de Spring Boot peut entrer en conflit avec la migration V3.
- **Alternative** : un commentaire qui finit par un caractère de contrôle (`\n`, `\t`) est refusé et non nettoyé, la limite de 280 caractères s'appliquant après retrait des espaces de bord (choix de P1, aucun test ne tranche).

## Légende des surnoms

- **Stagiaire survitaminé** : sous-agent haiku. Rapide et peu coûteux, il prend les tâches mécaniques, bien bornées, et la documentation.
- **Senior pragmatique** : sous-agent sonnet. Il prend le code d'implémentation courant, avec des décisions locales à prendre.
- **Architecte qui a tout vu** : sous-agent opus. Il prend les décisions structurantes, les contrats et les tests qui fixent le comportement attendu.

## Modes

- **TDD (phase spec)** : les tests décrivant le comportement attendu sont écrits en premier et doivent échouer (tests rouges) avant toute implémentation.
- **Rétroactif** : le livrable est produit d'abord, puis vérifié et documenté à partir du résultat.
