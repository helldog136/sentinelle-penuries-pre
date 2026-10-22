# Métriques des sous-agents, session Sentinelle (analyse par script)

Sources : transcript principal (1962 lignes), 53 transcripts de sous-agents (`.../subagents/agent-*.jsonl` + `.meta.json`), blocs `cost-state`. Tout chiffre ci-dessous est MESURÉ sauf mention « estimé ».

## 0. Lire avant : trois pièges de mesure

1. **`<subagent_tokens>` des task-notifications n'est PAS un cumul consommé.** Il vaut à peu près la taille du contexte au dernier tour (entrée + cache lecture + cache écriture + sortie du dernier message ; vérifié sur ~50 agents : écart de quelques centaines à quelques milliers de tokens). Il est donc non additif entre agents comme mesure de consommation. J'ai utilisé à la place la somme des `message.usage` des transcripts par sous-agent. La valeur de la notification figure dans le JSON (`final_context_tokens_notification`).
2. **Sorties (`output_tokens`) des sous-agents du JOUR 1 (2026-10-21, claude-code v2.1.306) sous-comptées** : médiane 8 tokens par message, 26 % des messages à ≤5 tokens (valeurs de type instantané de début de flux). Le jour 2 (v2.1.308, vagues A/B) les sorties sont plausibles (médiane 454, 0 % ≤5). L'orchestrateur est cohérent les deux jours. Entrée, cache lecture et cache écriture ne sont pas concernés : leur somme par modèle égale EXACTEMENT les totaux `cost-state` du harness pour haiku et opus (vérifié, voir §6).
3. Sorties jour 1 : borne basse seulement par agent et par vague. Le total par modèle est en revanche exact (différence de `cost-state`, §6) : sous-agents jour 1 = haiku 227 856, opus 503 959, sonnet ≈ 286 513 (dérivé). Contre-épreuve : pour le jour 2, la somme des sorties des fichiers égale exactement l'écart de `cost-state` (haiku +23 521, opus +168 130), donc les sorties du jour 2 sont exactes.

## 1. Totaux par modèle (sous-agents)

| Modèle | Agents | Entrée (non cachée) | Sortie (fichiers, borne basse) | Cache lecture | Cache écriture | Durée cumulée (s, notif.) |
|---|---|---|---|---|---|---|
| haiku (`claude-haiku-5-5`) | 14 | 428 | 39 844 | 20 251 451 | 1 147 728 | 2 623 |
| sonnet (`claude-sonnet-5-5`) | 23 | 682 | 118 803 | 35 205 475 | 2 008 385 | 9 650 |
| opus (`claude-opus-5-5`) | 16 | 720 | 176 346 | 41 390 231 | 2 422 571 | 10 815 |
| **Total** | 53 | 1 830 | 334 993 | 96 847 157 | 5 578 684 | 23 089 |

Modèle réel = `message.model` des transcripts ; il coïncide avec le modèle demandé (`haiku`/`sonnet`/`opus`) pour les 53 agents.

Sortie corrigée par modèle, sous-agents seuls (jour 1 via cost-state + jour 2 des fichiers) : haiku 251 377 ; opus 672 089 ; sonnet ≈ 286 513 (jour 1, dérivé) + 73 479 (jour 2, fichiers) + environ 35 500 (M1/P1, fichiers, M1 non terminé, valeur mouvante) ≈ 395 500. Total harness sonnet (orchestrateur inclus) : 694 529 à 09:41:32Z. Voir §6.

## 2. Par vague

Parallélisme = recouvrement des intervalles lancement (tool_use) → notification « finished » (et SendMessage → notification pour les agents repris) ; mesuré, tous les timestamps existent sauf S0 (appel synchrone, intervalle pris dans son transcript) et M1 (non terminé). « Même message » = nombre d'appels Agent dans un même message assistant.

