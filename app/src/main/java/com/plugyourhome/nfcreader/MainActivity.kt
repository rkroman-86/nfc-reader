package com.plugyourhome.nfcreader

import android.app.Application
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel

enum class NfcStatus { READY, DISABLED, UNSUPPORTED }

class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val store = CodeStore(app)

    val status = mutableStateOf(NfcStatus.READY)
    val history = mutableStateListOf<TagScan>()   // newest first, kept for the session

    // "Read virtual card" mode: actively query a phone emitting the PYHOME card
    val virtualMode = mutableStateOf(false)
    val virtualResult = mutableStateOf<VirtualReadResult?>(null)

    // Saved codes (persisted) + emission settings
    val codes = mutableStateListOf<SavedCode>().apply { addAll(store.load()) }
    val activeId = mutableStateOf(store.activeId)
    val emitEnabled = mutableStateOf(store.emitEnabled)

    fun add(scan: TagScan) {
        history.add(0, scan)
        if (history.size > 50) history.removeAt(history.lastIndex)
    }
    fun clear() = history.clear()

    private fun persist() = store.save(codes.toList())

    fun saveCode(name: String, value: String, source: String) {
        val trimmed = value.take(CodeStore.MAX_LEN)
        val code = SavedCode(System.currentTimeMillis(), name.ifBlank { "Code ${codes.size + 1}" }, trimmed, source)
        codes.add(0, code)
        persist()
        if (codes.size == 1) setActive(code.id)   // first code becomes active automatically
    }

    fun rename(id: Long, name: String) {
        val i = codes.indexOfFirst { it.id == id }
        if (i >= 0) { codes[i] = codes[i].copy(name = name); persist() }
    }

    fun delete(id: Long) {
        codes.removeAll { it.id == id }
        persist()
        if (activeId.value == id) setActive(codes.firstOrNull()?.id ?: -1L)
    }

    fun setActive(id: Long) { activeId.value = id; store.activeId = id }

    fun setEmit(on: Boolean) { emitEnabled.value = on; store.emitEnabled = on }
}

class MainActivity : ComponentActivity() {

    private val vm: ScanViewModel by viewModels()
    private var adapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        adapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            NfcReaderTheme {
                MainScreen(
                    vm = vm,
                    onOpenNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                )
            }
        }
        // App was launched by scanning a tag
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val a = adapter
        vm.status.value = when {
            a == null -> NfcStatus.UNSUPPORTED
            !a.isEnabled -> NfcStatus.DISABLED
            else -> NfcStatus.READY
        }
        if (a != null && a.isEnabled) {
            // Reader mode: the app gets every tag directly while in the foreground,
            // without Android's "choose an app" dialog.
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE
            a.enableReaderMode(this, { tag -> onTag(tag) }, flags, Bundle())
        }
    }

    override fun onPause() {
        super.onPause()
        adapter?.disableReaderMode(this)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val tag: Tag? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        tag?.let { onTag(it) }
    }

    /** Called on a binder thread in reader mode — talk to the tag there, publish on the UI thread. */
    private fun onTag(tag: Tag) {
        if (vm.virtualMode.value) {
            val result = TagParser.readVirtualCard(tag)
            runOnUiThread { vm.virtualResult.value = result }
            return
        }
        val scan = try { TagParser.parse(tag) } catch (e: Exception) { null } ?: return
        runOnUiThread { vm.add(scan) }
    }
}
