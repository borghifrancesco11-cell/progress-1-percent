# Architecture

Progress 1 Percent is a single-module Kotlin application built with Jetpack Compose.
Everything is stored locally in the app's private storage (SQLite for structured records,
files for photos).

```
MainActivity
 ├── AppShell (navigation, bottom bar, module visibility)
 ├── TodayScreen          home: progress score, essentials, module grid
 ├── FoodScreen           photo AI, macro AI, targets, meals
 ├── GymScreen / SleepScreen / SchoolScreen / FinanceScreen
 ├── TrackScreen / WardrobeScreen / SocialScreen / TasksScreen
 ├── ChallengesScreen / DiaryScreen / MixMatchScreen
 └── SettingsScreen       modules, profile, BMI, language, backup
```

## Layers

| Layer | Package | Responsibility |
|-------|---------|----------------|
| Data | `data/` | `LocalStore` (SQLite persistence), domain models, pure calculation helpers (`NutritionMath`, `ProgressScore`, `Metrics`, `OutfitLogic`, `SleepUtils`) |
| AI | `foodvision/` | `Food101Classifier` (vision model), `MacroModel` (macro model), `FoodDatabase` (reference table and scoring) |
| Platform | `media/`, `reminders/`, `weather/` | image decoding and editing, reminder scheduling, Open-Meteo weather lookup |
| UI | `ui/` | Compose screens, design system (`Design.kt`), localisation helpers (`Localization.kt`) |

## Data flow

```
UI  ──►  LocalStore (SQLite)  ──►  AppSnapshot  ──►  UI
 │                                        ▲
 └─► repository / model helpers ─────────┘
```

`AppSnapshot` is an immutable snapshot of every record; `LocalStore.revision` is a counter
that increments on each write, so Compose recomposes automatically after a change.

## AI flow (Food)

```
camera / gallery
   └─► ImageProcessor.decode  (EXIF rotation, bounded size)
        └─► Food101Classifier.classify  (Swin Food-101, on-device)
             └─► MacroModel.estimate  (macro estimator, on-device)
                  └─► NutritionMath → FoodDatabase.calculateIntelligentScore
                       └─► saved meal with VISION AI / MACRO AI badge
```

## Design system

`ui/Design.kt` centralises the palette, the frosted card, the progress ring, the small
stat block and the bottom sheet frame. The default theme is an OLED dark theme with mint
and lavender accents; a light theme is available from the settings.

## Tests

* `app/src/test/java/com/progressone/nativeapp/` — JVM unit tests for the calculation
  helpers and for the parity between the Kotlin feature hashing and the Python training
  pipeline.
