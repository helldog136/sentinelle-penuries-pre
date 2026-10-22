import warnings;warnings.filterwarnings('ignore')
exec(open('final.py').read().replace("if __name__=='__main__':","if False:"))
f=lambda x:f"{int(x):,}".replace(',',' ')
# day-1 / day-2 output reliability
d2={m:sum(r['tokens']['output'] for r in rows if r['model_short']==m and r['tokens_reliable_output'] and r['end']<=CS_TS) for m in ('haiku','sonnet','opus')}
d1f={m:sum(r['tokens']['output'] for r in rows if r['model_short']==m and not r['tokens_reliable_output']) for m in ('haiku','sonnet','opus')}
CS1=main[1407]['modelUsage']
orch_d1=sum(v['o'] for v in seen.values() if v['ts']<='2026-10-21T14:57:37.965Z')
est={'haiku':CS1['claude-haiku-5-5']['outputTokens'],'opus':CS1['claude-opus-5-5']['outputTokens'],'sonnet':CS1['claude-sonnet-5-5']['outputTokens']-orch_d1}
out['output_day1_subagents_by_model']=dict(note="Sortie des sous-agents du jour 1 par modele: cost-state a 2026-10-21T14:57:37Z (aucun agent du jour 2 encore lance); sonnet = cost-state moins sortie orchestrateur du transcript (suppose celle-ci complete). haiku/opus: mesure exacte; sonnet: mesure derivee.",day1_file_lower_bound=d1f,day1_estimate=est,day2_reliable=d2)
json.dump(out,open('metriques-agents.json','w'),ensure_ascii=False,indent=1,default=lambda o:dict(o) if isinstance(o,collections.Counter) else str(o))
L=[]
A=L.append
A("# Métriques des sous-agents, session Sentinelle (analyse par script)\n")
A("Sources : transcript principal (1962 lignes), 53 transcripts de sous-agents (`.../subagents/agent-*.jsonl` + `.meta.json`), blocs `cost-state`. Tout chiffre ci-dessous est MESURÉ sauf mention « estimé ».\n")
A("## 0. Lire avant : trois pièges de mesure\n")
A("1. **`<subagent_tokens>` des task-notifications n'est PAS un cumul consommé.** Il vaut à peu près la taille du contexte au dernier tour (entrée + cache lecture + cache écriture + sortie du dernier message ; vérifié sur ~50 agents : écart de quelques centaines à quelques milliers de tokens). Il est donc non additif entre agents comme mesure de consommation. J'ai utilisé à la place la somme des `message.usage` des transcripts par sous-agent. La valeur de la notification figure dans le JSON (`final_context_tokens_notification`).")
A("2. **Sorties (`output_tokens`) des sous-agents du JOUR 1 (2026-10-21, claude-code v2.1.306) sous-comptées** : médiane 8 tokens par message, 26 % des messages à ≤5 tokens (valeurs de type instantané de début de flux). Le jour 2 (v2.1.308, vagues A/B) les sorties sont plausibles (médiane 454, 0 % ≤5). L'orchestrateur est cohérent les deux jours. Entrée, cache lecture et cache écriture ne sont pas concernés : leur somme par modèle égale EXACTEMENT les totaux `cost-state` du harness pour haiku et opus (vérifié, voir §6).")
A("3. Sorties jour 1 : borne basse seulement par agent et par vague. Le total par modèle est en revanche exact (différence de `cost-state`, §6) : sous-agents jour 1 = haiku 227 856, opus 503 959, sonnet ≈ 286 513 (dérivé). Contre-épreuve : pour le jour 2, la somme des sorties des fichiers égale exactement l'écart de `cost-state` (haiku +23 521, opus +168 130), donc les sorties du jour 2 sont exactes.\n")
A("## 1. Totaux par modèle (sous-agents)\n")
A("| Modèle | Agents | Entrée (non cachée) | Sortie (fichiers, borne basse) | Cache lecture | Cache écriture | Durée cumulée (s, notif.) |\n|---|---|---|---|---|---|---|")
for m in ('haiku','sonnet','opus'):
    b=bymodel[m]; A(f"| {m} (`{[r for r in rows if r['model_short']==m][0]['model_real'][0]}`) | {b['n']} | {f(b['input'])} | {f(b['output'])} | {f(b['cache_read'])} | {f(b['cache_write'])} | {f(b['dur_notif_s'])} |")
