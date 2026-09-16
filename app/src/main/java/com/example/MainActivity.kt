package com.example

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), SerialInputOutputManager.Listener {

    private val ACTION_USB_PERMISSION = "com.aistudio.kurma.orch.USB_PERMISSION"
    private var usbPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var mediaPlayer: MediaPlayer? = null
    
    private var statusMessage by mutableStateOf("Waiting for Arduino via USB OTG...")
    
    private val serialBuffer = StringBuilder()
    
    private var idleTimerJob: Job? = null
    private var isIdleMode = false
    private var customSongUris by androidx.compose.runtime.mutableStateOf(listOf<android.net.Uri>())

    private val pickAudioLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
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
    }

    private val songs = listOf(
        R.raw.gajanana_1, R.raw.gajanana_2, R.raw.gajanana_3,
        R.raw.gajanana_4, R.raw.gajanana_5, R.raw.gajanana_6,
        R.raw.gajanana_7, R.raw.gajanana_8, R.raw.gajanana_9,
        R.raw.gajanana_10, R.raw.gajanana_11
    )
    
    private val preGeneratedStories = listOf(
        R.raw.story_1,
        R.raw.story_2,
        R.raw.story_3,
        R.raw.story_4,
        R.raw.story_5,
        R.raw.story_6
    )

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.apply {
                                connectToDevice(this)
                            }
                        } else {
                            statusMessage = "USB permission denied"
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    connectToArduino()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    disconnect()
                    statusMessage = "Arduino disconnected"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("MakharPrefs", Context.MODE_PRIVATE)
        val savedUris = prefs.getStringSet("custom_songs", emptySet())?.mapNotNull {
            try { android.net.Uri.parse(it) } catch(e: Exception) { null }
        } ?: emptyList()
        customSongUris = savedUris
        
        val filter = IntentFilter(ACTION_USB_PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
        
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(innerPadding).fillMaxSize(), 
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Text(
                            text = statusMessage, 
                            textAlign = TextAlign.Center,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = 16.dp))
                        androidx.compose.material3.Button(onClick = { 
                            pickAudioLauncher.launch(arrayOf("audio/*")) 
                        }) {
                            Text("Upload Custom Songs")
                        }
                        if (customSongUris.isNotEmpty()) {
                            Text(
                                text = "${customSongUris.size} custom songs loaded.",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectToArduino()
    }

    private fun connectToArduino() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        
        if (availableDrivers.isEmpty()) {
            statusMessage = "No USB serial devices found"
            return
        }

        val driver = availableDrivers[0]
        val device = driver.device

        if (usbManager.hasPermission(device)) {
            connectToDevice(device)
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flags)
            usbManager.requestPermission(device, permissionIntent)
            statusMessage = "Requesting USB permission..."
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        if (usbPort != null) {
            disconnect()
        }
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            statusMessage = "No driver for device"
            return
        }

        usbPort = driver.ports[0]
        try {
            val connection = usbManager.openDevice(driver.device)
            if (connection == null) {
                statusMessage = "Failed to open device"
                return
            }

            usbPort?.open(connection)
            usbPort?.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            usbPort?.dtr = true
            usbPort?.rts = true
            
            ioManager = SerialInputOutputManager(usbPort, this)
            Executors.newSingleThreadExecutor().submit(ioManager)
            
            statusMessage = "Connected to Arduino at 9600 baud"
        } catch (e: Exception) {
            statusMessage = "Error connecting: ${e.message}"
            disconnect()
        }
    }

    private fun disconnect() {
        ioManager?.stop()
        ioManager = null
        try {
            usbPort?.close()
        } catch (e: Exception) {
            // Ignored
        }
        usbPort = null
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        disconnect()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onNewData(data: ByteArray?) {
        data?.let {
            val text = String(it)
            serialBuffer.append(text)
            
            val content = serialBuffer.toString()
            if (content.contains("\n")) {
                val lines = content.split("\n")
                serialBuffer.clear()
                if (!content.endsWith("\n")) {
                    serialBuffer.append(lines.last())
                }
                
                val completedLines = if (content.endsWith("\n")) lines else lines.dropLast(1)
                
                for (line in completedLines) {
                    val cleanLine = line.trim()
                    if (cleanLine == "MOTION") {
                        playAudio()
                    }
                }
            }
        }
    }

    private fun sendCommandToArduino(command: String) {
        try {
            usbPort?.write(command.toByteArray(), 500)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playSequenceStep(stepIndex: Int) {
        val steps = listOf(
            Pair(R.raw.kurma_welcome_mr, "K_ON\n"),
            Pair(R.raw.kurma_river, "R_ON\n"),
            Pair(R.raw.kurma_warkari, "W_ON\n"),
            Pair(R.raw.kurma_temple, "T_ON\n")
        )
        
        val stepNames = listOf(
            "Welcome (Marathi)", "River", "Warkari", "Temple"
        )

        if (stepIndex >= steps.size) {
            runOnUiThread {
                resetIdleTimer()
                statusMessage = "Sequence Finished.\nWaiting for motion or idle timeout (1 min)."
            }
            return
        }

        val (audioRes, command) = steps[stepIndex]
        
        runOnUiThread {
            statusMessage = "Playing Sequence: ${stepNames[stepIndex]}..."
        }
        
        sendCommandToArduino(command)
        
        try {
            mediaPlayer?.release()
            val attrs = android.media.AudioAttributes.Builder().setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(android.media.AudioAttributes.USAGE_MEDIA).build()
            mediaPlayer = MediaPlayer().apply { setAudioAttributes(attrs); val afd = resources.openRawResourceFd(audioRes); setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close(); prepare() }
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener {
                playSequenceStep(stepIndex + 1)
            }
        } catch (e: Exception) {
            runOnUiThread {
                statusMessage = "Error playing sequence: ${e.message}"
            }
            playSequenceStep(stepIndex + 1)
        }
    }

    private fun playAudio() {
        runOnUiThread {
            if (!isIdleMode) {
                // Sequence is currently running
                if (mediaPlayer?.isPlaying == true) return@runOnUiThread
            }
            
            isIdleMode = false
            idleTimerJob?.cancel()
            
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
            
            statusMessage = "MOTION DETECTED!\nStarting Sequence..."
            playSequenceStep(0)
        }
    }

    private fun resetIdleTimer() {
        isIdleMode = false
        idleTimerJob?.cancel()
        idleTimerJob = lifecycleScope.launch {
            delay(60000)
            onIdleTimeout()
        }
    }

    private fun onIdleTimeout() {
        isIdleMode = true
        statusMessage = "Idle Mode:\nPlaying Marathi songs/stories..."
        playRandomContent()
    }
    
    private fun playRandomContent() {
        if (!isIdleMode) return
        
        val isStory = Math.random() > 0.6 // 40% chance for story, 60% for song
        
        try {
            mediaPlayer?.release()
            val attrs = android.media.AudioAttributes.Builder().setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(android.media.AudioAttributes.USAGE_MEDIA).build()
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(attrs)
                val transitionAudio = if (isStory) R.raw.transition_story_mr else R.raw.transition_song_mr
                val afd = resources.openRawResourceFd(transitionAudio)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
            }
            
            runOnUiThread {
                statusMessage = "Idle Mode:\nPlaying transition..."
                sendCommandToArduino("K_ON\n") // Kurma speaks transition
            }
            
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener {
                if (!isIdleMode) return@setOnCompletionListener
                
                if (isStory) {
                    playActualStory()
                } else {
                    playActualSong()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (isIdleMode) playRandomContent()
        }
    }
    
    private fun playActualStory() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                val afd = resources.openRawResourceFd(preGeneratedStories.random())
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
            }
            runOnUiThread {
                statusMessage = "Idle Mode:\nTelling a story..."
                sendCommandToArduino("K_ON\n") // Kurma tells story
            }
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener {
                if (isIdleMode) playRandomContent()
            }
        } catch (e: Exception) {
            if (isIdleMode) playRandomContent()
        }
    }

    private fun playActualSong() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                if (customSongUris.isNotEmpty()) {
                    setDataSource(this@MainActivity, customSongUris.random())
                } else if (songs.isNotEmpty()) {
                    val afd = resources.openRawResourceFd(songs.random())
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                } else {
                    if (isIdleMode) playRandomContent()
                    return
                }
                prepare()
            }
            runOnUiThread {
                statusMessage = "Idle Mode:\nPlaying a song..."
                sendCommandToArduino("ALL_OFF\n") // Kurma stops speaking for song
            }
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener {
                if (isIdleMode) playRandomContent()
            }
        } catch (e: Exception) {
            if (isIdleMode) playRandomContent()
        }
    }

    override fun onRunError(e: Exception?) {
        runOnUiThread {
            statusMessage = "USB Serial Error: ${e?.message}"
        }
        disconnect()
    }
}
