sed -i 's/val ttsFile = File(cacheDir, "kurma_response.wav")//g' app/src/main/java/com/example/MainActivity.kt
sed -i 's/val success = sarvamManager.textToSpeech(llmResponse, ttsFile)/val audioBytes = sarvamManager.textToSpeech(llmResponse)/g' app/src/main/java/com/example/MainActivity.kt
