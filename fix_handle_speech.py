import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# find the start of handleUserSpeech and resumeChatbotMode
start_idx = content.find('private fun handleUserSpeech(wavFile: File) {')
end_idx = content.find('private fun resumeChatbotMode() {')

if start_idx != -1 and end_idx != -1:
    new_method = """    private fun handleUserSpeech(wavFile: File) {
        audioRecorder?.stopListening()
        
        runOnUiThread {
            mediaPlayer?.pause()
            statusMessage = "Processing your question..."
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            val transcript = sarvamManager.transcribeAudio(wavFile)
            if (transcript.isNullOrBlank() || transcript.length < 3) {
                resumeChatbotMode()
                return@launch
            }
            
            runOnUiThread {
                statusMessage = "You: $transcript\\nThinking..."
            }
            
            val llmResponse = llmManager.generateResponse(transcript)
            
            runOnUiThread {
                statusMessage = "Kurma: $llmResponse\\nGenerating speech..."
            }
            
            val audioBytes = sarvamManager.textToSpeech(llmResponse)
            
            if (audioBytes != null) {
                runOnUiThread {
                    statusMessage = "Kurma: $llmResponse\\nSpeaking..."
                    sendCommandToArduino("K_ON\\n")
                }
                try {
                    runOnUiThread { mediaPlayer?.release() }
                    val audioTrack = android.media.AudioTrack(
                        android.media.AudioManager.STREAM_MUSIC,
                        22050,
                        android.media.AudioFormat.CHANNEL_OUT_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT,
                        audioBytes.size,
                        android.media.AudioTrack.MODE_STATIC
                    )
                    val startOffset = if (audioBytes.size > 44 && audioBytes[0] == 'R'.code.toByte() && audioBytes[1] == 'I'.code.toByte()) 44 else 0
                    val length = audioBytes.size - startOffset
                    audioTrack.write(audioBytes, startOffset, length)
                    audioTrack.play()
                    val durationMs = (length / (22050.0 * 2)) * 1000
                    kotlinx.coroutines.delay(durationMs.toLong() + 300)
                    audioTrack.release()
                    resumeChatbotMode()
                } catch(e: Exception) {
                    resumeChatbotMode()
                }
            } else {
                resumeChatbotMode()
            }
        }
    }
    
    """
    
    final_content = content[:start_idx] + new_method + content[end_idx:]
    with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
        f.write(final_content)
