import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Let's see if we should add the stories to the raw resources list.
# Currently playRandomSong() randomly plays a song from `songs` list or `tellRandomStory()`.

target = """    private fun tellRandomStory() {
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
                    currentAudioTrack = android.media.AudioTrack(
                        android.media.AudioManager.STREAM_MUSIC,
                        22050,
                        android.media.AudioFormat.CHANNEL_OUT_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT,
                        audioBytes.size,
                        android.media.AudioTrack.MODE_STATIC
                    )
                    val startOffset = if (audioBytes.size > 44 && audioBytes[0] == 'R'.code.toByte() && audioBytes[1] == 'I'.code.toByte()) 44 else 0
                    val length = audioBytes.size - startOffset
                    currentAudioTrack?.write(audioBytes, startOffset, length)
                    currentAudioTrack?.play()
                    val durationMs = (length / (22050.0 * 2)) * 1000
                    kotlinx.coroutines.delay(durationMs.toLong() + 300)
                    currentAudioTrack?.release()
                    currentAudioTrack = null
                    
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
    }"""

replacement = """    private val preGeneratedStories = listOf(
        R.raw.story_ganesha,
        R.raw.story_vitthal,
        R.raw.story_devotion
    )

    private fun tellRandomStory() {
        try {
            runOnUiThread {
                statusMessage = "Idle Mode:\\nTelling a story..."
                sendCommandToArduino("K_ON\\n")
            }
            mediaPlayer?.release()
            val attrs = android.media.AudioAttributes.Builder().setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION).build()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(attrs)
                val randomStory = preGeneratedStories.random()
                val afd = resources.openRawResourceFd(randomStory)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
            }
            mediaPlayer?.start()
            
            mediaPlayer?.setOnCompletionListener {
                if (isIdleMode) playRandomSong()
            }
        } catch (e: Exception) {
            if (isIdleMode) playRandomSong()
        }
    }"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