tt=collections.Counter()
for b in bymodel.values():
    for k in('n','input','output','cache_read','cache_write','dur_notif_s'): tt[k]+=b[k]
A(f"| **Total** | {tt['n']} | {f(tt['input'])} | {f(tt['output'])} | {f(tt['cache_read'])} | {f(tt['cache_write'])} | {f(tt['dur_notif_s'])} |\n")
A("Modèle réel = `message.model` des transcripts ; il coïncide avec le modèle demandé (`haiku`/`sonnet`/`opus`) pour les 53 agents.\n")
A("Sortie corrigée par modèle, sous-agents seuls (jour 1 via cost-state + jour 2 des fichiers) : haiku 251 377 ; opus 672 089 ; sonnet ≈ 286 513 (jour 1, dérivé) + 73 479 (jour 2, fichiers) + environ 35 500 (M1/P1, fichiers, M1 non terminé, valeur mouvante) ≈ 395 500. Total harness sonnet (orchestrateur inclus) : 694 529 à 09:41:32Z. Voir §6.\n")
A("## 2. Par vague\n")
A("Parallélisme = recouvrement des intervalles lancement (tool_use) → notification « finished » (et SendMessage → notification pour les agents repris) ; mesuré, tous les timestamps existent sauf S0 (appel synchrone, intervalle pris dans son transcript) et M1 (non terminé). « Même message » = nombre d'appels Agent dans un même message assistant.\n")
A("| Vague | Agents | haiku/sonnet/opus | Parallèle max (recouvrement) | Lancés dans le même message (max) | Début → fin (UTC) | Durée murale | Durée cumulée agents |\n|---|---|---|---|---|---|---|---|")
for w in waves:
    mm=w['models']; A(f"| {w['wave']} | {w['n']} | {mm.get('haiku',0)}/{mm.get('sonnet',0)}/{mm.get('opus',0)} | {w['max_parallel']} | {w['max_launched_same_message']} | {w['start'][5:16]} → {w['end'][5:16]} | {int(w['wall_s']//60)} min {int(w['wall_s']%60)} s | {int(w['dur_ts_s']//60)} min |")
A(f"\nParallélisme maximal global (toutes vagues) : **{glob_par}** (vague 2, lots 1 à 7). Les vagues 2 et 2′ se chevauchent un peu dans le temps, les autres non (vague 3 : 4 en parallèle max, jamais plus de 3 lancés dans le même message).\n")
A("### Tokens par vague (sous-agents ; sortie = borne basse pour les vagues du jour 1)\n")
A("| Vague | Entrée | Sortie | Cache lecture | Cache écriture | Détail par modèle (agents ; sortie ; cache écriture) |\n|---|---|---|---|---|---|")
for w in waves:
    det='; '.join(f"{m}: {v['n']} ag, {f(v['output'])}, {f(v['cache_write'])}" for m,v in w['by_model'].items())
    A(f"| {w['wave']} | {f(w['input'])} | {f(w['output'])}{'' if w['wave'] in ('A','B (tests rouges)','hors vagues (instrumentation M1/P1)') else ' (sous-compté)'} | {f(w['cache_read'])} | {f(w['cache_write'])} | {det} |")
A("\n### Tâches par vague\n")
for w in waves:
    A(f"- **{w['wave']}** : "+' ; '.join(f"{r['desc']} [{r['model_short']}]" for r in rows if r['wave']==w['wave']))
