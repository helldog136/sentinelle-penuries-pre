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

## Décisions à retenir pour la suite

- **Audit** : écrit dans la même transaction que l'opération métier (proposé par T0.4, non demandé). À soumettre à la revue de la vague 2.
- **Alternatives** : le use case (T1.1) trie par code du produit alternatif, puis par `ruptureId` selon la forme textuelle canonique de l'UUID (`UUID.compareTo` donne un autre ordre).
- **Clôture** : le client fournit la date de fin ; interdire une date future est une règle du service applicatif, à trancher en T1.1.
- **Vague 1** : l'implémentation du domaine (passage des tests rouges au vert) reste à faire avant ou avec le lot use cases.

## Légende des surnoms

- **Stagiaire survitaminé** : sous-agent haiku. Rapide et peu coûteux, il prend les tâches mécaniques, bien bornées, et la documentation.
- **Senior pragmatique** : sous-agent sonnet. Il prend le code d'implémentation courant, avec des décisions locales à prendre.
- **Architecte qui a tout vu** : sous-agent opus. Il prend les décisions structurantes, les contrats et les tests qui fixent le comportement attendu.

## Modes

- **TDD (phase spec)** : les tests décrivant le comportement attendu sont écrits en premier et doivent échouer (tests rouges) avant toute implémentation.
- **Rétroactif** : le livrable est produit d'abord, puis vérifié et documenté à partir du résultat.
