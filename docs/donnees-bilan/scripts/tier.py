import json,collections,sys
S='/tmp/claude-0/-home-user-sentinelle-penuries-pre/6df2a025-905f-5489-8b36-41051470d462/scratchpad/'
B='/root/.claude/projects/-home-user-sentinelle-penuries-pre/6df2a025-905f-5489-8b36-41051470d462/subagents/'
d=json.load(open(S+'metriques-agents.json'))
res=collections.defaultdict(lambda: collections.Counter())
perwave=collections.defaultdict(lambda: collections.Counter())
for r in d['rows']:
    if r['model_short']!='haiku': continue
    mx={}
    for ln in open(B+'agent-%s.jsonl'%r['agent_id']):
        e=json.loads(ln)
        if e.get('type')=='assistant':
            u=e['message']['usage'];k=e['message']['id'];o=mx.setdefault(k,[0,0,0,0])
            o[0]=max(o[0],u.get('input_tokens',0));o[1]=max(o[1],u.get('output_tokens',0));o[2]=max(o[2],u.get('cache_read_input_tokens',0));o[3]=max(o[3],u.get('cache_creation_input_tokens',0))
    for i,o_,cr,cw in mx.values():
        t='hi' if i+cr+cw>100_000 else 'lo'
        for c,(k,v) in zip(range(4),(('in',i),('out',o_),('cr',cr),('cw',cw))): res[t][k]+=v; perwave[(r['wave'],t)][k]+=v
        res[t]['calls']+=1; perwave[(r['wave'],t)]['calls']+=1
print({k:dict(v) for k,v in res.items()})
for k,v in sorted(perwave.items()): print(k,dict(v))
json.dump({'all':{k:dict(v) for k,v in res.items()},'wave':{'%s|%s'%k:dict(v) for k,v in perwave.items()}},open(S+'bilan/haiku_tiers.json','w'),indent=1)
