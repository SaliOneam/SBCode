package com.SBStudio.SBCode.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.SBStudio.SBCode.data.ProjectFiles
import com.SBStudio.SBCode.data.ReadResult
import com.SBStudio.SBCode.data.TreeNode
import com.SBStudio.SBCode.server.Preview
import com.SBStudio.SBCode.ui.theme.CodeFont
import com.SBStudio.SBCode.ui.theme.SbAccent
import com.SBStudio.SBCode.ui.theme.SbCardBorder
import com.SBStudio.SBCode.ui.theme.SbCard
import com.SBStudio.SBCode.ui.theme.SbEditorBg
import com.SBStudio.SBCode.ui.theme.SbGreen
import com.SBStudio.SBCode.ui.theme.SbPanel
import com.SBStudio.SBCode.ui.theme.SbPrimary
import com.SBStudio.SBCode.ui.theme.SbPrimaryDeep
import com.SBStudio.SBCode.ui.theme.SbPalette
import com.SBStudio.SBCode.ui.theme.SbRed
import com.SBStudio.SBCode.ui.theme.SbSidebar
import com.SBStudio.SBCode.ui.theme.SbText
import com.SBStudio.SBCode.ui.theme.SbTextDim
import com.SBStudio.SBCode.ui.theme.SbTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogKind { Info, Success, Error }

private class LogLine(val time: String, val text: String, val kind: LogKind)

private sealed class ExplorerDialog {
    class NewFile(val dir: File) : ExplorerDialog()
    class NewFolder(val dir: File) : ExplorerDialog()
    class Rename(val file: File) : ExplorerDialog()
    class Delete(val file: File) : ExplorerDialog()
}

