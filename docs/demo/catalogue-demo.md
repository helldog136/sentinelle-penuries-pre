# Catalogue de démonstration

> **Tout est fictif.** Les produits, les distributeurs, les régions et les formes galéniques ci-dessous sont inventés pour la démo. Toute ressemblance avec un produit, une marque, un laboratoire ou un organisme existant est une coïncidence, et une coïncidence de paperasse, probablement.

La base de démo ne contient **que des codes** : `VMP-NNNNNN` pour les produits, `PRO-NNNNNN` pour les distributeurs. Les noms vivent ici, dans ce fichier, parce que Sentinelle ne tient pas de référentiel produits (voir [`docs/openapi.yaml`](../openapi.yaml), description de `/api/v1/produits/{code}/alternatives`). Pour retrouver le nom d'un code, cherchez-le dans les tableaux ci-dessous.

Le fichier de données est [`src/main/resources/db/demo/V1000__donnees_demo.sql`](../../src/main/resources/db/demo/V1000__donnees_demo.sql).

## Produits (30)

| Code | Nom | Forme |
|---|---|---|
| `VMP-000101` | Patientol 500 mg | comprimé |
| `VMP-000102` | Sirop du Lundi Matin | sirop |
| `VMP-000103` | Réunionite forte | gélule |
| `VMP-000104` | Pastille Anti-File d'Attente | pastille |
| `VMP-000105` | Gouttes du Formulaire 12B | gouttes |
| `VMP-000106` | Collyre Vert Tamponné | collyre |
| `VMP-000107` | Ampoule de la Dernière Minute | ampoule |
| `VMP-000108` | Crème Hydratante du Comité | crème |
| `VMP-000109` | Sachet Tiède Multi-Usage | sachet |
| `VMP-000110` | Injectable Sans Notice | injectable |
| `VMP-000111` | Patch Rétro-Validé | patch |
| `VMP-000112` | Poudre Effervescente de Mardi | poudre |
| `VMP-000113` | Suppositoire Recommandé AR | suppositoire |
| `VMP-000114` | Comprimé Orange Pâle Sans Goût | comprimé |
| `VMP-000115` | Solution Buvable Sous Réserve | solution |
| `VMP-000116` | Gélule Anti-Doublon | gélule |
| `VMP-000117` | Sirop Bis-Deux-Fois-Rien | sirop |
| `VMP-000118` | Pommade du Quatrième Bureau | pommade |
| `VMP-000119` | Capsule Attestée Conforme | capsule |
| `VMP-000120` | Granulés du Jeudi Férié | granulés |
| `VMP-000121` | Lyophilisat Sans Parenthèse | lyophilisat |
| `VMP-000122` | Comprimé Pelliculé Déjà Vu | comprimé |
| `VMP-000123` | Ovule de la Salle d'Attente | ovule |
| `VMP-000124` | Ampoule Buvable Rétrospective | ampoule |
| `VMP-000125` | Collutoire Anti-Relance | collutoire |
| `VMP-000126` | Tisane Bien Tamponnée | tisane |
| `VMP-000127` | Pastilles du Week-End Prolongé | pastilles |
| `VMP-000128` | Bâton Anti-Stress Administratif | bâton |
| `VMP-000129` | Mousse Sans Mode d'Emploi | mousse |
| `VMP-000130` | Sérum du Pôle Ouvert | sérum |

## Distributeurs (8)

| Code | Raison sociale (fictive) |
|---|---|
| `PRO-000201` | Logistique Escargot SRL |
| `PRO-000202` | Transports Lenteur Réunis SA |
| `PRO-000203` | Cartons Tardifs du Nord SAS |
| `PRO-000204` | Entrepôt du Quart d'Heure SARL |
| `PRO-000205` | Camions Hésitants SA |
| `PRO-000206` | Distribution Pigeon Voyageur SPRL |
| `PRO-000207` | Fret Sans Adresse SA |
| `PRO-000208` | Grossiste de la Pause-Café SCOP |

## Régions de la Valdarie (6)

Les codes sont ceux de la base et du contrat. Les libellés sont ceux de l'enum `Region` du domaine.

| Code | Libellé |
|---|---|
| `HAUTE_PAPERASSE` | Haute-Paperasse |
| `BASSE_VALIDATION` | Basse-Validation |
| `PLATEAU_DU_LEGACY` | Plateau du Legacy |
| `COTE_DU_TIMEOUT` | Côte du Timeout |
| `MARAIS_DE_LA_RECETTE` | Marais de la Recette |
| `PROVINCE_DU_TICKET_OUVERT` | Province du Ticket Ouvert |

## Ce que contient la démo

- **40 ruptures** : 20 `EN_COURS` et 20 `CLOTUREE`, réparties sur les quatre motifs (`PRODUCTION`, `LOGISTIQUE`, `REGLEMENTAIRE`, `DEMANDE`).
- **Toutes les régions** sont touchées. Les ruptures en cours les plus anciennes (plus de 90 jours, dont une de 182 jours) touchent surtout le Plateau du Legacy et la Côte du Timeout : c'est là que l'indice de tension monte.
- **Des alternatives**, certaines avec commentaire, jamais le produit en rupture lui-même.
- **Des dates relatives** : elles sont calculées par rapport à la date de démarrage de l'application. La démo reste donc vivante, mais les dates ne sont pas celles du jour où vous lisez ce fichier.

Aucune donnée patient, aucun secret, aucun identifiant réel.
