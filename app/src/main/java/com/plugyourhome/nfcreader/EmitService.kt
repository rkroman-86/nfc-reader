package com.plugyourhome.nfcreader

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Host Card Emulation service: the phone answers your own alarm reader with the active saved code.
 *
 * Protocol (you control both ends — phone and ESP32/PN532 reader):
 *   Reader sends a SELECT-by-AID for AID  F0 50 59 48 4F 4D 45 01   ("PYHOME" + 01).
 *   Phone replies with the code bytes (UTF-8) followed by status 90 00.
 *   If emission is off or no code is active, the phone replies 6A 82 (not found),
 *   so a tag that isn't armed simply reads as "nothing here".
 */
class EmitService : HostApduService() {

    override fun processCommandApdu(apdu: ByteArray?, extras: Bundle?): ByteArray {
        if (apdu == null) return SW_ERROR
        if (isSelectAid(apdu)) {
            val store = CodeStore(applicationContext)
            if (!store.emitEnabled) return SW_NOT_FOUND
            val code = store.activeCode() ?: return SW_NOT_FOUND
            return code.value.toByteArray(Charsets.UTF_8) + SW_OK
        }
        return SW_UNKNOWN
    }

    override fun onDeactivated(reason: Int) { /* field lost or another AID selected */ }

    private fun isSelectAid(apdu: ByteArray): Boolean {
        // 00 A4 04 00 <len> <aid...>
        if (apdu.size < 6) return false
        if (apdu[0].toInt() and 0xFF != 0x00) return false
        if (apdu[1].toInt() and 0xFF != 0xA4) return false
        if (apdu[2].toInt() and 0xFF != 0x04) return false
        val len = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + len) return false
        val aid = apdu.copyOfRange(5, 5 + len)
        return aid.contentEquals(AID)
    }

    companion object {
        // "PYHOME" (50 59 48 4F 4D 45) + 01 — a custom, non-registered AID in the F0 range
        private val AID = byteArrayOf(0xF0.toByte(), 0x50, 0x59, 0x48, 0x4F, 0x4D, 0x45, 0x01)
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        private val SW_UNKNOWN = byteArrayOf(0x6D, 0x00)
        private val SW_ERROR = byteArrayOf(0x6F, 0x00)
    }
}
