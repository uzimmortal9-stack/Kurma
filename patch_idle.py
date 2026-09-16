import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

if 'private var isIdleMode = false' not in content:
    content = content.replace('private var idleTimerJob: Job? = null', 
        'private var idleTimerJob: Job? = null\n    private var isIdleMode = false')

# Update resetIdleTimer
target1 = """    private fun resetIdleTimer() {
        idleTimerJob?.cancel()
        idleTimerJob = lifecycleScope.launch {
            delay(60000)
            onIdleTimeout()
        }
    }"""
replacement1 = """    private fun resetIdleTimer() {
        isIdleMode = false
        idleTimerJob?.cancel()
        idleTimerJob = lifecycleScope.launch {
            delay(60000)
            onIdleTimeout()
        }
    }"""
content = content.replace(target1, replacement1)

# Update onIdleTimeout
target2 = """    private fun onIdleTimeout() {
        sendCommandToArduino("ALL_OFF\\n")
        statusMessage = "Idle Mode: All electronics off.\\nPlaying songs/stories..."
        playRandomSong()
    }"""
replacement2 = """    private fun onIdleTimeout() {
        isIdleMode = true
        sendCommandToArduino("ALL_OFF\\n")
        statusMessage = "Idle Mode: All electronics off.\\nPlaying songs/stories..."
        playRandomSong()
    }"""
content = content.replace(target2, replacement2)

# Update playAudio
target3 = """    private fun playAudio() {
        runOnUiThread {
            isChatbotModeActive = false
            idleTimerJob?.cancel()
            audioRecorder?.stopListening()
            
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
            
            statusMessage = "MOTION DETECTED!\\nStarting Sequence..."
            playSequenceStep(0)
        }
    }"""
replacement3 = """    private fun playAudio() {
        runOnUiThread {
            if (!isIdleMode && !isChatbotModeActive) {
                // Sequence is currently running
                if (mediaPlayer?.isPlaying == true) return@runOnUiThread
            }
            if (isChatbotModeActive && !isIdleMode) {
                // Currently in active chatbot mode, ignore motion
                return@runOnUiThread
            }
            
            isChatbotModeActive = false
            isIdleMode = false
            idleTimerJob?.cancel()
            audioRecorder?.stopListening()
            
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
            
            statusMessage = "MOTION DETECTED!\\nStarting Sequence..."
            playSequenceStep(0)
        }
    }"""
content = content.replace(target3, replacement3)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
