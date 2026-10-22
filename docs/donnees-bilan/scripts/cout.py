# Calcul du cout de la session Sentinelle. Entrees : metriques-agents.json, haiku_tiers.json, tarifs.json
import json
S='/tmp/claude-0/-home-user-sentinelle-penuries-pre/6df2a025-905f-5489-8b36-41051470d462/scratchpad/'
d=json.load(open(S+'metriques-agents.json')); ht=json.load(open(S+'bilan/haiku_tiers.json')); T=json.load(open(S+'tarifs.json'))
FX=T['fx']['eur_per_usd']
HL=T['models']['claude-haiku-5-5']['palier_<=100K']; HH=T['models']['claude-haiku-5-5']['palier_>100K']
SO=T['models']['claude-sonnet-5-5']; OP=T['models']['claude-opus-5-5']
def c(p,i,o,r,w,ttl='5m'): return (i*p['input']+o*p['output']+r*p['cache_read']+w*p['cache_write_'+ttl])/1e6
def haiku_wave(w):
    tot=0
    for t,p in (('lo',HL),('hi',HH)):
        v=ht['wave'].get(w+'|'+t)
        if v: tot+=c(p,v['in'],v['out'],v['cr'],v['cw'])
    return tot
WAVES=[x['wave'] for x in d['waves']]
rows=[]; bym={'haiku':0,'sonnet':0,'opus':0}
print('| Vague | haiku | sonnet | opus | Total USD | Total EUR |'); print('|---|---|---|---|---|---|')
tw=0
for W in d['waves']:
    w=W['wave']; bm=W['by_model']; v={}
    v['haiku']=haiku_wave(w) if 'haiku' in bm else 0
    v['sonnet']=c(SO,bm['sonnet']['input'],bm['sonnet']['output'],bm['sonnet']['cache_read'],bm['sonnet']['cache_write']) if 'sonnet' in bm else 0
    v['opus']=c(OP,bm['opus']['input'],bm['opus']['output'],bm['opus']['cache_read'],bm['opus']['cache_write']) if 'opus' in bm else 0
    s=sum(v.values()); tw+=s
    for k in v: bym[k]+=v[k]
    print(f"| {w} | {v['haiku']:.2f} | {v['sonnet']:.2f} | {v['opus']:.2f} | {s:.2f} | {s*FX:.2f} |")
print(f"| sous-total vagues (sorties fichiers) | {bym['haiku']:.2f} | {bym['sonnet']:.2f} | {bym['opus']:.2f} | {tw:.2f} | {tw*FX:.2f} |")
# sorties jour 1 manquantes
od=d['output_day1_subagents_by_model']; miss={m:od['day1_estimate'][m]-od['day1_file_lower_bound'][m] for m in ('haiku','sonnet','opus')}
lo_calls=sum(v['calls'] for k,v in ht['wave'].items() if k.split('|')[0] in ('0a','0a-bis',"1'",'2',"2'",'3') and k.endswith('lo'))
hi_calls=sum(v['calls'] for k,v in ht['wave'].items() if k.split('|')[0] in ('0a','0a-bis',"1'",'2',"2'",'3') and k.endswith('hi'))
fh=hi_calls/(lo_calls+hi_calls)
mh=miss['haiku']*(HL['output']*(1-fh)+HH['output']*fh)/1e6
mh_lo=miss['haiku']*HL['output']/1e6; mh_hi=miss['haiku']*HH['output']/1e6
ms=miss['sonnet']*SO['output']/1e6; mo=miss['opus']*OP['output']/1e6
print('\nsorties J1 manquantes',miss,'fh=%.3f'%fh, 'haiku %.3f [%.3f-%.3f] sonnet %.3f opus %.3f'%(mh,mh_lo,mh_hi,ms,mo))
# hors vagues = M1/P1
mp=[W for W in d['waves'] if W['wave'].startswith('hors')][0]['by_model']['sonnet']
cmp_=c(SO,mp['input'],mp['output'],mp['cache_read'],mp['cache_write'])
# orchestrateur
O=d['orchestrator']; oc=O['jusqua_cost_state']; ot=O['total']
def orch(x,ttl): return c(SO,x['input'],x['output'],x['cache_read'],x['cache_write'],ttl)
# trou sonnet (appels non journalises) = harness - fichiers - orch
H=d['cost_state_reconciliation']['sonnet']['harness']; sf=d['cost_state_reconciliation']['sonnet']['subagent_files']
gap=dict(input=H['input']-sf['input']-oc['input'],cache_read=H['cache_read']-sf['cache_read']-oc['cache_read'],cache_write=H['cache_write']-sf['cache_write']-oc['cache_write'])
gap['output']=H['output']-oc['output']-(od['day1_estimate']['sonnet']+od['day2_reliable']['sonnet'])
print('gap sonnet',gap)
cg5=c(SO,gap['input'],gap['output'],gap['cache_read'],gap['cache_write'],'5m'); cg1=c(SO,gap['input'],gap['output'],gap['cache_read'],gap['cache_write'],'1h')
# perimetre cost-state : vagues 0a..B (sans hors vagues)
sub={m:bym[m] for m in bym}; sub['sonnet']-=cmp_
print('\nPERIMETRE COST-STATE (09:41:32Z)')
for ttl in ('5m','1h'):
    hk=sub['haiku']+mh; sn=sub['sonnet']+ms+orch(oc,ttl)+(cg5 if ttl=='5m' else cg1); op=sub['opus']+mo
    print(f"ttl orch {ttl}: haiku {hk:.3f} sonnet {sn:.3f} (dont orch {orch(oc,ttl):.3f}, sous-agents {sub['sonnet']+ms:.3f}, trou {cg5 if ttl=='5m' else cg1:.3f}) opus {op:.3f} total {hk+sn+op:.3f} USD = {(hk+sn+op)*FX:.2f} EUR")
