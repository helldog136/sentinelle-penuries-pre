# Démo Claude Code — délégation agentique

Oct 21, 2026 · @Helldog

## Le projet fictif : Sentinelle

Sentinelle est le service de signalement des pénuries de médicaments de l'Agence Fédérale Fictive de Santé Numérique (AFFSN) de la Valdarie, un pays imaginaire. Le contexte ressemble au vôtre (plateforme publique, acteurs de santé, contrats protobuf, batch de nuit), mais il ne contient aucune donnée patient et aucun nom réel. Devise officieuse de l'agence : « Chez nous, même les pénuries sont bien documentées. »

| Composant | Rôle | Techno |
| --- | --- | --- |
| Ingestion | Les distributeurs déclarent, mettent à jour et clôturent une rupture | gRPC + protobuf |
| Consultation | Les pharmaciens et prescripteurs voient les ruptures en cours et les alternatives | REST, OpenAPI |
| Batch nocturne | Calcule un indice de tension par région et produit un fichier de notifications | Spring Batch |
| Persistance | Stocke les ruptures, les alternatives et l'audit | PostgreSQL en cible, H2 en test, Flyway |
| Sécurité | Rôles DISTRIBUTEUR, PHARMACIEN, PRESCRIPTEUR | JWT signé avec une clé de test |
| Audit | Journal immuable de chaque déclaration et modification | Table en ajout seul |

Les identifiants sont fictifs : code produit `VMP-123456`, identifiant professionnel `PRO-123456`. La stack est Java 21, Spring Boot 3, Maven wrapper, en architecture hexagonale.

La Valdarie compte six régions : la Haute-Paperasse, la Basse-Validation, le Plateau du Legacy, la Côte du Timeout, le Marais de la Recette et la Province du Ticket Ouvert. L'humour reste dans les noms, les données et les messages de suivi : le code, lui, reste sérieux.

## Avant la démo

Fais une répétition complète ce soir sur un repo jetable : c'est elle qui te dira si le bac à sable cloud a Java 21 et accède à Maven Central, et combien de temps prend chaque vague.

- [ ] Créer le repo public vide (par exemple `sentinelle-penuries`) et le connecter dans Claude Code sur le web.
- [ ] Répéter les 8 prompts sur un repo de répétition, et noter la durée de chaque vague.
- [ ] Garder ce repo de répétition terminé comme plan B, à montrer si le live rame ou plante.
- [ ] Ne pas compter sur Docker dans le bac à sable : H2 pour les tests, pas de Testcontainers.
- [ ] Préparer pendant les vagues longues ce que tu racontes : le tableau de délégation, les commits rouges puis verts, `docs/DELEGATION.md`.
- [ ] Avoir sous la main la commande qui montre qui a fait quoi : `git log --format='%h %s%n    %(trailers:key=Delegated-to,valueonly)'`.

Le fil rouge pour les architectes tient en trois axes : la délégation dimensionnée, la sécurité, et les tests. Les tests sont montrés de deux façons. En TDD, le commit de test rouge précède le commit d'implémentation. En rétroactif, un agent qui n'a pas écrit le code ajoute les tests après coup, et on mesure ce qu'ils couvrent vraiment.

## Prompt 1 — Rôle de l'orchestrateur

Ce prompt fixe les règles du jeu pour toute la session : l'agent principal ne code jamais, il découpe, délègue au bon modèle et contrôle.

