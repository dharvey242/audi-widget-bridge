# Building and signing

The build uses Android's command-line tools directly, with no Gradle, Maven or third-party Android libraries. Bash, a JDK 17+, SDK platform `android-34`, and build-tools `36.1.0` are required. Python 3.9+ runs the host-side tests/setup tools. Android SDK licences must be accepted through your SDK installation.

Set `JAVA_HOME` and `ANDROID_SDK_ROOT` to **your** paths. Run `bash tests/run.sh`, then `bash build.sh`. Output: `build/audi-widget-bridge.apk`. Temporary compiler output is isolated and removed after each build, preventing stale classes from being packaged.

Optional settings:

| Variable | Default | Purpose |
|---|---|---|
| `BUILD_DIR` | `build/` under the repository | Local build artifacts and default development key |
| `BUILD_TOOLS_VERSION` | `36.1.0` | Installed Android build-tools version |
| `SIGNING_KEYSTORE` | Generated `build/debug.keystore` | Your stable private signing key |
| `SIGNING_ALIAS` | `androiddebugkey` for development | Key alias when supplying a keystore |
| `SIGNING_STORE_PASSWORD` | `android` for development only | Keystore password, passed through the environment |
| `SIGNING_KEY_PASSWORD` | `android` for development only | Key password, passed through the environment |

## Development builds

The first local build generates its own development key. Reusing that key lets later builds update the same installed application. Deleting `build/` also deletes that default key, so back it up privately if you need compatible upgrades. Never commit it.

CI creates a new development key each run. Its APK artifact is a test build, not a stable update channel. Only the APK is uploaded by CI; the keystore is not an artifact.

## Your own stable signing key

Create a private key outside the repository's tracked files, for example:

```bash
mkdir -p .private
"$JAVA_HOME/bin/keytool" -genkeypair \
  -keystore .private/release.keystore -alias release \
  -keyalg RSA -keysize 3072 -validity 10000
```

Answer keytool's password/identity prompts yourself. For signing, set the following in your own terminal or secret store:

```bash
export SIGNING_KEYSTORE="$PWD/.private/release.keystore"
export SIGNING_ALIAS="release"
read -r -s -p "Keystore password: " SIGNING_STORE_PASSWORD; echo
export SIGNING_STORE_PASSWORD
read -r -s -p "Key password: " SIGNING_KEY_PASSWORD; echo
export SIGNING_KEY_PASSWORD
bash build.sh
unset SIGNING_STORE_PASSWORD SIGNING_KEY_PASSWORD
```

Use the same key password as the store password if your keystore format requires it. Passwords are passed to signing tools through environment variables, not literal command-line arguments. Avoid shell tracing (`set -x`) around secrets.

Android requires the same signing certificate and application ID for in-place updates. The application ID is `local.audi.widgetprobe`. A build signed with a different key cannot update another installation, including earlier privately signed prototypes. Before uninstalling an incompatible build, export logs and retain your private connection file; uninstalling removes app data and its hosted widget instance.

## Updating Android versions

The app targets API 34 and supports API 26+. It is intended for sideloading. Before changing target SDK, test foreground-service, widget-host, notification and boot behaviour on the relevant Android versions. Increasing target SDK alone does not establish Google Play policy compliance.