print('harness: haiku 1.278 sonnet 27.594 opus 33.836 total 62.708')
# session complete
after=dict(input=ot['input']-oc['input'],output=ot['output']-oc['output'],cache_read=ot['cache_read']-oc['cache_read'],cache_write=ot['cache_write']-oc['cache_write'])
print('\nSESSION VISIBLE (jusqu a ~10:01, M1 partiel)')
for ttl in ('5m','1h'):
    hk=sub['haiku']+mh; op=sub['opus']+mo
    sn_sub=sub['sonnet']+ms; sn_orch=orch(ot,ttl); sn=sn_sub+sn_orch+cmp_+(cg5 if ttl=='5m' else cg1)
    tot=hk+sn+op
    print(f"ttl {ttl}: haiku {hk:.2f} / sonnet sous-agents {sn_sub:.2f} / M1P1 {cmp_:.2f} / orch {sn_orch:.2f} / trou {cg5 if ttl=='5m' else cg1:.2f} / opus {op:.2f} => total {tot:.2f} USD = {tot*FX:.2f} EUR")
print('orch apres 09:41', after, 'cout 5m %.3f 1h %.3f'%(c(SO,**{'i':0,'o':0,'r':0,'w':0}) if False else orch(after,'5m'),orch(after,'1h')))
print('M1P1 %.3f'%cmp_)
# detail orch par composante (total, 5m)
for name,x in (('orch total',ot),('orch avant compaction',O['avant_compaction']),('orch apres compaction',O['apres_compaction'])):
    print(name, {k:round(v,3) for k,v in dict(inp=x['input']*SO['input']/1e6,out=x['output']*SO['output']/1e6,cr=x['cache_read']*SO['cache_read']/1e6,cw5=x['cache_write']*2.5/1e6,cw1=x['cache_write']*4/1e6).items()})
# part du cache dans le cout opus et sonnet sous-agents
bm=d['by_model']
for m,p in (('opus',OP),('sonnet',SO)):
    x=bm[m]; o_corr={'opus':od['day1_estimate']['opus']+od['day2_reliable']['opus'],'sonnet':None}[m]
    print(m,'composantes USD', dict(inp=round(x['input']*p['input']/1e6,3),cr=round(x['cache_read']*p['cache_read']/1e6,2),cw=round(x['cache_write']*p['cache_write_5m']/1e6,2),out_files=round(x['output']*p['output']/1e6,2)))
print('sonnet cache read 0.20 variante: delta USD (perimetre cost-state)', round(H['cache_read']*0.10/1e6,2))

print('\n=== TABLE FINALE (session visible) ===')
for ttl in ('5m','1h'):
    g=cg5 if ttl=='5m' else cg1
    comp=[('haiku sous-agents (vagues)',sub['haiku']+mh),('sonnet sous-agents (vagues)',sub['sonnet']+ms),('sonnet M1/P1',cmp_),('sonnet orchestrateur',orch(ot,ttl)),('sonnet non journalise',g),('opus sous-agents',sub['opus']+mo)]
    tot=sum(v for k,v in comp)
    for k,v in comp: print(ttl,k,'%.2f USD %.2f EUR %.1f%%'%(v,v*FX,100*v/tot))
    print(ttl,'TOTAL %.2f USD %.2f EUR'%(tot,tot*FX))
    sn=sum(v for k,v in comp if k.startswith('sonnet')); print(ttl,'sonnet %.2f USD %.2f EUR ; haiku %.2f EUR ; opus %.2f EUR'%(sn,sn*FX,(sub['haiku']+mh)*FX,(sub['opus']+mo)*FX))
# part du cache (variante 1h)
bm=d['by_model']
cache= (bm['opus']['cache_read']*0.2+bm['opus']['cache_write']*5 + bm['sonnet']['cache_read']*0.1+bm['sonnet']['cache_write']*2.5 + ot['cache_read']*0.1+ot['cache_write']*4 + gap['cache_read']*0.1+gap['cache_write']*4)/1e6
hc=sum(v['cr']*(0.01 if t=='lo' else 0.05)+v['cw']*(0.125 if t=='lo' else 0.625) for t,v in ht['all'].items())/1e6
print('cache USD %.2f + haiku %.2f'%(cache,hc))
outc=(672089*20+ (od['day1_estimate']['sonnet']+od['day2_reliable']['sonnet']+mp['output'])*10 + ot['output']*10+gap['output']*10)/1e6
print('output USD approx (hors haiku) %.2f'%outc)
