# Build di Progress 1 Percent

Questa guida descrive l'ambiente richiesto, il download verificato del modello
Food-101, i test, la build delle APK e la firma production.

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
| architetture debug | `arm64-v8a`, `x86_64` |
| ONNX Runtime Android | 1.23.2 |
| `androidx.graphics:graphics-path` | 1.1.0 |
| ML Kit barcode scanning | 17.3.0 |
| ML Kit Subject Segmentation (thin) | 16.0.0-beta1 |

`androidx.graphics:graphics-path:1.1.0` è dichiarato esplicitamente: senza
l'override, Compose UI risolverebbe la versione 1.0.1. La versione 1.1.0 porta
`libandroidx.graphics.path.so` nell'APK; il gate ELF verifica comunque i segmenti
`LOAD` e `GNU_RELRO` del file nativo effettivamente impacchettato.

Sul ramo `release/v1.4.0`, `app/build.gradle.kts` dichiara
`versionName = "1.4.0"` e `versionCode = 1400`. La CI rifiuta un tag `v*` incompatibile
con `versionName`.

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

Scaricarlo con lo script verificato:

```bash
./scripts/download_food_model.sh
```

Lo script usa l'URL del repository Hugging Face:

```text
https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX/resolve/e5e50bfc6425aa546f3b4421ca8bd79d0dd610b8/onnx/model.onnx
```

La revisione upstream `e5e50bfc6425aa546f3b4421ca8bd79d0dd610b8` è fissa; il checksum
SHA-256 costituisce un secondo gate indipendente.

Il download usa un file temporaneo, verifica dimensione e SHA-256 e installa il modello
in modo atomico. Prima della build, la stessa verifica può essere ripetuta manualmente:

```bash
MODEL='app/src/main/assets/swin_food101_fp32.onnx'

test "$(stat -c '%s' "$MODEL")" = '352389545'
printf '%s  %s\n' \
  'f79aefaa7af43b7a11ae47267a8f733cb1152dec223d4d3db8fc95ab5dc44a83' \
  "$MODEL" | sha256sum --check --strict
```

La CI esegue gli stessi controlli e rifiuta la build se il file fosse tracciato in Git.
Un aggiornamento upstream deve essere valutato esplicitamente; in quel caso vanno
aggiornati insieme revisione, checksum e documentazione, senza aggiungere il binario
al repository.

### Modello stimatore dei macronutrienti

`app/src/main/assets/macro_estimator.onnx` è versionato intenzionalmente. Il file
corrente pesa 7.398.723 byte, ha SHA-256
`a4c7ed6e196051d40b137b1277148ebd2c2d9b87f04d25ce5aeb42a2a351de26` e viene impacchettato
nell'APK. La CI ne controlla dimensione e checksum. La provenienza e le limitazioni
della pipeline di training sono descritte in [AI_MODELS.md](AI_MODELS.md).

## 3. Test, lint e build

Dalla radice del repository:

```bash
# Test unitari JVM, debug e release
./gradlew --no-daemon --dependency-verification strict \
  testDebugUnitTest testReleaseUnitTest

# Android Lint, debug e release
./gradlew --no-daemon --dependency-verification strict lintDebug lintRelease

# APK debug, firmata con il keystore debug generato da Gradle
./gradlew --no-daemon --dependency-verification strict assembleDebug

# APK release unsigned e AAB
./gradlew --no-daemon --dependency-verification strict assembleRelease bundleRelease

# Verifica combinata della CI
./gradlew --no-daemon --stacktrace --dependency-verification strict \
  testDebugUnitTest testReleaseUnitTest lintDebug lintRelease \
  assembleDebug assembleRelease bundleRelease

# Test strumentati (richiede un emulatore o un dispositivo connesso)
./gradlew --no-daemon --dependency-verification strict connectedDebugAndroidTest
```

I task `testDebugUnitTest` e `testReleaseUnitTest` eseguono i test JVM sotto
`app/src/test/java/com/progressone/nativeapp/`. I test e gli strumenti di lint non
richiedono un dispositivo fisico.

Output principali:

| Task | Output |
|------|--------|
| `assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| `assembleRelease` | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| `bundleRelease` | `app/build/outputs/bundle/release/app-release.aab` |
| `testDebugUnitTest` | `app/build/reports/tests/testDebugUnitTest/index.html` |
| `testReleaseUnitTest` | `app/build/reports/tests/testReleaseUnitTest/index.html` |
| `lintDebug` | `app/build/reports/lint-results-debug.html` |
| `lintRelease` | `app/build/reports/lint-results-release.html` |

La variante debug è installabile affiancata alla release perché Android Gradle Plugin
aggiunge il suffisso `.debug`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

L'`applicationId` effettivo può essere controllato dall'APK:

```bash
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging \
  app/build/outputs/apk/debug/app-debug.apk | head -n 1
