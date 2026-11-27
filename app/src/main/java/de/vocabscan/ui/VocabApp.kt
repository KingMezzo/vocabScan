package de.vocabscan.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.vocabscan.VocabScanApplication
import de.vocabscan.ui.components.ReleaseNotesDialog
import de.vocabscan.ui.components.UpdateDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import de.vocabscan.data.Deck
import de.vocabscan.data.Folder
import de.vocabscan.data.DeckQrCodec
import de.vocabscan.data.SharedDeck
import de.vocabscan.data.Vocabulary
import de.vocabscan.learn.ReviewReminder
import de.vocabscan.learn.LearningGoal
import de.vocabscan.learn.LearningStatsCalculator
import de.vocabscan.learn.StudyDirection
import de.vocabscan.learn.StudyOptions
import de.vocabscan.learn.StudyFilter
import de.vocabscan.learn.DirectionChoice
import de.vocabscan.learn.StudyData
import de.vocabscan.learn.StudyPlanner
import de.vocabscan.learn.StudySummary
import de.vocabscan.scan.ScanSource
import java.io.File
import java.time.ZoneId
import java.time.LocalDate
import java.time.Instant
import java.time.Duration
import kotlinx.coroutines.delay

private enum class MainTab(val title: String) { LEARN("Lernen"), LIBRARY("Bibliothek"), INSIGHTS("Statistik"), SETTINGS("Einstellungen") }
private sealed interface LearningRoute {
    data class Card(val session: StudySession) : LearningRoute
    data class Results(val summary: StudySummary) : LearningRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabApp(model: VocabViewModel, themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    val decks by model.decks.collectAsStateWithLifecycle()
    val folders by model.folders.collectAsStateWithLifecycle()
    val libraryContents by model.libraryContents.collectAsStateWithLifecycle()
    val selectedId by model.selectedDeckId.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val progress by model.progress.collectAsStateWithLifecycle()
    val reviewLogs by model.reviewLogs.collectAsStateWithLifecycle()
    val scanPages by model.scanPages.collectAsStateWithLifecycle()
    val selected = decks.firstOrNull { it.deck.id == selectedId }
    val context = LocalContext.current
    val updateModel: UpdateViewModel = viewModel(factory = viewModelFactory {
        initializer { UpdateViewModel((context.applicationContext as VocabScanApplication).updates) }
    })
    val updateState by updateModel.state.collectAsStateWithLifecycle()
    fun launchUpdateInstaller() {
        try {
            context.startActivity(updateModel.installationIntent())
        } catch (_: Exception) {
            updateModel.installationError("Die Installation konnte nicht geöffnet werden. Bitte erneut versuchen.")
        }
    }
    val installPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (updateModel.state.value.downloadedApk != null) {
            if (context.packageManager.canRequestPackageInstalls()) launchUpdateInstaller()
            else updateModel.installationError("Erlaube VocabScan die Installation von Updates, um fortzufahren.")
        }
    }
    val wideScreen = LocalWindowInfo.current.containerSize.width / LocalDensity.current.density >= 840
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val automaticQrDefault = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled != true
    }
    val onboardingPreferences = remember(context) { context.getSharedPreferences("onboarding", android.content.Context.MODE_PRIVATE) }
    var showOnboarding by rememberSaveable { mutableStateOf(!onboardingPreferences.getBoolean("completed", false)) }
    val learningPreferences = remember(context) { context.getSharedPreferences("learning", android.content.Context.MODE_PRIVATE) }
    var preferredMode by remember { mutableStateOf(
        runCatching { StudyMode.valueOf(learningPreferences.getString("preferred_mode", StudyMode.INPUT.name)!!) }
            .getOrDefault(StudyMode.INPUT)
    ) }
    var dailyGoal by remember { mutableIntStateOf(LearningGoal.read(context)) }
    var dayTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val zone = ZoneId.systemDefault()
            val next = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()
            delay(Duration.between(Instant.now(), next).toMillis().coerceIn(1_000L, 60_000L))
            dayTick = System.currentTimeMillis()
        }
    }
    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) dayTick = System.currentTimeMillis()
    }
    var remindersEnabled by remember { mutableStateOf(ReviewReminder.isEnabled(context)) }
    var currentTab by rememberSaveable { mutableStateOf(MainTab.LEARN) }
    var libraryFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryArchived by rememberSaveable { mutableStateOf(false) }
    var archiveReturnFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    fun openArchive() {
        archiveReturnFolderId = libraryFolderId
        libraryFolderId = null
        libraryArchived = true
    }
    fun closeArchive() {
        libraryFolderId = archiveReturnFolderId?.takeIf { id -> folders.any { it.id == id } }
        libraryArchived = false
    }
    var libraryMenuOpen by remember { mutableStateOf(false) }
    var showLibraryAdd by rememberSaveable { mutableStateOf(false) }
    var showFolderDialog by rememberSaveable { mutableStateOf(false) }
    var renameFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    val libraryFolder = folders.firstOrNull { it.id == libraryFolderId }
    var showStudySetup by rememberSaveable { mutableStateOf(false) }
    var showGlobalSearch by rememberSaveable { mutableStateOf(false) }
    var shareQrDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    val collectorSaver = remember { Saver<DeckQrCodec.Collector, ArrayList<String>>(
        save = { it.savedFrames }, restore = { frames -> DeckQrCodec.Collector().apply { frames.forEach { accept(it) } } }) }
    var qrCollector by rememberSaveable(stateSaver = collectorSaver) { mutableStateOf(DeckQrCodec.Collector()) }
    var qrProgress by rememberSaveable { mutableIntStateOf(qrCollector.received) }
    var qrPending by remember { mutableStateOf(qrCollector.completed) }
    var qrImportOpen by rememberSaveable { mutableStateOf(false) }
    var qrAutomatic by rememberSaveable { mutableStateOf(automaticQrDefault) }
    var qrScanning by remember { mutableStateOf(false) }
    var qrNextScanRequest by remember { mutableIntStateOf(0) }
    var qrTransferGeneration by remember { mutableIntStateOf(0) }
    var qrStatus by remember { mutableStateOf<String?>(null) }
    var setupDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var setupDirection by rememberSaveable { mutableStateOf(DirectionChoice.MIXED) }
    var setupFilter by rememberSaveable { mutableStateOf(StudyFilter.DUE) }
    var setupLimit by rememberSaveable { mutableIntStateOf(20) }
    var setupScanGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCsvDeckIds by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    val snackbars = remember { SnackbarHostState() }
    var showDeckDialog by remember { mutableStateOf(false) }
    var renameDeck by remember { mutableStateOf<Deck?>(null) }
    var deleteDeck by remember { mutableStateOf<Deck?>(null) }
    var managedDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCardTextImport by rememberSaveable { mutableStateOf(false) }
    var keepAdding by rememberSaveable { mutableStateOf(false) }
    var editorVersion by rememberSaveable { mutableIntStateOf(0) }
    var showWordEditor by rememberSaveable { mutableStateOf(false) }
    var editingWordId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailWordId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingWord by remember { mutableStateOf<Vocabulary?>(null) }
    var resettingWord by remember { mutableStateOf<Vocabulary?>(null) }
    var discardDraft by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var importTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var preparingScanner by rememberSaveable { mutableStateOf(false) }
    val locked = state.busy || preparingScanner
    var showBusyIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(locked, preparingScanner) {
        if (locked) {
            if (!preparingScanner) delay(250)
            showBusyIndicator = true
        } else showBusyIndicator = false
    }
    val editingDeck = decks.firstOrNull { deck -> deck.words.any { it.id == editingWordId } } ?: selected
    val editingWord = editingDeck?.words?.firstOrNull { it.id == editingWordId }
        ?: state.session?.queue?.firstOrNull()?.word?.takeIf { it.id == editingWordId }
    val scanSource = ScanSource.decode(editingWord?.scanSource)
    val scanImage = scanPages.firstOrNull { it.id == scanSource?.pageId }
        ?.let { File(context.filesDir, "scans/${it.fileName}") }
    val detailWord = selected?.words?.firstOrNull { it.id == detailWordId }
    val qrScanner = remember(context) { GmsBarcodeScanning.getClient(context,
        GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()) }
    fun scanQr() {
        if (qrScanning) return
        qrImportOpen = true
        qrScanning = true
        qrStatus = null
        val generation = qrTransferGeneration
        qrScanner.startScan().addOnSuccessListener { code ->
            if (generation != qrTransferGeneration || !qrImportOpen) return@addOnSuccessListener
            qrScanning = false
            try {
                val previousCount = qrCollector.received
                val finished = qrCollector.accept(code.rawValue ?: "")
                qrProgress = qrCollector.received
                if (finished != null) { qrPending = finished; qrProgress = 0; qrImportOpen = false }
                else {
                    qrStatus = if (qrCollector.received == previousCount) "Diesen Code hast du schon erfasst. Warte auf den nächsten." else "Code erfasst."
                    qrNextScanRequest++
                }
            } catch (error: Exception) {
                if (qrCollector.expected > 0 && qrCollector.received == qrCollector.expected)
                    qrCollector = DeckQrCodec.Collector()
                qrProgress = qrCollector.received
                qrAutomatic = false
                qrStatus = error.message ?: "QR-Code konnte nicht gelesen werden."
            }
        }.addOnCanceledListener {
            if (generation == qrTransferGeneration) { qrScanning = false; qrAutomatic = false }
        }.addOnFailureListener {
            if (generation == qrTransferGeneration) {
                qrScanning = false; qrAutomatic = false
                qrStatus = "QR-Scanner nicht verfügbar. Prüfe Google Play-Dienste und die Internetverbindung für die erste Einrichtung."
            }
        }
    }
    fun beginQrImport() {
        qrTransferGeneration++
        qrCollector = DeckQrCodec.Collector(); qrProgress = 0; qrNextScanRequest = 0
        qrAutomatic = automaticQrDefault; qrScanning = false; scanQr()
    }
    LaunchedEffect(qrNextScanRequest, qrAutomatic, qrImportOpen, qrScanning, lifecycleState) {
        if (qrNextScanRequest > 0 && qrAutomatic && qrImportOpen && !qrScanning && qrPending == null &&
            lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            delay(1800)
            scanQr()
        }
    }
    LaunchedEffect(selectedId) { if (selectedId == null) { detailWordId = null; showWordEditor = false } }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        preparingScanner = false
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.map { it.imageUri }.orEmpty()
            val target = importTarget
            if (target != null && pages.isNotEmpty()) model.startImport(target, pages)
        }
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { pages ->
        val target = importTarget
        if (target != null && pages.isNotEmpty()) model.startImport(target, pages)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) model.exportCsv(uri, pendingCsvDeckIds.toSet())
    }
    fun shareCsv(deckIds: Set<String>) {
        model.shareCsv(deckIds) { uri ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "Listen", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(send, "Listen teilen").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }) }
                .onFailure { model.showMessage("Keine App zum Teilen verfügbar.") }
        }
    }
    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importCsv(uri)
    }
    val fullExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) model.exportFull(uri)
    }
    val fullImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            ReviewReminder.setEnabled(context, true)
            remindersEnabled = true
        } else model.showMessage("Ohne Benachrichtigungsberechtigung bleiben Erinnerungen aus.")
    }

    LaunchedEffect(state.message) {
        if (state.message == "Sicherung vollständig wiederhergestellt.") {
            libraryFolderId = null
            onThemeChange(ThemePreference.read(context))
            remindersEnabled = ReviewReminder.isEnabled(context)
            dailyGoal = LearningGoal.read(context)
        }
        state.message?.let { snackbars.showSnackbar(it); model.clearMessage() }
    }
    LaunchedEffect(state.session != null) {
        if (state.session != null) snackbars.currentSnackbarData?.dismiss()
    }
    LaunchedEffect(state.deletion?.token) {
        val deletion = state.deletion ?: return@LaunchedEffect
        val result = snackbars.showSnackbar(deletion.label, actionLabel = "Rückgängig", duration = SnackbarDuration.Long)
        model.finishDeletion(deletion.token, result == SnackbarResult.ActionPerformed)
    }
    LaunchedEffect(state.archiveChange?.token) {
        val change = state.archiveChange ?: return@LaunchedEffect
        val result = snackbars.showSnackbar(
            if (change.archived) "Liste ins Archiv verschoben." else "Liste wieder aktiviert.",
            actionLabel = "Rückgängig", duration = SnackbarDuration.Long)
        model.finishArchiveChange(change.token, result == SnackbarResult.ActionPerformed)
    }
    LaunchedEffect(selected?.deck?.id, selected?.deck?.isArchived) {
        selected?.deck?.let { libraryArchived = it.isArchived; libraryFolderId = it.folderId }
    }
    BackHandler(enabled = showStudySetup || showWordEditor || detailWord != null || state.summary != null || state.draft != null || state.session != null || selectedId != null ||
        currentTab != MainTab.LEARN || libraryFolderId != null || showGlobalSearch || locked) {
        if (!locked) {
            if (showStudySetup) showStudySetup = false
            else if (state.draft != null) discardDraft = true
            else if (showWordEditor) showWordEditor = false
            else if (state.session != null) model.endStudy()
            else if (state.summary != null) model.closeSummary()
            else if (detailWord != null) detailWordId = null
            else if (showGlobalSearch) showGlobalSearch = false
            else if (selectedId != null) model.selectDeck(null)
            else if (currentTab == MainTab.LIBRARY && libraryFolderId != null) libraryFolderId = null
            else if (currentTab == MainTab.LIBRARY && libraryArchived) closeArchive()
            else currentTab = MainTab.LEARN
        }
    }

    if (showOnboarding) {
        OnboardingScreen(onFinish = {
            onboardingPreferences.edit().putBoolean("completed", true).apply()
            showOnboarding = false
        })
        return
    }

    val whatsNew = updateState.whatsNew
    val availableUpdate = updateState.update
    if (whatsNew != null) {
        ReleaseNotesDialog(
            title = "Was ist neu", notes = whatsNew, actionLabel = "Alles klar",
            onAction = updateModel::acknowledgeWhatsNew,
            onDismiss = updateModel::acknowledgeWhatsNew,
        )
    } else if (availableUpdate != null) {
        UpdateDialog(
            notes = availableUpdate.notes,
            downloading = updateState.downloading,
            progress = updateState.downloadProgress,
            ready = updateState.downloadedApk != null,
            error = updateState.downloadError,
            onDownload = updateModel::download,
            onInstall = {
                if (context.packageManager.canRequestPackageInstalls()) launchUpdateInstaller()
                else try {
                    installPermissionLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        "package:${context.packageName}".toUri()))
                } catch (_: Exception) {
                    updateModel.installationError("Die Installationsfreigabe konnte nicht geöffnet werden.")
                }
            },
            onDismiss = updateModel::postpone,
        )
    }

    val railVisible = wideScreen && !showGlobalSearch && !showWordEditor && detailWord == null &&
        state.draft == null && state.session == null && state.summary == null
    val contentWidth = when {
        state.session != null || showWordEditor || detailWord != null -> 760.dp
        state.draft != null || selected != null || currentTab == MainTab.LIBRARY -> 1100.dp
        currentTab == MainTab.LEARN -> 760.dp
        else -> 900.dp
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(when {
                    state.draft != null -> "Import prüfen"
                    showWordEditor && state.session != null -> if (editingDeck?.deck?.isFlashcards == true) "Karte bearbeiten" else "Vokabel bearbeiten"
                    state.session != null -> "Lernen"
                    state.summary != null -> "Deine Runde"
                    showWordEditor -> if (selected?.deck?.isFlashcards == true) { if (editingWord == null) "Karte hinzufügen" else "Karte bearbeiten" } else if (editingWord == null) "Vokabel hinzufügen" else "Vokabel bearbeiten"
                    detailWord != null -> if (selected.deck.isFlashcards) "Kartendetails" else "Vokabeldetails"
                    showGlobalSearch -> "Suche"
                    selected != null -> selected.deck.name
                    currentTab == MainTab.LIBRARY && libraryArchived -> "Bibliothek"
                    currentTab == MainTab.LIBRARY && libraryFolder != null -> "Bibliothek"
                    currentTab == MainTab.LEARN || currentTab == MainTab.LIBRARY -> "VocabScan"
                    else -> currentTab.title
                }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (selectedId != null || showGlobalSearch || (currentTab == MainTab.LIBRARY && (libraryFolderId != null || libraryArchived)) ||
                        showWordEditor || state.draft != null || state.session != null || state.summary != null) IconButton(
                        enabled = !locked,
                        onClick = {
                            if (state.draft != null) discardDraft = true
                            else if (showWordEditor) showWordEditor = false
                            else if (state.session != null) model.endStudy()
                            else if (state.summary != null) model.closeSummary()
                            else if (detailWord != null) detailWordId = null
                            else if (showGlobalSearch) showGlobalSearch = false
                            else if (selectedId == null && libraryFolderId != null) libraryFolderId = null
                            else if (selectedId == null && libraryArchived) closeArchive()
                            else model.selectDeck(null)
                        },
                    ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") }
                },
                actions = {
                    if (currentTab == MainTab.LIBRARY && selected == null && !showGlobalSearch && state.draft == null && state.session == null && state.summary == null) {
                        IconButton(onClick = { showGlobalSearch = true }, enabled = !locked) { Icon(Icons.Outlined.Search, "Bibliothek durchsuchen") }
                        if (!libraryArchived) Box {
                            IconButton(onClick = { libraryMenuOpen = true }, enabled = !locked) {
                                Icon(Icons.Outlined.MoreVert, "Bibliotheksoptionen")
                            }
                            DropdownMenu(expanded = libraryMenuOpen, onDismissRequest = { libraryMenuOpen = false }) {
                                DropdownMenuItem(text = { Text("Archiv öffnen") },
                                    leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                                    onClick = { libraryMenuOpen = false; openArchive() })
                                libraryFolder?.let { folder ->
                                    DropdownMenuItem(text = { Text("Ordner umbenennen") }, onClick = { libraryMenuOpen = false; renameFolderId = folder.id })
                                    DropdownMenuItem(text = { Text("Ordner löschen") }, onClick = { libraryMenuOpen = false; deleteFolderId = folder.id })
                                }
                            }
                        }
                    }
                    if (selected != null && !showWordEditor && detailWord == null && state.draft == null && state.session == null && state.summary == null) {
                        IconButton(enabled = !locked, onClick = { managedDeckId = selected.deck.id }) { Icon(Icons.Outlined.MoreVert, "Liste verwalten") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            if (currentTab == MainTab.LIBRARY && !showGlobalSearch && !showWordEditor && detailWord == null && state.draft == null && state.session == null && state.summary == null) {
                if (!libraryArchived && selected?.deck?.isArchived != true) FloatingActionButton(
                onClick = {
                    if (!locked) {
                        if (selected == null) showLibraryAdd = true
                        else { editingWordId = null; showWordEditor = true }
                    }
                },
            ) { Icon(Icons.Outlined.Add, if (selected == null) "Zur Bibliothek hinzufügen" else if (selected.deck.isFlashcards) "Karte hinzufügen" else "Vokabel hinzufügen") }
            }
        },
        bottomBar = {
            if (selected?.deck?.isArchived == true && !showGlobalSearch && !showWordEditor && detailWord == null &&
                state.draft == null && state.session == null && state.summary == null) {
                ArchiveRestoreBar(locked) { model.setArchived(selected.deck, false) }
            } else if (!wideScreen && selectedId == null && !showGlobalSearch && state.draft == null && state.session == null && state.summary == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                            onClick = { if (!locked) currentTab = tab },
                            icon = { Icon(when (tab) {
                                MainTab.LEARN -> Icons.Outlined.School
                                MainTab.LIBRARY -> Icons.Outlined.FolderOpen
                                MainTab.INSIGHTS -> Icons.Outlined.BarChart
                                MainTab.SETTINGS -> Icons.Outlined.Settings
                            }, null) },
                            label = { NavigationLabel(tab.title) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        AdaptiveAppBody(padding, showRail = railVisible, maxWidth = contentWidth, navigation = {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxHeight().width((96.dp * LocalDensity.current.fontScale).coerceIn(112.dp, 200.dp))) {
                Spacer(Modifier.height(16.dp))
                MainTab.entries.forEach { tab ->
                    NavigationRailItem(selected = currentTab == tab, enabled = !locked,
                        colors = NavigationRailItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                        onClick = { model.selectDeck(null); currentTab = tab },
                        icon = { Icon(when (tab) {
                            MainTab.LEARN -> Icons.Outlined.School
                            MainTab.LIBRARY -> Icons.Outlined.FolderOpen
                            MainTab.INSIGHTS -> Icons.Outlined.BarChart
                            MainTab.SETTINGS -> Icons.Outlined.Settings
                        }, null) }, label = { NavigationLabel(tab.title) })
                }
            }
        }) {
            val draft = state.draft
            val destinationKey = when {
                draft != null -> "import"
                state.session != null || state.summary != null -> "study"
                showGlobalSearch -> "search"
                showWordEditor -> "editor-$editingWordId"
                detailWord != null -> "word-${detailWord.id}"
                selected != null -> "deck-${selected.deck.id}"
                else -> "main"
            }
            key(destinationKey) {
                ScreenEnter {
                    when {
                draft != null -> ImportScreen(
                    draft = draft, existing = selected?.words.orEmpty(), language = selected?.deck?.language ?: "en", busy = locked,
                    targetLanguage = selected?.deck?.targetLanguage ?: "de",
                    onUpdate = model::updatePair, onRemove = model::removePair,
                    onAdd = model::addPair, onSwap = model::swapColumns, onSplit = model::setColumnSplit,
                    onDeduplicate = { model.removeDuplicates(selected?.words.orEmpty()) }, onPageEdit = model::editScanPage,
                    onConfirm = model::confirmImport,
                    canUndo = state.canUndoImport, onUndo = model::undoImport,
                    onCrop = model::cropScanPage, onMerge = model::mergePairs, onSeparate = model::splitPair,
                    onRemoveMany = model::removePairs, onReviewMany = model::reviewPairs,
                    availableDecks = decks.map { it.deck }.filter { !it.isFlashcards && (!it.isArchived || it.id == draft.deckId) },
                    onTargetChange = model::changeImportTarget,
                )
                state.session != null || state.summary != null -> {
                    val route: LearningRoute = state.session?.let(LearningRoute::Card)
                        ?: LearningRoute.Results(state.summary!!)
                    AnimatedContent(
                        targetState = route,
                        contentKey = { if (it is LearningRoute.Card) "card" else "results" },
                        transitionSpec = {
                            (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 12 }) togetherWith
                                (fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 12 })
                        },
                        label = "Lernen und Ergebnis",
                    ) { destination ->
                        when (destination) {
                            is LearningRoute.Card -> {
                                val session = destination.session
                                StudyScreen(session = session, busy = locked || state.session == null,
                                    onGrade = { model.gradeCurrent(session.queue.first().key, session.attempt, it) },
                                    onEdit = { editingWordId = session.queue.first().word.id; showWordEditor = true },
                                    onSaveAlternative = { answer ->
                                        model.addAlternative(session.queue.first().word.id, session.queue.first().direction, answer)
                                    })
                            }
                            is LearningRoute.Results -> StudySummaryScreen(destination.summary, model::closeSummary)
                        }
                    }
                }
                showGlobalSearch -> GlobalSearchScreen(decks, locked, folders = folders,
                    onOpenFolder = { folderId -> libraryFolderId = folderId; showGlobalSearch = false },
                    onOpenDeck = { deck ->
                        libraryFolderId = deck.folderId; libraryArchived = deck.isArchived
                        showGlobalSearch = false; model.selectDeck(deck.id)
                    },
                    onOpen = { deckId, word ->
                        currentTab = MainTab.LIBRARY
                        decks.firstOrNull { it.deck.id == deckId }?.deck?.let {
                            libraryFolderId = it.folderId; libraryArchived = it.isArchived
                        }
                        showGlobalSearch = false
                        model.selectDeck(deckId)
                        detailWordId = word.id
                    },
                    onFavorite = model::setFavorite)
                showWordEditor && selected != null -> WordEditorScreen(
                    original = editingWord, language = selected.deck.language, busy = locked,
                    targetLanguage = selected.deck.targetLanguage,
                    type = selected.deck.type, resetKey = editorVersion, keepAdding = keepAdding, onKeepAddingChange = { keepAdding = it },
                    scanImage = scanImage, scanSource = scanSource,
                    onCancel = { showWordEditor = false },
                    onSave = { term, translation, note, termAlternatives, translationAlternatives, chapter, tags ->
                        model.saveWord(selected.deck.id, editingWord, term, translation, note,
                            termAlternatives, translationAlternatives, chapter, tags) {
                            if (editingWord == null && selected.deck.isFlashcards && keepAdding) editorVersion++
                            else showWordEditor = false
                        }
                    },
                )
                detailWord != null -> WordDetailsScreen(
                    word = detailWord, language = selected.deck.language, type = selected.deck.type, progress = progress,
                    targetLanguage = selected.deck.targetLanguage,
                    logs = reviewLogs, busy = locked,
                    onEdit = { editingWordId = detailWord.id; showWordEditor = true },
                    onReset = { resettingWord = detailWord },
                    onDelete = { deletingWord = detailWord },
                    onFavorite = { model.setFavorite(detailWord) },
                )
                selected != null -> DeckScreen(
                    data = selected, availableDecks = decks.map { it.deck }, progress = progress,
                    reviewLogs = reviewLogs, busy = locked,
                    scanPages = scanPages.filter { it.deckId == selected.deck.id },
                    scanFile = { page -> Uri.fromFile(java.io.File(context.filesDir, "scans/${page.fileName}")) },
                    onEdit = { detailWordId = it.id },
                    onFavorite = model::setFavorite,
                    onManage = { managedDeckId = selected.deck.id },
                    onAddWord = { editingWordId = null; showWordEditor = true },
                    onImportText = { showCardTextImport = true },
                    onShareQr = { shareQrDeckId = selected.deck.id },
                    onShareCsv = { shareCsv(setOf(selected.deck.id)) },
                    onStudy = {
                        setupDeckId = selected.deck.id
                        setupDirection = if (it == StudyDirection.FOREIGN_TO_GERMAN) DirectionChoice.FOREIGN_TO_GERMAN
                            else DirectionChoice.GERMAN_TO_FOREIGN
                        setupFilter = StudyFilter.DUE; setupLimit = 20
                        setupScanGroupId = null
                        showStudySetup = true
                    },
                    onPractice = {
                        setupDeckId = selected.deck.id
                        setupDirection = if (selected.deck.isFlashcards) DirectionChoice.MIXED else DirectionChoice.FOREIGN_TO_GERMAN
                        setupFilter = StudyFilter.DUE; setupLimit = 20; showStudySetup = true
                        setupScanGroupId = null
                    },
                    onPracticeAll = {
                        setupDeckId = selected.deck.id; setupDirection = DirectionChoice.FOREIGN_TO_GERMAN
                        setupFilter = StudyFilter.ALL; setupLimit = 0; showStudySetup = true
                        setupScanGroupId = null
                    },
                    onStudyScan = { groupId ->
                        setupDeckId = selected.deck.id; setupDirection = DirectionChoice.FOREIGN_TO_GERMAN
                        setupFilter = StudyFilter.ALL; setupLimit = 0; setupScanGroupId = groupId
                        showStudySetup = true
                    },
                    onTransfer = { ids, target, move -> model.transferWords(selected.deck.id, target, ids, move) },
                    onScan = {
                        importTarget = selected.deck.id
                        preparingScanner = true
                        val options = GmsDocumentScannerOptions.Builder()
                            .setGalleryImportAllowed(true).setPageLimit(5)
                            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
                        GmsDocumentScanning.getClient(options).getStartScanIntent(context as Activity)
                            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                            .addOnFailureListener {
                                preparingScanner = false
                                model.showMessage("Scanner nicht verfügbar. Prüfe Google Play-Dienste und die Internetverbindung für die erste Einrichtung, oder nutze den Bildimport.")
                            }
                    },
                    onPhoto = {
                        importTarget = selected.deck.id
                        photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                else -> Crossfade(targetState = currentTab, animationSpec = tween(180), label = "Tabwechsel") { tab -> when (tab) {
                MainTab.LEARN -> {
                    val stats = remember(decks, progress, reviewLogs, dayTick) {
                        LearningStatsCalculator.calculate(decks, progress, reviewLogs, dayTick, ZoneId.systemDefault())
                    }
                    val dueCount = remember(decks, progress, dayTick) {
                        StudyPlanner.candidates(StudyData(decks, progress), StudyOptions(), System.currentTimeMillis()).size
                    }
                    LearnScreen(roundSize = minOf(10, dueCount),
                        hasWords = decks.any { !it.deck.isArchived && it.words.isNotEmpty() },
                        busy = locked, todayReviews = stats.today, dailyGoal = dailyGoal,
                        onLearn = { model.startStudy(StudyOptions(limit = 10), preferredMode) },
                        onLibrary = { currentTab = MainTab.LIBRARY },
                        onCustomize = {
                            setupDeckId = null; setupDirection = DirectionChoice.MIXED
                            setupFilter = if (dueCount > 0) StudyFilter.DUE else StudyFilter.ALL
                            setupLimit = 20; showStudySetup = true
                            setupScanGroupId = null
                        })
                }
                MainTab.LIBRARY -> key(libraryFolderId, libraryArchived) {
                    if (libraryArchived) ArchiveScreen(libraryContents, libraryFolderId, locked,
                        onFolder = { libraryFolderId = it },
                        onSelect = { detailWordId = null; model.selectDeck(it) },
                        onManage = { managedDeckId = it.id }, onLibrary = ::closeArchive, onRetry = model::retryLibrary)
                    else LibraryScreen(libraryContents.decks, libraryContents.folders, libraryFolderId, false, locked,
                        onFolder = { libraryFolderId = it },
                        onSelect = { detailWordId = null; model.selectDeck(it) },
                        onManage = { managedDeckId = it.id },
                        onRenameFolder = { renameFolderId = it.id },
                        onDeleteFolder = { deleteFolderId = it.id },
                        onShowActive = ::closeArchive, onOpenArchive = ::openArchive,
                        loading = libraryContents.loading, failed = libraryContents.failed, onRetry = model::retryLibrary)
                }
                MainTab.INSIGHTS -> {
                    val stats = remember(decks, progress, reviewLogs, dayTick) {
                        LearningStatsCalculator.calculate(decks, progress, reviewLogs, dayTick, ZoneId.systemDefault())
                    }
                    InsightsScreen(stats, reviewLogs, dailyGoal)
                }
                MainTab.SETTINGS -> SettingsScreen(
                    installedVersion = updateModel.installedVersion,
                    updatesEnabled = updateModel.updatesEnabled,
                    checkingUpdates = updateState.checking,
                    onCheckUpdates = updateModel::checkManually,
                    automaticUpdates = updateState.automaticEnabled,
                    onAutomaticUpdatesChange = updateModel::setAutomaticEnabled,
                    updateCheckMessage = updateState.checkMessage,
                    decks = decks.map { it.deck },
                    onOnboarding = { showOnboarding = true },
                    mode = themeMode, onModeChange = onThemeChange,
                    remindersEnabled = remindersEnabled, busy = locked,
                    onExport = { ids ->
                        pendingCsvDeckIds = ArrayList(ids)
                        exportLauncher.launch("vocabscan-listen.csv")
                    },
                    onShareCsv = ::shareCsv,
                    onImport = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
                    onFullExport = { fullExportLauncher.launch("vocabscan-full-backup.zip") },
                    onFullImport = { fullImportLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    dailyGoal = dailyGoal,
                    onDailyGoalChange = { LearningGoal.write(context, it); dailyGoal = it },
                    onRemindersChange = { enabled ->
                        if (!enabled) {
                            ReviewReminder.setEnabled(context, false)
                            remindersEnabled = false
                        } else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else {
                            ReviewReminder.setEnabled(context, true)
                            remindersEnabled = true
                        }
                    },
                )
                } }
                    }
                }
            }
            if (showBusyIndicator) LinearProgressIndicator(
                Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    if (showWordEditor && state.session != null && editingWord != null) {
        Dialog(onDismissRequest = { if (!locked) showWordEditor = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding()) {
                    WordEditorScreen(original = editingWord, language = editingDeck?.deck?.language
                        ?: state.session!!.queue.first().language, busy = locked,
                        type = state.session!!.queue.first().type,
                        targetLanguage = editingDeck?.deck?.targetLanguage ?: state.session!!.queue.first().targetLanguage,
                        scanImage = scanImage, scanSource = scanSource,
                        onCancel = { showWordEditor = false },
                        onSave = { term, translation, note, termAlternatives, translationAlternatives, chapter, tags ->
                            model.saveWord(editingWord.deckId, editingWord, term, translation, note,
                                termAlternatives, translationAlternatives, chapter, tags) { showWordEditor = false }
                        })
                }
            }
        }
    }

    if (showStudySetup) {
        StudySetupSheet(StudyData(decks.filter { !it.deck.isArchived || it.deck.id == setupDeckId }, progress), StudyOptions(deckId = setupDeckId, direction = setupDirection,
            filter = setupFilter, limit = setupLimit, scanGroupId = setupScanGroupId),
            onDismiss = { showStudySetup = false },
            onStart = { options, mode ->
                preferredMode = mode
                learningPreferences.edit().putString("preferred_mode", mode.name).apply()
                showStudySetup = false; model.startStudy(options, mode)
            })
    }
    decks.firstOrNull { it.deck.id == shareQrDeckId }?.let { deck ->
        DeckQrDialog(deck, onDismiss = { shareQrDeckId = null })
    }
    decks.firstOrNull { it.deck.id == managedDeckId }?.let { data ->
        DeckManagementSheet(data.deck, decks.map { it.deck }, folders, locked,
            onDismiss = { managedDeckId = null },
            onFolder = { folderId -> model.moveDeck(data.deck.id, folderId) { managedDeckId = null } },
            onRename = { renameDeck = data.deck; managedDeckId = null },
            onDelete = { deleteDeck = data.deck; managedDeckId = null },
            onArchive = { archived -> model.setArchived(data.deck, archived) { managedDeckId = null } },
            onDuplicate = { name, includeLearning -> model.duplicateDeck(data.deck.id, name, includeLearning) { managedDeckId = null } },
            onMerge = { target -> model.mergeDecks(data.deck.id, target) { managedDeckId = null } })
    }
    if (showLibraryAdd) LibraryAddSheet(onDismiss = { showLibraryAdd = false },
        busy = locked,
        onCreate = { model.clearMessage(); showLibraryAdd = false; libraryArchived = false; showDeckDialog = true },
        onFolder = { showLibraryAdd = false; showFolderDialog = true },
        onQr = { showLibraryAdd = false; libraryArchived = false; beginQrImport() },
        onCsv = { showLibraryAdd = false; csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
        onRestore = { showLibraryAdd = false; fullImportLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) })
    val renamingFolder = folders.firstOrNull { it.id == renameFolderId }
    if (showFolderDialog || renamingFolder != null) FolderNameDialog(renamingFolder, folders, locked,
        onDismiss = { showFolderDialog = false; renameFolderId = null },
        onSave = { name ->
            val close = { showFolderDialog = false; renameFolderId = null }
            if (renamingFolder == null) model.createFolder(name) { libraryFolderId = null; close() }
            else model.renameFolderById(renamingFolder.id, name, close)
        })
    folders.firstOrNull { it.id == deleteFolderId }?.let { folder -> ConfirmDialog(
        title = "Ordner löschen?",
        text = "„${folder.name}“ wird gelöscht. Alle Listen darin, einschließlich archivierter Listen, bleiben mit ihren Wörtern und Lernständen unter „Ohne Ordner“ erhalten.",
        confirmLabel = "Ordner löschen", onDismiss = { deleteFolderId = null },
        onConfirm = { model.deleteFolder(folder.id) {
            if (libraryFolderId == folder.id) libraryFolderId = null
            deleteFolderId = null
        } },
    ) }
    if (qrImportOpen && qrPending == null) QrImportProgress(
        received = qrCollector.receivedIndices, expected = qrCollector.expected, scanning = qrScanning,
        automatic = qrAutomatic, status = qrStatus,
        onAutomaticChange = { qrAutomatic = it; if (it && qrProgress > 0) qrNextScanRequest++ }, onScan = ::scanQr,
        onDismiss = {
            qrTransferGeneration++; qrImportOpen = false; qrScanning = false; qrAutomatic = false
            qrProgress = 0; qrCollector = DeckQrCodec.Collector()
        },
    )
    qrPending?.let { shared -> AlertDialog(
        onDismissRequest = { qrPending = null; qrCollector = DeckQrCodec.Collector() },
        title = { Text("Liste importieren?") },
        text = { Text("${shared.name} · " + (if (shared.type.isFlashcards) "Lernkarten" else
            "${de.vocabscan.data.StudyLanguage.fromCode(shared.language).label} → ${de.vocabscan.data.StudyLanguage.fromCode(shared.targetLanguage).label}") +
            " · ${shared.words.size} Karten. Es wird eine neue Liste angelegt; Lernstände werden nicht übertragen.") },
        confirmButton = { TextButton(onClick = {
            model.importSharedDeck(shared.name, shared.language, shared.words, libraryFolderId, shared.type, shared.targetLanguage)
            qrPending = null; qrCollector = DeckQrCodec.Collector()
        }) { Text("Importieren") } },
        dismissButton = { TextButton(onClick = { qrPending = null; qrCollector = DeckQrCodec.Collector() }) { Text("Abbrechen") } },
    ) }

    if (showCardTextImport && selected != null) CardTextImportDialog(locked,
        onDismiss = { showCardTextImport = false }, onReadFile = model::readCardFile,
        onImport = { text, delimiter -> model.importCardText(selected.deck.id, text, delimiter) { showCardTextImport = false } })
    if (showDeckDialog) CreateDeckDialog(busy = locked, errorMessage = state.message,
        onDismiss = { showDeckDialog = false },
        onSave = { name, language, target, type ->
            model.clearMessage()
            model.createDeck(name, language, libraryFolderId, type, target) { showDeckDialog = false }
        })
    if (renameDeck != null) DeckDialog(
        initial = renameDeck,
        onDismiss = { showDeckDialog = false; renameDeck = null },
        onSave = { name, language, target ->
            val original = renameDeck
            if (original == null) model.createDeck(name, language, libraryFolderId, targetLanguage = target) else model.renameDeck(original, name)
            showDeckDialog = false; renameDeck = null
        },
    )
    deleteDeck?.let { deck -> ConfirmDialog(
        title = "Liste löschen?", text = "„${deck.name}“ und alle enthaltenen Karten werden vom Gerät gelöscht.",
        confirmLabel = "Löschen", onDismiss = { deleteDeck = null },
        onConfirm = { model.deleteDeck(deck); detailWordId = null; showWordEditor = false; deleteDeck = null },
    ) }
    deletingWord?.let { word -> ConfirmDialog(
        title = "Karte löschen?", text = "„${word.term}“ wird aus dieser Liste gelöscht.",
        confirmLabel = "Löschen", onDismiss = { deletingWord = null },
        onConfirm = { model.deleteWord(word); detailWordId = null; showWordEditor = false; deletingWord = null },
    ) }
    resettingWord?.let { word -> ConfirmDialog(
        title = "Lernstand zurücksetzen?",
        text = "Beide Lernrichtungen und alle bisherigen Bewertungen für „${word.term}“ werden gelöscht. Die Karte und ihre Notiz bleiben erhalten.",
        confirmLabel = "Zurücksetzen", onDismiss = { resettingWord = null },
        onConfirm = { model.resetWordLearning(word.id); resettingWord = null },
    ) }
    if (discardDraft) ConfirmDialog(
        title = "Import verwerfen?", text = "Die noch nicht gespeicherten Einträge dieses Imports werden verworfen.",
        confirmLabel = "Verwerfen", onDismiss = { discardDraft = false },
        onConfirm = { model.discardImport(); discardDraft = false },
    )
    restoreUri?.let { uri -> ConfirmDialog(
        title = "Sicherung wiederherstellen?",
        text = "Alle aktuellen Listen, Lernstände, Bewertungen und Scanbilder werden durch den Inhalt der Sicherung ersetzt.",
        confirmLabel = "Wiederherstellen", onDismiss = { restoreUri = null },
        onConfirm = { model.restoreFull(uri); restoreUri = null },
    ) }
}
