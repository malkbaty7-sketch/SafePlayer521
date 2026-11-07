package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.SafePlayerApp
import com.example.util.LanguageManager
import com.example.util.findActivity
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: SafePlayerApp,
    onNavigateBack: () -> Unit,
    onNavigateToAiModels: () -> Unit,
    onNavigateToSources: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToIptv: () -> Unit = {},
    onNavigateToWifiShare: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showReleaseNotesDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val currentLangCode by LanguageManager.currentLanguageCode.collectAsStateWithLifecycle()
    val currentLanguage = remember(currentLangCode) { LanguageManager.getCurrentLanguage(context) }
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings_system), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // System Diagnostics Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.title_diagnostics), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("• App Version: 1.0.0 (Build 1) Production", style = MaterialTheme.typography.bodySmall)
                        Text("• Architecture: Local-First (Offline Media Engine)", style = MaterialTheme.typography.bodySmall)
                        Text("• Media Engine: AndroidX Media3 / ExoPlayer 1.5.1", style = MaterialTheme.typography.bodySmall)
                        Text("• Database: Room SQLite (Schema v11)", style = MaterialTheme.typography.bodySmall)
                        Text("• Operating System: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodySmall)
                        Text("• Hardware: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}", style = MaterialTheme.typography.bodySmall)
                        
                        val rt = Runtime.getRuntime()
                        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
                        val maxMb = rt.maxMemory() / (1024 * 1024)
                        Text("• Memory Heap: ${usedMb}MB used / ${maxMb}MB max", style = MaterialTheme.typography.bodySmall)

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val filePath = exportSafeDiagnostics(context, app)
                                    exportStatusMessage = "Diagnostics exported successfully"
                                    shareDiagnosticsFile(context, filePath)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_export_diagnostics))
                        }

                        exportStatusMessage?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Language Selection
            item {
                Text(stringResource(R.string.title_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    SettingsNavRow(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.title_language),
                        subtitle = "${currentLanguage.nativeName} (${currentLanguage.englishName})",
                        onClick = { showLanguageDialog = true }
                    )
                }
            }

            // Theme Selection
            item {
                Text(stringResource(R.string.title_theme_color), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                val themeColor by com.example.util.ThemeManager.themeColor.collectAsStateWithLifecycle()
                var showThemeDialog by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    SettingsNavRow(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.title_app_color_theme),
                        subtitle = when(themeColor) {
                            com.example.util.ThemeManager.COLOR_OCEAN -> stringResource(R.string.theme_ocean)
                            com.example.util.ThemeManager.COLOR_FOREST -> stringResource(R.string.theme_forest)
                            com.example.util.ThemeManager.COLOR_SLATE -> stringResource(R.string.theme_slate)
                            else -> stringResource(R.string.theme_bronze)
                        },
                        onClick = { showThemeDialog = true }
                    )
                }

                if (showThemeDialog) {
                    AlertDialog(
                        onDismissRequest = { showThemeDialog = false },
                        title = { Text(stringResource(R.string.action_select_theme), fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                val themes = listOf(
                                    com.example.util.ThemeManager.COLOR_BRONZE to stringResource(R.string.theme_bronze),
                                    com.example.util.ThemeManager.COLOR_OCEAN to stringResource(R.string.theme_ocean),
                                    com.example.util.ThemeManager.COLOR_FOREST to stringResource(R.string.theme_forest),
                                    com.example.util.ThemeManager.COLOR_SLATE to stringResource(R.string.theme_slate)
                                )
                                themes.forEach { (colorCode, label) ->
                                    val isSelected = themeColor == colorCode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                com.example.util.ThemeManager.setThemeColor(context, colorCode)
                                                showThemeDialog = false
                                            }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                com.example.util.ThemeManager.setThemeColor(context, colorCode)
                                                showThemeDialog = false
                                            }
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(label, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showThemeDialog = false }) {
                                Text(stringResource(R.string.action_close))
                            }
                        }
                    )
                }
            }

            // Core Features & Managers
            item {
                Text(stringResource(R.string.section_media_modules), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column {
                        SettingsNavRow(
                            icon = Icons.Default.Folder,
                            title = stringResource(R.string.item_sources_title),
                            subtitle = stringResource(R.string.item_sources_desc),
                            onClick = onNavigateToSources
                        )
                        HorizontalDivider()
                        SettingsNavRow(
                            icon = Icons.Default.PlaylistPlay,
                            title = stringResource(R.string.item_playlists_title),
                            subtitle = stringResource(R.string.item_playlists_desc),
                            onClick = onNavigateToPlaylists
                        )
                        HorizontalDivider()
                        SettingsNavRow(
                            icon = Icons.Default.Memory,
                            title = stringResource(R.string.item_models_title),
                            subtitle = stringResource(R.string.item_models_desc),
                            onClick = onNavigateToAiModels
                        )
                        HorizontalDivider()
                        SettingsNavRow(
                            icon = Icons.Default.TaskAlt,
                            title = stringResource(R.string.item_tasks_title),
                            subtitle = stringResource(R.string.item_tasks_desc),
                            onClick = onNavigateToTasks
                        )
                        HorizontalDivider()
                        SettingsNavRow(
                            icon = Icons.Default.Security,
                            title = stringResource(R.string.item_security_title),
                            subtitle = stringResource(R.string.item_security_desc),
                            onClick = onNavigateToSecurity
                        )
                        HorizontalDivider()
                        SettingsNavRow(
                            icon = Icons.Default.Storage,
                            title = stringResource(R.string.item_storage_title),
                            subtitle = stringResource(R.string.item_storage_desc),
                            onClick = onNavigateToStorage
                        )
                        HorizontalDivider()
                        SettingsNavRow(
                            icon = Icons.Default.LiveTv,
                            title = "بث IPTV والروابط المباشرة 📡",
                            subtitle = "تشغيل روابط البث المباشر وقوائم قنوات M3U",
                            onClick = onNavigateToIptv
                        )
                        HorizontalDivider()
                        SettingsNavRow(
                            icon = Icons.Default.Wifi,
                            title = "المشاركة والنقل عبر Wi-Fi 💻",
                            subtitle = "مشاركة ونقل الوسائط لاسلكياً مع الكمبيوتر والشاشات",
                            onClick = onNavigateToWifiShare
                        )
                        HorizontalDivider()
                        SettingsNavRow(
                            icon = Icons.Default.Insights,
                            title = "إحصائيات وسجل المشاهدة 📊",
                            subtitle = "سجل ساعات المشاهدة ونسبة إنجاز محتوى المكتبة",
                            onClick = onNavigateToAnalytics
                        )
                    }
                }
            }

            // Policies & Release
            item {
                Text(stringResource(R.string.section_info_support), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column {
                        SettingsNavRow(
                            icon = Icons.Default.PrivacyTip,
                            title = stringResource(R.string.item_privacy_title),
                            subtitle = stringResource(R.string.item_privacy_desc),
                            onClick = { showPrivacyDialog = true }
                        )
                        HorizontalDivider()
                        SettingsNavRow(
                            icon = Icons.Default.HistoryEdu,
                            title = stringResource(R.string.item_release_title),
                            subtitle = stringResource(R.string.item_release_desc),
                            onClick = { showReleaseNotesDialog = true }
                        )
                        HorizontalDivider()
                        SettingsNavRow(
                            icon = Icons.Default.Feedback,
                            title = stringResource(R.string.item_feedback_title),
                            subtitle = stringResource(R.string.item_feedback_desc),
                            onClick = { showFeedbackDialog = true }
                        )
                    }
                }
            }
        }
    }

    // Privacy Policy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(stringResource(R.string.item_privacy_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "1. Local-First Design:\nSafePlayer operates 100% offline for all playback, editing, scanning, and parental control features.\n\n" +
                        "2. Original Media Protection:\nYour original media files are never altered or deleted during editing. All censor operations are non-destructive.\n\n" +
                        "3. Zero Surveillance:\nSafePlayer does not analyze, upload, or collect your video or audio content for remote moderation or monitoring.\n\n" +
                        "4. Storage Access:\nOnly the directories and media you select are accessed, complying strictly with Android Scoped Storage and SAF standards.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(stringResource(R.string.action_understood))
                }
            }
        )
    }

    // Release Notes Dialog
    if (showReleaseNotesDialog) {
        AlertDialog(
            onDismissRequest = { showReleaseNotesDialog = false },
            title = { Text(stringResource(R.string.item_release_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Welcome to SafePlayer Production Release 1.0.0!\n\n" +
                        "• Media Player: Hardware accelerated playback with subtitle, aspect ratio, and gesture controls.\n" +
                        "• Manual Censorship Studio: Real-time blur, black box, audio muting, and clip trimming.\n" +
                        "• Non-Destructive Timeline: Full undo/redo, marker navigation, and project saving.\n" +
                        "• Media3 Transformer: Multi-format high quality export engine.\n" +
                        "• Safe Task Manager: Priority queue with thermal and memory management.\n" +
                        "• Security & Parental Control: App lock, PIN, biometric authentication, and audit logs.\n" +
                        "• Playlists & Media Sources: Complete organization of media directories.\n" +
                        "• Full Localization: Seamless English and Arabic RTL support.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showReleaseNotesDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    // Feedback Dialog
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text(stringResource(R.string.item_feedback_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Have feedback or found a bug in SafePlayer?\n\n" +
                    "You can attach the Safe Diagnostics JSON export file when contacting support to help diagnose playback or export issues without revealing any sensitive media information.\n\n" +
                    "Support: support@safeplayer.app",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val emailIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "message/rfc822"
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@safeplayer.app"))
                            putExtra(Intent.EXTRA_SUBJECT, "SafePlayer Feedback - v1.0.0")
                            putExtra(Intent.EXTRA_TEXT, "Device: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\n\nFeedback:\n")
                        }
                        try {
                            context.startActivity(Intent.createChooser(emailIntent, "Send Feedback"))
                        } catch (_: Exception) {}
                        showFeedbackDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_email_support))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.title_language), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            stringResource(R.string.desc_language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    items(LanguageManager.SUPPORTED_LANGUAGES) { lang ->
                        val isSelected = (lang.code == currentLangCode)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LanguageManager.setLanguage(context, lang.code)
                                    showLanguageDialog = false
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.language_changed_toast),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    context.findActivity()?.recreate()
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    LanguageManager.setLanguage(context, lang.code)
                                    showLanguageDialog = false
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.language_changed_toast),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    context.findActivity()?.recreate()
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    lang.nativeName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    lang.englishName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.action_cancel_dialog))
                }
            }
        )
    }
}

