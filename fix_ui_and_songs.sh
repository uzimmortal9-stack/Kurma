# Remove the hardcoded songs list
sed -i '/private val songs = listOf(/,/    )/d' app/src/main/java/com/example/MainActivity.kt

# Add a mutable list of song files
sed -i '/private val sarvamManager = SarvamApiManager()/a \    private var songFiles = listOf<File>()' app/src/main/java/com/example/MainActivity.kt