```text
Tu es le tech lead d'une démo de développement agentique. Règles valables pour toute la session :

1. Tu n'écris jamais de code toi-même. Ton rôle : comprendre le besoin, découper, déléguer, vérifier, intégrer, committer.
2. Chaque action de développement est confiée à un sous-agent dont le modèle dépend de la difficulté :
   - haiku : tâches mécaniques et bien bornées (scaffolding, DTO, configuration, données de test, README) ;
   - sonnet : implémentation standard à logique claire (services, contrôleurs, repositories, tests unitaires) ;
   - opus : conception et tâches difficiles (contrats d'API, modèle de domaine, sécurité, batch, revues).
3. Avant chaque vague, affiche un tableau : tâche | sous-agent | modèle | justification | fichiers touchés. Lance ensuite en parallèle toutes les tâches indépendantes.
4. Deux sous-agents d'une même vague ne touchent jamais le même fichier. Un fichier partagé (pom.xml, application.yml) fait l'objet d'une tâche à part, avant ou après la vague.
5. Les sous-agents ne committent pas. À la fin de chaque vague, tu vérifies (build + tests), puis tu fais un commit par tâche, au format Conventional Commits, avec ce pied de message :
   Delegated-to: <rôle du sous-agent> (<modèle>)
6. Tests : chaque lot est fait soit en TDD, soit en code d'abord, selon ce que je demande. En TDD, les tests sont committés seuls et rouges ("test: ... [red]") avant le commit d'implémentation qui les rend verts.
7. Sécurité : aucun secret réel dans le repo, uniquement des clés de test générées et marquées comme telles. Toute entrée externe est validée.
8. Tu tiens à jour docs/DELEGATION.md : une ligne par tâche (vague, tâche, modèle, mode TDD ou rétroactif, résultat).
9. Tout est fictif : aucun nom réel d'organisme, de registre, de nomenclature ou d'identifiant de santé. Uniquement les noms que je te donne.

10. Ton : rigueur totale sur le code, mais l'humour est bienvenu dans tes points d'avancement, les README et les données de démo. Chaque sous-agent porte un surnom selon son modèle : haiku = « le Stagiaire survitaminé », sonnet = « le Senior pragmatique », opus = « l'Architecte qui a tout vu ». Le pied de commit devient : Delegated-to: <surnom> (<modèle>).

Confirme en 5 lignes maximum, sans rien créer pour l'instant.
```

## Prompt 2 — Brief métier et plan

C'est le moment de laisser les architectes contester le plan, avant que la moindre ligne soit écrite.

```text
Voici le projet. Ne code rien : produis d'abord le plan.

Contexte (fictif) : l'Agence Fédérale Fictive de Santé Numérique (AFFSN) de la Valdarie, pays imaginaire, exploite une plateforme de microservices pour les acteurs de santé. On construit "Sentinelle", le service de signalement des pénuries de médicaments.

Géographie : six régions fictives, Haute-Paperasse, Basse-Validation, Plateau du Legacy, Côte du Timeout, Marais de la Recette et Province du Ticket Ouvert.

Besoins :
- Via gRPC, un distributeur déclare la rupture d'un produit (code VMP-123456) : date de début, date de fin estimée, motif (PRODUCTION, LOGISTIQUE, REGLEMENTAIRE, DEMANDE), régions touchées, alternatives éventuelles. Il peut la mettre à jour ou la clôturer.
- Via REST, les pharmaciens et prescripteurs consultent les ruptures en cours (filtres par région et par produit), le détail d'une rupture et les alternatives d'un produit.
- Chaque nuit, un job Spring Batch recalcule un indice de tension par région (ruptures actives pondérées par leur durée). Il produit un fichier de notifications pour les régions dont l'indice dépasse un seuil configurable.
- Chaque déclaration et modification est tracée dans un journal d'audit immuable.
- Sécurité : JWT signé avec une clé de test, trois rôles DISTRIBUTEUR, PHARMACIEN, PRESCRIPTEUR. Un distributeur (identifiant PRO-123456) ne peut modifier que ses propres déclarations.
- Aucune donnée patient.

Contraintes : Java 21, Spring Boot 3, Maven wrapper, gRPC + protobuf, Spring Batch, Flyway, PostgreSQL en cible mais H2 pour les tests (pas de Docker), architecture hexagonale (domaine sans dépendance Spring), JUnit 5, rapport de couverture JaCoCo.

Livrable attendu maintenant :
1. Un découpage en vagues (fondations, implémentation parallèle, intégration), avec le tableau de délégation de chaque vague.
2. Les dépendances entre tâches et ce qui tourne en parallèle.
3. Pour chaque lot, ta recommandation TDD ou code d'abord, et pourquoi.
4. Les menaces de sécurité principales du service, en 5 lignes.
5. Les 3 décisions d'architecture à consigner en ADR.

Attends ma validation avant de lancer quoi que ce soit.
```

