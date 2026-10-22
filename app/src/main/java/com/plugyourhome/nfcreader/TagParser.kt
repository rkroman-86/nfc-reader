package com.plugyourhome.nfcreader

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import java.nio.charset.Charset

data class InfoRow(val label: String, val value: String)

data class NdefRecordInfo(
    val type: String,
    val content: String,
    val isLink: Boolean = false,
)

data class TagScan(
    val timestamp: Long,
    val uid: String,
    val tagType: String,
    val technologies: List<String>,
    val details: List<InfoRow>,
    val records: List<NdefRecordInfo>,
)

object TagParser {

    fun parse(tag: Tag): TagScan {
        val techs = tag.techList.map { it.substringAfterLast('.') }
        val details = mutableListOf<InfoRow>()
        var tagType = "Unknown"

        NfcA.get(tag)?.let { a ->
            details += InfoRow("ATQA", a.atqa.toHex())
            details += InfoRow("SAK", "0x%02X".format(a.sak.toInt()))
            tagType = "ISO 14443-3A"
        }
        NfcB.get(tag)?.let { b ->
            details += InfoRow("Application data", b.applicationData.toHex())
            details += InfoRow("Protocol info", b.protocolInfo.toHex())
            tagType = "ISO 14443-3B"
        }
        NfcF.get(tag)?.let { f ->
            details += InfoRow("Manufacturer", f.manufacturer.toHex())
            details += InfoRow("System code", f.systemCode.toHex())
            tagType = "FeliCa (JIS 6319-4)"
        }
        NfcV.get(tag)?.let { v ->
            details += InfoRow("DSF ID", "0x%02X".format(v.dsfId.toInt()))
            details += InfoRow("Response flags", "0x%02X".format(v.responseFlags.toInt()))
            tagType = "ISO 15693 (NFC-V)"
        }
        IsoDep.get(tag)?.let { d ->
            d.historicalBytes?.let { details += InfoRow("Historical bytes", it.toHex()) }
            d.hiLayerResponse?.let { details += InfoRow("Hi-layer response", it.toHex()) }
            details += InfoRow("Max transceive", "${d.maxTransceiveLength} bytes")
            tagType = "ISO 14443-4 (IsoDep)"
        }
        MifareClassic.get(tag)?.let { m ->
            tagType = when (m.type) {
                MifareClassic.TYPE_CLASSIC -> "MIFARE Classic"
                MifareClassic.TYPE_PLUS -> "MIFARE Plus"
                MifareClassic.TYPE_PRO -> "MIFARE Pro"
                else -> "MIFARE Classic (unknown)"
            }
            details += InfoRow("Memory", "${m.size} bytes · ${m.sectorCount} sectors · ${m.blockCount} blocks")
        }
        MifareUltralight.get(tag)?.let { u ->
            tagType = when (u.type) {
                MifareUltralight.TYPE_ULTRALIGHT -> "MIFARE Ultralight"
                MifareUltralight.TYPE_ULTRALIGHT_C -> "MIFARE Ultralight C"
                else -> "MIFARE Ultralight / NTAG"
            }
        }

        val records = mutableListOf<NdefRecordInfo>()
        Ndef.get(tag)?.let { ndef ->
            details += InfoRow("NDEF type", ndef.type.substringAfterLast('.'))
            details += InfoRow("NDEF capacity", "${ndef.maxSize} bytes")
            details += InfoRow("Writable", if (ndef.isWritable) "Yes" else "No (read-only)")
            val message: NdefMessage? = ndef.cachedNdefMessage ?: readNdef(ndef)
            message?.let {
                details += InfoRow("NDEF used", "${it.byteArrayLength} bytes")
                it.records.forEach { r -> records += parseRecord(r) }
            }
        }

        return TagScan(
            timestamp = System.currentTimeMillis(),
            uid = tag.id.toHex(":"),
            tagType = tagType,
            technologies = techs,
            details = details,
            records = records,
        )
    }

    private fun readNdef(ndef: Ndef): NdefMessage? = try {
        ndef.connect()
        ndef.ndefMessage
    } catch (_: Exception) {
        null
    } finally {
        try { ndef.close() } catch (_: Exception) {}
    }

    private fun parseRecord(r: NdefRecord): NdefRecordInfo {
        return when {
            r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_TEXT) ->
                NdefRecordInfo("Text", parseText(r.payload))

            r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_URI) ||
                r.tnf == NdefRecord.TNF_ABSOLUTE_URI -> {
                val uri: Uri? = r.toUri()
                NdefRecordInfo("URI", uri?.toString() ?: r.payload.toHex(), isLink = uri != null)
            }

            r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_SMART_POSTER) ->
                NdefRecordInfo("Smart Poster", r.toUri()?.toString() ?: r.payload.toHex(), isLink = r.toUri() != null)

            r.tnf == NdefRecord.TNF_MIME_MEDIA -> {
                val mime = String(r.type, Charsets.US_ASCII)
                val body = if (mime.startsWith("text/") || mime.contains("json") || mime.contains("vcard"))
                    String(r.payload, Charsets.UTF_8) else r.payload.toHex()
                NdefRecordInfo("MIME · $mime", body)
            }

            r.tnf == NdefRecord.TNF_EXTERNAL_TYPE ->
                NdefRecordInfo("External · ${String(r.type, Charsets.US_ASCII)}", r.payload.toPrintable())

            r.tnf == NdefRecord.TNF_EMPTY -> NdefRecordInfo("Empty", "—")

            else -> NdefRecordInfo("TNF ${r.tnf}", r.payload.toHex())
        }
    }

    /** NFC Forum Text RTD: status byte (encoding + lang length), lang code, text */
    private fun parseText(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val status = payload[0].toInt()
        val utf16 = status and 0x80 != 0
        val langLen = status and 0x3F
        val lang = String(payload, 1, langLen, Charsets.US_ASCII)
        val text = String(
            payload, 1 + langLen, payload.size - 1 - langLen,
            if (utf16) Charset.forName("UTF-16") else Charsets.UTF_8
        )
        return if (lang.isNotEmpty()) "$text  [$lang]" else text
    }
}

fun ByteArray.toHex(sep: String = " "): String =
    joinToString(sep) { "%02X".format(it) }

private fun ByteArray.toPrintable(): String {
    val s = String(this, Charsets.UTF_8)
    return if (s.all { it.isLetterOrDigit() || it.isWhitespace() || it in "-_.:/@{}[]\",=+?&#%" }) s else toHex()
}
