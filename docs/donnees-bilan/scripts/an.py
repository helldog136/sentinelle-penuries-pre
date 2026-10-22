import json,glob,os,re,collections
B='/root/.claude/projects/-home-user-sentinelle-penuries-pre/6df2a025-905f-5489-8b36-41051470d462'
main=[json.loads(l) for l in open(B+'.jsonl')]
# launches
launch={}
for i,d in enumerate(main):
    if d['type']=='assistant':
        for b in d['message']['content']:
            if b.get('type')=='tool_use' and b['name'] in('Agent','Task'):
                launch[b['id']]=dict(idx=i,ts=d['timestamp'],msgid=d['message'].get('id'),**{k:b['input'].get(k) for k in('description','model','subagent_type','isolation','run_in_background')},prompt_len=len(b['input'].get('prompt','')))
# SendMessage
sm=[]
for i,d in enumerate(main):
    if d['type']=='assistant':
        for b in d['message']['content']:
            if b.get('type')=='tool_use' and b['name']=='SendMessage':
                sm.append((i,d['timestamp'],b['input']))
# notifications (dedupe by deliveryId/ content)
notifs=collections.OrderedDict()
rx=lambda t,s:(re.search('<%s>(.*?)</%s>'%(t,t),s,re.S) or [None,None])[1]
for i,d in enumerate(main):
    c=None
    if d['type']=='queue-operation' and d.get('operation')=='enqueue': c=d.get('content')
    if c and '<task-notification>' in c:
        key=(rx('task-id',c),rx('summary',c),rx('subagent_tokens',c),rx('duration_ms',c))
        notifs.setdefault(key,dict(ts=d['timestamp'],tid=rx('task-id',c),tuid=rx('tool-use-id',c),status=rx('status',c),summary=rx('summary',c),tok=int(rx('subagent_tokens',c) or -1),tools=int(rx('tool_uses',c) or -1),dur=int(rx('duration_ms',c) or -1)))
# also user-type ones not in enqueue
for i,d in enumerate(main):
    if d['type']=='user' and isinstance(d['message']['content'],str) and '<task-notification>' in d['message']['content']:
        c=d['message']['content']
        key=(rx('task-id',c),rx('summary',c),rx('subagent_tokens',c),rx('duration_ms',c))
        if key not in notifs: print('extra user notif',key)
        notifs.setdefault(key,dict(ts=d['timestamp'],tid=rx('task-id',c),tuid=rx('tool-use-id',c),status=rx('status',c),summary=rx('summary',c),tok=int(rx('subagent_tokens',c) or -1),tools=int(rx('tool_uses',c) or -1),dur=int(rx('duration_ms',c) or -1)))
# subagent files
subs={}
for f in glob.glob(B+'/subagents/*.meta.json'):
    m=json.load(open(f)); aid=os.path.basename(f)[6:-10]
    ents=[json.loads(l) for l in open(f[:-10]+'.jsonl')]
    seen={};
    for e in ents:
        if e['type']=='assistant':
            mm=e['message']; seen[mm.get('id') or e['uuid']]=(e,mm)  # last wins
    u=collections.Counter(); models=collections.Counter(); n=0
    for k,(e,mm) in seen.items():
        us=mm.get('usage') or {}
        u['in']+=us.get('input_tokens',0);u['out']+=us.get('output_tokens',0)
        u['cr']+=us.get('cache_read_input_tokens',0);u['cw']+=us.get('cache_creation_input_tokens',0)
        models[mm.get('model')]+=1;n+=1
    ts=[e['timestamp'] for e in ents if e.get('timestamp')]
    subs[aid]=dict(meta=m,usage=dict(u),models=dict(models),nmsg=n,nraw=sum(1 for e in ents if e['type']=='assistant'),first=ts[0],last=ts[-1],nuser=sum(1 for e in ents if e['type']=='user'))
