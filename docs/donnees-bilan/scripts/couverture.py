import sys, json, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
def cnt(el, t):
    for c in el.findall('counter'):
        if c.get('type') == t:
            return int(c.get('missed')), int(c.get('covered'))
    return 0, 0
rows = {}
for p in root.findall('package'):
    name = p.get('name').replace('vd/affsn/sentinelle', '').strip('/').replace('/', '.') or '(racine)'
    lm, lc = cnt(p, 'LINE'); bm, bc = cnt(p, 'BRANCH')
    rows[name] = dict(lm=lm, lc=lc, bm=bm, bc=bc)
def groupe(n):
    for g in ('domain', 'application', 'adapter.in.grpc', 'adapter.in.rest', 'adapter.out.persistence', 'adapter.out.audit', 'batch', 'security', 'config', 'service'):
        if n == g or n.startswith(g + '.'): return g
    return n
groups = {}
for n, r in rows.items():
    g = groups.setdefault(groupe(n), dict(lm=0, lc=0, bm=0, bc=0))
    for k in r: g[k] += r[k]
def pct(c, m): return f"{100*c/(c+m):5.1f}%" if c + m else "   n/a"
def show(d, title):
    print(title)
    print(f"{'package':38}{'lignes':>9}{'branches':>10}   (lignes couvertes/total)")
    for n in sorted(d):
        r = d[n]; print(f"{n:38}{pct(r['lc'], r['lm']):>9}{pct(r['bc'], r['bm']):>10}   {r['lc']}/{r['lc']+r['lm']}")
show(rows, "== PAR PACKAGE ==")
print()
show(groups, "== PAR GROUPE ==")
tot = dict(lm=sum(r['lm'] for r in rows.values()), lc=sum(r['lc'] for r in rows.values()), bm=sum(r['bm'] for r in rows.values()), bc=sum(r['bc'] for r in rows.values()))
print(f"\nGLOBAL : lignes {pct(tot['lc'], tot['lm'])} ({tot['lc']}/{tot['lc']+tot['lm']}), branches {pct(tot['bc'], tot['bm'])} ({tot['bc']}/{tot['bc']+tot['bm']})")
json.dump(dict(rows=rows, groups=groups, total=tot), open(sys.argv[2], 'w'))
