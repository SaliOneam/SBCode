package com.SBStudio.SBCode.ui

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.SBStudio.SBCode.R
import com.SBStudio.SBCode.data.ProjectFiles
import com.SBStudio.SBCode.data.ProjectInfo
import com.SBStudio.SBCode.data.Storage
import com.SBStudio.SBCode.ui.theme.SbAccent
import com.SBStudio.SBCode.ui.theme.SbCard
import com.SBStudio.SBCode.ui.theme.SbCardBorder
import com.SBStudio.SBCode.ui.theme.SbCss
import com.SBStudio.SBCode.ui.theme.SbHtml
import com.SBStudio.SBCode.ui.theme.SbIconBlue
import com.SBStudio.SBCode.ui.theme.SbIconPurple
import com.SBStudio.SBCode.ui.theme.SbIconTeal
import com.SBStudio.SBCode.ui.theme.SbJs
import com.SBStudio.SBCode.ui.theme.SbPrimary
import com.SBStudio.SBCode.ui.theme.SbPrimaryDeep
import com.SBStudio.SBCode.ui.theme.SbSidebar
import com.SBStudio.SBCode.ui.theme.SbText
import com.SBStudio.SBCode.ui.theme.SbTextDim
import com.SBStudio.SBCode.ui.theme.SbTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------- permission

@Composable
fun PermissionScreen() {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) denied = true
    }
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(24.dp))
                .background(SbCard)
                .border(1.dp, SbCardBorder, RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LogoMark(56.dp)
            Spacer(Modifier.height(16.dp))
            Text("Allow access to your files", color = SbText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "SBCode keeps your projects in a folder named SBCode on your phone, " +
                    "so you can also open them with any file manager. Android needs your permission to create that folder.",
                color = SbTextDim,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            if (modern) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You'll be taken to a settings page. Turn on the switch for SBCode, then come back.",
                    color = SbTextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (modern) {
                        Storage.openAllFilesAccessSettings(context)
                    } else {
                        launcher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SbPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Allow access", fontSize = 15.sp) }
            if (denied && !modern) {
                TextButton(onClick = { Storage.openAppSettings(context) }) {
                    Text("Open app settings", color = SbAccent)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- panel 1

private enum class ProjectsDialog { New, Open, Creator }

@Composable
fun ProjectsScreen(onOpenProject: (File) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<ProjectInfo>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf<ProjectsDialog?>(null) }

    LaunchedEffect(Unit) {
        projects = withContext(Dispatchers.IO) {
            Storage.ensureRoot()
            ProjectFiles.listProjects(Storage.projectsDir())
        }
        loaded = true
    }

    val shown = if (query.isBlank()) projects.take(10) else projects.filter { it.name.contains(query.trim(), ignoreCase = true) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    if (maxHeight > maxWidth) {
        PortraitProjects(
            shown = shown,
            loaded = loaded,
            noProjects = projects.isEmpty(),
            query = query,
            onQuery = { query = it },
            onNew = { dialog = ProjectsDialog.New },
            onOpen = { dialog = ProjectsDialog.Open },
            onCreator = { dialog = ProjectsDialog.Creator },
            onExit = onExit,
            onOpenProject = onOpenProject,
            wide = maxWidth >= 412.dp,
        )
    } else {
    Row(Modifier.fillMaxSize()) {
        Sidebar(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight(),
            onNew = { dialog = ProjectsDialog.New },
            onOpen = { dialog = ProjectsDialog.Open },
            onCreator = { dialog = ProjectsDialog.Creator },
            onExit = onExit,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = SbText)) { append("Recent ") }
                            withStyle(SpanStyle(color = SbAccent)) { append("Projects") }
                        },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Continue where you left off", color = SbTextDim, fontSize = 13.sp, letterSpacing = 0.5.sp)
                }
                SearchBox(query = query, onChange = { query = it }, modifier = Modifier.width(260.dp))
            }
            Spacer(Modifier.height(16.dp))

            when {
                loaded && projects.isEmpty() -> EmptyMessage(
                    "No projects yet",
                    "Tap New Project to create your first one.",
                )
                loaded && shown.isEmpty() -> EmptyMessage("No match", "No project is named like \"${query.trim()}\".")
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    itemsIndexed(shown, key = { _, p -> p.dir.absolutePath }) { _, p ->
                        ProjectCard(p) { onOpenProject(p.dir) }
                    }
                }
            }
        }
    }
    }
    }

    when (dialog) {
        ProjectsDialog.New -> NameDialog(
            title = "New project",
            label = "Project name",
            confirmText = "Create",
            validate = {
                ProjectFiles.validateName(it, Storage.projectsDir(), "A project with this name already exists")
            },
            onConfirm = { name ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        Storage.ensureRoot()
                        ProjectFiles.createProject(Storage.projectsDir(), name)
                    }
                    result
                        .onSuccess {
                            dialog = null
                            onOpenProject(it.dir)
                        }
                        .onFailure {
                            Toast.makeText(context, it.message ?: "Couldn't create the project", Toast.LENGTH_LONG).show()
                        }
                }
            },
            onDismiss = { dialog = null },
        )

        ProjectsDialog.Open -> AllProjectsDialog(
            projects = projects,
            onPick = {
                dialog = null
                onOpenProject(it.dir)
            },
            onDismiss = { dialog = null },
        )

        ProjectsDialog.Creator -> InfoDialog(
            title = "Creator",
            message = "SBCode is made by SBStudio.\n\nAn offline editor for building websites with HTML, CSS and JS on your phone.",
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

@Composable
private fun EmptyMessage(title: String, subtitle: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SbIcon(SbIcons.Folder, SbTextMuted, 40.dp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = SbText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = SbTextDim, fontSize = 13.sp)
        }
    }
}