| Vague | Agents | haiku/sonnet/opus | Parallèle max (recouvrement) | Lancés dans le même message (max) | Début → fin (UTC) | Durée murale | Durée cumulée agents |
|---|---|---|---|---|---|---|---|
| 0a | 4 | 2/0/2 | 4 | 4 | 10-21T11:52 → 10-21T12:01 | 9 min 45 s | 19 min |
| 0a-bis | 2 | 1/1/0 | 2 | 2 | 10-21T12:02 → 10-21T12:04 | 1 min 47 s | 2 min |
| 1' | 4 | 1/2/1 | 4 | 4 | 10-21T12:07 → 10-21T12:11 | 3 min 12 s | 7 min |
| 2 | 9 | 2/4/3 | 7 | 7 | 10-21T12:15 → 10-21T13:07 | 52 min 24 s | 144 min |
| 2' | 3 | 2/1/0 | 2 | 2 | 10-21T12:33 → 10-21T12:41 | 7 min 59 s | 7 min |
| 3 | 15 | 2/8/5 | 4 | 3 | 10-21T13:10 → 10-21T14:50 | 99 min 40 s | 130 min |
| A | 5 | 1/2/2 | 5 | 5 | 10-22T09:12 → 10-22T09:18 | 6 min 11 s | 15 min |
| B (tests rouges) | 9 | 3/3/3 | 6 | 6 | 10-22T09:19 → 10-22T09:41 | 22 min 12 s | 63 min |
| hors vagues (instrumentation M1/P1) | 2 | 0/2/0 | 2 | 2 | 10-22T09:55 → 10-22T10:01 | 5 min 55 s | 7 min |

Parallélisme maximal global (toutes vagues) : **7** (vague 2, lots 1 à 7). Les vagues 2 et 2′ se chevauchent un peu dans le temps, les autres non (vague 3 : 4 en parallèle max, jamais plus de 3 lancés dans le même message).

### Tokens par vague (sous-agents ; sortie = borne basse pour les vagues du jour 1)

| Vague | Entrée | Sortie | Cache lecture | Cache écriture | Détail par modèle (agents ; sortie ; cache écriture) |
|---|---|---|---|---|---|
| 0a | 106 | 510 (sous-compté) | 3 809 616 | 365 191 | haiku: 2 ag, 190, 161 935; opus: 2 ag, 320, 203 256 |
| 0a-bis | 36 | 153 (sous-compté) | 1 011 902 | 134 517 | haiku: 1 ag, 106, 70 585; sonnet: 1 ag, 47, 63 932 |
| 1' | 52 | 411 (sous-compté) | 1 623 601 | 190 646 | haiku: 1 ag, 31, 24 471; sonnet: 2 ag, 154, 59 910; opus: 1 ag, 226, 106 265 |
| 2 | 628 | 9 076 (sous-compté) | 39 731 792 | 1 655 377 | haiku: 2 ag, 1 174, 265 287; sonnet: 4 ag, 2 357, 769 193; opus: 3 ag, 5 545, 620 897 |
| 2' | 50 | 10 459 (sous-compté) | 1 722 301 | 121 731 | haiku: 2 ag, 10 334, 98 334; sonnet: 1 ag, 125, 23 397 |
| 3 | 474 | 8 042 (sous-compté) | 26 701 637 | 1 581 311 | haiku: 2 ag, 4 488, 217 679; sonnet: 8 ag, 1 429, 455 511; opus: 5 ag, 2 125, 908 121 |
| A | 82 | 56 606 | 2 536 968 | 416 675 | haiku: 1 ag, 4 639, 67 377; sonnet: 2 ag, 13 121, 142 505; opus: 2 ag, 38 846, 206 793 |
| B (tests rouges) | 342 | 208 524 | 16 960 700 | 842 789 | haiku: 3 ag, 18 882, 242 060; sonnet: 3 ag, 60 358, 223 490; opus: 3 ag, 129 284, 377 239 |
| hors vagues (instrumentation M1/P1) | 60 | 41 212 | 2 748 640 | 270 447 | sonnet: 2 ag, 41 212, 270 447 |

### Tâches par vague

