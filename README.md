# Zalo Watch Bridge

Shows incoming Zalo calls full screen on a Wear OS watch, vibrates until the call is answered or ends,
and lets you answer / answer on speaker / decline / hang up from the watch.

Two apps: **phone** (reads Zalo's call notification) and **wear** (the watch UI). Both are required.

## Install

You need `adb` (Android SDK platform-tools) on your computer.

### 1. Get the APKs

Download `ZaloBridge-phone.apk` and `ZaloCalls-watch.apk` from Releases, or build them:

```bash
# JDK 17–21 and the Android SDK (platform 35) required
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug
# phone/build/outputs/apk/debug/phone-debug.apk
# wear/build/outputs/apk/debug/wear-debug.apk
```

> Both APKs must be signed with the same key, or the phone and watch apps can't talk to each other.
> Build both on the same machine (or use the same keystore).

### 2. Phone

On the phone: **Settings → About phone → Software information → tap Build number 7 times**,
then **Developer options → USB debugging** on. Plug it in and allow the prompt.

```bash
adb -d install -r -g ZaloBridge-phone.apk
adb -d shell cmd notification allow_listener com.min.zalowear/com.min.zalowear.ZaloCallListener
adb -d shell appops set com.min.zalowear SYSTEM_ALERT_WINDOW allow
adb -d shell pm grant com.min.zalowear android.permission.ANSWER_PHONE_CALLS
adb -d shell dumpsys deviceidle whitelist +com.min.zalowear
adb -d shell am start -n com.min.zalowear/.MainActivity
```

(Or install the APK normally and use the buttons in the app to grant the same permissions.)

### 3. Watch

On the watch: **Settings → About watch → Software information → tap Software version 5 times**,
then **Developer options → ADB debugging** and **Wireless debugging** on.
The watch must be on the same Wi-Fi as your computer.

In **Wireless debugging → Pair new device**, note the pairing code and IP:port, then:

```bash
adb pair <PAIR_IP:PORT> <CODE>
WATCH=<IP:PORT>            # shown on the main Wireless debugging screen (different port)
adb connect $WATCH

adb -s $WATCH install -r -g ZaloCalls-watch.apk
adb -s $WATCH shell appops set com.min.zalowear SYSTEM_ALERT_WINDOW allow
adb -s $WATCH shell appops set com.min.zalowear USE_FULL_SCREEN_INTENT allow
adb -s $WATCH shell pm grant com.min.zalowear android.permission.POST_NOTIFICATIONS
adb -s $WATCH shell dumpsys deviceidle whitelist +com.min.zalowear
```

### 4. Test

Open **Zalo Watch Bridge** on the phone. All lines should be ✅, including the connected watch.
Tap **Send test call to watch**.

On Samsung phones, also add Zalo Watch Bridge to
**Settings → Battery → Background usage limits → Never sleeping apps**.
