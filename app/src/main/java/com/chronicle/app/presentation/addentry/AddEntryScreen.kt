package com.chronicle.app.presentation.addentry

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

// ── Brand colours (shared) ────────────────────────────────────────────────────
val MemoraViolet  = Color(0xFF6C63FF)
val MemoraDeep    = Color(0xFF4A3ECC)
val MemoraRed     = Color(0xFFFF6B6B)
val MemoraGreen   = Color(0xFF43E97B)
val MemoraAmber   = Color(0xFFFF8C42)
val MemoraTeal    = Color(0xFF0ABDE3)

// ── Shake detector  sensorevent listner is used────────────────────────────────────────────────────────────
class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
    private var lastTime = 0L
    private var lastX = 0f; private var lastY = 0f; private var lastZ = 0f
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val now = System.currentTimeMillis()
        if (now - lastTime < 100) return
        val diff = now - lastTime
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        val speed = sqrt(((x-lastX)*(x-lastX)+(y-lastY)*(y-lastY)+(z-lastZ)*(z-lastZ)).toDouble()).toFloat() / diff * 10000
        if (speed > 1200) onShake()
        lastTime = now; lastX = x; lastY = y; lastZ = z
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

// ── Helper: create a temp file for camera capture app specific external storage─────────────────────────────
fun createImageFile(context: Context): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val dir = File(context.getExternalFilesDir(null), "Pictures").also { it.mkdirs() }
    return File(dir, "IMG_${timestamp}.jpg")
}

