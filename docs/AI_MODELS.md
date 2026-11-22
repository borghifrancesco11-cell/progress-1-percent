# The two AI models

Both models run **inside the app, on the device**. There is no API key, no account and no
Google AI service involved. The APK ships with both networks in `app/src/main/assets/`.

---

## Model 1 — food recognition (photo → dish)

| | |
|---|---|
| Network | Swin Transformer (tiny), fine-tuned on Food-101 |
| Format | ONNX FP32 (`swin_food101_fp32.onnx`, ~336 MB) |
| Runtime | `com.microsoft.onnxruntime:onnxruntime-android` |
| Input | `pixel_values` — float32 `[1, 3, 224, 224]`, RGB, ImageNet normalisation |
| Output | `logits` — float32 `[1, 101]` |
| Code | `app/src/main/java/com/progressone/nativeapp/foodvision/Food101Classifier.kt` |

### Preprocessing

The implementation mirrors the model card of
[`onnx-community/swin-finetuned-food101-ONNX`](https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX):

1. decode the photo and rotate it according to its EXIF orientation;
2. resize to 224 × 224 with bilinear filtering;
3. scale the RGB channels by 1/255;
4. subtract the ImageNet mean `(0.485, 0.456, 0.406)` and divide by the standard deviation
   `(0.229, 0.224, 0.225)`;
5. write the result as NCHW float32.

A softmax turns the logits into confidences and the top four classes are offered to the
user as suggestions.

### Labels

`food101_labels.txt` contains the 101 class names in the exact order of the model's
`config.json` `id2label` map (index 0 = `apple_pie`, index 1 = `baby_back_ribs`, …).

### Deployment

ONNX Runtime needs a real file on disk, so the asset is materialised once into the app's
private storage (`noBackupFilesDir`) and reused afterwards. The copy is atomic (temporary
file + rename), verified against the asset length, and retried if it fails. The session is
created from the file path so the model is memory-mapped instead of being loaded into the
Java heap.

---

## Model 2 — macro estimation (dish → calories, macros, portion)

| | |
|---|---|
| Network | feed-forward MLP, 3 hidden layers (512 / 256 / 128), ReLU, trained on log1p targets |
| Format | ONNX (`macro_estimator.onnx`, ~7 MB) |
| Runtime | `com.microsoft.onnxruntime:onnxruntime-android` |
| Input | `features` — float32 `[1, 2149]` |
| Output | `nutrition_per100g` — float32 `[1, 4]` (kcal, protein, carbs, fat) and `portion_grams` — float32 `[1, 1]` |
| Code | `app/src/main/java/com/progressone/nativeapp/foodvision/MacroModel.kt` |

### Feature vector

The 2149 float features are built deterministically from the food name:

| Range | Content |
|-------|---------|
| `0 … 511` | hashed word unigrams (FNV-1a, L2 normalised) |
| `512 … 1023` | hashed word bigrams (L2 normalised) |
| `1024 … 2047` | hashed character 3-grams (L2 normalised) |
| `2048 … 2148` | one-hot Food-101 class (all zeros when the food is not a Food-101 dish) |

The class one-hot lets the network learn a dedicated embedding for each of the 101 dishes
while the hashed text features carry the generalisation to arbitrary food names.

`app/src/test/java/com/progressone/nativeapp/MacroModelFeatureTest.kt` asserts that the
Kotlin implementation produces exactly the same features as the Python training pipeline.

### Training data

| Source | Rows | Content |
|--------|------|---------|
| Open Food Facts (JSONL export) | ~39 700 raw / 29 600 unique | real products, nutrition per 100 g |
| `Nandhini3737/standardized-food-nutrition-dataset` | 521 | standardised meals |
| App reference table (`FoodDatabase.kt`) | 101 | curated Food-101 dishes, USDA-style averages |

Rows whose energy does not match the Atwater factors (`4·protein + 4·carbs + 9·fat`) within
30 % are discarded. The 101 curated dishes replace same-name rows from the open datasets and
are up-weighted during training.

### Measured quality

| Metric | Model | Constant baseline |
|--------|-------|-------------------|
| Held-out foods, kcal/100 g | **54.0** | 149.4 |
| Held-out foods, protein/100 g | **2.3** | 6.0 |
| Held-out foods, carbs/100 g | **8.7** | 24.5 |
| Held-out foods, fat/100 g | **4.3** | 11.3 |
| Food-101 dishes, kcal/100 g | **2.6** | — |

The exported ONNX graph is checked against the scikit-learn model on every export
(`max |sklearn − onnx| < 0.5`).

### Retraining

The pipeline is reproducible:

```bash
python3 -m venv venv && ./venv/bin/pip install numpy scikit-learn onnx onnxruntime skl2onnx
./venv/bin/python build_dataset.py     # downloads and merges the training data
./venv/bin/python train_macro_model_v4.py   # trains and exports macro_estimator.onnx
```

`train_macro_model_v4.py` writes the model to `app/src/main/assets/macro_estimator.onnx`.

---

## How the two models work together

```
photo ──► model 1 (Swin Food-101) ──► dish + confidence
                                            │
                                            ▼
                                  model 2 (macro estimator)
                                            │
                                            ▼
                              calories · protein · carbs · fat · portion
```

Both stages are local, so the whole flow works in airplane mode.

If the vision model cannot be prepared (for example not enough free storage), the app keeps
working with manual entry and falls back to the reference nutrition table. If the macro
model returns implausible values, the same fallback is used.
