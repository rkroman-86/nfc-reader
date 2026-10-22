package com.plugyourhome.nfcreader

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private val Teal = Color(0xFF14B8A6)

@Composable
fun NfcReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Teal,
            onPrimary = Color(0xFF042F2E),
            background = Color(0xFF0F1720),
            surface = Color(0xFF0F1720),
            surfaceVariant = Color(0xFF1B2733),
            onSurface = Color(0xFFE6EDF3),
            onSurfaceVariant = Color(0xFF9FB0BF),
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: ScanViewModel, onOpenNfcSettings: () -> Unit) {
    val status by vm.status
    val history = vm.history
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFC Reader", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (history.isNotEmpty()) IconButton(onClick = vm::clear) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusCard(status, hasScans = history.isNotEmpty(), onOpenNfcSettings) }
            items(history, key = { it.timestamp }) { scan ->
                ScanCard(scan, latest = scan === history.firstOrNull(), snackbar)
            }
        }
    }
}

@Composable
private fun StatusCard(status: NfcStatus, hasScans: Boolean, onOpenNfcSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = if (hasScans) 16.dp else 40.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val pulse = rememberInfiniteTransition(label = "pulse")
            val s by pulse.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
            Icon(
                Icons.Default.Nfc, contentDescription = null,
                tint = if (status == NfcStatus.READY) Teal else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (hasScans) 36.dp else 72.dp)
                    .scale(if (status == NfcStatus.READY) s else 1f),
            )
            Spacer(Modifier.height(10.dp))
            when (status) {
                NfcStatus.READY -> Text(
                    if (hasScans) "Ready — scan another tag" else "Hold a tag against the back of your phone",
                    style = MaterialTheme.typography.titleMedium,
                )
                NfcStatus.DISABLED -> {
                    Text("NFC is turned off", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onOpenNfcSettings) { Text("Open NFC settings") }
                }
                NfcStatus.UNSUPPORTED -> Text(
                    "This device has no NFC hardware", style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanCard(scan: TagScan, latest: Boolean, snackbar: SnackbarHostState) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember(scan.timestamp) { mutableStateOf(latest) }

    fun copy(label: String, text: String) {
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbar.showSnackbar("$label copied") }
    }

    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.alpha(if (latest) 1f else 0.85f),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(scan.tagType, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(scan.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { copy("UID", scan.uid) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy UID")
                }
            }
            Spacer(Modifier.height(6.dp))
            Label("UID")
            SelectionContainer {
                Text(scan.uid, fontFamily = FontFamily.Monospace, fontSize = 17.sp, color = Teal)
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Label("Technologies")
                Text(scan.technologies.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)

                scan.details.forEach { row ->
                    Spacer(Modifier.height(8.dp))
                    Label(row.label)
                    SelectionContainer {
                        Text(row.value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (scan.records.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "NDEF records (${scan.records.size})",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    )
                    scan.records.forEachIndexed { i, r ->
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Label("#${i + 1} · ${r.type}")
                                SelectionContainer { Text(r.content, style = MaterialTheme.typography.bodyMedium) }
                                Row {
                                    TextButton(onClick = { copy("Record", r.content) }) { Text("Copy") }
                                    if (r.isLink) TextButton(onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.content)))
                                        }
                                    }) {
                                        Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Open")
                                    }
                                }
                            }
                        }
                    }
                } else if (scan.technologies.contains("Ndef") || scan.technologies.contains("NdefFormatable")) {
                    Spacer(Modifier.height(10.dp))
                    Text("No NDEF data on this tag", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    letterSpacing = 1.sp,
)
