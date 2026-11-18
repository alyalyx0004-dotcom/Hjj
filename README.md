# Premium Glass Launcher — Android Studio source project

A functional **Kotlin + Jetpack Compose Android home-screen replacement** with frosted-style translucent panels. It is an original starter launcher for personal use, not a clone of another app.

## Implemented in this source

- Registers as a real Android `HOME` / `DEFAULT` launcher and also has a normal app icon.
- Discovers launchable apps with Android package-visibility `<queries>`; displays their actual installed icons; launches their explicit activities.
- Swipe UP on the home screen to open the installed-app drawer; swipe DOWN for search. App search filters by name and package ID, with a separate web search action.
- Long-press an app to add/remove it from the home page, or hide it; one-tap launching and a list of recently launched apps (only apps opened from this launcher).
- Frosted-style translucent UI, four accent colors, 3–6-column grid, adjustable panel opacity, app-label toggle, reduced-motion preference storage.
- One system Android AppWidget on the home screen, via the OS widget picker (device-dependent). Replace/remove the active widget in Settings.
- Privacy PIN for launcher hidden-app controls, salted PBKDF2-SHA256 local hash. **Not** device-level application locking. Other launchers/Settings may still open hidden apps.
- Settings export/import to JSON through Android's document picker; PIN, widget binding, and recent history are excluded.
- Settings shortcut to select the default launcher; Android wallpaper chooser shortcut.
- No ads, accounts, network permissions, analytics or background services.

## Build a debug APK (Android Studio)

1. Install **Android Studio** (recent version with JDK 17+ support) and its Android SDK Platform **35** and corresponding build tools.
2. Unzip this folder, then choose **File > Open** and select the `PremiumGlassLauncher` root folder (with `settings.gradle.kts`).
3. Allow Android Studio to import and sync Gradle; use an internet connection for the first download of Gradle and the AndroidX/Kotlin dependencies.
4. The project targets Android 8.0+ (`minSdk 26`), compiles against SDK 35 and targets Android 15 (`targetSdk 35`).
5. Choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. Find the generated debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
7. Transfer it to the phone and permit app installation from the file manager if prompted. Open the app, then choose it under **Settings > Apps > Default apps > Home app** (wording varies by phone).
8. Return to the original home launcher using the same Android default-app setting at any time.

### Command line, after Gradle and Android SDK are installed

```bash
gradle :app:assembleDebug
```

A Gradle wrapper JAR/script is not included in this archive because the Android build toolchain is not available in the creation environment. Android Studio can import the Gradle project, and a locally installed Gradle 8.11.1 can generate a wrapper using `gradle wrapper --gradle-version 8.11.1`.

## Important limitations

This is a **working first-generation launcher project**, not a fully production-tested commercial Ultimate launcher. The following requested premium features are **not yet implemented**: draggable app placement, editable app folders, third-party icon-pack parsing, OS-level notification badges, contact search, weather APIs, multi-widget layout management, animated live wallpaper rendering, and automated app-category classification. A stored “Reduce motion” preference is reserved for future animation support. The glass treatment uses efficient translucent backgrounds and gradients, **not expensive real-time background blur**.

Widget-provider support differs by Android OEM. The widget picker path is provided but has **not been verified on a physical device**. AppWidget restore and multiple widgets are not supported. Android's home/gesture animations are system-controlled on some devices, and a third-party launcher cannot guarantee identical OEM transitions.

## Privacy & security

- PINs are not backed up. If you forget the PIN, clearing the launcher's app data resets all local launcher settings.
- Hiding apps only removes them from this launcher's drawer and home UI; it does not disable/uninstall/secure them on Android.
- Apps can appear or disappear as packages are installed or removed. Tap the refresh icon to refresh the app list.
- JSON backup files are not encrypted: avoid sharing backups if your hidden-app list is sensitive.
- No dangerous system permissions or accessibility permissions are requested.

## Project layout

- `app/src/main/java/com/premiumglass/launcher/MainActivity.kt`: launcher navigation, compose home/drawer, app/widget actions.
- `LauncherStore.kt`: preferences, app discovery, favorites, recents, PIN verification and backup format.
- `SettingsScreen.kt`: appearance, privacy, widget and backup controls.
- `app/src/main/AndroidManifest.xml`: home intent filters and package-visibility query.

## Build status

**Source authored and packaged; compilation, Gradle sync, APK install, and device testing have not run in this environment.** If Android Studio reports a dependency issue, use its recommended compatible Gradle/Android Gradle Plugin pair and ensure SDK 35 is installed.
