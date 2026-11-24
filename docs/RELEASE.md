# Procedura di rilascio

La release Android è automatizzata da
[`.github/workflows/android.yml`](../.github/workflows/android.yml). Il workflow
costruisce e verifica una APK **debug**, calcola checksum e provenienza e, per i tag
`v*`, crea o aggiorna la GitHub Release.

## Avvertenza sulla variante pubblicata

Nel repository non è presente una chiave di produzione. Di conseguenza:

- la CI esegue `assembleDebug`;
- l'APK è firmata dal keystore debug temporaneo di Android Gradle Plugin;
- l'`applicationId` è `com.progressone.nativeapp.debug`;
- l'asset e il titolo della release contengono sempre la dicitura **DEBUG**;
- la variante può essere installata affiancata alla build di produzione
  `com.progressone.nativeapp`.

Non presentare questi asset come distribuzione production. Per una release firmata con
una chiave stabile, usare la procedura manuale descritta più avanti.

## Trigger

| Evento | Test, lint e build | GitHub Release |
|--------|--------------------|----------------|
| push a un ramo | sì | no |
| pull request | sì | no |
| esecuzione manuale | sì | no |
| push di un tag `v*` | sì | sì |

Il tag deve iniziare con `v`, per esempio `v1.4.0`. Il workflow usa la GitHub CLI e il
token temporaneo del job, che dispone dell'autorizzazione `contents: write` solo nel job
di release.

## Flusso della pipeline

1. `actions/checkout@v4` scarica il commit associato all'evento.
2. `actions/setup-java@v4` installa JDK 21.
3. `gradle/actions/setup-gradle@v4` configura il wrapper e la cache Gradle.
4. Vengono installati Android platform 36 e build-tools 36.0.0.
5. `scripts/download_food_model.sh` scarica il modello Food-101 escluso da Git.
6. La CI controlla dimensione e SHA-256 del modello e verifica che non sia tracciato.
7. Vengono eseguiti `testDebugUnitTest`, `lintDebug` e `assembleDebug`.
8. Lo smoke test `connectedDebugAndroidTest` viene eseguito su emulatore x86_64.
9. `aapt` conferma l'`applicationId`; `apksigner` verifica la firma dell'APK.
10. Vengono prodotti checksum SHA-256 e `provenance.txt`.
11. `actions/upload-artifact@v4` trasferisce i file al job di release.
12. Per un tag `v*`, `actions/download-artifact@v4` li recupera e GitHub CLI crea o
    aggiorna la release.

## Asset pubblicati

| Asset | Contenuto |
|-------|-----------|
| `Progress1Percent-debug.apk` | APK della variante debug |
| `Progress1Percent-debug.apk.sha256` | checksum SHA-256 nel formato `sha256sum` |
| `provenance.txt` | provenienza della build e integrità del modello |

Il modello Food-101 da circa 336 MiB non viene caricato come asset separato. È
compreso nell'APK; checksum, dimensione e URL di origine sono registrati in
`provenance.txt`. Il modello non viene mai committato nel repository.

## Preparazione del maintainer

### 1. Verificare la versione Android

Controllare `app/build.gradle.kts`:

```text
applicationId = "com.progressone.nativeapp"
versionCode = ...
versionName = ...
```

Nel ramo `release/v1.4.0` i valori sono `versionCode = 1400` e `versionName = "1.4.0"`.
Il workflow non corregge automaticamente questi valori e rifiuta la creazione della
release se `versionName` non coincide con il tag `v*`.

### 2. Eseguire i controlli locali

```bash
./scripts/download_food_model.sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
./gradlew --no-daemon connectedDebugAndroidTest
```

Il modello deve superare il checksum indicato in
[docs/BUILD.md](BUILD.md). Non eseguire `git add` sul file ONNX da 336 MiB.

### 3. Verificare l'identità e la firma dell'APK

```bash
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging \
  app/build/outputs/apk/debug/app-debug.apk | head -n 1

"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs \
  app/build/outputs/apk/debug/app-debug.apk
```

Il package name atteso è:

```text
com.progressone.nativeapp.debug
```

### 4. Creare e pubblicare il tag

Dopo la fusione del ramo e il passaggio dei controlli, creare un tag annotato sulla
revisione desiderata:

```bash
git tag -a v1.4.0 -m 'Progress 1 Percent v1.4.0'
git push origin v1.4.0
```

Il push del tag attiva test, lint, build e release. La creazione del tag non deve
avvenire prima dell'allineamento di `versionName` e `versionCode`.

## Contenuto di `provenance.txt`

Il file registra:

- variante e `applicationId` effettivamente presenti nell'APK;
- `versionName` e `versionCode` letti dall'APK;
- commit, ref, workflow e URL della run;
- data e ora UTC di generazione;
- task Gradle eseguiti;
- nome e SHA-256 dell'APK;
- tipo di firma applicata;
- percorso, URL, dimensione e SHA-256 del modello Food-101.

Questi campi permettono di risalire all'esatto artefatto pubblicato e di distinguerlo da
una ricostruzione successiva.

## Verifica da parte dell'utente

Dopo il download degli asset:

```bash
sha256sum --check Progress1Percent-debug.apk.sha256
```

Il comando deve riportare:

```text
Progress1Percent-debug.apk: OK
```

Il file `.sha256` contiene il nome dell'APK, quindi la verifica va eseguita nella stessa
cartella degli asset.

## Release con firma di produzione

La pipeline attuale non supporta una chiave production. Non inserire keystore o
password nel repository e non esporle nei log.

Una build production controllata può essere prodotta localmente o in un workflow
dedicato che riceva la chiave come secret:

1. eseguire `assembleRelease` per ottenere la APK unsigned;
2. applicare `zipalign`;
3. firmare con `apksigner` e la chiave production;
4. verificare firma e certificato;
5. calcolare lo SHA-256 dell'APK firmata;
6. includere APK, checksum e una nuova provenienza nella release;
7. usare l'`applicationId` `com.progressone.nativeapp`, non quello `.debug`.

La chiave usata per firmare deve rimanere stabile per consentire aggiornamenti futuri.
Il comando `apksigner` con una chiave diversa produce un APK che Android non considera
un aggiornamento della release precedente.

## Rilancio e aggiornamento della release

Un nuovo run sullo stesso tag aggiorna gli asset esistenti con `--clobber` e rigenera le
note. Il tag non dovrebbe essere spostato: spostare un tag già pubblicato rende ambiguo
l'artefatto associato. Per correggere una release, creare una nuova versione del tag o
pubblicare una release successiva.

## Sicurezza e privacy della CI

- il workflow non riceve dati utente;
- il modello viene scaricato da una sorgente nota e verificato con SHA-256;
- l'APK non contiene chiavi, token o credenziali;
- il job di build ha accesso in sola lettura ai contenuti del repository;
- il job di release ha accesso in scrittura solo per creare o aggiornare la release;
- i file `*.jks`, `*.keystore` e le password non devono mai essere versionati.

## Checklist finale

- [ ] `versionName` e `versionCode` corrispondono al tag;
- [ ] modello Food-101 scaricato e checksum verificato;
- [ ] `testDebugUnitTest` completato con successo;
- [ ] `lintDebug` completato con successo;
- [ ] `assembleDebug` completato con successo;
- [ ] `connectedDebugAndroidTest` completato con successo;
- [ ] APK verificata come `com.progressone.nativeapp.debug`;
- [ ] checksum e provenienza presenti;
- [ ] release etichettata come DEBUG;
- [ ] nessun modello grande, keystore o backup utente aggiunto a git.
