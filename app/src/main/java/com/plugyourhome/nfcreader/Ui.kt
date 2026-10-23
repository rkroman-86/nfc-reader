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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFC Reader", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (tab == 0 && vm.history.isNotEmpty()) IconButton(onClick = vm::clear) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Reader") })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Codes") })
            }
            when (tab) {
                0 -> ReaderTab(vm, snackbar, onSaved = { tab = 1 }, onOpenNfcSettings)
                else -> CodesTab(vm, snackbar)
            }
        }
    }
}

// ───────────────────────── Reader tab ─────────────────────────

@Composable
private fun ReaderTab(
    vm: ScanViewModel,
    snackbar: SnackbarHostState,
    onSaved: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    val status by vm.status
    val history = vm.history
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusCard(status, hasScans = history.isNotEmpty(), onOpenNfcSettings) }
        item { VirtualReadCard(vm, snackbar) }
        items(history, key = { it.timestamp }) { scan ->
            ScanCard(scan, latest = scan === history.firstOrNull(), snackbar, vm, onSaved)
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

@Composable
private fun VirtualReadCard(vm: ScanViewModel, snackbar: SnackbarHostState) {
    val on by vm.virtualMode
    val result by vm.virtualResult
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (on) Teal.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Read virtual card", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Query a phone in Emit mode for its PYHOME code",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = on, onCheckedChange = { vm.virtualMode.value = it; vm.virtualResult.value = null })
            }
            if (on) {
                Spacer(Modifier.height(8.dp))
                val r = result
                when {
                    r == null -> Text(
                        "Hold the other (unlocked) phone against this one…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    r.ok -> {
                        Label("Code received")
                        SelectionContainer {
                            Text(r.code ?: "", fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = Teal)
                        }
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(r.code ?: ""))
                            scope.launch { snackbar.showSnackbar("Code copied") }
                        }) { Text("Copy") }
                    }
                    else -> Text(r.message, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanCard(
    scan: TagScan,
    latest: Boolean,
    snackbar: SnackbarHostState,
    vm: ScanViewModel,
    onSaved: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember(scan.timestamp) { mutableStateOf(latest) }

    fun copy(label: String, text: String) {
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbar.showSnackbar("$label copied") }
    }
    fun save(name: String, value: String, source: String) {
        vm.saveCode(name, value, source)
        scope.launch { snackbar.showSnackbar("Saved to Codes") }
        onSaved()
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
            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = { save("UID ${scan.uid.take(8)}", scan.uid, "UID") },
                label = { Text("Save UID as code") },
                leadingIcon = { Icon(Icons.Default.Save, null, Modifier.size(18.dp)) },
            )

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
                                    TextButton(onClick = { save(r.type, r.content, r.type) }) { Text("Save") }
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

// ───────────────────────── Codes tab ─────────────────────────

@Composable
private fun CodesTab(vm: ScanViewModel, snackbar: SnackbarHostState) {
    val codes = vm.codes
    val activeId by vm.activeId
    val emit by vm.emitEnabled
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavedCode?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { EmitCard(emit, codes.firstOrNull { it.id == activeId }, onToggle = vm::setEmit) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Saved codes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add")
                }
            }
        }

        if (codes.isEmpty()) item {
            Text(
                "No codes yet. Scan a tag and tap “Save”, or add one by hand.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(codes, key = { it.id }) { code ->
            CodeRow(
                code = code,
                active = code.id == activeId,
                onSelect = { vm.setActive(code.id) },
                onEdit = { editing = code },
                onDelete = { vm.delete(code.id) },
            )
        }
    }

    if (showAdd) NameValueDialog(
        title = "Add code",
        initialName = "",
        initialValue = "",
        onDismiss = { showAdd = false },
        onConfirm = { n, v -> vm.saveCode(n, v, "Manual"); showAdd = false },
    )
    editing?.let { e ->
        NameValueDialog(
            title = "Rename",
            initialName = e.name,
            initialValue = e.value,
            valueEditable = false,
            onDismiss = { editing = null },
            onConfirm = { n, _ -> vm.rename(e.id, n); editing = null },
        )
    }
}

@Composable
private fun EmitCard(emit: Boolean, active: SavedCode?, onToggle: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emit) Teal.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, null, tint = if (emit) Teal else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Emit to reader", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (active != null) "Active: ${active.name}" else "No code selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = emit && active != null, enabled = active != null, onCheckedChange = onToggle)
            }
            if (emit && active != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Unlock the phone and hold it against your reader. It answers the PYHOME badge request with this code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CodeRow(
    code: SavedCode,
    active: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) Teal.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        ),
        border = if (active) null else CardDefaults.outlinedCardBorder(),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = active, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(code.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    code.value,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) Teal else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(code.source, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Rename", Modifier.size(20.dp)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun NameValueDialog(
    title: String,
    initialName: String,
    initialValue: String,
    valueEditable: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (name: String, value: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                if (valueEditable) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value, { value = it.take(CodeStore.MAX_LEN) },
                        label = { Text("Code / value") },
                        supportingText = { Text("UID hex, text, or any string your reader expects") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, value) },
                enabled = name.isNotBlank() && (!valueEditable || value.isNotBlank()),
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Label(text: String) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    letterSpacing = 1.sp,
)
