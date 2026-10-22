# NFC Reader — plug-your-home

Android app (Kotlin + Jetpack Compose) that reads any NFC tag held against the phone.

## What it shows
- UID (tap the copy icon), tag type (MIFARE Classic / Ultralight / NTAG, ISO 14443-A/B, ISO 15693, FeliCa, IsoDep)
- Low-level info: ATQA, SAK, historical bytes, memory size, sectors/blocks
- NDEF: capacity, used size, writable or not, and every record decoded
  (Text with language, URI / Smart Poster with an "Open" button, MIME incl. vCard/JSON, external types)
- Session history of the last 50 scans (tap a card to expand/collapse)
- Detects NFC off (button to NFC settings) or missing NFC hardware

The app uses Reader Mode while in the foreground (no "choose an app" popup)
and can also be launched by scanning a tag while it's closed.

## Build
1. Android Studio (Ladybug or newer) → File › Open → this folder.
2. Let Gradle sync (it downloads Gradle 8.9, AGP 8.7, Kotlin 2.0).
   If Studio offers to upgrade AGP/Gradle, accept.
3. Run on a real phone with NFC (the emulator has no NFC).

Package: `com.plugyourhome.nfcreader` · minSdk 26 · targetSdk 35.

## Phone-as-badge (HCE) — for your own alarm

The **Codes** tab lets you save a value (scanned UID, NDEF text, or typed by hand),
pick one as *active*, and turn on **Emit to reader**. The phone then behaves as a
card: when *your* reader selects the app's AID, the phone returns the active code.

Important: this is **not** cloning a tag's UID. Android generates the UID in the
NFC controller and won't let an app forge it, so a real badge cannot be replayed.
Instead you define a shared value and recognise it on a reader *you* own — a proper
challenge you control on both ends. It's meant for your own system, not for
presenting someone else's credential.

- **AID:** `F0 50 59 48 4F 4D 45 01` ("PYHOME" + 01) — a custom, non-registered AID.
- **SELECT (reader → phone):** `00 A4 04 00 08 F0 50 59 48 4F 4D 45 01 00`
- **Response (phone → reader):** `<code bytes, UTF-8> 90 00`, or `6A 82` if emit is off.
- The phone must be **unlocked** (`requireDeviceUnlock="true"`).

### Reader side (ESP32 + PN532)
```cpp
// after inListPassiveTarget() detects the phone:
uint8_t sel[] = {0x00,0xA4,0x04,0x00,0x08, 0xF0,0x50,0x59,0x48,0x4F,0x4D,0x45,0x01, 0x00};
uint8_t resp[64]; uint8_t rlen = sizeof(resp);
if (nfc.inDataExchange(sel, sizeof(sel), resp, &rlen) && rlen >= 2 &&
    resp[rlen-2] == 0x90 && resp[rlen-1] == 0x00) {
    String code = "";
    for (int i = 0; i < rlen - 2; i++) code += (char)resp[i];
    if (code == MY_SECRET) toggleAlarm();   // arm / disarm
}
```
For real security, don't send a static string: have the reader send a random
nonce and the phone return HMAC(nonce, key). The current build sends the stored
value as-is, which is fine for a bench test.

## Not included (yet)
- Writing tags
- Reading protected MIFARE Classic sectors (needs keys)
- Persisting scan history across app restarts (saved codes *are* persisted)
- Challenge-response / HMAC (the emit path sends the code verbatim for now)
