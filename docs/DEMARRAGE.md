# Démarrer Sentinelle

> **Avertissement.** Tout est fictif : l'Agence Fédérale Fictive de Santé Numérique, la Valdarie, ses régions, ses produits, ses distributeurs et ses registres. Aucune donnée patient, aucun secret, aucun nom réel. Si une ressemblance vous frappe, c'est de la paperasse qui se ressemble partout.

Ce guide couvre quatre choses : construire le projet, configurer la sécurité, lancer la démo, et activer les adaptateurs. Il finit par un état des lieux honnête, parce que la paperasse, ça se vérifie.

## Prérequis

- **Java 21** (un JDK suffit). Vérifiez avec `java -version`.
- Rien d'autre. Le wrapper Maven (`./mvnw`) télécharge Maven à votre place, ce qui est la seule tâche que Maven accepte de déléguer sans rechigner.
- Un accès réseau la première fois, pour télécharger les dépendances.
- `openssl`, pour générer les clés de démonstration (section 3).

Sous Windows, utilisez `mvnw.cmd` à la place de `./mvnw`.

## 1. Construire et vérifier

```bash
./mvnw clean verify
```

Cette commande compile le projet, génère le code gRPC à partir du contrat `.proto`, lance les tests, les contrôles d'architecture (ArchUnit) et produit le rapport de couverture.

Rapport de couverture JaCoCo, après la commande ci-dessus :

```
target/site/jacoco/index.html
```

## 2. Profils

| Profil | Base | Usage |
|---|---|---|
| (aucun) | PostgreSQL, via variables d'environnement | Configuration « prod-like », celle de la cible. |
| `demo` | H2 en mémoire, mode PostgreSQL | Démonstration locale, données fictives. |
| `test` | H2 en mémoire, mode PostgreSQL, clé publique de **test** | Tests. Ne jamais utiliser ailleurs. |

Sans profil, les variables suivantes sont **obligatoires, sans valeur par défaut** (`application.yml`) :

- `SENTINELLE_DB_URL`
- `SENTINELLE_DB_USER`
- `SENTINELLE_DB_PASSWORD`

Le profil `demo` remplace ces trois propriétés par ses propres valeurs H2 (`application-demo.yml`).

## 3. Sécurité : la clé publique, l'émetteur et l'audience

Sentinelle ne fait que **vérifier** des jetons RS256 (ADR-002). Hors profil `test`, trois variables d'environnement sont obligatoires, **sans aucune valeur de repli** (`config/security.yml`) :

| Variable | Propriété | Rôle |
|---|---|---|
| `SENTINELLE_JWT_PUBLIC_KEY_LOCATION` | `sentinelle.security.jwt.public-key` | Emplacement Spring (`file:` ou `classpath:`) d'un PEM de clé **publique** RSA. |
| `SENTINELLE_JWT_ISSUER` | `sentinelle.security.jwt.issuer` | Émetteur attendu (claim `iss`). |
| `SENTINELLE_JWT_AUDIENCE` | `sentinelle.security.jwt.audience` | Audience attendue (claim `aud`). |

Sans elles, le démarrage échoue. C'est voulu : mieux vaut un service qui refuse de démarrer qu'un service qui accepte n'importe quel jeton. **Le profil `demo` n'échappe pas à cette règle.**

### Générer une paire de clés de démonstration

À faire une seule fois, **hors du dépôt** :

```bash
mkdir -p ~/sentinelle-demo-cles && chmod 700 ~/sentinelle-demo-cles
cd ~/sentinelle-demo-cles
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out demo-jwt-prive.pem
openssl pkey -in demo-jwt-prive.pem -pubout -out demo-jwt-public.pem
chmod 600 demo-jwt-prive.pem
```

- Seule la clé **publique** (`demo-jwt-public.pem`, format `-----BEGIN PUBLIC KEY-----`) est donnée à Sentinelle.
- La clé **privée** (`demo-jwt-prive.pem`) sert à signer les jetons de démonstration. **Aucune clé privée ne doit être committée**, ni copiée dans le dépôt. Le `.gitignore` ne couvre pas `*.pem` (les clés de test du dépôt y sont committées, volontairement et publiquement) : la vigilance reste de votre ressort.

Puis, dans le shell qui lancera l'application :

```bash
export SENTINELLE_JWT_PUBLIC_KEY_LOCATION=file:/chemin/absolu/vers/sentinelle-demo-cles/demo-jwt-public.pem
export SENTINELLE_JWT_ISSUER=https://emetteur-de-demo.affsn.invalid
export SENTINELLE_JWT_AUDIENCE=https://sentinelle-de-demo.affsn.invalid
```

