package com.premiumglass.launcher

import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.provider.Settings
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val accents = listOf(Color(0xFFA18AFF), Color(0xFF65BFFF), Color(0xFF52DFC3), Color(0xFFFF86B6))
private val glassShape = RoundedCornerShape(24.dp)

class MainActivity : ComponentActivity() {
    private lateinit var host: AppWidgetHost
    private lateinit var store: LauncherStore
    private var widgetId by mutableIntStateOf(-1)
    private var pendingWidgetId = -1
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var revision by mutableIntStateOf(0)
    private var message by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LauncherStore(this)
        host = AppWidgetHost(this, 4482)
        widgetId = getSharedPreferences("widgets", MODE_PRIVATE).getInt("widget_id", -1)
        refreshApps()
        setContent { LauncherScreen() }
    }
    override fun onStart() { super.onStart(); try { host.startListening() } catch (_: Exception) {} }
    override fun onStop() { try { host.stopListening() } catch (_: Exception) {}; super.onStop() }
    override fun onResume() { super.onResume(); refreshApps() }
    private fun refreshApps() { apps = installedLauncherApps(this) }
    private fun notifyChanged() { revision++ }

    private fun openApp(app: AppEntry) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(intent)
            store.recordLaunch(app.id)
            notifyChanged()
        } catch (_: Exception) { message = "Unable to launch ${app.label}" }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LauncherScreen() {
        val currentRevision = revision
        var page by remember { mutableStateOf("home") }
        var query by remember { mutableStateOf("") }
        var editApp by remember { mutableStateOf<AppEntry?>(null) }
        var showPinDialog by remember { mutableStateOf(false) }
        var unlocked by remember { mutableStateOf(false) }
        var showSetPin by remember { mutableStateOf(false) }
        val currentAccent = accents[store.accent.coerceIn(0, 3)]
        val glass = Color.White.copy(alpha = store.glassOpacity)
        val foreground = Color.White
        val visibleApps = remember(apps, currentRevision) { apps.filterNot { it.id in store.hidden } }
        val favorites = visibleApps.filter { it.id in store.favorites }
        val recents = store.recentApps().mapNotNull { id -> visibleApps.find { it.id == id } }.take(6)
        val chooseWidget = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (result.resultCode == RESULT_OK && id != -1) {
                if (widgetId != -1 && widgetId != id) host.deleteAppWidgetId(widgetId)
                widgetId = id
                getSharedPreferences("widgets", MODE_PRIVATE).edit().putInt("widget_id", id).apply()
                notifyChanged()
            } else {
                val canceledId = if (id != -1) id else pendingWidgetId
                if (canceledId != -1) try { host.deleteAppWidgetId(canceledId) } catch (_: Exception) {}
            }
            pendingWidgetId = -1
        }
        val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) try {
                contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray(Charsets.UTF_8)) }
                message = "Backup saved"
            } catch (_: Exception) { message = "Could not save backup" }
        }
        val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) try {
                val bytes = contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() } ?: error("Empty file")
                require(bytes.size < 2_000_000) { "File too large" }
                store.importJson(bytes.toString(Charsets.UTF_8))
                notifyChanged()
                message = "Settings restored (PIN and widgets excluded)"
            } catch (e: Exception) { message = "Restore failed: ${e.message ?: "invalid file"}" }
        }
        fun pickWidget() {
            try {
                val id = host.allocateAppWidgetId()
                pendingWidgetId = id
                chooseWidget.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                })
            } catch (_: Exception) { message = "Widget picker not supported on this device" }
        }
        BackHandler(page != "home") { page = "home"; query = "" }
        MaterialTheme(colorScheme = darkColorScheme(primary = currentAccent, secondary = currentAccent)) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF101325), Color(0xFF272047), Color(0xFF0C2735))))) {
                // Pure gradients avoid costly offscreen blur passes; alpha-glass remains responsive.
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("◈  PREMIUM GLASS", color = foreground, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { refreshApps(); notifyChanged() }) { Icon(Icons.Default.Refresh, "Refresh apps", tint = foreground) }
                        IconButton(onClick = { page = "settings" }) { Icon(Icons.Default.Settings, "Settings", tint = foreground) }
                    }
                    when (page) {
                        "home" -> {
                            var swipe by remember { mutableFloatStateOf(0f) }
                            Column(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                                detectVerticalDragGestures(onVerticalDrag = { _, amount -> swipe += amount }, onDragEnd = {
                                    if (swipe < -65) page = "drawer"
                                    if (swipe > 95) page = "search"
                                    swipe = 0f
                                })
                            }.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                                Spacer(Modifier.height(12.dp))
                                GlassClock(glass, currentAccent)
                                if (widgetId != -1) {
                                    val id = widgetId
                                    key(id, currentRevision) {
                                        AndroidView(factory = { ctx ->
                                            val manager = AppWidgetManager.getInstance(ctx)
                                            val info = manager.getAppWidgetInfo(id)
                                            if (info == null) android.widget.TextView(ctx).apply { text = "Widget unavailable — remove it in Settings" }
                                            else host.createView(ctx, id, info)
                                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("MY SPACE", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { page = "drawer" }) { Text("All apps  →") }
                                }
                                if (favorites.isEmpty()) {
                                    GlassPanel(glass) { Text("Long-press an app in the drawer to add it here. Swipe up for all apps.", color = Color.White.copy(alpha = .82f)) }
                                } else {
                                    AppGrid(favorites, store.columns, store.showLabels, { openApp(it) }, { editApp = it })
                                }
                                if (store.showRecent && recents.isNotEmpty()) {
                                    Text("RECENTLY OPENED", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelLarge)
                                    AppGrid(recents, store.columns, store.showLabels, { openApp(it) }, { editApp = it })
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(Modifier.fillMaxWidth().padding(16.dp).clip(glassShape).background(glass).padding(10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                DockItem(Icons.Default.Search, "Search") { query = ""; page = "search" }
                                DockItem(Icons.Default.Apps, "Apps") { page = "drawer" }
                                DockItem(Icons.Default.AddCircleOutline, "Widget") { pickWidget() }
                                DockItem(Icons.Default.Wallpaper, "Wallpaper") {
                                    try { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
                                    catch (_: Exception) { try { startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) } catch (_: Exception) { message = "Wallpaper picker unavailable" } }
                                }
                            }
                        }
                        "drawer", "search" -> {
                            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search apps") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = {
                                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                                }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${visibleApps.count { it.label.contains(query, true) }} apps", color = Color.White.copy(alpha = .7f), modifier = Modifier.weight(1f))
                                    if (page == "search" && query.isNotBlank()) TextButton(onClick = {
                                        startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query))
                                    }) { Text("Search web") }
                                }
                                val filtered = visibleApps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
                                LazyVerticalGrid(columns = GridCells.Fixed(store.columns), contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                    items(filtered, key = { it.id }) { app -> AppTile(app, store.showLabels, { openApp(app) }, { editApp = app }) }
                                }
                            }
                        }
                        else -> {
                            SettingsScreen(store, glass, currentAccent, onChange = { notifyChanged() }, onBack = { page = "home" },
                                onBackup = { backup.launch("premium-glass-backup.json") },
                                onRestore = { restore.launch(arrayOf("application/json", "text/plain")) },
                                onWidget = { pickWidget() }, onRemoveWidget = {
                                    if (widgetId != -1) try { host.deleteAppWidgetId(widgetId) } catch (_: Exception) {}
                                    widgetId = -1; getSharedPreferences("widgets", MODE_PRIVATE).edit().remove("widget_id").apply(); notifyChanged()
                                }, hasWidget = widgetId != -1,
                                onPin = { showSetPin = true },
                                onManageHidden = { if (!store.hasPin() || unlocked) page = "hidden" else showPinDialog = true },
                                apps = apps, page = page, onToggleHidden = { store.toggleHidden(it.id); notifyChanged() })
                        }
                    }
                }
                if (message.isNotBlank()) Snackbar(Modifier.align(Alignment.BottomCenter).padding(12.dp), action = { TextButton(onClick = { message = "" }) { Text("Dismiss") } }) { Text(message) }
            }
            editApp?.let { app ->
                AlertDialog(onDismissRequest = { editApp = null }, title = { Text(app.label) }, text = { Text("Choose how this app appears in your launcher.") },
                    confirmButton = { TextButton(onClick = { store.toggleFavorite(app.id); notifyChanged(); editApp = null }) { Text(if (app.id in store.favorites) "Remove favorite" else "Add to home") } },
                    dismissButton = { TextButton(onClick = { editApp = null; if (store.hasPin() && !unlocked) showPinDialog = true else { store.toggleHidden(app.id); notifyChanged() } }) { Text(if (app.id in store.hidden) "Unhide" else "Hide app") } })
            }
            if (showPinDialog) PinDialog("Unlock hidden apps", onDismiss = { showPinDialog = false }, onConfirm = { pin ->
                if (store.verifyPin(pin)) { unlocked = true; page = "hidden"; showPinDialog = false } else message = "Incorrect PIN"
            })
            if (showSetPin) PinDialog(if (store.hasPin()) "Change / remove PIN" else "Create privacy PIN", onDismiss = { showSetPin = false }, onConfirm = { pin ->
                if (!store.hasPin()) { store.setPin(pin); message = "PIN enabled"; showSetPin = false }
                else if (store.verifyPin(pin)) { store.clearPin(); unlocked = true; message = "PIN removed; create a new PIN if needed"; showSetPin = false }
                else message = "Enter current PIN to remove it"
            })
        }
    }
}

