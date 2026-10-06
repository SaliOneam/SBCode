package com.SBStudio.SBCode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.SBStudio.SBCode.data.ProjectFiles
import com.SBStudio.SBCode.ui.theme.CodeAttr
import com.SBStudio.SBCode.ui.theme.CodeComment
import com.SBStudio.SBCode.ui.theme.CodeFont
import com.SBStudio.SBCode.ui.theme.CodeFunction
import com.SBStudio.SBCode.ui.theme.CodeKeyword
import com.SBStudio.SBCode.ui.theme.CodeNumber
import com.SBStudio.SBCode.ui.theme.CodeSelector
import com.SBStudio.SBCode.ui.theme.CodeString
import com.SBStudio.SBCode.ui.theme.CodeTag
import com.SBStudio.SBCode.ui.theme.SbCard
import com.SBStudio.SBCode.ui.theme.SbCardBorder
import com.SBStudio.SBCode.ui.theme.SbPanel
import com.SBStudio.SBCode.ui.theme.SbPrimaryDeep
import com.SBStudio.SBCode.ui.theme.SbText
import com.SBStudio.SBCode.ui.theme.SbTextDim
import com.SBStudio.SBCode.ui.theme.SbTextMuted
import java.io.File
import kotlin.math.roundToInt

// ---------------------------------------------------------------- document

/** One open file: its text, cursor, undo/redo history, suggestions and "needs saving" flag. */
class OpenDoc(initialFile: File, text: String) {
    var file by mutableStateOf(initialFile)

    var value by mutableStateOf(TextFieldValue(text))
        private set

    /** Goes up on every text change; the autosave watches it. */
    var version by mutableIntStateOf(0)
        private set

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** The suggestions shown right now (null = none). */
    var suggestionResult by mutableStateOf<SuggestionResult?>(null)
        private set
    var suggestionIndex by mutableIntStateOf(0)
        private set

    @Volatile
    var dirty = false

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var lastPush = 0L

    fun onValueChange(new: TextFieldValue) {
        val old = value
        if (new.text == old.text) {
            value = new // only the cursor/selection moved
            if (new.selection != old.selection) clearSuggestions()
            return
        }
        var next = new
        val lang = Lang.forFile(file.name)

        // Only a single typed character gets the smart treatment below.
        if (new.text.length == old.text.length + 1 && new.selection.collapsed && old.selection.collapsed) {
            val pos = new.selection.start
            if (pos > 0 && old.selection.start == pos - 1) {
                val typed = new.text[pos - 1]
                if (typed == '\n') {
                    // Enter keeps the indentation of the line above (and adds more after "{").
                    val lineStart = new.text.lastIndexOf('\n', pos - 2).let { if (it < 0) 0 else it + 1 }
                    val prevLine = new.text.substring(lineStart, pos - 1)
                    var indent = prevLine.takeWhile { it == ' ' || it == '\t' }
                    if (prevLine.trimEnd().endsWith("{")) indent += "    "
                    if (indent.isNotEmpty()) {
                        val t = new.text.substring(0, pos) + indent + new.text.substring(pos)
                        next = TextFieldValue(t, TextRange(pos + indent.length))
                    }
                } else if (typed == '>' && lang == Lang.HTML) {
                    // Typing ">" after <div adds </div> and keeps the cursor in between (like VS Code).
                    val closing = Suggestions.closingTagFor(new.text, pos)
                    if (closing != null) {
                        val t = new.text.substring(0, pos) + closing + new.text.substring(pos)
                        next = TextFieldValue(t, TextRange(pos))
                    }
                }
            }
        }

        commit(next, old)
        suggestionIndex = 0
        suggestionResult =
            if (next.selection.collapsed) Suggestions.compute(next.text, next.selection.start, lang) else null
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(value)
        value = undoStack.removeLast()
        lastPush = 0L
        markChanged()
        canUndo = undoStack.isNotEmpty()
        canRedo = true
        clearSuggestions()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(value)
        value = redoStack.removeLast()
        lastPush = 0L
        markChanged()
        canUndo = true
        canRedo = redoStack.isNotEmpty()
        clearSuggestions()
    }

