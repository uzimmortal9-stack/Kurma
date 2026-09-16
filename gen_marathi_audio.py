import urllib.request
import json
import base64
import os

API_KEY = "sk_da8ttogf_h7ZjLkuwPrHdxc3la5v3FcfG"
TTS_URL = "https://api.sarvam.ai/text-to-speech"

audios_to_generate = [
    ("kurma_welcome_mr", "नमस्कार! विठ्ठल रुक्मिणी आणि गणपती बाप्पाच्या या सुंदर देखाव्यात तुमचे स्वागत आहे."),
    ("story_ganesha_mr", "एकदा गणपतीने आपले आई-वडील, शिव आणि पार्वती यांनाच प्रदक्षिणा घातली आणि सांगितले की तुम्हीच माझे जग आहात. त्यांची बुद्धिमत्ता पाहून सगळेच थक्क झाले."),
    ("story_vitthal_mr", "पुंडलिक आपल्या आई-वडिलांच्या सेवेत इतका मग्न होता की, जेव्हा विठ्ठल त्याला भेटायला आले, तेव्हा त्याने त्यांना उभे राहण्यासाठी एक वीट दिली. त्याची भक्ती पाहून देव त्याच विटेवर उभे राहिले."),
    ("story_devotion_mr", "बाप्पा आणि विठ्ठल दोघेही भावनेचे भुकेले आहेत. प्रेमाने दिलेला एक मोदक किंवा तुळशीचे पानही त्यांना त्वरित प्रसन्न करते."),
    ("transition_song_mr", "चला, आता आपण एक सुंदर गाणे ऐकूया."),
    ("transition_story_mr", "आता मी तुम्हाला एक छोटीशी गोष्ट सांगतो.")
]

for filename, text in audios_to_generate:
    data = {
        "inputs": [text],
        "target_language_code": "mr-IN",
        "speaker": "shubh",
        "model": "bulbul:v3"
    }
    
    req = urllib.request.Request(TTS_URL, data=json.dumps(data).encode('utf-8'))
    req.add_header('Content-Type', 'application/json')
    req.add_header('api-subscription-key', API_KEY)
    
    try:
        with urllib.request.urlopen(req) as response:
            res_body = response.read().decode('utf-8')
            res_json = json.loads(res_body)
            base64_audio = res_json['audios'][0]
            audio_bytes = base64.b64decode(base64_audio)
            
            file_path = f"app/src/main/res/raw/{filename}.wav"
            with open(file_path, "wb") as f:
                f.write(audio_bytes)
            print(f"Saved {file_path} ({len(audio_bytes)} bytes)")
    except Exception as e:
        print(f"Error generating {filename}: {e}")
        if hasattr(e, 'read'):
            print(e.read().decode('utf-8'))

