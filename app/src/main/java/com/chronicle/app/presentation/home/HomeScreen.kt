package com.chronicle.app.presentation.home

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronicle.app.data.local.LogEntry
import java.text.SimpleDateFormat
import java.util.*

// ── Shared brand colours ──────────────────────────────────────────────────────
val MemoraViolet = Color(0xFF6C63FF)
val MemoraRed    = Color(0xFFFF6B6B)
val MemoraGreen  = Color(0xFF43E97B)
val MemoraAmber  = Color(0xFFFF8C42)
val MemoraTeal   = Color(0xFF0ABDE3)

// ── Category config ───────────────────────────────────────────────────────────
data class CategoryConfig(val label: String, val emoji: String, val color: Color)

val categories = listOf(
    CategoryConfig("All",    "🌟", MemoraViolet),
    CategoryConfig("Life",   "🌿", MemoraGreen),
    CategoryConfig("Study",  "📚", Color(0xFF3A86FF)),
    CategoryConfig("Gym",    "💪", MemoraRed),
    CategoryConfig("Work",   "💼", Color(0xFFFFBE0B)),
    CategoryConfig("Food",   "🍜", Color(0xFFFF6B9D)),
    CategoryConfig("Social", "🎉", MemoraAmber)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddEntry:   () -> Unit,
    onLogout:     () -> Unit,
    onEntryClick: (LogEntry) -> Unit,
    viewModel:    HomeViewModel = hiltViewModel()
) {
    val context         = LocalContext.current
    val filteredEntries by viewModel.filteredEntries.collectAsState()
    val streak          by viewModel.currentStreak.collectAsState()
    val onThisDay       by viewModel.onThisDayEntries.collectAsState()
    val selectedCat     by viewModel.selectedCategory.collectAsState()
    val searchQuery     by viewModel.searchQuery.collectAsState()
    val isDarkMode      by viewModel.isDarkMode.collectAsState()
    val weeklyMood      by viewModel.weeklyMoodData.collectAsState()
    val totalWords      by viewModel.totalWordsLogged.collectAsState()
    val moodByCat       by viewModel.moodByCategory.collectAsState()
    val syncStatus      by viewModel.syncStatus.collectAsState()

    var showMenu      by remember { mutableStateOf(false) }
    var showAnalytics by remember { mutableStateOf(false) }
    val listState     = rememberLazyListState()

    // ── Text-to-Speech ────────────────────────────────────────────────────────
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts.value?.language = Locale.US
        }
        tts.value = engine
        onDispose { engine.stop(); engine.shutdown() }
    }

    val colorScheme = if (isDarkMode) darkColorScheme(
        primary      = MemoraViolet,
        secondary    = Color(0xFF9D84FF),
        surface      = Color(0xFF1A1A2E),
        background   = Color(0xFF16213E),
        onBackground = Color(0xFFE8E8F0),
        onSurface    = Color(0xFFE8E8F0)
    ) else lightColorScheme(
        primary    = MemoraViolet,
        secondary  = Color(0xFF9D84FF),
        surface    = Color(0xFFF8F7FF),
        background = Color(0xFFF3F2FF)
    )

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            containerColor = colorScheme.background,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick        = onAddEntry,
                    expanded       = listState.firstVisibleItemIndex == 0,
                    containerColor = MemoraViolet,
                    contentColor   = Color.White,
                    icon           = { Icon(Icons.Default.Add, null) },
                    text           = { Text("New Memory", fontWeight = FontWeight.Bold) }
                )
            }
        ) { padding ->
            LazyColumn(
                state    = listState,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {

                // ── Top bar (UPDATED WITH BOTH ICONS) ─────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Memora ✨", fontSize = 32.sp, fontWeight = FontWeight.Black, color = MemoraViolet)
                            Text("Your story, one moment at a time.", color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontSize = 13.sp)
                        }

                        // Icons Container
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp) // Gap between the two icons
                        ) {
                            // 1. ANALYTICS (GRAPH) ICON
                            IconButton(
                                onClick = { showAnalytics = !showAnalytics },
                                modifier = Modifier
                                    .background(MemoraViolet.copy(alpha = 0.1f), CircleShape)
                                    .size(42.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "Analytics",
                                    tint = MemoraViolet
                                )
                            }

                            // 2. SETTINGS (DROP DOWN) ICON
                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier
                                        .background(MemoraViolet.copy(alpha = 0.1f), CircleShape)
                                        .size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = MemoraViolet
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (isDarkMode) "☀️ Light Mode" else "🌙 Dark Mode") },
                                        leadingIcon = { Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null) },
                                        onClick = { viewModel.toggleDarkMode(); showMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("⚡ Generate Demo Data") },
                                        leadingIcon = { Icon(Icons.Default.Bolt, null) },
                                        onClick = { viewModel.seedDemoData(); showMenu = false }
                                    )
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    DropdownMenuItem(
                                        text = { Text("Sort: Newest First") },
                                        leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                                        onClick = { viewModel.setSortOrder(SortOrder.NEWEST); showMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sort: Oldest First") },
                                        leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                                        onClick = { viewModel.setSortOrder(SortOrder.OLDEST); showMenu = false }
                                    )
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    DropdownMenuItem(
                                        text = { Text("Log Out", color = MemoraRed) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MemoraRed) },
                                        onClick = { onLogout() }

                                    )
                                }
                            }
                        }
                    }
                }


                // ── Sync status banner (shows when unsynced entries exist) ────
                item {
                    AnimatedVisibility(
                        visible = syncStatus != SyncStatus.ALL_SYNCED,
                        enter   = expandVertically() + fadeIn(),
                        exit    = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            color    = when (syncStatus) {
                                SyncStatus.SYNCING     -> MemoraTeal.copy(0.12f)
                                SyncStatus.PENDING     -> MemoraAmber.copy(0.12f)
                                SyncStatus.ERROR       -> MemoraRed.copy(0.12f)
                                SyncStatus.ALL_SYNCED  -> Color.Transparent
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment      = Alignment.CenterVertically,
                                horizontalArrangement  = Arrangement.spacedBy(10.dp)
                            ) {
                                when (syncStatus) {
                                    SyncStatus.SYNCING    -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MemoraTeal)
                                    SyncStatus.PENDING    -> Icon(Icons.Default.CloudOff, null, tint = MemoraAmber, modifier = Modifier.size(16.dp))
                                    SyncStatus.ERROR      -> Icon(Icons.Default.ErrorOutline, null, tint = MemoraRed, modifier = Modifier.size(16.dp))
                                    SyncStatus.ALL_SYNCED -> Icon(Icons.Default.Cloud, null, tint = MemoraGreen, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    when (syncStatus) {
                                        SyncStatus.SYNCING    -> "Syncing to cloud…"
                                        SyncStatus.PENDING    -> "Saved offline — will sync when connected"
                                        SyncStatus.ERROR      -> "Sync failed — will retry automatically"
                                        SyncStatus.ALL_SYNCED -> "All synced"
                                    },
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color      = when (syncStatus) {
                                        SyncStatus.SYNCING    -> MemoraTeal
                                        SyncStatus.PENDING    -> MemoraAmber
                                        SyncStatus.ERROR      -> MemoraRed
                                        SyncStatus.ALL_SYNCED -> MemoraGreen
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Stats row ─────────────────────────────────────────────────
                item {
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatChip(Modifier.weight(1f), "Entries", "${filteredEntries.size}", MemoraViolet)
                        StatChip(Modifier.weight(1f), "Words",   if (totalWords > 999) "${totalWords/1000}k+" else "$totalWords", MemoraRed)
                        StatChip(Modifier.weight(1f), "Streak",  "🔥 $streak", MemoraAmber)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Analytics panel ───────────────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = showAnalytics,
                        enter   = expandVertically() + fadeIn(),
                        exit    = shrinkVertically() + fadeOut()
                    ) {
                        Column(Modifier.padding(horizontal = 24.dp)) {
                            MoodChartCard(weeklyMood)
                            Spacer(Modifier.height(12.dp))
                            MoodByCategoryCard(moodByCat)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                // ── Search ────────────────────────────────────────────────────
                item {
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier      = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        placeholder   = { Text("Search your thoughts…") },
                        leadingIcon   = { Icon(Icons.Default.Search, null) },
                        trailingIcon  = {
                            if (searchQuery.isNotEmpty())
                                IconButton(onClick = { viewModel.setSearchQuery("") }) { Icon(Icons.Default.Close, null) }
                        },
                        shape      = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // ── Category chips ────────────────────────────────────────────
                item {
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cfg ->
                            val sel = selectedCat == cfg.label
                            Surface(
                                shape    = RoundedCornerShape(50),
                                color    = if (sel) cfg.color else colorScheme.surface,
                                shadowElevation = if (sel) 4.dp else 0.dp,
                                modifier = Modifier.clickable { viewModel.setCategory(cfg.label) }
                            ) {
                                Text(
                                    "${cfg.emoji} ${cfg.label}",
                                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color      = if (sel) Color.White else colorScheme.onSurface.copy(0.7f),
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize   = 13.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // ── On This Day ───────────────────────────────────────────────
                if (onThisDay.isNotEmpty() && searchQuery.isEmpty()) {
                    item { SectionHeader("🕰️ On This Day") }
                    items(onThisDay, key = { "memory_${it.id}" }) { entry ->
                        MemoraEntryCard(
                            entry        = entry,
                            onDelete     = { viewModel.deleteEntry(entry) },
                            onReadAloud  = { tts.value?.speak(it, TextToSpeech.QUEUE_FLUSH, null, null) },
                            onCardClick  = { onEntryClick(entry) }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // ── Memories list ─────────────────────────────────────────────
                item {
                    SectionHeader(
                        if (searchQuery.isNotEmpty()) "🔍 Results for \"$searchQuery\""
                        else "Your Memories"
                    )
                }

                if (filteredEntries.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("📭", fontSize = 52.sp)
                                Text(
                                    if (searchQuery.isNotEmpty()) "No entries match your search"
                                    else "No memories yet — add your first!",
                                    color = colorScheme.onSurface.copy(0.4f), fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                items(filteredEntries, key = { it.id }) { entry ->
                    MemoraEntryCard(
                        entry       = entry,
                        onDelete    = { viewModel.deleteEntry(entry) },
                        onReadAloud = { tts.value?.speak(it, TextToSpeech.QUEUE_FLUSH, null, null) },
                        onCardClick = { onEntryClick(entry) }
                    )
                }

                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MemoraEntryCard(
    entry:      LogEntry,
    onDelete:   () -> Unit,
    onReadAloud:(String) -> Unit,
    onCardClick:() -> Unit
) {
    val cfg = categories.firstOrNull { it.label == entry.category }
        ?: CategoryConfig(entry.category, "📝", MemoraViolet)

    ElevatedCard(
        onClick   = onCardClick,
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Colour strip
            Box(Modifier.width(4.dp).height(60.dp).background(cfg.color, RoundedCornerShape(4.dp)))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // Badges row
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Category badge
                    Surface(color = cfg.color.copy(0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text("${cfg.emoji} ${cfg.label}",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            color = cfg.color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    // Photo badge
                    if (entry.imagePath != null) {
                        Surface(color = MemoraTeal.copy(0.12f), shape = RoundedCornerShape(6.dp)) {
                            Row(
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.Image, null, tint = MemoraTeal, modifier = Modifier.size(10.dp))
                                Text("Photo", color = MemoraTeal, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    // Audio badge
                    if (entry.audioPath != null) {
                        Surface(color = MemoraAmber.copy(0.12f), shape = RoundedCornerShape(6.dp)) {
                            Row(
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.Mic, null, tint = MemoraAmber, modifier = Modifier.size(10.dp))
                                Text("Audio", color = MemoraAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    // Sync status dot
                    Box(
                        Modifier.size(7.dp).background(
                            if (entry.isSynced) MemoraGreen else MemoraAmber, CircleShape
                        )
                    )
                }

                Text(entry.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.body,  color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    SimpleDateFormat("MMM dd · h:mm a", Locale.getDefault()).format(Date(entry.timestamp)),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.35f), fontSize = 11.sp
                )
            }

            // Right: mood + chevron
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(when(entry.mood) { 5->"🤩"; 4->"😊"; 3->"🙂"; 2->"😐"; else->"😔" }, fontSize = 22.sp)
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.25f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Supporting Composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StreakBadge(streak: Int) {
    Surface(color = MemoraAmber.copy(0.15f), shape = RoundedCornerShape(10.dp)) {
        Text("🔥 $streak days", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = MemoraAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun StatChip(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = color.copy(0.12f)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = color)
            Text(label, fontSize    = 11.sp, color = color.copy(0.7f))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
}

@Composable
fun MoodChartCard(weeklyMoodData: List<Float>) {
    val days = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
    Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Box(Modifier.background(Brush.linearGradient(listOf(MemoraViolet, Color(0xFF9D84FF))))) {
            Column(Modifier.padding(20.dp)) {
                Text("📈 7-Day Mood Trend", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                    val barW = size.width / (weeklyMoodData.size * 2.2f)
                    val gap  = size.width / weeklyMoodData.size
                    weeklyMoodData.forEachIndexed { i, mood ->
                        val barH = (mood / 5f) * size.height
                        drawRoundRect(
                            color        = Color.White.copy(if (mood == weeklyMoodData.maxOrNull()) 1f else 0.5f),
                            topLeft      = Offset(i * gap + gap/2f - barW/2f, size.height - barH),
                            size         = Size(barW, barH),
                            cornerRadius = CornerRadius(10f)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    days.forEach { Text(it, color = Color.White.copy(0.65f), fontSize = 10.sp) }
                }
                Spacer(Modifier.height(6.dp))
                val avg = weeklyMoodData.filter { it > 0 }.average()
                Text(
                    when { avg >= 4.5 -> "Amazing week! 🤩"; avg >= 3.5 -> "Pretty good overall 😊"; avg >= 2.5 -> "Mixed feelings 😐"; else -> "Tough week — keep going 💪" },
                    color = Color.White.copy(0.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun MoodByCategoryCard(moodByCat: Map<String, Float>) {
    Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("📊 Mood by Category", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            moodByCat.entries.sortedByDescending { it.value }.forEach { (cat, avg) ->
                val cfg = categories.firstOrNull { it.label == cat } ?: return@forEach
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("${cfg.emoji} $cat", modifier = Modifier.width(72.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(50)).background(cfg.color.copy(0.2f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(avg / 5f).clip(RoundedCornerShape(50)).background(cfg.color))
                    }
                    Text("%.1f".format(avg), color = cfg.color, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(28.dp))
                }
            }
        }
    }
}