    /** Types [s] at the cursor (replacing any selected text). */
    fun insert(s: String) {
        val v = value
        val sel = v.selection
        val t = v.text.replaceRange(sel.min, sel.max, s)
        onValueChange(TextFieldValue(t, TextRange(sel.min + s.length)))
    }

    // ---- suggestions

    fun moveSuggestion(delta: Int) {
        val n = suggestionResult?.items?.size ?: return
        if (n == 0) return
        suggestionIndex = ((suggestionIndex + delta) % n + n) % n
    }

    /** Writes the chosen suggestion into the text. Returns false when there was nothing to accept. */
    fun acceptSuggestion(index: Int = suggestionIndex): Boolean {
        val r = suggestionResult ?: return false
        val s = r.items.getOrNull(index) ?: return false
        val v = value
        val cursor = v.selection.start
        val start = r.replaceStart.coerceIn(0, cursor)
        val t = v.text.substring(0, start) + s.insert + v.text.substring(cursor)
        commit(TextFieldValue(t, TextRange(start + s.insert.length - s.cursorBack)), v, newUndoStep = true)
        clearSuggestions()
        return true
    }

    fun clearSuggestions() {
        if (suggestionResult != null) suggestionResult = null
        suggestionIndex = 0
    }

    fun saveIfDirty() {
        if (!dirty) return
        dirty = false
        if (ProjectFiles.writeText(file, value.text).isFailure) dirty = true
    }

    private fun commit(next: TextFieldValue, old: TextFieldValue, newUndoStep: Boolean = false) {
        val now = System.currentTimeMillis()
        if (newUndoStep || undoStack.isEmpty() || now - lastPush > 700) {
            undoStack.addLast(old)
            if (undoStack.size > 200) undoStack.removeFirst()
        }
        lastPush = if (newUndoStep) 0L else now
        redoStack.clear()
        value = next
        markChanged()
        canUndo = true
        canRedo = false
    }

    private fun markChanged() {
        dirty = true
        version++
    }
}

// ---------------------------------------------------------------- highlighting

object Highlighter {
    private val cTag = CodeTag.toArgb()
    private val cAttr = CodeAttr.toArgb()
    private val cString = CodeString.toArgb()
    private val cComment = CodeComment.toArgb()
    private val cKeyword = CodeKeyword.toArgb()
    private val cNumber = CodeNumber.toArgb()
    private val cFunction = CodeFunction.toArgb()
    private val cSelector = CodeSelector.toArgb()

    // HTML
    private val htmlTagRe = Regex("""<!--[\s\S]*?-->|<![A-Za-z][^>]*>|</?[A-Za-z][^>]*>""")
    private val htmlNameRe = Regex("""^</?[A-Za-z][\w:-]*""")
    private val htmlAttrRe = Regex("""\s([A-Za-z_:@][\w:.-]*)(?=\s*=)""")
    private val quotedRe = Regex("\"[^\"]*\"|'[^']*'")
    private val scriptRe = Regex("""<script\b[^>]*>([\s\S]*?)</script\s*>""", RegexOption.IGNORE_CASE)
    private val styleRe = Regex("""<style\b[^>]*>([\s\S]*?)</style\s*>""", RegexOption.IGNORE_CASE)

    // JS
    private val jsTokenRe = Regex("""//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`""")
    private val jsKeywordRe = Regex(
        """\b(?:const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|new|class|extends|import|export|from|default|try|catch|finally|throw|async|await|typeof|instanceof|in|of|this|null|undefined|true|false|void|delete|static|super|yield)\b"""
    )
    private val jsFuncRe = Regex("""\b([A-Za-z_]\w*)(?=\s*\()""")
    private val jsNumRe = Regex("""\b\d+(?:\.\d+)?\b""")

