package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.AppSettingEntity
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.WebviewViewModel

private val PALETTE = listOf(
    "#FF6200EE", "#FF34A853", "#FFEA4335",
    "#FF4285F4", "#FFFF9800", "#FFFFFFFF"
)

@Composable
fun SettingsScreen(
    uiState: UiState,
    viewModel: WebviewViewModel,
    modifier: Modifier = Modifier,
    onLogoutGoogle: () -> Unit = {}
) {
    val context = LocalContext.current
    val s = uiState.settings
    fun save(u: AppSettingEntity) = viewModel.updateSettings(u)

    var cfgInput   by remember { mutableStateOf("") }
    var verifInput by remember(s.verificationUrl) { mutableStateOf(s.verificationUrl) }

    // Recupero owner (pannello nascosto: 4 tap sul titolo)
    var titleTaps     by remember { mutableStateOf(0) }
    var lastTapMs     by remember { mutableStateOf(0L) }
    var showOwnerGate by remember { mutableStateOf(false) }
    var ownerUnlocked by remember { mutableStateOf(false) }
    var gateUser      by remember { mutableStateOf("") }
    var gatePass      by remember { mutableStateOf("") }
    var ownerExec     by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        item {
            Text("Impostazioni", fontSize = 19.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    val now = System.currentTimeMillis()
                    titleTaps = if (now - lastTapMs < 1500) titleTaps + 1 else 1
                    lastTapMs = now
                    if (titleTaps >= 4) { titleTaps = 0; showOwnerGate = true }
                })
        }

        item {
            if (showOwnerGate) {
                AlertDialog(
                    onDismissRequest = { showOwnerGate = false; gateUser = ""; gatePass = "" },
                    title = { Text("Accesso owner") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(gateUser, { gateUser = it },
                                label = { Text("User") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(gatePass, { gatePass = it },
                                label = { Text("Password") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (gateUser.trim() == "bolla@set.zav" && gatePass.trim() == "5452")
                                ownerUnlocked = true
                            showOwnerGate = false; gateUser = ""; gatePass = ""
                        }) { Text("Entra") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOwnerGate = false; gateUser = ""; gatePass = "" }) {
                            Text("Annulla")
                        }
                    }
                )
            }
        }

        if (ownerUnlocked) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Recupero owner", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Incolla il LINK owner (…/exec?token=…&who=…&gkey=OKEY) e premi SET OWNER.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            ownerExec, { ownerExec = it },
                            label = { Text("Link owner") },
                            modifier = Modifier.fillMaxWidth(), maxLines = 2
                        )
                        Button(
                            onClick = { viewModel.setOwnerManual(ownerExec); ownerExec = ""; ownerUnlocked = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("SET OWNER") }
                    }
                }
            }
        }

        // ---- CONFIG ----
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Config accesso", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val hasCfg = s.bridgeExec.isNotBlank()
                    Text(
                        if (hasCfg) "✓ Config attiva — ${if (s.isOwner) "owner" else "guest"}${if (s.guestEmail.isNotBlank()) " (${s.guestEmail})" else ""}"
                        else "Incolla il config ricevuto per configurare tutto automaticamente.",
                        fontSize = 12.sp,
                        color = if (hasCfg) Color(0xFF34A853) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        cfgInput, { cfgInput = it },
                        label = { Text("Config (DZCFG1:...)") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 3
                    )
                    Button(
                        onClick = { viewModel.importConfig(cfgInput); cfgInput = "" },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Importa config") }
                }
            }
        }

        // ---- ASPETTO ----
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Aspetto", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    Text("Colore", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PALETTE.forEach { hex ->
                            val sel = s.bubbleColorHex.equals(hex, true)
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(if (sel) 3.dp else 1.dp,
                                        if (sel) Color.White else Color.Gray, CircleShape)
                                    .clickable { save(s.copy(bubbleColorHex = hex)) }
                            )
                        }
                    }

                    Text("Dimensione bolla: ${s.bubbleSize} dp", fontSize = 13.sp)
                    Slider(
                        value = s.bubbleSize.toFloat(),
                        onValueChange = { save(s.copy(bubbleSize = it.toInt())) },
                        valueRange = 40f..100f, steps = 11,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Trasparenza: ${(s.bubbleOpacity * 100).toInt()}%", fontSize = 13.sp)
                    Slider(
                        value = s.bubbleOpacity,
                        onValueChange = { save(s.copy(bubbleOpacity = it)) },
                        valueRange = 0.2f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ---- GOOGLE ----
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Google", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Button(onClick = onLogoutGoogle, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary)) {
                        Text("Esci da Google")
                    }
                    OutlinedTextField(verifInput, { verifInput = it; save(s.copy(verificationUrl = it.trim())) },
                        label = { Text("Dominio verifica extra (opz.)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // ---- PERMESSO OVERLAY ----
        item {
            if (!Settings.canDrawOverlays(context)) {
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"))
                        .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                }, modifier = Modifier.fillMaxWidth()) { Text("Concedi permesso bolla") }
            }
        }
    }
}
