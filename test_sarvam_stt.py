import logging
import httpx
logging.basicConfig(level=logging.DEBUG)

# Just mocking enough to see where the SDK tries to send the request
from sarvamai import SarvamAI
client = SarvamAI(api_subscription_key="sk_da8ttogf_h7ZjLkuwPrHdxc3la5v3FcfG")

# Create a dummy wav file
with open("dummy.wav", "wb") as f:
    f.write(b"RIFF$\x00\x00\x00WAVEfmt \x10\x00\x00\x00\x01\x00\x01\x00\x80\xbb\x00\x00\x00w\x01\x00\x02\x00\x10\x00data\x00\x00\x00\x00")

try:
    client.speech_to_text.transcribe(
        file=open("dummy.wav", "rb"),
        model="saaras:v3"
    )
except Exception as e:
    print(e)