    // CSS
    private val cssTokenRe = Regex("""/\*[\s\S]*?\*/|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'""")
    private val cssPropRe = Regex("""([A-Za-z-]+)(?=\s*:)""")
    private val cssNumRe = Regex("""#[0-9A-Fa-f]{3,8}\b|-?\d*\.?\d+(?:%|px|em|rem|vh|vw|vmin|vmax|pt|ms|s|deg|fr)?""")
    private val cssAtRe = Regex("""@[A-Za-z-]+""")
    private val cssSelectorRe = Regex("""[^{}]+(?=\{)""")

    fun highlight(text: String, lang: Lang): AnnotatedString {
        val n = text.length
        if (lang == Lang.PLAIN || n == 0 || n > 300_000) return AnnotatedString(text)
        // One color per character. Rules run from weakest to strongest, so strings and comments always win.
        val colors = IntArray(n)
        when (lang) {
            Lang.HTML -> applyHtml(text, 0, colors)
            Lang.CSS -> applyCss(text, 0, colors)
            Lang.JS -> applyJs(text, 0, colors)
            Lang.PLAIN -> Unit
        }
        return buildAnnotatedString {
            append(text)
            var i = 0
            while (i < n) {
                val c = colors[i]
                var j = i + 1
                while (j < n && colors[j] == c) j++
                if (c != 0) addStyle(SpanStyle(color = Color(c)), i, j)
                i = j
            }
        }
    }

    private fun fill(colors: IntArray, from: Int, to: Int, argb: Int) {
        val a = from.coerceAtLeast(0)
        val b = to.coerceAtMost(colors.size)
        for (i in a until b) colors[i] = argb
    }

    private fun applyHtml(src: String, off: Int, colors: IntArray) {
        for (m in htmlTagRe.findAll(src)) {
            val s = off + m.range.first
            val tag = m.value
            if (tag.startsWith("<!--")) {
                fill(colors, s, s + tag.length, cComment)
                continue
            }
            if (tag.startsWith("<!")) {
                fill(colors, s, s + tag.length, cTag)
                continue
            }
            htmlNameRe.find(tag)?.let { fill(colors, s + it.range.first, s + it.range.last + 1, cTag) }
            for (a in htmlAttrRe.findAll(tag)) {
                val g = a.groups[1]?.range ?: continue
                fill(colors, s + g.first, s + g.last + 1, cAttr)
            }
            for (q in quotedRe.findAll(tag)) fill(colors, s + q.range.first, s + q.range.last + 1, cString)
            val endLen = if (tag.endsWith("/>")) 2 else 1
            fill(colors, s + tag.length - endLen, s + tag.length, cTag)
        }
        // Code inside <script> and <style> gets its own colors.
        for (m in scriptRe.findAll(src)) {
            val g = m.groups[1] ?: continue
            if (g.range.isEmpty()) continue
            val a = off + g.range.first
            fill(colors, a, off + g.range.last + 1, 0)
            applyJs(src.substring(g.range.first, g.range.last + 1), a, colors)
        }
        for (m in styleRe.findAll(src)) {
            val g = m.groups[1] ?: continue
            if (g.range.isEmpty()) continue
            val a = off + g.range.first
            fill(colors, a, off + g.range.last + 1, 0)
            applyCss(src.substring(g.range.first, g.range.last + 1), a, colors)
        }
    }

    private fun applyJs(src: String, off: Int, colors: IntArray) {
        for (m in jsNumRe.findAll(src)) fill(colors, off + m.range.first, off + m.range.last + 1, cNumber)
        for (m in jsFuncRe.findAll(src)) {
            val g = m.groups[1]?.range ?: continue
            fill(colors, off + g.first, off + g.last + 1, cFunction)
        }
        for (m in jsKeywordRe.findAll(src)) fill(colors, off + m.range.first, off + m.range.last + 1, cKeyword)
        for (m in jsTokenRe.findAll(src)) {
            val isComment = m.value.startsWith("//") || m.value.startsWith("/*")
            fill(colors, off + m.range.first, off + m.range.last + 1, if (isComment) cComment else cString)
        }
    }