## Prompt 3 — Vague 1 : fondations et contrats

Les contrats et le domaine passent en premier : c'est ce qui permet ensuite aux sous-agents de travailler en parallèle sans se marcher dessus. Point à souligner : CLAUDE.md, c'est de la gouvernance exécutable.

```text
Plan validé. [Intègre ici les remarques des architectes, s'il y en a.]

Lance la vague 1 — fondations. Elle pose tout ce dont la vague 2 a besoin pour travailler en parallèle :
- squelette Maven structuré selon ton plan, Maven wrapper, .gitignore, application.yml, JaCoCo configuré ;
- CLAUDE.md à la racine : conventions, règles de couches, commandes de build et de test, règles TDD, règles de sécurité, règle « tout est fictif » ;
- contrats : le .proto du service d'ingestion et la spécification OpenAPI de la consultation ;
- le modèle de domaine (entités, value objects, ports) sans dépendance Spring, développé en TDD : les règles métier (dates cohérentes, transitions d'état d'une rupture, propriété d'une déclaration) sont d'abord écrites en tests rouges et committées seules ;
- les ADR dans docs/adr/.

Choisis le modèle de chaque sous-agent selon la règle et justifie-le dans le tableau.
À la fin : build vert (./mvnw -q verify), un commit par tâche (test rouge puis implémentation pour le domaine), push, et un résumé en 5 lignes.
```

## Prompt 4 — Vague 2 : implémentation en parallèle

Sept sous-agents tournent en même temps. Les lots critiques sont faits en TDD. Deux lots sont volontairement codés sans tests, pour la démonstration du rétroactif au prompt 5.

```text
Vague 2 : implémentation en parallèle, sur la base des contrats et du domaine de la vague 1, sans les modifier. Si un sous-agent estime qu'un contrat doit changer, il s'arrête et te le signale : c'est toi qui tranches, pas lui.

Lots indépendants, un sous-agent chacun, tous lancés en même temps :
1. [TDD] Sécurité : validation du JWT, rôles, règle « un distributeur ne modifie que ses déclarations ». Tests d'abord, y compris les cas d'attaque : jeton expiré, signature invalide, mauvais rôle, déclaration d'un autre distributeur.
2. [TDD] Adaptateur gRPC d'ingestion (déclarer, mettre à jour, clôturer), avec la validation des entrées et les bons codes de statut gRPC.
3. [TDD] API REST de consultation conforme à l'OpenAPI, tests MockMvc, codes HTTP et erreurs au format problem+json.
4. [CODE D'ABORD] Job Spring Batch : indice de tension et fichier de notifications. Pas de tests dans ce lot.
5. [CODE D'ABORD] Journal d'audit immuable. Pas de tests dans ce lot.
6. Persistance : migrations Flyway et adaptateurs JPA des ports, avec tests sur H2.
7. Jeu de données de démo fictif et drôle : 30 produits VMP aux noms improbables (du style « Patientol 500 mg » ou « Sirop du Lundi Matin »), 8 distributeurs du genre « Logistique Escargot SRL », et les 6 régions de la Valdarie. Plus un README de démarrage avec une touche d'humour, sans rien perdre en clarté.

Affiche le tableau de délégation avant de lancer, puis un point d'avancement court à chaque sous-agent terminé.
Commits : pour les lots TDD, le commit de tests rouges d'abord, puis celui d'implémentation.
Quand tout est fini, ne fais pas encore l'intégration : liste ce qui a été produit et les signalements remontés.
```

## Prompt 5 — Intégration et tests rétroactifs

