# Procedura di rilascio

La release Android `v1.4.0` è preparata per essere pubblicata da
[`.github/workflows/android.yml`](../.github/workflows/android.yml). Il workflow
costruisce e verifica le varianti debug e release, esegue i test su emulatore e, per i
tag `v*` compatibili, firma la variante production e crea la GitHub Release. La
firma, l'attestazione e la pubblicazione non risultano ancora eseguite per
`v1.4.0`: lo stato verificabile è documentato in
[Stato delle verifiche](#stato-delle-verifiche-al-25-novembre-2026).

## Trigger

| Evento | Test, lint, build ed emulatore | GitHub Release |
|--------|--------------------------------|----------------|
| push a un ramo | sì | no |
| pull request | sì | no |
| esecuzione manuale | sì | no |
| push di un tag `v*` | sì | sì, solo se non esiste già |

Il tag deve iniziare con `v`, per esempio `v1.4.0`, e la parte successiva deve
corrispondere a `versionName`. Per un rilascio il tag deve essere **annotato** e il
suo commit deve essere già incluso in `main`. Il workflow usa la GitHub CLI e il
token temporaneo del job, che dispone dell'autorizzazione `contents: write` solo nel
job di release.

## Requisiti di rilascio

### Rilascio manuale

Un rilascio prodotto e pubblicato manualmente richiede:

- JDK 21, Android platform e build-tools 36.0.0, wrapper Gradle e modello Food-101
  scaricato con checksum verificato;
- `versionName`/`versionCode` coerenti con il tag, test JVM e lint completati, APK/AAB
  costruiti e test strumentati superati;
- un keystore production stabile, conservato fuori dal repository, con alias,
  password e SHA-256 del certificato atteso;
- firma dell'APK release con `apksigner`, verifica del certificato, controlli ZIP e
  ELF a 16 KiB e checksum SHA-256 calcolato sull'APK finale;
- un `provenance.txt` che registri i campi elencati di seguito e una creazione della
  Release che non sovrascriva asset esistenti.

L'attestazione firmata `actions/attest-build-provenance` è prodotta dalla pipeline
GitHub con permessi OIDC: un rilascio eseguito soltanto in locale non genera
automaticamente quell'attestazione e non deve presentarsi come equivalente.

### Requisiti della CI

- il workflow aggiornato deve essere presente nel commit taggato e, per la release,
  nel ramo `main`;
- il job `build` deve terminare con test JVM, lint, build, test su emulatore e gate
  16 KiB superati;
- il job `release` deve essere attivato dal push di un tag `v1.4.0` annotato e deve
  usare l'ambiente `production`;
- l'ambiente deve fornire i cinque secret di signing elencati sotto;
- la Release `v1.4.0` non deve esistere: il controllo preliminare e quello finale
  rifiutano una Release preesistente; il tag remoto viene confrontato con il commit
  della run prima della firma e di nuovo prima della pubblicazione, così un tag
  spostato viene rifiutato;
- `actions/attest-build-provenance` deve completare la generazione dell'attestazione
  prima della creazione della Release.

Il job di build/test riceve soltanto `contents: read` e non usa secret di signing.
Il job di release è l'unico con `contents: write`, `id-token: write` e
`attestations: write`.

## Flusso della pipeline

1. `actions/checkout` viene fissato a un commit SHA e scarica tutto l'history.
2. Il tag viene confrontato con `versionName`; per un release deve essere annotato e
   il commit deve essere incluso in `main`.
3. `actions/setup-java` viene fissato a un commit SHA e installa JDK 21.
4. `gradle/actions/setup-gradle` viene fissato a un commit SHA e configura wrapper e cache.
5. Vengono installati Android platform 36 e build-tools 36.0.0.
6. `scripts/download_food_model.sh` scarica una revisione fissa del modello Food-101.
7. La CI controlla dimensione e SHA-256 dei due modelli e verifica che il modello
   grande non sia tracciato.
8. Vengono eseguiti `testDebugUnitTest`, `testReleaseUnitTest`, `lintDebug`,
   `lintRelease`, `assembleDebug`, `assembleRelease` e `bundleRelease`.
9. `connectedDebugAndroidTest` viene eseguito su emulatore Android 36 x86_64.
10. `aapt` conferma package, versione e ABI; `apksigner` verifica la firma debug;
    `zipalign -P 16` controlla l'allineamento ZIP e `verify_elf_alignment.sh`
    applica i gate ELF ufficiali: `p_align >= 0x4000` per ogni segmento `LOAD` e
    confine `GNU_RELRO` allineato a 16 KiB oppure esteso fino alla fine del `LOAD`
    contenitore.
11. Bundletool 1.18.1, scaricato e verificato tramite SHA-256, conferma che l'AAB
    dichiari `PAGE_ALIGNMENT_16K`.
12. Per un tag `v*` pushato, l'APK release unsigned viene trasferita al job di release.
13. Il job di release esegue un checkout, reinstalla i tool Android, riverifica il tag
    remoto annotato e il suo rapporto con `main`, ripristina il keystore dai secret,
    firma e verifica APK e certificato, verifica l'allineamento e genera checksum e
    provenienza.
14. Il tag e `main` vengono riverificati immediatamente prima della pubblicazione.
15. Viene generata un'attestazione GitHub firmata della provenienza dell'APK.
16. GitHub CLI crea la Release. Se il tag è già associato a una Release, la pipeline
    termina con errore e non sovrascrive nulla.

## Secret di signing

| Secret | Contenuto | Step che lo riceve |
|--------|-----------|---------------------|
| `PROGRESS_RELEASE_KEYSTORE_BASE64` | keystore JKS codificato Base64 | solo `Ripristino protetto del keystore` |
| `PROGRESS_RELEASE_KEYSTORE_PASSWORD` | password del keystore | solo `Allineamento, firma e verifica dell'APK production` |
| `PROGRESS_RELEASE_KEY_ALIAS` | alias della chiave di firma | solo `Allineamento, firma e verifica dell'APK production` |
| `PROGRESS_RELEASE_KEY_PASSWORD` | password della chiave | solo `Allineamento, firma e verifica dell'APK production` |
| `PROGRESS_RELEASE_CERT_SHA256` | SHA-256 del certificato atteso | solo `Allineamento, firma e verifica dell'APK production` |

I secret sono disponibili soltanto nel job di release. Il file JKS viene materializzato
nella directory temporanea del runner con permessi ristretti e non viene caricato come
artifact. La CI confronta il certificato effettivo con quello atteso prima di creare la
Release. Il job usa l'ambiente `production`; il maintainer può richiedere un'approvazione
e configurare in tale ambiente secret di produzione equivalenti.

La chiave deve rimanere stabile per consentire aggiornamenti futuri. Non inserire
keystore, password o token nel repository, nelle issue o nei log.

## Asset previsti per la Release

| Asset | Contenuto |
|-------|-----------|
| `Progress1Percent_v1.4.0.apk` | APK production `com.progressone.nativeapp` |
| `Progress1Percent_v1.4.0.apk.sha256` | checksum SHA-256 nel formato `sha256sum` |
| `provenance.txt` | provenienza della build, firma e integrità del modello |

Il modello Food-101 da circa 336 MiB non viene caricato come asset separato. È
compreso nell'APK; checksum, dimensione, revisione e URL di origine sono registrati in
`provenance.txt`. Il modello non viene mai committato nel repository. La pipeline
richiede inoltre un'attestazione firmata della provenienza dell'APK, verificabile con
`gh attestation verify`.

## Stato delle verifiche al 25 novembre 2026

Lo stato seguente distingue i controlli realmente eseguiti nel checkout locale dai
risultati che dipendono dalla pipeline GitHub.

| Area | Esito osservato |
|------|-----------------|
| toolchain e dipendenze | suite eseguita con JDK 21 e `--dependency-verification strict`; task di test, lint, build APK e bundle completati |
| test JVM | 90 test debug e 90 test release, senza failure o error |
| lint | debug e release completati senza errori; ogni report locale contiene 22 warning e 1 hint |
| test strumentati | 4 test completati senza failure o error su emulatore Android 36 x86_64, incluso il round-trip del backup multi-foto e il rollback dell'import |
| modelli | dimensioni e SHA-256 di Food-101 e macro estimator verificati con i valori documentati |
| identità delle APK | debug `com.progressone.nativeapp.debug` e release `com.progressone.nativeapp`, entrambe `1.4.0` / `1400` |
| gate 16 KiB | `zipalign -P 16 4`, gate ELF `LOAD`/`GNU_RELRO` e `PAGE_ALIGNMENT_16K` dell'AAB superati sulle build locali |
| GitHub | non risultavano ancora un tag o una Release `v1.4.0`; il workflow Android non era disponibile sul ramo predefinito GitHub, quindi non era osservabile una run Android CI per questa versione |
| firma e pubblicazione production | non eseguiti: non erano ancora stati prodotti l'APK production firmata, il checksum dell'asset pubblicato, `provenance.txt` e l'attestazione GitHub |

Non è quindi corretto dichiarare `v1.4.0` già pubblicata. Prima della pubblicazione
occorre portare il workflow aggiornato in `main`, configurare i secret nell'ambiente
`production`, eseguire i controlli della pipeline e creare il tag annotato sul commit
già incluso in `main`.

## Preparazione del maintainer

### 1. Verificare la versione Android

Controllare `app/build.gradle.kts`:

```text
applicationId = "com.progressone.nativeapp"
versionCode = 1400
versionName = "1.4.0"
```

Il workflow non corregge automaticamente questi valori e rifiuta la creazione della
release se `versionName` non coincide con il tag `v*`.

### 2. Verificare i secret e il certificato

I secret devono essere configurati nel repository GitHub prima del push del tag. Il
valore `PROGRESS_RELEASE_CERT_SHA256` deve corrispondere al certificato contenuto nel
keystore. Il controllo è ripetuto dalla CI, ma conviene eseguirlo anche in locale:

```bash
keytool -list -v \
  -keystore /percorso/privato/progress-one-release.jks \
  -storepass "$KS_PASS" \
  -alias "$KEY_ALIAS" | grep 'SHA256:'
```

Il certificato dell'APK firmato viene verificato con `apksigner verify --print-certs`.

### 3. Eseguire i controlli locali

```bash
./scripts/download_food_model.sh
./gradlew --no-daemon --stacktrace --dependency-verification strict \
  testDebugUnitTest testReleaseUnitTest lintDebug lintRelease \
  assembleDebug assembleRelease bundleRelease
./gradlew --no-daemon --dependency-verification strict connectedDebugAndroidTest
```

Entrambi i modelli devono superare dimensione e checksum indicati in
[docs/BUILD.md](BUILD.md). Non eseguire `git add` sul file ONNX da 336 MiB.

### 4. Verificare identità, firma e allineamento

```bash
BT="$ANDROID_HOME/build-tools/36.0.0"

"$BT/aapt" dump badging \
  app/build/outputs/apk/release/app-release-unsigned.apk | head -n 1

"$BT/zipalign" -c -P 16 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk

scripts/verify_elf_alignment.sh \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

La prima riga deve riportare:

```text
package: name='com.progressone.nativeapp' versionCode='1400' versionName='1.4.0'
```

La firma e il segmento ELF 16 KiB vengono verificati con i comandi descritti in
[BUILD.md](BUILD.md).

### 5. Creare e pubblicare il tag

Dopo la fusione del ramo in `main` e il passaggio dei controlli, creare un tag
annotato sulla revisione desiderata:

```bash
git fetch origin main
git switch main
git pull --ff-only origin main
git tag -a v1.4.0 -m 'Progress 1 Percent v1.4.0'
git push origin v1.4.0
```

Il push del tag attiva test, lint, build, emulatore, firma e release. La creazione del
tag non deve avvenire prima dell'allineamento di `versionName` e `versionCode`, né
prima del merge in `main`. Un tag firmato con GPG o SSH è consigliato; la pipeline
richiede almeno che il tag sia annotato e che il commit sia incluso in `main`.

## Contenuto di `provenance.txt`

Il file registra:

- variante e `applicationId` effettivamente presenti nell'APK;
- `versionName` e `versionCode` attesi e verificati;
- commit, ref, workflow e URL della run;
- data e ora UTC di generazione;
- task Gradle eseguiti;
- nome e SHA-256 dell'APK;
- SHA-256 del certificato di firma;
- verifica dell'allineamento ZIP, dei segmenti ELF `LOAD` e del confine `GNU_RELRO`;
- percorso, URL, revisione, dimensione e SHA-256 del modello Food-101;
- percorso, dimensione e SHA-256 del macro estimator.

L'attestazione GitHub collega inoltre l'APK al commit e alla run che l'ha prodotto.
Questi campi permettono di risalire all'esatto artefatto pubblicato e di distinguerlo da
una ricostruzione successiva.

## Verifica da parte dell'utente

Dopo il download degli asset:

```bash
sha256sum --check Progress1Percent_v1.4.0.apk.sha256
```

Il comando deve riportare:

```text
Progress1Percent_v1.4.0.apk: OK
```

Il file `.sha256` contiene il nome dell'APK, quindi la verifica va eseguita nella stessa
cartella degli asset. La firma può essere verificata anche con:

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs \
  Progress1Percent_v1.4.0.apk

gh attestation verify Progress1Percent_v1.4.0.apk \
  --repo borghifrancesco11-cell/progress-1-percent
```

## Rilancio e correzione di una release

Un nuovo run sullo stesso tag viene rifiutato se la GitHub Release esiste già. Gli
asset non vengono mai sovrascritti: questo evita che un checksum pubblicato continui a
indicare un file diverso. Non spostare un tag già pubblicato. Per correggere una
release, creare una nuova versione dell'app oppure pubblicare una release successiva.

## Sicurezza e privacy della CI

- il workflow non riceve dati utente;
- il modello viene scaricato da una sorgente nota e verificato con SHA-256;
- l'APK non contiene chiavi, token o credenziali;
- il job di build ha accesso in sola lettura ai contenuti del repository;
- il job di release ha `contents: write` per creare la Release, `id-token: write` e
  `attestations: write` soltanto per l'attestazione di provenienza;
- i file `*.jks`, `*.keystore` e le password non devono mai essere versionati.

## Checklist finale

- [ ] `versionName` e `versionCode` corrispondono al tag;
- [ ] tag annotato sul commit già incluso in `main`;
- [ ] secret di signing e checksum certificato configurati;
- [ ] entrambi i modelli scaricati e checksum verificati;
- [ ] `testDebugUnitTest` e `testReleaseUnitTest` completati con successo;
- [ ] `lintDebug` e `lintRelease` completati con successo;
- [ ] `assembleDebug`, `assembleRelease` e `bundleRelease` completati con successo;
- [ ] `connectedDebugAndroidTest` completato con successo;
- [ ] APK verificata come `com.progressone.nativeapp`;
- [ ] firma, allineamento ZIP e segmenti ELF `LOAD` verificati;
- [ ] checksum, provenienza e attestazione presenti;
- [ ] nessun modello grande, keystore o backup utente aggiunto a git.
