with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

story_func = """    private fun tellRandomStory() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runOnUiThread { statusMessage = "Idle Mode:\\nGenerating a story..." }
            val storyPrompt = "भगवान विट्ठल या गणपति बप्पा की कोई एक बहुत ही छोटी और मधुर कहानी सुनाएं। (सिर्फ 3-4 लाइन)"
            val llmResponse = llmManager.generateResponse(storyPrompt)
            val audioBytes = sarvamManager.textToSpeech(llmResponse)
            
            if (audioBytes != null) {
                runOnUiThread {
                    statusMessage = "Idle Mode:\\nTelling a story..."
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
                    
                    if (isIdleMode) {
                        runOnUiThread { playRandomSong() }
                    }
                } catch(e: Exception) {
                    if (isIdleMode) runOnUiThread { playRandomSong() }
                }
            } else {
                if (isIdleMode) runOnUiThread { playRandomSong() }
            }
        }
    }

"""

if 'private fun tellRandomStory()' not in content:
    content = content.replace('private fun playRandomSong() {', story_func + '    private fun playRandomSong() {\n        if (isIdleMode && Math.random() > 0.7) {\n            tellRandomStory()\n            return\n        }')

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