Les domaines `.invalid` sont réservés par la RFC 2606 : ils ne pointent nulle part, c'est le but.

## 4. Lancer le profil démo

Avec les variables de la section 3 exportées :

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Le profil `demo` :

- utilise une base **H2 en mémoire**, en mode PostgreSQL ;
- applique les migrations Flyway `V1` à `V3` (schéma), puis `V1000` (données de démonstration, dans `db/demo`) ;
- recrée les données à chaque démarrage. Les dates sont relatives à aujourd'hui, donc la démo ne vieillit pas. Les données disparaissent à l'arrêt : Ctrl+C et on repart à zéro.

Pour vérifier que les données sont bien chargées, regardez dans les logs que Flyway a appliqué `V1000` sans erreur.

Par défaut, **le démarrage n'expose ni REST ni gRPC, et ne lance pas le batch** : les trois adaptateurs sont désactivés (section 5).

## 5. Activer les adaptateurs

Les trois adaptateurs sont désactivés par défaut. Ils s'activent par une propriété `sentinelle.<adaptateur>.enabled`.

| Adaptateur | Propriété | Variable d'environnement | Défaut | Source |
|---|---|---|---|---|
| gRPC (écriture, distributeurs) | `sentinelle.grpc.enabled` | `SENTINELLE_GRPC_ENABLED` | `false` | `config/grpc.yml`, écrit en clair : `${SENTINELLE_GRPC_ENABLED:false}` |
| REST (consultation, pharmaciens et prescripteurs) | `sentinelle.rest.enabled` | `SENTINELLE_REST_ENABLED` | absente, donc désactivé | `@ConditionalOnProperty` ; aucun fichier yml ne la définit |
| Batch (indice de tension nocturne) | `sentinelle.batch.enabled` | `SENTINELLE_BATCH_ENABLED` | `false` | `config/batch.yml` |

Pour les deux dernières lignes, aucun fichier yml ne lit de variable d'environnement explicitement : la variable est obtenue par la **liaison relaxée** de Spring Boot. Le nom s'obtient en mettant la propriété en majuscules, en remplaçant les points par des `_`, et en supprimant les tirets des noms composés. Les variables d'environnement l'emportent sur les fichiers de configuration.

Exemple, pour activer REST :

```bash
export SENTINELLE_REST_ENABLED=true
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Le port gRPC est fixé par `SENTINELLE_GRPC_PORT` (défaut `50551`, voir `config/grpc.yml`). Le port REST est le port HTTP standard de Spring Boot (8080 par défaut), la configuration du projet ne le fixe pas.

### Le batch

Le batch exige **un répertoire de sortie absolu**, et ce répertoire n'a **aucune valeur par défaut** : sans lui, le démarrage échoue dès que le batch est activé (`config/batch.yml`, `TensionBatchProperties`).

| Variable (liaison relaxée) | Propriété | Défaut |
|---|---|---|
| `SENTINELLE_BATCH_NOTIFICATIONS_REPERTOIRE` | `sentinelle.batch.notifications.repertoire` | **aucun** : obligatoire quand le batch est activé, chemin absolu |
| `SENTINELLE_BATCH_SEUILTENSION` | `sentinelle.batch.seuil-tension` | `10.0` (une région est signalée si son indice est **strictement** supérieur) |
| `SENTINELLE_BATCH_CRON` | `sentinelle.batch.cron` | `0 0 2 * * *` (02:00 chaque jour) |
| `SENTINELLE_BATCH_FUSEAU` | `sentinelle.batch.fuseau` | `UTC` |

```bash
export SENTINELLE_BATCH_ENABLED=true
export SENTINELLE_BATCH_NOTIFICATIONS_REPERTOIRE=/var/lib/sentinelle/notifications-tension
```

Le job `calculerTensionNocturne` est **planifié**, jamais lancé au démarrage (`spring.batch.job.enabled=false`). Il écrit un fichier par date de référence : `notifications-tension-AAAA-MM-JJ.json`. Le répertoire est créé s'il n'existe pas (en `rwx------` sur un système POSIX). Ne le pointez pas vers un répertoire temporaire partagé.

Il n'existe pas, aujourd'hui, de point d'entrée externe pour rejouer une autre date. Voir ADR-003, section « Limite connue ».

## 6. Tests de mutation, à la demande

Les tests de mutation (PIT) ne tournent **pas** dans `./mvnw verify` : ils se lancent explicitement.

```bash
./mvnw org.pitest:pitest-maven:mutationCoverage
```

Par défaut, PIT cible les paquets `vd.affsn.sentinelle.batch.*` et `vd.affsn.sentinelle.adapter.out.audit.*`. Pour changer la cible, passez `-Dpit.targetClasses=...` et `-Dpit.targetTests=...`.

Rapport : `target/pit-reports`.

## 7. Appel REST

Cet exemple suppose :

- que REST est activé (`SENTINELLE_REST_ENABLED=true`) ;
- que l'instance tourne avec la clé publique de démonstration de la section 3 ;
- **que le jeton `Bearer` est signé avec la clé privée de démonstration** (`demo-jwt-prive.pem`), avec les claims `iss`, `aud`, `exp`, `sub` et `role` (`PHARMACIEN` ou `PRESCRIPTEUR`).

Les jetons fabriqués par `JwtTestFactory` (`src/test/java/vd/affsn/sentinelle/security/JwtTestFactory.java`) sont signés avec la clé de **test** : l'instance de démonstration les refusera. Ils ne servent que dans les tests.

```bash
curl -H "Authorization: Bearer <jeton-signe-avec-la-cle-demo>" \
  "http://localhost:8080/api/v1/ruptures?region=COTE_DU_TIMEOUT&page=0&size=20"