- **0a** : T0.1 Scaffolding Maven [haiku] ; T0.2 Domaine et tests rouges [opus] ; T0.3 Contrats proto et OpenAPI [opus] ; T0.4 ADR et documentation [haiku]
- **0a-bis** : F1 Aligner statut EN_COURS contrats [haiku] ; F2 Alternatives par code produit [sonnet]
- **1'** : P1 Implémenter le domaine (vert) [opus] ; P2 Schéma Flyway V1 à V3 [sonnet] ; P3 Test ArchUnit hexagonal [sonnet] ; P4 Aligner contrats sur 280 [haiku]
- **2** : S0 Clé de contexte gRPC partagée [haiku] ; Lot 1 Sécurité JWT (TDD phase A) [opus] ; Lot 2 Adaptateur gRPC (TDD phase A) [sonnet] ; Lot 3 API REST (TDD phase A) [sonnet] ; Lot 4 Job Spring Batch tension [opus] ; Lot 5 Journal d'audit immuable [opus] ; Lot 6 Persistance JPA et tests H2 [sonnet] ; Lot 7 Données de démo et README [haiku] ; Lot 6 Persistance JPA (relance) [sonnet]
- **2'** : C1 Commande de mise à jour sans Periode [sonnet] ; C2 Corriger les contrats proto et OpenAPI [haiku] ; C3 Cohérence documentaire et contrats [haiku]
- **3** : R1 Tests rétroactifs du job batch [opus] ; R2 Tests rétroactifs du journal d'audit [opus] ; S1 Services applicatifs (TDD phase A) [sonnet] ; P5 pom : exclusion JaCoCo et PIT [sonnet] ; B1 B2 Corriger fourche d'audit REQUIRES_NEW [opus] ; B3 Corriger assainissement du résumé d'audit [sonnet] ; B4 Durcissement du job batch [sonnet] ; W1 Câblage Spring, horloge et transactions [sonnet] ; E1 Test de bout en bout gRPC REST batch [opus] ; B5 Corriger les 500 REST en 404 405 406 [sonnet] ; B6 Conflit de verrou optimiste en ABORTED [sonnet] ; D1 Mise à jour de la documentation [haiku] ; P6 Corriger la portée PIT dans le pom [sonnet] ; D2 Hygiène : gitignore, commentaire batch, type d'erreur [haiku] ; F1 Rendre le test d'interblocage déterministe [opus]
- **A** : A0 ADR-004 règle essentiel [opus] ; A1 contrats additifs proto/OpenAPI [opus] ; A2 squelettes domaine/application [sonnet] ; A3 migration V4 référentiel [sonnet] ; A4 propriété date entrée en vigueur [haiku]
- **B (tests rouges)** : B0 aligner nom propriété [haiku] ; B1 tests règle pure et pondération [opus] ; B2 tests services déclarer/mettre à jour [opus] ; B3 tests gRPC REST référentiel [sonnet] ; B4 tests batch tension essentiels [sonnet] ; B5 tests e2e compat et ArchUnit [opus] ; B3 tests gRPC REST référentiel (relance) [sonnet] ; B0 aligner nom propriété (relance) [haiku] ; Fix collision stubs adapter/batch [haiku]
- **hors vagues (instrumentation M1/P1)** : M1 compteurs tokens par agent [sonnet] ; P1 tarifs API et taux EUR [sonnet]

Rattachement aux vagues : 0a, 0a-bis, 1′, 2, 2′, 3 = `docs/DELEGATION.md` (concordance exacte des tâches et modèles ; S0 vague 2, C1-C3 vague 2′). A (A0-A4 : ADR-004, contrats, squelettes, migration, propriété) et B (B0-B5 tests rouges, relances, correctif de collision de stubs) = ordre chronologique et messages de commit (`[stubs]`, `[red]`) ; ces deux vagues ne sont PAS dans DELEGATION.md. Le « Fix collision stubs adapter/batch » est rattaché à B par ma décision (supposition). M1 (compteurs tokens) et P1 (tarifs API) sont hors vagues.

## 3. Détail par sous-agent (ordre de lancement)

Colonnes : E = entrée non cachée, S = sortie (✱ = sous-comptée, jour 1), CL = cache lecture, CE = cache écriture, Ctx = `subagent_tokens` de la dernière notification (taille de contexte final, pas un cumul), Durée = somme des `duration_ms` des notifications « finished ».

