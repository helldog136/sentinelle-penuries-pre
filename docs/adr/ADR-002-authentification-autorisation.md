# ADR-002 : Authentification et autorisation par JWT signé

- **Statut** : Accepté
- **Date** : 2026-10-21

## Contexte

Sentinelle expose deux surfaces : une surface d'écriture en gRPC, réservée aux distributeurs qui déclarent des ruptures, et une surface de lecture en REST, ouverte aux pharmaciens et aux prescripteurs. Les appelants sont des systèmes tiers, sans interaction utilisateur directe avec Sentinelle. Il faut donc un mécanisme de jeton vérifiable hors ligne, sans session serveur, et un modèle d'autorisation simple et explicite.

Deux exigences guident la décision :

- une déclaration de rupture ne peut être créée ou modifiée que par le distributeur auquel elle appartient ;
- l'identité de l'appelant ne doit jamais être déduite de ce que le message contient.

## Décision

**Jeton.** L'authentification repose sur des **JWT signés avec une clé asymétrique**. L'algorithme est **épinglé** dans la configuration (`RS256`) : le validateur n'accepte que cet algorithme, indépendamment de l'en-tête `alg` du jeton, ce qui écarte les attaques par confusion d'algorithme (y compris `none`). Le validateur vérifie systématiquement :

- la signature, avec la clé publique de l'émetteur ;
- `exp` (expiration) ;
- `iss` (émetteur attendu) ;
- `aud` (audience attendue : Sentinelle).

**Gestion des clés.**

- Une **paire de clés de test** est générée pour les tests. Elle est **marquée `TEST ONLY`** dans son en-tête et son nom de fichier, et n'est **committée que sous `src/test/resources`**.
- En production, **la clé publique est fournie par variable d'environnement**. Si cette variable est absente ou invalide, **le démarrage échoue**. Il n'existe aucune clé de repli.
- Aucune clé privée ne se trouve dans le dépôt, ni en test ni en production : Sentinelle ne signe pas de jetons.

**Rôles.** Trois rôles sont définis :

| Rôle | Surface | Droits |
|---|---|---|
| `DISTRIBUTEUR` | gRPC (écriture) | Créer et modifier **ses propres** déclarations de rupture. |
| `PHARMACIEN` | REST (lecture) | Consulter les ruptures et l'indice de tension. |
| `PRESCRIPTEUR` | REST (lecture) | Consulter les ruptures et l'indice de tension. |

**Identité.** L'identité de l'appelant est **uniquement** celle portée par le JWT (sujet, identifiant de distributeur, rôle). Un champ `distributeurId` ou équivalent présent dans un message gRPC ou une requête REST est **ignoré pour l'identité** ; il n'est jamais utilisé comme source d'autorité.

**Contrôle de propriété.** Le contrôle « un distributeur ne modifie que ses déclarations » est réalisé **dans la couche application et le domaine**, et non dans les adaptateurs. Les cas d'usage reçoivent un **Acteur immuable** (identifiant, rôles) construit à partir du jeton validé. La règle de propriété est une règle métier : elle s'exprime dans le domaine, se teste sans infrastructure, et ne peut pas être oubliée par un nouvel adaptateur.

**Validation commune.** La même validation de jeton s'applique aux deux surfaces : un **intercepteur gRPC** pour les appels d'écriture et un **filtre REST** pour les appels de lecture. Ils délèguent au même composant de validation (package `security`) et produisent le même Acteur.

## Alternatives écartées

**Sessions serveur.** Elles imposent un état partagé entre instances, une gestion d'expiration côté serveur et une invalidation explicite. Elles ne se marient pas bien avec gRPC et ne sont pas vérifiables hors ligne par les systèmes appelants.

**OAuth2 complet avec serveur d'autorisation.** Un serveur d'autorisation (flux d'émission, gestion des clients, consentement) est hors périmètre : Sentinelle consomme des jetons émis par l'AFFSN, il ne les émet pas. Le service se limite donc à la validation.

**Contrôle de propriété dans l'adaptateur.** Placer la règle « propriétaire de la déclaration » dans l'intercepteur gRPC ou le contrôleur REST la rendrait dépendante du transport. Un second adaptateur pourrait l'omettre, et la règle ne serait pas testable sans démarrer le transport. Elle reste donc dans le domaine.

## Conséquences

- Le démarrage en production échoue sans clé publique : c'est voulu. Une erreur de configuration se voit immédiatement, pas à la première requête.
- La clé de test est committée volontairement. Elle ne doit servir à rien d'autre qu'aux tests ; toute réutilisation hors `src/test` est une faute à signaler.
- Le rôle et l'identité sont portés par le jeton : révoquer un accès avant expiration suppose des jetons de durée courte. La durée de vie est fixée dans la configuration.
- La règle de propriété est testée unitairement dans le domaine, et le câblage des deux surfaces est testé séparément, ce qui évite de tester deux fois la même règle.
