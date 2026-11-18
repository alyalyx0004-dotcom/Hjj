package com.premiumglass.launcher

import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    store: LauncherStore,
    glass: Color,
    accent: Color,
    onChange: () -> Unit,
    onBack: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onWidget: () -> Unit,
    onRemoveWidget: () -> Unit,
    hasWidget: Boolean,
    onPin: () -> Unit,
    onManageHidden: () -> Unit,
    apps: List<AppEntry>,
    page: String,
    onToggleHidden: (AppEntry) -> Unit
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Text(if (page == "hidden") "Hidden apps" else "Launcher settings", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        if (page == "hidden") {
            Text("Hidden here only: apps remain installed and accessible elsewhere on Android.", color = Color.White.copy(alpha = .7f))
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(apps, key = { it.id }) { app ->
                    Row(Modifier.fillMaxWidth().clickable { onToggleHidden(app) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(app.label, color = Color.White, modifier = Modifier.weight(1f))
                        Checkbox(checked = app.id in store.hidden, onCheckedChange = { onToggleHidden(app) })
                    }
                }
            }
        } else {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Text("APPEARANCE", color = accent, style = MaterialTheme.typography.labelMedium)
                Text("Accent colors", color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(Color(0xFFA18AFF), Color(0xFF65BFFF), Color(0xFF52DFC3), Color(0xFFFF86B6)).forEachIndexed { index, color ->
                        Box(Modifier.size(42.dp).background(color, RoundedCornerShape(50)).clickable { store.accent = index; onChange() }, contentAlignment = Alignment.Center) {
                            if (index == store.accent) Icon(Icons.Default.Check, "Selected", tint = Color.Black)
                        }
                    }
                }
                Text("Glass panel opacity: ${(store.glassOpacity * 100).toInt()}%", color = Color.White)
                Slider(value = store.glassOpacity, onValueChange = { store.glassOpacity = it; onChange() }, valueRange = 0.08f..0.60f)
                Text("App grid columns: ${store.columns}", color = Color.White)
                Slider(value = store.columns.toFloat(), onValueChange = { store.columns = it.toInt(); onChange() }, valueRange = 3f..6f, steps = 2)
                Toggle("Show app labels", store.showLabels) { store.showLabels = it; onChange() }
                Toggle("Show launcher recent apps", store.showRecent) { store.showRecent = it; onChange() }
                Toggle("Reduce motion (reserved for future transitions)", store.reduceMotion) { store.reduceMotion = it; onChange() }
                HorizontalDivider(color = glass)
                Text("HOME & WIDGETS", color = accent, style = MaterialTheme.typography.labelMedium)
                Button(onClick = onWidget, modifier = Modifier.fillMaxWidth()) { Text("Choose Android widget") }
                if (hasWidget) OutlinedButton(onClick = onRemoveWidget, modifier = Modifier.fillMaxWidth()) { Text("Remove widget") }
                Text("Supports one Android AppWidget on the home screen. Some vendors limit third-party widget selection.", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = glass)
                Text("PRIVACY & ORGANIZATION", color = accent, style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = onManageHidden, modifier = Modifier.fillMaxWidth()) { Text("Manage hidden apps") }
                OutlinedButton(onClick = onPin, modifier = Modifier.fillMaxWidth()) { Text(if (store.hasPin()) "Remove privacy PIN (enter current PIN)" else "Set privacy PIN") }
                Text("Launcher hiding is not system-wide protection. No app data is accessed.", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = glass)
                Text("BACKUP & RESTORE", color = accent, style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("Export settings to JSON") }
                OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("Import settings from JSON") }
                Text("PIN credentials, widget bindings, and recent activity are excluded from backups.", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = glass)
                Text("ANDROID SETTINGS", color = accent, style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = {
                    try { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    catch (_: Exception) { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Choose default home app") }
                Text("Swipe up on the home screen for the app drawer; swipe down for search. Long-press an app to add it to the home screen.", color = Color.White.copy(alpha = .8f))
                Spacer(Modifier.height(35.dp))
            }
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = Color.White)
        Switch(checked = value, onCheckedChange = onChange)
    }
}
