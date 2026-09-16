import sys

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

target = """            if (audioBytes != null) {
                runOnUiThread {
                    statusMessage = "Kurma: $llmResponse\\nSpeaking..."
                    sendCommandToArduino("K_ON\\n")
                    
                    try {
                        mediaPlayer?.release()
                        val attrs = android.media.AudioAttributes.Builder().setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION).build()
                        mediaPlayer = MediaPlayer().apply { setAudioAttributes(attrs); setDataSource(ttsFile.absolutePath); prepare() }
                        mediaPlayer?.start()
                        mediaPlayer?.setOnCompletionListener {
                            resumeChatbotMode()
                        }
                    } catch(e: Exception) {
                        resumeChatbotMode()
                    }
                }
            } else {
                resumeChatbotMode()
            }"""

replacement = """            if (audioBytes != null) {
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
            }"""

if "if (success) {" in content:
    # Ah wait, I already replaced 'success' with 'audioBytes' but not the inner body!
    pass

# Let's just find where audioBytes is and replace until the end of the method