Ce prompt montre la deuxième façon de tester : des agents qui n'ont pas écrit le code y ajoutent des tests après coup. La couverture est mesurée avant et après, et les tests de mutation vérifient que ces tests attrapent vraiment des bugs.

```text
Vague 3 : intégration et tests rétroactifs.

1. Lance le build complet et note la couverture JaCoCo actuelle, globale et par package.
2. Tests rétroactifs : lance en parallèle deux sous-agents qui n'ont pas écrit le code, l'un sur le job batch, l'autre sur le journal d'audit. Chacun :
   - lit le code existant et en déduit le comportement attendu ;
   - écrit les tests : cas nominaux, cas limites, erreurs, idempotence et reprise pour le batch, impossibilité de modifier ou supprimer une entrée pour l'audit ;
   - signale tout bug découvert par un test, sans le corriger lui-même.
3. Pour chaque bug signalé et chaque échec de build, délègue la correction à un sous-agent dimensionné selon la difficulté du problème, pas selon la taille du fichier. Justifie ton choix en une ligne.
4. Ajoute un test de bout en bout : un distributeur déclare une rupture via gRPC, un pharmacien la voit via REST, le batch recalcule l'indice de la région.
5. Recommence jusqu'au vert. Puis donne la couverture avant/après, par package.
6. [Optionnel] Lance des tests de mutation (PIT) sur le batch et l'audit, et donne le score.
7. Commits séparés (« test: retroactive tests for ... » puis « fix: ... »), push, mise à jour de docs/DELEGATION.md.
```

## Prompt 6 — Revues d'architecture et de sécurité indépendantes

Deux relecteurs qui n'ont rien écrit travaillent en parallèle, pour que le code ne soit pas évalué par celui qui l'a produit. Les failles de sécurité se corrigent en TDD : on écrit d'abord le test qui exploite la faille.

```text
Lance en parallèle deux sous-agents opus qui n'ont rien écrit dans ce projet. Ils ne corrigent rien. Ils sont aussi indulgents qu'un architecte à qui l'on présente un microservice qui partage sa base de données avec trois autres.

Relecteur architecture → docs/reviews/architecture-1.md
- étanchéité du domaine (aucune dépendance Spring, JPA ou gRPC) ;
- respect des contrats .proto et OpenAPI ;
- gestion des erreurs et des codes de retour gRPC et HTTP ;
- idempotence et reprise du batch ;
- écarts entre les ADR et le code.

Relecteur sécurité → docs/reviews/securite-1.md
- contrôle d'accès : un rôle ou un distributeur peut-il agir hors de son périmètre ?
- validation des entrées gRPC et REST, injection, tailles et formats ;
- gestion du JWT (algorithme, expiration, clé) et secrets présents dans le repo ;
- immuabilité réelle du journal d'audit ;
- fuites d'information dans les messages d'erreur et les logs ;
- dépendances Maven connues pour être vulnérables, si tu peux le vérifier.

Chaque constat a une gravité (bloquant, majeur, mineur) et une preuve (fichier et ligne).

Ensuite, toi : pour chaque constat bloquant ou majeur, délègue une correction en TDD. Le sous-agent écrit d'abord le test qui démontre le problème (commit rouge), puis corrige (commit vert). Push et mise à jour de docs/DELEGATION.md.
```

## Prompt 7 — Changement de règle métier en live

Le meilleur moment de la démo, c'est quand les architectes inventent eux-mêmes le changement. Le texte ci-dessous est la version de secours. Le changement suit une analyse d'impact puis du TDD strict.

