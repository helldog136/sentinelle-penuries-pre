exec(open('an.py').read())
from datetime import datetime
P=lambda s:datetime.fromisoformat(s.replace('Z','+00:00')).timestamp()
CS_TS='2026-10-22T09:41:32.497Z'
def wave(desc,ts):
    d=desc
    if ts<'2026-10-21T12:00': return '0a'
    if ts<'2026-10-21T12:07': return '0a-bis'
    if ts<'2026-10-21T12:14': return "1'"
    if ts<'2026-10-21T12:32': return '2'
    if ts<'2026-10-21T13:00': return "2'"
    if ts<'2026-10-22T09:00': return '3'
    if ts<'2026-10-22T09:15': return 'A'
    if ts<'2026-10-22T09:50': return 'B (tests rouges)'
    return 'hors vagues (instrumentation M1/P1)'
WORD=['0a','0a-bis',"1'",'2',"2'",'3','A','B (tests rouges)','hors vagues (instrumentation M1/P1)']
# S0 launched 12:15 -> wave 2 ; C1-C3 12:33+ -> 2'
tid2agent={s['meta']['toolUseId']:a for a,s in subs.items()}
sm_by=collections.defaultdict(list)
for i,ts,inp in sm: sm_by[inp['to']].append(ts)
notif_by=collections.defaultdict(list)
for n in notifs.values():
    if n['tid'] in subs: notif_by[n['tid']].append(n)
rows=[]
for tu,l in sorted(launch.items(),key=lambda x:x[1]['idx']):
    a=tid2agent[tu]; s=subs[a]
    # per-message max usage
    mx={}
    for ln in open(B+'/subagents/agent-%s.jsonl'%a):
        e=json.loads(ln)
        if e['type']=='assistant':
            u=e['message']['usage']; k=e['message']['id']; o=mx.setdefault(k,dict(i=0,o=0,r=0,w=0,ts=e['timestamp'],model=e['message']['model']))
            o['i']=max(o['i'],u.get('input_tokens',0));o['o']=max(o['o'],u.get('output_tokens',0));o['r']=max(o['r'],u.get('cache_read_input_tokens',0));o['w']=max(o['w'],u.get('cache_creation_input_tokens',0))
    tok=dict(input=sum(v['i'] for v in mx.values()),output=sum(v['o'] for v in mx.values()),cache_read=sum(v['r'] for v in mx.values()),cache_write=sum(v['w'] for v in mx.values()))
    models=sorted(set(v['model'] for v in mx.values()))
    ns=sorted(notif_by[a],key=lambda n:n['ts'])
    fin=[n for n in ns if 'finished' in n['summary'].split('"')[-1]]
    # segments
    segs=[];start=P(l['ts']);open_=True
    events=sorted([(P(n['ts']),'n',n) for n in fin]+[(P(t),'s',None) for t in sm_by.get(a,[])],key=lambda x:x[0])
    for t,k,n in events:
        if k=='n' and open_: segs.append((start,t));open_=False
        elif k=='s' and not open_: start=t;open_=True
    ended=bool(fin) or l.get('run_in_background') is False
    if open_:
        if l.get('run_in_background') is False or not fin: segs.append((start,P(s['last'])))
    lastnotif_tok=fin[-1]['tok'] if fin else None
    rows.append(dict(wave=wave(l['description'],l['ts']),desc=l['description'],model_req=l['model'],model_real=models,subagent_type=l['subagent_type'],isolation=l.get('isolation') or 'aucune',launch=l['ts'],end=(fin[-1]['ts'] if fin else s['last']),agent_id=a,tool_use_id=tu,tokens=tok,nmsgs=len(mx),
        final_context_tokens_notification=lastnotif_tok,notif_tokens_all=[n['tok'] for n in fin],dur_notif_s=sum(n['dur'] for n in fin)/1000 if fin else None,dur_ts_s=sum(e-b for b,e in segs),
        runs=len(segs),resumed=len(sm_by.get(a,[])),has_notification=bool(fin),segs=segs,tokens_reliable_output=l['ts']>='2026-10-22',interim_notifs=len(ns)-len(fin)))
def maxpar(rs):
    ev=[]
    for r in rs:
        for b,e in r['segs']: ev+= [(b,1),(e,-1)]
    ev.sort(key=lambda x:(x[0],x[1])); c=m=0
    for t,d in ev: c+=d; m=max(m,c)
    return m
