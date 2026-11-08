# FileBrowser — istruzioni per Claude

App Android **nativa (Kotlin + Jetpack Compose)** per esplorare tutto il file system, con menu contestuale (tocco lungo) e azioni rapide, prima fra tutte "Estrai" per gli archivi. Uso personale. Il proprietario lavora **solo da smartphone e GitHub**: nessun Android Studio, nessun test locale. Ogni modifica si verifica compilando con GitHub Actions e provando l'APK sul telefono.

## Decisioni già prese
- Kotlin nativo, non Capacitor: un file manager con migliaia di file va meglio in Compose che in una WebView.
- Build tramite GitHub Actions (`.github/workflows/android.yml`), stesso schema di `portfolioTrackerMobile`: l'APK debug esce come artifact del run.
- Firma stabile: `debug.keystore` nella root (password `android`, alias `androiddebugkey`) e `versionCode` = numero del run, così ogni APK si installa sopra il precedente senza disinstallare. Il repository è pubblico e la chiave è volutamente non protetta, come nel progetto del portafoglio: da sistemare quando il progetto sarà maturo.
- Niente Gradle wrapper committato: il workflow usa `gradle/actions/setup-gradle` con Gradle 8.9.
- UI in italiano.

## Requisiti funzionali
- Mostrare **tutti** i file e le cartelle, nascosti compresi.
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
