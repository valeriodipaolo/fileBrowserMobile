# FileBrowser — istruzioni per Claude

App Android **nativa (Kotlin + Jetpack Compose)** per esplorare tutto il file system, con menu delle azioni rapide (pulsante ⋮ su ogni riga), prima fra tutte "Estrai" per gli archivi. Uso personale. Il proprietario lavora **solo da smartphone e GitHub**: nessun Android Studio, nessun test locale. Ogni modifica si verifica compilando con GitHub Actions e provando l'APK sul telefono.

## Decisioni già prese
- Kotlin nativo, non Capacitor: un file manager con migliaia di file va meglio in Compose che in una WebView.
- Build tramite GitHub Actions (`.github/workflows/android.yml`), stesso schema di `portfolioTrackerMobile`: l'APK esce come artifact del run. È la variante **release** (non debuggabile, molto più fluida del debug con Compose), firmata con la stessa chiave stabile: si installa sopra le versioni debug. Minificazione (R8) disattivata per prudenza.
- Firma stabile: `debug.keystore` nella root (password `android`, alias `androiddebugkey`) e `versionCode` = numero del run, così ogni APK si installa sopra il precedente senza disinstallare. Il repository è pubblico e la chiave è volutamente non protetta, come nel progetto del portafoglio: da sistemare quando il progetto sarà maturo.
- Niente Gradle wrapper committato: il workflow usa `gradle/actions/setup-gradle` con Gradle 8.9.
- UI in italiano.

## Requisiti funzionali
- Mostrare tutti i file e le cartelle. I file nascosti (nome che inizia con `.`) si vedono con la voce "Mostra file nascosti" nel menu ⋮ della barra in alto, disattivata di default.
- Icone per categoria (cartelle, archivi, testo, PDF, video, musica, immagini, altri) e barra di filtri in alto: "Tutti" di default, selezione singola alternativa. Le cartelle restano visibili con ogni filtro (tranne "Cartelle", che mostra solo quelle). Miniature per le immagini.
- Sotto i filtri per tipo c'è l'ordinamento, senza etichetta: chip Data, Nome, Tipo, alternativi (ne è attivo al massimo uno) e a tre stati ciascuno (disabilitato → crescente → decrescente). Default: Data decrescente. Con tutto disabilitato si ordina per nome crescente. Le cartelle restano sempre prima dei file.
- Icone dei tipi di file nell'elenco (`FileTypeIcon` in `MainActivity.kt`, vettori in `AppIcons.kt`): tutte disegnate da noi, nessun logo o icona di terzi, tutte della stessa dimensione e stile (pagina con angolo tagliato e bordo colorato, 32x40 dp; cartelle con la cartella blu dell'app). PDF: rosso, righe in alto e scritta "PDF" sotto, senza sovrapposizioni. Archivi: viola, con una cerniera al centro e l'estensione sotto (ZIP, RAR, 7Z...). Testo: blu-grigio, molte righe. Video: verde acqua con triangolo play. Musica: indaco con nota. Immagini: verde con sole e montagne (solo come segnaposto della miniatura). Generici: grigi, pagina vuota con l'estensione sotto (es. DOCX).
- Icona dell'app: icona adattiva vettoriale, cartella blu a due toni (`res/drawable/ic_launcher_foreground.xml`) su sfondo bianco pieno (`@android:color/white` in `res/mipmap-anydpi-v26/`), con versione monocromatica per le icone tematiche di Android 13+.
- Visualizzatori integrati (`MediaViewers.kt`), aperti con un tocco su foto, video o musica: **foto** a schermo intero con scorrimento laterale tra le immagini della cartella, zoom a due dita o doppio tocco, trascinamento da ingrandite, un tocco mostra o nasconde la barra (immagini ridotte a ~2000 px, GIF solo primo fotogramma); **video e musica** con Media3/ExoPlayer (`PlayerView`), i file dello stesso tipo della cartella formano l'elenco di riproduzione (avanti/indietro), la riproduzione si ferma quando l'app esce dallo schermo (niente servizio in background, scelta del proprietario). Gli elenchi seguono ordine e filtri attuali. "Apri con..." resta nel menu ⋮ per usare un'app esterna. Se `PlayerView` desse problemi con il tema non AppCompat dell'app, valutare un tema Material.
- Gli altri file si aprono con l'app predefinita di Android; "Apri con..." nel menu forza il selettore. Eccezione: i file di testo (categoria Testo) si aprono nel lettore/editor integrato (`TextEditor.kt`: lettura di default, pulsante Modifica/Salva, conferma sulle modifiche non salvate, UTF-8, massimo 2 MB, rifiuta i binari). "Leggi / modifica come testo" nel menu lo apre per qualsiasi file.
- Pulsante "+ Nuovo file di testo" in basso: chiede il nome (proposto "nuovo.txt", senza sovrascrivere file esistenti), crea il file vuoto nella cartella corrente e lo apre subito nell'editor in modalità modifica.
- I file .apk si installano toccandoli: serve il permesso `REQUEST_INSTALL_PACKAGES` nel manifest e il consenso "Installa app sconosciute" per FileBrowser, che l'app chiede aprendo la schermata giusta delle impostazioni la prima volta.
- Selezione multipla per eliminare: la pressione prolungata su una riga avvia la modalità selezione (non apre nessun menu); poi un tocco, o un'altra pressione prolungata, aggiunge o toglie un elemento (casella e riga evidenziata). La barra in alto mostra il conteggio, "Tutti" (tutti gli elementi visibili), il cestino (con conferma) e la X per uscire; il tasto indietro esce dalla selezione. La selezione si azzera cambiando cartella.
- L'elenco si rilegge trascinandolo verso il basso (`PullToRefreshBox`), quando l'app torna in primo piano (`resumeTick`) e con la voce "Aggiorna" del menu ⋮. Non c'è un controllo automatico mentre l'app resta aperta in primo piano.
- Gesti sulle righe: scorrimento verso destra = elimina (rosso, con la solita conferma), verso sinistra = rinomina (blu, con il solito dialogo); la riga torna al suo posto. Disattivati durante la selezione multipla.
- Menu delle azioni rapide: pulsante ⋮ a destra di ogni riga (nascosto durante la selezione). Voci: Apri, Leggi / modifica come testo, Apri con..., Rinomina, Elimina e, per gli archivi, "Estrai qui" e "Estrai in cartella".
- Primo obiettivo: ZIP, comprese le password. Formati successivi: TAR, GZ, BZ2, XZ, 7z (Commons Compress) e RAR in sola estrazione (junrar).