# launched in same message group: consecutive launches with no other tool between? use idx gaps and msgid
msg=collections.Counter(launch[r['tool_use_id']]['msgid'] for r in rows)
for r in rows: r['same_message_launch_count']=msg[launch[r['tool_use_id']]['msgid']]
def agg(rs):
    t=collections.Counter()
    for r in rs:
        for k,v in r['tokens'].items(): t[k]+=v
    return dict(n=len(rs),**t,dur_notif_s=sum(r['dur_notif_s'] or 0 for r in rs),dur_ts_s=sum(r['dur_ts_s'] for r in rs))
short=lambda m:'haiku' if 'haiku' in m else 'opus' if 'opus' in m else 'sonnet'
for r in rows: r['model_short']=short(r['model_real'][0])
waves=[]
for w in WORD:
    rs=[r for r in rows if r['wave']==w]
    b=min(P(r['launch']) for r in rs); e=max(max(x[1] for x in r['segs']) for r in rs)
    msgs=collections.Counter(launch[r['tool_use_id']]['msgid'] for r in rs)
    waves.append(dict(wave=w,agents=[r['desc'] for r in rs],models=collections.Counter(r['model_short'] for r in rs),max_parallel=maxpar(rs),max_launched_same_message=max(msgs.values()),start=datetime.utcfromtimestamp(b).isoformat(),end=datetime.utcfromtimestamp(e).isoformat(),wall_s=e-b,**{k:v for k,v in agg(rs).items()},by_model={m:agg([r for r in rs if r['model_short']==m]) for m in ('haiku','sonnet','opus') if any(r['model_short']==m for r in rs)}))
bymodel={m:agg([r for r in rows if r['model_short']==m]) for m in ('haiku','sonnet','opus')}
glob_par=maxpar(rows)
# orchestrator
seen={}
for i,d in enumerate(main):
    if d['type']=='assistant':
        u=d['message']['usage'];k=d['message']['id'];o=seen.setdefault(k,dict(i=0,o=0,r=0,w=0,ts=d['timestamp'],idx=i,model=d['message']['model'],th=0))
        o['i']=max(o['i'],u.get('input_tokens',0));o['o']=max(o['o'],u.get('output_tokens',0));o['r']=max(o['r'],u.get('cache_read_input_tokens',0));o['w']=max(o['w'],u.get('cache_creation_input_tokens',0));o['th']=max(o['th'],(u.get('output_tokens_details') or {}).get('thinking_tokens',0))
def osum(f):
    vs=[v for v in seen.values() if f(v)]
    return dict(calls=len(vs),input=sum(v['i'] for v in vs),output=sum(v['o'] for v in vs),thinking_included_in_output=sum(v['th'] for v in vs),cache_read=sum(v['r'] for v in vs),cache_write=sum(v['w'] for v in vs))
orch=dict(total=osum(lambda v:True),avant_compaction=osum(lambda v:v['idx']<1465),apres_compaction=osum(lambda v:v['idx']>1465),jusqua_cost_state=osum(lambda v:v['ts']<=CS_TS),models=sorted(set(v['model'] for v in seen.values())))
# reconciliation with cost-state (09:41:32)
cs=[d for d in main if d['type']=='cost-state'][-1]
rec={}
for m,full in (('haiku','claude-haiku-5-5'),('opus','claude-opus-5-5'),('sonnet','claude-sonnet-5-5')):
    h=cs['modelUsage'][full]
    sub=agg([r for r in rows if r['model_short']==m and r['end']<=CS_TS])
    rec[m]=dict(harness=dict(input=h['inputTokens'],output=h['outputTokens'],thinking=h['thinkingTokens'],cache_read=h['cacheReadInputTokens'],cache_write=h['cacheCreationInputTokens'],usd=h['costUSD']),subagent_files=dict(input=sub['input'],output=sub['output'],cache_read=sub['cache_read'],cache_write=sub['cache_write']))
o2=orch['jusqua_cost_state']
rec['sonnet']['orchestrator_main_until_cost_state']=o2
out=dict(rows=[{k:v for k,v in r.items() if k!='segs'} for r in rows],waves=waves,by_model=bymodel,global_max_parallel=glob_par,orchestrator=orch,cost_state_reconciliation=rec,cost_state_ts=CS_TS,cost_state_total_usd=cs['totalCostUSD'],
  all_notifs_n=len(notifs))
json.dump(out,open('metriques-agents.json','w'),ensure_ascii=False,indent=1,default=lambda o:dict(o) if isinstance(o,collections.Counter) else str(o))
if __name__=='__main__':
    for w in waves: print(w['wave'],dict(w['models']),'par',w['max_parallel'],w['max_launched_same_message'],'wall',int(w['wall_s']),'cum',int(w['dur_ts_s']),int(w['dur_notif_s']),{k:w[k] for k in('input','output','cache_read','cache_write')})
    print(bymodel);print(glob_par);print(orch);print(json.dumps(rec,indent=0))
