# Building Progress 1 Percent

## Requirements

| Tool | Version |
|------|---------|
| JDK | 21 |
| Android SDK | platform 36, build-tools 36 |
| Gradle | wrapper included (`./gradlew`) |
| Android Studio | optional, Ladybug or newer |

`local.properties` must point to your SDK and is **not** committed:

```properties
sdk.dir=/home/<you>/Android/Sdk
```

## Models

The Food-101 vision model is 336 MB and therefore excluded from git. Download it once:

```bash
./scripts/download_food_model.sh
# -> app/src/main/assets/swin_food101_fp32.onnx
```

The macro estimator (`app/src/main/assets/macro_estimator.onnx`, ~7 MB) is committed.

## Build

```bash
# debug APK (package com.progressone.nativeapp.debug)
./gradlew :app:assembleDebug

# release APK (package com.progressone.nativeapp, minified with R8)
./gradlew :app:assembleRelease

# unit tests
./gradlew :app:testDebugUnitTest
```

Outputs:

* `app/build/outputs/apk/debug/app-debug.apk`
* `app/build/outputs/apk/release/app-release-unsigned.apk`

## Signing a release

The release build is not signed by Gradle. Sign it yourself with `apksigner`:

```bash
ZIPALIGN=~/Android/Sdk/build-tools/36.0.0/zipalign
APKSIGNER=~/Android/Sdk/build-tools/36.0.0/apksigner

$ZIPALIGN -p -f 4 app-release-unsigned.apk Progress1Percent_v1.3.0.apk
$APKSIGNER sign --ks <your-keystore>.jks --ks-pass env:KS_PASS \
                --key-pass env:KEY_PASS --out Progress1Percent_v1.3.0.apk \
                Progress1Percent_v1.3.0.apk
$APKSIGNER verify --print-certs Progress1Percent_v1.3.0.apk
```

Keep the same key that signed the installed build, otherwise Android refuses the update.

## Release checklist

1. `versionCode` / `versionName` in `app/build.gradle.kts`;
2. `./gradlew :app:testDebugUnitTest` passes;
3. `./gradlew :app:assembleRelease`;
4. sign and verify the APK;
5. tag the release and upload the APK (and, optionally, the vision model) as assets.

## Project layout

```
app/
├── build.gradle.kts          # app module configuration
├── proguard-rules.pro        # R8 rules (Compose, ML Kit, ONNX Runtime)
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── assets/           # ONNX models + Food-101 labels
    │   ├── java/com/progressone/nativeapp/
    │   └── res/
    ├── test/                 # JVM unit tests
    └── androidTest/          # instrumented tests (optional)
scripts/
└── download_food_model.sh    # fetches the 336 MB vision model
docs/
├── AI_MODELS.md
├── ARCHITECTURE.md
└── BUILD.md
```
