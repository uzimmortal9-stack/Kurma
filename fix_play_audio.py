import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

target = """    private fun playAudio() {
        if (mediaPlayer?.isPlaying == true) {
            // Already playing
            return
        }
        
        runOnUiThread {
            statusMessage = "MOTION DETECTED!\\nStarting Sequence..."
        }
        
        playSequenceStep(0)
    }"""
    
replacement = """    private fun playAudio() {
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
    
content = content.replace(target, replacement)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