@Composable
fun LogoMark(size: Dp) {
    // The app logo (res/drawable/icon1.png) in a rounded square.
    Image(
        painter = painterResource(R.drawable.icon1),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f)),
    )
}

@Composable
private fun Sidebar(
    modifier: Modifier,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onCreator: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(SbSidebar)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(46.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = SbAccent)) { append("SB") }
                        withStyle(SpanStyle(color = SbText)) { append("Code") }
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("HTML \u2022 CSS \u2022 JS", color = SbTextDim, fontSize = 10.sp, letterSpacing = 1.5.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        SideItem(SbIcons.NoteAdd, "New Project", highlighted = true, onClick = onNew)
        Spacer(Modifier.height(6.dp))
        SideItem(SbIcons.FolderOpen, "Open Project", highlighted = false, onClick = onOpen)
        Spacer(Modifier.height(6.dp))
        SideItem(SbIcons.Person, "Creator", highlighted = false, onClick = onCreator)
        Spacer(Modifier.height(6.dp))
        SideItem(SbIcons.Logout, "Exit", highlighted = false, onClick = onExit)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SbCard)
                    .border(1.dp, SbCardBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) { SbIcon(SbIcons.Code, SbAccent, 24.dp) }
            Spacer(Modifier.width(12.dp))
            Text("BUILD\nSOMETHING\nGREAT", color = SbTextMuted, fontSize = 10.sp, letterSpacing = 2.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun SideItem(
    icon: ImageVector,
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    iconSize: Dp = 24.dp,
    fontSize: TextUnit = 15.sp,
    vPad: Dp = 12.dp,
    hPad: Dp = 14.dp,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (highlighted) Modifier.background(Brush.horizontalGradient(listOf(SbPrimaryDeep, SbPrimary)), shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = hPad, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SbIcon(icon, if (highlighted) Color.White else SbTextDim, iconSize)
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (highlighted) Color.White else SbTextDim, fontSize = fontSize, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        SbIcon(SbIcons.ChevronRight, if (highlighted) Color.White else SbTextMuted, iconSize * 0.8f)
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = SbText, fontSize = 14.sp),
        cursorBrush = SolidColor(SbText),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(shape)
                    .background(SbCard)
                    .border(1.dp, SbCardBorder, shape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SbIcon(SbIcons.Search, SbTextDim, 22.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search projects...", color = SbTextMuted, fontSize = 14.sp)
                    inner()
                }
            }
        },
    )
}

