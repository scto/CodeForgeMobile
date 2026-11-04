import re,subprocess,sys,collections
files=subprocess.check_output("git ls-files '*.kt'",shell=True,text=True).split()
files=[f for f in files if '/test/' not in f and '/androidTest/' not in f and not f.startswith(('examples/','build-logic/','app/src/test'))]
decl=re.compile(r'^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:(?:public|internal|protected|open|abstract|sealed|data|enum|annotation|inline|value|suspend|override|const|lateinit|operator|infix|fun interface)\s+)*(class|interface|object|fun|val|var|typealias)\s+(?:<[^>]*>\s*)?([\w.]+)')
tot=collections.Counter(); miss=collections.Counter(); perfile={}
for f in files:
    lines=open(f,encoding='utf8').read().split('\n')
    m=0;t=0
    top=''
    for i,l in enumerate(lines):
        if l and not l[0].isspace() and not l.startswith(('}',')','*','/','@',')')):
            top=l
        if l.startswith((' ','\t')) and not re.match(r'^\s{4}(?:(?:public|internal|open|abstract|override|suspend|data|sealed|enum)\s+)*(fun|val|var|class|interface|object)\b',l): continue
        if re.match(r'^\s*(private|override)\b',l) or 'private ' in l.split('fun')[0].split('val')[0].split('class')[0]: continue
        d=decl.match(l)
        if not d: continue
        if re.match(r'^\s{4,}',l) and len(l)-len(l.lstrip())>4: continue
        # Konstruktor-Properties (Zeile endet auf Komma oder danach folgt ')') zählen nicht: sie gehören zur Klassen-Doku.
        if d.group(1) in ('val','var'):
            nxt=next((x.strip() for x in lines[i+1:] if x.strip()),'')
            if l.rstrip().endswith(',') or nxt.startswith(')'): continue
        if l.startswith('    ') and not l.startswith('     ') and re.search(r'\bfun\b',top) and not re.search(r'\b(class|object|interface)\b',top): continue
        t+=1
        j=i-1
        while j>=0 and (lines[j].strip().startswith('@') or lines[j].strip()==''): j-=1
        if not (j>=0 and (lines[j].strip().endswith('*/'))):
            m+=1
    perfile[f]=(t,m); mod='/'.join(f.split('/')[:2]) if f.split('/')[0] in('core','libs','feature') else f.split('/')[0]
    tot[mod]+=t; miss[mod]+=m
print("TOTAL decl",sum(tot.values()),"undoc",sum(miss.values()))
for k,v in sorted(miss.items(),key=lambda x:-x[1]): print(f"{k:40} {v:5}/{tot[k]}")
