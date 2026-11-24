# Build di Progress 1 Percent

Questa guida descrive l'ambiente richiesto, il download verificato del modello
Food-101, i task di verifica e la firma delle APK.

## 1. Requisiti

| Componente | Valore richiesto |
|------------|-------------------|
| JDK | 21 |
| Android SDK | platform 36 |
| Build Tools | 36.0.0 |
| Gradle | 8.14, tramite il wrapper `./gradlew` |
| Android Gradle Plugin | 8.11.1 |
| Kotlin | 2.2.21 |
| Android Studio | opzionale, versione compatibile con AGP 8.11 |

Configurazioni Android dichiarate nel progetto:

| Parametro | Valore |
|-----------|--------|
| namespace | `com.progressone.nativeapp` |
| `applicationId` release | `com.progressone.nativeapp` |
| `applicationId` debug | `com.progressone.nativeapp.debug` |
| `compileSdk` | 36 |
| `targetSdk` | 36 |
| `minSdk` | 26 |
| Java/Kotlin bytecode target | 17 |
| architettura release | `arm64-v8a` |
| architetture debug pubblicata | `arm64-v8a`, `x86_64` |

> **Coerenza della versione:** sul ramo `release/v1.4.0`,
> `app/build.gradle.kts` dichiara `versionName = "1.4.0"` e `versionCode = 1400`.
> Il workflow usa il tag solo per il nome della release e degli asset e rifiuta un tag
> `v*` incompatibile con `versionName`.

Il file `local.properties` non deve essere versionato:

```properties
sdk.dir=/home/<utente>/Android/Sdk
```

Per crearne una copia dall'esempio:

```bash
cp local.properties.example local.properties
```

## 2. Modelli ONNX

### Modello Food-101 escluso da git

Il modello di riconoscimento alimentare occupa 352.389.545 byte (circa 336 MiB) e non
è tracciato in Git. La regola di ignore corrispondente è:

```gitignore
app/src/main/assets/swin_food101_fp32.onnx
```

Scaricarlo con lo script esistente:

```bash
./scripts/download_food_model.sh
```

Lo script usa l'URL ufficiale del repository Hugging Face:

```text
https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX/resolve/main/onnx/model.onnx
```

Prima della build, verificare dimensione e checksum:

```bash
MODEL='app/src/main/assets/swin_food101_fp32.onnx'

test "$(stat -c '%s' "$MODEL")" = '352389545'
printf '%s  %s\n' \
  'f79aefaa7af43b7a11ae47267a8f733cb1152dec223d4d3db8fc95ab5dc44a83' \
  "$MODEL" | sha256sum --check --strict
```

La CI esegue gli stessi controlli e rifiuta la build se il file fosse tracciato in Git.
Poiché `main` è un riferimento mutabile, un aggiornamento upstream del modello deve
essere valutato esplicitamente; in quel caso vanno aggiornati insieme checksum e
documentazione, senza aggiungere il binario al repository.

### Modello stimatore dei macronutrienti

`app/src/main/assets/macro_estimator.onnx` è versionato intenzionalmente. Il file
corrente pesa 7.398.723 byte e viene impacchettato nell'APK. La provenienza e le
limitazioni della pipeline di training sono descritte in
[AI_MODELS.md](AI_MODELS.md).

## 3. Test, lint e build

Dalla radice del repository:

```bash
# Test unitari JVM
./gradlew --no-daemon testDebugUnitTest

# Android Lint sulla variante debug
./gradlew --no-daemon lintDebug

# APK debug, firmata con il keystore debug generato da Gradle
./gradlew --no-daemon assembleDebug

# Verifica combinata, usata dalla CI
./gradlew --no-daemon --stacktrace testDebugUnitTest lintDebug assembleDebug

# Smoke test strumentato (richiede un emulatore o un dispositivo connesso)
./gradlew --no-daemon connectedDebugAndroidTest
```

Il task `testDebugUnitTest` esegue i test JVM sotto
`app/src/test/java/com/progressone/nativeapp/`. I test e gli strumenti di lint non
richiedono un dispositivo fisico.

Output principali:

| Task | Output |
|------|--------|
| `assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| `assembleRelease` | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| `testDebugUnitTest` | `app/build/reports/tests/testDebugUnitTest/index.html` |
| `lintDebug` | `app/build/reports/lint-results-debug.html` |

La variante debug è installabile affiancata alla release perché Android Gradle Plugin
aggiunge il suffisso `.debug`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Il `applicationId` effettivo può essere controllato dall'APK:

```bash
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging \
  app/build/outputs/apk/debug/app-debug.apk | head -n 1