A("\nRattachement aux vagues : 0a, 0a-bis, 1′, 2, 2′, 3 = `docs/DELEGATION.md` (concordance exacte des tâches et modèles ; S0 vague 2, C1-C3 vague 2′). A (A0-A4 : ADR-004, contrats, squelettes, migration, propriété) et B (B0-B5 tests rouges, relances, correctif de collision de stubs) = ordre chronologique et messages de commit (`[stubs]`, `[red]`) ; ces deux vagues ne sont PAS dans DELEGATION.md. Le « Fix collision stubs adapter/batch » est rattaché à B par ma décision (supposition). M1 (compteurs tokens) et P1 (tarifs API) sont hors vagues.\n")
A("## 3. Détail par sous-agent (ordre de lancement)\n")
A("Colonnes : E = entrée non cachée, S = sortie (✱ = sous-comptée, jour 1), CL = cache lecture, CE = cache écriture, Ctx = `subagent_tokens` de la dernière notification (taille de contexte final, pas un cumul), Durée = somme des `duration_ms` des notifications « finished ».\n")
A("| Vague | Tâche | Modèle demandé → réel | Isolation | E | S | CL | CE | Ctx notif. | Durée (s) | Notes |\n|---|---|---|---|---|---|---|---|---|---|---|")
for r in rows:
    t=r['tokens']; notes=[]
    if r['resumed']: notes.append(f"repris {r['resumed']}× (SendMessage)")
    if r['interim_notifs']: notes.append(f"{r['interim_notifs']} notif. intermédiaire(s) « waiting on background »")
    if not r['has_notification']: notes.append('aucune notification de fin' + (' (appel synchrone)' if r['desc'].startswith('S0') else ' (non terminé / sans retour)'))
    if r['desc'] in ("Lot 6 Persistance JPA et tests H2","B0 aligner nom propriété","B3 tests gRPC REST référentiel"): notes.append('BLOQUÉ (rapporté « non réalisé »), relancé')
    A(f"| {r['wave']} | {r['desc']} | {r['model_req']} → {r['model_real'][0].replace('claude-','')} | {r['isolation']} | {f(t['input'])} | {f(t['output'])}{'' if r['tokens_reliable_output'] else '✱'} | {f(t['cache_read'])} | {f(t['cache_write'])} | {f(r['final_context_tokens_notification']) if r['final_context_tokens_notification'] else '—'} | {int(r['dur_notif_s']) if r['dur_notif_s'] else '—'} | {'; '.join(notes)} |")
o=orch
A("\n## 4. Orchestrateur (conversation principale, modèle `claude-sonnet-5-5`)\n")
A("Par script sur les messages assistant du transcript principal, dédoublonnés par `message.id` (un message est écrit une fois par bloc de contenu avec le même usage), valeur max par champ.\n")
A("| Périmètre | Appels API | Entrée | Sortie (dont réflexion) | Cache lecture | Cache écriture |\n|---|---|---|---|---|---|")
for k,lab in (('avant_compaction','Avant compaction (jour 1 et début jour 2, jusqu'+"'"+'à 09:06:37Z)'),('apres_compaction','Après compaction'),('total','**Total transcript**'),('jusqua_cost_state','Jusqu'+"'"+'à 09:41:32Z (comparaison cost-state)')):
    v=o[k]; A(f"| {lab} | {v['calls']} | {f(v['input'])} | {f(v['output'])} ({f(v['thinking_included_in_output'])}) | {f(v['cache_read'])} | {f(v['cache_write'])} |")
