# ADR-004 : Produits essentiels, alternative obligatoire et pondération de l'indice de tension

- **Statut** : Accepté
- **Date** : 2026-10-22
- **Complète** : ADR-002 (aucun nouveau rôle), ADR-003 (formule de l'indice de tension)

## Contexte

Un texte réglementaire de l'AFFSN (fictif, comme tout le reste) introduit la notion de **produit essentiel** et en tire deux conséquences pour Sentinelle :

1. la déclaration d'une rupture **de plus de 30 jours** d'un produit essentiel doit proposer **au moins une alternative**, faute de quoi elle est refusée ;
2. dans l'**indice de tension** régional (ADR-003 b), une rupture d'un produit essentiel **compte double**.

Jusqu'ici, Sentinelle ne tenait aucun référentiel produits : un code produit n'était qu'une chaîne au bon format (`^VMP-[0-9]{6}$`), et la documentation REST le dit en toutes lettres. Il faut donc une source de vérité pour le caractère essentiel, une définition opérable de « plus de 30 jours », une règle qui ne se contourne pas par la mise à jour, et une formule d'indice amendée sans casser les consommateurs existants (gRPC, REST, fichier de notifications, journal d'audit).

Les décisions de fond ci-dessous ont été **validées par le product owner** et sont consignées comme telles. Cet ADR ne les rediscute pas ; il en fixe les détails d'application et tranche les points laissés ouverts.

## Décision

### 1. Source du caractère essentiel : référentiel AFFSN en lecture seule

- Le caractère essentiel provient d'une table **`produits_essentiels`** créée par la migration Flyway **V4**. Un produit est essentiel si et seulement si son code figure dans cette table. Un produit absent (y compris un produit inconnu de Sentinelle) est **non essentiel**.
- Structure minimale : `code_produit VARCHAR(10) NOT NULL PRIMARY KEY`, avec la contrainte `CHECK (code_produit LIKE 'VMP-%')` déjà utilisée par V1. La migration crée la structure **sans données** ; son alimentation relève de l'AFFSN (voir Questions ouvertes).
- L'accès passe par un **port sortant** du package `application` :

  ```java
  public interface ReferentielProduitsPort {
      boolean estEssentiel(CodeProduit produit);
  }
  ```

  Le port n'expose **aucune** opération d'écriture. Son implémentation vit dans `adapter.out.persistence`.
- **Pas de nouveau rôle, pas d'API d'écriture.** Ni la surface gRPC ni la surface REST ne permettent de lire ou de modifier le référentiel en tant que tel. Le distributeur n'a **aucun moyen d'influencer** le statut d'un produit : aucun champ de requête ne le porte.
- Le statut lu est toujours le **statut courant** du référentiel. Il n'y a pas d'historique des classements.
- Une indisponibilité du référentiel n'a pas de repli : pas de cache, pas de valeur par défaut. L'opération échoue (gRPC `INTERNAL`, message fixe `Erreur interne`, transaction annulée, rien d'écrit ; job batch en `FAILED`, journalisé en ERROR comme le prévoit ADR-003). Le référentiel étant dans la même base que les ruptures, ce cas se confond en pratique avec une indisponibilité de la base.

### 2. Durée : « plus de 30 jours »

- Une rupture est **longue** si sa date de fin estimée est **strictement postérieure** à `date_debut + 30 jours` (arithmétique calendaire sur `LocalDate`, donc `finEstimee.isAfter(debut.plusDays(30))`). Autrement dit : 30 jours d'écart sont acceptés sans alternative, 31 ne le sont plus.
- Une rupture **sans fin estimée** a une durée **non bornée** : elle est **longue**.
- La durée est une propriété de la période déclarée (`date_debut`, `date_fin_estimee`). Elle ne dépend **ni** de la date du jour **ni** du temps déjà écoulé : une rupture commencée il y a 60 jours avec une fin estimée à `début + 20 jours` n'est pas longue au sens de cette règle (voir Questions ouvertes).

### 3. Règle d'alternative obligatoire

**Énoncé.** Une rupture dont le produit est essentiel et dont la durée est longue doit comporter **au moins une** alternative. La borne haute (20 alternatives) et les règles de validité des alternatives restent celles du contrat v1.

**Champ d'application.**

| Opération | Règle appliquée ? | Précision |
|---|---|---|
| `DeclarerRupture` | Oui | Sur l'état déclaré. |
| `MettreAJourRupture` | Oui | Sur l'**état résultant** du remplacement complet : produit et début enregistrés, nouvelle fin estimée, nouvelle liste d'alternatives. Sans cela, il suffirait de déclarer court avec alternative puis d'allonger, ou de retirer les alternatives : la règle serait décorative. |
| `CloturerRupture` | **Jamais** | Une clôture n'est jamais bloquée par cette règle, même sur une rupture non conforme. Empêcher de déclarer la fin d'une pénurie serait un remède étrange. |

**Ruptures existantes.** Les ruptures enregistrées avant l'entrée en vigueur, ou devenues non conformes parce que leur produit a été reclassé essentiel, sont **tolérées** : elles restent stockées, consultables et comptées dans l'indice. Elles ne sont jamais revérifiées d'office. La règle s'applique à leur **première modification** : tout appel `MettreAJourRupture`, y compris un appel qui renvoie un état identique, doit produire un état conforme.

**Lieu de la règle.** La règle est une règle métier pure, exprimée dans le `domain` (aucune dépendance Spring, aucune I/O). Le domaine reçoit deux faits déjà résolus : « le produit est essentiel » et « la règle est en vigueur ». Les cas d'usage `DeclarerRuptureService` et `MettreAJourRuptureService` obtiennent le premier via `ReferentielProduitsPort` et le second via `HorlogePort` et la date configurée (§ 5). Comme pour le contrôle de propriété (ADR-002), aucun adaptateur ne porte la règle.

**Ordre d'évaluation et erreur.**

- La règle est évaluée **après** la validation de format au bord (`ValidateurRequetes`). Si la requête comporte des violations de format, seules celles-ci sont renvoyées et la règle n'est pas évaluée.
- Pour la mise à jour, la règle a besoin de la rupture enregistrée (produit, date de début). Elle est donc évaluée **après** existence, propriété et état, comme la règle existante « fin estimée ≥ début enregistré ». Concrètement : rupture inconnue → `NOT_FOUND`, rupture d'un autre distributeur → `PERMISSION_DENIED`, rupture clôturée → `FAILED_PRECONDITION`, et seulement ensuite la règle. Un tiers n'apprend donc rien de l'état de la rupture d'autrui.
- Violation → `INVALID_ARGUMENT`, avec pour `Status.description` **exactement** :

  ```
  alternatives: au moins une alternative requise (produit essentiel, rupture de plus de 30 jours)
  ```

  Le message est fixe. Il respecte le format `champ: raison` du contrat v1, ne reprend aucune valeur saisie et n'est concaténé à aucune autre violation (les autres violations de domaine possibles, comme la fin estimée antérieure au début, sont levées avant).
- Une alternative est une alternative : un produit alternatif **lui-même essentiel** satisfait la règle et ne déclenche aucune exigence supplémentaire. Sentinelle ne vérifie pas non plus la disponibilité de l'alternative.
- Le nombre de régions est sans effet sur la règle : une rupture touchant six régions exige une alternative, pas six.

### 4. Indice de tension : les produits essentiels comptent double

La définition d'ADR-003 (b) est amendée ainsi :

- `poids = 1 + jours_écoulés / 7` pour une rupture d'un produit non essentiel (inchangé) ;
- `poids = (1 + jours_écoulés / 7) × 2` pour une rupture d'un produit essentiel.

Le multiplicateur s'applique au poids entier, après la division : une rupture essentielle commencée à la date de référence pèse 2, pas 1.

Les autres règles d'ADR-003 restent : prise en compte des seules ruptures `EN_COURS` commencées au plus tard à `D`, poids entier ajouté à **chaque** région touchée, seuil strict.

**Statut courant.** Le caractère essentiel est celui du référentiel **au moment du calcul**, par cohérence avec la limite déjà documentée dans ADR-003 (le calcul lit le statut courant des ruptures, pas leur historique). Un produit reclassé change donc aussi le résultat d'un **rejeu** d'une date passée. Au sein d'une même exécution, le statut est résolu **une fois par code produit distinct**, si bien qu'un produit a le même poids relatif dans toutes les régions d'un même calcul.

Le `CalculateurTension` reste un service de domaine pur : l'application résout les statuts via le port et les lui fournit ; il ne fait aucune I/O.

Le seuil d'alerte (`sentinelle.batch.seuil-tension`) est **inchangé**.

### 5. Entrée en vigueur

- Propriété : `sentinelle.regles.alternative-obligatoire.entree-en-vigueur`, date au format `AAAA-MM-JJ`, **optionnelle**. Absente : la règle est en vigueur **immédiatement**. Présente mais invalide (format, date inexistante) : **le démarrage échoue**, comme les autres propriétés validées du service.
- La règle d'alternative obligatoire est en vigueur pour une opération si `horloge.aujourdhui() ≥ entree-en-vigueur` (borne **incluse**), où `aujourdhui()` est la date de Valdarie fournie par `HorlogePort` à la réception. C'est la date de l'**acte** (déclarer, mettre à jour) qui compte, pas la date de début de la rupture : une obligation porte sur ce qu'on fait aujourd'hui, pas sur ce qui a commencé hier.
- Avant cette date, la règle ne s'applique ni à la déclaration ni à la mise à jour.

**La pondération × 2 ne dépend pas de cette date.** Elle s'applique dès le déploiement, à tous les calculs, y compris le rejeu d'une date de référence antérieure à `entree-en-vigueur`. Justification :

- la date d'entrée en vigueur existe pour laisser aux **distributeurs** le temps d'adapter leurs systèmes à une **obligation** nouvelle. La pondération ne leur impose rien : c'est une mesure interne de l'AFFSN, qui n'a pas de période d'adaptation à accorder à elle-même ;
- coupler la pondération à cette date ajouterait au calcul une seconde dimension temporelle (« la règle était-elle en vigueur à `D` ? ») alors qu'il lit délibérément le statut courant et non l'historique (ADR-003). On aurait un calcul à moitié historique, ce qui est pire que pas du tout ;
- la date d'entrée en vigueur est évaluée par rapport à `aujourdhui()`, la date de référence du batch par rapport à `D` : les mélanger rendrait le résultat d'un rejeu dépendant du jour où on le lance d'une deuxième façon, en plus de celle qu'ADR-003 admet déjà.

### 6. Audit

- Les **refus** (dont ceux de cette règle) ne sont **pas audités** : le journal ne contient que des écritures réussies, comme aujourd'hui.
- Les écritures réussies sont auditées comme avant. Le **format canonique `sentinelle-audit-v1` est inchangé**, détail compris : le caractère essentiel n'y figure pas.

### 7. Exposition en lecture

- **gRPC** : champ additif `bool essentiel = 13;` dans le message `Rupture` (sortie uniquement ; 11 et 12 sont réservés). Il est renseigné dans les réponses des trois RPC, selon le statut courant lu pendant l'appel.
- **REST** : propriété additive `essentiel` (booléen, toujours présente) dans le schéma `Rupture` (liste et détail, ruptures en cours comme clôturées).
- **REST** : filtre optionnel `essentiel` sur `GET /api/v1/ruptures`. `true` ne garde que les ruptures de produits essentiels, `false` que les autres, absent ne filtre rien. Il se combine par ET avec `region` et `produit`, et la pagination (`totalElements`, `totalPages`) porte sur le résultat filtré. Seules les valeurs `true` et `false` (minuscules) sont acceptées. Toute autre valeur produit un `400` au format d'erreur existant, avec une violation sur le paramètre `essentiel`.
- Le schéma `AlternativesProduit` est inchangé : le caractère essentiel d'une alternative n'est pas exposé (il est sans effet, § 3).

## Alternatives écartées

**Référentiel déclaratif par le distributeur** (un champ `essentiel` dans `DeclarerRuptureRequest`). C'est la solution la plus simple à coder et la plus facile à contourner : le seul acteur soumis à l'obligation serait aussi celui qui décide si elle s'applique. Elle contredirait aussi le principe d'ADR-002 selon lequel l'autorité ne vient jamais du contenu du message. Le même produit pourrait de plus être essentiel pour un distributeur et pas pour un autre. Écartée.

**API d'administration du référentiel** (RPC ou endpoint REST d'écriture, avec un rôle `ADMINISTRATEUR`). Elle obligerait à créer un rôle, un circuit de jetons, des contrôles d'autorisation et un audit des modifications de référentiel, pour une donnée dont l'AFFSN est déjà propriétaire et qu'elle gère ailleurs. Sentinelle deviendrait l'outil de gestion d'un référentiel qui n'est pas le sien. La lecture seule suffit au besoin et réduit la surface d'attaque à zéro ligne de code. Écartée.

**Pondération soumise à la date d'entrée en vigueur.** Écartée pour les raisons données au § 5.

**Vérification rétroactive des ruptures existantes** (refus de lecture, clôture forcée ou marquage automatique). Sentinelle n'a ni la légitimité ni l'information pour corriger d'office une déclaration. La tolérance jusqu'à la première modification suffit à faire converger le stock sans casser un client qui ne touche pas à ses ruptures. Écartée.

## Conséquences

- Sentinelle **tient désormais un référentiel produits**, minimal et en lecture seule. La phrase de `docs/openapi.yaml` qui dit le contraire sera amendée au moment de l'implémentation. L'endpoint des alternatives garde son comportement (200 et liste vide pour un produit sans rupture en cours).
- **Plus de régions seront signalées** à seuil constant, puisque les poids ne peuvent qu'augmenter. Les indices calculés avant et après le déploiement **ne sont pas comparables**. Les destinataires du fichier de notifications doivent en être informés, avec la date du changement de formule. Le format du fichier est inchangé : seules les valeurs changent.
- Un rejeu reflète le référentiel courant. Comme pour les statuts de rupture (ADR-003), le calcul reste déterministe pour un état donné des données mais n'est pas reproductible dans le temps.
- Le journal d'audit ne permet pas de prouver a posteriori qu'un produit était essentiel au moment d'une déclaration : ni le journal ni le référentiel n'en gardent la trace.
- Chaque déclaration, mise à jour et clôture fait une lecture supplémentaire (clé primaire). Le batch en fait une par code produit distinct. Le coût est négligeable pour le volume attendu ; une méthode de lecture groupée pourra être ajoutée au port si une mesure le justifie.
- ArchUnit (ADR-001) couvre le nouveau port comme les autres : déclaré dans `application`, implémenté dans `adapter.out.persistence`, sans Spring dans `domain` ni `application`.

## Compatibilité ascendante

| Surface | Changement | Nature |
|---|---|---|
| gRPC, requêtes | Aucun champ ajouté, retiré ou modifié. | Compatible. |
| gRPC, réponses | Champ `essentiel = 13` dans `Rupture`. Un client généré avant l'ajout l'ignore comme champ inconnu. | Additif. |
| gRPC, erreurs | Aucun nouveau code. La règle réutilise `INVALID_ARGUMENT` et le format `champ: raison`, avec le message fixé au § 3. | Compatible au sens du contrat. |
| REST, réponses | Propriété `essentiel` dans `Rupture`. | Additif. |
| REST, requêtes | Paramètre de requête optionnel `essentiel`. Une requête sans lui se comporte comme avant. | Additif. |
| Fichier de notifications | Format identique. Les valeurs d'indice changent de formule. | Compatible en syntaxe, **changement de sémantique** à communiquer. |
| Journal d'audit | `sentinelle-audit-v1` inchangé, chaîne inchangée. | Compatible. |
| Configuration | Nouvelle propriété optionnelle, défaut immédiat. | Additif. |

**Risque principal.** Le contrat gRPC ne change pas, mais son comportement si. Un client qui **met à jour** une rupture longue d'un produit essentiel **sans alternative**, par exemple en renvoyant l'état tel qu'il l'a toujours stocké, recevra désormais `INVALID_ARGUMENT` là où il recevait un succès. C'est voulu, mais un client qui traite `INVALID_ARGUMENT` comme une erreur de programmation définitive peut bloquer sa synchronisation. Mesures : annoncer la règle et son message exact aux distributeurs, utiliser `entree-en-vigueur` pour leur laisser une fenêtre d'adaptation, et surveiller le taux de refus portant ce message après l'activation.

## Cas limites explicites

| Cas | Comportement |
|---|---|
| Fin estimée = début + 30 jours | Pas longue : acceptée sans alternative. |
| Fin estimée = début + 31 jours | Longue : refusée sans alternative. |
| Fin estimée = début | Durée nulle : acceptée sans alternative. |
| Fin estimée absente | Durée non bornée, donc longue : refusée sans alternative. |
| Le calcul traverse février ou une année bissextile | Arithmétique calendaire (`plusDays(30)`), pas des mois et pas des tranches de 24 h. |
| Mise à jour qui retire les alternatives d'une rupture longue | Refusée ; la rupture enregistrée est inchangée. |
| Mise à jour qui allonge une rupture courte sans alternative au-delà de 30 jours, ou qui efface sa fin estimée | Refusée. |
| Mise à jour qui raccourcit une rupture à 30 jours ou moins et retire ses alternatives | Acceptée. |
| Rupture existante non conforme | Tolérée en lecture et dans l'indice. La première mise à jour, même identique, doit être conforme. |
| Clôture d'une rupture non conforme | Acceptée. |
| Rupture multi-régions | Une alternative suffit. Poids × 2 dans chaque région touchée. |
| Alternative elle-même essentielle | Satisfait la règle ; aucun effet supplémentaire. |
| Produit reclassé essentiel après la déclaration | Tolérée jusqu'à la prochaine mise à jour ; × 2 dès le calcul suivant, rejeux compris. |
| Produit retiré du référentiel | La règle ne s'applique plus ; poids × 1 dès le calcul suivant. |
| Opération avant la date d'entrée en vigueur | Règle non appliquée. Pondération × 2 appliquée quand même. |
| Opération le jour même de l'entrée en vigueur | Règle appliquée (borne incluse). |
| Requête avec violations de format et non conforme | Seules les violations de format sont renvoyées. |
| Mise à jour non conforme par un tiers, sur une rupture clôturée ou inconnue | `PERMISSION_DENIED`, `FAILED_PRECONDITION` ou `NOT_FOUND`, jamais le message de la règle. |
| Référentiel indisponible | `INTERNAL`, rien écrit ; batch en `FAILED`. |

## Questions ouvertes

1. **Alimentation du référentiel.** Qui charge `produits_essentiels`, par quel procédé et à quelle fréquence ? En production, le compte applicatif devrait n'avoir que le droit `SELECT` sur cette table, pour que la lecture seule ne repose pas uniquement sur l'absence de code. À convenir avec l'exploitation AFFSN.
2. **Fin estimée dépassée.** Une rupture essentielle déclarée sur 20 jours et toujours en cours au 60ᵉ jour reste conforme sans alternative, puisque la règle porte sur la durée *déclarée*. Une fin estimée optimiste est donc un moyen légal, sinon élégant, d'échapper à l'obligation. Faut-il retenir `max(fin estimée, aujourd'hui) − début` ? Ce serait une décision réglementaire, pas technique.
3. **Traçabilité du classement.** Faut-il historiser le référentiel, ou consigner le caractère essentiel dans le détail d'audit (ce qui imposerait un format canonique v2), pour pouvoir prouver la conformité d'une déclaration passée ?
4. **Inventaire des ruptures tolérées.** L'AFFSN voudra probablement la liste des ruptures longues essentielles sans alternative. Le filtre `essentiel=true` y contribue, mais aucun endpoint ne les isole.
5. **Recalibrage du seuil.** Le seuil est inchangé par décision. Une revue après quelques semaines de calculs pondérés dira s'il signale encore ce qu'il est censé signaler, ou simplement davantage.

## Cas d'acceptation

Conventions communes, sauf mention contraire :

- Référentiel : `VMP-100001` et `VMP-300003` essentiels ; `VMP-200002` et `VMP-400004` absents (non essentiels).
- Horloge : `aujourdhui()` = **2026-11-01** (Valdarie). `entree-en-vigueur` absente (règle en vigueur).
- Acteur : distributeur `PRO-0001`, propriétaire des ruptures préexistantes. Les requêtes sont valides par ailleurs (motif défini, une région `HAUTE_PAPERASSE`).
- `MSG` désigne exactement `alternatives: au moins une alternative requise (produit essentiel, rupture de plus de 30 jours)`.
- « Refusé » signifie : statut `INVALID_ARGUMENT`, description = `MSG`, aucune écriture en base, aucune entrée ajoutée au journal d'audit.
- Batch : date de référence `D` = **2026-11-30**, seuil **10.0**. Valeurs d'indice comparées à 1e-9 près.

### Déclaration

| ID | Given | When | Then |
|---|---|---|---|
| AC-01 | Produit `VMP-100001` (essentiel) | `DeclarerRupture` début 2026-11-01, fin 2026-12-01 (30 j), 0 alternative | Acceptée, statut `EN_COURS`, `essentiel = true` dans la réponse. |
| AC-02 | Produit `VMP-100001` | `DeclarerRupture` début 2026-11-01, fin 2026-12-02 (31 j), 0 alternative | Refusée (`MSG`). |
| AC-03 | Produit `VMP-100001` | Comme AC-02 avec 1 alternative `VMP-400004` | Acceptée, `essentiel = true`. |
| AC-04 | Produit `VMP-100001` | `DeclarerRupture` début 2026-11-01, sans fin estimée, 0 alternative | Refusée (`MSG`). |
| AC-05 | Produit `VMP-100001` | Comme AC-04 avec 1 alternative | Acceptée. |
| AC-06 | Produit `VMP-100001` | `DeclarerRupture` début = fin = 2026-11-01, 0 alternative | Acceptée. |
| AC-07 | Produit `VMP-200002` (non essentiel) | `DeclarerRupture` sans fin estimée, 0 alternative ; puis fin 2026-12-02, 0 alternative | Les deux acceptées, `essentiel = false`. |
| AC-08 | Horloge 2027-01-15 ; produit `VMP-100001` | `DeclarerRupture` début 2027-02-01, fin 2027-03-03 (début + 30 j), 0 alternative ; puis fin 2027-03-04 | Première acceptée ; seconde refusée (`MSG`). |
| AC-09 | Horloge 2028-01-15 (année bissextile) ; produit `VMP-100001` | `DeclarerRupture` début 2028-02-01, fin 2028-03-02 (début + 30 j), 0 alternative ; puis fin 2028-03-03 | Première acceptée ; seconde refusée (`MSG`). |
| AC-10 | Produit `VMP-100001` | `DeclarerRupture` début 2026-09-01 (passé), sans fin estimée, 0 alternative | Refusée (`MSG`) : la durée ne dépend pas de la date du jour. |
| AC-11 | Produit `VMP-100001` | `DeclarerRupture` début 2026-11-01, fin 2026-12-02, 1 seule alternative `VMP-300003` (essentielle) | Acceptée : une alternative essentielle satisfait la règle. |
| AC-12 | Produit `VMP-100001` | `DeclarerRupture` longue, 6 régions, 0 alternative ; puis la même avec 1 alternative | Première refusée avec une description égale à `MSG`, une seule fois ; seconde acceptée. |
| AC-13 | Produit `VMP-100001` | `DeclarerRupture` longue, 0 alternative, motif `MOTIF_RUPTURE_UNSPECIFIED` | `INVALID_ARGUMENT`, description exactement `motif: valeur obligatoire` (règle non évaluée). |
| AC-14 | Produit `VMP-999999` (inconnu du référentiel et de Sentinelle) | `DeclarerRupture` sans fin estimée, 0 alternative | Acceptée, `essentiel = false`. |
| AC-15 | Produit `VMP-100001` ; le port lève une exception technique | `DeclarerRupture` (quelle que soit la durée) | `INTERNAL`, description `Erreur interne`, aucune écriture, aucune entrée d'audit. |

### Entrée en vigueur

| ID | Given | When | Then |
|---|---|---|---|
| AC-16 | `entree-en-vigueur` = 2026-12-01, horloge 2026-11-30 | `DeclarerRupture` `VMP-100001` sans fin estimée, 0 alternative | Acceptée. |
| AC-17 | `entree-en-vigueur` = 2026-11-01, horloge 2026-11-01 | Même requête qu'AC-16 | Refusée (`MSG`) : borne incluse. |
| AC-18 | `entree-en-vigueur` = 2026-12-01 ; rupture `VMP-100001` sans fin estimée ni alternative déclarée le 2026-11-30 | Mise à jour identique le 2026-11-30, puis le 2026-12-01 (horloge avancée) | Première acceptée ; seconde refusée (`MSG`). |
| AC-19 | `entree-en-vigueur` = `2026-13-01` (ou `demain`) | Démarrage de l'application | Le démarrage échoue avec une erreur de configuration nommant la propriété. |
| AC-20 | Propriété absente | Même requête qu'AC-16, horloge 2026-11-01 | Refusée (`MSG`) : en vigueur immédiatement. |

### Mise à jour

Rupture préexistante `R` : produit `VMP-100001`, début 2026-10-01, `EN_COURS`, propriétaire `PRO-0001`.

| ID | Given | When | Then |
|---|---|---|---|
| AC-21 | `R` sans fin estimée, 1 alternative `VMP-400004` | `MettreAJourRupture` sans fin estimée, 0 alternative | Refusée (`MSG`) ; `R` relue en base garde son alternative ; journal inchangé. |
| AC-22 | `R` fin 2026-10-31 (30 j), 0 alternative | `MettreAJourRupture` fin 2026-11-01 (31 j), 0 alternative | Refusée (`MSG`). |
| AC-23 | `R` fin 2026-10-31, 0 alternative | `MettreAJourRupture` sans fin estimée, 0 alternative | Refusée (`MSG`). |
| AC-24 | `R` sans fin estimée, 1 alternative | `MettreAJourRupture` fin 2026-10-31, 0 alternative | Acceptée. |
| AC-25 | `R` sans fin estimée, 0 alternative (antérieure à la règle, non conforme) | `GET /api/v1/ruptures/{id}` | 200, `essentiel: true`, rupture présente telle quelle. |
| AC-26 | `R` comme AC-25 | `MettreAJourRupture` reprenant exactement l'état enregistré | Refusée (`MSG`). |
| AC-27 | `R` comme AC-25 | `MettreAJourRupture` sans fin estimée, 1 alternative | Acceptée, une entrée d'audit `MODIFICATION`. |
| AC-28 | `R` comme AC-25 | `CloturerRupture` fin effective 2026-11-01 | Acceptée, statut `CLOTUREE`, `essentiel = true` dans la réponse. |
| AC-29 | `R` conforme (1 alternative, sans fin estimée) | `CloturerRupture` fin effective 2026-11-01 | Acceptée. |
| AC-30 | `R` comme AC-25 ; acteur `PRO-0002` (non propriétaire) | `MettreAJourRupture` non conforme | `PERMISSION_DENIED`, description `Accès refusé` (pas `MSG`). |
| AC-31 | `R` clôturée | `MettreAJourRupture` non conforme par `PRO-0001` | `FAILED_PRECONDITION`, description `Rupture déjà clôturée`. |
| AC-32 | Identifiant bien formé, absent | `MettreAJourRupture` non conforme | `NOT_FOUND`, description `Rupture introuvable`. |
| AC-33 | `R` sans fin estimée, 1 alternative, région `HAUTE_PAPERASSE` | `MettreAJourRupture` sans fin estimée, régions `HAUTE_PAPERASSE` et `COTE_DU_TIMEOUT`, 0 alternative | Refusée (`MSG`). |
| AC-34 | `R` comme AC-25 ; `VMP-100001` retiré du référentiel | `MettreAJourRupture` sans fin estimée, 0 alternative | Acceptée, `essentiel = false`. |
| AC-35 | Rupture `S` de `VMP-200002`, sans fin estimée ni alternative ; `VMP-200002` ajouté ensuite au référentiel | `GET` de `S`, puis `MettreAJourRupture` identique, puis avec 1 alternative | `GET` : `essentiel: true` ; mise à jour identique refusée (`MSG`) ; avec alternative acceptée. |
| AC-36 | `R` sans fin estimée, 1 alternative `VMP-400004` | `MettreAJourRupture` remplaçant l'alternative par `VMP-300003` (essentielle) | Acceptée. |

### Lecture REST et réponse gRPC

| ID | Given | When | Then |
|---|---|---|---|
| AC-37 | Ruptures en cours : `A` (`VMP-100001`, `HAUTE_PAPERASSE`), `B` (`VMP-200002`, `HAUTE_PAPERASSE`), `C` (`VMP-300003`, `COTE_DU_TIMEOUT`) | `GET /api/v1/ruptures?essentiel=true` | `A` et `C`, `totalElements = 2`, tri existant conservé. |
| AC-38 | Données d'AC-37 | `GET /api/v1/ruptures?essentiel=false` | `B` seule, `totalElements = 1`. |
| AC-39 | Données d'AC-37 | `GET /api/v1/ruptures` (sans filtre) | `A`, `B`, `C`, chacune avec `essentiel` renseigné (`true`, `false`, `true`). |
| AC-40 | Données d'AC-37 | `GET /api/v1/ruptures?essentiel=true&region=HAUTE_PAPERASSE` | `A` seule. |
| AC-41 | Données d'AC-37 | `GET /api/v1/ruptures?essentiel=oui` (puis `TRUE`, puis `1`) | `400` au format d'erreur existant, violation sur `essentiel`. |
| AC-42 | Rupture clôturée de `VMP-100001` | `GET /api/v1/ruptures/{id}` | 200, `essentiel: true`. |
| AC-43 | `A` affichée `essentiel: true` ; `VMP-100001` retiré du référentiel | `GET /api/v1/ruptures/{id de A}` | `essentiel: false`, sans redémarrage ni modification de `A`. |
| AC-44 | Client gRPC généré depuis le proto v1 **sans** le champ 13 | `DeclarerRupture` acceptée | Réponse décodée sans erreur, champs connus identiques à avant. |
| AC-45 | Rupture de `VMP-200002` | Réponses de `DeclarerRupture`, `MettreAJourRupture`, `CloturerRupture` | `essentiel = false` dans les trois. |

### Audit

| ID | Given | When | Then |
|---|---|---|---|
| AC-46 | Journal de N entrées, chaîne intègre | Une déclaration refusée (AC-02) et une mise à jour refusée (AC-21) | Toujours N entrées ; vérification d'intégrité OK. |
| AC-47 | Journal de N entrées | Déclaration acceptée d'AC-03 | N + 1 entrées ; nouvelle entrée de type `DECLARATION`, version canonique `sentinelle-audit-v1`, détail `produit VMP-100001, 1 régions` (même forme qu'avant ADR-004, sans mention du caractère essentiel). |

### Indice de tension (batch)

Sauf mention contraire, chaque rupture est `EN_COURS` et touche la seule région `HAUTE_PAPERASSE` ; les autres régions valent 0.0.

| ID | Given | When | Then |
|---|---|---|---|
| AC-48 | Une rupture `VMP-200002` début 2026-11-16 (14 j) | Calcul à `D` | `HAUTE_PAPERASSE` = **3.0** (non essentiel, inchangé). |
| AC-49 | Une rupture `VMP-100001` début 2026-11-16 (14 j) | Calcul à `D` | **6.0**. |
| AC-50 | Une rupture `VMP-100001` début 2026-11-30 (0 j) | Calcul à `D` | **2.0** (et non 1.0 : le × 2 porte sur le poids entier). |
| AC-51 | Une rupture `VMP-100001` début 2026-11-23 (7 j) | Calcul à `D` | **4.0**. |
| AC-52 | Une rupture `VMP-100001` début 2026-11-20 (10 j) | Calcul à `D` | **34/7 ≈ 4.857142857142857** (= 2 × (1 + 10/7)). |
| AC-53 | Une rupture `VMP-100001` début 2026-12-01 (postérieure à `D`) | Calcul à `D` | **0.0** (non prise en compte). |
| AC-54 | Une rupture `VMP-100001` début 2026-11-16, **clôturée** le 2026-11-20 | Calcul à `D` | **0.0**. |
| AC-55 | Une rupture `VMP-100001` début 2026-11-16, régions `HAUTE_PAPERASSE` et `COTE_DU_TIMEOUT` | Calcul à `D` | Ces deux régions = **6.0** chacune ; les quatre autres = 0.0. |
| AC-56 | `VMP-200002` début 2026-11-16 (3.0) + `VMP-100001` début 2026-11-16 (6.0) + `VMP-300003` début 2026-11-30 (2.0) | Calcul à `D` | `HAUTE_PAPERASSE` = **11.0** > 10.0 : signalée. Fichier exactement `{"dateReference":"2026-11-30","seuil":10.0,"regions":[{"region":"HAUTE_PAPERASSE","indice":11.0}]}`. |
| AC-57 | Mêmes ruptures qu'AC-56, référentiel vide | Calcul à `D` | **7.0** (3 + 3 + 1) : non signalée, `regions: []`. Montre que la pondération signale des régions qui ne l'étaient pas. |
| AC-58 | `VMP-100001` début 2026-11-16 (6.0) + `VMP-300003` début 2026-11-23 (4.0) | Calcul à `D` | **10.0** : non signalée (seuil strict). |
| AC-59 | Une rupture `VMP-999999` (absent du référentiel) début 2026-11-16 | Calcul à `D` | **3.0**. |
| AC-60 | Une rupture `VMP-100001` début 2026-11-16 avec 1 alternative, et une autre de même début sans alternative (tolérée) | Calcul à `D` | **12.0** (6.0 + 6.0) : les alternatives et la conformité n'influent pas sur le poids. |
| AC-61 | `entree-en-vigueur` = 2027-01-01 ; une rupture `VMP-100001` début 2026-11-16 | Calcul à `D` = 2026-11-30 | **6.0** : la pondération ignore la date d'entrée en vigueur. |
| AC-62 | Calcul d'AC-49 exécuté (6.0) ; puis `VMP-100001` retiré du référentiel | Relance pour la même `D` | **3.0** ; le fichier du jour est remplacé ; nouvelle instance de job. |
| AC-63 | Une rupture `VMP-100001` début 2026-11-16, régions `HAUTE_PAPERASSE` et `BASSE_VALIDATION`, et une rupture `VMP-100001` début 2026-11-23 en `HAUTE_PAPERASSE` | Calcul à `D` | Le port est interrogé **une seule fois** pour `VMP-100001` ; `HAUTE_PAPERASSE` = 10.0, `BASSE_VALIDATION` = 6.0. |
| AC-64 | Données d'AC-56 | Calcul à `D` | Contexte d'exécution : `indice.HAUTE_PAPERASSE` = 11.0, `nombreRupturesActives` = 3, `nombreRegionsSignalees` = 1. |
| AC-65 | Le port lève une exception technique | Lancement du job | Job `FAILED`, journalisé en ERROR, aucun fichier écrit ou remplacé, planification suivante active. |

### Architecture

| ID | Given | When | Then |
|---|---|---|---|
| AC-66 | Code compilé | Test ArchUnit | `ReferentielProduitsPort` est dans `application.port.out`, n'a qu'une méthode publique (`estEssentiel(CodeProduit) → boolean`), et son implémentation est dans `adapter.out.persistence`. Ni `domain` ni `application` ne dépendent de Spring. |
| AC-67 | Contrat gRPC et OpenAPI | Revue du contrat et test de contrat | Aucun RPC ni endpoint ne crée, modifie ou supprime une entrée de `produits_essentiels` ; aucune requête ne transporte de champ `essentiel`. |