// ── Helper: create audio file ─────────────────────────────────────────────────
fun createAudioFile(context: Context): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val dir = File(context.getExternalFilesDir(null), "Audio").also { it.mkdirs() }
    return File(dir, "AUD_${timestamp}.mp4")
}
//manages the UI State
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(
    onBack: () -> Unit,
    viewModel: AddEntryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    // ── Form state ─────────────────────────────────────────────────────────────
    var title         by remember { mutableStateOf("") }
    var body          by remember { mutableStateOf("") }
    var category      by remember { mutableStateOf("Life") }
    var mood          by remember { mutableIntStateOf(3) }
    var imageUri      by remember { mutableStateOf<Uri?>(null) }
    var audioFilePath by remember { mutableStateOf<String?>(null) }
    var showShakeTip  by remember { mutableStateOf(false) }

    // ── Audio recording state ─────────────────────────────────────────────────
    var isRecording       by remember { mutableStateOf(false) }
    var recordingSeconds  by remember { mutableIntStateOf(0) }
    var recorder          by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile         by remember { mutableStateOf<File?>(null) }
    var audioPreviewPlaying by remember { mutableStateOf(false) }
    var previewPlayer     by remember { mutableStateOf<MediaPlayer?>(null) }

    // Recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) { delay(1000); recordingSeconds++ }
        }
    }

    // ── Camera temp file + URI ─────────────────────────────────────────────────
    var cameraImageFile by remember { mutableStateOf<File?>(null) }

    // ── Shake-to-Prompt ───────────────────────────────────────────────────────
    val prompts = listOf(
        "What was the best part of today?",
        "Describe how you're feeling right now in three words.",
        "What's something small you're grateful for?",
        "What challenged you today and how did you handle it?",
        "Write about a conversation that stuck with you.",
        "What do you wish you had done differently?",
        "What are you looking forward to tomorrow?"
    )
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val det = ShakeDetector {
            val p = prompts.random()
            body = if (body.isBlank()) p else "$body\n\n$p"
            showShakeTip = true
        }
        sm.registerListener(det, acc, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(det) }
    }

    // ── Voice-to-text   opens the Google speech to text dialog ─────────────────────────────────────────────────────────
    var isListening by remember { mutableStateOf(false) }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) body = if (body.isBlank()) spoken else "$body $spoken"
        isListening = false
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your memory…")
            }
            isListening = true; speechLauncher.launch(i)
        }
    }

    // ── Gallery picker  Anoroid file picker ────────────────────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
    }

    // ── Camera capture ────────────────────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) imageUri = Uri.fromFile(cameraImageFile)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            val f = createImageFile(context)
            cameraImageFile = f
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
            cameraLauncher.launch(uri)
        }
    }

    // ── Audio record permission ───────────────────────────────────────────────
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRecording(context) { rec, file -> recorder = rec; audioFile = file; isRecording = true }
    }

    // ── Pulse animation  to show rec is active ───────────────────────────────────────────────────────
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOut), RepeatMode.Reverse),
        label = "scale"
    )

    // Cleanup on leave
    DisposableEffect(Unit) {
        onDispose {
            recorder?.apply { try { stop() } catch (_: Exception) {}; release() }
            previewPlayer?.release()
        }
    }

    MaterialTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("New Memory", fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Text(
                                SimpleDateFormat("EEEE, MMM dd", Locale.getDefault()).format(Date()),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // ── Shake tip banner ──────────────────────────────────────────
                AnimatedVisibility(showShakeTip, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Surface(color = MemoraViolet.copy(0.1f), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📳", fontSize = 18.sp)
                            Text("Prompt inserted! Keep writing ✍️", color = MemoraViolet, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showShakeTip = false }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, null, tint = MemoraViolet, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // ── Title ─────────────────────────────────────────────────────
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("Give this memory a name…") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.EditNote, null, tint = MemoraViolet) }
                )

                // ── Body ──────────────────────────────────────────────────────
                Column {
                    OutlinedTextField(
                        value = body, onValueChange = { body = it },
                        label = { Text("Your Memory") },
                        placeholder = { Text("Write freely… shake the phone for a prompt 📳") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                        shape = RoundedCornerShape(16.dp),
                        maxLines = 14
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Voice dictation
                        FilledTonalButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your memory…")
                                    }
                                    isListening = true; speechLauncher.launch(i)
                                } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (isListening) MemoraRed.copy(0.15f) else MemoraViolet.copy(0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Mic, null, tint = if (isListening) MemoraRed else MemoraViolet, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isListening) "Listening…" else "Dictate", color = if (isListening) MemoraRed else MemoraViolet, fontSize = 13.sp)
                        }
                        // Shake hint badge
                        Surface(color = MemoraViolet.copy(0.07f), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📳", fontSize = 13.sp)
                                Spacer(Modifier.width(4.dp))
                                Text("Shake", color = MemoraViolet.copy(0.8f), fontSize = 12.sp)
                            }
                        }
                    }
                }

                // ── Category ──────────────────────────────────────────────────
                SectionLabel("Category")
                val cats = listOf("Life" to "🌿", "Study" to "📚", "Gym" to "💪", "Work" to "💼", "Food" to "🍜", "Social" to "🎉")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    cats.forEach { (cat, emoji) ->
                        val sel = category == cat
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (sel) MemoraViolet else MemoraViolet.copy(0.08f),
                            modifier = Modifier.clickable { category = cat }
                        ) {
                            Text("$emoji $cat", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                color = if (sel) Color.White else MemoraViolet,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                        }
                    }
                }

                // ── Mood ──────────────────────────────────────────────────────
                SectionLabel("How are you feeling?")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(1 to "😔", 2 to "😐", 3 to "🙂", 4 to "😊", 5 to "🤩").forEach { (v, emoji) ->
                        val sel = mood == v
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(if (sel) 58.dp else 50.dp)
                                .clip(CircleShape)
                                .background(if (sel) MemoraViolet.copy(0.18f) else Color.Transparent)
                                .border(if (sel) 2.dp else 0.dp, if (sel) MemoraViolet else Color.Transparent, CircleShape)
                                .clickable { mood = v }
                        ) { Text(emoji, fontSize = if (sel) 28.sp else 24.sp) }
                    }
                }

                // ── IMAGE ATTACHMENT ─────────────────────────────────────────
                SectionLabel("📸 Photo")
                if (imageUri != null) {
                    Box(Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Attached photo",
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop
                        )
                        // Remove button
                        IconButton(
                            onClick = { imageUri = null },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                .size(32.dp)
                                .background(Color.Black.copy(0.5f), CircleShape)
                        ) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Gallery button
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MemoraViolet)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Gallery", fontSize = 13.sp)
                    }
                    // Camera button
                    OutlinedButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                val f = createImageFile(context); cameraImageFile = f
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
                                cameraLauncher.launch(uri)
                            } else cameraPermission.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MemoraTeal)
                    ) {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Camera", fontSize = 13.sp)
                    }
                }

                // ── AUDIO NOTE ────────────────────────────────────────────────
                SectionLabel("🎙️ Voice Note")
                AudioRecorderCard(
                    isRecording       = isRecording,
                    recordingSeconds  = recordingSeconds,
                    audioFilePath     = audioFilePath,
                    isPlaying         = audioPreviewPlaying,
                    pulseScale        = pulseScale,
                    onStartRecording  = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startRecording(context) { rec, file ->
                                recorder = rec; audioFile = file; isRecording = true
                            }
                        } else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStopRecording   = {
                        recorder?.apply { try { stop() } catch (_: Exception) {}; release() }
                        recorder = null; isRecording = false
                        audioFilePath = audioFile?.absolutePath
                        scope.launch { snackbarHost.showSnackbar("Voice note saved ✓") }
                    },
                    onDeleteAudio     = { audioFilePath = null; audioFile = null },
                    onTogglePlayback  = {
                        if (audioPreviewPlaying) {
                            previewPlayer?.pause(); audioPreviewPlaying = false
                        } else {
                            audioFilePath?.let { path ->
                                previewPlayer?.release()
                                previewPlayer = MediaPlayer().apply {
                                    setDataSource(path); prepare(); start()
                                    setOnCompletionListener { audioPreviewPlaying = false }
                                }
                                audioPreviewPlaying = true
                            }
                        }
                    }
                )

                // ── Save ──────────────────────────────────────────────────────
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (title.isBlank() || body.isBlank()) {
                            scope.launch { snackbarHost.showSnackbar("Please add a title and memory!") }
                            return@Button
                        }
                        viewModel.saveEntry(
                            title     = title.trim(),
                            body      = body.trim(),
                            category  = category,
                            mood      = mood,
                            imagePath = imageUri?.toString(),
                            audioPath = audioFilePath
                        )
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape    = RoundedCornerShape(18.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MemoraViolet
                    )
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Memory", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ── Audio Recorder Card ───────────────────────────────────────────────────────
@Composable
fun AudioRecorderCard(
    isRecording: Boolean,
    recordingSeconds: Int,
    audioFilePath: String?,
    isPlaying: Boolean,
    pulseScale: Float,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDeleteAudio: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (audioFilePath != null && !isRecording) {
                // ── Playback UI ───────────────────────────────────────────────
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(52.dp).background(
                            Brush.radialGradient(listOf(MemoraTeal, MemoraViolet)),
                            CircleShape
                        ).clickable { onTogglePlayback() }
                    ) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Voice Note", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(if (isPlaying) "Playing…" else "Tap to play", color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontSize = 12.sp)
                    }
                    // Waveform decoration
                    AudioWaveformDecoration(isPlaying)
                    IconButton(onClick = onDeleteAudio, modifier = Modifier.size(36.dp).background(MemoraRed.copy(0.1f), CircleShape)) {
                        Icon(Icons.Default.DeleteOutline, null, tint = MemoraRed, modifier = Modifier.size(18.dp))
                    }
                }
            } else if (isRecording) {
                // ── Recording UI ──────────────────────────────────────────────
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.size((52 * pulseScale).dp).background(MemoraRed.copy(0.25f), CircleShape))
                        Box(Modifier.size(52.dp).background(MemoraRed, CircleShape).clickable { onStopRecording() }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Recording…", fontWeight = FontWeight.Bold, color = MemoraRed, fontSize = 15.sp)
                        Text(formatDuration(recordingSeconds), color = MemoraRed.copy(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    // Live mic indicator dots
                    LiveMicDots()
                }
            } else {
                // ── Idle UI ───────────────────────────────────────────────────
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(52.dp).background(MemoraViolet.copy(0.12f), CircleShape).clickable { onStartRecording() }
                    ) { Icon(Icons.Default.Mic, null, tint = MemoraViolet, modifier = Modifier.size(24.dp)) }
                    Column {
                        Text("Record a Voice Note", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Tap to start recording", color = MaterialTheme.colorScheme.onSurface.copy(0.4f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AudioWaveformDecoration(animated: Boolean) {
    val heights = listOf(8, 14, 20, 12, 18, 10, 16, 8, 14, 20)
    val anim by rememberInfiniteTransition(label = "wf").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "wfA"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        heights.forEachIndexed { i, h ->
            val finalH = if (animated) (h * (0.5f + 0.5f * if (i % 2 == 0) anim else (1f - anim))).toInt() else h / 2
            Box(Modifier.width(3.dp).height(finalH.dp).clip(RoundedCornerShape(2.dp)).background(MemoraTeal.copy(0.7f)))
        }
    }
}

@Composable
fun LiveMicDots() {
    val anim by rememberInfiniteTransition(label = "dots").animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart), label = "dotsA"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        (0..2).forEach { i ->
            val alpha = if (anim.toInt() == i) 1f else 0.25f
            Box(Modifier.size(8.dp).background(MemoraRed.copy(alpha), CircleShape))
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.75f))
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60; val s = seconds % 60
    return "%d:%02d".format(m, s)
}

// ── MediaRecorder helper ──────────────────────────────────────────────────────
fun startRecording(context: Context, onStarted: (MediaRecorder, File) -> Unit) {
    val file = createAudioFile(context)
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION") MediaRecorder()
    }
    recorder.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setOutputFile(file.absolutePath)
        prepare()
        start()
    }
    onStarted(recorder, file)
}