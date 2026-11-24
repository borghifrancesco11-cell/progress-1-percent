# Privacy e trattamento dei dati

**Ultima revisione:** 24 novembre 2026

Questo documento descrive il comportamento di Progress 1 Percent osservabile nel codice
dell'applicazione. Si riferisce all'app Android e non ai servizi esterni ai quali
l'utente decide eventualmente di accedere.

## Sintesi

Progress 1 Percent è progettata come applicazione *local-first*:

- non richiede registrazione o account;
- non contiene telemetria proprietaria, advertising o tracciamento cross-app;
- non usa servizi AI cloud e non invia foto o dati personali per l'inferenza;
- conserva database, foto, preferenze e risultati locali nello spazio privato
  dell'app;
- disabilita il backup Android automatico;
- effettua richieste di rete solo per meteo e codice a barre, su azione esplicita
  dell'utente.

Il modulo Meteo è opzionale. Il modulo Social Detox richiede un accesso speciale alle
statistiche d'uso di Android, ma non trasmette tali statistiche a un server.

## Dati trattati sul dispositivo

| Categoria | Esempi | Finalità |
|-----------|--------|-----------|
| Profilo | nome, peso, altezza, età | BMI, stime delle porzioni e personalizzazione |
| Alimentazione | pasti, porzioni, calorie, macronutrienti, note, foto | diario alimentare, punteggio e analisi |
| Allenamento | workout, serie, ripetizioni, peso corporeo | progressi e obiettivi |
| Sonno | ore, qualità e recupero | score e andamento |
| Scuola | materie, voti, esami e simulazioni | media e pianificazione |
| Finanze | entrate, uscite, categorie e budget | saldo, risparmio e budget |
| Tracker e obiettivi | record, note, serie, promemoria | monitoraggio delle abitudini |
| Impegni | nome, scadenza, ora del promemoria, date di completamento | pianificazione, score, heatmap e streak |
| Guardaroba | capi, categorie, foto, outfits | gestione e suggerimenti |
| Diario e sfide | note, attività, risultati | riflessione e gamification |
| Social | limiti per app e minuti d'uso | controllo del tempo sullo schermo |
| Meteo | città, coordinate e ultima previsione | suggerimento outfit |
| Preferenze | tema, moduli attivi, notifiche, stato del tutorial | configurazione |

I dati strutturati sono memorizzati nel database SQLite `progress_one.db`. Le foto
importate o scattate vengono copiate nella directory privata dell'app; i modelli
grandi sono materializzati in `noBackupFilesDir`.

## Richieste di rete

Le connessioni esterne previste dal codice sono le seguenti.

| Funzione | Destinatario | Dati trasmessi | Quando avviene |
|----------|--------------|-----------------|----------------|
| Ricerca città | `geocoding-api.open-meteo.com` | testo della città inserito dall'utente | quando l'utente cerca una città |
| Previsioni meteo | `api.open-meteo.com` | latitudine e longitudine selezionate | quando l'utente aggiorna il meteo |
| Dati di un prodotto | `world.openfoodfacts.org` | codice a barre e campi nutrizionali richiesti | quando l'utente avvia la ricerca per codice a barre |

Non vengono trasmessi alle API esterne foto, note del diario, punteggi, allenamenti,
transazioni, voti, profilo o contenuti del guardaroba. Il codice a barre è l'unico dato
di prodotto inviato a Open Food Facts; la risposta nutrizionale viene salvata localmente
quando l'utente registra il pasto.

La ricerca del codice a barre può essere effettuata mediante una foto scattata o
selezionata: l'immagine viene elaborata localmente per estrarre il codice. A quel punto
solo il codice numerico viene inviato al servizio.

## Modelli AI e machine learning

I due modelli ONNX dell'app sono locali:

- Swin Transformer Food-101 classifica il piatto dall'immagine;
- lo stimatore dei macronutrienti calcola calorie, macro e porzione.

Le immagini e i nomi dei cibi restano sul dispositivo. Non sono presenti richieste di
inferenza remoto, token, account o SDK AI generativi.

La rimozione dello sfondo per le foto del guardaroba usa ML Kit Subject Segmentation.
Il componente può scaricare il proprio modello tramite Google Play services al primo
utilizzo, se non è già presente sul dispositivo. L'elaborazione dell'immagine resta
locale; l'utente è informato nella schermata quando può avvenire il download.

