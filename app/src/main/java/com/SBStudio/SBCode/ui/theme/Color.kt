package com.SBStudio.SBCode.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Dark mode switch (opened from the three-line menu). Off = the default navy of the app. */
object SbPalette {
    var dark by mutableStateOf(false)
        private set

    private const val PREFS = "sbcode"
    private const val KEY = "dark_mode"

    fun load(context: Context) {
        dark = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun setDark(context: Context, value: Boolean) {
        dark = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }
}

// Palette taken from the SBCode concept panels (deep navy with blue/violet accents).
// The background colors below change in dark mode; everything else stays the same.
val SbBgTop: Color
    get() = if (SbPalette.dark) Color(0xFF0B0B10) else Color(0xFF0B1238)
val SbBg: Color
    get() = if (SbPalette.dark) Color(0xFF050507) else Color(0xFF070B26)
val SbSidebar: Color
    get() = if (SbPalette.dark) Color(0xFF08080C) else Color(0xFF0A1136)
val SbCard: Color
    get() = if (SbPalette.dark) Color(0xFF111118) else Color(0xFF0D1544)
val SbCardBorder: Color
    get() = if (SbPalette.dark) Color(0xFF24242F) else Color(0xFF1D2B6E)
val SbPanel: Color
    get() = if (SbPalette.dark) Color(0xFF0C0C12) else Color(0xFF0A1240)
val SbEditorBg: Color
    get() = if (SbPalette.dark) Color(0xFF000000) else Color(0xFF070C2E)

val SbPrimary = Color(0xFF3D5AFE)
val SbPrimaryDeep = Color(0xFF2A2FC9)
val SbAccent = Color(0xFF4E6BFF)

val SbText = Color(0xFFFFFFFF)
val SbTextDim = Color(0xFF8FA2E6)
val SbTextMuted = Color(0xFF5B6BB0)

val SbHtml = Color(0xFFB23A2C)
val SbCss = Color(0xFF1F55B8)
val SbJs = Color(0xFFD0A12A)

val SbIconBlue = Color(0xFF1565E0)
val SbIconPurple = Color(0xFF4A32C8)
val SbIconTeal = Color(0xFF17A2A6)

val SbGreen = Color(0xFF3DE08A)
val SbRed = Color(0xFFFF6B6B)

// Code colors
val CodeTag = Color(0xFF4DB2FF)
val CodeAttr = Color(0xFF7DD3FC)
val CodeString = Color(0xFFFF7B72)
val CodeComment = Color(0xFF6C7BB5)
val CodeKeyword = Color(0xFFC49BFF)
val CodeNumber = Color(0xFFFFB86C)
val CodeFunction = Color(0xFFFFD866)
val CodeSelector = Color(0xFFE5C07B)
