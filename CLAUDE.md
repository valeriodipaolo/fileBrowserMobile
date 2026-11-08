# FileBrowser — istruzioni per Claude

App Android **nativa (Kotlin + Jetpack Compose)** per esplorare tutto il file system, con menu contestuale (tocco lungo) e azioni rapide, prima fra tutte "Estrai" per gli archivi. Uso personale. Il proprietario lavora **solo da smartphone e GitHub**: nessun Android Studio, nessun test locale. Ogni modifica si verifica compilando con GitHub Actions e provando l'APK sul telefono.

## Decisioni già prese
- Kotlin nativo, non Capacitor: un file manager con migliaia di file va meglio in Compose che in una WebView.
- Build tramite GitHub Actions (`.github/workflows/android.yml`), stesso schema di `portfolioTrackerMobile`: l'APK debug esce come artifact del run.
- Firma stabile: `debug.keystore` nella root (password `android`, alias `androiddebugkey`) e `versionCode` = numero del run, così ogni APK si installa sopra il precedente senza disinstallare. Il repository è pubblico e la chiave è volutamente non protetta, come nel progetto del portafoglio: da sistemare quando il progetto sarà maturo.
- Niente Gradle wrapper committato: il workflow usa `gradle/actions/setup-gradle` con Gradle 8.9.
- UI in italiano.

## Requisiti funzionali
- Mostrare tutti i file e le cartelle. I file nascosti (nome che inizia con `.`) si vedono con la casella "Mostra file nascosti", disattivata di default.
- Icone per categoria (cartelle, archivi, testo, PDF, video, musica, immagini, altri) e barra di filtri in alto: "Tutti" di default, selezione singola alternativa. Le cartelle restano visibili con ogni filtro (tranne "Cartelle", che mostra solo quelle). Miniature per le immagini.
- Sotto i filtri per tipo c'è l'ordinamento: Tipo, Data, Nome, componibili e a tre stati ciascuno (disabilitato → crescente → decrescente). Più criteri attivi si applicano nell'ordine in cui sono stati attivati (numerati sul chip). Le cartelle restano sempre prima dei file; senza criteri si ordina per nome crescente.
- Nell'elenco i PDF hanno un riquadro rosso con la scritta "PDF" al posto dell'emoji.
- Icona dell'app: icona adattiva vettoriale, solo una cartella blu a due toni su sfondo trasparente (`res/drawable/ic_launcher_foreground.xml`, `res/mipmap-anydpi-v26/`), con versione monocromatica per le icone tematiche di Android 13+. Se un launcher mostrasse male lo sfondo trasparente, aggiungere un colore pieno come sfondo.
- Il file si apre con l'app predefinita di Android; "Apri con..." nel menu forza il selettore. Eccezione: i file di testo (categoria Testo) si aprono nel lettore/editor integrato (`TextEditor.kt`: lettura di default, pulsante Modifica/Salva, conferma sulle modifiche non salvate, UTF-8, massimo 2 MB, rifiuta i binari). "Leggi / modifica come testo" nel menu lo apre per qualsiasi file.
- Pulsante "+ Nuovo file di testo" in basso: chiede il nome (proposto "nuovo.txt", senza sovrascrivere file esistenti), crea il file vuoto nella cartella corrente e lo apre subito nell'editor in modalità modifica.
- Menu contestuale al tocco lungo, con azioni rapide. Per gli archivi: "Estrai qui" e "Estrai in cartella".
- Primo obiettivo: ZIP, comprese le password. Formati successivi: TAR, GZ, BZ2, XZ, 7z (Commons Compress) e RAR in sola estrazione (junrar).

## Vincoli di Android da ricordare
- Da Android 11 serve `MANAGE_EXTERNAL_STORAGE` ("Accesso a tutti i file"), che l'utente concede a mano. Va bene per un APK installato a mano, non per il Play Store.
- `Android/data` e `Android/obb` restano quasi inaccessibili da Android 13 in poi, e le cartelle di sistema senza root.
- Per aprire un file con un'altra app si usa `FileProvider` (`${applicationId}.fileprovider`).

## Come lavorare qui
- Non puoi compilare: scrivi codice prudente, con API stabili e dipendenze a versioni note. Se la build fallisce, il proprietario incolla il log di Actions.
- Dopo ogni modifica indica cosa provare sul telefono.
- Non aggiungere dipendenze pesanti senza motivo (niente `material-icons-extended`).
- **Non pubblicare ogni modifica su `main`.** Ogni push lancia una build e il proprietario vuole raccogliere più modifiche per volta. Fai i commit in locale e fai `git push` solo quando il proprietario lo chiede esplicitamente (per esempio "pubblica"). Le correzioni a una build fallita seguono la stessa regola.
