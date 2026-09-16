import urllib.request
import json

API_KEYS = [
    "sk_da8ttogf_h7ZjLkuwPrHdxc3la5v3FcfG",
    "sk_ygranrnk_IGQs99FEIT6SqzzNh9oVH3IW",
    "sk_xt4vvbng_3XvLW87qTXDzyh4XO3mBRseY"
]
TTS_URL = "https://api.sarvam.ai/text-to-speech"

for key in API_KEYS:
    data = {
        "inputs": ["test"],
        "target_language_code": "mr-IN",
        "speaker": "shubh",
        "model": "bulbul:v3"
    }
    req = urllib.request.Request(TTS_URL, data=json.dumps(data).encode('utf-8'))
    req.add_header('Content-Type', 'application/json')
    req.add_header('api-subscription-key', key)
    try:
        urllib.request.urlopen(req)
        print(f"Key {key} works!")
    except Exception as e:
        print(f"Key {key} failed: {e}")
