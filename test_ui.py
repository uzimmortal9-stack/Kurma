import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

target = """                        if (isChatbotModeActive) {
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
                        
replacement = """                        if (isChatbotModeActive && !isIdleMode) {
                            androidx.compose.material3.Button(onClick = { 
                                if (isManualRecording) {
                                    isManualRecording = false
                                    // Let AudioRecorder know manual recording is done so it processes the audio
                                    audioRecorder?.stopListening()
                                } else {
                                    isManualRecording = true
                                    // Start a new session explicitly for manual recording
                                    val tempWav = File(cacheDir, "user_audio.wav")
                                    audioRecorder?.stopListening()
                                    audioRecorder?.startManualRecording()
                                    audioRecorder?.startListening(tempWav)
                                    statusMessage = "Listening... Tap 'Stop & Send' when done."
                                    resetIdleTimer()
                                }
                            }) {
                                Text(if (isManualRecording) "Stop & Send" else "Hold/Tap to Speak to Kurma")
                            }
                        }"""
content = content.replace(target, replacement)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
