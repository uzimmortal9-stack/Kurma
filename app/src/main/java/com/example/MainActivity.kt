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
import android.os.SystemClock
import android.view.WindowManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Kurma Makhar orchestrator.
 *
 * Presence model (one welcome per group of bhakts):
 *
 *   ARMED          -> nobody (as far as we know) is in front of the makhar.
 *                     Ambient songs/stories play. First MOTION starts the welcome sequence.
 *   WELCOME        -> the 4-step welcome sequence is playing. MOTION is ignored (same people).
 *   SHOWCASE       -> for SHOWCASE_MS (10 min) after the sequence the whole makhar stays lit
 *                     so people can keep watching. Ambient songs/stories play.
 *                     MOTION is ignored (same people), it only refreshes lastMotionAt.
 *   WAITING_CLEAR  -> showcase is over, makhar lights off. We do NOT welcome anyone yet,
 *                     because motion here is most likely the same crowd still standing there.
 *                     Only after CLEAR_GAP_MS of continuous no-motion do we conclude the crowd
 *                     left, and go back to ARMED so the next motion is treated as a new bhakt.
 *                     A hard cap (MAX_CLEAR_WAIT_MS) re-arms anyway, so a stuck/noisy PIR can
 *                     never freeze the installation.
 */
class MainActivity : ComponentActivity(), SerialInputOutputManager.Listener {

    private val ACTION_USB_PERMISSION = "com.aistudio.kurma.orch.USB_PERMISSION"

    // ---- Tunables -----------------------------------------------------------------
    private val SHOWCASE_MS = 10 * 60 * 1000L   // keep makhar ON for 10 min after the sequence
    private val CLEAR_GAP_MS = 2 * 60 * 1000L   // no motion for this long = crowd has left
    private val MAX_CLEAR_WAIT_MS = 15 * 60 * 1000L // safety: re-arm even if PIR keeps firing
    private val TICK_MS = 1000L
    private val PING_MS = 60 * 1000L            // keep-alive gap for the Arduino watchdog
    // -------------------------------------------------------------------------------

    private enum class State { ARMED, WELCOME, SHOWCASE, WAITING_CLEAR }

    private var usbPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var mediaPlayer: MediaPlayer? = null

    private val serialExecutor = Executors.newSingleThreadExecutor()
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private var state = State.ARMED
    private var lastPingAt = 0L            // last keep-alive sent to the Arduino
    private var lastMotionAt = 0L          // SystemClock.elapsedRealtime of last MOTION line
    private var motionSeenEver = false
    private var showcaseEndsAt = 0L
    private var waitingClearSince = 0L

    /** Bumped every time playback intent changes, so stale MediaPlayer callbacks are ignored. */
    private var playbackToken = 0

    private var clockJob: Job? = null

    private var statusMessage by mutableStateOf("Waiting for Arduino via USB OTG...")
    private var stateLine by mutableStateOf("State: ARMED")
    private var motionLine by mutableStateOf("Motion: none yet")
    private var songsSinceStory = 0

    private val serialBuffer = StringBuilder()

    private var customSongUris by androidx.compose.runtime.mutableStateOf(listOf<android.net.Uri>())

