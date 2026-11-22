# Progress1Percent — instructions for coding agents

Android personal-growth app: everything revolves around a **single progress percentage on
the home screen** that aggregates the state of every module. All data is stored **offline**
in the app's private storage (SQLite) plus files for photos.

## Stack

* Kotlin 2.x, Jetpack Compose (BOM 2025.09.00) + Material 3
* `compileSdk` / `targetSdk` 36, `minSdk` 26, namespace and applicationId
  `com.progressone.nativeapp` (debug builds use the `.debug` suffix)
* `com.microsoft.onnxruntime:onnxruntime-android` for the two on-device AI models
* ML Kit (barcode scanning, subject segmentation) — offline, no Google AI APIs
* No XML layouts: Compose only

## Commands

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # release APK (unsigned)
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew installDebug                # install on a device/emulator
```

* JDK 21 is required (`JAVA_HOME`).
* `local.properties` holds `sdk.dir` and must never be committed.

## The two AI models (both on-device)

1. `foodvision/Food101Classifier.kt` — Swin Transformer Food-101 (ONNX, 336 MB asset).
   Input `pixel_values [1,3,224,224]`, output `logits [1,101]`.
2. `foodvision/MacroModel.kt` — macro estimator (ONNX, 7 MB asset).
   Input `features [1,2149]`, outputs `nutrition_per100g [1,4]` and `portion_grams [1,1]`.

Never introduce a cloud AI dependency (no Gemini, no Firebase AI, no API keys): the app
must stay fully functional offline. If a model cannot be loaded, fall back to the local
reference table (`foodvision/FoodDatabase.kt`).

The feature hashing in `MacroModel.kt` must stay byte-identical to the Python training
pipeline; `MacroModelFeatureTest` enforces that.

## Structure

* `MainActivity.kt` — app shell, navigation, module visibility
* `data/` — `LocalStore` (SQLite), domain models, pure helpers
* `foodvision/` — the AI models and the nutrition reference table
* `media/`, `reminders/`, `weather/` — platform integrations
* `ui/` — screens and the design system (`Design.kt`)

## Conventions

* UI strings are English; the app exposes an Italian localisation for the system language.
* Keep the home screen minimal: one hero card, the essentials, a compact module grid.
* New screens must respect the existing design tokens in `ui/Design.kt`.
* Never commit large binaries: the Food-101 model is downloaded with
  `scripts/download_food_model.sh`.