```

La riga deve riportare `package: name='com.progressone.nativeapp.debug'`.

## 4. APK release e firma di produzione

Il progetto non contiene una chiave di produzione e non esegue il signing automatico
della variante release. La chiave deve restare fuori dal repository e dai log della CI.

Prima generare la build unsigned:

```bash
./gradlew --no-daemon assembleRelease
```

Allineare e firmare con gli strumenti dell'SDK, usando un keystore protetto:

```bash
SDK_BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
VERSION='v1.4.0'

cp app/build/outputs/apk/release/app-release-unsigned.apk \
  "Progress1Percent-${VERSION}-aligned.apk"

"$SDK_BUILD_TOOLS/zipalign" -p -f 4 \
  "Progress1Percent-${VERSION}-aligned.apk" \
  "Progress1Percent-${VERSION}.apk"

"$SDK_BUILD_TOOLS/apksigner" sign \
  --ks /percorso/privato/progress-one-release.jks \
  --ks-pass env:KS_PASS \
  --key-pass env:KEY_PASS \
  --out "Progress1Percent-${VERSION}-signed.apk" \
  "Progress1Percent-${VERSION}.apk"

"$SDK_BUILD_TOOLS/apksigner" verify --verbose --print-certs \
  "Progress1Percent-${VERSION}-signed.apk"
```

Rimuovere i file intermedi non firmati e calcolare il checksum dell'artefatto finale:

```bash
sha256sum "Progress1Percent-${VERSION}-signed.apk" \
  > "Progress1Percent-${VERSION}-signed.apk.sha256"
```

Conservare la stessa chiave per tutti gli aggiornamenti: Android rifiuta un upgrade
firmato con una chiave differente. Non versionare `*.jks`, `*.keystore`, password o
file firmati.

## 5. Build in CI

Il workflow `.github/workflows/android.yml` esegue su push, pull request, esecuzione
manuale e tag `v*`:

1. installazione di JDK 21 e dei componenti Android SDK 36;
2. download del modello tramite `scripts/download_food_model.sh`;
3. verifica di dimensione e SHA-256 del modello;
4. `testDebugUnitTest`, `lintDebug` e `assembleDebug`;
5. smoke test `connectedDebugAndroidTest` su emulatore x86_64;
6. verifica di firma e `applicationId` dell'APK;
7. calcolo dello SHA-256 e creazione di `provenance.txt`;
8. upload dell'artifact e, per i tag `v*`, pubblicazione della GitHub Release.

La CI usa esclusivamente la variante debug, perché il repository non dispone di una
chiave di produzione. L'asset `Progress1Percent-debug.apk` è firmato con il keystore
debug temporaneo del runner e ha `applicationId`
`com.progressone.nativeapp.debug`. La procedura di rilascio e la struttura della
provenienza sono documentate in [RELEASE.md](RELEASE.md).

## 6. Riproducibilità e integrità

- Il checksum SHA-256 identifica il file effettivamente pubblicato; non implica che
  due APK prodotti in momenti diversi abbiano lo stesso byte stream.
- `provenance.txt` collega l'APK al commit, al ref, alla run della CI e al modello
  utilizzato.
- Il modello Food-101 non viene committato né caricato come artifact della release; è
  incorporato nell'APK e identificato dal checksum registrato nella provenienza.
- Il macro estimator e il file delle etichette sono versionati perché piccoli e
  necessari alla build.

## 7. Diagnostica

### `SDK location not found`

Verificare `local.properties` e la variabile `ANDROID_HOME`. In CI entrambe vengono
configurate esplicitamente.

### `Failed to find target with hash string 'android-36'`

Installare la platform 36 e i build-tools 36.0.0 con `sdkmanager`, oppure usare
l'immagine Android richiesta dalla CI.

### Errore di checksum del modello

Eliminare il file locale e ripetere il download:

```bash
rm -f app/src/main/assets/swin_food101_fp32.onnx
./scripts/download_food_model.sh
```

Se l'errore persiste, confrontare il checksum con la documentazione del modello. Non
accettare un file con hash diverso senza revisionare la fonte upstream.

### Errore di firma dell'APK debug

La variante debug deve essere firmata automaticamente dal keystore debug. Se
`apksigner verify` segnala un errore, eliminare `app/build/` e ripetere
`assembleDebug`; per una release production, verificare alias, password e certificato
del keystore senza esporli nei log.

## 8. Struttura utile

```text
app/
├── build.gradle.kts              # configurazione del modulo app
├── proguard-rules.pro            # regole R8
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── assets/               # modelli e label
    │   ├── java/com/progressone/nativeapp/
    │   └── res/
    └── test/                     # test unitari JVM
scripts/
└── download_food_model.sh        # download del modello escluso da git
docs/
├── AI_MODELS.md
├── ARCHITECTURE.md
├── BUILD.md
├── PRIVACY.md
└── RELEASE.md
```