@Composable
private fun GlassPanel(glass: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(glassShape).background(glass).border(1.dp, Color.White.copy(alpha = .13f), glassShape).padding(20.dp), content = content)
}

@Composable
private fun GlassClock(glass: Color, accent: Color) {
    var now by remember { mutableStateOf(Date()) }
    // Updates only while this composable is on screen; no background service.
    LaunchedEffect(Unit) { while (isActive) { now = Date(); delay(15_000L) } }
    GlassPanel(glass) {
        Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now), color = Color.White.copy(alpha = .8f))
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), fontSize = 62.sp, color = Color.White, fontWeight = FontWeight.Light)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = accent)
            Spacer(Modifier.width(8.dp))
            Text("Make today yours", color = Color.White.copy(alpha = .85f))
        }
    }
}

@Composable
private fun DockItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, click: () -> Unit) {
    Column(Modifier.width(73.dp).clickable(onClick = click).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, title, tint = Color.White, modifier = Modifier.size(27.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 11.sp, color = Color.White)
    }
}

@Composable
private fun AppGrid(apps: List<AppEntry>, columns: Int, showLabels: Boolean, open: (AppEntry) -> Unit, options: (AppEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        apps.chunked(columns).forEach { chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                chunk.forEach { app -> Box(Modifier.weight(1f)) { AppTile(app, showLabels, { open(app) }, { options(app) }) } }
                repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: AppEntry, labels: Boolean, open: () -> Unit, options: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = open, onLongClick = options).padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }, update = { view ->
            try { view.setImageDrawable(context.packageManager.getActivityIcon(ComponentName(app.packageName, app.activityName))) }
            catch (_: Exception) { view.setImageResource(android.R.drawable.sym_def_app_icon) }
        }, modifier = Modifier.size(54.dp))
        if (labels) {
            Spacer(Modifier.height(5.dp))
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, textAlign = TextAlign.Center, color = Color.White)
        }
    }
}

@Composable
private fun PinDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            Text("Use a 4–12 digit PIN. This controls launcher settings only; it is not a system app lock.")
            OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(12) }, label = { Text("PIN") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        }
    }, confirmButton = { TextButton(enabled = pin.length >= 4, onClick = { onConfirm(pin) }) { Text("Continue") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
