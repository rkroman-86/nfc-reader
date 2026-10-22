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

## Not included (yet)
- Writing tags
- Reading protected MIFARE Classic sectors (needs keys)
- Persisting history across app restarts
