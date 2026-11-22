# Progress 1 Percent

[![Version](https://img.shields.io/badge/version-1.3.0-blue)](https://github.com/borghifrancesco11-cell/progress-1-percent/releases)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-0098D1?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=android)
![Material 3](https://img.shields.io/badge/Design-Material%203-6200EE?logo=material-design)
![minSdk](https://img.shields.io/badge/minSdk-26-green)
![Offline](https://img.shields.io/badge/data-off%20first-2ea44f)

**Progress 1 Percent** is an offline-first Android app for personal growth: nutrition, gym,
sleep, money, study, habits, wardrobe and social life, all aggregated into a single daily
progress score.

This is version **1.3.0**, built on the newer native app (`com.progressone.nativeapp`) and
extended with the features that only existed in the older GitHub release.

---

## 🤖 Two AI models — the short answer

**Yes, the app ships two different AI models and both run on the phone. No cloud, no API key.**

| # | Model | Runs on | Purpose | Size |
|---|-------|---------|---------|------|
| 1 | **Swin Transformer Food-101** (ONNX Runtime) | Device, offline | Recognises the **dish in the photo** across 101 Food-101 categories | ~336 MB |
| 2 | **Macro estimator** — 3-hidden-layer neural network (ONNX Runtime) | Device, offline | Estimates **calories and macros** (protein, carbs, fat) and the portion size | ~7 MB |

The Food screen shows a "Two on-device AI models" card that states which models are active,
and every logged meal carries a badge (`VISION AI` / `MACRO AI`) describing how its numbers
were produced.

### Model 1 — food recognition (photo → dish)

* `onnx-community/swin-finetuned-food101-ONNX` (FP32), shipped inside the APK as an
  uncompressed asset.
* Preprocessing matches the official model card exactly: RGB, resize to 224×224 (bilinear),
  scale by 1/255, normalise with the ImageNet mean/std, NCHW float32.
* The class list in `app/src/main/assets/food101_labels.txt` matches the model `config.json`
  label order one-to-one.

### Model 2 — macro estimation (dish → calories & macros)

* A feed-forward network (2048 hashed text features + a one-hot Food-101 class → 512/256/128
  hidden units) exported to ONNX and bundled as `app/src/main/assets/macro_estimator.onnx`.
* It was trained in this repository's ML pipeline on **29 700+ labelled food entries**:
  Open Food Facts products, a standardised nutrition dataset, and the curated USDA-style
  reference values of the 101 Food-101 dishes.
* Measured quality: **~54 kcal/100 g** mean absolute error on held-out foods (a constant
  "average food" predictor is off by ~149 kcal) and **~2.5 kcal/100 g** on the 101 Food-101
  dishes.
* The Kotlin featurisation is covered by unit tests that compare the hashing output with the
  values produced by the Python training script, so the app feeds the network exactly what it
  was trained on.

> The older APK only had **one** model: the vision network. Macro values came from a static
> lookup table. The second model was missing entirely. This release adds it — integrated,
> offline and free of any Google API.

---

## ✨ Features

### Nutrition
* 📷 Photo recognition with Swin Food-101 (on-device, offline)
* 🧠 Macro estimation with the bundled neural network (on-device, offline)
* 🎯 Editable daily calorie and macro targets
* 🏷️ `VISION AI` / `MACRO AI` badges on every logged meal
* 🥗 Local nutrition reference table for manually entered foods
* 📊 Barcode scanning for packaged products (ML Kit, offline)

### Gym
* Workouts, sets and repetitions, training history
* Weight, height, **BMI** and body composition
* Strength goals and progression

### Sleep & recovery
* Hours slept and sleep quality
* Recovery score feeding the daily progress score

### School
* Subjects, grades and weighted average
* **Hypothetical grade simulator** ("what if I get an X?")

### Money
* Income and expenses by category
* Monthly budget and savings

### Habits & tracker
* Daily / weekly / monthly / **yearly** tracker frequency
* Streaks and reminders

### Wardrobe
* Digital wardrobe, mix & match outfits
* **Weather-aware outfit generation** (temperature and conditions)

### Other
* Aggregated progress score (0–100)
* OLED dark theme, English UI
* First-run setup and tutorial
* Works **without an account and without the cloud** — everything stays on the device

---

## 🏗️ Architecture

```
app/src/main/java/com/progressone/nativeapp/
├── MainActivity.kt
├── data/                     # local store, domain models, pure logic
│   ├── LocalStore.kt
│   ├── Metrics.kt
│   ├── NutritionMath.kt
│   ├── OutfitLogic.kt
│   ├── ProgressScore.kt
│   └── ...
├── foodvision/
│   ├── Food101Classifier.kt  # model 1: Swin Food-101 ONNX (on-device)
│   ├── MacroModel.kt         # model 2: macro estimator ONNX (on-device)
│   └── FoodDatabase.kt       # reference nutrition table + scoring
├── media/ImageProcessor.kt
├── reminders/ReminderScheduler.kt
├── weather/WeatherService.kt
└── ui/                       # Jetpack Compose screens
    ├── TodayScreen.kt        # minimal home
    ├── FoodScreen.kt
    ├── GymScreen.kt
    ├── SleepScreen.kt
    ├── SchoolScreen.kt
    ├── FinanceScreen.kt
    ├── TrackScreen.kt
    ├── WardrobeScreen.kt
    ├── SettingsScreen.kt
    └── ...
```

---

## 🚀 Build

### Requirements
* JDK **21**
* Android SDK with API 36
* The Food-101 ONNX model downloaded (see below)

```bash
# 1. Download the vision model (~336 MB, not stored in git)
./scripts/download_food_model.sh

# 2. Point the build at your SDK
cp local.properties.example local.properties
# then set sdk.dir=/path/to/Android/Sdk

# 3. Debug build
./gradlew :app:assembleDebug

# 4. Release build (sign it with your own keystore before publishing)
./gradlew :app:assembleRelease
```

The debug APK uses the package `com.progressone.nativeapp.debug` and can be installed
side by side with the release build `com.progressone.nativeapp`.

### Model assets

| File | Size | Source |
|------|------|--------|
| `app/src/main/assets/swin_food101_fp32.onnx` | ~336 MB | `https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX/resolve/main/onnx/model.onnx` |
| `app/src/main/assets/macro_estimator.onnx` | ~7 MB | trained in this repository (see `docs/AI_MODELS.md`) |
| `app/src/main/assets/food101_labels.txt` | 1 KB | the 101 Food-101 class names |

The 336 MB file is excluded from git; `scripts/download_food_model.sh` fetches it. Both
models are also attached to the GitHub release.

---

## 📄 Documentation

* [docs/AI_MODELS.md](docs/AI_MODELS.md) — the two models, the training pipeline, the metrics
* [docs/BUILD.md](docs/BUILD.md) — build, signing and release instructions
* [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — module map and data flow

---

## 📱 Modules

| Module | Status | Notes |
|--------|--------|-------|
| Food | ✅ | Vision + macro AI + targets |
| Gym | ✅ | BMI and history |
| Sleep | ✅ | Recovery score |
| School | ✅ | Grade simulator |
| Money | ✅ | Budget |
| Tracker | ✅ | Day/week/month/year |
| Wardrobe | ✅ | Weather outfits |
| Social | ✅ | Digital wellbeing detox |
| Tasks | ✅ | — |

Modules can be switched on and off in Settings; disabling a module hides it from the app
and the home screen without deleting any data or photo.

---

## 🔒 Privacy

Everything is stored locally in the app's private storage. The only optional network calls
are the weather lookup (Open-Meteo) and the Open Food Facts barcode lookup. The AI models
never leave the device.
