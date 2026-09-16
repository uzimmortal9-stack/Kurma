sed -i 's/if (transcript.isNullOrBlank()) {/if (transcript.isNullOrBlank() || transcript.length < 3) {/g' app/src/main/java/com/example/MainActivity.kt
