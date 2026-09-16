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
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
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
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), SerialInputOutputManager.Listener {

    private val ACTION_USB_PERMISSION = "com.aistudio.kurma.orch.USB_PERMISSION"
    private var usbPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var mediaPlayer: MediaPlayer? = null
    
    private var statusMessage by mutableStateOf("Waiting for Arduino via USB OTG...")
    
    private val serialBuffer = StringBuilder()
    
    private var isChatbotModeActive = false
    private var customSongUris by androidx.compose.runtime.mutableStateOf(listOf<android.net.Uri>())

    private val pickAudioLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            customSongUris = customSongUris + uris
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) { e.printStackTrace() }
            }
            runOnUiThread { statusMessage = "Added ${uris.size} custom songs! (Total: ${customSongUris.size})" }
        }
    }

    private var audioRecorder: AudioRecorder? = null
    private val llmManager = LlmKeyManager()
    private val sarvamManager = SarvamApiManager()
    private var songFiles = listOf<File>()
    private val songs = listOf(
        R.raw.gajanana_1,
        R.raw.gajanana_2,
        R.raw.gajanana_3,
        R.raw.gajanana_4,
        R.raw.gajanana_5,
        R.raw.gajanana_6,
        R.raw.gajanana_7,
        R.raw.gajanana_8,
        R.raw.gajanana_9,
        R.raw.gajanana_10,
        R.raw.gajanana_11
    )


    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let { connectToDevice(it) }
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
        enableEdgeToEdge()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
            }
        }
        
        val filter = IntentFilter(ACTION_USB_PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
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
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
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
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
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
        audioRecorder?.stopListening()
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
                // Keep the last part in the buffer if it doesn't end with \n
                serialBuffer.clear()
                if (!content.endsWith("\n")) {
                    serialBuffer.append(lines.last())
                }
                
                // Process completed lines
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
            Pair(R.raw.kurma_welcome, "K_ON\n"),
            Pair(R.raw.kurma_river, "R_ON\n"),
            Pair(R.raw.kurma_warkari, "W_ON\n"),
            Pair(R.raw.kurma_temple, "T_ON\n"),
            Pair(R.raw.kurma_bappa_chat, "K_ON\n")
        )
        
        val stepNames = listOf(
            "Welcome", "River", "Warkari", "Temple", "Bappa Chatbot Intro"
        )

        if (stepIndex >= steps.size) {
            runOnUiThread {
                startChatbotMode()
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
            val attrs = android.media.AudioAttributes.Builder().setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION).build()
            mediaPlayer = MediaPlayer().apply { setAudioAttributes(attrs); val afd = resources.openRawResourceFd(audioRes); setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close(); prepare() }
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener {
                // Send OFF command for the previous part (optional, depending on Arduino logic)
                // sendCommandToArduino("OFF\n")
                playSequenceStep(stepIndex + 1)
            }
        } catch (e: Exception) {
            runOnUiThread {
                statusMessage = "Error playing sequence: ${e.message}"
            }
            // Proceed to next step even if error occurs
            playSequenceStep(stepIndex + 1)
        }
    }

    private fun playAudio() {
        if (mediaPlayer?.isPlaying == true) {
            // Already playing
            return
        }
        
        runOnUiThread {
            statusMessage = "MOTION DETECTED!\nStarting Sequence..."
        }
        
        playSequenceStep(0)
    }

    private fun startChatbotMode() {
        isChatbotModeActive = true
        statusMessage = "Chatbot Mode Active.\nListening for voice or playing songs..."
        playRandomSong()
        
        audioRecorder = AudioRecorder { wavFile ->
            handleUserSpeech(wavFile)
        }
        
        val tempWav = File(cacheDir, "user_audio.wav")
        audioRecorder?.startListening(tempWav)
    }

    private fun handleUserSpeech(wavFile: File) {
        audioRecorder?.stopListening()
        
        runOnUiThread {
            mediaPlayer?.pause()
            statusMessage = "Processing your question..."
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            val transcript = sarvamManager.transcribeAudio(wavFile)
            if (transcript.isNullOrBlank() || transcript.length < 3) {
                return@launch
            }
            
            runOnUiThread {
                statusMessage = "You: $transcript\nThinking..."
            }
            
            val llmResponse = llmManager.generateResponse(transcript)
            
            runOnUiThread {
                statusMessage = "Kurma: $llmResponse\nGenerating speech..."
            }
            
            
            val audioBytes = sarvamManager.textToSpeech(llmResponse)
            
            if (success) {
                runOnUiThread {
                    statusMessage = "Kurma: $llmResponse\nSpeaking..."
                    sendCommandToArduino("K_ON\n")
                    
                    try {
                        mediaPlayer?.release()
                        val attrs = android.media.AudioAttributes.Builder().setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION).build()
                        mediaPlayer = MediaPlayer().apply { setAudioAttributes(attrs); setDataSource(ttsFile.absolutePath); prepare() }
                        mediaPlayer?.start()
                        mediaPlayer?.setOnCompletionListener {
                        }
                    } catch(e: Exception) {
                    }
                }
            }
        }
    }
    
        runOnUiThread {
            statusMessage = "Chatbot Mode:\nListening and playing..."
            playRandomSong()
            val tempWav = File(cacheDir, "user_audio.wav")
            audioRecorder?.startListening(tempWav)
        }
    }

    private fun playRandomSong() {
        if (!isChatbotModeActive) return
        try {
            mediaPlayer?.release()
            val attrs = android.media.AudioAttributes.Builder().setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION).build()
            mediaPlayer = MediaPlayer().apply { 
                setAudioAttributes(attrs)
                if (customSongUris.isNotEmpty()) {
                    val randomUri = customSongUris.random()
                    setDataSource(this@MainActivity, randomUri)
                } else if (songs.isNotEmpty()) {
                    val randomSong = songs.random()
                    val afd = resources.openRawResourceFd(randomSong)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    return
                }
                prepare() 
            }
            mediaPlayer?.start()
            
            runOnUiThread {
                if (statusMessage.startsWith("Chatbot Mode")) {
                    statusMessage = "Chatbot Mode:\nPlaying a Vitthal song randomly..."
                }
            }
            
            mediaPlayer?.setOnCompletionListener {
                playRandomSong()
            }
        } catch (e: Exception) {
            runOnUiThread {
                statusMessage = "Error playing song: ${e.message}"
            }
        }
    }

    override fun onRunError(e: Exception?) {
        runOnUiThread {
            statusMessage = "USB Serial Error: ${e?.message}"
        }
        disconnect()
    }
}
