# Progress 1 Percent

[![Latest release](https://img.shields.io/github/v/release/borghifrancesco11-cell/progress-1-percent?label=release)](https://github.com/borghifrancesco11-cell/progress-1-percent/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-0098D1?logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=android)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Design-Material%203-6200EE?logo=material-design)](https://m3.material.io/)
[![minSdk](https://img.shields.io/badge/minSdk-26-green)](https://developer.android.com/)
[![Offline first](https://img.shields.io/badge/data-offline%20first-2ea44f)](#privacy--data)

**Progress 1 Percent** is an *offline-first* Android personal growth app.
It brings together nutrition, training, sleep, finance, study, habits, wardrobe, and social detox into a single daily progress score.

No account is required, no cloud AI service is used, and all personal data stays in private on-device storage.

---

## Screenshots

| Home | Calendar & Streak | Food AI |
| :---: | :---: | :---: |
| <img src="docs/screenshots/screen_home.png" alt="Home" width="240" /> | <img src="docs/screenshots/screen_calendar.png" alt="Calendar & streak" width="240" /> | <img src="docs/screenshots/screen_food.png" alt="Food AI & macros" width="240" /> |

| Gym & Recovery | Sleep | Tracker |
| :---: | :---: | :---: |
| <img src="docs/screenshots/screen_gym.png" alt="Gym & recovery" width="240" /> | <img src="docs/screenshots/screen_sleep.png" alt="Sleep" width="240" /> | <img src="docs/screenshots/screen_tracker.png" alt="Daily tracker" width="240" /> |

| Tasks | Challenges | Backup ZIP |
| :---: | :---: | :---: |
| <img src="docs/screenshots/screen_tasks.png" alt="Tasks & reminders" width="240" /> | <img src="docs/screenshots/screen_challenges.png" alt="Personal challenges" width="240" /> | <img src="docs/screenshots/screen_backup.png" alt="Backup & restore" width="240" /> |

---

## Key Features

- **Minimal Home**: Daily score counter (`TODAY'S SCORE`, `GOALS`, `TASKS`, `STREAK`) paired with a 4-menu horizontal slide bar (`Body`, `Recovery`, `Mind`, `Life`), each containing 2 logically grouped modules.
- **Food & Nutrition**: On-device dish recognition (**Swin Transformer Food-101**) + local neural macro estimation (`macro_estimator.onnx`), barcode lookup, portions, and daily targets.
- **Gym & Training**: Personalized workout templates recommended from your weight, height, and BMI, with sets, reps, rest timers, and personal records.
- **Sleep & Recovery**: Sleep duration, quality, recovery score, and weekly trends.
- **Tasks & School**: Daily to-dos with local Android reminders, subjects, weighted grade average, and grade simulator.
- **Finance, Challenges, Wardrobe & Detox**: Offline expense/budget tracking, personal challenges, weather-aware outfit planner, and local app-usage detox.
- **Backup & Restore**: Streamable ZIP backup with manifest checksums and atomic import.

---

## On-Device AI & Coach

All intelligence runs locally on your phone with zero API keys or cloud uploads:

| # | Component | Runtime | Purpose |
|---|-----------|---------|---------|
| 1 | **Swin Transformer Food-101** | ONNX Runtime (on-device) | Classifies dishes across 101 Food-101 categories |
| 2 | **Macro Estimator MLP** | ONNX Runtime (on-device) | Estimates calories, protein, carbs, fat, and portion size |
| 3 | **Local Coach Engine** | Deterministic Kotlin (`ProgressIntelligence`) | Generates contextual on-device guidance in Food, Gym, and Calendar |

---

## Privacy & Data

- Everything is stored locally on the device (`SQLite` + private app files).
- No account, no ads, no cloud AI.
- Open-Meteo weather lookup and Open Food Facts barcode lookup are strictly optional and on-demand.

---

## Build

Requires **JDK 21** and Android SDK (compileSdk 36, minSdk 26):

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon
```
