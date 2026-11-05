package com.example.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.SecurityManager
import kotlinx.coroutines.launch

@Composable
fun AppLockOverlay(
    securityManager: SecurityManager,
    onUnlocked: () -> Unit
) {
    val config by securityManager.securityConfig.collectAsStateWithLifecycle(initialValue = null)
    val isUnlocked by securityManager.isUnlocked.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    if (!isUnlocked && config?.isAppLockEnabled == true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Text(
                        "التطبيق محمي برمز سري 🔒",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "أدخل الرمز السري (PIN) أو استخدم البصمة للمتابعة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = {
                            if (it.length <= 6) {
                                enteredPin = it
                                errorText = null
                                if (it.length >= 4) {
                                    scope.launch {
                                        val success = securityManager.verifyAppPin(it)
                                        if (success) {
                                            enteredPin = ""
                                            onUnlocked()
                                        }
                                    }
                                }
                            }
                        },
                        label = { Text("الرمز السري (PIN)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = errorText != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorText?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (config?.isBiometricEnabled == true) {
                            FilledTonalIconButton(
                                onClick = {
                                    securityManager.unlockWithBiometrics()
                                    onUnlocked()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = "فتح بالبصمة",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val remainingCooldown = securityManager.getLockoutRemainingSeconds()
                                    if (remainingCooldown > 0) {
                                        errorText = "محاولات خاطئة كثيرة. انتظر $remainingCooldown ثانية."
                                        return@launch
                                    }
                                    val success = securityManager.verifyAppPin(enteredPin)
                                    if (success) {
                                        enteredPin = ""
                                        onUnlocked()
                                    } else {
                                        errorText = "الرمز السري غير صحيح"
                                        enteredPin = ""
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("فتح القفل")
                        }
                    }
                }
            }
        }
    }
}