## Prestazioni (scorrimento fluido)
- Le righe non devono fare chiamate al file system durante la composizione (`File.length()`, `lastModified()`, `isDirectory`, ordinamento con `lastModified`...). La cartella si legge una volta, fuori dal thread principale, in `readEntries` (`FileUtils.kt`), che produce `FileEntry` con dati già pronti; filtri, ordinamento e righe usano solo questi.
- Miniature: cache in memoria, lettura immediata dalla cache, decodifica in secondo piano con al massimo 3 richieste contemporanee.
- Il menu ⋮ di una riga si crea solo quando è aperto.

## Vincoli di Android da ricordare
- Da Android 11 serve `MANAGE_EXTERNAL_STORAGE` ("Accesso a tutti i file"), che l'utente concede a mano. Va bene per un APK installato a mano, non per il Play Store.
- `Android/data` e `Android/obb` restano quasi inaccessibili da Android 13 in poi, e le cartelle di sistema senza root.
- Per aprire un file con un'altra app si usa `FileProvider` (`${applicationId}.fileprovider`).

## Come lavorare qui
- Non puoi compilare: scrivi codice prudente, con API stabili e dipendenze a versioni note. Se la build fallisce, il proprietario incolla il log di Actions.
- Dopo ogni modifica indica cosa provare sul telefono.
- Non aggiungere dipendenze pesanti senza motivo (niente `material-icons-extended`).
- **Non pubblicare ogni modifica su `main`.** Ogni push lancia una build e il proprietario vuole raccogliere più modifiche per volta. Fai i commit in locale e fai `git push` solo quando il proprietario lo chiede esplicitamente (per esempio "pubblica"). Le correzioni a una build fallita seguono la stessa regola.
