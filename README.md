# Air Control

Air Control is an Android accessibility application built with Flutter and MediaPipe that allows you to control your phone completely hands-free using hand gestures. 

It tracks your hand in real-time via the front-facing camera, draws a smooth floating cursor over all other apps, and lets you perform clicks, drags, and swipes by simply pinching your fingers together.

## Features

- **Real-Time Hand Tracking:** Powered by Google's MediaPipe Hand Landmarker (LIVE_STREAM mode) for ultra-low latency, 60fps tracking.
- **Dynamic Cursor Overlay:** A globally visible cursor drawn using WindowManager that floats on top of the entire OS.
- **Smart Pinch Detection (Hysteresis):** Pinch your thumb and index finger to click. The app uses hysteresis to prevent "flickering" pinches, allowing you to easily hold and drag.
- **Advanced Gesture Dispatching:** Automatically translates your hand's path into Android GestureDescription strokes
- **Ergonomic Sensitivity:** The tracking maps a small central "active box" from the camera view to the entire bounds of the device screen, meaning you only need to make small wrist movements to reach all edges.
- **Zero-Crash Native Setup:** Custom Android configuration completely disables R8 minification and resource shrinking to ensure MediaPipe's C++ reflection engine runs flawlessly without throwing IllegalStateException or UnsatisfiedLinkError.

## Architecture

This project uses a hybrid architecture:
- **Flutter (Dart):** Handles the simple UI for granting permissions (Camera, Display Over Other Apps, Accessibility).
- **Android Native (Kotlin):** 90% of the core logic lives here.
  - AirMouseService.kt: The AccessibilityService that draws the cursor, calculates sensitivity, applies low-pass smoothing, and dispatches native Android gestures.
  - HandTracker.kt: Interfaces with CameraX and the MediaPipe API to extract hand coordinates.
  - ServiceLifecycleOwner.kt: Custom lifecycle management to safely bind CameraX within a background service without crashing the Android system registry.

## Building and Running

1. Clone this repository.
2. Ensure you have the Flutter SDK installed.
3. Plug in your Android device (Android 8.0+ recommended).
4. Run the following command:

`Bash
flutter build apk
flutter install
`

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
