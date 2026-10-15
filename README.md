# Overlay Prompter

A see-through teleprompter that floats on top of your normal Android camera app,
so you can read your script while recording yourself on the selfie camera.

## Get the APK

**Option A: GitHub, no installs needed**
1. Make a new GitHub repository and upload this whole folder to it.
2. Open the repo's **Actions** tab. The "Build APK" job runs on its own (takes about 3–5 minutes).
3. Open the finished run and download **OverlayPrompter-apk** at the bottom. Unzip it to get `app-debug.apk`.

**Option B: Android Studio**
Open this folder in Android Studio, then pick **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

## Install on the phone
Copy `app-debug.apk` to the phone and tap it. Android will ask you to allow installs
from that source (your file manager or browser). Say yes.

## Use it
1. Open **Overlay Prompter**, paste your script, and set speed, text size and background darkness.
2. Tap **Show prompter + open camera**. The first time, Android opens the
   "Display over other apps" screen. Switch it on and go back.
3. Switch the camera to the front lens and video mode, then hit record.
4. Tap **▶** on the prompter. It counts down 3‑2‑1, then scrolls.

### Overlay controls
| Button | What it does |
|---|---|
| ⠿ | Drag to move the window |
| ▶ / ❚❚ | Play (with 3‑2‑1) / pause. Tapping the text also does this |
| ⟲ | Back to the top |
| 🐢 / 🐇 | Slower / faster |
| A− / A+ | Smaller / bigger text |
| ◢ (corner) | Drag to resize |
| ✕ | Close the prompter (or use "Close prompter" in the notification) |

While paused you can swipe the text up and down by hand.

### Tips
- Keep the window near the top of the screen, close to the selfie lens, so your eyes look
  almost at the camera.
- Keep it clear of the camera's record button. Touches outside the prompter go to the camera app.
- The prompter does **not** show up in your recorded video. It only appears on screen.
  (It *would* show in a screen recording, but not in a camera recording.)