private fun isInside(path: String, parent: String): Boolean =
    path == parent || path.startsWith(parent + File.separator)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(projectDir: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val docs = remember { mutableStateListOf<OpenDoc>() }
    var activeDoc by remember { mutableStateOf<OpenDoc?>(null) }
    val expanded = remember { mutableStateListOf<String>() }
    var treeVersion by remember { mutableIntStateOf(0) }
    var selectedDir by remember { mutableStateOf(projectDir) }
    var explorerVisible by remember { mutableStateOf(true) }
    var logVisible by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ExplorerDialog?>(null) }
    val logs = remember { mutableStateListOf<LogLine>() }
    val imeVisible = WindowInsets.isImeVisible
    // With a real keyboard connected (OTG) the layout stays as it is while you type.
    // With the on-screen keyboard the panels step aside so the code gets the room.
    val hardwareKeyboard = rememberHasHardwareKeyboard()
    val typingMode = imeVisible && !hardwareKeyboard
    var menuOpen by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun log(text: String, kind: LogKind = LogKind.Info) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logs.add(LogLine(time, text, kind))
        if (logs.size > 200) logs.removeAt(0)
    }

    fun saveAllNow() {
        docs.toList().forEach { it.saveIfDirty() }
    }

    fun openFile(f: File) {
        val existing = docs.firstOrNull { it.file == f }
        if (existing != null) {
            activeDoc = existing
            return
        }
        when (val r = ProjectFiles.readText(f)) {
            is ReadResult.Text -> {
                val d = OpenDoc(f, r.text)
                docs.add(d)
                activeDoc = d
            }
            is ReadResult.Unsupported -> toast("${f.name}: ${r.reason}")
        }
    }

    fun closeDoc(d: OpenDoc) {
        d.saveIfDirty()
        val idx = docs.indexOf(d)
        docs.remove(d)
        if (activeDoc === d) activeDoc = docs.getOrNull(idx.coerceAtMost(docs.size - 1))
    }

    // Save when the app goes to the background or this screen closes.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) saveAllNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            saveAllNow()
        }
    }

    BackHandler {
        saveAllNow()
        onClose()
    }

    // Registered after the one above, so it is asked first: Back closes the menu before leaving.
    BackHandler(enabled = menuOpen) { menuOpen = false }

    AutoSave(docs)

    // ---- run in Chrome
    fun startRun() {
        scope.launch {
            logVisible = true
            withContext(Dispatchers.IO) { docs.toList().forEach { it.saveIfDirty() } }
            val entry = File(projectDir, "index.html")
            if (!entry.isFile) {
                log("index.html was not found in this project.", LogKind.Error)
                return@launch
            }
            log("Running...")
            val port = try {
                withContext(Dispatchers.IO) { Preview.start(context, projectDir) }
            } catch (e: Exception) {
                log("Couldn't start the preview: ${e.message}", LogKind.Error)
                return@launch
            }
            delay(2000)
            if (Preview.openInBrowser(context, port, "index.html")) {
                log("Opened in Chrome", LogKind.Success)
            } else {
                log("Couldn't open a browser on this phone.", LogKind.Error)
            }
        }
    }

    var askedNotif by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startRun() }
    val onRun: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 && !askedNotif &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotif = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startRun()
        }
    }

    val nodes = remember(treeVersion, expanded.toList()) { ProjectFiles.flattenTree(projectDir, expanded.toSet()) }

    val explorerPanel: @Composable (Modifier) -> Unit = { m ->
        Explorer(
            modifier = m,
            projectName = projectDir.name,
            nodes = nodes,
            expanded = expanded,
            activeFile = activeDoc?.file,
            onTapDir = { dir ->
                val p = dir.absolutePath
                if (p in expanded) expanded.remove(p) else expanded.add(p)
                selectedDir = dir
            },
            onTapFile = { f ->
                selectedDir = f.parentFile ?: projectDir
                openFile(f)
            },
            onNewFile = { dialog = ExplorerDialog.NewFile(selectedDir) },
            onNewFolder = { dialog = ExplorerDialog.NewFolder(selectedDir) },
            onRefresh = { treeVersion++ },
            onCollapseAll = { expanded.clear() },
            onAction = { dialog = it },
        )
    }
    val logPanel: @Composable (Modifier) -> Unit = { m ->
        LogPanel(modifier = m, logs = logs, onClose = { logVisible = false })
    }

    // All the state above lives outside this block, so turning the phone keeps your tabs and text.
    Box(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxHeight > maxWidth) {
            // ---- portrait: tabs on top, then the code, then the buttons, then the explorer and LOG
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    docs = docs,
                    activeDoc = activeDoc,
                    explorerVisible = explorerVisible,
                    logVisible = logVisible,
                    showActions = false,
                    onSelect = { activeDoc = it },
                    onCloseDoc = { closeDoc(it) },
                    onToggleExplorer = { explorerVisible = !explorerVisible },
                    onToggleLog = { logVisible = !logVisible },
                    onRun = onRun,
                    onMenu = { menuOpen = true },
                )
                EditorPane(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    doc = activeDoc,
                    projectEmpty = nodes.isEmpty(),
                    showHelper = false,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolbarButtons(
                        explorerVisible = explorerVisible,
                        logVisible = logVisible,
                        onToggleExplorer = { explorerVisible = !explorerVisible },
                        onToggleLog = { logVisible = !logVisible },
                        onRun = onRun,
                    )
                }
                if (typingMode) {
                    // Keyboard open: the panels step aside and the quick keys sit right above the keyboard.
                    activeDoc?.let { HelperBar(it) }
                } else {
                    if (explorerVisible) {
                        explorerPanel(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .height(210.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (logVisible) {
                        logPanel(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .height(140.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        } else {
            // ---- landscape: explorer | code | LOG side by side
            Column(Modifier.fillMaxSize()) {
                if (!typingMode) {
                    TopBar(
                        docs = docs,
                        activeDoc = activeDoc,
                        explorerVisible = explorerVisible,
                        logVisible = logVisible,
                        showActions = true,
                        onSelect = { activeDoc = it },
                        onCloseDoc = { closeDoc(it) },
                        onToggleExplorer = { explorerVisible = !explorerVisible },
                        onToggleLog = { logVisible = !logVisible },
                        onRun = onRun,
                        onMenu = { menuOpen = true },
                    )
                }
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(top = if (typingMode) 4.dp else 0.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (explorerVisible && !typingMode) {
                        explorerPanel(
                            Modifier
                                .width(240.dp)
                                .fillMaxHeight()
                        )
                    }
                    EditorPane(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        doc = activeDoc,
                        projectEmpty = nodes.isEmpty(),
                        showHelper = typingMode,
                    )
                    if (logVisible && !typingMode) {
                        logPanel(
                            Modifier
                                .width(210.dp)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }

    SideMenu(
        open = menuOpen,
        dark = SbPalette.dark,
        onToggleDark = { SbPalette.setDark(context, !SbPalette.dark) },
        onClose = { menuOpen = false },
    )
    }

    // ---- explorer dialogs
    when (val d = dialog) {
        is ExplorerDialog.NewFile -> NameDialog(
            title = "New file",
            label = "File name (for example index.html)",
            confirmText = "Create",
            validate = { ProjectFiles.validateName(it, d.dir, "A file or folder with this name already exists") },
            onConfirm = { name ->
                ProjectFiles.createFile(d.dir, name)
                    .onSuccess { f ->
                        dialog = null
                        if (d.dir != projectDir && d.dir.absolutePath !in expanded) expanded.add(d.dir.absolutePath)
                        selectedDir = d.dir
                        treeVersion++
                        openFile(f)
                    }
                    .onFailure { toast(it.message ?: "Couldn't create the file") }
            },
            onDismiss = { dialog = null },
        )

        is ExplorerDialog.NewFolder -> NameDialog(
            title = "New folder",
            label = "Folder name",
            confirmText = "Create",
            validate = { ProjectFiles.validateName(it, d.dir, "A file or folder with this name already exists") },
            onConfirm = { name ->
                ProjectFiles.createFolder(d.dir, name)
                    .onSuccess { f ->
                        dialog = null
                        if (d.dir != projectDir && d.dir.absolutePath !in expanded) expanded.add(d.dir.absolutePath)
                        expanded.add(f.absolutePath)
                        selectedDir = f
                        treeVersion++
                    }
                    .onFailure { toast(it.message ?: "Couldn't create the folder") }
            },
            onDismiss = { dialog = null },
        )

        is ExplorerDialog.Rename -> NameDialog(
            title = "Rename",
            label = "New name",
            confirmText = "Rename",
            initial = d.file.name,
            validate = {
                ProjectFiles.validateName(
                    it, d.file.parentFile ?: projectDir,
                    "A file or folder with this name already exists", ignore = d.file
                )
            },
            onConfirm = { name ->
                saveAllNow()
                val old = d.file
                ProjectFiles.rename(old, name)
                    .onSuccess { renamed ->
                        val oldPath = old.absolutePath
                        val newPath = renamed.absolutePath
                        docs.forEach { doc ->
                            val p = doc.file.absolutePath
                            if (p == oldPath) doc.file = renamed
                            else if (p.startsWith(oldPath + File.separator)) doc.file = File(newPath + p.substring(oldPath.length))
                        }
                        val updated = expanded.map {
                            if (isInside(it, oldPath)) newPath + it.substring(oldPath.length) else it
                        }
                        expanded.clear()
                        expanded.addAll(updated)
                        if (isInside(selectedDir.absolutePath, oldPath)) {
                            selectedDir = File(newPath + selectedDir.absolutePath.substring(oldPath.length))
                        }
                        treeVersion++
                        dialog = null
                    }
                    .onFailure { toast(it.message ?: "Couldn't rename") }
            },
            onDismiss = { dialog = null },
        )

        is ExplorerDialog.Delete -> ConfirmDialog(
            title = "Delete \"${d.file.name}\"?",
            message = if (d.file.isDirectory) "The folder and everything inside it will be deleted. This can't be undone."
            else "This can't be undone.",
            confirmText = "Delete",
            onConfirm = {
                val path = d.file.absolutePath
                docs.filter { isInside(it.file.absolutePath, path) }.forEach {
                    it.dirty = false
                    docs.remove(it)
                }
                val current = activeDoc
                if (current != null && current !in docs) activeDoc = docs.lastOrNull()
                if (ProjectFiles.delete(d.file)) {
                    expanded.removeAll { isInside(it, path) }
                    if (isInside(selectedDir.absolutePath, path)) selectedDir = projectDir
                } else {
                    toast("Couldn't delete ${d.file.name}")
                }
                treeVersion++
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

/** Saves every changed file shortly after you stop typing. */
@Composable
private fun AutoSave(docs: List<OpenDoc>) {
    val tick = docs.sumOf { it.version }
    LaunchedEffect(tick) {
        delay(700)
        withContext(Dispatchers.IO) { docs.toList().forEach { it.saveIfDirty() } }
    }
}

// ---------------------------------------------------------------- side menu

/** The menu that slides in from the left when you tap the three lines. */
@Composable
private fun SideMenu(open: Boolean, dark: Boolean, onToggleDark: () -> Unit, onClose: () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }

    // dimmed background; tapping it closes the menu
    AnimatedVisibility(visible = open, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = noRipple, indication = null, onClick = onClose)
        )
    }

    // the panel itself
    AnimatedVisibility(
        visible = open,
        enter = slideInHorizontally(animationSpec = tween(260)) { fullWidth -> -fullWidth },
        exit = slideOutHorizontally(animationSpec = tween(220)) { fullWidth -> -fullWidth },
    ) {
        val shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
        Column(
            Modifier
                .fillMaxHeight()
                .width(290.dp)
                .clip(shape)
                .background(SbSidebar)
                .border(1.dp, SbCardBorder, shape)
                .clickable(interactionSource = noRipple, indication = null, onClick = {}) // keeps taps inside the panel
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark(44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = SbAccent)) { append("SB") }
                            withStyle(SpanStyle(color = SbText)) { append("Code") }
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Menu", color = SbTextDim, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            MenuSwitchRow(
                icon = SbIcons.Moon,
                title = "Dark mode",
                subtitle = if (dark) "On. Tap to go back to the default color" else "Off. The default navy color",
                checked = dark,
                onToggle = onToggleDark,
            )
        }
    }
}

@Composable
private fun MenuSwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SbCard)
            .border(1.dp, SbCardBorder, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SbIcon(icon, if (checked) SbAccent else SbTextDim, 24.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = SbText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = SbTextDim, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SbPrimary,
                uncheckedThumbColor = SbTextDim,
                uncheckedTrackColor = SbPanel,
                uncheckedBorderColor = SbCardBorder,
            ),
        )
    }
}

// ---------------------------------------------------------------- real keyboard (OTG)

private fun hasRealKeyboard(): Boolean = InputDevice.getDeviceIds().any { id ->
    val d = InputDevice.getDevice(id)
    d != null && !d.isVirtual &&
        d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
        (d.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
}

/** True while a real (physical, for example OTG) keyboard is connected. Updates when it is plugged or unplugged. */
@Composable
private fun rememberHasHardwareKeyboard(): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var connected by remember { mutableStateOf(hasRealKeyboard()) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                connected = hasRealKeyboard()
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                connected = hasRealKeyboard()
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                connected = hasRealKeyboard()
            }
        }
        manager.registerInputDeviceListener(listener, null)
        onDispose { manager.unregisterInputDeviceListener(listener) }
    }
    // The system's own report is used as a second opinion.
    val reportedByConfig = configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
        configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    return connected || reportedByConfig
}

// ---------------------------------------------------------------- top bar

@Composable
private fun TopBar(
    docs: List<OpenDoc>,
    activeDoc: OpenDoc?,
    explorerVisible: Boolean,
    logVisible: Boolean,
    showActions: Boolean,
    onSelect: (OpenDoc) -> Unit,
    onCloseDoc: (OpenDoc) -> Unit,
    onToggleExplorer: () -> Unit,
    onToggleLog: () -> Unit,
    onRun: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The three-line button opens the side menu.
        IconAction(SbIcons.Menu, onClick = onMenu, tint = SbText, boxSize = 40.dp, iconSize = 24.dp)
        Spacer(Modifier.width(6.dp))
        SbIcon(SbIcons.Code, SbAccent, 26.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = SbAccent)) { append("SB") }
                withStyle(SpanStyle(color = SbText)) { append("Code") }
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(14.dp))
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items(docs, key = { it.file.absolutePath }) { d ->
                TabItem(d, selected = d === activeDoc, onSelect = { onSelect(d) }, onClose = { onCloseDoc(d) })
            }
        }
        if (showActions) {
            ToolbarButtons(explorerVisible, logVisible, onToggleExplorer, onToggleLog, onRun)
        }
    }
}

