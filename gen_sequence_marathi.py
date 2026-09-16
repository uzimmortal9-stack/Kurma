import urllib.request, json, base64, os
API_KEY = "sk_da8ttogf_h7ZjLkuwPrHdxc3la5v3FcfG"
URL = "https://api.sarvam.ai/text-to-speech"
prompts = {
 "kurma_river": "हे पाहा, पवित्र चंद्रभागा नदी, जिच्या तीरावर भक्तीचा वास आहे.",
 "kurma_warkari": "नदीच्या मागे, वारकरी भक्तांच्या भव्य मूर्ती उजळून निघत आहेत.",
 "kurma_temple": "आणि आता, या पवित्र धुक्यामध्ये, साक्षात विठ्ठल आणि रखुमाईचे दर्शन घ्या.",
}
for name, text in prompts.items():
    req = urllib.request.Request(URL, method="POST")
    req.add_header("api-subscription-key", API_KEY)
    req.add_header("Content-Type", "application/json")
    data = {"inputs":[text],"target_language_code":"mr-IN","speaker":"shubh","model":"bulbul:v3"}
    try:
        r = urllib.request.urlopen(req, data=json.dumps(data).encode())
        b = json.loads(r.read().decode())["audios"][0]
        open(f"raw/{name}.wav","wb").write(base64.b64decode(b))
        print("OK", name, os.path.getsize(f"raw/{name}.wav"))
    except Exception as e:
        print("FAIL", name, e)
        if hasattr(e,'read'): print(e.read().decode())
