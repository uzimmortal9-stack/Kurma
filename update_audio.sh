sed -i 's/fun textToSpeech(text: String, outputFile: File): Boolean {/fun textToSpeech(text: String): ByteArray? {/g' app/src/main/java/com/example/SarvamApiManager.kt
sed -i 's/outputFile.writeBytes(audioBytes)/return audioBytes/g' app/src/main/java/com/example/SarvamApiManager.kt
sed -i 's/return true//g' app/src/main/java/com/example/SarvamApiManager.kt
sed -i 's/return false/return null/g' app/src/main/java/com/example/SarvamApiManager.kt
