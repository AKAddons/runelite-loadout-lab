import sys,re,os
def clean(t):
    t=re.sub(r'<ref[^>/]*/>','',t)
    t=re.sub(r'<ref[^>]*>.*?</ref>','',t,flags=re.S)
    t=re.sub(r'<!--.*?-->','',t,flags=re.S)
    t=re.sub(r'\[\[File:[^\[\]]*(\[\[[^\]]*\]\][^\[\]]*)*\]\]','',t)
    t=re.sub(r'\[\[(?:[^|\]]*\|)?([^\]]*)\]\]',r'\1',t)
    t=re.sub(r"'''?",'',t)
    t=re.sub(r'\{\{(?:[Cc]ite|[Cc]itation|[Ss]ync|[Rr]eflist|[Cc]lear|[Ee]xternal|[Hh]as |Main|See also|[Ii]nfobox Bonuses)[^{}]*\}\}','',t)
    t=re.sub(r'\{\{[Hh]itsplat\|([^|}]*)[^}]*\}\}',r'[\1 hitsplat]',t)
    t=re.sub(r'\{\{(?:[Pp]link|[Ss]link|[Cc]hatbox|[Ff]loortext)\|([^|}]*)[^}]*\}\}',r'\1',t)
    t=re.sub(r'\{\{[Ss]CP\|([^|}]*)\|?([^|}]*)[^}]*\}\}',r'\2 \1',t)
    t=re.sub(r'\n{3,}','\n\n',t)
    return t
def sections(t):
    parts=re.split(r'(?m)^(=+[^=\n]+=+)\s*$',t)
    out=[("INTRO",parts[0])]
    for i in range(1,len(parts),2): out.append((parts[i].strip(),parts[i+1]))
    return out
if __name__=="__main__":
    fn=sys.argv[1]; mode=sys.argv[2] if len(sys.argv)>2 else "heads"
    t=clean(open(fn,encoding='utf-8').read())
    secs=sections(t)
    if mode=="heads":
        print(os.path.basename(fn), [ (h,len(b)) for h,b in secs])
    else:
        rx=re.compile(mode,re.I)
        skip=re.compile(sys.argv[3],re.I) if len(sys.argv)>3 else None
        for h,b in secs:
            if rx.search(h) and not (skip and skip.search(h)):
                print(h); print(b.strip()[:int((sys.argv[4] if len(sys.argv)>4 else 4000))]); print()
