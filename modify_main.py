import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# 1. Update onCreate to load custom songs from SharedPreferences
target_oncreate = """    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val filter = IntentFilter(ACTION_USB_PERMISSION).apply {"""
        
replacement_oncreate = """    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("MakharPrefs", Context.MODE_PRIVATE)
        val savedUris = prefs.getStringSet("custom_songs", emptySet())?.mapNotNull {
            try { android.net.Uri.parse(it) } catch(e: Exception) { null }
        } ?: emptyList()
        customSongUris = savedUris
        
        val filter = IntentFilter(ACTION_USB_PERMISSION).apply {"""
        
content = content.replace(target_oncreate, replacement_oncreate)

# 2. Update pickAudioLauncher to save custom songs to SharedPreferences
target_launcher = """    private val pickAudioLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            customSongUris = customSongUris + uris
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) { e.printStackTrace() }
            }
            runOnUiThread { statusMessage = "Added ${uris.size} custom songs! (Total: ${customSongUris.size})" }
        }
    }"""
    
replacement_launcher = """    private val pickAudioLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            customSongUris = customSongUris + uris
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) { e.printStackTrace() }
            }
            val prefs = getSharedPreferences("MakharPrefs", Context.MODE_PRIVATE)
            prefs.edit().putStringSet("custom_songs", customSongUris.map { it.toString() }.toSet()).apply()
            runOnUiThread { statusMessage = "Added ${uris.size} custom songs! (Total: ${customSongUris.size})" }
        }
    }"""

content = content.replace(target_launcher, replacement_launcher)

# 3. Update preGeneratedStories array
target_stories = """    private val preGeneratedStories = listOf(
        R.raw.story_ganesha_mr,
        R.raw.story_vitthal_mr,
        R.raw.story_devotion_mr
    )"""
    
replacement_stories = """    private val preGeneratedStories = listOf(
        R.raw.story_1,
        R.raw.story_2,
        R.raw.story_3,
        R.raw.story_4,
        R.raw.story_5,
        R.raw.story_6
    )"""

content = content.replace(target_stories, replacement_stories)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