/** Explorer, LOG and Run buttons. Run is always the last one on the right. */
@Composable
private fun ToolbarButtons(
    explorerVisible: Boolean,
    logVisible: Boolean,
    onToggleExplorer: () -> Unit,
    onToggleLog: () -> Unit,
    onRun: () -> Unit,
) {
    IconAction(
        SbIcons.Folder, onToggleExplorer,
        tint = if (explorerVisible) SbAccent else SbTextDim, boxSize = 40.dp, iconSize = 24.dp
    )
    Spacer(Modifier.width(6.dp))
    IconAction(
        SbIcons.Terminal, onToggleLog,
        tint = if (logVisible) SbAccent else SbTextDim, boxSize = 40.dp, iconSize = 24.dp
    )
    Spacer(Modifier.width(6.dp))
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(SbPrimaryDeep, SbPrimary)))
            .clickable(onClick = onRun),
        contentAlignment = Alignment.Center,
    ) { SbIcon(SbIcons.Play, Color.White, 26.dp) }
}

@Composable
private fun TabItem(doc: OpenDoc, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    Row(
        Modifier
            .height(42.dp)
            .clip(shape)
            .background(if (selected) SbPanel else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, SbCardBorder, shape) else Modifier)
            .clickable(onClick = onSelect)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileBadge(doc.file.name, 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            doc.file.name,
            color = if (selected) SbText else SbTextDim,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        Spacer(Modifier.width(2.dp))
        IconAction(SbIcons.Close, onClose, boxSize = 30.dp, iconSize = 16.dp)
    }
}

