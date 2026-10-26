package com.SBStudio.SBCode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.SBStudio.SBCode.ui.theme.SbCard
import com.SBStudio.SBCode.ui.theme.SbCardBorder
import com.SBStudio.SBCode.ui.theme.SbPrimary
import com.SBStudio.SBCode.ui.theme.SbPrimaryDeep
import com.SBStudio.SBCode.ui.theme.SbRed
import com.SBStudio.SBCode.ui.theme.SbText
import com.SBStudio.SBCode.ui.theme.SbTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

/** This Project = the phone that owns the project. Code Display = the phone that is only a code screen. */
enum class MultiDisplayMode { ThisProject, CodeDisplay }

/**
 * Finds out whether this phone is on a local network (Wi-Fi, its own hotspot, or a cable).
 * It looks at the network connections of the phone itself, so it needs no permission and no internet.
 */
object LocalNetwork {
    // Wi-Fi, hotspot and tethering use these names. Mobile data (rmnet, ccmni...) is left out on purpose.
    private val wantedPrefixes = listOf("wlan", "swlan", "ap", "eth", "rndis")

    internal fun usableName(name: String): Boolean {
        val n = name.lowercase()
        return wantedPrefixes.any { n.startsWith(it) }
    }

    /** The address of this phone on the local network (for example 192.168.1.20), or null if there is none. */
    fun localAddress(): String? = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && usableName(it.name) }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }

    fun isAvailable(): Boolean = localAddress() != null
}

/** True while this phone is on a local network. It re-checks every two seconds while [active] is true. */
@Composable
fun rememberHasLocalNetwork(active: Boolean): Boolean {
    var available by remember { mutableStateOf(LocalNetwork.isAvailable()) }
    LaunchedEffect(active) {
        while (active) {
            available = withContext(Dispatchers.IO) { LocalNetwork.isAvailable() }
            delay(2000)
        }
    }
    return available
}

/**
 * The panel that explains Multi Display. It fades and grows in softly, and the screen behind it is
 * blurred (the caller applies the blur, see EditorScreen and ProjectsScreen).
 * Only one button is shown, depending on [mode]. It turns gray when there is no local network.
 */
@Composable
fun MultiDisplayPanel(
    visible: Boolean,
    mode: MultiDisplayMode,
    onClose: () -> Unit,
    onChoose: () -> Unit,
) {
    val networkOk = rememberHasLocalNetwork(active = visible)
    val noRipple = remember { MutableInteractionSource() }
    // Registered after the screen's own Back handlers, so Back closes this panel first.
    BackHandler(enabled = visible, onBack = onClose)

    // dimmed background; tapping it closes the panel
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(250)), exit = fadeOut(tween(220))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.40f))
                .clickable(interactionSource = noRipple, indication = null, onClick = onClose)
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.9f, animationSpec = tween(250)),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f, animationSpec = tween(200)),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .clip(shape)
                    .background(SbCard)
                    .border(1.dp, SbCardBorder, shape)
                    .clickable(interactionSource = noRipple, indication = null, onClick = {}) // keeps taps inside the panel
                    .padding(18.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                ) {
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(end = 44.dp)
                    ) {
                        Text("Multi Display", color = SbText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        val lines = if (mode == MultiDisplayMode.ThisProject) thisProjectText else codeDisplayText
                        lines.forEachIndexed { i, line ->
                            if (i > 0) Spacer(Modifier.height(8.dp))
                            Text(line, color = SbTextDim, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    // red close button, top right
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SbRed)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { SbIcon(SbIcons.Close, Color(0xFF111111), 18.dp) }
                }
                Spacer(Modifier.height(14.dp))
                if (!networkOk) {
                    Text(
                        "No Wi-Fi or hotspot found. Connect both phones to the same network first.",
                        color = SbRed,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                val label = if (mode == MultiDisplayMode.ThisProject) "This Project" else "Code Display"
                val buttonShape = RoundedCornerShape(16.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(buttonShape)
                        .background(
                            if (networkOk) Brush.linearGradient(listOf(SbPrimaryDeep, SbPrimary))
                            else Brush.linearGradient(listOf(Color(0xFF3A3A44), Color(0xFF3A3A44)))
                        )
                        .then(if (networkOk) Modifier.clickable(onClick = onChoose) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (networkOk) Color.White else Color(0xFF8A8A93),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private val thisProjectText = listOf(
    "Use a second phone as the code screen for this project.",
    "1. Put SBCode on both phones and connect them to the same Wi-Fi, or turn on a hotspot on one of them.",
    "2. Tap This Project below. A pairing code will show up.",
    "3. On the other phone open SBCode, tap Multi Display, then Code Display, and type the code.",
    "This phone keeps the Explorer and LOG. Whatever you type on the other phone goes into this project live.",
)

private val codeDisplayText = listOf(
    "Turn this phone into a full-screen code editor for a project that is open on another phone.",
    "1. Put SBCode on both phones and connect them to the same Wi-Fi, or turn on a hotspot on one of them.",
    "2. On the other phone open your project, tap the menu, then Multi Display, then This Project.",
    "3. Tap Code Display below and type the pairing code you see on the other phone.",
    "What you type here is saved live into the project on the other phone.",
)
