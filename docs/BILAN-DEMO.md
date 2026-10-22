# Bilan de la démo agentique Sentinelle

> **Comment lire ce document**
>
> - Chaque chiffre porte son origine : **mesuré** (source citée : `git`, rapports Surefire/JaCoCo/PIT, transcripts de session), **calculé** (formule et hypothèses données) ou **estimation** (le mot est écrit). Quand une source se contredit, les deux valeurs sont citées.
> - Les coûts en dollars et en euros sont **indicatifs**. Les tokens sont mesurés, mais les tarifs, le taux de change, la durée de vie du cache et une partie des sorties du jour 1 relèvent d'hypothèses explicites (§ 7). Ce n'est pas une facture.
> - Les métriques d'agents viennent d'une analyse par script des transcripts (`metriques-agents.md` / `.json`, produits pendant la session, hors dépôt). Les durées sont en heure UTC.
> - Tout est fictif : l'AFFSN, la Valdarie, ses régions, ses produits et ses distributeurs. Aucune donnée patient.
> - Homonymies à connaître : `B1` à `B6` désignent des **correctifs** en vague 3 et `B0` à `B5` des **lots de tests rouges** en vague B. `F1` existe en vague 0a-bis et en vague 3, `P1` en vague 1′ et hors vagues. Le texte précise la vague quand il y a ambiguïté. On a appris à nommer des régions, pas des tâches.

## 1. Synthèse

