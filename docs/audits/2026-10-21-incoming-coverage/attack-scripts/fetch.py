import sys, json, os, re
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from q import api
OUT=sys.argv[1]
os.makedirs(OUT+"/raw",exist_ok=True)
monsters=["Blood Moon","Blue Moon","Eclipse Moon","Tormented Demon","The Whisperer","Vardorvis","Duke Sucellus","The Leviathan","Nex","Abyssal Sire","Dagannoth Rex","Dagannoth Prime","Dagannoth Supreme","The Nightmare","Corrupted Hunllef","Phantom Muspah","Araxxor","The Hueycoatl","Branda the Fire Queen","Eldric the Ice King","Amoxliatl","Yama","Doom of Mokhaiotl","Sarachnis","Kalphite Queen","Thermonuclear smoke devil","Dusk","Dawn","Scurrius"]
pages=monsters+["Moons of Peril","Moons of Peril/Strategies","Royal Titans","Royal Titans/Strategies","Dagannoth Kings","Dagannoth Kings/Strategies","Grotesque Guardians","Grotesque Guardians/Strategies","The Gauntlet/Strategies"]
for m in monsters:
    if m not in("Dusk","Dawn","Dagannoth Rex","Dagannoth Prime","Dagannoth Supreme","Branda the Fire Queen","Eldric the Ice King","Blood Moon","Blue Moon","Eclipse Moon"):
        pages.append(m+"/Strategies")
# bucket
bk={}
for m in monsters:
    q="bucket('infobox_monster').select('page_name','name','version_anchor','max_hit','attack_style','attack_speed','combat_level','hitpoints','poisonous').where('page_name','%s').run()"%m.replace("'","\'")
    try:
        bk[m]=api({"action":"bucket","query":q}).get("bucket")
    except Exception as e:
        bk[m]={"error":str(e)}
    print("bucket",m,len(bk[m]) if isinstance(bk[m],list) else bk[m])
json.dump(bk,open(OUT+"/raw/bucket_infobox_monster.json","w",encoding="utf-8"),indent=1,ensure_ascii=False)
for p in pages:
    fn=OUT+"/raw/"+re.sub(r"[^A-Za-z0-9]+","_",p)+".wiki"
    if os.path.exists(fn): continue
    try:
        d=api({"action":"parse","page":p,"prop":"wikitext","redirects":1})
        if "error" in d: print("ERR",p,d["error"].get("info")); continue
        open(fn,"w",encoding="utf-8").write(d["parse"]["wikitext"]["*"])
        print("page",p,len(d["parse"]["wikitext"]["*"]))
    except Exception as e:
        print("EXC",p,e)
