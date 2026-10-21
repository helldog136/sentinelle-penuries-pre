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
| 2 | S0 clé de contexte gRPC partagée (`ActeurGrpcContext`) | Stagiaire survitaminé | haiku | rétroactif | OK : créée avant la vague pour que les lots 1 et 2 compilent contre la même classe |
| 2 | Lot 1 sécurité : JWT, rôles, propriété, intercepteur gRPC, chaîne REST | Architecte qui a tout vu | opus | TDD | OK : 165 tests verts (138 rouges au départ), cas d'attaque couverts. Non vérifié : démarrage réel en profil prod, gRPC sur le réseau |
| 2 | Lot 2 adaptateur gRPC d'ingestion | Senior pragmatique | sonnet | TDD | OK : 159 tests verts (158 rouges au départ). Non vérifié : refus réel d'un message trop gros, couplage avec l'intercepteur JWT réel |
| 2 | Lot 3 API REST de consultation | Senior pragmatique | sonnet | TDD | OK : 34 tests verts (tous rouges au départ), aucun test modifié |
| 2 | Lot 4 job Spring Batch et fichier de notifications | Architecte qui a tout vu | opus | code d'abord, **sans tests** (consigne du client) | Livré **sans tests**, jamais exécuté à ce stade. Testé a posteriori en vague 3 (R1, 164 tests) |
| 2 | Lot 5 journal d'audit immuable | Architecte qui a tout vu | opus | code d'abord, **sans tests** (consigne du client) | Livré **sans tests**, aucune écriture ni vérification exécutée à ce stade. Testé a posteriori en vague 3 (R2, 111 tests) : **3 bugs trouvés**, dont une fourche de chaîne et un déni de service |
| 2 | Lot 6 persistance JPA | Senior pragmatique | sonnet | rétroactif | OK : 20 tests sur H2, dont CHECK, FK et cascades du schéma exercés par de vrais INSERT. PostgreSQL réel non vérifié |
| 2 | Lot 7 données de démo et guide de démarrage | Stagiaire survitaminé | haiku | rétroactif | OK : 30 produits, 8 distributeurs, 40 ruptures ; Flyway applique V1 à V1000 et l'application démarre en profil demo |
| 2′ | C1 la commande de mise à jour porte la fin estimée (pas la `Periode`) | Senior pragmatique | sonnet | rétroactif | OK : bloquant signalé par le lot 2, arbitré par le tech lead |
| 2′ | C2 contrats : retrait de `creeLe`/`modifieLe`, exemples `PRO-NNNNNN`, règles du commentaire | Stagiaire survitaminé | haiku | rétroactif | OK |
| 2′ | C3 cohérence ADR-002, exemple 401 générique, ordre des alternatives, plafond de 730 jours | Stagiaire survitaminé | haiku | rétroactif | OK |
| 3 | P5 `pom.xml` : exclusion JaCoCo du code généré, PIT à la demande | Senior pragmatique | sonnet | rétroactif | OK pour JaCoCo (couverture globale 34,5 % brute, 66,6 % hors code généré). **PIT faux-succès** : la portée par défaut ne mesurait rien (validée avec un seul motif), corrigé par P6 |
| 3 | S1 six services applicatifs | Senior pragmatique | sonnet | TDD | OK : 48 tests rouges d'abord, puis verts, plus 1 test ajouté pour l'ordre des contrôles à la clôture |
| 3 | R1 tests rétroactifs du job batch (relecteur indépendant) | Architecte qui a tout vu | opus | rétroactif | OK : 164 tests, aucun bug déclaré, 10 ambiguïtés verrouillées dont 3 jugées défectueuses |
| 3 | R2 tests rétroactifs du journal d'audit (relecteur indépendant) | Architecte qui a tout vu | opus | rétroactif | OK : 111 tests, **3 bugs** (dont 2 de gravité élevée) |
| 3 | B1+B2 fourche de la chaîne d'audit et déni de service après un `REQUIRES_NEW` | Architecte qui a tout vu | opus | correctif | OK. Choix du modèle : problème de synchronisation transactionnelle, faux correctif facile. Cause racine confirmée |
| 3 | B3 jeton `Bearer` non masqué, assainissement non idempotent | Senior pragmatique | sonnet | correctif | OK. Choix : problème étroit, spécification claire, tests prêts. Fermeture aussi du contournement par U+00A0 |
| 3 | B4 durcissement du batch (`run.id`, répertoire obligatoire et privé, ERROR) | Senior pragmatique | sonnet | correctif | OK. Choix : trois corrections locales aux tests prêts. 6 tests retournés (listés dans le commit) |
| 3 | W1 câblage Spring : horloge, six décorateurs transactionnels | Senior pragmatique | sonnet | rétroactif | OK. `@ConditionalOnMissingBean` remplacé par un post-processeur (dépendance à l'ordre de traitement) |
| 3 | E1 test de bout en bout gRPC → REST → batch (relecteur indépendant) | Architecte qui a tout vu | opus | rétroactif | OK : 40 tests, 3 rouges pour 1 bug d'intégration |
| 3 | B5 erreurs REST 500 au lieu de 404/405/406 | Senior pragmatique | sonnet | correctif | OK. Choix : sémantique Spring MVC connue, un fichier |
| 3 | B6 conflit de verrou optimiste : `ABORTED` au lieu de `INTERNAL` | Senior pragmatique | sonnet | correctif | OK. Un test retourné, un fichier de tests ajouté |
| 3 | D1, D2 documentation et hygiène du dépôt | Stagiaire survitaminé | haiku | rétroactif | OK. D1 avait lu un rapport Surefire périmé : chiffre de tests corrigé par le tech lead |
| 3 | P6 portée PIT corrigée, échec si aucun mutant | Senior pragmatique | sonnet | correctif | OK, prouvé par exécution. **Erreur du tech lead** : la validation de P5 n'avait pas couvert la portée par défaut |
| 3 | F1 test d'interblocage de l'audit rendu déterministe | Architecte qui a tout vu | opus | correctif | OK. Choix : concurrence, un délai arbitraire n'aurait rien réparé. 81 échecs sur 300 avant, 300 sur 300 après |

### Résultats de la vague 3

- **Suite** : 942 tests, 0 échec, 1 ignoré (exige des droits non-root).
- **Couverture** (hors code généré, lignes / branches) : global 66,6 % / 65,3 % → **97,7 % / 92,9 %** ; batch 0 % → 97,2 % ; audit 13,0 % → 97,3 % ; application 44,6 % → 97,7 %.
- **PIT, batch et audit** : 120 mutants, score **95,8 %** (110 tués, 5 timeouts comptés comme tués par PIT, 3 survivants, 2 sans couverture). 91,7 % en ne comptant que les kills francs.
- **Mutants non tués à regarder** : la suppression du `bindResource` de `LiberationVerrou.resume` survit (le correctif B1/B2 n'est pas couvert par un test de reprise), la suppression de `unlock` en chemin d'erreur n'est pas couverte, et `planificationCalculTension` renvoyant `null` survit.

Incident d'exécution de la vague 2 : les worktrees d'agents ont été créés depuis le commit initial de `main` et non depuis la branche de travail. Le tech lead les a avancés ou recréés à la bonne base, puis a recopié chaque lot dans l'arbre principal pour le vérifier avant commit.

La vague 1′ est un prérequis ajouté par le tech lead : l'énoncé de la vague 2 supposait le domaine implémenté et le schéma présent, ce qui n'était pas le cas.

## Décisions à retenir pour la suite

- **Audit** : écrit dans la même transaction que l'opération métier (proposé par T0.4, non demandé). Câblé ainsi en vague 3 et prouvé de bout en bout (un audit en échec annule la sauvegarde). Un audit en `REQUIRES_NEW` depuis une transaction déjà auditée est refusé. À confirmer par le client.
- **Alternatives** : le use case (T1.1) trie par code du produit alternatif, puis par `ruptureId` selon la forme textuelle canonique de l'UUID (`UUID.compareTo` donne un autre ordre).
- **Clôture** : une date de fin future est refusée (`ValidationMetierException`), **après** le contrôle d'accès du domaine : un non-déclarant reçoit `AccesRefuse`, pas une erreur de validation.
- **Audit, contrainte UNIQUE** : sans `UNIQUE(hash_precedent)` en base, l'audit ne garantit l'absence de fourche qu'avec une seule instance. Migration V4 recommandée par le lot 5, **non faite**, à trancher.
- **Audit et transaction métier** : l'écriture rejoint la transaction appelante et tient un verrou jusqu'à son terme ; les services devront auditer en dernier. À confirmer.
- **Mise à jour** : la commande ne porte que la fin estimée ; le plafond de 730 jours ne vaut qu'à la déclaration, la mise à jour vérifie seulement fin ≥ début (domaine).
- **Alternatives** : l'ordre de saisie n'est pas conservé (relues triées par code produit) ; pas de colonne de position dans le schéma V1.
- **Verrou optimiste** : il ne joue que sur des transactions qui se chevauchent, l'agrégat n'exposant pas de version (un « lu hier, écrit aujourd'hui » entre deux requêtes n'est pas détecté). Le conflit est traduit en `ABORTED` côté gRPC (message fixe, journalisé en WARN).
- **Limites connues, non corrigées** : la limite de 64 Kio des messages gRPC n'est pas vérifiée (le transport in-process ne l'applique pas) ; PostgreSQL réel, plusieurs instances et le déclenchement par cron réel n'ont jamais été exercés ; `HttpMessageNotReadableException` reste en 500 (sans effet, l'API REST étant en lecture seule) ; un fichier `.tmp` orphelin du batch n'est pas nettoyé et le répertoire n'est pas synchronisé sur disque après le renommage ; rejouer une date passée utilise le statut courant des ruptures.
- **Batch** : le fuseau du cron (UTC par défaut) et celui de l'horloge peuvent décaler la date d'un jour ; la relance ne reproduit le même fichier que si les données n'ont pas changé (l'ADR-003 le promet sans réserve).
- **Batch** : le lot batch positionne `spring.batch.jdbc.initialize-schema=never` dans `config/batch.yml`, sinon l'initialiseur de Spring Boot peut entrer en conflit avec la migration V3.
- **Alternative** : un commentaire qui finit par un caractère de contrôle (`\n`, `\t`) est refusé et non nettoyé, la limite de 280 caractères s'appliquant après retrait des espaces de bord (choix de P1, aucun test ne tranche).

## Légende des surnoms

- **Stagiaire survitaminé** : sous-agent haiku. Rapide et peu coûteux, il prend les tâches mécaniques, bien bornées, et la documentation.
- **Senior pragmatique** : sous-agent sonnet. Il prend le code d'implémentation courant, avec des décisions locales à prendre.
- **Architecte qui a tout vu** : sous-agent opus. Il prend les décisions structurantes, les contrats et les tests qui fixent le comportement attendu.

## Modes

- **TDD (phase spec)** : les tests décrivant le comportement attendu sont écrits en premier et doivent échouer (tests rouges) avant toute implémentation.
- **Rétroactif** : le livrable est produit d'abord, puis vérifié et documenté à partir du résultat.
