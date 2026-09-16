import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

target = """    private fun playRandomSong() {
        if (isIdleMode && Math.random() > 0.7) {
            tellRandomStory()
            return
        }"""
        
replacement = """    private fun playRandomSong() {
        if (isIdleMode && Math.random() > 0.7) {
            tellRandomStory()
            return
        }
        if (isIdleMode) {
            // Ensure Kurma stops talking during idle music
            sendCommandToArduino("ALL_OFF\\n")
        }"""
content = content.replace(target, replacement)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
