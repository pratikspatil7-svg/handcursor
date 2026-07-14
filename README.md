# HandCursor — camera-driven touch cursor for Android

A no-root Android app that tracks your hand via the front camera (MediaPipe
HandLandmarker) and turns your index fingertip into an on-screen cursor,
with a pinch (thumb+index touch) triggering a real tap anywhere on screen —
injected system-wide via an AccessibilityService.

## Get a ready-to-install APK without installing Android Studio

This project includes `.github/workflows/build.yml`, which builds the APK
for you on GitHub's free cloud servers — it also auto-downloads the hand
model, so you don't need to fetch that file by hand either. All you do is
push the code and download the result.

1. **Create a free GitHub account** at https://github.com/join if you don't
   have one.
2. **Create a new repository**: click the "+" top-right → "New repository"
   → name it e.g. `handcursor` → keep it Public or Private, doesn't matter
   → click "Create repository". Leave it empty (don't add a README here).
3. **Upload the code.** Easiest way with no command line:
   - On the new repo's page, click "uploading an existing file"
   - Drag in *everything inside* the extracted `HandCursor` folder
     (the `app` folder, `build.gradle.kts`, `settings.gradle.kts`, the
     `.github` folder, etc. — select them all and drag together)
   - Scroll down, click "Commit changes"
   - Note: GitHub's web upload sometimes hides folders starting with `.`
     like `.github` — if it doesn't appear after upload, use the "git"
     command-line method instead (ask me and I'll walk you through it).
4. **Watch it build**: click the **"Actions"** tab at the top of your repo.
   You should see a workflow run start automatically (triggered by your
   push). Click into it — it takes a few minutes.
5. **Download the APK**: once it finishes (green checkmark), scroll to the
   bottom of that run's page to the "Artifacts" section, and download
   `HandCursor-debug-apk` — it's a zip containing `app-debug.apk`.
6. **Get it onto your phone**: airdrop/email/cloud-drive the apk to your
   Poco F6, or plug the phone into your computer and copy it over via USB
   file transfer.
7. **Install it**: tap the apk file on your phone. HyperOS will warn about
   "unknown sources" — go to the settings it links you to and allow
   installs from that source (e.g. your Files app or browser), then go
   back and tap the apk again to install.

If the Actions build fails (red X instead of green check), click into the
failed step and paste me the error text — first cloud builds often need a
one-line fix (dependency version mismatch, etc.) and I can patch the repo
file directly, you'd just re-push it.

---

## (Alternative) Build locally in Android Studio instead


- `MainActivity` — permission setup screen (overlay, accessibility, camera)
- `OverlayCursorService` — camera capture + hand tracking + floating cursor
- `GestureAccessibilityService` — injects the actual tap via dispatchGesture()
- `CursorView` — the little floating dot you see on screen

## One-time setup before you build

1. **Open in Android Studio** (Koala/2024.1 or newer recommended).
   File → Open → select the `HandCursor` folder.

2. **Download the hand landmark model.**
   Get `hand_landmarker.task` from Google's MediaPipe model page:
   https://ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker#models
   (use the "float16" or "full" model — either works to start)
   Place it at:
   `app/src/main/assets/hand_landmarker.task`

3. **Check the MediaPipe Tasks Vision version** in `app/build.gradle.kts`.
   I set `0.10.14` from memory — check
   https://mvnrepository.com/artifact/com.google.mediapipe/tasks-vision
   for the current latest and bump it if needed.

4. **Sync Gradle**, then **Run** on your Poco F6 over USB (enable Developer
   Options → USB debugging first).

## First-run flow on the phone
1. Open the app, tap through the three buttons in order:
   - Allow "display over other apps"
   - Enable the HandCursor accessibility service (this is what lets it tap
     for you — Android will show a warning dialog, that's expected for any
     accessibility service)
   - Grant camera permission, then "Start hand cursor"
2. A small blue dot should appear, following your index finger via the
   front camera. Pinch thumb+index together to tap wherever the dot is.
3. Stop from the notification or the app's Stop button.

## Known rough edges / next steps
- **Pinch threshold** (`pinchThreshold` in OverlayCursorService) is a
  normalized-distance guess — you'll likely need to tune it to your hand
  size and camera framing. Log the actual `pinchDist` values while testing
  to find a good cutoff.
- **Only single-hand, tap-only** right now, per your requested scope.
  `performDrag()` is already stubbed in GestureAccessibilityService for
  when you want to add drag/scroll gestures later (e.g. map a "fist" hand
  shape to drag instead of tap).
- **Battery/perf**: running the front camera + a neural net continuously
  will drain the battery faster than normal use — consider adding an
  auto-timeout or a "pause tracking" gesture later.
- **YUV→Bitmap conversion** in `toBitmap()` goes through a JPEG
  re-encode for simplicity, which is a bit wasteful CPU-wise. Fine to
  start; if frame rate feels laggy, that's the first place to optimize
  (e.g. use MediaPipe's ImageProxy extension utilities directly instead).

## Why this approach (vs. Poco's built-in gesture scrolling)
Xiaomi/Poco's "Motion Control" only recognizes a small fixed set of swipes
mapped to system actions (scroll, screenshot, etc.) — it doesn't expose a
live cursor or arbitrary tap targeting. This app instead does real-time
landmark tracking + generic touch injection, so it can in principle drive
any point on screen, not just fire preset actions.