```

Le contrat complet est dans [`docs/openapi.yaml`](openapi.yaml). Un jeton absent ou invalide donne un `401` avec `WWW-Authenticate: Bearer`, sans explication : c'est voulu.

## 8. Catalogue et base de démo

La base ne contient que des **codes**. Les noms des produits et des distributeurs sont dans le catalogue fictif : [`docs/demo/catalogue-demo.md`](demo/catalogue-demo.md).

| Élément | Quantité |
|---|---|
| Produits (codes `VMP-NNNNNN`) | 30 |
| Distributeurs (codes `PRO-NNNNNN`) | 8 |
| Régions | 6, toutes touchées |
| Ruptures | 40 : 20 `EN_COURS`, 20 `CLOTUREE` |
| Liens rupture-région | 73 |
| Alternatives proposées | 53 |

Pour retrouver un code :

```bash
grep "VMP-000101" docs/demo/catalogue-demo.md
```

Le fichier de données est [`src/main/resources/db/demo/V1000__donnees_demo.sql`](../src/main/resources/db/demo/V1000__donnees_demo.sql). Il est commenté et peut se lire comme une fiche de travail.

Le profil démo n'active pas la console H2. On ne peut donc pas, aujourd'hui, ouvrir la base pendant que l'application tourne. Ce sera à ajouter explicitement, à valider avant usage.

## 9. Où en est le projet

### Ce qui est dans le code

- le **domaine** (règles métier, indice de tension, acteurs), les **ports** et les **services d'application**, câblés dans `config/ServicesConfig` ;
- les **migrations** Flyway `V1` (ruptures), `V2` (journal d'audit) et `V3` (Spring Batch), et les données de démonstration `V1000` ;
- les **contrats** : `src/main/proto/sentinelle/v1/sentinelle.proto` pour gRPC, `docs/openapi.yaml` pour REST ;
- l'**adaptateur gRPC** d'ingestion, conditionné par `sentinelle.grpc.enabled` ;
- l'**adaptateur REST** de consultation, conditionné par `sentinelle.rest.enabled`, avec sa gestion d'erreurs (RFC 7807) ;
- la **sécurité JWT** : vérification RS256, rôles `PHARMACIEN`, `PRESCRIPTEUR` et `DISTRIBUTEUR`, intercepteur gRPC et chaîne REST ;
- le **job batch** `calculerTensionNocturne`, conditionné par `sentinelle.batch.enabled` ;
- le **journal d'audit** à chaînage SHA-256, avec sa vérification d'intégrité ;
- des **tests** par couche : domaine, sécurité, gRPC, REST, persistance, batch, audit, et des scénarios de bout en bout.

### Ce qu'il faut garder en tête

- **Rien n'est activé par défaut.** Un démarrage nu n'expose ni REST, ni gRPC, ni batch.
- Ce document décrit le code, il ne garantit pas son état d'aujourd'hui. Pour savoir si tout passe, lancez `./mvnw clean verify`.
- Les tests de persistance tournent sur **H2** en mode PostgreSQL. Un **PostgreSQL réel** n'a pas encore été testé.
- L'indice de tension lit le **statut courant** des ruptures et ne rejoue pas l'historique : rejouer une date passée ne reproduit pas forcément le calcul d'origine (ADR-003, « Limite connue »).
- Il n'existe pas de point d'entrée externe pour rejouer une date de référence.

## Une dernière chose

Projet de démonstration, fictif, sans valeur opérationnelle. Il ne doit être ni déployé pour de vrai, ni utilisé pour prendre une décision de santé.
