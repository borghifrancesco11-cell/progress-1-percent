# Modelli AI locali

Progress 1 Percent usa due reti ONNX indipendenti. Entrambe sono eseguite sul
dispositivo: non sono presenti account, chiavi API, endpoint di inferenza o servizi AI
cloud. I modelli sono asset dell'APK e i dati personali non vengono inviati per
l'inferenza.

| Modello | File | Scopo | Runtime |
|---------|------|-------|---------|
| Swin Transformer Food-101 FP32 | `swin_food101_fp32.onnx` | foto → piatto e confidenza | ONNX Runtime Android 1.20.0 |
| Macro estimator | `macro_estimator.onnx` | piatto/nome → calorie, macronutrienti e porzione | ONNX Runtime Android 1.20.0 |

Entrambi i file sono locali. Il modello Food-101 è escluso da Git e viene scaricato al
build time; il macro estimator, più piccolo, è versionato intenzionalmente.

---

## 1. Riconoscimento alimentare

### Specifiche

| Proprietà | Valore |
|-----------|--------|
| Rete | Swin Transformer (tiny), fine-tuned su Food-101 |
| Formato | ONNX FP32 |
| Dimensione | 352.389.545 byte (~336 MiB) |
| Input | `pixel_values`, float32 `[1, 3, 224, 224]` |
| Output | `logits`, float32 `[1, 101]` |
| Preprocessing | RGB, resize 224×224, `/255`, normalizzazione ImageNet |
| Runtime | `com.microsoft.onnxruntime:onnxruntime-android:1.20.0` |
| Implementazione | `app/src/main/java/com/progressone/nativeapp/foodvision/Food101Classifier.kt` |

La sorgente è il repository Hugging Face
[`onnx-community/swin-finetuned-food101-ONNX`](https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX),
distribuito con licenza Apache-2.0. L'URL usato dallo script e dalla CI è:

```text
https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX/resolve/main/onnx/model.onnx
```

### Integrità del file

Il file escluso da Git è:

```text
app/src/main/assets/swin_food101_fp32.onnx
```

La CI controlla sia la dimensione sia lo SHA-256:

| Proprietà | Valore |
|-----------|--------|
| Dimensione attesa | 352.389.545 byte |
| SHA-256 | `f79aefaa7af43b7a11ae47267a8f733cb1152dec223d4d3db8fc95ab5dc44a83` |

Verifica manuale:

```bash
printf '%s  %s\n' \
  'f79aefaa7af43b7a11ae47267a8f733cb1152dec223d4d3db8fc95ab5dc44a83' \
  'app/src/main/assets/swin_food101_fp32.onnx' | sha256sum --check --strict
```

`main` è un riferimento mutabile. Se upstream pubblica un nuovo file, la build deve
interrompersi finché il nuovo contenuto non è stato verificato e il checksum non è stato
aggiornato in modo esplicito. Il binario non deve essere aggiunto al repository.

### Preprocessing

L'implementazione segue la scheda del modello:

1. decodifica della foto e correzione dell'orientamento EXIF;
2. resize bilineare a 224 × 224 pixel;
3. conversione in RGB e divisione di ogni canale per 255;
4. sottrazione delle medie ImageNet `(0.485, 0.456, 0.406)`;
5. divisione per le deviazioni standard `(0.229, 0.224, 0.225)`;
6. composizione del tensore float32 NCHW.

I logit ricevono una softmax; le quattro classi con confidenza più alta vengono proposte
all'utente come suggerimenti.

### Etichette

`app/src/main/assets/food101_labels.txt` contiene 101 nomi di classe, 1.184 byte, nello
stesso ordine della mappa `id2label` del `config.json` del modello. L'indice 0
corrisponde a `apple_pie`, l'indice 1 a `baby_back_ribs`, e così via. Il loader verifica
esplicitamente che siano presenti esattamente 101 etichette non vuote.

### Preparazione su disco

ONNX Runtime richiede un file reale. Al primo utilizzo l'asset viene copiato in
`context.noBackupFilesDir`:

1. il file temporaneo viene scritto come `swin_food101_fp32.onnx.part`;
2. la copia viene convalidata confrontandone la lunghezza con l'asset;
3. il file temporaneo viene rinominato atomicamente;
4. la sessione ONNX viene aperta tramite path, consentendo il memory mapping.

Se la copia fallisce, l'errore non viene memorizzato come stato valido e la
preparazione può essere ripetuta. Il modello non viene mai inviato a un server.

---

## 2. Stima di calorie e macronutrienti

### Specifiche della rete