| Vague | Tâche | Modèle demandé → réel | Isolation | E | S | CL | CE | Ctx notif. | Durée (s) | Notes |
|---|---|---|---|---|---|---|---|---|---|---|
| 0a | T0.1 Scaffolding Maven | haiku → haiku-5-5 | aucune | 36 | 150✱ | 1 274 619 | 92 671 | 94 382 | 234 |  |
| 0a | T0.2 Domaine et tests rouges | opus → opus-5-5 | aucune | 36 | 179✱ | 1 566 779 | 120 336 | 123 054 | 541 |  |
| 0a | T0.3 Contrats proto et OpenAPI | opus → opus-5-5 | aucune | 24 | 141✱ | 720 497 | 82 920 | 85 144 | 246 |  |
| 0a | T0.4 ADR et documentation | haiku → haiku-5-5 | aucune | 10 | 40✱ | 247 721 | 69 264 | 70 240 | 70 |  |
| 0a-bis | F1 Aligner statut EN_COURS contrats | haiku → haiku-5-5 | aucune | 26 | 106✱ | 775 677 | 70 585 | 71 391 | 101 |  |
| 0a-bis | F2 Alternatives par code produit | sonnet → sonnet-5-5 | aucune | 10 | 47✱ | 236 225 | 63 932 | 64 999 | 53 |  |
| 1' | P1 Implémenter le domaine (vert) | opus → opus-5-5 | aucune | 20 | 226✱ | 736 562 | 106 265 | 108 040 | 166 |  |
| 1' | P2 Schéma Flyway V1 à V3 | sonnet → sonnet-5-5 | aucune | 12 | 106✱ | 345 935 | 32 584 | 71 001 | 139 |  |
| 1' | P3 Test ArchUnit hexagonal | sonnet → sonnet-5-5 | aucune | 8 | 48✱ | 213 020 | 27 326 | 65 282 | 65 |  |
| 1' | P4 Aligner contrats sur 280 | haiku → haiku-5-5 | aucune | 12 | 31✱ | 328 084 | 24 471 | 61 896 | 37 |  |
| 2 | S0 Clé de contexte gRPC partagée | haiku → haiku-5-5 | aucune | 22 | 291✱ | 675 674 | 72 204 | — | — | aucune notification de fin (appel synchrone) |
| 2 | Lot 1 Sécurité JWT (TDD phase A) | opus → opus-5-5 | worktree | 100 | 1 040✱ | 6 522 610 | 401 948 | 221 007 | 1395 | repris 1× (SendMessage) |
| 2 | Lot 2 Adaptateur gRPC (TDD phase A) | sonnet → sonnet-5-5 | worktree | 216 | 1 369✱ | 17 808 581 | 370 911 | 247 929 | 2020 | repris 2× (SendMessage) |
| 2 | Lot 3 API REST (TDD phase A) | sonnet → sonnet-5-5 | worktree | 50 | 561✱ | 2 114 885 | 122 800 | 123 968 | 388 | repris 1× (SendMessage) |
| 2 | Lot 4 Job Spring Batch tension | opus → opus-5-5 | worktree | 36 | 209✱ | 1 322 267 | 108 371 | 111 739 | 468 |  |
| 2 | Lot 5 Journal d'audit immuable | opus → opus-5-5 | worktree | 32 | 4 296✱ | 1 197 858 | 110 578 | 114 680 | 492 |  |
| 2 | Lot 6 Persistance JPA et tests H2 | sonnet → sonnet-5-5 | worktree | 12 | 64✱ | 290 215 | 60 651 | 61 379 | 34 | BLOQUÉ (rapporté « non réalisé »), relancé |
| 2 | Lot 7 Données de démo et README | haiku → haiku-5-5 | worktree | 128 | 883✱ | 8 635 918 | 193 083 | 193 854 | 706 | 2 notif. intermédiaire(s) « waiting on background » |
| 2 | Lot 6 Persistance JPA (relance) | sonnet → sonnet-5-5 | aucune | 32 | 363✱ | 1 163 784 | 214 831 | 111 340 | 2787 | 1 notif. intermédiaire(s) « waiting on background » |
| 2' | C1 Commande de mise à jour sans Periode | sonnet → sonnet-5-5 | aucune | 12 | 125✱ | 326 108 | 23 397 | 60 688 | 152 |  |
| 2' | C2 Corriger les contrats proto et OpenAPI | haiku → haiku-5-5 | aucune | 24 | 10 280✱ | 976 393 | 59 347 | 97 402 | 264 |  |
| 2' | C3 Cohérence documentaire et contrats | haiku → haiku-5-5 | aucune | 14 | 54✱ | 419 800 | 38 987 | 77 077 | 43 |  |
| 3 | R1 Tests rétroactifs du job batch | opus → opus-5-5 | aucune | 62 | 367✱ | 4 565 186 | 214 671 | 218 285 | 1067 |  |
| 3 | R2 Tests rétroactifs du journal d'audit | opus → opus-5-5 | aucune | 42 | 258✱ | 2 271 514 | 160 541 | 164 593 | 877 |  |
| 3 | S1 Services applicatifs (TDD phase A) | sonnet → sonnet-5-5 | aucune | 30 | 194✱ | 1 185 135 | 81 423 | 119 000 | 414 | repris 1× (SendMessage) |
| 3 | P5 pom : exclusion JaCoCo et PIT | sonnet → sonnet-5-5 | aucune | 10 | 56✱ | 282 303 | 30 358 | 68 424 | 116 |  |
| 3 | B1 B2 Corriger fourche d'audit REQUIRES_NEW | opus → opus-5-5 | aucune | 18 | 96✱ | 561 947 | 35 535 | 73 984 | 614 |  |
| 3 | B3 Corriger assainissement du résumé d'audit | sonnet → sonnet-5-5 | aucune | 18 | 162✱ | 487 499 | 36 746 | 74 375 | 689 | repris 1× (SendMessage) |
| 3 | B4 Durcissement du job batch | sonnet → sonnet-5-5 | aucune | 30 | 185✱ | 1 233 250 | 67 791 | 107 028 | 470 |  |
| 3 | W1 Câblage Spring, horloge et transactions | sonnet → sonnet-5-5 | aucune | 22 | 467✱ | 822 661 | 57 498 | 95 993 | 440 |  |
| 3 | E1 Test de bout en bout gRPC REST batch | opus → opus-5-5 | aucune | 72 | 819✱ | 7 474 412 | 300 594 | 304 033 | 1028 |  |
| 3 | B5 Corriger les 500 REST en 404 405 406 | sonnet → sonnet-5-5 | aucune | 16 | 180✱ | 442 420 | 67 673 | 68 775 | 179 |  |
| 3 | B6 Conflit de verrou optimiste en ABORTED | sonnet → sonnet-5-5 | aucune | 10 | 65✱ | 275 873 | 84 508 | 86 352 | 200 |  |
| 3 | D1 Mise à jour de la documentation | haiku → haiku-5-5 | aucune | 70 | 4 440✱ | 4 494 881 | 179 628 | 182 936 | 333 |  |
| 3 | P6 Corriger la portée PIT dans le pom | sonnet → sonnet-5-5 | aucune | 14 | 120✱ | 403 384 | 29 514 | 67 418 | 291 |  |
| 3 | D2 Hygiène : gitignore, commentaire batch, type d'erreur | haiku → haiku-5-5 | aucune | 16 | 48✱ | 498 539 | 38 051 | 75 877 | 48 |  |
| 3 | F1 Rendre le test d'interblocage déterministe | opus → opus-5-5 | aucune | 44 | 585✱ | 1 702 633 | 196 780 | 101 043 | 1269 |  |
| A | A0 ADR-004 règle essentiel | opus → opus-5-5 | worktree | 22 | 30 355 | 829 484 | 125 799 | 129 467 | 345 |  |
| A | A1 contrats additifs proto/OpenAPI | opus → opus-5-5 | worktree | 18 | 8 491 | 577 620 | 80 994 | 83 347 | 118 |  |
| A | A2 squelettes domaine/application | sonnet → sonnet-5-5 | worktree | 14 | 9 845 | 391 307 | 74 360 | 75 756 | 158 |  |
| A | A3 migration V4 référentiel | sonnet → sonnet-5-5 | worktree | 14 | 3 276 | 373 911 | 68 145 | 69 131 | 158 |  |
| A | A4 propriété date entrée en vigueur | haiku → haiku-5-5 | worktree | 14 | 4 639 | 364 646 | 67 377 | 68 417 | 81 |  |
| B (tests rouges) | B0 aligner nom propriété | haiku → haiku-5-5 | worktree | 14 | 3 645 | 386 831 | 79 434 | 80 603 | 42 | BLOQUÉ (rapporté « non réalisé »), relancé |
| B (tests rouges) | B1 tests règle pure et pondération | opus → opus-5-5 | worktree | 56 | 27 362 | 2 718 259 | 92 313 | 131 653 | 639 |  |
| B (tests rouges) | B2 tests services déclarer/mettre à jour | opus → opus-5-5 | worktree | 58 | 34 143 | 3 007 745 | 106 932 | 147 169 | 661 |  |
| B (tests rouges) | B3 tests gRPC REST référentiel | sonnet → sonnet-5-5 | worktree | 16 | 2 206 | 454 486 | 26 254 | 63 734 | 50 | BLOQUÉ (rapporté « non réalisé »), relancé |
| B (tests rouges) | B4 tests batch tension essentiels | sonnet → sonnet-5-5 | worktree | 50 | 35 218 | 2 421 342 | 109 239 | 148 575 | 464 | repris 1× (SendMessage) |
| B (tests rouges) | B5 tests e2e compat et ArchUnit | opus → opus-5-5 | worktree | 80 | 67 779 | 5 614 858 | 177 994 | 217 614 | 881 |  |
| B (tests rouges) | B3 tests gRPC REST référentiel (relance) | sonnet → sonnet-5-5 | aucune | 26 | 22 934 | 1 184 511 | 87 997 | 127 058 | 293 |  |
| B (tests rouges) | B0 aligner nom propriété (relance) | haiku → haiku-5-5 | aucune | 10 | 2 502 | 277 705 | 27 583 | 64 968 | 19 |  |
| B (tests rouges) | Fix collision stubs adapter/batch | haiku → haiku-5-5 | aucune | 32 | 12 735 | 894 963 | 135 043 | 78 806 | 640 |  |
| hors vagues (instrumentation M1/P1) | M1 compteurs tokens par agent | sonnet → sonnet-5-5 | aucune | 44 | 35 343 | 2 017 912 | 149 598 | — | — | aucune notification de fin (non terminé / sans retour) |
| hors vagues (instrumentation M1/P1) | P1 tarifs API et taux EUR | sonnet → sonnet-5-5 | aucune | 16 | 5 869 | 730 728 | 120 849 | 122 849 | 78 |  |

