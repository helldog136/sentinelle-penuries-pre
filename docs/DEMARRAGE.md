# Démarrer Sentinelle

> **Avertissement.** Tout est fictif : l'Agence Fédérale Fictive de Santé Numérique, la Valdarie, ses régions, ses produits, ses distributeurs et ses registres. Aucune donnée patient, aucun secret, aucun nom réel. Si une ressemblance vous frappe, c'est de la paperasse qui se ressemble partout.

Ce guide couvre trois choses : construire le projet, lancer la démo, et savoir ce qui marche aujourd'hui (et ce qui n'est pas encore branché).

## Prérequis

- **Java 21** (un JDK suffit). Vérifiez avec `java -version`.
- Rien d'autre. Le wrapper Maven (`./mvnw`) télécharge Maven à votre place, ce qui est la seule tâche que Maven accepte de déléguer sans rechigner.
- Un accès réseau la première fois, pour télécharger les dépendances.

Sous Windows, utilisez `mvnw.cmd` à la place de `./mvnw`.

## 1. Construire et vérifier

```bash
./mvnw clean verify
```

Cette commande compile le projet, génère le code gRPC à partir du contrat `.proto`, lance les tests, les contrôles d'architecture (ArchUnit) et produit le rapport de couverture.

## 2. Lancer le profil démo

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Le profil `demo` :

- utilise une base **H2 en mémoire**, en mode PostgreSQL ;
- applique les migrations Flyway `V1` à `V3` (schéma), puis `V1000` (données de démonstration, dans `db/demo`) ;
- recrée les données à chaque démarrage. Les dates sont relatives à aujourd'hui, donc la démo ne vieillit pas. Les données disparaissent à l'arrêt : Ctrl+C et on repart à zéro.

Pour vérifier que les données sont bien chargées, regardez dans les logs que Flyway a appliqué `V1000` sans erreur.

## 3. Ce qu'on trouve dans la base de démo

| Élément | Quantité |
|---|---|
| Produits (codes `VMP-NNNNNN`) | 30 |
| Distributeurs (codes `PRO-NNNNNN`) | 8 |
| Régions | 6, toutes touchées |
| Ruptures | 40 : 20 `EN_COURS`, 20 `CLOTUREE` |
| Liens rupture-région | 73 |
| Alternatives proposées | 53 |

La base ne contient que des **codes**. Les noms des produits et des distributeurs (« Patientol 500 mg », « Logistique Escargot SRL », etc.) sont dans le catalogue, voir section suivante.

Le profil démo n'active pas la console H2. On ne peut donc pas, aujourd'hui, ouvrir la base pendant que l'application tourne. Ce sera à ajouter explicitement, à valider avant usage.

## 4. Parcourir le catalogue

Le catalogue fictif est dans [`docs/demo/catalogue-demo.md`](demo/catalogue-demo.md). Il donne le nom de chaque code produit et distributeur, ainsi que le libellé de chaque région.

Pour retrouver un code :

```bash
grep "VMP-000101" docs/demo/catalogue-demo.md
```

Le fichier de données est [`src/main/resources/db/demo/V1000__donnees_demo.sql`](../src/main/resources/db/demo/V1000__donnees_demo.sql). Il est commenté et peut se lire comme une fiche de travail.

## 5. Rapport de couverture JaCoCo

Après `./mvnw clean verify`, ouvrez :

```
target/site/jacoco/index.html
```

## 6. Où en est le projet (état honnête)

Ce qui existe et fonctionne aujourd'hui :

- le **domaine** (règles métier, indice de tension, acteurs), avec ses tests ;
- les **migrations** Flyway `V1` (ruptures), `V2` (journal d'audit) et `V3` (Spring Batch), appliquées sur H2 ;
- les **contrats** : `src/main/proto` pour gRPC, `docs/openapi.yaml` pour REST ;
- le **profil démo**, avec ses données.

Ce qui est développé mais **pas encore câblé** :

- l'**adaptateur gRPC** (écriture par les distributeurs) ;
- l'**adaptateur REST** (lecture par les pharmaciens et prescripteurs) ;
- la **sécurité JWT** (validation des jetons, rôles) ;
- le **job Spring Batch** de calcul nocturne de l'indice de tension (il n'est pas lancé au démarrage, `spring.batch.job.enabled` vaut `false`) ;
- le **journal d'audit** à chaînage de hachage.

Les activations sont prévues par propriétés `sentinelle.<adapter>.enabled`. L'intégration reste à venir. **Aujourd'hui, l'application n'expose donc ni endpoint REST ni service gRPC.**

Les clés JWT de test ne servent qu'à la démo et aux tests. Elles ne doivent jamais servir ailleurs. Le profil démo ne fournit aucune clé de production, et la clé de production doit être fournie par variable d'environnement (voir [ADR-002](adr/ADR-002-authentification-autorisation.md)).

## Exemple d'appel REST (à venir après l'intégration)

Cet exemple **ne fonctionne pas aujourd'hui**. Il montre le contrat cible, tel que défini dans [`docs/openapi.yaml`](openapi.yaml) :

```bash
# À venir après l'intégration de l'adaptateur REST et de la sécurité JWT
curl -H "Authorization: Bearer <jeton-de-test-demo>" \
  "http://localhost:8080/api/v1/ruptures?region=COTE_DU_TIMEOUT&page=0&size=20"
```

## Une dernière chose

Projet de démonstration, fictif, sans valeur opérationnelle. Il ne doit être ni déployé pour de vrai, ni utilisé pour prendre une décision de santé.
