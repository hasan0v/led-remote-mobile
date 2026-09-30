# LED Remote

Native Android (Java, no dependencies) 24-key IR remote for LED strips. It transmits NEC codes through the phone's IR blaster (`ConsumerIrManager`).

- Pixel-style dark UI with the same 24 keys as the original remote (brightness ±, OFF/ON, R/G/B/W, 12 colours, FLASH/STROBE/FADE/SMOOTH)
- Press feedback (glow + haptics), brightness keys repeat while held (NEC repeat frame)
- Settings (top-right): haptics, hold-repeat, code set A/B, **code edit mode** (tap a key to change its hex code), reset
- Azerbaijani + English UI
- Needs a phone with an IR emitter (many Xiaomi/Redmi/Huawei/Samsung-older models)

## Build

`./build.sh` → `dist/led-remote.apk` (needs JDK 17+, `aapt zipalign apksigner dalvik-exchange libandroid-23-java`).

The APK is signed with `keystore/release.keystore` (password `android`, hobby-grade key) so updates install over each other.
