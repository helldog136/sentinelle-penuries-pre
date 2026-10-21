# ADR-003 : Audit immuable à chaînage de hachage et indice de tension régional

- **Statut** : Accepté
- **Date** : 2026-10-21

Cet ADR traite de deux sujets distincts, regroupés parce qu'ils partagent le même traitement nocturne et la même exigence de traçabilité.

---

## (a) Journal d'audit immuable

### Contexte

Chaque déclaration, modification ou consultation sensible de Sentinelle doit être tracée. Le journal sert à établir a posteriori qui a fait quoi, et doit pouvoir démontrer qu'il n'a pas été altéré. Une altération silencieuse (suppression d'une ligne, modification d'un montant, réordonnancement) doit être détectable.

### Décision

- Le journal est un **journal append-only**. Le port sortant d'audit n'expose **aucune opération de modification ni de suppression** : seule l'ajout d'entrée existe. Cette absence est vérifiée par le test d'architecture (ADR-001) et par la revue.
- Chaque entrée contient **le hachage SHA-256 de l'entrée précédente**, ce qui forme une chaîne. Le hachage de l'entrée courante couvre son contenu et le hachage précédent. La première entrée référence une valeur de genèse fixe, documentée dans la configuration.
- Une **vérification d'intégrité** recalcule la chaîne de bout en bout et signale la première rupture (hachage incohérent, entrée manquante, ordre modifié). Elle est exécutable à la demande et est appelée par le job nocturne.
- Les écritures d'audit sont faites dans la même transaction que l'opération métier qu'elles tracent, afin qu'une opération ne soit jamais effectuée sans trace.

### Alternatives écartées

**Triggers de base de données** pour interdire les `UPDATE` et `DELETE` sur la table d'audit. Ils ne sont pas portables : la syntaxe et le comportement diffèrent entre H2 (tests) et PostgreSQL (cible). Ils protègent aussi mal que prévu contre un administrateur de base. La protection par absence de port et par chaînage est indépendante du moteur.

**Event sourcing complet.** Il ferait de l'événement la source de vérité de tout le domaine. Le besoin est un journal de traçabilité, pas une reconstruction de l'état métier à partir des événements. Le coût de modélisation et d'évolution serait disproportionné.

### Conséquences

- Une altération de la table est détectable mais non empêchable au niveau du moteur : la vérification d'intégrité est le filet de sécurité.
- Le chaînage est séquentiel : les écritures d'audit doivent être sérialisées par chaîne. Le débit d'écriture est suffisant pour le volume attendu, à confirmer par mesure.

---

## (b) Indice de tension régional

### Contexte

Les autorités doivent repérer les régions où les ruptures actives s'accumulent et durent. Une rupture isolée ne suffit pas à signaler une tension ; ce qui compte est le nombre de ruptures actives et leur ancienneté.

Les régions de référence de la Valdarie sont : Haute-Paperasse, Basse-Validation, Plateau du Legacy, Côte du Timeout, Marais de la Recette et Province du Ticket Ouvert.

### Décision

**Définitions.**

- Une rupture est **prise en compte** à une date de référence `D` si son statut est `EN_COURS` à cette date et si elle touche la région considérée.
- Le **nombre de jours écoulés** d'une rupture est `jours_écoulés = max(0, D − date_début)`, en jours entiers, où `date_début` est la date de début de la rupture.
- Le **poids** d'une rupture prise en compte est `poids = 1 + jours_écoulés / 7`.
- L'**indice de région** est la **somme des poids** des ruptures prises en compte pour cette région.
- Une rupture **touchant plusieurs régions compte dans chacune** : son poids est ajouté à l'indice de chaque région concernée. Il n'est pas réparti.

**Notification.** Une région dont l'indice est **strictement supérieur** au seuil reçoit une notification. Le seuil est **configurable** et n'est pas codé en dur.

**Traitement.**

- Le calcul est un **job Spring Batch nocturne**, exécuté à une date de référence explicite (par défaut, la date du jour d'exécution, surchargeable pour rejouer un calcul).
- Le résultat est écrit dans un **fichier de notifications**. Son écriture est **atomique** : le contenu est d'abord écrit dans un fichier temporaire situé dans le même répertoire, puis déplacé sur le nom définitif par un renommage atomique. Un lecteur ne voit jamais un fichier partiel.
- Le job est relançable : une exécution interrompue puis relancée à la même date de référence produit le même fichier.

### Alternatives écartées

Aucune alternative de calcul n'a été retenue : la formule est imposée par le besoin métier. Les alternatives considérées portaient sur la persistance du résultat (table dédiée contre fichier seul) ; la sortie fichier a été retenue car elle est le contrat avec les destinataires, la table restant un détail d'implémentation.

### Conséquences

- Le calcul dépend de la date de référence : il est reproductible pour une date donnée, mais l'indice d'aujourd'hui n'est pas l'indice d'hier recalculé à la volée.
- Une rupture multi-régions contribue à plusieurs indices ; la somme des indices régionaux n'est donc pas le nombre de ruptures. Ce point doit être explicité auprès des destinataires.
- Le choix de la sortie fichier impose une politique de rétention et de nommage des fichiers, à définir avec les destinataires.