    private fun applyCss(src: String, off: Int, colors: IntArray) {
        for (m in cssPropRe.findAll(src)) {
            val g = m.groups[1]?.range ?: continue
            fill(colors, off + g.first, off + g.last + 1, cAttr)
        }
        for (m in cssNumRe.findAll(src)) fill(colors, off + m.range.first, off + m.range.last + 1, cNumber)
        for (m in cssSelectorRe.findAll(src)) fill(colors, off + m.range.first, off + m.range.last + 1, cSelector)
        for (m in cssAtRe.findAll(src)) fill(colors, off + m.range.first, off + m.range.last + 1, cKeyword)
        for (m in cssTokenRe.findAll(src)) {
            val isComment = m.value.startsWith("/*")
            fill(colors, off + m.range.first, off + m.range.last + 1, if (isComment) cComment else cString)
        }
    }
}

private class HighlightTransformation(private val lang: Lang) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(Highlighter.highlight(text.text, lang), OffsetMapping.Identity)
}

// ---------------------------------------------------------------- editor view

@Composable
fun CodeEditor(doc: OpenDoc, modifier: Modifier = Modifier) {
    val lang = remember(doc.file) { Lang.forFile(doc.file.name) }
    val transformation = remember(lang) { HighlightTransformation(lang) }
    val value = doc.value
    val lineCount = remember(value.text) { value.text.count { it == '\n' } + 1 }
    val gutterText = remember(lineCount) { (1..lineCount).joinToString("\n") }
    val gutterWidth = (lineCount.toString().length.coerceAtLeast(2) * 9 + 26).dp
    // "calt 0" turns off the font's joined symbols (so <!-- stays <!-- and not an arrow),
    // and LTR keeps a line in code order even when it contains Persian text.
    val textStyle = remember {
        TextStyle(
            fontFamily = CodeFont,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = SbText,
            fontFeatureSettings = "calt 0, liga 0",
            textDirection = TextDirection.Ltr,
        )
    }
    val gutterStyle = remember { textStyle.copy(color = SbTextMuted, textAlign = TextAlign.End) }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier) {
        val viewportH = maxHeight
        val viewportW = maxWidth
        Row(
            Modifier
                .fillMaxWidth()
                .verticalScroll(vScroll)
        ) {
            Text(
                text = gutterText,
                style = gutterStyle,
                modifier = Modifier
                    .width(gutterWidth)
                    .heightIn(min = viewportH)
                    .padding(top = 8.dp, end = 10.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .horizontalScroll(hScroll)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { doc.onValueChange(it) },
                    textStyle = textStyle,
                    cursorBrush = SolidColor(SbText),
                    visualTransformation = transformation,
                    onTextLayout = { layoutResult = it },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Text,
                    ),
                    modifier = Modifier
                        .widthIn(min = viewportW - gutterWidth)
                        .heightIn(min = viewportH)
                        .padding(start = 4.dp, top = 8.dp, end = 32.dp, bottom = 8.dp)
                        .onPreviewKeyEvent { ev ->
                            // For a keyboard connected to the phone:
                            // Ctrl+Z undo, Ctrl+Y (or Ctrl+Shift+Z) redo, and with suggestions open
                            // Up/Down choose, Tab completes, Esc closes.
                            if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed) {
                                when (ev.key) {
                                    Key.Z -> {
                                        if (ev.isShiftPressed) doc.redo() else doc.undo()
                                        return@onPreviewKeyEvent true
                                    }
                                    Key.Y -> {
                                        doc.redo()
                                        return@onPreviewKeyEvent true
                                    }
                                    else -> Unit
                                }
                            }
                            if (ev.type != KeyEventType.KeyDown) {
                                false
                            } else if (doc.suggestionResult != null) {
                                when (ev.key) {
                                    Key.DirectionDown -> { doc.moveSuggestion(1); true }
                                    Key.DirectionUp -> { doc.moveSuggestion(-1); true }
                                    Key.Tab -> { doc.acceptSuggestion(); true }
                                    Key.Escape -> { doc.clearSuggestions(); true }
                                    else -> false
                                }
                            } else if (ev.key == Key.Tab) {
                                doc.insert("    ")
                                true
                            } else {
                                false
                            }
                        },
                )
            }
        }

        // Suggestion list next to the cursor.
        val result = doc.suggestionResult
        val layout = layoutResult
        if (result != null && result.items.isNotEmpty() && layout != null) {
            val cursor = value.selection.start
            if (cursor <= layout.layoutInput.text.length) {
                val rect = layout.getCursorRect(cursor)
                val gutterPx = with(density) { gutterWidth.roundToPx() }
                val padStartPx = with(density) { 4.dp.roundToPx() }
                val padTopPx = with(density) { 8.dp.roundToPx() }
                val popupH = with(density) { (SUGGESTION_ROW_HEIGHT * result.items.size + 8.dp).roundToPx() }
                val popupW = with(density) { SUGGESTION_WIDTH.roundToPx() }
                val viewW = with(density) { viewportW.roundToPx() }
                val viewH = with(density) { viewportH.roundToPx() }
                val xRaw = gutterPx + padStartPx + rect.left.roundToInt() - hScroll.value
                val yTop = padTopPx + rect.top.roundToInt() - vScroll.value
                val yBottom = padTopPx + rect.bottom.roundToInt() - vScroll.value
                // Open below the line; if there is no room, open above it.
                val y = if (yBottom + popupH > viewH && yTop - popupH >= 0) yTop - popupH else yBottom
                val x = xRaw.coerceIn(0, maxOf(0, viewW - popupW))
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(x, y),
                    properties = PopupProperties(focusable = false),
                ) {
                    SuggestionList(result, doc.suggestionIndex) { doc.acceptSuggestion(it) }
                }
            }
        }
    }
}

