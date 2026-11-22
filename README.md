# Progress 1 Percent

[![Version](https://img.shields.io/badge/version-1.2.0-blue)](https://github.com/borghifrancesco11-cell/progress-1-percent)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-0098D1?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=android)
![Material 3](https://img.shields.io/badge/Design-Material%203-6200EE?logo=material-design)
![minSdk](https://img.shields.io/badge/minSdk-26-green)

App Android **offline-first** per il miglioramento personale: alimentazione, palestra,
sonno, finanze, studio, abitudini, guardaroba e social, tutto aggregato in un unico
punteggio di progresso giornaliero.

Questa è la versione **1.2.0**, nata dall'APK nativo più recente (`com.progressone.nativeapp`)
con l'aggiunta delle funzioni che esistevano solo nella vecchia versione pubblicata su GitHub.

---

## 🤖 I due modelli AI (risposta breve: sì, ma con una precisazione)

L'app ha **due percorsi AI distinti** nel modulo Alimentazione:

| # | Modello | Dove gira | Cosa fa | Quando è attivo |
|---|---------|-----------|---------|-----------------|
| 1 | **Swin Transformer Food-101** (ONNX Runtime) | Sul telefono, offline | Riconosce il **cibo dalla foto** tra 101 categorie | Sempre (se il modello ONNX è presente negli assets) |
| 2 | **Google Gemini 1.5 Flash** | Cloud | Stima **calorie e macro** (proteine, carboidrati, grassi, grammi) del piatto riconosciuto | Solo se è configurata `GEMINI_API_KEY` |

Se la chiave Gemini non è configurata (o la rete non è disponibile), il secondo percorso
**non è un modello AI**: usa il database nutrizionale offline (`FoodDatabase`) con valori
deterministici. La card "Modelli AI" nella schermata Alimentazione mostra in tempo reale
quale dei due percorsi è realmente attivo.

> ⚠️ Nell'APK originale che avevi installato c'era **solo** il modello 1 (vision).
> I macro venivano presi dal database locale. Il modello 2 esisteva solo nella vecchia
> versione GitHub: in questa 1.2.0 è stato riportato e integrato.

---

## ✨ Funzionalità

### Alimentazione
- 📷 **Riconoscimento cibo da foto** con Swin Food-101 (on-device, offline)
- 🧠 **Stima macro con Gemini 1.5 Flash** (opzionale, cloud) con fallback offline
- 🎯 Obiettivi giornalieri di calorie e macro modificabili
- 🏷️ Badge che indicano la fonte di ogni stima (AI cloud / database offline)
- 🥗 Database nutrizionale locale per pasti inseriti a mano
- 📊 Barcode scanning per prodotti confezionati (ML Kit, offline)

### Palestra
- Schede, serie/ripetizioni, storico allenamenti
- Peso, altezza, **BMI** e composizione corporea
- Obiettivi di forza e progressione

### Sonno e recupero
- Registrazione ore dormite e qualità
- Punteggio di recupero integrato nel progresso giornaliero

### Studio
- Materie, voti e media
- **Simulatore di voto ipotetico** ("e se prendessi X?")

### Finanze
- Entrate/uscite per categoria
- Budget mensile e risparmio

### Abitudini e tracker
- Tracker giornaliero / settimanale / mensile / **annuale**
- Streak e promemoria

### Guardaroba
- Armadio digitale, mix & match outfit
- **Generazione outfit in base al meteo** (temperatura e condizioni)

### Altro
- Punteggio di progresso aggregato (0–100%)
- Tema OLED dark, UI completamente in italiano
- Prima configurazione guidata e tutorial
- Funziona **senza account e senza cloud** (tranne la stima macro opzionale)

---

## 🏗️ Architettura

```
app/src/main/java/com/progressone/nativeapp/
├── MainActivity.kt
├── data/                     # Store locale, modelli, calcoli, logica pura
│   ├── LocalStore.kt
│   ├── Metrics.kt
│   ├── NutritionMath.kt
│   ├── OutfitLogic.kt
│   ├── ProgressScore.kt
│   └── ...
├── foodvision/
│   ├── Food101Classifier.kt  # Modello 1: Swin Food-101 ONNX (on-device)
│   ├── FoodDatabase.kt       # Fallback nutrizionale offline
│   └── MacroAiEstimator.kt   # Modello 2: Gemini 1.5 Flash + fallback
├── media/ImageProcessor.kt
├── reminders/ReminderScheduler.kt
├── weather/WeatherService.kt
└── ui/                       # Schermate Jetpack Compose
    ├── TodayScreen.kt
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

### Requisiti
- JDK **21**
- Android SDK con API 36
- Modello ONNX Food-101 scaricato (vedi sotto)

```bash
# 1. Scarica il modello vision (336 MB, non incluso nel repo)
./scripts/download_food_model.sh

# 2. Configura il percorso dell'SDK
cp local.properties.example local.properties
# poi imposta sdk.dir=/percorso/Android/Sdk

# 3. Build debug
./gradlew :app:assembleDebug

# 4. Build release (firmata con la tua keystore)
./gradlew :app:assembleRelease
```

L'APK di debug ha package `com.progressone.nativeapp.debug` e può convivere con la
versione release `com.progressone.nativeapp`.

### Modello vision

Il file `app/src/main/assets/swin_food101_fp32.onnx` (~336 MB) è escluso da git perché
troppo grande. Viene scaricato da Hugging Face:

```
https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX/resolve/main/onnx/model.onnx
```

Le etichette delle 101 classi sono in `app/src/main/assets/food101_labels.txt`.

---

## 🔑 Configurazione Gemini (opzionale)

La stima macro cloud usa Google Gemini 1.5 Flash. La chiave **non va mai committata**:
inseriscila in `local.properties` (già in `.gitignore`):

```properties
GEMINI_API_KEY=la_tua_chiave_qui
```

Senza chiave l'app resta pienamente funzionante e usa il database nutrizionale offline.
La chiave viene letta a build time e inserita in `BuildConfig.GEMINI_API_KEY`.

---

## 📱 Moduli

| Modulo | Stato | Note |
|--------|-------|------|
| Alimentazione | ✅ | Vision + macro AI + obiettivi |
| Palestra | ✅ | BMI e storico |
| Sonno | ✅ | Punteggio recupero |
| Studio | ✅ | Simulatore voto |
| Finanze | ✅ | Budget |
| Tracker | ✅ | Giorno/settimana/mese/anno |
| Guardaroba | ✅ | Outfit meteo |
| Social | ✅ | Challenge |
| Compiti | ✅ | — |

I moduli possono essere attivati/disattivati dalle Impostazioni.

---

## 📄 Licenza

Progetto personale. Vedi il repository per i dettagli.