A("\nSortie de l'orchestrateur : jugée fiable (médiane 848 / 500 tokens par message, aucun message ≤5 tokens).\n")
A("**Compaction et couverture** : un seul marqueur `compact_boundary` (ligne 1465, 2026-10-22T09:06:37Z, déclenchement manuel, `preTokens` 559 994, `postTokens` 18 251, `cumulativeDroppedTokens` 541 743) suivi d'un message de reprise (`isCompactSummary`). **L'historique d'avant compaction EST présent** dans le fichier : 1465 lignes avant le marqueur, dont 184 appels assistant, du premier message (2026-10-21T11:46:42Z) jusqu'à 09:06 . Le transcript couvre donc toute la session visible, jusqu'à 09:56:04Z. Les transcripts de sous-agents sont tous présents (53 lancements, 53 fichiers, appariés par `toolUseId`). Ce que le transcript ne contient pas : l'appel API qui a produit le résumé de compaction (son usage n'est pas journalisé) et d'éventuels appels auxiliaires (voir §6).\n")
A("## 5. Reprises, échecs, interruptions\n")
A("- **Bloqués puis relancés (3)** : Lot 6 (worktree créé sur le commit initial, 34 s, aucun fichier écrit) → relancé « Lot 6 (relance) » ; B0 (propriété absente de son worktree, 42 s) → relancé « (relance) » ; B3 (worktree sur un ancien merge, 50 s) → relancé « (relance) » ; leurs tokens sont comptés (coût de l'échec inclus).")
A("- **Agents repris par SendMessage (7 envois, 6 agents)** : Lot 3 (1), Lot 2 (2), Lot 1 (1), S1 (1), B3 audit (1), B4 tests batch (1 envoi à 09:21:08 alors que l'agent tournait encore : une seule notification de fin, pas de reprise). Les notifications multiples d'un même agent n'ont pas été additionnées en tokens (les tokens viennent des transcripts, un fichier par agent, reprises incluses) ; les durées « finished » ont été additionnées, pas les notifications intermédiaires.")
A("- **Notifications intermédiaires « waiting on its own background work »** : Lot 7 (2), Lot 6 relance (1) ; l'agent a ensuite fini (« finished »).")
A("- **M1 « compteurs tokens par agent »** : aucun SubagentHandback ni notification dans le transcript lu ; son transcript s'arrête à 09:58:03Z. Considéré non terminé à la date du snapshot (ses tokens sont partiels). P1 « tarifs API » : terminé (09:57:11Z). Les deux sont hors vagues (instrumentation de ce suivi).")
A("- Aucun `TaskStop`, aucune interruption explicite dans le transcript (aucun appel TaskStop). Les 53 notifications d'agents sont toutes `completed`. Seules deux notifications non-agent ont un autre statut : une commande d'arrière-plan « failed » (démarrage de l'app en profil demo, 12:26Z) et une « killed » (attente Maven, limite de temps, 13:07Z).")
A("- 10 notifications `Monitor`/`Background command` (builds Maven, PIT) n'ont pas de `subagent_tokens` : ce ne sont pas des sous-agents, ignorées.\n")
A("## 6. Trous et incertitudes (honnêtes)\n")
r=rec
A(f"- **Réconciliation avec `cost-state` (dernier, 09:41:32Z, coût total 62,71 USD)** : haiku : entrée/cache lecture/cache écriture identiques au token près ; opus : idem. Sonnet : cache écriture quasi égal (harness {f(r['sonnet']['harness']['cache_write'])} vs fichiers {f(r['sonnet']['orchestrator_main_until_cost_state']['cache_write']+r['sonnet']['subagent_files']['cache_write'])}) mais **écart inexpliqué** : cache lecture harness {f(r['sonnet']['harness']['cache_read'])} vs {f(r['sonnet']['orchestrator_main_until_cost_state']['cache_read']+r['sonnet']['subagent_files']['cache_read'])} (≈14 M de plus), entrée {f(r['sonnet']['harness']['input'])} vs {f(r['sonnet']['orchestrator_main_until_cost_state']['input']+r['sonnet']['subagent_files']['input'])} (≈24 k de plus). Hypothèse NON vérifiée : appels sonnet non journalisés (classifieur de permissions du mode auto, résumé de compaction).")
A(f"- **Sorties du jour 1 sous-comptées dans les fichiers** : fichiers sous-agents haiku {f(d1f['haiku'])}, sonnet {f(d1f['sonnet'])}, opus {f(d1f['opus'])} (bornes basses). Totaux exacts par modèle pour le jour 1 par différence de `cost-state` (14:57:37Z, avant tout agent du jour 2) : haiku {f(est['haiku'])}, opus {f(est['opus'])} (mesures), sonnet {f(est['sonnet'])} (dérivé : cost-state moins sortie de l'orchestrateur du transcript, suppose celle-ci complète). Aucune ventilation par agent ou par vague n'est mesurable pour ces sorties ; je n'en invente pas.")
A("- `cost-state` ne couvre pas les agents M1/P1 ni la fin de session (09:41 → 09:56).")
A("- Durées : « durée cumulée » = somme des intervalles lancement → fin (ou `duration_ms` des notifications) ; elle inclut la création de worktree et l'attente de permission, pas seulement le temps de calcul. La durée du lot 6 relance (46 min) et celle des lots repris incluent des attentes. M1 est encore actif au moment de la lecture : ses chiffres (et donc ceux de la ligne « hors vagues » et du total sonnet) ne sont pas définitifs.")
A("- Parallélisme : mesuré au niveau agent ; je ne mesure pas la contention réelle (API, Maven). Le maximum de 7 est atteint quelques secondes seulement (les lots sont lancés à 12-15 s d'écart ; lot 6 se bloque après 34 s).")
A("- Rattachement des agents A/B/Fix aux vagues : jugement chronologique, non documenté dans DELEGATION.md.")
A("- Aucun id de modèle plus fin que `claude-haiku-5-5` / `claude-sonnet-5-5` / `claude-opus-5-5` n'existe dans les données.")
A("- Aucune estimation en dollars n'est faite par moi ; seuls les USD du `cost-state` (haiku 1,28 ; sonnet 27,59 ; opus 33,84 ; total 62,71, non ventilables orchestrateur / sous-agents sonnet) sont cités.")
open('metriques-agents.md','w').write('\n'.join(L)+'\n')
