# Class Reel

Video player for lectures. Kotlin + Jetpack Compose + Media3 (ExoPlayer) + Vosk (offline captions).

## Features
- Plays video from any app/folder (MP4, MKV, WebM, 3GP, TS, MOV, FLV and anything your phone's codecs support). Also appears in "Open with" for video files.
- Skip buttons: -30s -20s -10s / +10s +20s +30s. Double-tap left/right side = 10s.
- Speed: 0.5x to 3x (pitch stays natural).
- CC captions: Hinglish (Hindi speech written in English letters) or English (Indian accent). Runs offline after a one-time ~40 MB download.
- History: remembers the exact second you stopped in each video. Reopen the app days later and tap Resume.

## Build
GitHub: push to `main` -> Actions -> artifact `ClassReel-debug-apk`.
Local (proot ubuntu): `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
