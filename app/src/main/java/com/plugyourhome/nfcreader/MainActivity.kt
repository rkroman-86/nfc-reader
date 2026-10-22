package com.plugyourhome.nfcreader

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
import androidx.lifecycle.ViewModel

enum class NfcStatus { READY, DISABLED, UNSUPPORTED }

class ScanViewModel : ViewModel() {
    val status = mutableStateOf(NfcStatus.READY)
    val history = mutableStateListOf<TagScan>()   // newest first, kept for the session

    fun add(scan: TagScan) {
        history.add(0, scan)
        if (history.size > 50) history.removeAt(history.lastIndex)
    }
    fun clear() = history.clear()
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

    /** Called on a binder thread in reader mode — parse there, publish on the UI thread. */
    private fun onTag(tag: Tag) {
        val scan = try { TagParser.parse(tag) } catch (e: Exception) { null } ?: return
        runOnUiThread { vm.add(scan) }
    }
}