## Permessi Android

| Permesso | Uso | Carattere |
|----------|-----|-----------|
| `INTERNET` | richieste opzionali a Open-Meteo e Open Food Facts | necessario solo per le funzioni online |
| `POST_NOTIFICATIONS` | promemoria locali di tracker e impegni | richiesto a Android 13+ |
| `RECEIVE_BOOT_COMPLETED` | ripianificazione dei promemoria dopo il riavvio | sistema |
| `PACKAGE_USAGE_STATS` | lettura delle statistiche d'uso per Social Detox | accesso speciale concesso dall'utente nelle impostazioni di sistema |

L'app non dichiara il permesso `CAMERA`: usa il contratto di sistema per la fotocamera,
quindi l'utente conferma ogni acquisizione nell'app Fotocamera di Android. Non chiede
inoltre il permesso di posizione: la città e le coordinate meteo sono inserite o
selezionate manualmente.

I promemoria sono alarm Android locali, pianificati nel dispositivo e ripianificati dopo
riavvio, cambio ora o aggiornamento dell'app. La notifica può mostrare il nome del
tracker o dell'impegno; l'utente può disattivare globalmente le notifiche e revocare il
permesso dalle impostazioni di Android. Nessun servizio push riceve le scadenze.

## Backup, importazione ed esportazione

L'esportazione usa Android Storage Access Framework. L'utente sceglie il documento e la
cartella di destinazione; l'app non seleziona automaticamente un servizio cloud e non
carica il backup su GitHub o altri account.

Il backup JSON può contenere:

- tutti i record del database;
- note, voti, transazioni e obiettivi;
- le foto archiviate, codificate nel file;
- le impostazioni locali.

Il limite di importazione accettato dall'app è 60 MB. L'importazione richiede una
conferma e sostituisce i record locali correnti; per questo motivo è consigliato
esportare prima un backup. Sono supportati il formato nativo e alcuni formati legacy
Progress 1%.

Poiché `android:allowBackup="false"`, Android non include i dati dell'app nel backup
automatico del sistema. Le regole `data_extraction_rules.xml` escludono inoltre
esplicitamente cloud backup e device transfer su Android 12+. La conservazione del file
esportato dipende dal provider scelto dall'utente.

## Conservazione e cancellazione

- i dati restano nel database e nella directory privata finché l'app è installata;
- la disattivazione di un modulo nasconde le relative funzioni ma non elimina i dati;
- l'eliminazione di un record rimuove anche le foto non più referenziate;
- la disinstallazione dell'app rimuove database, preferenze, foto e modelli locali;
- un backup esportato continua a esistere nel file o nel provider scelto dall'utente e
  deve essere cancellato dall'utente stesso.

Non viene mantenuto un backend remoto dell'app e non esiste una retention policy
applicata dal progettista ai dati presenti sul telefono.

## Sicurezza e accessi locali

- i file sono conservati nella sandbox dell'applicazione;
- il traffico in chiaro è disabilitato (`android:usesCleartextTraffic="false"`);
- il `FileProvider` usato per la fotocamera non è esportato e concede permessi URI solo
  alle operazioni richieste;
- non sono memorizzate password, token o credenziali;
- le build pubblicate automaticamente dalla CI sono varianti debug, firmate con il
  keystore debug del runner e identificano l'`applicationId`
  `com.progressone.nativeapp.debug`; non contengono dati utente.

Le dipendenze esterne e i loro aggiornamenti restano soggetti ai rispettivi processi di
sicurezza. L'app non implementa un canale di aggiornamento proprio.

## Link esterni

Le schermate informative possono contenere link a fonti esterne. Tali link vengono
aperti soltanto dopo un'azione esplicita dell'utente nel browser. Le pagine esterne
hanno proprie informative privacy, non controllate da Progress 1 Percent.

## Modifiche a questa informativa

Le modifiche rilevanti devono essere accompanyate da una revisione del codice e da una
nuova versione del documento nel repository. La cronologia delle modifiche è disponibile
nel sistema di versionamento del progetto.

Per segnalare un problema relativo alla privacy, evitare di pubblicare dati personali,
foto o backup in issue pubbliche. Prima di condividere un dump, verificarne e rimuovere
contenuti personali.
