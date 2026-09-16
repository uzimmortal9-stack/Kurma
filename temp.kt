            if (audioBytes != null) {
                runOnUiThread {
                    statusMessage = "Kurma: $llmResponse\nSpeaking..."
                    sendCommandToArduino("K_ON\n")
                }
                
                try {
                    mediaPlayer?.release()
                    mediaPlayer = null
                    val audioTrack = android.media.AudioTrack(
                        android.media.AudioManager.STREAM_MUSIC,
                        22050,
                        android.media.AudioFormat.CHANNEL_OUT_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT,
                        audioBytes.size,
                        android.media.AudioTrack.MODE_STATIC
                    )
                    
                    // If bytes contains a WAV header, skip the first 44 bytes to play raw PCM safely
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