```text
Changement réglementaire (fictif) : le ministre valdarien de la Santé a vu un reportage hier soir. Désormais, la déclaration d'une rupture de plus de 30 jours d'un produit marqué « essentiel » doit proposer au moins une alternative, sinon elle est refusée. Et dans l'indice de tension, les produits essentiels comptent double.

1. Avant toute modification, fais l'analyse d'impact : contrats, domaine, adaptateurs, batch, sécurité, tests existants, données de démo.
2. Dis-moi si un contrat doit évoluer, et comment garder la compatibilité ascendante pour les clients gRPC et REST existants.
3. Propose le découpage avec le tableau de délégation.
4. Développement en TDD strict : les nouveaux tests d'acceptation sont committés rouges avant toute implémentation.

Attends mon « go » avant l'étape 4.
```

Ensuite, tape simplement : `Go.`

## Prompt 8 — Bilan pour les architectes

Ce dernier prompt laisse dans le repo une synthèse que tes collègues pourront relire tranquillement après la réunion.

```text
Produis docs/BILAN-DEMO.md à destination d'architectes logiciels :
- un tableau des vagues : tâches, modèle utilisé, nombre de sous-agents en parallèle ;
- la répartition des tâches par modèle, et pourquoi ;
- les tests : lots en TDD et lots en rétroactif, couverture avant et après, bugs trouvés par les tests rétroactifs, score de mutation s'il a été mesuré ;
- la sécurité : constats de la revue, leur gravité et leur statut ;
- ce qui a échoué en route et comment ça a été corrigé ;
- une estimation du coût de la session : tokens consommés par vague et par modèle (entrée et sortie, en reprenant les compteurs remontés par chaque sous-agent), puis le coût en euros calculé avec les tarifs publics actuels de l'API Anthropic (indique les tarifs et le taux USD→EUR utilisés). Ajoute une estimation du temps qu'aurait demandé le même travail à un développeur, en indiquant clairement que ce sont des ordres de grandeur ;
- les limites que tu vois toi-même avant une mise en production ;
- pour conclure, une phrase que l'équipe pourrait imprimer sur un mug.

Commit, push, puis donne-moi le lien du repo et les commits à regarder en premier pour voir un cycle TDD rouge → vert.
```

## Prompts de secours

À coller tels quels si la démo dérape. Et si tout dérape vraiment : « Explique au public, avec dignité, ce qui vient de se passer. »

| Situation | Prompt |
| --- | --- |
| Un sous-agent tourne trop longtemps | `Où en est chaque sous-agent ? Si un lot est bloqué depuis plus de 5 minutes, arrête-le, découpe-le en deux tâches plus petites et relance.` |
| L'orchestrateur se met à coder | `Rappel de la règle 1 : tu ne codes pas. Annule ta modification et délègue-la.` |
| Le build échoue pour une raison réseau | `Le build échoue à cause du réseau. Identifie le dépôt inaccessible et propose une alternative sans changer l'architecture.` |
| Deux lots ont touché le même fichier | `Deux lots ont modifié le même fichier. Montre le conflit, tranche, et ajoute à CLAUDE.md la règle qui l'aurait évité.` |
| Un test TDD est vert dès le départ | `Ce test était vert avant l'implémentation : il ne teste rien. Fais-le réécrire pour qu'il échoue sur le code actuel.` |
| Un architecte challenge un choix | `Explique au groupe, en 5 lignes, pourquoi tu as choisi ce modèle pour cette tâche et ce que tu aurais perdu avec un autre.` |
| Le temps manque | `On a 10 minutes. Termine la vague en cours, pousse ce qui est vert, et liste le reste dans docs/RESTE-A-FAIRE.md.` |

## Liens à envoyer aux architectes

Ces trois repos publics, codés avec Claude, complètent la démo : on peut y lire tout l'historique des commits.

| Repo | Lien |
| --- | --- |
| Sentinelle (repo de la démo) | <https://github.com/helldog136/sentinelle-penuries-pre> |
| Open Loupedeck | [github.com/helldog136/open-loupedeck](https://github.com/helldog136/open-loupedeck) |
| Curiosa | [github.com/helldog136/Curiosa](https://github.com/helldog136/Curiosa) |
| Curiosa extras | [github.com/helldog136/curiosa-extras](https://github.com/helldog136/curiosa-extras) |