## 4. Orchestrateur (conversation principale, modèle `claude-sonnet-5-5`)

Par script sur les messages assistant du transcript principal, dédoublonnés par `message.id` (un message est écrit une fois par bloc de contenu avec le même usage), valeur max par champ.

| Périmètre | Appels API | Entrée | Sortie (dont réflexion) | Cache lecture | Cache écriture |
|---|---|---|---|---|---|
| Avant compaction (jour 1 et début jour 2, jusqu'à 09:06:37Z) | 184 | 461 | 274 942 (91 034) | 60 308 216 | 1 006 208 |
| Après compaction | 60 | 144 | 58 641 (16 233) | 9 474 627 | 165 242 |
| **Total transcript** | 244 | 605 | 333 583 (107 267) | 69 782 843 | 1 171 450 |
| Jusqu'à 09:41:32Z (comparaison cost-state) | 239 | 593 | 326 973 (104 582) | 68 674 382 | 1 156 585 |

Sortie de l'orchestrateur : jugée fiable (médiane 848 / 500 tokens par message, aucun message ≤5 tokens).

**Compaction et couverture** : un seul marqueur `compact_boundary` (ligne 1465, 2026-10-22T09:06:37Z, déclenchement manuel, `preTokens` 559 994, `postTokens` 18 251, `cumulativeDroppedTokens` 541 743) suivi d'un message de reprise (`isCompactSummary`). **L'historique d'avant compaction EST présent** dans le fichier : 1465 lignes avant le marqueur, dont 184 appels assistant, du premier message (2026-10-21T11:46:42Z) jusqu'à 09:06 . Le transcript couvre donc toute la session visible, jusqu'à 09:56:04Z. Les transcripts de sous-agents sont tous présents (53 lancements, 53 fichiers, appariés par `toolUseId`). Ce que le transcript ne contient pas : l'appel API qui a produit le résumé de compaction (son usage n'est pas journalisé) et d'éventuels appels auxiliaires (voir §6).

## 5. Reprises, échecs, interruptions

- **Bloqués puis relancés (3)** : Lot 6 (worktree créé sur le commit initial, 34 s, aucun fichier écrit) → relancé « Lot 6 (relance) » ; B0 (propriété absente de son worktree, 42 s) → relancé « (relance) » ; B3 (worktree sur un ancien merge, 50 s) → relancé « (relance) » ; leurs tokens sont comptés (coût de l'échec inclus).
- **Agents repris par SendMessage (7 envois, 6 agents)** : Lot 3 (1), Lot 2 (2), Lot 1 (1), S1 (1), B3 audit (1), B4 tests batch (1 envoi à 09:21:08 alors que l'agent tournait encore : une seule notification de fin, pas de reprise). Les notifications multiples d'un même agent n'ont pas été additionnées en tokens (les tokens viennent des transcripts, un fichier par agent, reprises incluses) ; les durées « finished » ont été additionnées, pas les notifications intermédiaires.
- **Notifications intermédiaires « waiting on its own background work »** : Lot 7 (2), Lot 6 relance (1) ; l'agent a ensuite fini (« finished »).
- **M1 « compteurs tokens par agent »** : aucun SubagentHandback ni notification dans le transcript lu ; son transcript s'arrête à 09:58:03Z. Considéré non terminé à la date du snapshot (ses tokens sont partiels). P1 « tarifs API » : terminé (09:57:11Z). Les deux sont hors vagues (instrumentation de ce suivi).
- Aucun `TaskStop`, aucune interruption explicite dans le transcript (aucun appel TaskStop). Les 53 notifications d'agents sont toutes `completed`. Seules deux notifications non-agent ont un autre statut : une commande d'arrière-plan « failed » (démarrage de l'app en profil demo, 12:26Z) et une « killed » (attente Maven, limite de temps, 13:07Z).
- 10 notifications `Monitor`/`Background command` (builds Maven, PIT) n'ont pas de `subagent_tokens` : ce ne sont pas des sous-agents, ignorées.

## 6. Trous et incertitudes (honnêtes)

- **Réconciliation avec `cost-state` (dernier, 09:41:32Z, coût total 62,71 USD)** : haiku : entrée/cache lecture/cache écriture identiques au token près ; opus : idem. Sonnet : cache écriture quasi égal (harness 2 921 047 vs fichiers 2 894 523) mais **écart inexpliqué** : cache lecture harness 115 208 695 vs 101 131 217 (≈14 M de plus), entrée 25 525 vs 1 215 (≈24 k de plus). Hypothèse NON vérifiée : appels sonnet non journalisés (classifieur de permissions du mode auto, résumé de compaction).
- **Sorties du jour 1 sous-comptées dans les fichiers** : fichiers sous-agents haiku 16 323, sonnet 4 112, opus 8 216 (bornes basses). Totaux exacts par modèle pour le jour 1 par différence de `cost-state` (14:57:37Z, avant tout agent du jour 2) : haiku 227 856, opus 503 959 (mesures), sonnet 286 513 (dérivé : cost-state moins sortie de l'orchestrateur du transcript, suppose celle-ci complète). Aucune ventilation par agent ou par vague n'est mesurable pour ces sorties ; je n'en invente pas.
- `cost-state` ne couvre pas les agents M1/P1 ni la fin de session (09:41 → 09:56).
- Durées : « durée cumulée » = somme des intervalles lancement → fin (ou `duration_ms` des notifications) ; elle inclut la création de worktree et l'attente de permission, pas seulement le temps de calcul. La durée du lot 6 relance (46 min) et celle des lots repris incluent des attentes. M1 est encore actif au moment de la lecture : ses chiffres (et donc ceux de la ligne « hors vagues » et du total sonnet) ne sont pas définitifs.
- Parallélisme : mesuré au niveau agent ; je ne mesure pas la contention réelle (API, Maven). Le maximum de 7 est atteint quelques secondes seulement (les lots sont lancés à 12-15 s d'écart ; lot 6 se bloque après 34 s).
- Rattachement des agents A/B/Fix aux vagues : jugement chronologique, non documenté dans DELEGATION.md.
- Aucun id de modèle plus fin que `claude-haiku-5-5` / `claude-sonnet-5-5` / `claude-opus-5-5` n'existe dans les données.
- Aucune estimation en dollars n'est faite par moi ; seuls les USD du `cost-state` (haiku 1,28 ; sonnet 27,59 ; opus 33,84 ; total 62,71, non ventilables orchestrateur / sous-agents sonnet) sont cités.