    private val pickAudioLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
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
        R.raw.story_1, R.raw.story_2, R.raw.story_3,
        R.raw.story_4, R.raw.story_5, R.raw.story_6
    )

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.apply { connectToDevice(this) }
                        } else {
                            statusMessage = "USB permission denied"
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> connectToArduino()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    disconnect()
                    statusMessage = "Arduino disconnected"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("MakharPrefs", Context.MODE_PRIVATE)
        customSongUris = prefs.getStringSet("custom_songs", emptySet())?.mapNotNull {
            try { android.net.Uri.parse(it) } catch (e: Exception) { null }
        } ?: emptyList()

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
                        Text(text = stateLine, textAlign = TextAlign.Center, fontSize = 14.sp)
                        Text(text = motionLine, textAlign = TextAlign.Center, fontSize = 14.sp)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = 16.dp))
                        androidx.compose.material3.Button(onClick = {
                            pickAudioLauncher.launch(arrayOf("audio/*"))
                        }) { Text("Upload Custom Songs") }
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = 8.dp))
                        androidx.compose.material3.Button(onClick = { forceReset() }) {
                            Text("Reset (new group)")
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

        startClock()
        // Nobody has been seen yet: ambient content plays, first motion is a real welcome.
        enterArmed(startAmbient = true)
    }

    override fun onStart() {
        super.onStart()
        connectToArduino()
    }

    // ================================ Serial ======================================

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
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flags)
            usbManager.requestPermission(device, permissionIntent)
            statusMessage = "Requesting USB permission..."
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        if (usbPort != null) disconnect()
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
            ioExecutor.submit(ioManager)

            statusMessage = "Connected to Arduino at 9600 baud"
            // The Arduino resets when the port opens; make sure its outputs match our state.
            applyLightsForState()
        } catch (e: Exception) {
            statusMessage = "Error connecting: ${e.message}"
            disconnect()
        }
    }

    private fun disconnect() {
        ioManager?.stop()
        ioManager = null
        try { usbPort?.close() } catch (e: Exception) { /* ignored */ }
        usbPort = null
    }

    override fun onDestroy() {
        super.onDestroy()
        clockJob?.cancel()
        unregisterReceiver(usbReceiver)
        disconnect()
        mediaPlayer?.release()
        mediaPlayer = null
        serialExecutor.shutdownNow()
        ioExecutor.shutdownNow()
    }

    override fun onNewData(data: ByteArray?) {
        data?.let {
            serialBuffer.append(String(it))
            val content = serialBuffer.toString()
            if (!content.contains("\n")) return
            val lines = content.split("\n")
            serialBuffer.clear()
            if (!content.endsWith("\n")) serialBuffer.append(lines.last())
            val completedLines = if (content.endsWith("\n")) lines else lines.dropLast(1)
            for (line in completedLines) {
                if (line.trim() == "MOTION") runOnUiThread { onMotion() }
            }
        }
    }

    /** All serial writes go through one executor so concurrent writes can never corrupt a command. */
    private fun sendCommandToArduino(command: String) {
        serialExecutor.execute {
            try {
                usbPort?.write(command.toByteArray(), 800)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * One opcode lights the whole makhar for the darshan showcase. The sketch keeps the
     * river motors looping gently and duty-cycles the mist makers so nothing runs dry
     * for the full ten minutes.
     */
    private fun makharAllOn() = sendCommandToArduino("SHOW_ON\n")

    private fun makharAllOff() = sendCommandToArduino("ALL_OFF\n")

    private fun applyLightsForState() {
        when (state) {
            State.SHOWCASE -> makharAllOn()
            State.ARMED, State.WAITING_CLEAR -> makharAllOff()
            State.WELCOME -> { /* the sequence drives its own zones */ }
        }
    }

    // ============================ Presence state machine ===========================

    private fun onMotion() {
        lastMotionAt = SystemClock.elapsedRealtime()
        motionSeenEver = true
        when (state) {
            State.ARMED -> startWelcome()          // a genuinely new bhakt
            State.WELCOME -> { /* same people, ignore */ }
            State.SHOWCASE -> { /* same people watching, ignore; makhar stays on */ }
            State.WAITING_CLEAR -> {
                // Same crowd is still standing here: do NOT replay the welcome.
                // Their motion pushes the "crowd has left" decision further out.
            }
        }
        refreshUi()
    }

    private fun enterArmed(startAmbient: Boolean) {
        state = State.ARMED
        makharAllOff()
        if (startAmbient) startAmbient()
        refreshUi()
    }

    private fun startWelcome() {
        state = State.WELCOME
        playbackToken++
        stopPlayer()
        statusMessage = "New darshan!\nPlaying welcome sequence..."
        refreshUi()
        playSequenceStep(0, playbackToken)
    }

    private fun enterShowcase() {
        state = State.SHOWCASE
        showcaseEndsAt = SystemClock.elapsedRealtime() + SHOWCASE_MS
        makharAllOn()   // whole makhar stays lit for the next 10 minutes
        startAmbient()
        refreshUi()
    }

    private fun enterWaitingClear() {
        state = State.WAITING_CLEAR
        waitingClearSince = SystemClock.elapsedRealtime()
        makharAllOff()
        startAmbient()  // songs/stories keep playing softly, but no welcome and no lights
        refreshUi()
    }

    private fun startClock() {
        clockJob?.cancel()
        clockJob = lifecycleScope.launch {
            while (true) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        // Keep-alive: the sketch shuts the makhar down if the tablet goes silent.
        if (now - lastPingAt >= PING_MS) {
            lastPingAt = now
            sendCommandToArduino("PING\n")
        }
        when (state) {
            State.SHOWCASE -> if (now >= showcaseEndsAt) enterWaitingClear()
            State.WAITING_CLEAR -> {
                val quietFor = now - lastMotionAt
                val waited = now - waitingClearSince
                if (quietFor >= CLEAR_GAP_MS || waited >= MAX_CLEAR_WAIT_MS) {
                    // No continuous movement for the last few minutes: the group that already
                    // had darshan has left. The next person to arrive is a new bhakt.
                    enterArmed(startAmbient = false)
                }
            }
            else -> { }
        }
        refreshUi()
    }

    private fun forceReset() {
        playbackToken++
        stopPlayer()
        lastMotionAt = 0L
        statusMessage = "Reset. Waiting for a new bhakt..."
        enterArmed(startAmbient = true)
    }

    private fun refreshUi() {
        val now = SystemClock.elapsedRealtime()
        stateLine = when (state) {
            State.ARMED -> "State: ARMED - next motion starts the welcome"
            State.WELCOME -> "State: WELCOME sequence playing"
            State.SHOWCASE -> "State: MAKHAR ON - ${secs(showcaseEndsAt - now)} left"
            State.WAITING_CLEAR -> "State: same crowd - re-arms after ${secs(CLEAR_GAP_MS - (now - lastMotionAt))} quiet"
        }
        motionLine = if (!motionSeenEver) "Motion: none yet"
        else "Last motion: ${secs(now - lastMotionAt)} ago"
    }

    private fun secs(ms: Long): String {
        val s = (if (ms < 0) 0 else ms) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    // ================================ Playback =====================================

    private fun stopPlayer() {
        try {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
        } catch (e: Exception) { /* ignored */ }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun speechAttrs() = android.media.AudioAttributes.Builder()
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
        .build()

    private fun playSequenceStep(stepIndex: Int, token: Int) {
        if (token != playbackToken) return

        val steps = listOf(
            Pair(R.raw.kurma_welcome_mr, "K_ON\n"),
            Pair(R.raw.kurma_river, "R_ON\n"),
            Pair(R.raw.kurma_warkari, "W_ON\n"),
            Pair(R.raw.kurma_temple, "T_ON\n")
        )
        val stepNames = listOf("Welcome", "Chandrabhaga", "Warkari", "Vitthal-Rakhumai")

        if (stepIndex >= steps.size) {
            runOnUiThread {
                if (token != playbackToken) return@runOnUiThread
                statusMessage = "Sequence finished.\nMakhar stays lit for 10 minutes."
                enterShowcase()
            }
            return
        }

        val (audioRes, command) = steps[stepIndex]
        runOnUiThread {
            if (token != playbackToken) return@runOnUiThread
            statusMessage = "Welcome sequence: ${stepNames[stepIndex]}..."
        }
        sendCommandToArduino(command)

        try {
            stopPlayer()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(speechAttrs())
                val afd = resources.openRawResourceFd(audioRes)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
            }
            mediaPlayer?.setOnCompletionListener {
                runOnUiThread { playSequenceStep(stepIndex + 1, token) }
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            runOnUiThread {
                statusMessage = "Error playing sequence: ${e.message}"
                playSequenceStep(stepIndex + 1, token)
            }
        }
    }

    private fun ambientAllowed() = state == State.SHOWCASE || state == State.WAITING_CLEAR || state == State.ARMED

    private fun startAmbient() {
        playbackToken++
        stopPlayer()
        playAmbient(playbackToken)
    }

    /** One ambient item: a short Marathi transition line, then a story or a song. */
    private fun playAmbient(token: Int) {
        if (token != playbackToken || !ambientAllowed()) return

        // Guarantee stories actually get heard: at most two songs in a row.
        val isStory = songsSinceStory >= 2 || Math.random() > 0.5
        if (isStory) songsSinceStory = 0 else songsSinceStory++

        try {
            stopPlayer()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(speechAttrs())
                val transitionAudio = if (isStory) R.raw.transition_story_mr else R.raw.transition_song_mr
                val afd = resources.openRawResourceFd(transitionAudio)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
            }
            statusMessage = if (isStory) "Kurma is about to tell a story..." else "Kurma is about to play a song..."
            // NOTE: no lighting commands here any more. During SHOWCASE the whole makhar is
            // already lit and must stay lit; ambient audio never touches the relays.
            mediaPlayer?.setOnCompletionListener {
                runOnUiThread {
                    if (isStory) playStory(token) else playSong(token)
                }
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread { playAmbient(token) }
        }
    }

    private fun playStory(token: Int) {
        if (token != playbackToken || !ambientAllowed()) return
        try {
            stopPlayer()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(speechAttrs())
                val afd = resources.openRawResourceFd(preGeneratedStories.random())
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
            }
            statusMessage = "Telling a story..."
            mediaPlayer?.setOnCompletionListener { runOnUiThread { playAmbient(token) } }
            mediaPlayer?.start()
        } catch (e: Exception) {
            runOnUiThread { playAmbient(token) }
        }
    }

    private fun playSong(token: Int) {
        if (token != playbackToken || !ambientAllowed()) return
        try {
            stopPlayer()
            mediaPlayer = MediaPlayer().apply {
                if (customSongUris.isNotEmpty()) {
                    setDataSource(this@MainActivity, customSongUris.random())
                } else if (songs.isNotEmpty()) {
                    val afd = resources.openRawResourceFd(songs.random())
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                } else {
                    runOnUiThread { playAmbient(token) }
                    return
                }
                prepare()
            }
            statusMessage = "Playing a song..."
            mediaPlayer?.setOnCompletionListener { runOnUiThread { playAmbient(token) } }
            mediaPlayer?.start()
        } catch (e: Exception) {
            runOnUiThread { playAmbient(token) }
        }
    }

    override fun onRunError(e: Exception?) {
        runOnUiThread { statusMessage = "USB Serial Error: ${e?.message}" }
        disconnect()
    }
}