@Composable
private fun ProjectCard(p: ProjectInfo, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val iconColor = listOf(SbIconBlue, SbIconPurple, SbIconTeal)[Math.floorMod(p.name.hashCode(), 3)]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SbCard)
            .border(1.dp, SbCardBorder, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center,
        ) { SbIcon(SbIcons.File, Color.White, 26.dp) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = SbText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SbIcon(SbIcons.Folder, SbTextDim, 14.dp)
                Spacer(Modifier.width(6.dp))
                Text("/${Storage.ROOT_NAME}/${Storage.PROJECTS_NAME}", color = SbTextDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (p.hasHtml) TagChip("HTML", SbHtml)
                if (p.hasCss) TagChip("CSS", SbCss)
                if (p.hasJs) TagChip("JS", SbJs)
                if (!p.hasHtml && !p.hasCss && !p.hasJs) Text("Empty project", color = SbTextMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SbIcon(SbIcons.Clock, SbTextDim, 16.dp)
            Spacer(Modifier.width(6.dp))
            Text("Last modified ${ProjectFiles.timeAgo(p.lastModified)}", color = SbTextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        SbIcon(SbIcons.ChevronRight, SbTextDim, 24.dp)
    }
}

// ---------------------------------------------------------------- portrait layout

@Composable
private fun PortraitProjects(
    shown: List<ProjectInfo>,
    loaded: Boolean,
    noProjects: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onCreator: () -> Unit,
    onExit: () -> Unit,
    onOpenProject: (File) -> Unit,
    wide: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark(84.dp)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = SbAccent)) { append("SB") }
                            withStyle(SpanStyle(color = SbText)) { append("Code") }
                        },
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("HTML \u2022 CSS \u2022 JS", color = SbTextDim, fontSize = 12.sp, letterSpacing = 2.sp)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SideItem(SbIcons.NoteAdd, "New Project", highlighted = true, onClick = onNew, iconSize = 30.dp, fontSize = 20.sp, vPad = 16.dp, hPad = 18.dp)
                SideItem(SbIcons.FolderOpen, "Open Project", highlighted = false, onClick = onOpen, iconSize = 30.dp, fontSize = 20.sp, vPad = 16.dp, hPad = 18.dp)
                SideItem(SbIcons.Person, "Creator", highlighted = false, onClick = onCreator, iconSize = 30.dp, fontSize = 20.sp, vPad = 16.dp, hPad = 18.dp)
                SideItem(SbIcons.Logout, "Exit", highlighted = false, onClick = onExit, iconSize = 30.dp, fontSize = 20.sp, vPad = 16.dp, hPad = 18.dp)
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 380.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { RecentTitle() }
                        Spacer(Modifier.width(10.dp))
                        SearchBox(query = query, onChange = onQuery, modifier = Modifier.width(170.dp))
                    }
                } else {
                    Column {
                        RecentTitle()
                        Spacer(Modifier.height(10.dp))
                        SearchBox(query = query, onChange = onQuery, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (loaded && noProjects) {
            item {
                EmptyMessage(
                    "No projects yet",
                    "Tap New Project to create your first one.",
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                )
            }
        } else if (loaded && shown.isEmpty()) {
            item {
                EmptyMessage(
                    "No match",
                    "No project is named like \"${query.trim()}\".",
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                )
            }
        } else {
            items(shown, key = { it.dir.absolutePath }) { p ->
                PortraitCard(p, wide) { onOpenProject(p.dir) }
            }
        }
    }
}

@Composable
private fun RecentTitle() {
    Column {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = SbText)) { append("Recent ") }
                withStyle(SpanStyle(color = SbAccent)) { append("Projects") }
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("Continue where you left off", color = SbTextDim, fontSize = 12.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun SmallTag(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PortraitCard(p: ProjectInfo, wide: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val iconColor = listOf(SbIconBlue, SbIconPurple, SbIconTeal)[Math.floorMod(p.name.hashCode(), 3)]
    val tags: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p.hasHtml) SmallTag("HTML", SbHtml)
            if (p.hasCss) SmallTag("CSS", SbCss)
            if (p.hasJs) SmallTag("JS", SbJs)
            if (!p.hasHtml && !p.hasCss && !p.hasJs) Text("Empty project", color = SbTextMuted, fontSize = 11.sp)
        }
    }
    val time: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SbIcon(SbIcons.Clock, SbTextDim, 14.dp)
            Spacer(Modifier.width(5.dp))
            Text(
                "Last modified ${ProjectFiles.timeAgo(p.lastModified)}",
                color = SbTextDim, fontSize = 10.sp, maxLines = 1,
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SbCard)
            .border(1.dp, SbCardBorder, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center,
        ) { SbIcon(SbIcons.File, Color.White, 26.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = SbText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SbIcon(SbIcons.Folder, SbTextDim, 14.dp)
                Spacer(Modifier.width(6.dp))
                Text("/${Storage.ROOT_NAME}/${Storage.PROJECTS_NAME}", color = SbTextDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (wide) {
                // Wider phones: the time sits on the right of the tags, like in the design.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    tags()
                    Spacer(Modifier.weight(1f))
                    time()
                }
            } else {
                // Narrow phones: not enough room next to the tags, so the time goes underneath.
                tags()
                Spacer(Modifier.height(6.dp))
                time()
            }
        }
        Spacer(Modifier.width(6.dp))
        SbIcon(SbIcons.ChevronRight, SbTextDim, 22.dp)
    }
}

@Composable
private fun AllProjectsDialog(projects: List<ProjectInfo>, onPick: (ProjectInfo) -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(shape)
                .background(SbCard)
                .border(1.dp, SbCardBorder, shape)
                .padding(18.dp)
        ) {
            Text("Open project", color = SbText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            if (projects.isEmpty()) {
                Text("There are no projects in SBCode/Projects yet.", color = SbTextDim, fontSize = 14.sp)
            } else {
                LazyColumn(Modifier.heightIn(max = 230.dp)) {
                    items(projects, key = { it.dir.absolutePath }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(p) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SbIcon(SbIcons.Folder, SbAccent, 22.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(p.name, color = SbText, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ProjectFiles.timeAgo(p.lastModified), color = SbTextDim, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Close", color = SbTextDim) }
            }
        }
    }
}