private val SUGGESTION_ROW_HEIGHT = 28.dp
private val SUGGESTION_WIDTH = 220.dp

@Composable
private fun SuggestionList(result: SuggestionResult, selected: Int, onPick: (Int) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .width(SUGGESTION_WIDTH)
            .clip(shape)
            .background(SbCard)
            .border(1.dp, SbCardBorder, shape)
            .padding(vertical = 4.dp)
    ) {
        result.items.forEachIndexed { i, s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(SUGGESTION_ROW_HEIGHT)
                    .background(if (i == selected) SbPrimaryDeep else Color.Transparent)
                    .clickable { onPick(i) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.label,
                    color = SbText,
                    fontFamily = CodeFont,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (s.hint.isNotEmpty()) Text(s.hint, color = SbTextMuted, fontSize = 10.sp)
            }
        }
    }
}

private val helperKeys = listOf("<", ">", "/", "{", "}", ";", ":", "\"", "'", "=", "(", ")", "[", "]", "#", ".", ",", "!", "-", "_", "&", "*")

/** Row of quick keys shown above the keyboard. */
@Composable
fun HelperBar(doc: OpenDoc, modifier: Modifier = Modifier) {
    val hasSuggestions = doc.suggestionResult != null
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(SbPanel)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(SbIcons.Undo, onClick = { doc.undo() }, tint = if (doc.canUndo) SbText else SbTextMuted, boxSize = 36.dp)
        IconAction(SbIcons.Redo, onClick = { doc.redo() }, tint = if (doc.canRedo) SbText else SbTextMuted, boxSize = 36.dp)
        Spacer(Modifier.width(6.dp))
        // Tab completes the chosen suggestion; without suggestions it types four spaces.
        HelperKey("Tab") { if (!doc.acceptSuggestion()) doc.insert("    ") }
        if (hasSuggestions) {
            Spacer(Modifier.width(4.dp))
            HelperKey("\u2191") { doc.moveSuggestion(-1) }
            Spacer(Modifier.width(4.dp))
            HelperKey("\u2193") { doc.moveSuggestion(1) }
            Spacer(Modifier.width(4.dp))
            HelperKey("Esc") { doc.clearSuggestions() }
        }
        for (k in helperKeys) {
            Spacer(Modifier.width(4.dp))
            HelperKey(k) { doc.insert(k) }
        }
    }
}

@Composable
private fun HelperKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .widthIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SbCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = SbText, fontFamily = CodeFont, fontSize = 15.sp)
    }
}