| Proprietà | Valore |
|-----------|--------|
| Rete | feed-forward MLP, 3 livelli hidden `512 / 256 / 128`, ReLU |
| Formato | ONNX |
| Dimensione | 7.398.723 byte (~7 MiB) |
| Input | `features`, float32 `[1, 2149]` |
| Output | `nutrition_per100g`, float32 `[1, 4]`; `portion_grams`, float32 `[1, 1]` |
| Valori di output | kcal, proteine, carboidrati, grassi; grammi della porzione |
| Runtime | `com.microsoft.onnxruntime:onnxruntime-android:1.20.0` |
| Implementazione | `app/src/main/java/com/progressone/nativeapp/foodvision/MacroModel.kt` |

Il file versionato è:

```text
app/src/main/assets/macro_estimator.onnx
```

### Vettore delle feature

Il modello riceve 2.149 valori float deterministici costruiti dal nome del cibo:

| Intervallo | Contenuto |
|------------|-----------|
| `0 … 511` | unigrammi di parola hashati con FNV-1a e normalizzati L2 |
| `512 … 1023` | bigrammi di parola hashati con FNV-1a e normalizzati L2 |
| `1024 … 2047` | trigrammi di carattere hashati con FNV-1a e normalizzati L2 |
| `2048 … 2148` | one-hot della classe Food-101; tutti zero se il cibo non corrisponde |

La parte one-hot permette alla rete di apprendere un embedding dedicato per ciascuna
delle 101 portate, mentre le feature del testo consentono la generalizzazione ad altri
nomi di alimenti.

L'hashing usa FNV-1a a 32 bit e la stessa riduzione modulo delle bucket usata dalla
pipeline Python. Il test JVM
`app/src/test/java/com/progressone/nativeapp/MacroModelFeatureTest.kt` confronta i
risultati Kotlin con i valori di riferimento e impedisce drift silenziosi tra inferenza e
training.

### Dati di addestramento

| Fonte | Record | Contenuto |
|-------|--------|-----------|
| Open Food Facts (esportazione JSONL) | ~39.700 grezzi / ~29.600 unici | prodotti reali e valori nutrizionali per 100 g |
| `Nandhini3737/standardized-food-nutrition-dataset` | 521 | pasti standardizzati |
| Tabella di riferimento dell'app (`FoodDatabase.kt`) | 101 | valori medi USDA-style delle portate Food-101 |

Le righe con energia incompatibile con i fattori di Atwater
(`4·proteine + 4·carboidrati + 9·grassi`) entro il 30% vengono scartate. Le 101 voci
curate sostituiscono i record omonimi dei dataset aperti e ricevono un peso maggiore
durante l'addestramento.

### Qualità misurata

Le metriche riportate per l'esportazione del modello sono:

| Metrica | Modello | Baseline costante |
|---------|---------|-------------------|
| Alimenti held-out, kcal/100 g | **54,0** | 149,4 |
| Alimenti held-out, proteine/100 g | **2,3** | 6,0 |
| Alimenti held-out, carboidrati/100 g | **8,7** | 24,5 |
| Alimenti held-out, grassi/100 g | **4,3** | 11,3 |
| Portate Food-101, kcal/100 g | **2,6** | — |

L'esportazione ONNX è stata confrontata con il modello scikit-learn con il criterio
`max |sklearn − onnx| < 0.5`.

### Rigenerazione del modello

La documentazione storica del repository descrive una pipeline basata su:

```bash
python3 -m venv venv
./venv/bin/pip install numpy scikit-learn onnx onnxruntime skl2onnx
./venv/bin/python build_dataset.py
./venv/bin/python train_macro_model_v4.py
```

I due script Python e il dataset sorgente **non sono presenti nel checkout corrente**.
L'ONNX versionato è quindi l'artefatto autorevole disponibile; per rigenerarlo occorre
recuperare la pipeline originale, rieseguire l'addestramento e verificare le metriche e
la parità con scikit-learn prima di sostituire il file.

---

## Flusso combinato

```text
foto ──► Swin Food-101 ──► piatto + confidenza
                              │
                              ▼
                        Macro estimator
                              │
                              ▼
              calorie · proteine · carboidrati · grassi · porzione
```

1. la foto viene decodificata e ridimensionata localmente;
2. il classificatore propone le classi Food-101 più probabili;
3. il nome del piatto e la classe riconosciuta alimentano lo stimatore dei
   macronutrienti;
4. i valori vengono moltiplicati per la porzione scelta o stimata;
5. il risultato viene salvato con il badge `VISION AI` o `MACRO AI` a seconda della
   sorgente.

## Fallback e sicurezza

- Se il modello visivo non può essere preparato, l'app mantiene l'inserimento manuale e
  la tabella nutrizionale locale.
- Se l'output del macro estimator non è plausibile, viene usato il valore di riferimento
  locale.
- Il file del modello visivo viene mantenuto in `noBackupFilesDir` e non nel backup
  Android.
- Nessuna immagine, voce del diario, transazione, voto o record di allenamento viene
  inviato per l'inferenza.
- Le uniche ricerche esterne dell'app riguardano meteo e codice a barre, sono
  opzionali e sono descritte in [PRIVACY.md](PRIVACY.md).