```

La riga deve riportare `package: name='com.progressone.nativeapp.debug'`. Per la release
deve riportare `package: name='com.progressone.nativeapp'` e `versionName='1.4.0'`.

## 4. APK release e firma production

Il repository non contiene una chiave di produzione e non esegue signing automatico
durante `assembleRelease`. La chiave deve restare fuori dal repository e dai log.

Per un rilascio manuale sono necessari JDK 21, Android platform/build-tools 36.0.0,
il wrapper Gradle, il modello Food-101 verificato e un keystore production stabile
Conservato fuori dal repository. Sono inoltre necessari alias, password del
keystore, password della chiave e SHA-256 del certificato atteso. L'artefatto finale
deve avere il package name `com.progressone.nativeapp`, essere firmato, superare i
controlli ZIP/ELF a 16 KiB e avere un checksum SHA-256 calcolato dopo la firma.

Prima generare la build unsigned:

```bash
./gradlew --no-daemon --dependency-verification strict assembleRelease
```

Allineare e firmare con gli strumenti dell'SDK, usando un keystore protetto. Le password
sono lette da variabili d'ambiente e non devono comparire nella shell history:

```bash
export SDK_BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
export VERSION='1.4.0'
export KS_PASS='<password-keystore>'
export KEY_ALIAS='<alias-chiave>'
export KEY_PASS='<password-chiave>'

cp app/build/outputs/apk/release/app-release-unsigned.apk \
  "Progress1Percent_v${VERSION}-zipaligned.apk"

"$SDK_BUILD_TOOLS/zipalign" -P 16 -f 4 \
  "Progress1Percent_v${VERSION}-zipaligned.apk" \
  "Progress1Percent_v${VERSION}-aligned.apk"

"$SDK_BUILD_TOOLS/apksigner" sign \
  --ks /percorso/privato/progress-one-release.jks \
  --ks-pass env:KS_PASS \
  --key-pass env:KEY_PASS \
  --ks-key-alias "$KEY_ALIAS" \
  --out "Progress1Percent_v${VERSION}.apk" \
  "Progress1Percent_v${VERSION}-aligned.apk"

"$SDK_BUILD_TOOLS/apksigner" verify --verbose --print-certs \
  "Progress1Percent_v${VERSION}.apk"
"$SDK_BUILD_TOOLS/zipalign" -c -P 16 4 \
  "Progress1Percent_v${VERSION}.apk"
scripts/verify_elf_alignment.sh \
  "Progress1Percent_v${VERSION}.apk"
```

Rimuovere i file intermedi non firmati e calcolare il checksum dell'artefatto finale:

```bash
sha256sum "Progress1Percent_v${VERSION}.apk" \
  > "Progress1Percent_v${VERSION}.apk.sha256"
```

Conservare la stessa chiave per tutti gli aggiornamenti: Android rifiuta un upgrade
firmato con una chiave differente. Non versionare `*.jks`, `*.keystore`, password o
file firmati.

### Verifica ELF 16 KiB

`zipalign -P 16` controlla l'allineamento ZIP delle librerie native dentro l'APK.
Non verifica invece i segmenti ELF. I gate ufficiali per librerie native compatibili
con pagine da 16 KiB sono:

1. ogni segmento ELF `LOAD` deve avere `p_align >= 0x4000` (16 KiB);
2. la fine di `GNU_RELRO` deve essere allineata a 16 KiB oppure `GNU_RELRO` deve
   estendersi fino alla fine del segmento `LOAD` contenitore, così nella stessa
   pagina non possono seguire dati scrivibili.

Lo script riutilizzato anche dalla CI automatizza entrambi i controlli:

```bash
scripts/verify_elf_alignment.sh \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

Lo script estrae tutte le librerie in una directory temporanea, usa `readelf` (o
`llvm-readelf`) e fallisce se una libreria non ha segmenti `LOAD`, se un
allineamento è inferiore a `0x4000`, se non è una potenza di due o non è multiplo
di `0x4000`, oppure se il confine `GNU_RELRO` non è sicuro. Lo stesso controllo
deve essere ripetuto sull'APK production finale firmato.

La CI verifica inoltre che l'AAB dichiari `PAGE_ALIGNMENT_16K` con Bundletool
1.18.1, il cui JAR è scaricato da un URL versionato e verificato tramite SHA-256.
Nel checkout verificato il 25 novembre 2026, le 4 librerie native arm64 della
release (`graphics-path`, barcode scanner e le due librerie ONNX Runtime) e le 8
librerie della variante debug hanno superato i gate `LOAD`/`GNU_RELRO`; l'AAB ha
mostrato `PAGE_ALIGNMENT_16K`. Questi risultati riguardano le build locali del
checkout, non implicano una Release GitHub già pubblicata.

## 5. Build in CI

Il workflow `.github/workflows/android.yml` esegue su push, pull request, esecuzione
manuale e tag `v*`:

