# SparkStock Scanner (Android)

The IT manager's phone app: lend, receive and check SparkStock devices by scanning their QR labels with the phone's
camera. It talks to SparkStock over the office network by IP, without HTTPS (the browser can't use the camera without
HTTPS, an installed app can).

## What it does

- **Lend**: scan one or more devices (a headset, its controllers, the charger…), choose who takes them (the people
  lent to last come first, with a search over every SparkyTC account) and the return date (7 days by default). They
  are lent together: if one isn't available, none is, and the list says why.
- **Return**: scan a device someone brings back, choose **Fine** or **Needs repair** (it goes to *In repair*), add a
  note and a photo, and confirm; then the next one. The photo shows in the device's history on the website.
- **Inventory check**: choose a location (a cabinet, a room), scan everything that's there and see what's where it
  should be, what's missing, what SparkStock has somewhere else (another location, on loan, lost) and unknown labels.
  **Move here** fixes the location of the ones SparkStock had elsewhere (devices on loan are only reported).
- Labels whose QR is damaged can be typed (e.g. `VR-0001`). The flashlight helps in dark cabinets.
- English or Spanish, following the phone's language (Android 13+ can set it for the app alone in its settings).
- Signs in with a SparkyTC **admin** account; the session lasts as long as SparkyTC's (7 days).

## Installing

1. Copy `SparkStockScanner.apk` to the phone (or download it from a link) and open it.
2. Android asks to allow installing apps from that source (the browser or the file manager): allow it once.
3. Open **SparkStock Scanner**, enter SparkStock's address as in the browser, e.g. `192.168.1.20:3100`, and sign in.

The phone must be on the same network as the server. Android 8 (API 26) or later, ARM phones.

## Building

Needs JDK 17+ and the Android SDK (platform 35, build tools 35), e.g. with Android Studio.

```bash
./gradlew testDebugUnitTest   # unit and screen tests (Robolectric), no phone needed
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

A debug build is signed with the key of the machine that built it: an APK built on another machine can't update it
(uninstall first). For updates from anywhere, create a release key once, keep it safe, and build with it:

```bash
keytool -genkeypair -v -keystore sparkstock.jks -alias sparkstock -keyalg RSA -keysize 2048 -validity 10000
```

Then a `keystore.properties` next to this README (never committed) with `storeFile=sparkstock.jks`,
`storePassword`, `keyAlias=sparkstock` and `keyPassword`, and `./gradlew assembleRelease`.

## How it works

- Kotlin and Jetpack Compose; CameraX for the camera and ML Kit for the QR codes, with the model inside the app (no
  Google services or internet needed to scan).
- Calls `http://<server>:3100/api/v1/...`: SparkStock's website forwards `/api/v1` to its backend with the app's token
  (`Authorization`) and language (`Accept-Language`), never a cookie. Cleartext HTTP is allowed in
  `res/xml/network_security_config.xml`, like the website.
- `TagParser` reads the tag from the label's address (`http://<server>/t/VR-0001`) or a plain tag.
- The session token, the server and the recent people are kept in the app's private storage.
