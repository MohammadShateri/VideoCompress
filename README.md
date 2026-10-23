# Video Squeeze (Android)

An offline-first Android video transcoder starter using Kotlin, Jetpack Compose, and AndroidX Media3 Transformer.

## Current functionality

- Pick a local video using Android's Storage Access Framework.
- Read basic video metadata (size, duration, dimensions, source bitrate, MIME type).
- Choose a compression preset and H.264 / H.265 output codec.
- Request a lower output video bitrate through Media3's encoder factory.
- Show an estimated output size, progress, cancel action, and actual saved file size/reduction.
- Save MP4 output to `Movies/VideoSqueeze` on Android 10+ and share the result.
- No network permission or server upload is included.

## Build a debug APK

Requirements: Android Studio (current stable), Android SDK Platform 36, JDK 17, and Gradle downloaded by Android Studio.

1. Open this folder (`VideoSqueeze`) in Android Studio.
2. Allow Gradle to sync and download dependencies.
3. If Android Studio requests a Gradle wrapper or SDK components, accept its recommended compatible versions.
4. Run the `app` configuration on a physical Android device or emulator.
5. Build > Build Bundle(s) / APK(s) > Build APK(s).

The generated debug APK will normally be at `app/build/outputs/apk/debug/app-debug.apk`.

## Important limitations of this initial implementation

- This is a first functional implementation, not yet a HandBrake-equivalent feature set.
- Bitrate estimates are heuristics. Device encoders can ignore or clamp requested bitrates, and actual size can differ from the estimate.
- H.265 availability depends on the device. If an encoder is missing or fails, select H.264; automatic codec fallback is not yet implemented.
- The app uses device MediaCodec through Media3, so supported input formats and output performance vary by Android version and hardware.
- Output is MP4. Resolution controls, batch queue, background WorkManager processing, thumbnails, detailed audio settings, and an FFmpeg backend are planned follow-up work.
- On Android versions below 10, output is stored in the app-specific Movies folder; Android's file manager or the share sheet can be used to export it.

## Testing

Unit tests cover bitrate preset ordering, size estimation, reduction percentage, and byte formatting. Run them from Android Studio or with `./gradlew test` once a Gradle wrapper is available. A real codec/export test requires a device or emulator and a sample video; this execution environment does not currently have Android SDK/Gradle/ADB installed, so an APK build or on-device test cannot honestly be claimed here.