1. installazione di JDK 21 e dei componenti Android SDK 36;
2. download del modello tramite `scripts/download_food_model.sh`;
3. verifica di dimensione e SHA-256 dei due modelli e controllo che il modello grande
   non sia tracciato;
4. `testDebugUnitTest`, `testReleaseUnitTest`, `lintDebug`, `lintRelease`,
   `assembleDebug`, `assembleRelease` e `bundleRelease`;
5. test strumentati su emulatore Android 36 x86_64;
6. verifica di package, versione, ABI e firma debug;
7. gate ZIP 16 KiB e gate ELF ufficiali `LOAD`/`GNU_RELRO` sulle APK debug e release;
8. verifica AAB `PAGE_ALIGNMENT_16K` con Bundletool 1.18.1;
9. caricamento dell'APK release unsigned nel job di release, per i soli tag;
10. ripristino protetto del keystore, signing, verifica del certificato, dell'APK
    firmato e checksum;
11. generazione dell'attestazione GitHub di provenienza;
12. creazione della GitHub Release senza sovrascrivere asset esistenti.

Per un tag, la CI richiede un tag annotato, la corrispondenza con `versionName` e un
commit del tag già incluso in `main`. Il job di firma usa l'ambiente GitHub
`production`; le regole di protezione e i secret possono essere configurati dal
maintainer nel repository.

### Secret richiesti al job di release

| Secret | Contenuto | Step che lo riceve |
|--------|-----------|---------------------|
| `PROGRESS_RELEASE_KEYSTORE_BASE64` | keystore JKS codificato Base64 | solo `Ripristino protetto del keystore` |
| `PROGRESS_RELEASE_KEYSTORE_PASSWORD` | password del keystore | solo `Allineamento, firma e verifica dell'APK production` |
| `PROGRESS_RELEASE_KEY_ALIAS` | alias della chiave di firma | solo `Allineamento, firma e verifica dell'APK production` |
| `PROGRESS_RELEASE_KEY_PASSWORD` | password della chiave | solo `Allineamento, firma e verifica dell'APK production` |
| `PROGRESS_RELEASE_CERT_SHA256` | SHA-256 del certificato atteso | solo `Allineamento, firma e verifica dell'APK production` |

Il job di build/test non riceve questi secret. Nel job di release il token GitHub è
usato soltanto per il checkout, il controllo della Release e `gh release create`;
i permessi `id-token: write` e `attestations: write` servono all'azione GitHub di
provenienza. La chiave viene scritta soltanto nella directory temporanea del runner,
con permessi ristretti, e non viene inclusa negli artifact. Il certificato
effettivamente usato viene confrontato con `PROGRESS_RELEASE_CERT_SHA256` prima della
pubblicazione. Il job usa inoltre l'ambiente `production`, che può essere protetto con
approvazione dei maintainer e con secret di ambiente equivalenti.

Gli asset previsti per `v1.4.0` sono:

- `Progress1Percent_v1.4.0.apk`;
- `Progress1Percent_v1.4.0.apk.sha256`;
- `provenance.txt`.

La procedura completa di rilascio è in [RELEASE.md](RELEASE.md).

### Stato delle verifiche locali al 25 novembre 2026

| Controllo | Esito osservato nel checkout |
|-----------|------------------------------|
| checksum Food-101 e macro estimator | superato con gli SHA-256 documentati |
| suite Gradle con `--dependency-verification strict` | `testDebugUnitTest`, `testReleaseUnitTest`, lint debug/release e build APK/AAB completati con successo; 90 test JVM per variante, nessun errore |
| lint debug/release | completato senza errori; i report locali contengono ancora 22 warning e 1 hint per variante |
| `connectedDebugAndroidTest` | 4 test completati con successo su emulatore Android 36 x86_64, incluso il round-trip del backup multi-foto |
| identità e versione APK | debug `com.progressone.nativeapp.debug` e release `com.progressone.nativeapp`, entrambe `1.4.0` / `1400` |
| ZIP e ELF 16 KiB | superati su 8 librerie debug e 4 librerie release arm64 |
| AAB | Bundletool 1.18.1 ha riportato `PAGE_ALIGNMENT_16K` |
| firma production e asset GitHub | non eseguiti localmente: richiedono il keystore production e la CI |

La verifica non equivale alla pubblicazione: checksum dell'APK production firmata,
`provenance.txt`, attestazione GitHub e creazione della Release restano output della
pipeline su tag.

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
`assembleDebug`. Per una release production, verificare alias, password e certificato
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
    ├── test/                     # test unitari JVM
    └── androidTest/              # test su emulatore/dispositivo
scripts/
├── download_food_model.sh        # download verificato del modello escluso da git
└── verify_elf_alignment.sh       # gate ELF LOAD 16 KiB per APK
docs/
├── AI_MODELS.md
├── ARCHITECTURE.md
├── BUILD.md
├── PRIVACY.md
└── RELEASE.md
```
