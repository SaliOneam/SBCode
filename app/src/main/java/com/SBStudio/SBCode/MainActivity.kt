package com.SBStudio.SBCode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.SBStudio.SBCode.data.Storage
import com.SBStudio.SBCode.server.ServerService
import com.SBStudio.SBCode.ui.EditorScreen
import com.SBStudio.SBCode.ui.PermissionScreen
import com.SBStudio.SBCode.ui.ProjectsScreen
import com.SBStudio.SBCode.ui.theme.SBCodeTheme
import com.SBStudio.SBCode.ui.theme.SbPalette
import com.SBStudio.SBCode.ui.theme.SbBg
import com.SBStudio.SBCode.ui.theme.SbBgTop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        SbPalette.load(this)
        setContent {
            SBCodeTheme {
                AppRoot(onExit = { finish() })
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        if (isFinishing) ServerService.stop(this)
        super.onDestroy()
    }

    /** Game-style full screen. Swipe from the edge to show the system bars for a moment. */
    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun AppRoot(onExit: () -> Unit) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(Storage.hasAccess(context)) }
    var openPath by rememberSaveable { mutableStateOf<String?>(null) }

    // Re-check the permission when the user comes back from the settings page.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAccess = Storage.hasAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // As soon as access is granted, create SBCode/Projects and SBCode/System.
    LaunchedEffect(hasAccess) {
        if (hasAccess) withContext(Dispatchers.IO) { Storage.ensureRoot() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SbBgTop, SbBg)))
            .safeDrawingPadding()
    ) {
        val path = openPath
        when {
            !hasAccess -> PermissionScreen()
            path == null -> ProjectsScreen(
                onOpenProject = { openPath = it.absolutePath },
                onExit = onExit,
            )
            else -> EditorScreen(projectDir = File(path), onClose = { openPath = null })
        }
    }
}
