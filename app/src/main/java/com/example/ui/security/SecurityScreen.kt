package com.example.ui.security

import android.content.Context
import android.content.ContextWrapper
import android.text.format.Formatter
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.SafePlayerApp
import com.example.data.MediaItem
import com.example.data.MediaType
import com.example.security.VaultAuthResult
import com.example.security.data.SecurityAuditLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    app: SafePlayerApp,
    onNavigateBack: () -> Unit,
    onPlayMedia: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val securityManager = app.securityManager
    val scope = rememberCoroutineScope()
    val config by securityManager.securityConfig.collectAsStateWithLifecycle(initialValue = null)
    val isParentAuth by securityManager.isParentAuthorized.collectAsStateWithLifecycle()
    val isVaultUnlocked by securityManager.isVaultUnlocked.collectAsStateWithLifecycle()
    val isDecoyModeActive by securityManager.isDecoyModeActive.collectAsStateWithLifecycle()
    val isDecoyConfigured by securityManager.isDecoyConfigured.collectAsStateWithLifecycle()
    val protectedMediaIds by securityManager.protectedMediaIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val allMedia by app.repository.allMedia.collectAsStateWithLifecycle(initialValue = emptyList())
    val auditLogs by app.database.securityDao().getRecentAuditLogsFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    val vaultItems = remember(allMedia, protectedMediaIds, isDecoyModeActive) {
        if (isDecoyModeActive) emptyList()
        else allMedia.filter { it.id in protectedMediaIds }
    }
    val unprotectedItems = remember(allMedia, protectedMediaIds) {
        allMedia.filter { it.id !in protectedMediaIds }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Safe Vault, 1 = Security & Parental Settings
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showDisableAppLockDialog by remember { mutableStateOf(false) }
    var currentPinInput by remember { mutableStateOf("") }
    var disablePinError by remember { mutableStateOf<String?>(null) }
    var showDecoyPinDialog by remember { mutableStateOf(false) }
    var showParentalSetupDialog by remember { mutableStateOf(false) }
    var showParentAuthDialog by remember { mutableStateOf(false) }
    var showAddMediaToVaultSheet by remember { mutableStateOf(false) }

    var vaultPinInput by remember { mutableStateOf("") }
    var vaultPinError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الخزنة السرية والحماية 🔐", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (isVaultUnlocked) {
                        IconButton(
                            onClick = {
                                securityManager.lockVault()
                                Toast.makeText(context, "تم قفل الخزنة السرية 🔒", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "قفل الخزنة", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("الخزنة السرية (${if (isVaultUnlocked && !isDecoyModeActive) vaultItems.size else "🔒"})", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.EnhancedEncryption, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("إعدادات الأمان والرقابة", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Shield, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                // TAB 0: SAFE VAULT PRO
                val needsVaultUnlock = config?.isAppLockEnabled == true && !isVaultUnlocked
                if (needsVaultUnlock) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.EnhancedEncryption,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Text(
                                    text = "الخزنة السرية مقفلة 🔐",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "أدخل رمز PIN الخاص بك أو استخدم البصمة لفتح ملفاتك الخاصة (يدعم رمز التمويه الذكي)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                OutlinedTextField(
                                    value = vaultPinInput,
                                    onValueChange = {
                                        if (it.length <= 6) {
                                            vaultPinInput = it
                                            vaultPinError = null
                                        }
                                    },
                                    label = { Text("الرمز السري (PIN)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    isError = vaultPinError != null,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                vaultPinError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (config?.isBiometricEnabled == true) {
                                        FilledTonalButton(
                                            onClick = {
                                                val activity = context.findFragmentActivity()
                                                val availability = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                                                if (activity == null) {
                                                    Toast.makeText(context, "تعذر فتح نافذة التحقق البيومتري", Toast.LENGTH_SHORT).show()
                                                } else if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
                                                    Toast.makeText(context, "البصمة غير متاحة أو لم تُعد على هذا الجهاز", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val prompt = BiometricPrompt(
                                                        activity,
                                                        ContextCompat.getMainExecutor(context),
                                                        object : BiometricPrompt.AuthenticationCallback() {
                                                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                                super.onAuthenticationSucceeded(result)
                                                                securityManager.unlockWithBiometrics()
                                                                Toast.makeText(context, "تم فتح الخزنة السرية بالبصمة 🔓", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    )
                                                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                                        .setTitle("فتح الخزنة السرية")
                                                        .setSubtitle("تحقق من هويتك للمتابعة")
                                                        .setNegativeButtonText("إلغاء")
                                                        .build()
                                                    prompt.authenticate(promptInfo)
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("البصمة")
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val res = securityManager.verifyVaultOrAppPin(vaultPinInput)
                                                vaultPinInput = ""
                                                when (res) {
                                                    VaultAuthResult.REAL_UNLOCKED -> {
                                                        Toast.makeText(context, "تم فتح الخزنة السرية بنجاح 🔓", Toast.LENGTH_SHORT).show()
                                                    }
                                                    VaultAuthResult.DECOY_UNLOCKED -> {
                                                        Toast.makeText(context, "تم فتح الخزنة", Toast.LENGTH_SHORT).show()
                                                    }
                                                    VaultAuthResult.INVALID -> {
                                                        vaultPinError = "الرمز السري غير صحيح"
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("فتح الخزنة")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Vault is Unlocked (or PIN not yet set)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (config?.isAppLockEnabled != true) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("الخزنة غير محمية برمز PIN بعد!", fontWeight = FontWeight.Bold)
                                        Text("قم بتعيين رمز PIN وتفعيل البصمة لحماية ملفاتك المخفية", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Button(onClick = { showPinSetupDialog = true }) {
                                        Text("تعيين PIN")
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isDecoyModeActive) "الملفات المحمية (0)" else "الملفات المحمية (${vaultItems.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isDecoyModeActive) {
                                Button(onClick = { showAddMediaToVaultSheet = true }) {
                                    Icon(Icons.Default.AddModerator, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("إخفاء ملفات في الخزنة")
                                }
                            }
                        }

                        if (vaultItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.LockOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "لا توجد ملفات مخفية في الخزنة السرية حالياً",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "اضغط على 'إخفاء ملفات في الخزنة' لحماية مقاطع الفيديو أو الصوت الخاصة بك",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(vaultItems, key = { it.id }) { media ->
                                    VaultMediaCard(
                                        media = media,
                                        onPlay = { onPlayMedia(media.id) },
                                        onRemoveFromVault = {
                                            scope.launch {
                                                securityManager.toggleItemProtection("MEDIA", media.id)
                                                Toast.makeText(context, "تم إخراج الملف من الخزنة وإعادته للمكتبة", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // TAB 1: SECURITY & PARENTAL SETTINGS
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Section 1: App & Vault Lock + Biometrics + Decoy PIN
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("قفل التطبيق والخزنة السرية (PIN & Biometrics)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "حماية التطبيق والخزنة السرية بالرمز السري أو البصمة",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = config?.isAppLockEnabled == true,
                                        onCheckedChange = { enabled ->
                                            if (enabled) {
                                                showPinSetupDialog = true
                                            } else {
                                                showDisableAppLockDialog = true
                                            }
                                        }
                                    )
                                }

                                if (config?.isAppLockEnabled == true) {
                                    HorizontalDivider()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text("تفعيل الدخول بالبصمة (Biometric Auth)", style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Switch(
                                            checked = config?.isBiometricEnabled == true,
                                            onCheckedChange = { enabled ->
                                                scope.launch {
                                                    val curr = config ?: return@launch
                                                    app.database.securityDao().saveSecurityConfig(curr.copy(isBiometricEnabled = enabled))
                                                }
                                            }
                                        )
                                    }

                                    // Decoy PIN (وضع التمويه الذكي)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("وضع التمويه الذكي (Decoy PIN) 🕵️", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                if (isDecoyConfigured) "مفعل: إدخال رمز التمويه يفتح خزنة وهمية فارغة"
                                                else "إعداد رمز بديل يفتح خزنة فارغة عند التطفل",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isDecoyConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        FilledTonalButton(onClick = { showDecoyPinDialog = true }) {
                                            Text(if (isDecoyConfigured) "تعديل" else "إعداد")
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showPinSetupDialog = true },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("تغيير رمز PIN")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                securityManager.lockApp("قفل يدوي فوري")
                                                Toast.makeText(context, "تم قفل التطبيق والخزنة 🔒", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("قفل الآن")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 2: Screen Capture Protection (FLAG_SECURE)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("منع لقطات الشاشة والتسجيل (FLAG_SECURE)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "حجب تصوير الشاشة وإخفاء محتوى التطبيق في قائمة التطبيقات الحديثة",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = config?.isScreenshotProtectionEnabled == true,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            securityManager.setScreenshotProtection(enabled)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Section 3: Parental Controls
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("الرقابة الأبوية وحماية العائلة 👨‍👩‍👧‍👦", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "تقييد الحذف أو التعديل أو المشاركة بدون إذن الوالدين",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = config?.isParentalControlEnabled == true,
                                        onCheckedChange = { enabled ->
                                            if (enabled) {
                                                showParentalSetupDialog = true
                                            } else {
                                                scope.launch {
                                                    val curr = config ?: return@launch
                                                    app.database.securityDao().saveSecurityConfig(
                                                        curr.copy(isParentalControlEnabled = false, userRole = "OWNER")
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }

                                if (config?.isParentalControlEnabled == true) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (isParentAuth) "الحالة: وضع الوالدين مفعل ✅" else "الحالة: وضع الطفل المقيد 🛡️",
                                            fontWeight = FontWeight.Bold,
                                            color = if (isParentAuth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                        if (isParentAuth) {
                                            TextButton(onClick = { securityManager.clearParentAuthorization() }) {
                                                Text("تفعيل القفل الأبوي")
                                            }
                                        } else {
                                            FilledTonalButton(onClick = { showParentAuthDialog = true }) {
                                                Text("إدخال رمز الوالدين")
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { showParentalSetupDialog = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("تخصيص القيود الأبوية والرمز السري")
                                    }
                                }
                            }
                        }
                    }

                    // Section 4: Security Audit Trail
                    item {
                        Text("سجل النشاط الأمني (Security Audit Log)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    if (auditLogs.isEmpty()) {
                        item {
                            Text("لا توجد أحداث أمنية مسجلة حتى الآن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(auditLogs.take(15)) { log ->
                            AuditLogItem(log = log)
                        }
                    }
                }
            }
        }
    }

    // Bottom Sheet: Add Media to Safe Vault
    if (showAddMediaToVaultSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddMediaToVaultSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "اختر ملفات لإخفائها في الخزنة السرية 🔐",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (unprotectedItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("جميع الملفات موجودة بالفعل في الخزنة أو لا توجد ملفات في المكتبة")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(unprotectedItems, key = { it.id }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            securityManager.toggleItemProtection("MEDIA", item.id)
                                            Toast.makeText(context, "تم نقل '${item.name}' إلى الخزنة السرية 🔒", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Default.VideoLibrary else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(item.folder, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.EnhancedEncryption, contentDescription = "إخفاء", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // PIN Setup Dialog
    if (showPinSetupDialog) {
        var pin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = { Text("تعيين الرمز السري الحقيقي (PIN) 🔐", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6) pin = it },
                        label = { Text("أدخل رمز PIN من 4 إلى 6 أرقام") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 6) confirmPin = it },
                        label = { Text("تأكيد رمز PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pin.length < 4) {
                        error = "يجب أن يتكون الرمز من 4 أرقام على الأقل"
                    } else if (pin != confirmPin) {
                        error = "الرمزان غير متطابقين"
                    } else {
                        scope.launch {
                            securityManager.setupAppLock(pin, enableBiometric = true, autoLockSeconds = 60)
                            showPinSetupDialog = false
                            Toast.makeText(context, "تم تفعيل حماية الخزنة والتطبيق بنجاح 🔐", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("حفظ الرمز")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Confirm the current real PIN before disabling the app lock.
    if (showDisableAppLockDialog) {
        AlertDialog(
            onDismissRequest = {
                showDisableAppLockDialog = false
                currentPinInput = ""
                disablePinError = null
            },
            title = { Text("تأكيد إيقاف القفل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل رمز PIN الحقيقي لإيقاف حماية التطبيق.")
                    OutlinedTextField(
                        value = currentPinInput,
                        onValueChange = { value -> if (value.length <= 6 && value.all { it.isDigit() }) currentPinInput = value },
                        label = { Text("رمز PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = disablePinError != null
                    )
                    disablePinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val disabled = securityManager.disableAppLock(currentPinInput)
                        if (disabled) {
                            showDisableAppLockDialog = false
                            currentPinInput = ""
                            disablePinError = null
                            Toast.makeText(context, "تم إيقاف قفل التطبيق", Toast.LENGTH_SHORT).show()
                        } else {
                            disablePinError = "رمز PIN غير صحيح"
                        }
                    }
                }, enabled = currentPinInput.isNotBlank()) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDisableAppLockDialog = false
                    currentPinInput = ""
                    disablePinError = null
                }) { Text("إلغاء") }
            }
        )
    }

    // Decoy PIN Setup Dialog (وضع التمويه الذكي)
    if (showDecoyPinDialog) {
        var decoyPin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showDecoyPinDialog = false },
            title = { Text("إعداد رمز التمويه الذكي (Decoy PIN) 🕵️", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "عند إدخال هذا الرمز البديل، سيتم فتح خزنة وهمية فارغة تماماً لإبعاد الشبهات وحماية ملفاتك الحقيقية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = decoyPin,
                        onValueChange = { if (it.length <= 6) decoyPin = it },
                        label = { Text("رمز التمويه (4-6 أرقام مختلفة عن الرمز الأصلي)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (decoyPin.length < 4) {
                        error = "يجب أن يتكون رمز التمويه من 4 أرقام على الأقل"
                    } else {
                        securityManager.setupDecoyPin(decoyPin)
                        showDecoyPinDialog = false
                        Toast.makeText(context, "تم تفعيل رمز التمويه الذكي بنجاح 🕵️", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("حفظ رمز التمويه")
                }
            },
            dismissButton = {
                Row {
                    if (isDecoyConfigured) {
                        TextButton(onClick = {
                            securityManager.setupDecoyPin(null)
                            showDecoyPinDialog = false
                            Toast.makeText(context, "تم إلغاء رمز التمويه", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("إزالة التمويه", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showDecoyPinDialog = false }) {
                        Text("إغلاق")
                    }
                }
            }
        )
    }

    // Parental Controls Setup Dialog
    if (showParentalSetupDialog) {
        var parentPin by remember { mutableStateOf("") }
        var blockDelete by remember { mutableStateOf(true) }
        var blockExport by remember { mutableStateOf(true) }
        var blockShare by remember { mutableStateOf(true) }
        var blockEdit by remember { mutableStateOf(true) }
        var blockAddSource by remember { mutableStateOf(true) }
        val blockSettings by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showParentalSetupDialog = false },
            title = { Text("إعداد الرقابة الأبوية 👨‍👩‍👧‍👦", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = parentPin,
                        onValueChange = { if (it.length <= 6) parentPin = it },
                        label = { Text("رمز الوالدين (4-6 أرقام)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("اختر الإجراءات المقيدة:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = blockDelete, onCheckedChange = { blockDelete = it })
                        Text("منع حذف الوسائط", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = blockExport, onCheckedChange = { blockExport = it })
                        Text("منع تصدير الفيديو", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = blockShare, onCheckedChange = { blockShare = it })
                        Text("منع مشاركة الملفات", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = blockEdit, onCheckedChange = { blockEdit = it })
                        Text("منع التعديل والمونتاج", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = blockAddSource, onCheckedChange = { blockAddSource = it })
                        Text("منع إضافة مجلدات خارجية", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (parentPin.length < 4) {
                        error = "يجب أن يتكون الرمز من 4 أرقام على الأقل"
                    } else {
                        scope.launch {
                            securityManager.setupParentalControl(
                                parentPin = parentPin,
                                blockDelete = blockDelete,
                                blockExport = blockExport,
                                blockShare = blockShare,
                                blockEdit = blockEdit,
                                blockAddSource = blockAddSource,
                                blockSettings = blockSettings
                            )
                            showParentalSetupDialog = false
                        }
                    }
                }) {
                    Text("حفظ وتفعيل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showParentalSetupDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Parent Auth Dialog
    if (showParentAuthDialog) {
        var enteredPin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showParentAuthDialog = false },
            title = { Text("تصريح الوالدين") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل رمز الوالدين لفتح القيود:")
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { if (it.length <= 6) enteredPin = it },
                        label = { Text("رمز الوالدين") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val ok = securityManager.verifyParentPin(enteredPin)
                        if (ok) {
                            showParentAuthDialog = false
                        } else {
                            error = "رمز الوالدين غير صحيح"
                        }
                    }
                }) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showParentAuthDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun VaultMediaCard(
    media: MediaItem,
    onPlay: () -> Unit,
    onRemoveFromVault: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (media.mediaType == MediaType.VIDEO) Icons.Default.PlayCircleFilled else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${Formatter.formatFileSize(context, media.sizeBytes)} • محفوظ في الخزنة 🔒",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onRemoveFromVault) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "إخراج من الخزنة",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun AuditLogItem(log: SecurityAuditLog) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(log.eventType, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(dateFormat.format(Date(log.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Text(log.details, style = MaterialTheme.typography.bodySmall)
        }
    }
}


private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current != null) {
        if (current is FragmentActivity) return current
        val next = (current as? ContextWrapper)?.baseContext ?: return null
        if (next === current) return null
        current = next
    }
    return null
}