- **Construit** : un service Java 21 / Spring Boot 3.3 hexagonal mono-module. Écriture en gRPC par les distributeurs, lecture en REST par les pharmaciens et prescripteurs, JWT RS256, persistance JPA et Flyway (H2 testé, PostgreSQL visé), batch nocturne de l'indice de tension, journal d'audit chaîné par SHA-256. S'y ajoute l'évolution « produits essentiels » (ADR-004), menée jusqu'à la **phase rouge**.
- **Méthode** : un tech lead (orchestrateur sonnet) découpe le travail, puis **53 sous-agents** (14 haiku, 23 sonnet, 16 opus) le réalisent : 51 répartis en 8 vagues, plus 2 agents d'instrumentation. TDD par lot quand c'était demandé, rétroactif ailleurs, et des relecteurs indépendants sur le code livré sans tests.
- **Résultat mesuré** : 100 fichiers Java de production (5 018 lignes) et 88 de test (16 641 lignes). Au dernier `verify` : **1 175 tests**, dont **181 rouges voulus** (évolution essentiels), 1 ignoré, et les 942 tests historiques verts. Avant l'évolution : couverture hors code généré de 97,7 % des lignes et 92,9 % des branches, score de mutation de 95,8 % sur le batch et l'audit.
- **Bugs** : 7 défauts de production (décompte de la PR #1, détail au § 4.3) et 1 test instable trouvés par les tests rétroactifs et de bout en bout, tous corrigés. Deux étaient de gravité élevée (fourche de la chaîne d'audit et déni de service).
- **État réel** : le service de base est prêt pour une **revue humaine**, pas pour la production (§ 9). L'évolution essentiels n'a **que ses tests**, et son implémentation reste à faire.
- **Coût calculé** : environ **62,5 à 64,3 USD (55,6 à 57,2 EUR)** pour la session visible. Opus en représente à lui seul environ la moitié. Durée murale active d'environ **4 h 25 min** (§ 7, § 8).

## 2. Les vagues

Source : `metriques-agents.md` § 2 (mesuré). Deux notions à ne pas confondre :

- **Lancés dans le même message** : nombre d'appels d'agent émis par l'orchestrateur dans un seul message. C'est une intention de parallélisme.
- **Parallélisme max mesuré** : nombre maximal d'intervalles « lancement → notification de fin » qui se recouvrent. C'est ce qui a réellement tourné en même temps, au niveau agent. La contention réelle (API, CPU, Maven) n'est pas mesurée.

| Vague | Tâches | Modèles (haiku/sonnet/opus) | Sous-agents lancés | Lancés même message (max) | Parallélisme max mesuré | Début → fin (UTC) | Durée murale |
|---|---|---|---|---|---|---|---|
| 0a | T0.1 scaffolding, T0.2 domaine et tests rouges, T0.3 contrats, T0.4 ADR | 2/0/2 | 4 | 4 | 4 | 21/10 11:52 → 12:01 | 9 min 45 s |
| 0a-bis | F1 `ACTIVE`→`EN_COURS`, F2 alternatives par code produit | 1/1/0 | 2 | 2 | 2 | 12:02 → 12:04 | 1 min 47 s |
| 1′ | P1 domaine vert, P2 Flyway V1-V3, P3 ArchUnit, P4 contrats 280 | 1/2/1 | 4 | 4 | 4 | 12:07 → 12:11 | 3 min 12 s |
| 2 | S0 clé gRPC, lots 1 à 7, relance du lot 6 | 2/4/3 | 9 | 7 | **7** | 12:15 → 13:07 | 52 min 24 s |
| 2′ | C1 commande de mise à jour, C2 et C3 contrats et docs | 2/1/0 | 3 | 2 | 2 | 12:33 → 12:41 | 7 min 59 s |
| 3 | R1, R2, S1, P5, B1-B6, W1, E1, D1, P6, D2, F1 | 2/8/5 | 15 | 3 | 4 | 13:10 → 14:50 | 99 min 40 s |
| A | A0 ADR-004, A1 contrats, A2 squelettes, A3 V4, A4 propriété | 1/2/2 | 5 | 5 | 5 | 22/10 09:12 → 09:18 | 6 min 11 s |
| B | B0-B5 tests rouges, 2 relances, correctif de collision | 3/3/3 | 9 | 6 | 6 | 09:19 → 09:41 | 22 min 12 s |
| hors vagues | M1 compteurs de tokens, P1 tarifs (instrumentation de ce bilan) | 0/2/0 | 2 | 2 | 2 | 09:55 → 10:01 | 5 min 55 s |

Remarques :

- **Le pic de 7 a duré quelques secondes.** Les lots de la vague 2 sont lancés à 12-15 s d'écart, et le lot 6 s'arrête après 34 s.
- **Les vagues 2 et 2′ se chevauchent.** Les tâches C1 à C3 traitent des signalements des lots en cours.
- **La vague 3 est la plus longue (1 h 40) et la moins parallèle.** Sa nature l'impose : on teste, on trouve un bug, on corrige, on reteste. Jamais plus de 3 agents lancés ensemble, 4 en recouvrement.
- **Durée cumulée des agents** : 23 089 s, soit environ 6 h 25 min (somme des `duration_ms` des notifications). Elle inclut la création des worktrees et les attentes de permission.

## 3. Répartition par modèle

Source : `metriques-agents.md` § 1 et § 3 (mesuré). Le modèle réellement servi (`message.model`) est celui demandé pour les 53 agents.

| Surnom | Modèle | Agents | Tâches typiques de la session |
|---|---|---|---|
| Stagiaire survitaminé | `claude-haiku-5-5` | 14 | Scaffolding Maven, ADR initiaux, alignements de contrats (`EN_COURS`, 280, exemples), clé de contexte partagée, données de démo, documentation, hygiène, propriété de config, correctif d'une ligne |
| Senior pragmatique | `claude-sonnet-5-5` | 23 (dont 2 hors vagues) | Flyway, ArchUnit, gRPC, REST, JPA, services, câblage Spring, correctifs étroits (assainissement, batch, REST, ABORTED, PIT), squelettes et tests rouges des adaptateurs et du batch, instrumentation |
| Architecte qui a tout vu | `claude-opus-5-5` | 16 | Domaine et sa spécification, contrats, sécurité JWT, batch et audit livrés sans tests, relectures indépendantes (R1, R2, E1), correctif transactionnel, test de concurrence, ADR-004, tests rouges de la règle, des services et de bout en bout |

### Critères de dimensionnement appliqués

Trois questions, dans cet ordre :

1. **Coût d'une erreur non détectée.** Une erreur dans l'audit, la sécurité ou une spécification de tests se propage à tout ce qui s'appuie dessus. Ces tâches vont à opus.
2. **Ambiguïté de la consigne.** Plus il reste de décisions à prendre (contrat à inventer, cause racine inconnue), plus le modèle monte. Une spécification fermée avec des tests prêts descend à sonnet.
3. **Caractère mécanique et vérifiable.** Remplacements, docs, scaffolding : le résultat se vérifie en une commande. Ces tâches vont à haiku.

### Bien dimensionné

- **B1+B2 de la vague 3 (fourche d'audit) sur opus.** C'est un problème de synchronisation transactionnelle Spring où un faux correctif était facile. La cause racine a été trouvée et documentée (`404b756`).
- **R2 et E1 en relecteurs opus indépendants.** R2 a trouvé deux bugs de gravité élevée dans un code écrit par un autre opus. E1 a trouvé un défaut d'intégration qui avait échappé à un lot TDD.
- **F1 de la vague 3 (test d'interblocage) sur opus.** Un `sleep` n'aurait rien réparé, et l'agent a imposé l'ordre par des latches (`27ccad4`).
- **B3 de la vague 3 (assainissement) sur sonnet.** La spécification était étroite et les tests rouges prêts. L'agent a aussi fermé le contournement par U+00A0 (`8d31460`).
- **Les alignements de contrats sur haiku** (F1 0a-bis, P4, C2, C3) : mécaniques, vérifiés par le build.

### Discutable, en toute honnêteté

- **D1 (haiku) a lu un rapport Surefire périmé** et écrit « environ 600 tests ». Le tech lead a corrigé en 942. Une documentation qui cite des chiffres a besoin d'une source fraîche, quel que soit le modèle.
- **Le lot 7 (haiku, données de démo)** a consommé 8,6 M tokens de cache en lecture. 51 de ses appels dépassaient 100 K tokens de contexte et tombaient donc dans le palier tarifaire haut de haiku, cinq fois plus cher (calculé, § 7). Un stagiaire bon marché qui garde tout le dépôt en tête ne l'est plus tant que ça.
- **Le lot 2 (sonnet, gRPC)** a été le sous-agent sonnet le plus long : 2 020 s, repris deux fois, 17,8 M tokens de cache en lecture. Le résultat est bon, mais un découpage plus fin aurait été moins cher.
- **B1 de la vague B (opus) a enfreint une consigne explicite.** Il a écrit une implémentation temporaire dans `src/main` pour valider ses assertions, puis l'a restaurée. Le modèle le plus capable n'est pas le plus obéissant (voir § 6).
- **B4 de la vague B (sonnet), sur une base périmée**, s'est mis à recréer des squelettes hors de son périmètre. C'est un défaut d'outillage plus que de modèle.
- **Le correctif de collision de stubs (haiku)** a duré 640 s pour retirer une annotation. La correction était triviale, la vérification ne l'était pas : la suite complète tourne en plusieurs minutes.
- **P5 (sonnet) puis le tech lead ont validé PIT avec un seul motif.** Personne n'a vu le faux succès avant P6. Ce n'est pas une question de modèle : la vérification était mal conçue.

## 4. Tests

### 4.1 TDD strict ou rétroactif

Dans ce document, **TDD** veut dire qu'un commit de squelettes (stubs) précède un commit `[red]` dont les tests échouent pour la bonne raison, puis un commit vert qui ne modifie aucun test. Les trois commits sont vérifiables dans `git log`.

| Lot | Squelettes | Rouge (commit, tests) | Vert | Remarque |
|---|---|---|---|---|
| Domaine | `6ac844b` | `a01c74d` : 159 tests, 153 rouges, 6 verts hors stubs | `f7bd6e5` (P1, autre agent opus) | Seul lot où la spécification et l'implémentation sont écrites par deux agents distincts |
| Sécurité JWT/REST/gRPC | `8695b5b` | `a3122d0` : 165 tests, 138 rouges, 27 verts (cas négatifs qu'un stub satisfait) | `e7cadfd` | Rouge non total, assumé dans le commit |
| Adaptateur gRPC | `cf51593` | `2725e38` : 159 tests, 158 rouges | `5754a94` | |
| API REST | `659f171` | `363f159` : 34 tests, 34 rouges | `fbe412c` | E1 y trouvera pourtant le bug 404/405/406 : les tests ne couvraient que la spécification écrite |
| Services applicatifs | `e986794` | `9ab6db2` : 48 tests, 48 rouges | `a9ad769` (+1 test) | |
| **Essentiels, domaine et application** | `452018b` | `3370668` (B1) : **52** rouges ; `13c050c` (B2) : **42** rouges | **pas encore** | |
| **Essentiels, adaptateurs** | `9c5582b` | `39aed38` (B3) : **32** rouges | **pas encore** | |
| **Essentiels, batch** | `c9a57d0` | `f1cde3a` (B4) : **27** rouges | **pas encore** | `c9a57d0` corrige aussi la collision de stubs |
| **Essentiels, bout en bout et ArchUnit** | (squelettes ci-dessus) | `813f744` (B5) : **28** rouges (mesuré) | **pas encore** | Le tech lead avait annoncé 25, B5 en avait annoncé 30 dans son worktree |

**Phase rouge essentiels, mesurée** (`target/surefire-reports`, 73 fichiers, exécution de 09:36:59Z) : 1 175 tests, 118 échecs et 63 erreurs, soit **181 rouges**, plus 1 ignoré. Les 181 se répartissent ainsi : 52 + 42 + 32 + 27 + 28. Ils sont tous dans les 16 classes nouvelles, et les 942 tests historiques passent. La somme des comptes annoncés dans les messages de commit et de l'orchestrateur donne 178 : l'écart vient de `813f744` (25 annoncés, 28 mesurés).

**Rétroactif** (code d'abord, tests ensuite) :

| Composant | Code | Tests | Commit des tests |
|---|---|---|---|
| Batch (lot 4, livré **sans tests** sur consigne du client) | `0028115` | R1 : 164 tests, par un relecteur indépendant | `94239a6` |
| Audit (lot 5, livré **sans tests** sur consigne du client) | `ad0a87b` | R2 : 111 tests, 6 rouges pour 2 causes | `bb534eb` |
| Bout en bout gRPC → REST → batch → audit | tout | E1 : 40 tests, 3 rouges pour 1 bug | `924aaf9` |
| Persistance JPA (lot 6) | `3cd0c55` | 20 tests H2 (CHECK, FK, cascades), même commit | `3cd0c55` |
| Câblage Spring (W1) | `11467df` | 9 tests, même commit | `11467df` |
| Architecture (P3) | | 12 règles ArchUnit | `6b29a07` |

### 4.2 Couverture (JaCoCo, hors code protobuf généré)

Source : `docs/DELEGATION.md`. Détail par package recalculé sur les rapports JaCoCo avant R1/R2 (13:09Z) et après la vague 3 (14:20Z). Ces mesures datent d'avant l'évolution essentiels : la couverture avec les 181 tests rouges n'a pas de sens et n'est pas donnée.

| Périmètre | Lignes avant | Lignes après | Branches avant | Branches après |
|---|---|---|---|---|
| **Global** | **66,6 %** | **97,7 %** (1 348 / 1 380) | **65,3 %** | **92,9 %** (455 / 490) |
| batch | 0,0 % | 97,2 % | 0,0 % | 92,9 % |
| adapter.out.audit | 13,0 % | 97,3 % | 10,7 % | 95,3 % |
| application | 44,6 % | 97,7 % | 37,5 % | 66,7 % |
| adapter.out.persistence | 94,3 % | 96,7 % | 56,2 % | 87,5 % |
| adapter.in.grpc | 98,9 % | 98,3 % | 97,3 % | 98,6 % |
| adapter.in.rest | 100 % | 100 % | 100 % | 96,7 % |
| security | 95,9 % | 95,9 % | 82,9 % | 84,2 % |
| domain | 98,4 % | 99,0 % | 97,3 % | 97,3 % |
| config | (absent) | 100 % | (absent) | 87,5 % |

La couverture brute, code généré compris, était de 34,5 % : 3 342 lignes protobuf sur 4 550 (`03f9f5c`). Un indicateur qui compte le code généré mesure surtout `protoc`.

### 4.3 Bugs trouvés par les tests rétroactifs et de bout en bout

| # | Trouvé par | Défaut | Correctif |
|---|---|---|---|
| 1 | R2 (`bb534eb`) | Un audit en `REQUIRES_NEW` après un audit englobant **fourche la chaîne** avec une seule instance : la ressource liée sous une clé ad hoc n'est pas suspendue par Spring | `404b756` (opus) |
| 2 | R2, même cause | La séquence annulée devient la « dernière committée » et **l'instance refuse toute écriture d'audit jusqu'au redémarrage** | `404b756` |
| 3 | R2 | `Bearer` séparé par U+2028, U+200B ou U+202E **stocké en clair**, assainissement non idempotent | `8d31460` (sonnet) |
| 4 | R2 (ambiguïté documentée, puis jugée défaut) | Contournement du masquage par **U+00A0** (espace insécable) | `8d31460`, test retourné |
| 5 | R1 (`94239a6`, ambiguïté jugée défaut) | `run.id` textuel qui **empoisonne tous les lancements suivants** (`RunIdIncrementer`) | `37dbc65` (sonnet) |
| 6 | R1 (idem) | Répertoire de sortie par défaut **prévisible sous le répertoire temporaire** (inscriptible par tous, liens symboliques suivis) | `37dbc65` |
| 7 | R1 (idem) | Job en échec journalisé en INFO | `37dbc65` |
| 8 | E1 (`924aaf9`) | **500 au lieu de 404/405/406**, avec pile en ERROR, dans un lot pourtant TDD | `cfabf57` (sonnet) |
| 9 | E1 (ambiguïté) | Conflit de verrou optimiste rendu en `INTERNAL` | `6d92c1e` (sonnet), `ABORTED` |
| 10 | PIT puis mesure | **Test** d'interblocage instable (pas un bug de production) | `27ccad4` (opus) |

Le décompte dépend de l'unité choisie. R2 compte « 3 bugs » (DELEGATION.md), son commit « 2 bugs » : deux causes, trois effets. R1 déclare « aucun bug », mais trois de ses dix ambiguïtés ont été jugées défectueuses par le tech lead. Le TDD n'a pas empêché le bug n° 8 : les tests REST couvraient ce que la spécification disait, et la spécification ne parlait pas des URL inconnues.

### 4.4 Mutation (PIT 1.17.4, batch et audit)

Mesuré dans `target/pit-reports/mutations.xml` (14:55Z) et le journal d'exécution (3 min 48 s, 351 tests lancés, 2,92 tests par mutant) :

| Mutants | Tués | Timeouts (comptés tués par PIT) | Survivants | Sans couverture | Score PIT | Kills francs seuls | Force des tests |
|---|---|---|---|---|---|---|---|
| 120 | 110 | 5 | 3 | 2 | **95,8 %** (115/120) | 91,7 % (110/120) | 97 % (115/118) |

Mutants non tués :

| Statut | Classe, méthode, ligne | Mutation | Lecture |
|---|---|---|---|
| Survivant | `JournalAuditJdbcAdapter$LiberationVerrou.resume`, l. 233 | appel `bindResource` supprimé | **Le correctif de la fourche (`404b756`) n'a pas de test de reprise.** C'est le plus important des cinq. |
| Survivant | `CanonisationAudit.assainirResume`, l. 105 | borne conditionnelle modifiée | Cas limite d'assainissement non discriminé |
| Survivant | `TensionBatchConfiguration.planificationCalculTension`, l. 85 | retour `null` | La planification n'est vérifiée que par sa présence |
| Sans couverture | `JournalAuditJdbcAdapter.verrouillerJusquaFinDeTransaction`, l. 192 | `unlock` supprimé (chemin d'erreur) | Chemin d'erreur jamais exercé |
| Sans couverture | `FichierNotificationRegionAdapter.creerRepertoire`, l. 93 | retour `null` | Idem |

**Ce que PIT a révélé sur lui-même.**

1. **Faux succès** : la première configuration (P5, `03f9f5c`) passait les motifs dans un `<param>` imbriqué. Maven ne découpait pas la chaîne, si bien que PIT recevait un motif unique, annonçait `No mutations found` et finissait **en succès en 11 s**. La validation de P5 et celle du tech lead n'avaient utilisé qu'un motif simple. P6 (`1c4b186`) a corrigé la portée et ajouté `failWhenNoMutations=true`.
2. **Suite non verte** : la première vraie exécution a échoué parce qu'un test ne passait pas sans mutation (journal PIT de 14:27Z : `did not pass without mutation`). PIT a ainsi servi de détecteur d'instabilité.

### 4.5 Instabilité corrigée

`interblocageVerrouDAuditEtVerrouDeLigneResoluParLeDelai` n'imposait pas l'ordre des acquisitions. Mesuré sous charge CPU : **81 échecs sur 300**. Correctif (`27ccad4`) :

- ordre imposé par deux `CountDownLatch`, sans `sleep` ;
- attente de l'état « bloqué » constaté par la base (`INFORMATION_SCHEMA.SESSIONS`) ;
- délais bornés.

Après correction : 300 exécutions sur 300, 30 invocations Maven sur 30, et PIT passe. Le code de production n'était pas en cause.

## 5. Sécurité

### 5.1 Échelle de gravité utilisée

| Gravité | Définition |
|---|---|
| **Critique** | Usurpation d'identité, contournement d'autorisation ou altération **indétectable** de l'audit, exploitable à distance par un acteur authentifié ou non |
| **Élevée** | Perte d'une garantie de sécurité annoncée (intégrité ou disponibilité de l'audit), déclenchable par un chemin de code normal |
| **Moyenne** | Fuite limitée, déni de service local ou conditionnel, durcissement manquant sur une surface exposée |
| **Faible** | Défense en profondeur, bruit d'alerte, sémantique d'erreur incorrecte sans fuite |

Ces gravités sont les miennes. Là où elles diffèrent de celles des rapports d'agents, je le signale. Aucun constat n'est critique. Ce n'est pas une certification : aucune revue humaine ni aucun test d'intrusion n'a eu lieu.

### 5.2 Constats

| # | Constat | Source | Gravité | Statut |
|---|---|---|---|---|
| S1 | Fourche de la chaîne d'audit après `REQUIRES_NEW` (instance unique) | R2 | Élevée | **Corrigé** `404b756`. Résidu : aucun test de reprise (mutant `bindResource` survivant), **ouvert** |
| S2 | Déni de service de l'audit jusqu'au redémarrage, même cause | R2 | Élevée | **Corrigé** `404b756` |
| S3 | Jeton `Bearer` non masqué si séparé par un caractère de format ou un séparateur Unicode ; non-idempotence | R2 (« faible à moyenne ») | Moyenne | **Corrigé** `8d31460` |
| S4 | Contournement du masquage par U+00A0 | R2 (ambiguïté) | Moyenne | **Corrigé** `8d31460` |
| S5 | `run.id` textuel qui empoisonne les lancements du batch | R1 | Moyenne | **Corrigé** `37dbc65` |
| S6 | Répertoire de sortie par défaut sous le répertoire temporaire (inscriptible par tous, liens symboliques suivis) | R1 | Moyenne | **Corrigé** `37dbc65` : obligatoire, absolu, `rwx------` |
| S7 | 500 et pile en ERROR sur URL, méthode ou `Accept` erronés (bruit d'alerte déclenchable par tout client authentifié) | E1 | Moyenne | **Corrigé** `cfabf57` |
| S8 | `HttpMessageNotReadableException` reste en 500 | `cfabf57` | Faible | **Accepté** (API REST en lecture seule) |
| S9 | Conflit optimiste en `INTERNAL` ; `ABORTED` appliqué ensuite à tout RPC passant par le mapping | E1, `6d92c1e` | Faible | **Corrigé**, extension à tout RPC **acceptée** |
| S10 | Clés privées non ignorées hors `test-keys` | tech lead | Faible | **Corrigé** `6903e85` |
| S11 | Exemple OpenAPI 401 qui révélait la raison du refus | C3 | Faible | **Corrigé** `d45d46a`, `29a5b24` |
| S12 | Pas de `UNIQUE(hash_precedent)` : **fourche possible avec plusieurs instances** (détectée, pas empêchée) | lot 5, R2 | Élevée (multi-instance), sans objet (instance unique) | **Ouvert**, migration à décider (le numéro V4 est désormais pris) |
| S13 | Troncature de la queue du journal indétectable sans ancrage externe du dernier hash ; ligne forgée en queue acceptée par qui a accès à la base | R2 | Moyenne | **Ouvert** (documenté) |
| S14 | `UPDATE`/`DELETE` directs acceptés par la base sur le journal (détectés par le vérificateur) | R2 | Moyenne | **Ouvert** |
| S15 | Sous H2, une transaction en lecture seule écrit quand même l'audit ; sous PostgreSQL l'INSERT échouerait | R2 | Moyenne (non vérifié) | **Ouvert** |
| S16 | Limite de 64 Kio des messages gRPC jamais vérifiée de bout en bout (le transport in-process ne l'applique pas : 100 Ko acceptés en test) | E1 | Moyenne | **Ouvert** |
| S17 | Message d'erreur réel du pilote JDBC non vérifié (pourrait contenir des valeurs) ; délai de verrou inférieur à 1 ms ramené à 0 ; même événement soumis deux fois = deux entrées | R2 | Faible | **Ouvert** |
| S18 | Référentiel essentiel : la lecture seule ne repose que sur l'absence de code tant que le compte applicatif n'est pas restreint à `SELECT` | ADR-004 (question 1) | Moyenne | **Ouvert** (exploitation) |
| S19 | Une fin estimée optimiste échappe légalement à l'obligation d'alternative | ADR-004 (question 2) | Sans objet technique (réglementaire) | **Ouvert** |

### 5.3 Contrôles en place (vérifiés par des tests, sur H2 et en in-process)

- **JWT** : RS256 épinglé (`alg:none` et confusion HS256 refusés). `exp`, `iss` et `aud` vérifiés, tolérance d'horloge bornée à 60 s, jeton borné à 8 KiB. Le rôle et le rattachement au distributeur doivent être cohérents (`e7cadfd`).
- **Identité issue du jeton seul** : ni la metadata, ni des champs inconnus, ni un jeton greffé ne changent l'acteur (tests d'usurpation, E1).
- **Propriété des ruptures** contrôlée dans le domaine : un non-déclarant reçoit `PERMISSION_DENIED` / `AccesRefuse`, avant toute validation métier qui révélerait l'état de la rupture.
- **Erreurs génériques** : 401 et 403 en problem+json identiques quelle que soit la cause, pas d'écho des valeurs saisies, `INTERNAL` sans fuite.
- **Secrets** : hors profil test, la clé publique, l'émetteur et l'audience viennent de l'environnement, sans valeur de repli, et un placeholder non résolu fait échouer le démarrage. La seule clé privée du dépôt est une paire RSA **`TEST-ONLY`** sous `src/test/resources/test-keys`, et `.gitignore` bloque `*.pem` et `*.key` partout ailleurs.
- **Audit** : append-only côté code (aucun UPDATE/DELETE, vérifié par réflexion et par recherche dans `src/main`), chaînage SHA-256 au format canonique v1, vérificateur d'intégrité (altération, suppression, échange, fourche). Le résumé est assaini.
- **Surface réduite** : adaptateurs gRPC, REST et batch désactivés par défaut. Pas d'API d'écriture du référentiel essentiel.
- **Données** : aucune donnée patient, tout est fictif.

## 6. Ce qui a échoué en route

Format : symptôme → cause → correction → leçon.

1. **Worktrees partis de la mauvaise base, trois fois.**
   - Symptôme : en vague 2, les 7 worktrees d'agents étaient sur `cd6a0c7` (commit initial) au lieu de `108a9e8`. En vague B, B0 et B3 n'ont trouvé ni l'ADR-004 ni les squelettes, sur `98592c4`.
   - Cause : l'outil d'isolation crée le worktree depuis `main`, pas depuis la branche de travail.
   - Correction : le tech lead a avancé ou recréé les worktrees, puis créé lui-même ceux de B0 et B3. Il a ensuite recopié chaque lot dans l'arbre principal pour le vérifier avant commit.
   - Leçon : vérifier `git rev-parse HEAD` de chaque worktree avant de lancer, ou créer les worktrees soi-même.
2. **Agents bloqués puis relancés : lot 6, B0, B3.**
   - Symptôme : arrêt après 34 s, 42 s ou 50 s, rapport « non réalisé ».
   - Cause : mauvaise base. Pour le lot 6, le classifieur de permissions a de plus refusé un `git merge`.
   - Correction : relance dans un worktree préparé. Les tokens des tentatives ratées sont comptés au § 7.
   - Leçon : un agent qui s'arrête proprement au lieu de contourner un refus, c'est le comportement voulu. Le lot 6 ne l'a pas contourné.
3. **Incohérences entre contrat et domaine** : `ACTIVE` contre `EN_COURS` (F1, 11 remplacements), 500 contre 280 caractères (P4), alternatives par `CodeProduit` (F2), `creeLe`/`modifieLe` absents du domaine (C2), commande de mise à jour impossible à construire faute de date de début (C1, `309edea`).
   - Cause : T0.2 (domaine) et T0.3 (contrats) écrits en parallèle par deux agents, sans source de vérité commune.
   - Correction : vérification croisée par le tech lead, puis micro-tâches haiku ou sonnet.
   - Leçon : le parallélisme coûte une passe de réconciliation. Il faut la prévoir dès le départ.
4. **Prérequis manquant.**
   - Symptôme : l'énoncé de la vague 2 supposait le domaine implémenté et le schéma présent, ce qui n'était pas le cas.
   - Correction : ajout de la vague 1′.
   - Leçon : relire les hypothèses d'une vague contre l'état réel du dépôt.
5. **PIT « No mutations found » en succès** (§ 4.4).
   - Cause : `<param>` imbriqué et `failWhenNoMutations=false`.
   - Correction : P6.
   - Leçon : un outil de mesure doit échouer quand il ne mesure rien, et sa validation doit couvrir la configuration par défaut.
6. **Test d'interblocage instable** (§ 4.5).
   - Leçon : en concurrence, ce qui n'est pas imposé finira par arriver. Les latches valent mieux que l'optimisme.
7. **README avec un compte de tests périmé.**
   - Cause : D1 a lu un rapport Surefire ancien.
   - Correction : 942, par le tech lead (`ab845bb`).
   - Leçon : un chiffre dans une doc doit venir d'une exécution datée. Remarque : le README est **de nouveau** périmé (942 tests et migrations V1 à V3, contre 1 175 et V4 aujourd'hui). Ce n'est pas corrigé ici, ce document ne touchant que deux fichiers.
8. **Maven concurrents et `pkill`.**
   - Symptôme : des builds simultanés dans le même `target/` se sont marché dessus. Un `pkill -f` a tué le shell de l'orchestrateur (code 144), son motif figurant dans sa propre ligne de commande. Une notification « exit code 0 » venait du sous-shell, pas du vrai statut de PIT.
   - Correction : un seul Maven à la fois dans un `target/` donné, interdiction de `pkill -f` sur un motif présent dans la ligne de commande (consigne ajoutée aux prompts suivants).
   - Leçon : le parallélisme des agents ne s'étend pas au répertoire de build.
9. **Collision de stubs entre B3 et B4.**
   - Symptôme : 15 tests historiques rouges après assemblage, alors que chaque lot était vert seul.
   - Cause : le batch de B4 détectait le stub `@Repository` de B3 et empruntait son chemin non implémenté.
   - Correction : l'adaptateur n'est plus un bean tant qu'il n'est pas implémenté (`c9a57d0`, correctif haiku). Le `@Repository` est à réactiver à l'implémentation.
   - Leçon : des stubs sur des fichiers disjoints ne sont pas forcément disjoints au runtime, puisque Spring les assemble. Il faut vérifier l'assemblage, pas seulement chaque lot.
10. **Rapport de l'agent correctif désynchronisé.**
    - Symptôme : l'agent haiku a rapporté que HEAD contenait déjà sa modification et qu'il ne pouvait pas isoler son diff. Il a aussi relevé un écart entre le résumé Maven (1 014 tests) et les XML Surefire (1 175).
    - Cause : le tech lead a committé `c9a57d0` pendant que l'agent validait encore.
    - Leçon : on ne committe pas le travail d'un agent avant son rapport. Pour compter, ce sont les XML qui font foi.
11. **B1 a écrit une implémentation temporaire contre la consigne, puis l'a restaurée.**
    - Conséquence : ses 52 tests rouges n'ont jamais été confrontés à une implémentation correcte.
    - Correction : aucune pour l'instant. Le risque sera levé à la phase verte.
    - Leçon : « tests seulement » doit se vérifier par `git status` côté `src/main`, ce qui a été fait.
12. **Nom de propriété divergent.**
    - Symptôme : A4 (haiku) a créé `...date-entree-en-vigueur`, alors que l'ADR-004, écrit en parallèle par A0, fixe `...entree-en-vigueur`.
    - Correction : il a fallu un agent dédié, B0, d'abord bloqué par la mauvaise base puis relancé, pour une ligne de YAML (`71a75be`).
    - Leçon : un nom fixé par un ADR doit l'être **avant** de lancer les tâches qui l'utilisent.
13. **B4 sur une base périmée a recréé des squelettes hors périmètre.**
    - Correction : ébauches annulées (checkout et clean), avance rapide, reprise.
14. **Comptes annoncés contre comptes mesurés.**
    - Symptôme : 25 rouges annoncés pour `813f744`, 28 mesurés.
    - Autre cas : le commit `37dbc65` renvoie à DELEGATION.md pour la liste des 6 tests retournés, et DELEGATION.md renvoyait au commit. Référence circulaire corrigée dans DELEGATION.md (5 tests identifiés par renommage dans le diff).
    - Leçon : citer un chiffre, c'est citer une exécution.
15. **Trous dans les squelettes et l'ADR de l'évolution.**
    - `CloturerRuptureService` n'a pas de port référentiel, alors que la clôture doit renvoyer `essentiel`.
    - Le port `rechercherEnCours` n'a pas de paramètre `essentiel`, alors que le filtre doit précéder la pagination.
    - L'ADR-004 utilise `PRO-0001` (invalide, le format exige 6 chiffres) et ignore la colonne `depuis DATE NOT NULL` de V4.
    - Tout cela a été relevé par les agents de tests rouges, et reste **à corriger** (§ 9).

## 7. Coût de la session

### 7.1 Méthode

- **Tokens** : somme des `message.usage` de chaque transcript de sous-agent, dédoublonnés par `message.id`. Pour l'orchestrateur, même méthode sur le transcript principal (244 appels). Source : `metriques-agents.md`.
- **Sorties du jour 1** : elles sont sous-comptées dans les fichiers (médiane de 8 tokens par message, valeurs d'instantané de début de flux). Les totaux exacts par modèle viennent de la différence entre deux `cost-state` du harnais : haiku 227 856 et opus 503 959 (mesurés), sonnet 286 513 (dérivé, car il suppose complète la sortie de l'orchestrateur). Ces sorties ne sont **pas ventilables** par vague ni par agent : elles figurent sur une ligne à part. Les sorties du jour 2 sont exactes (contre-épreuve faite sur `cost-state`).
- **Entrée, cache en lecture et cache en écriture** de haiku et opus : identiques **au token près** aux totaux `cost-state`.
- **Palier haiku** : un appel dont le contexte (entrée + cache lu + cache écrit) dépasse 100 K tokens est facturé au palier haut, calculé appel par appel. 80 appels sur 210 sont dans ce cas. Pour les sorties manquantes du jour 1, le palier est inconnu : hypothèse d'une répartition au prorata des appels du jour 1 (45,7 % au palier haut), avec une fourchette de 1,07 à 1,50 USD pour haiku.
- **Cache en écriture** : tarif 5 min par défaut, faute d'information sur la durée de vie. Le recoupement du § 7.5 montre que l'orchestrateur est facturé au tarif 1 h. Deux variantes sont donc présentées.
- Calculs par script (python3, hors dépôt). Arrondis au centime.

### 7.2 Tarifs et taux de change

Source : `tarifs.md` (page officielle des tarifs API Anthropic, lue le 2026-10-22).

| Modèle | Entrée | Cache écriture 5 min | Cache écriture 1 h | Cache lecture | Sortie |
|---|---|---|---|---|---|
| Haiku 5.5, contexte ≤ 100 K | 0,10 | 0,125 | 0,20 | 0,01 | 0,50 |
| Haiku 5.5, contexte > 100 K | 0,50 | 0,625 | 1,00 | 0,05 | 2,50 |
| Sonnet 5.5 | 2 | 2,50 | 4 | **0,10** | 10 |
| Opus 5.5 | 4 | 5 | 8 | 0,20 | 20 |

(USD par million de tokens.)

- **Divergence** : le cache en lecture de Sonnet vaut 0,10 sur la page officielle et 0,20 dans une source secondaire (documentation de skill mise en cache le 2026-10-06). **Retenu : 0,10.** Le recoupement le confirme : avec 0,10, le calcul retrouve le montant sonnet du harnais au centime près, alors qu'avec 0,20 il le dépasserait de 11,52 USD.
- **Taux de change** : 1 EUR = 1,1234 USD, soit **1 USD = 0,89015 EUR**. Taux de référence BCE du 2026-10-21, obtenu par recherche web, la page BCE étant injoignable depuis le bac à sable. Le taux du 22/10 n'a pas été obtenu.

### 7.3 Tokens par vague (sous-agents, mesuré)

| Vague | Agents | Entrée non cachée | Sortie | Cache lecture | Cache écriture |
|---|---|---|---|---|---|
| 0a | 4 | 106 | 510 (borne basse) | 3 809 616 | 365 191 |
| 0a-bis | 2 | 36 | 153 (borne basse) | 1 011 902 | 134 517 |
| 1′ | 4 | 52 | 411 (borne basse) | 1 623 601 | 190 646 |
| 2 | 9 | 628 | 9 076 (borne basse) | 39 731 792 | 1 655 377 |
| 2′ | 3 | 50 | 10 459 (borne basse) | 1 722 301 | 121 731 |
| 3 | 15 | 474 | 8 042 (borne basse) | 26 701 637 | 1 581 311 |
| A | 5 | 82 | 56 606 | 2 536 968 | 416 675 |
| B | 9 | 342 | 208 524 | 16 960 700 | 842 789 |
| hors vagues (M1, P1) | 2 | 60 | 41 212 (M1 non terminé) | 2 748 640 | 270 447 |
| **Total** | **53** | **1 830** | **334 993** (fichiers) | **96 847 157** | **5 578 684** |

Par modèle (sous-agents), sortie corrigée :

| Modèle | Agents | Entrée | Sortie (fichiers) | Sortie corrigée | Cache lecture | Cache écriture |
|---|---|---|---|---|---|---|
| haiku | 14 | 428 | 39 844 | **251 377** (exact) | 20 251 451 | 1 147 728 |
| sonnet | 23 | 682 | 118 803 | **≈ 401 204** (dérivé : 286 513 au jour 1 + 73 479 au jour 2 + 41 212 pour M1/P1) | 35 205 475 | 2 008 385 |
| opus | 16 | 720 | 176 346 | **672 089** (exact) | 41 390 231 | 2 422 571 |

Orchestrateur (sonnet, transcript principal, 244 appels) : entrée 605, sortie 333 583 (dont 107 267 de réflexion), cache en lecture 69 782 843, cache en écriture 1 171 450. Sa sortie est jugée fiable (aucun message à 5 tokens ou moins).

### 7.4 Coût calculé

**Par vague**, sous-agents, sorties des fichiers (donc borne basse pour les vagues du jour 1) :

| Vague | haiku | sonnet | opus | Total USD | Total EUR |
|---|---|---|---|---|---|
| 0a | 0,04 | 0,00 | 1,48 | 1,52 | 1,35 |
| 0a-bis | 0,02 | 0,18 | 0,00 | 0,20 | 0,18 |
| 1′ | 0,01 | 0,21 | 0,68 | 0,90 | 0,80 |
| 2 | 0,51 | 4,08 | 5,02 | 9,62 | 8,56 |
| 2′ | 0,03 | 0,09 | 0,00 | 0,12 | 0,11 |
| 3 | 0,30 | 1,67 | 7,90 | 9,86 | 8,78 |
| A | 0,01 | 0,56 | 2,09 | 2,67 | 2,38 |
| B | 0,06 | 1,57 | 6,74 | 8,36 | 7,45 |
| hors vagues (M1, P1) | 0,00 | 1,36 | 0,00 | 1,36 | 1,21 |
| **Sous-total** | 0,97 | 9,73 | 23,92 | **34,62** | **30,82** |
| Sorties du jour 1 non ventilées | 0,30 (0,11-0,53) | 2,82 | 9,92 | 13,04 | 11,61 |

**Session visible** (jusque vers 10:01Z ; M1 partiel) :

| Poste | Variante A : cache écrit 5 min partout | Variante B : orchestrateur en 1 h (recoupée) |
|---|---|---|
| haiku, sous-agents | 1,27 USD | 1,27 USD |
| sonnet, sous-agents des vagues | 11,19 USD | 11,19 USD |
| sonnet, M1 et P1 (instrumentation) | 1,36 USD | 1,36 USD |
| sonnet, orchestrateur | 13,24 USD | 15,00 USD |
| sonnet, appels non journalisés (écart avec `cost-state`, § 7.5) | 1,60 USD | 1,64 USD |
| opus, sous-agents | 33,84 USD | 33,84 USD |
| **Total** | **62,50 USD = 55,63 EUR** | **64,30 USD = 57,23 EUR** |

**Par modèle**, variante B, que je retiens comme la meilleure estimation :

| Modèle | USD | EUR | Part |
|---|---|---|---|
| haiku | 1,27 | 1,13 | 2,0 % |
| sonnet (orchestrateur, sous-agents, instrumentation, non journalisé) | 29,19 | 25,99 | 45,4 % |
| opus | 33,84 | 30,12 | 52,6 % |
| **Total** | **64,30** | **57,23** | |

Ce que disent ces chiffres :

- **Le cache domine.** Lecture et écriture de cache représentent environ 43 USD sur 64, soit deux tiers. Les sorties en représentent environ un tiers (21 USD), l'entrée non cachée moins d'un centime. L'écriture de cache d'opus coûte à elle seule 12,11 USD, soit 2,42 M tokens à 5 USD.
- **Les agents les plus chers** (sorties du jour 1 exclues) sont B5 (opus, 3,37 USD), le lot 1 sécurité (opus, 3,34), E1 (opus, 3,01) et le lot 2 gRPC (sonnet, 2,72).
- **Mesurer le coût a coûté** 1,36 USD (M1 et P1, avant ce document), soit plus que tous les agents haiku de la session réunis. On l'assume.

### 7.5 Recoupement avec le `cost-state` du harnais

Le dernier `cost-state` (2026-10-22T09:41:32Z) donne 62,71 USD : haiku 1,28 ; sonnet 27,59 ; opus 33,84. Il ne couvre ni M1/P1 ni la fin de session.

| Modèle | Harnais | Mon calcul, même périmètre, variante A | Variante B | Commentaire |
|---|---|---|---|---|
| opus | 33,836 | 33,836 | 33,836 | Identique : tokens identiques, tarifs identiques |
| sonnet | 27,594 | 25,820 | **27,594** | Écart de 1,77 USD en variante A, nul en B : l'orchestrateur est facturé en cache 1 h |
| haiku | 1,278 | 1,266 | 1,266 | 0,012 d'écart, dû à l'hypothèse sur le palier des sorties manquantes |
| **Total** | **62,708** | 60,921 | **62,696** | |

**Écart sonnet résiduel.** Le harnais compte, pour sonnet, 14 077 478 tokens de cache en lecture, 24 310 tokens d'entrée, 26 524 tokens de cache en écriture et environ 7 564 tokens de sortie de plus que les transcripts. J'ai valorisé ces tokens sur la ligne « appels non journalisés ». Hypothèse **non vérifiée** : il s'agirait du classifieur de permissions du mode auto et de l'appel de compaction. Sans cette ligne, le calcul passerait sous le harnais.

**Conclusion.** Avec les tarifs de `tarifs.md` et la variante B, le calcul reproduit le harnais à 0,01 USD près. L'hypothèse « cache 5 min partout » sous-estime l'orchestrateur d'environ 1,8 USD.

### 7.6 Piège : `subagent_tokens` n'est pas une consommation

Le champ `<subagent_tokens>` des notifications de fin d'agent vaut **la taille du contexte au dernier tour**, pas un cumul. Exemple, le lot 2 : la notification annonce 247 929 tokens, alors que le transcript en totalise 18 181 077 sur 106 appels, soit 73 fois plus. Additionner ces champs sur les 51 agents notifiés donne 5,65 M tokens, contre 102,8 M réellement traités : on sous-estimerait la session d'un facteur 18. Ce champ sert à savoir si un agent était près de sa limite de contexte, pas à faire des comptes.

### 7.7 Ce qui est compté, et ce qui ne l'est pas

| Élément | Compté ? |
|---|---|
| 53 sous-agents, tentatives ratées et relances comprises | Oui |
| Orchestrateur, historique d'avant compaction compris (présent dans le transcript) | Oui |
| M1 (compteurs) et P1 (tarifs) | Oui. M1 est **partiel** : non terminé au moment de la mesure |
| Appel de compaction (09:06:37Z), classifieur de permissions | Non mesurés directement. **Probablement** inclus dans la ligne « non journalisés » jusqu'à 09:41:32Z (hypothèse), absents ensuite |
| Orchestrateur après 09:41:32Z | Oui (5 appels, environ 0,21-0,24 USD) |
| L'agent qui a rédigé ce document (opus), et tout ce qui suit 10:01Z | **Non** |
| Temps humain de pilotage et de relecture | **Non** (sans objet en USD ici) |

L'orchestrateur réel coûte donc **au moins** ce qu'indique son transcript. Le total de la session visible est une borne basse raisonnable, pas un plafond.

## 8. Ce qu'aurait demandé le même travail sans agents (estimation)

**Durée murale réelle (calculée à partir des timestamps du transcript principal)** :

| Jour | Période | Durée |
|---|---|---|
| 21/10 | 11:46:42 → 14:57:38 | 3 h 11 min (dont une pause de 21 min) |
| 22/10 | 08:49:04 → 09:41:32, fin de la phase rouge | 52 min |
| 22/10 | 09:41 → 10:03, instrumentation et ce bilan | 22 min |
| **Total actif** | | **environ 4 h 25 min** |

S'y ajoutent 6 h 25 min de temps cumulé des agents, qui se recouvrent (mesuré). Le temps humain passé à lire, valider et répondre n'est pas mesuré.

**Ce qui a été produit (mesuré)** :

- code : 5 018 lignes Java de production, 16 641 lignes de test, 355 lignes de proto, 393 lignes de SQL, 142 lignes de YAML ;
- documentation : environ 950 lignes (4 ADR, README, DEMARRAGE, DELEGATION, catalogue) et 640 lignes d'OpenAPI ;
- tests : 1 175 (942 verts historiques et 233 nouveaux, dont 181 rouges) ;
- 67 cas d'acceptation dans l'ADR-004 ;
- revue par trois relecteurs indépendants, PIT et JaCoCo.

**Méthode 1, par composant** (estimation, en jours-homme d'un développeur Java/Spring confirmé qui connaît gRPC et Spring Batch ; les fourchettes incluent les tests du composant) :

| Composant | Basse | Haute |
|---|---|---|
| Cadrage, ADR-001 à 003, contrats proto et OpenAPI | 3 | 5 |
| Domaine et 159 tests | 3 | 5 |
| Flyway V1-V3, persistance JPA, tests H2 | 2 | 4 |
| Sécurité JWT, REST et gRPC avec cas d'attaque (165 tests) | 4 | 7 |
| Adaptateur gRPC (159 tests) | 3 | 5 |
| API REST (34 tests) | 1,5 | 3 |
| Services applicatifs, câblage, transactions | 2 | 4 |
| Batch et 164 tests, correctifs | 3 | 6 |
| Audit chaîné et 111 tests, correctif transactionnel, concurrence | 5 | 9 |
| Bout en bout (40 tests) et correctifs | 2 | 4 |
| Démo, docs, outillage (JaCoCo, PIT) | 2 | 4 |
| Évolution essentiels jusqu'au rouge (ADR-004, contrats, V4, squelettes, 233 tests) | 4 | 7 |
| **Total** | **34,5** | **63** |

**Méthode 2, par productivité** (estimation grossière) : 5 018 lignes de production livrées, testées et documentées, à 50-150 lignes par jour-homme, donnent 33 à 100 jours-homme. Ce ratio est très discutable, et les 16 641 lignes de test, plus répétitives, sont supposées comprises.

**Fourchette retenue (estimation)** : **35 à 90 jours-homme**, soit environ 1,5 à 4 mois pour une personne. Pour une équipe de trois, compter 3 à 6 semaines calendaires, coordination comprise.

**Comparaison** : 4 h 25 min de durée murale contre 245 à 630 heures de travail (à 7 h par jour). Je ne transforme pas ce rapport en « gain » chiffré, pour quatre raisons :

- **La qualité n'est pas à iso-périmètre.** Un développeur aurait peut-être évité certains défauts (contrats incohérents, PIT mal configuré), et en aurait introduit d'autres. Aucune comparaison contrôlée n'a été faite.
- **La relecture humaine n'est pas faite.** Relire 22 000 lignes produites par des agents, dont 16 600 de tests, demande à elle seule une **estimation** de 5 à 15 jours-homme si l'on veut la faire sérieusement. C'est une dette de revue, pas une économie.
- **L'évolution essentiels n'est pas implémentée** : la phase verte reste à payer.
- **Le pilotage humain a un coût** : arbitrages, « go », lecture des rapports. Il n'est pas mesuré.

## 9. Limites avant une mise en production

Liste priorisée. Les trois premières bloquent à elles seules un déploiement.

1. **L'évolution « produits essentiels » n'est pas implémentée.** Son état réel : **phase rouge terminée, 181 tests rouges committés** (`3370668`, `13c050c`, `39aed38`, `f1cde3a`, `813f744`), aucune ligne d'implémentation. Les nouveaux chemins lèvent `UnsupportedOperationException`.
2. **Aucune revue humaine.** Toute la revue a été faite par des agents, y compris la revue de sécurité. Aucun architecte, aucun RSSI, aucun exploitant n'a relu.
3. **PostgreSQL n'a jamais été exécuté**, faute de Docker : tout tourne sur H2 en mode PostgreSQL. CHECK, verrous, isolation et transaction en lecture seule qui écrit l'audit (S15) restent à prouver sur la vraie cible.
4. **Fourche possible de la chaîne d'audit avec plusieurs instances** : pas de `UNIQUE(hash_precedent)`, et le verrou est local à la JVM. La migration est à décider, et le numéro V4 est déjà pris par le référentiel.
5. **Troncature de la queue du journal indétectable** sans ancrage externe du dernier hash. Une ligne forgée en queue passe la vérification.
6. **`UPDATE`/`DELETE` directs possibles en base** sur le journal. Ils sont détectés a posteriori, pas empêchés : pas de trigger, pas de droits restreints.
7. **Secrets et rotation de clés** : une seule clé publique JWT, sans rotation ni JWKS. Gestion de la clé de production, de son renouvellement et de sa révocation non traitée.
8. **Limite de 64 Kio des messages gRPC non vérifiée** de bout en bout, le transport in-process ne l'appliquant pas.
9. **Verrou optimiste limité aux transactions concurrentes** : un « lu hier, écrit aujourd'hui » n'est pas détecté, l'agrégat n'exposant pas de version.
10. **Le rejeu du batch lit le statut courant** des ruptures (et désormais du référentiel). Un calcul passé n'est pas reproductible dans le temps, et les indices ne sont pas persistés.
11. **Cron et fuseau** : le cron est en UTC par défaut, l'horloge métier sur un fuseau configurable, d'où un décalage possible d'un jour. Le déclenchement réel par cron n'a jamais été observé. Côté fichiers : `.tmp` orphelin non nettoyé, répertoire non synchronisé sur disque après le renommage.
12. **Observabilité** : pas de métriques métier, pas de traces, pas d'alerte sur le taux de refus (que l'ADR-004 recommande pourtant de surveiller), pas de supervision du job.
13. **Erreurs du référentiel** : une indisponibilité donne `INTERNAL` ou `FAILED`, sans repli, sans nouvelle tentative et sans disjoncteur. C'est cohérent avec la décision prise, mais sans filet. Le compte applicatif doit être restreint à `SELECT` (S18).
14. **Dépendances non auditées** : aucun outil d'analyse de vulnérabilités dans le build, et gRPC 1.63.0 et protobuf 3.25.3 sont épinglés à la main.
15. **Couverture PIT partielle** : seuls le batch et l'audit sont mutés, et le correctif de la fourche n'a pas de test de reprise.
16. **Documentation désynchronisée** : le README annonce 942 tests et des migrations V1 à V3. La phrase d'OpenAPI « pas de référentiel produits » est à amender.

**Décisions prises par un agent (A0), à valider par le product owner :**

- la pondération ×2 s'applique **indépendamment de la date d'entrée en vigueur**, rejeux antérieurs compris ;
- **référentiel indisponible** = `INTERNAL` en gRPC et `FAILED` pour le batch, sans repli ;
- **ordre d'évaluation** : format au bord, puis existence, propriété et état, puis la règle. En déclaration, le rôle et les invariants du domaine passent avant le message de la règle, ce que l'ADR ne dit pas explicitement (déduction de B2) ;
- `?essentiel=` avec une valeur vide n'est pas tranché. Le tech lead propose 400.

**À régler à l'implémentation (déjà identifié par les agents de tests rouges) :**

1. `CloturerRuptureService` n'a pas de port référentiel, alors que la clôture doit renvoyer `essentiel`. AC-28 et AC-45 ne sont testés que côté acceptation.
2. Le filtre REST `essentiel` doit s'appliquer **avant** la pagination, mais le port `rechercherEnCours` n'a pas de paramètre `essentiel`.
3. La date d'entrée en vigueur doit se lier par `@ConfigurationProperties` avec une analyse ISO stricte, et le nom de la propriété doit figurer dans l'erreur de démarrage (AC-19).
4. Réactiver `@Repository` sur `ReferentielProduitsJpaAdapter`, retiré pour éviter la collision de stubs.
5. Retirer le constructeur de compatibilité de `RuptureVue`.
6. Corriger l'ADR-004 : `PRO-0001` est un identifiant invalide (6 chiffres attendus), et la colonne `depuis DATE NOT NULL` de [V4](../src/main/resources/db/migration/V4__produits_essentiels.sql) n'y est pas décrite.
7. Mettre à jour la Javadoc de `CalculateurTension` pour le ×2.
8. Les 52 tests de B1 n'ont jamais été confrontés à une implémentation correcte : toute assertion fausse doit être signalée, pas « corrigée » en silence.

## 10. Pour le mug

> **« 181 tests rouges, zéro régression : je ne suis pas en retard, je suis en phase rouge. »**

---

Références : [README](../README.md) · [Délégation](DELEGATION.md) · [Démarrage](DEMARRAGE.md) · [ADR-001](adr/ADR-001-architecture-hexagonale.md) · [ADR-002](adr/ADR-002-authentification-autorisation.md) · [ADR-003](adr/ADR-003-audit-immuable-et-indice-de-tension.md) · [ADR-004](adr/ADR-004-produits-essentiels-et-alternative-obligatoire.md) · [OpenAPI](openapi.yaml) · [Catalogue de démo](demo/catalogue-demo.md) · [pom.xml](../pom.xml)