@Composable
fun SettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun exportSafeDiagnostics(context: Context, app: SafePlayerApp): String {
    val diag = JSONObject().apply {
        put("app_name", "SafePlayer")
        put("version_name", "1.0.0")
        put("version_code", 1)
        put("build_type", "release")
        put("timestamp", System.currentTimeMillis())
        put("os_release", Build.VERSION.RELEASE)
        put("os_sdk", Build.VERSION.SDK_INT)
        put("manufacturer", Build.MANUFACTURER)
        put("device_model", Build.MODEL)
        put("database_version", 11)
        put("media_engine", "AndroidX Media3 ExoPlayer 1.5.1")
        put("privacy_mode", "Local-First / No Surveillance")
        
        val rt = Runtime.getRuntime()
        put("memory_total_mb", rt.totalMemory() / (1024 * 1024))
        put("memory_free_mb", rt.freeMemory() / (1024 * 1024))
        put("memory_max_mb", rt.maxMemory() / (1024 * 1024))
    }

    val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
    val file = File(dir, "SafePlayer_Diagnostics_${System.currentTimeMillis()}.json")
    FileOutputStream(file).use { out ->
        out.write(diag.toString(2).toByteArray())
    }
    return file.absolutePath
}

private fun shareDiagnosticsFile(context: Context, path: String) {
    try {
        val file = File(path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostics"))
    } catch (_: Exception) {
        // Fallback simple share
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "SafePlayer v1.0.0 Diagnostics generated at $path")
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Diagnostics"))
    }
}
