# Testing

## Host-side checks

```bash
export JAVA_HOME="/path/to/your/jdk"
bash tests/run.sh
```

The Java test uses `tests/widget-text.txt`, a **synthetic** fixture. It covers nonbreaking spaces, grouped mileage, mi/km units, raw status and relative timestamps, missing/invalid readings and non-data screens. Python tests cover safe registration URL handling, redirect rejection, private file permissions, reusing registrations without creating duplicates, interrupted registration handling and log-analysis classification. They do not contact any live service.

CI runs these tests and builds/signs/verifies the APK with an ephemeral development key. Successful compilation is not proof of device compatibility.

## Device smoke test

Use your own Android phone and a working myAudi widget:

1. Install and configure the bridge; confirm its initial text extraction.
2. Start capture and leave the phone locked through a widget update.
3. Export logs and confirm `deviceLocked=true`, `screenInteractive=false` and `phase=live_callback` on the received event.
4. Verify the original receipt time and raw contact text in your own HA sensors.
5. Test network loss/recovery; the latest pending reading should upload without changing its original receipt time.
6. Check overnight and reboot behaviour on your OEM/Android combination.

A separate **Probe control widget** is included for local diagnostics. Its synthetic readings are never forwarded to Home Assistant. It provides regular sample updates and a refresh click listener; these exercise the Android mechanism, not Audi's servers.

## Emulator instrumentation reference

`tests/RefreshValidation.java` is an emulator-only instrumentation test. It exercises target selection and a real offscreen control-widget refresh PendingIntent while PIN-locked. It is not compiled into the normal APK by `build.sh`.

To run it, create a separate test APK with:

- package `local.audi.widgetprobe.tests`;
- instrumentation class `local.audi.widgetprobe.RefreshValidation`;
- target package `local.audi.widgetprobe`;
- the test class compiled against the app's Java sources/classes and Android API 34;
- the **same signing certificate** as the app.

On a disposable emulator only, grant widget binding and notifications to the target app, set a temporary PIN and an immediate lock timeout, and ensure the emulator is unlocked before running the instrumentation. The test starts capture, creates a control widget, arms the 30-second test and turns the screen off. Do not run it on your everyday phone: it changes the bridge's selected widget and diagnostic preferences. A representative invocation is:

```bash
adb -s YOUR_EMULATOR_SERIAL shell am instrument -w \
  local.audi.widgetprobe.tests/local.audi.widgetprobe.RefreshValidation
```

The earlier device test demonstrated receipt of a refresh callback while locked, with an older Audi contact timestamp. Do not describe that as proof of a fresh vehicle poll. See [REFRESH.md](REFRESH.md).
