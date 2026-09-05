# USSD Auto – Android App

This app lets you enter your mobile-money PIN **once** inside the app.  
It then dials the USSD code `*847*1*1*0977759483*1000#`, automatically injects your PIN when asked, and automatically replies with `1` on the confirmation step.

**Target:** Android 9.0 (API 28) and higher.

---

## How to build & install

### Option A – Android Studio (recommended)

1. Open **Android Studio**.
2. Choose **File → Open** and select the `UssdAutoApp` folder.
3. Let Gradle sync (it will download dependencies).
4. Connect your phone (USB debugging enabled) or start an emulator (Android 9+).
5. Click the green **Run** button.

### Option B – Command line

```bash
cd UssdAutoApp
./gradlew assembleDebug
```

The APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

Install it with:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## First-time setup on the phone

1. Open the app.
2. Tap **“Enable Accessibility Service”**.
3. In the system Settings screen, find **“USSD Auto”** and turn it **ON**.
4. Go back to the app.
5. Grant **Phone** permission when asked.
6. Type your PIN and tap **Send Money**.

---

## Important security notes

- The PIN is kept **only in memory** and is cleared immediately after it is injected.
- The Accessibility Service is powerful. Only enable it when you need it, or keep it enabled only if you trust the app.
- This project is intended for **personal use**. Publishing an app that automates financial USSD flows on the Play Store is usually rejected.

---

## Customising the USSD code

Edit the constant in `MainActivity.kt`:

```kotlin
private val USSD_CODE = "*847*1*1*0977759483*1000#"
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| PIN is not injected | The prompt text is different on your network. Open `UssdAccessibilityService.kt` and add the exact words that appear in the `isPinPrompt` check. |
| Confirmation “1” not sent | Same – add the exact confirmation text you see. |
| Accessibility Service disappears | Some manufacturers (Xiaomi, Huawei, Tecno…) kill background services. Add the app to battery whitelist / auto-start list. |
| “Restricted settings” on Android 13+ | Go to App info → ⋮ menu → **Allow restricted settings**, then enable Accessibility again. |

---

## Project structure

```
UssdAutoApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/ussdauto/
│   │   │   ├── MainActivity.kt
│   │   │   └── UssdAccessibilityService.kt
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── xml/ussd_accessibility_config.xml
│   │   │   └── values/...
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

Enjoy!
