import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Add imports for coroutines Job and delay if missing
if 'import kotlinx.coroutines.Job' not in content:
    content = content.replace('import kotlinx.coroutines.launch', 'import kotlinx.coroutines.launch\nimport kotlinx.coroutines.Job\nimport kotlinx.coroutines.delay')

# Add variables to MainActivity
if 'private var idleTimerJob: Job? = null' not in content:
    content = content.replace('private var isChatbotModeActive = false', 
        'private var isChatbotModeActive = false\n'
        '    private var idleTimerJob: Job? = null\n'
        '    private var isManualRecording by mutableStateOf(false)')

# Modify UI
ui_target = """                        androidx.compose.material3.Button(onClick = { 
                            pickAudioLauncher.launch(arrayOf("audio/*")) 
                        }) {
                            Text("Upload Custom Songs")
                        }"""
ui_replacement = """                        androidx.compose.material3.Button(onClick = { 
                            pickAudioLauncher.launch(arrayOf("audio/*")) 
                        }) {
                            Text("Upload Custom Songs")
                        }
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                        if (isChatbotModeActive) {
                            androidx.compose.material3.Button(onClick = { 
                                if (isManualRecording) {
                                    isManualRecording = false
                                    audioRecorder?.stopListening()
                                } else {
                                    isManualRecording = true
                                    audioRecorder?.startManualRecording()
                                    statusMessage = "Listening... Tap 'Stop & Send' when done."
                                }
                            }) {
                                Text(if (isManualRecording) "Stop & Send" else "Hold/Tap to Speak to Kurma")
                            }
                        }"""
content = content.replace(ui_target, ui_replacement)

# Update startChatbotMode
chatbot_target = """    private fun startChatbotMode() {
        isChatbotModeActive = true
        statusMessage = "Chatbot Mode Active.\\nListening for voice or playing songs..."
        playRandomSong()
        
        audioRecorder = AudioRecorder { wavFile ->
            handleUserSpeech(wavFile)
        }
        
        val tempWav = File(cacheDir, "user_audio.wav")
        audioRecorder?.startListening(tempWav)
    }"""
    
chatbot_replacement = """    private fun resetIdleTimer() {
        idleTimerJob?.cancel()
        idleTimerJob = lifecycleScope.launch {
            delay(60000)
            onIdleTimeout()
        }
    }

    private fun onIdleTimeout() {
        sendCommandToArduino("ALL_OFF\\n")
        statusMessage = "Idle Mode: All electronics off.\\nPlaying songs/stories..."
        playRandomSong()
    }

    private fun startChatbotMode() {
        isChatbotModeActive = true
        statusMessage = "Chatbot Mode Active.\\nListening for voice..."
        
        audioRecorder = AudioRecorder { wavFile ->
            handleUserSpeech(wavFile)
        }
        
        val tempWav = File(cacheDir, "user_audio.wav")
        audioRecorder?.startListening(tempWav)
        
        resetIdleTimer()
    }"""
content = content.replace(chatbot_target, chatbot_replacement)

# Update handleUserSpeech
handle_speech_target = """        lifecycleScope.launch(Dispatchers.IO) {
            val transcript = sarvamManager.transcribeAudio(wavFile)
            if (transcript.isNullOrBlank() || transcript.length < 3) {
                resumeChatbotMode()
                return@launch
            }"""
handle_speech_replacement = """        resetIdleTimer()
        lifecycleScope.launch(Dispatchers.IO) {
            val transcript = sarvamManager.transcribeAudio(wavFile)
            if (transcript.isNullOrBlank() || transcript.length < 3) {
                resumeChatbotMode()
                return@launch
            }"""
content = content.replace(handle_speech_target, handle_speech_replacement)

# Update resumeChatbotMode
resume_target = """    private fun resumeChatbotMode() {
        runOnUiThread {
            statusMessage = "Chatbot Mode:\\nListening and playing..."
            playRandomSong()
            val tempWav = File(cacheDir, "user_audio.wav")
            audioRecorder?.startListening(tempWav)
        }
    }"""
resume_replacement = """    private fun resumeChatbotMode() {
        runOnUiThread {
            isManualRecording = false
            if (idleTimerJob?.isActive == true) {
                statusMessage = "Chatbot Mode:\\nListening..."
                mediaPlayer?.pause()
            } else {
                statusMessage = "Idle Mode:\\nListening and playing..."
                if (mediaPlayer?.isPlaying != true) {
                    playRandomSong()
                }
            }
            val tempWav = File(cacheDir, "user_audio.wav")
            audioRecorder?.startListening(tempWav)
        }
    }"""
content = content.replace(resume_target, resume_replacement)

# Fix playRandomSong (remove "if (!isChatbotModeActive) return") since we want it to play during idle
play_target = """    private fun playRandomSong() {
        if (!isChatbotModeActive) return"""
play_replacement = """    private fun playRandomSong() {"""
content = content.replace(play_target, play_replacement)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
