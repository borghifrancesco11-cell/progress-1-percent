# Architettura

Progress 1 Percent è un'applicazione Android Kotlin a modulo singolo basata su Jetpack
Compose. Tutti i dati strutturati sono salvati localmente in SQLite; foto, modello
Food-101 e altri file binari risiedono nello spazio privato dell'app.

```text
MainActivity
├── NativeRoot                 navigazione, tema e snapshot
├── TodayScreen                score giornaliero, moduli attivi e Coach Home
├── CalendarScreen             heatmap annuale, giornata e streak
├── FoodScreen                 foto AI, macro AI, diario e Coach Food
├── GymScreen / SleepScreen    allenamento, recupero e Coach Gym
├── SchoolScreen / FinanceScreen
├── TrackScreen / WardrobeScreen / SocialScreen / TasksScreen
├── ChallengesScreen / SummaryScreen
└── SettingsScreen             moduli, profilo, backup e permessi
```

## Livelli

| Livello | Package | Responsabilità |
|---------|---------|----------------|
| Dati | `data/` | `LocalStore` e persistenza SQLite; backup ZIP streamabile; validazione input/import; modelli; helper puri per metriche, punteggio, sonno, nutrizione e intelligenza locale |
| AI | `foodvision/` | `Food101Classifier` (Swin), `MacroModel` e `FoodDatabase`; inferenza ONNX sul dispositivo |
| Piattaforma | `media/`, `reminders/`, `weather/` | elaborazione foto, promemoria Android locali e lookup meteo opzionale |
| UI | `ui/` | schermate Compose, design system, navigazione e adapter |
| Dominio locale | `data/ProgressIntelligence.kt` | score giornaliero pesato, heatmap, streak e Coach deterministico |

## Snapshot e flusso dati

`AppSnapshot.load()` legge il database su `Dispatchers.IO` e produce un snapshot
immutabile delle entità correnti. `LocalStore.revision` invalida lo snapshot a ogni
mutazione locale; la UI ricompone quindi senza interrogazioni bloccanti sul thread
principale.

Il motore `DailyProgressEngine` calcola un punteggio 0–100 a partire dai soli moduli
abilitati che hanno un segnale reale nella giornata. I pesi vengono normalizzati sul
totale disponibile: un modulo senza dati non penalizza l'utente. `SmartScoreEngine`
consente di testare il contratto di pesatura senza Android o dipendenze cloud.

`LocalCoachEngine` produce soltanto advice deterministico da dati locali. Ogni route
proposto viene validato contro i moduli abilitati o contro le rotte core, così un
Coach per Food o Gym non può portare a una funzione disattivata.

## Persistenza

```text
entities(type, id, payload JSON, created_at, updated_at)
entries(owner_id, date, value, status, notes)
settings(key, value)
```

Gli snapshot JSON consentono di estendere un record senza migrazioni SQLite. La
completion history dei task usa `completedHistory` e `completedAt` dentro il payload
`task`; la data reale di completamento alimenta score, heatmap e streak senza
perdere le date precedenti.

Le foto sono cancellate insieme al record che le referenzia. `allowBackup="false"`
impedisce il backup Android automatico; l'export manuale usa il Storage Access
Framework e resta sotto controllo dell'utente.

Il backup manuale è un ZIP con `manifest.json`, `data.json` e foto binarie.
`BackupArchive` applica limiti e checksum durante la copia stream; `NativeBackupReader`
trasforma il JSON standard in record indipendenti senza costruire un `JSONObject`
monolitico. La validazione condivisa (`InputValidation` e `LocalStore.sanitizeEntity`)
rifiuta importi non finiti, date impossibili e record incompleti. Foto e dati vengono
preparati prima della transazione SQLite; un errore conserva integralmente lo stato
precedente.

## AI on-device

```text
foto → ImageProcessor → Swin Food-101 → piatto
                                      ↓
                              Macro estimator
                                      ↓
                    calorie · macro · porzione · score
```

Il runtime ONNX e i modelli sono inclusi nell'APK. Nessuna inferenza richiede rete,
account o chiave API. Il lookup del codice a barre e il meteo sono funzioni opzionali
e controllate dall'utente.

## Notifiche locali

`ReminderScheduler` usa un alarm Android inexact e due canali locali: Tracker e Task.
Il piano mantiene la scadenza reale del task, un orario configurabile e un marker
idempotente per data. Le notifiche non contengono dati sensibili; il `PendingIntent`
trasporta tipo e ID del record, così l'apertura riporta l'utente al task o tracker
esatto.
