import sys, json, urllib.request, urllib.parse, time
UA="LoadoutLab-research/1.0 (contact via GitHub ajkatz)"
def api(params):
    params=dict(params); params.setdefault("format","json")
    url="https://oldschool.runescape.wiki/api.php?"+urllib.parse.urlencode(params)
    req=urllib.request.Request(url,headers={"User-Agent":UA})
    with urllib.request.urlopen(req,timeout=60) as r: data=json.load(r)
    time.sleep(1.0)
    return data
if __name__=="__main__":
    q=sys.argv[1]
    print(json.dumps(api({"action":"bucket","query":q}),indent=1)[:6000])