// ---------------------------------------------------------------- editor area

@Composable
private fun EditorPane(modifier: Modifier, doc: OpenDoc?, projectEmpty: Boolean, showHelper: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(SbEditorBg)
            .border(1.dp, SbCardBorder, shape)
    ) {
        if (doc == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    SbIcon(SbIcons.Code, SbTextMuted, 40.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (projectEmpty) "This project is empty" else "No file is open",
                        color = SbText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (projectEmpty) "Tap the new file button in the Explorer and create index.html."
                        else "Pick a file in the Explorer to start editing.",
                        color = SbTextDim, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            CodeEditor(doc, Modifier.weight(1f).fillMaxWidth())
            if (showHelper) HelperBar(doc)
        }
    }
}

// ---------------------------------------------------------------- explorer

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Explorer(
    modifier: Modifier,
    projectName: String,
    nodes: List<TreeNode>,
    expanded: List<String>,
    activeFile: File?,
    onTapDir: (File) -> Unit,
    onTapFile: (File) -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onRefresh: () -> Unit,
    onCollapseAll: () -> Unit,
    onAction: (ExplorerDialog) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    var projectMenu by remember { mutableStateOf(false) }

    Column(
        modifier
            .clip(shape)
            .background(SbPanel)
            .border(1.dp, SbCardBorder, shape)
            .padding(10.dp)
    ) {
        Text(
            "EXPLORER", color = SbTextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp, modifier = Modifier.padding(start = 6.dp, top = 2.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SbIcon(SbIcons.ExpandMore, SbText, 18.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                projectName.uppercase(), color = SbText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            IconAction(SbIcons.CreateNewFolder, onNewFolder, tint = SbAccent, boxSize = 34.dp, background = SbCard)
            Spacer(Modifier.width(4.dp))
            IconAction(SbIcons.NoteAdd, onNewFile, tint = SbAccent, boxSize = 34.dp, background = SbCard)
            Box {
                IconAction(SbIcons.ExpandMore, { projectMenu = true }, boxSize = 28.dp, iconSize = 20.dp)
                DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                    DropdownMenuItem(text = { Text("Refresh") }, onClick = { projectMenu = false; onRefresh() })
                    DropdownMenuItem(text = { Text("Collapse all") }, onClick = { projectMenu = false; onCollapseAll() })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(nodes, key = { it.file.absolutePath }) { node ->
                TreeRow(
                    node = node,
                    isOpen = node.file.absolutePath in expanded,
                    selected = !node.isDir && node.file == activeFile,
                    onTap = { if (node.isDir) onTapDir(node.file) else onTapFile(node.file) },
                    onAction = onAction,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(
    node: TreeNode,
    isOpen: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onAction: (ExplorerDialog) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) SbPrimaryDeep else Color.Transparent)
                .combinedClickable(onClick = onTap, onLongClick = { menu = true })
                .padding(start = (6 + node.depth * 14).dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (node.isDir) {
                SbIcon(if (isOpen) SbIcons.ExpandMore else SbIcons.ChevronRight, SbTextDim, 18.dp)
                Spacer(Modifier.width(2.dp))
                SbIcon(if (isOpen) SbIcons.FolderOpen else SbIcons.Folder, Color(0xFF4D8DFF), 20.dp)
            } else {
                Spacer(Modifier.width(20.dp))
                FileBadge(node.file.name, 20.dp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                node.file.name,
                color = if (selected) Color.White else SbText,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (node.isDir) {
                DropdownMenuItem(text = { Text("New file here") }, onClick = { menu = false; onAction(ExplorerDialog.NewFile(node.file)) })
                DropdownMenuItem(text = { Text("New folder here") }, onClick = { menu = false; onAction(ExplorerDialog.NewFolder(node.file)) })
            }
            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onAction(ExplorerDialog.Rename(node.file)) })
            DropdownMenuItem(text = { Text("Delete", color = SbRed) }, onClick = { menu = false; onAction(ExplorerDialog.Delete(node.file)) })
        }
    }
}

// ---------------------------------------------------------------- log

@Composable
private fun LogPanel(modifier: Modifier, logs: List<LogLine>, onClose: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }
    Column(
        modifier
            .clip(shape)
            .background(SbPanel)
            .border(1.dp, SbCardBorder, shape)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SbIcon(SbIcons.Terminal, SbAccent, 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("LOG", color = SbText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconAction(SbIcons.Close, onClose, boxSize = 28.dp, iconSize = 16.dp)
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(state = listState) {
            items(logs) { l ->
                val color = when (l.kind) {
                    LogKind.Info -> SbTextDim
                    LogKind.Success -> SbGreen
                    LogKind.Error -> SbRed
                }
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = SbTextMuted)) { append("[${l.time}] ") }
                        withStyle(SpanStyle(color = color)) { append(l.text) }
                    },
                    fontFamily = CodeFont, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            item { Text("_", color = SbAccent, fontFamily = CodeFont, fontSize = 12.sp) }
        }
    }
}
