# QA guide — downloading and installing builds

Where to download a build of **{{APP_NAME}}** and how to install it on a device with ADB. No prior Android
knowledge is assumed; follow it top to bottom.

> **Two kinds of build exist:**
> - **Test builds** — produced on every merge to `{{DEFAULT_BRANCH}}`. Their version ends in `-test`
>   (e.g. `1.4.2-test`). Downloaded from **GitHub Actions → Artifacts**.
> - **Release builds** — cut deliberately. Clean version, no suffix (e.g. `1.4.0`). Downloaded from
>   **Releases**.

---

## 1. Download a test build (GitHub Actions)

1. Open the repository on GitHub and click the **Actions** tab.
2. In the left sidebar pick the workflow **Publish ({{DEFAULT_BRANCH}})**.
3. Pick a run with a **green check** (a red cross means it failed — do not use it) and open it.
4. Scroll to **Artifacts** at the bottom of the summary page.
5. Click **`{{PROJECT_SLUG}}-<version>-test`** — a `.zip` downloads. Unzip it. Inside:
   - `app-release.apk` — **use this one** unless told otherwise;
   - `app-debug.apk` — the debuggable variant, only when asked for it.

> Artifacts are deleted after **90 days**. You must be signed in to GitHub with access to the repository.

## 2. Download a release build (GitHub Releases)

1. On the repository's main page, click **Releases** in the right sidebar.
2. Open the release you want (the newest is marked **Latest**), e.g. `v1.4.0`.
3. Under **Assets**, click **`{{PROJECT_SLUG}}-<version>.apk`** — the APK downloads directly (no zip).

> Releases never expire.

## 3. One-time setup — install ADB

ADB (Android Debug Bridge) installs an APK onto a device. The simplest way to get it is Android Studio
(<https://developer.android.com/studio>): install it and launch it once. ADB then lives in:
- macOS: `~/Library/Android/sdk/platform-tools/adb`
- Windows: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`
- Linux: `~/Android/Sdk/platform-tools/adb`

Add that folder to your `PATH` (macOS/Linux: `export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"`
in `~/.zshrc`; Windows: System Properties → Environment Variables → Path). Check with `adb version`.

## 4. Prepare the device (physical phone)

Skip this for an emulator.
1. Settings → About phone → tap **Build number** seven times ("You are now a developer").
2. Settings → System → Developer options → turn on **USB debugging**.
3. Connect the USB cable and tap **Allow** on the "Allow USB debugging?" prompt (tick "Always allow").
4. `adb devices` must list the phone as `device` (not `unauthorized` / `offline`).

## 5. Install the APK

```bash
cd ~/Downloads
adb install {{PROJECT_SLUG}}-1.4.0.apk          # wait for "Success"
adb install -r {{PROJECT_SLUG}}-1.4.1.apk       # update in place, keeps app data (same build type)
```

**Several devices connected** (`more than one device/emulator`): list serials with `adb devices -l`, then
`adb -s <serial> install <file>`. `-s <serial>` works with every adb command.

**Switching between debug and release builds — uninstall first.** Both use the package
`{{APPLICATION_ID}}`, so only one can be installed at a time, and installing one over the other fails:
```bash
adb uninstall {{APPLICATION_ID}}      # removes the app AND its data (you start logged out)
adb install app-debug.apk
```
Check whether it is installed: `adb shell pm list packages | grep {{APPLICATION_ID}}`.

## 6. Troubleshooting

| Message | Meaning / fix |
|---|---|
| `adb: command not found` | Platform-tools not on `PATH` (section 3), or use the full path |
| `no devices/emulators found` | Not connected, USB debugging off, or the prompt was not allowed (section 4) |
| `unauthorized` | Replug the cable and tap **Allow** |
| `more than one device/emulator` | Use `adb -s <serial> …` |
| `INSTALL_FAILED_ALREADY_EXISTS` | Use `adb install -r`, or uninstall first |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / signatures do not match | Mixed build types or signing keys — `adb uninstall {{APPLICATION_ID}}`, then install |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | Installing an older version — uninstall first, or `adb install -d <file>` |

## Quick reference

```bash
adb devices -l                                   # devices + serials
adb install <file>.apk                           # install (one device)
adb -s <serial> install <file>.apk               # install to a specific device
adb install -r <file>.apk                        # update in place
adb uninstall {{APPLICATION_ID}}                 # before switching build type
```
