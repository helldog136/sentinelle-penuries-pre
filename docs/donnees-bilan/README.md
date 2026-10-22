# Données du bilan

Données brutes et scripts derrière les chiffres de [BILAN-DEMO.md](../BILAN-DEMO.md). Tout est fictif, comme le reste du dépôt, et les coûts sont indicatifs : ce n'est pas une facture.

Les JSON et les Markdown sont des résultats figés, à lire tels quels. Les scripts décrivent la méthode, mais ils ne se relancent pas tels quels (voir plus bas).

## Fichiers

| Fichier | Contenu | Origine |
|---|---|---|
| `metriques-agents.md` | Lecture des métriques : tokens par sous-agent, par vague et par modèle ; parallélisme mesuré ; orchestrateur. Heures en UTC. | Mesuré, depuis les transcripts de session. Produit par `md.py`. |
| `metriques-agents.json` | Les mêmes données, brutes : un enregistrement par sous-agent (`rows`) et les totaux par vague (`waves`). | Idem. |
| `tarifs.md` | Tarifs publics de l'API en USD par million de tokens, pour Haiku 5.5 (deux paliers), Sonnet 5.5 et Opus 5.5. Fable 5.1 figure dans la table, sans usage dans le bilan. | Page officielle des tarifs, lue le 2026-10-22. |
| `tarifs.json` | Les mêmes tarifs, au format machine, et le taux de change retenu : 1 EUR = 1,1234 USD, soit 1 USD = 0,89015 EUR. | Taux de référence BCE du 2026-10-21, obtenu par recherche web. Le taux du 22/10 n'a pas été obtenu. |
| `couverture-avant.json` | Couverture JaCoCo par package avant les tests rétroactifs (13:09Z, le 21/10), code protobuf généré compris : 34,5 % des lignes. | Mesuré, rapports JaCoCo. Produit par `couverture.py`. |
| `couverture-base-corrigee.json` | Même mesure « avant », sans le paquet `grpc.v1` (code protobuf généré) : c'est la base de comparaison corrigée. Elle donne 66,6 % des lignes et 65,3 % des branches. | Dérivé de `couverture-avant.json`. Le bilan ne nomme pas ce fichier ; son identité vient de la comparaison des chiffres avec le § 4.2. |
| `couverture-apres.json` | Couverture JaCoCo après la vague 3 (14:20Z), hors code généré : 97,7 % des lignes et 92,9 % des branches. | Mesuré, rapports JaCoCo. |
| `haiku_tiers.json` | Appels haiku, classés selon le palier de prix : contexte jusqu'à 100 K tokens, ou au-delà (palier cinq fois plus cher). Totaux (`all`) et par vague (`wave`). 210 appels, dont 80 au palier haut. | Mesuré, depuis les transcripts. Produit par `tier.py`. |

## Scripts

Dans `scripts/`, du plus bas niveau au plus haut :

| Script | Rôle |
|---|---|
| `an.py` | Lit le transcript principal et les transcripts des sous-agents : lancements, modèles, notifications, tokens. Base des scripts suivants. |
| `final.py` | Rattache chaque sous-agent à sa vague, d'après l'horodatage, calcule les totaux par vague et par modèle, et les rapproche du dernier `cost-state`. |
| `md.py` | Écrit `metriques-agents.json` et `metriques-agents.md`. Estime aussi les sorties du jour 1, sous-comptées dans les fichiers. |
| `tier.py` | Classe les appels haiku par palier de prix et écrit `haiku_tiers.json`. |
| `cout.py` | Calcule le coût à partir des tarifs, des paliers et des tokens, par vague et par modèle, avec les deux durées de vie du cache (5 min et 1 h). Affiche le tableau, sans écrire de fichier. |
| `couverture.py` | Résume un rapport JaCoCo XML par package et par groupe, et écrit un JSON au format des `couverture-*.json`. Usage : `python3 couverture.py <rapport-jacoco.xml> <sortie.json>`. Le préfixe de paquet (`vd/affsn/sentinelle`) est codé en dur. |

## Ce qui ne tourne pas tel quel

- `an.py`, `final.py`, `md.py` et `tier.py` lisent les transcripts de la session d'origine, sous `/root/.claude/projects/...`. Ces transcripts ne sont pas dans le dépôt, et l'identifiant de session est codé en dur.
- `cout.py` et `tier.py` lisent et écrivent aussi des fichiers de travail dans un répertoire scratchpad `/tmp/claude-0/...`, absent du dépôt.
- `couverture.py` est autonome : il suffit d'un rapport JaCoCo XML, par exemple `target/site/jacoco/jacoco.xml`. Un rapport produit pendant la phase rouge ne donne pas une couverture significative (voir § 4.2 du bilan).

## Piège : `subagent_tokens` n'est pas une consommation

Le champ `<subagent_tokens>` des notifications de fin d'agent vaut la taille du contexte au dernier tour, pas un cumul. Additionner ces valeurs sous-estime la session d'un facteur 18. Ce champ sert à voir si un agent approchait de sa limite de contexte, pas à compter des tokens.

Pour la méthode de coût (tokens, tarifs, cache, hypothèses, recoupement avec le harnais), voir [BILAN-DEMO.md](../BILAN-DEMO.md), section 7.
