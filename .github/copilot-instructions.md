# Progress1Percent — istruzioni per Copilot

App Android di crescita personale: tutto ruota attorno a una **percentuale in home** che
aggrega l'andamento dei moduli dell'app. Tutti i dati sono salvati **offline** in file JSON.

## Stack e versioni
- Kotlin 2.2.10, Jetpack Compose (BOM 2026.02.01) + Material 3, MVVM
- AGP 9.5.1, Gradle Kotlin DSL con version catalog (`gradle/libs.versions.toml`)
- `compileSdk`/`targetSdk` 37, `minSdk` 24, namespace e applicationId `com.example.myapplication`
- Firebase AI (`com.google.firebase:firebase-ai`) per il riconoscimento cibo
- Nessun XML layout: solo Compose

## Comandi
```bash
./gradlew assembleDebug          # build APK debug
./gradlew test                   # unit test
./gradlew installDebug           # installa su emulatore/dispositivo
adb shell am start -n com.example.myapplication/.MainActivity
```
- SDK: `$ANDROID_HOME` = `~/Android/Sdk` (platform 37, build-tools 36, platform-tools 37)
- Emulatore disponibile: `emulator -avd medium_phone` (KVM attivo, boot ~20 s)
- `local.properties` contiene `sdk.dir` e non va committato

## Struttura attuale
- `MainActivity` → `MyApplicationTheme` → `BakingScreen()`
- Pattern MVVM: `BakingViewModel` + `UiState` (`sealed interface`: `Initial`, `Loading`, `Success`, `Error`)
- Tema in `app/src/main/java/com/example/myapplication/ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`)
- `1% versions/1%` contiene dump UI/screenshot della versione di riferimento: usalo solo come
  riferimento visivo, non modificarlo

## Convenzioni di codice
- Nomi di classi/funzioni in inglese; **testi UI e commenti in italiano**
- Una schermata = un file `*Screen.kt` + il suo `*ViewModel.kt`; stato esposto con `StateFlow`
- Stato immutabile (`data class`), eventi verso il ViewModel, nessuna logica nei composable
- Riuso del tema Material 3, niente colori hardcoded nelle schermate
- Persistenza: JSON locale, nessun backend; ogni modulo serializza/deserializza il proprio stato
- Prima di aggiungere una dipendenza, verifica che sia davvero necessaria

## Regole di prodotto (importanti)
- La percentuale in home è l'aggregato dei moduli; ogni modulo deve esporre il proprio score
  con la stessa interfaccia, così l'aggregazione resta uniforme
- **Tracker**: goal di tipo *quit*, *count* e *count* con periodicità settimana/mese/anno;
  ha uno score separato dal totale
- **School**: media generale e per materia, prossime verifiche, simulator di impatto voto
- **Money**: entrate/uscite per categoria, budget mensile — incide sulla percentuale
- **Task**: promemoria con notifica — completarne di più alza la percentuale
- **Challenges**: sfide offline — incidono sulla percentuale
- **Food**: diario alimentare con score interno e modello AI per i macro — incide
- **Wardrobe**: vestiti, outfit, wishlist, valigie — **non** incide sulla percentuale
- Onboarding al primo avvio: permette di disattivare tutti i moduli tranne *tracker*;
  ogni nuovo modulo deve comparire lì
- Roadmap dichiarata: modello AI cibi (`riconoscitore di cibi.safetensors`), integrazione
  semplificata di openGym (solo le funzioni coerenti, unita a peso/altezza/cibo),
  modulo cultura con lettura PDF e TTS in background, blocchi social

## Sicurezza e igiene del repo
- Mai committare API key, `google-services.json`, `local.properties`, keystore
- Mai committare APK/AAB o modelli (`*.apk`, `*.aab`, `*.safetensors` sono ignorati)
- Se usi Firebase AI ricordati del permesso `INTERNET` nel manifest
- Prima di proporre una modifica importante, verifica su emulatore (bug + regressioni)
