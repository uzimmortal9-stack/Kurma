import re

with open('app/src/main/java/com/example/AudioRecorder.kt', 'r') as f:
    content = f.read()

# Make sure isManualRecording starts collecting data immediately if requested
target = """            var tempPcmData = mutableListOf<Short>()
            
            while (isListening) {"""
            
replacement = """            var tempPcmData = mutableListOf<Short>()
            
            while (isListening) {
                if (isManualRecording && !isSpeaking) {
                    isSpeaking = true
                    tempPcmData.clear()
                }"""
content = content.replace(target, replacement)

with open('app/src/main/java/com/example/AudioRecorder.kt', 'w') as f:
    f.write(content)
