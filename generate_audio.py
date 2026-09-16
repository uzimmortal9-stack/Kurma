import urllib.request
import json
import base64
import os

API_KEY = "sk_da8ttogf_h7ZjLkuwPrHdxc3la5v3FcfG"
URL = "https://api.sarvam.ai/text-to-speech"

prompts = {
    "kurma_welcome": "नमस्ते! मैं कुर्मा हूँ। हमारे बाप्पा के विट्ठल-रखुमाई मखर में आपका स्वागत है।",
    "kurma_river": "यह देखिए, पवित्र चंद्रभागा नदी, जिसके तट पर भक्ति का वास है।",
    "kurma_warkari": "नदी के पीछे, वारकरी भक्तों की विशाल मूर्तियां रौशन हो रही हैं।",
    "kurma_temple": "और अब, इस पवित्र धुंध के बीच, साक्षात विट्ठल और रखुमाई के दर्शन कीजिए।",
    "kurma_bappa_chat": "विट्ठल और बाप्पा की कृपा से यह मखर सजाया गया है। अब आप मुझसे कुछ भी पूछ सकते हैं।"
}

out_dir = "app/src/main/res/raw"
os.makedirs(out_dir, exist_ok=True)

for name, text in prompts.items():
    print(f"Generating {name}...")
    req = urllib.request.Request(URL, method="POST")
    req.add_header("api-subscription-key", API_KEY)
    req.add_header("Content-Type", "application/json")
    
    data = {
        "inputs": [text],
        "target_language_code": "hi-IN",
        "speaker": "shubh",
        "model": "bulbul:v3"
    }
    
    try:
        response = urllib.request.urlopen(req, data=json.dumps(data).encode("utf-8"))
        res_body = json.loads(response.read().decode("utf-8"))
        audio_base64 = res_body["audios"][0]
        
        with open(os.path.join(out_dir, f"{name}.wav"), "wb") as f:
            f.write(base64.b64decode(audio_base64))
        print(f"Saved {name}.wav")
    except Exception as e:
        print(f"Failed {name}: {e}")

