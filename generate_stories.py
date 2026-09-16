import urllib.request
import json
import base64
import os

API_KEYS = [
    "sk_da8ttogf_h7ZjLkuwPrHdxc3la5v3FcfG",
    "sk_ygranrnk_IGQs99FEIT6SqzzNh9oVH3IW",
    "sk_xt4vvbng_3XvLW87qTXDzyh4XO3mBRseY"
]
TTS_URL = "https://api.sarvam.ai/text-to-speech"

stories = [
    ("story_ganesha", "एक बार भगवान गणेश और कार्तिकेय में प्रतियोगिता हुई कि ब्रह्मांड की परिक्रमा पहले कौन करेगा। गणेश जी ने अपने माता-पिता शिव-पार्वती की परिक्रमा की और कहा कि आप ही मेरा ब्रह्मांड हैं। उनकी बुद्धिमानी देखकर सभी प्रसन्न हुए।"),
    ("story_vitthal", "भक्त पुंडरीक अपने माता-पिता की सेवा में इतने मग्न थे कि जब भगवान कृष्ण उनसे मिलने आए, तो पुंडरीक ने उन्हें खड़े रहने के लिए एक ईंट दे दी। भगवान उनकी मातृ-पितृ भक्ति से इतने प्रसन्न हुए कि वे उसी ईंट पर विट्ठल रूप में हमेशा के लिए खड़े हो गए।"),
    ("story_devotion", "बप्पा और विट्ठल दोनों ही अपने भक्तों के भाव के भूखे हैं। मोदक और तुलसी का पत्ता, दोनों ही सच्ची श्रद्धा से अर्पण किए जाएं, तो भगवान तुरंत प्रसन्न हो जाते हैं।")
]

os.makedirs("app/src/main/res/raw", exist_ok=True)

for i, (filename, text) in enumerate(stories):
    data = {
        "inputs": [text],
        "target_language_code": "hi-IN",
        "speaker": "shubh",
        "model": "bulbul:v3"
    }
    
    req = urllib.request.Request(TTS_URL, data=json.dumps(data).encode('utf-8'))
    req.add_header('Content-Type', 'application/json')
    req.add_header('api-subscription-key', API_KEYS[i % len(API_KEYS)])
    
    try:
        with urllib.request.urlopen(req) as response:
            res_body = response.read().decode('utf-8')
            res_json = json.loads(res_body)
            base64_audio = res_json['audios'][0]
            audio_bytes = base64.b64decode(base64_audio)
            
            # Save raw bytes to file (assuming Sarvam returns WAV encoded in Base64)
            # Actually, sometimes it's raw PCM, sometimes WAV. Let's save it directly.
            file_path = f"app/src/main/res/raw/{filename}.wav"
            with open(file_path, "wb") as f:
                f.write(audio_bytes)
            print(f"Saved {file_path} ({len(audio_bytes)} bytes)")
    except Exception as e:
        print(f"Error generating {filename}: {e}")

