package com.chronicle.app.presentation.detail

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
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
import coil.compose.AsyncImage
import com.chronicle.app.data.local.LogEntry
import com.chronicle.app.presentation.home.MemoraViolet
import com.chronicle.app.presentation.home.MemoraRed
import com.chronicle.app.presentation.home.MemoraGreen
import com.chronicle.app.presentation.home.MemoraAmber
import com.chronicle.app.presentation.home.MemoraTeal
import com.chronicle.app.presentation.home.categories
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entry    : LogEntry,
    onBack   : () -> Unit,
    onDelete : () -> Unit
) {
    val context = LocalContext.current

    var player           by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying        by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var playbackSeconds  by remember { mutableIntStateOf(0) }
    var totalSeconds     by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFullImage    by remember { mutableStateOf(false) }

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts.value?.language = Locale.US
        }
        tts.value = engine
        onDispose { engine.stop(); engine.shutdown() }
    }

    LaunchedEffect(entry.audioPath) {
        entry.audioPath?.let { path ->
            if (File(path).exists()) {
                try {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(path)
                    totalSeconds = ((r.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 0L) / 1000).toInt()
                    r.release()
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val p = player
            if (p != null && p.isPlaying) {
                playbackSeconds  = p.currentPosition / 1000
                playbackProgress = if (totalSeconds > 0)
                    p.currentPosition / (totalSeconds * 1000f) else 0f
            }
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }

    fun togglePlayback() {
        if (isPlaying) {
            player?.pause()
            isPlaying = false
        } else {
            entry.audioPath?.let { path ->
                if (player == null) {
                    player = MediaPlayer().apply {
                        setDataSource(path)
                        prepare()
                        setOnCompletionListener {
                            isPlaying        = false
                            playbackProgress = 0f
                            playbackSeconds  = 0
                            seekTo(0)
                        }
                    }
                }
                player?.start()
                isPlaying = true
            }
        }
    }

    val cfg      = categories.firstOrNull { it.label == entry.category }
    val catColor = cfg?.color ?: MemoraViolet
    val catEmoji = cfg?.emoji ?: "📝"

    // Subtle waveform pulse when playing
    val waveAnim by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue   = 0.6f,
        targetValue    = 1f,
        animationSpec  = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label          = "wA"
    )
    val bars = listOf(0.4f, 0.7f, 1f, 0.6f, 0.85f, 0.5f, 0.9f, 0.4f, 0.75f, 0.6f, 0.95f, 0.5f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick  = onBack,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(0.9f),
                                CircleShape
                            )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(
                        onClick  = {
                            tts.value?.speak(
                                "${entry.title}. ${entry.body}",
                                TextToSpeech.QUEUE_FLUSH, null, null
                            )
                        },
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.surface.copy(0.9f), CircleShape
                        )
                    ) {
                        Icon(Icons.Default.VolumeUp, null, tint = MemoraViolet)
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick  = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, entry.title)
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "📖 ${entry.title}\n\n${entry.body}\n\n— Logged with Memora"
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, "Share via"))
                        },
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.surface.copy(0.9f), CircleShape
                        )
                    ) {
                        Icon(Icons.Default.Share, null, tint = Color(0xFF3A86FF))
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick  = { showDeleteDialog = true },
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.surface.copy(0.9f), CircleShape
                        )
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, tint = MemoraRed)
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp)
        ) {

            // ── Hero ──────────────────────────────────────────────────────────
            if (entry.imagePath != null && Uri.parse(entry.imagePath).let {
                    (it.scheme == "file" && File(it.path ?: "").exists()) ||
                            it.scheme == "content"
                }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clickable { showFullImage = true }
                ) {
                    AsyncImage(
                        model              = Uri.parse(entry.imagePath),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(0.6f)),
                                startY = 150f
                            )
                        )
                    )
                    Surface(
                        color    = Color.Black.copy(0.4f),
                        shape    = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.ZoomIn, null,
                                tint     = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Text("Expand", color = Color.White, fontSize = 10.sp)
                        }
                    }
                    Surface(
                        color    = catColor.copy(0.88f),
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)
                    ) {
                        Text(
                            "$catEmoji ${entry.category}",
                            modifier   = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 12.sp
                        )
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().height(160.dp).background(
                        Brush.linearGradient(
                            listOf(catColor.copy(0.7f), MemoraViolet)
                        )
                    )
                ) {
                    Text(
                        catEmoji,
                        fontSize = 64.sp,
                        modifier = Modifier.padding(24.dp).align(Alignment.BottomStart)
                    )
                    Surface(
                        color    = Color.White.copy(0.2f),
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
                    ) {
                        Text(
                            entry.category,
                            modifier   = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 12.sp
                        )
                    }
                }
            }

            // ── Body ──────────────────────────────────────────────────────────
            Column(
                modifier            = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {

                // Title row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.title,
                            fontWeight = FontWeight.Black,
                            fontSize   = 24.sp,
                            lineHeight = 30.sp
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.CalendarToday, null,
                                tint     = MaterialTheme.colorScheme.onSurface.copy(0.35f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                SimpleDateFormat(
                                    "EEE, MMM d yyyy · h:mm a",
                                    Locale.getDefault()
                                ).format(Date(entry.timestamp)),
                                color    = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    // Mood bubble
                    val (moodEmoji, moodBg) = when (entry.mood) {
                        5    -> "🤩" to Color(0xFFFFF3E0)
                        4    -> "😊" to Color(0xFFE8F5E9)
                        3    -> "🙂" to Color(0xFFE3F2FD)
                        2    -> "😐" to Color(0xFFFCE4EC)
                        else -> "😔" to Color(0xFFEDE7F6)
                    }
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(moodBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(moodEmoji, fontSize = 24.sp)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.07f))

                // Body text
                Text(
                    entry.body,
                    fontSize   = 15.sp,
                    lineHeight = 26.sp,
                    color      = MaterialTheme.colorScheme.onSurface.copy(0.82f)
                )

                // ── Compact Audio Player ───────────────────────────────────────
                if (entry.audioPath != null && File(entry.audioPath).exists()) {
                    Surface(
                        shape  = RoundedCornerShape(16.dp),
                        color  = MemoraTeal.copy(alpha = 0.07f),
                        border = BorderStroke(1.dp, MemoraTeal.copy(alpha = 0.18f))
                    ) {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Play / Pause button — compact circle
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier         = Modifier
                                    .size(40.dp)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(MemoraTeal, MemoraViolet)
                                        ),
                                        CircleShape
                                    )
                                    .clickable { togglePlayback() }
                            ) {
                                Icon(
                                    imageVector        = if (isPlaying)
                                        Icons.Default.Pause
                                    else
                                        Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint               = Color.White,
                                    modifier           = Modifier.size(22.dp)
                                )
                            }

                            // Middle: mini waveform + seek bar stacked
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Mini waveform — only 12 bars, max 20dp tall
                                Row(
                                    modifier              = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    bars.forEachIndexed { i, h ->
                                        val animatedH = if (isPlaying)
                                            h * (if (i % 2 == 0) waveAnim else 1f - waveAnim + 0.4f)
                                        else h * 0.4f
                                        Box(
                                            Modifier
                                                .width(3.dp)
                                                .height((animatedH * 20).dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(
                                                    if (playbackProgress > i.toFloat() / bars.size)
                                                        MemoraTeal
                                                    else
                                                        MemoraTeal.copy(alpha = 0.22f)
                                                )
                                        )
                                    }
                                }

                                // Thin seek bar
                                Slider(
                                    value         = playbackProgress,
                                    onValueChange = { frac ->
                                        playbackProgress = frac
                                        player?.seekTo((frac * totalSeconds * 1000).toInt())
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(18.dp),
                                    colors   = SliderDefaults.colors(
                                        thumbColor         = MemoraTeal,
                                        activeTrackColor   = MemoraTeal,
                                        inactiveTrackColor = MemoraTeal.copy(0.15f)
                                    )
                                )
                            }

                            // Right: time display
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    formatDuration(playbackSeconds),
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = MemoraTeal
                                )
                                Text(
                                    formatDuration(totalSeconds),
                                    fontSize = 10.sp,
                                    color    = MaterialTheme.colorScheme.onSurface.copy(0.35f)
                                )
                            }
                        }
                    }
                }

                // ── Sync status ────────────────────────────────────────────────
                Surface(
                    color = if (entry.isSynced)
                        MemoraGreen.copy(0.08f)
                    else
                        MemoraAmber.copy(0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier          = Modifier.padding(
                            horizontal = 10.dp, vertical = 6.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector        = if (entry.isSynced)
                                Icons.Default.Cloud
                            else
                                Icons.Default.CloudOff,
                            contentDescription = null,
                            tint               = if (entry.isSynced) MemoraGreen else MemoraAmber,
                            modifier           = Modifier.size(13.dp)
                        )
                        Text(
                            text       = if (entry.isSynced)
                                "Synced to cloud"
                            else
                                "Saved locally · not yet synced",
                            color      = if (entry.isSynced) MemoraGreen else MemoraAmber,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // ── Full screen image viewer ───────────────────────────────────────────
        if (showFullImage && entry.imagePath != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showFullImage = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model              = Uri.parse(entry.imagePath),
                    contentDescription = null,
                    modifier           = Modifier.fillMaxWidth(),
                    contentScale       = ContentScale.Fit
                )
                IconButton(
                    onClick  = { showFullImage = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.White.copy(0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
        }

        // ── Delete dialog ──────────────────────────────────────────────────────
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon             = {
                    Icon(
                        Icons.Default.DeleteForever, null,
                        tint     = MemoraRed,
                        modifier = Modifier.size(30.dp)
                    )
                },
                title            = {
                    Text("Delete Memory?", fontWeight = FontWeight.Bold)
                },
                text             = {
                    Text("This will be permanently deleted and cannot be recovered.")
                },
                confirmButton    = {
                    Button(
                        onClick = { showDeleteDialog = false; onDelete() },
                        colors  = ButtonDefaults.buttonColors(containerColor = MemoraRed)
                    ) { Text("Delete") }
                },
                dismissButton    = {
                    OutlinedButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}