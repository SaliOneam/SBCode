package com.SBStudio.SBCode.ui

/** Which language a file is, decided by its extension. */
enum class Lang {
    HTML, CSS, JS, PLAIN;

    companion object {
        fun forFile(name: String): Lang = when (name.substringAfterLast('.', "").lowercase()) {
            "html", "htm", "svg", "xml" -> HTML
            "css" -> CSS
            "js", "mjs", "json" -> JS
            else -> PLAIN
        }
    }
}

/**
 * One item in the suggestion list.
 * [label] is what you see, [insert] is what gets typed for you,
 * and [cursorBack] moves the cursor back from the end of [insert] (to land between quotes, for example).
 */
class Suggestion(
    val label: String,
    val insert: String = label,
    val cursorBack: Int = 0,
    val hint: String = "",
)

/** The words to show, and where the word being typed starts (that part gets replaced). */
class SuggestionResult(val replaceStart: Int, val items: List<Suggestion>)

object Suggestions {

    private const val MAX_ITEMS = 8

    // ------------------------------------------------------------ word lists

    private val htmlTags = listOf(
        "a", "abbr", "address", "article", "aside", "audio", "b", "blockquote", "body", "br", "button", "canvas",
        "caption", "code", "div", "em", "fieldset", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6",
        "head", "header", "hr", "html", "i", "iframe", "img", "input", "label", "legend", "li", "link", "main",
        "meta", "nav", "ol", "option", "p", "pre", "script", "section", "select", "small", "source", "span",
        "strong", "style", "sub", "summary", "sup", "svg", "table", "tbody", "td", "template", "textarea",
        "tfoot", "th", "thead", "title", "tr", "u", "ul", "video",
    )

    private val htmlAttrs = listOf(
        "accept", "action", "alt", "autocomplete", "autofocus", "autoplay", "charset", "checked", "class", "cols",
        "colspan", "content", "controls", "disabled", "download", "for", "height", "hidden", "href", "http-equiv",
        "id", "lang", "loop", "max", "maxlength", "method", "min", "multiple", "muted", "name", "onblur",
        "onchange", "onclick", "onfocus", "oninput", "onkeydown", "onload", "onsubmit", "pattern", "placeholder",
        "poster", "readonly", "rel", "required", "rows", "rowspan", "selected", "size", "src", "srcset", "step",
        "style", "tabindex", "target", "title", "type", "value", "viewBox", "width",
    )

    /** Attributes that are written alone, with no ="..." after them. */
    private val booleanAttrs = setOf(
        "autofocus", "autoplay", "checked", "controls", "disabled", "hidden", "loop", "multiple",
        "muted", "readonly", "required", "selected",
    )

    private val voidTags = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
    )

    private val cssProps = listOf(
        "align-content", "align-items", "align-self", "animation", "background", "background-color",
        "background-image", "background-position", "background-repeat", "background-size", "border",
        "border-bottom", "border-color", "border-left", "border-radius", "border-right", "border-top",
        "border-width", "bottom", "box-shadow", "box-sizing", "clear", "color", "column-gap", "content",
        "cursor", "display", "filter", "flex", "flex-basis", "flex-direction", "flex-grow", "flex-shrink",
        "flex-wrap", "float", "font-family", "font-size", "font-style", "font-weight", "gap", "grid",
        "grid-column", "grid-gap", "grid-row", "grid-template-areas", "grid-template-columns",
        "grid-template-rows", "height", "justify-content", "justify-items", "left", "letter-spacing",
        "line-height", "list-style", "margin", "margin-bottom", "margin-left", "margin-right", "margin-top",
        "max-height", "max-width", "min-height", "min-width", "object-fit", "opacity", "order", "outline",
        "overflow", "overflow-x", "overflow-y", "padding", "padding-bottom", "padding-left", "padding-right",
        "padding-top", "pointer-events", "position", "right", "row-gap", "text-align", "text-decoration",
        "text-overflow", "text-shadow", "text-transform", "top", "transform", "transition", "user-select",
        "vertical-align", "visibility", "white-space", "width", "word-break", "z-index",
    )

    private val cssValues: Map<String, List<String>> = mapOf(
        "display" to listOf("block", "inline", "inline-block", "flex", "inline-flex", "grid", "none", "contents"),
        "position" to listOf("static", "relative", "absolute", "fixed", "sticky"),
        "flex-direction" to listOf("row", "row-reverse", "column", "column-reverse"),
        "flex-wrap" to listOf("nowrap", "wrap", "wrap-reverse"),
        "justify-content" to listOf("flex-start", "flex-end", "center", "space-between", "space-around", "space-evenly"),
        "align-items" to listOf("stretch", "flex-start", "flex-end", "center", "baseline"),
        "text-align" to listOf("left", "right", "center", "justify"),
        "overflow" to listOf("visible", "hidden", "scroll", "auto"),
        "overflow-x" to listOf("visible", "hidden", "scroll", "auto"),
        "overflow-y" to listOf("visible", "hidden", "scroll", "auto"),
        "cursor" to listOf("pointer", "default", "text", "move", "not-allowed", "grab", "crosshair"),
        "font-weight" to listOf("normal", "bold", "bolder", "lighter"),
        "font-style" to listOf("normal", "italic", "oblique"),
        "text-decoration" to listOf("none", "underline", "line-through", "overline"),
        "text-transform" to listOf("none", "uppercase", "lowercase", "capitalize"),
        "box-sizing" to listOf("border-box", "content-box"),
        "visibility" to listOf("visible", "hidden"),
        "float" to listOf("left", "right", "none"),
        "white-space" to listOf("normal", "nowrap", "pre", "pre-wrap"),
        "object-fit" to listOf("cover", "contain", "fill", "none", "scale-down"),
        "background-repeat" to listOf("no-repeat", "repeat", "repeat-x", "repeat-y"),
        "list-style" to listOf("none", "disc", "circle", "square"),
        "pointer-events" to listOf("none", "auto"),
        "user-select" to listOf("none", "auto", "text", "all"),
        "vertical-align" to listOf("baseline", "top", "middle", "bottom"),
    )

    private val jsWords = listOf(
        "async", "await", "break", "case", "catch", "class", "const", "continue", "default", "else", "export",
        "extends", "false", "finally", "for", "function", "if", "import", "let", "new", "null", "return",
        "switch", "this", "throw", "true", "try", "typeof", "undefined", "var", "while",
        "console", "document", "window", "localStorage", "sessionStorage", "setTimeout", "setInterval",
        "clearTimeout", "clearInterval", "fetch", "JSON", "Math", "Date", "Array", "Object", "String", "Number",
        "Boolean", "Promise", "parseInt", "parseFloat", "isNaN", "alert", "confirm", "prompt", "navigator",
        "location", "history", "requestAnimationFrame",
    )

    private val jsMembers: Map<String, List<String>> = mapOf(
        "console" to listOf("log", "error", "warn", "info", "table", "clear"),
        "document" to listOf(
            "getElementById", "querySelector", "querySelectorAll", "createElement", "addEventListener", "body",
            "head", "title", "getElementsByClassName", "getElementsByTagName", "cookie",
        ),
        "Math" to listOf("floor", "ceil", "round", "random", "max", "min", "abs", "pow", "sqrt", "PI"),
        "JSON" to listOf("stringify", "parse"),
        "Object" to listOf("keys", "values", "entries", "assign", "freeze"),
        "Array" to listOf("isArray", "from", "of"),
        "localStorage" to listOf("getItem", "setItem", "removeItem", "clear"),
        "sessionStorage" to listOf("getItem", "setItem", "removeItem", "clear"),
        "window" to listOf("addEventListener", "innerWidth", "innerHeight", "location", "localStorage", "setTimeout", "alert", "open", "scrollTo"),
        "Promise" to listOf("all", "resolve", "reject", "race"),
        "Date" to listOf("now", "parse"),
    )

    /** Used after "something." when we don't know what "something" is. */
    private val commonMembers = listOf(
        "addEventListener", "append", "appendChild", "children", "classList", "className", "click", "closest",
        "contains", "dataset", "endsWith", "filter", "focus", "forEach", "getAttribute", "id", "includes",
        "indexOf", "innerHTML", "innerText", "join", "json", "length", "map", "parentElement", "preventDefault",
        "push", "querySelector", "querySelectorAll", "remove", "removeChild", "replace", "scrollIntoView",
        "setAttribute", "slice", "split", "startsWith", "stopPropagation", "style", "textContent", "then",
        "toLowerCase", "toUpperCase", "trim", "value",
    )

    private val identRe = Regex("[A-Za-z_][A-Za-z0-9_]{2,}")
    private val memberRe = Regex("([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*)?$")
    private val wordRe = Regex("[A-Za-z_]\\w*$")
    private val cssValueRe = Regex("(?:^|[;{\\s])([A-Za-z-]+)\\s*:\\s*([A-Za-z-]+)$")
    private val cssWordRe = Regex("[A-Za-z-]+$")
    private val attrWordRe = Regex("\\s([A-Za-z@:_][\\w:.-]*)$")
    private val attrNameRe = Regex("([A-Za-z@:_][\\w:.-]*)\\s*=")
    private val tagNameRe = Regex("^([A-Za-z][\\w:-]*)")

    // ------------------------------------------------------------ main entry

    /** Suggestions for the word that ends at [cursor], or null when there is nothing useful to show. */
    fun compute(text: String, cursor: Int, lang: Lang): SuggestionResult? {
        if (cursor <= 0 || cursor > text.length || text.length > 300_000) return null
        return when (lang) {
            Lang.HTML -> html(text, cursor)
            Lang.CSS -> css(text, cursor)
            Lang.JS -> js(text, cursor)
            Lang.PLAIN -> null
        }
    }

    // ------------------------------------------------------------ HTML

    private fun html(text: String, cursor: Int): SuggestionResult? {
        when (embeddedKind(text, cursor)) {
            "script" -> return js(text, cursor)
            "style" -> return css(text, cursor)
        }
        val lt = text.lastIndexOf('<', cursor - 1)
        if (lt < 0) return null
        if (text.lastIndexOf('>', cursor - 1) > lt) return null // the cursor is outside any tag
        val inside = text.substring(lt + 1, cursor)
        if (inside.isEmpty() || inside[0] == '!' || inside[0] == '?' || inside[0] == '/') return null
        if (inQuote(inside, "\"'")) return null

        // Writing the tag name: <di
        if (inside.all { it.isLetterOrDigit() || it == '-' || it == ':' }) {
            val items = rank(htmlTags, inside)
            if (items.isEmpty()) return null
            return SuggestionResult(cursor - inside.length, items.map { Suggestion(it, hint = "tag") })
        }

        // Writing an attribute name: <div cl
        val word = attrWordRe.find(inside) ?: return null
        val prefix = word.groupValues[1]
        val used = attrNameRe.findAll(inside).map { it.groupValues[1] }.toSet()
        val items = rank(htmlAttrs.filter { it !in used }, prefix)
        if (items.isEmpty()) return null
        return SuggestionResult(
            cursor - prefix.length,
            items.map {
                if (it in booleanAttrs) Suggestion(it, hint = "attr")
                else Suggestion(it, insert = "$it=\"\"", cursorBack = 1, hint = "attr")
            },
        )
    }

    /** "script" or "style" when the cursor is inside such a block, otherwise null. */
    private fun embeddedKind(text: String, cursor: Int): String? {
        val before = text.substring(0, cursor)
        var best: String? = null
        var bestOpen = -1
        for (k in listOf("script", "style")) {
            val open = before.lastIndexOf("<$k", ignoreCase = true)
            if (open < 0) continue
            val close = before.lastIndexOf("</$k", ignoreCase = true)
            if (close > open) continue
            if (before.indexOf('>', open) < 0) continue // still writing the opening tag
            if (open > bestOpen) {
                best = k
                bestOpen = open
            }
        }
        return best
    }

    /**
     * Call right after the user typed ">" (the cursor is just after it).
     * Returns the closing tag to add (like "</div>"), or null when none should be added.
     */
    fun closingTagFor(text: String, cursor: Int): String? {
        if (cursor < 2 || cursor > text.length || text[cursor - 1] != '>') return null
        if (embeddedKind(text, cursor - 1) != null) return null // ">" inside <script> or <style> code
        val lt = text.lastIndexOf('<', cursor - 2)
        if (lt < 0) return null
        if (text.lastIndexOf('>', cursor - 2) > lt) return null
        val inner = text.substring(lt + 1, cursor - 1)
        val name = tagNameRe.find(inner)?.groupValues?.get(1) ?: return null
        if (inner.trimEnd().endsWith("/")) return null
        if (inQuote(inner, "\"'")) return null
        if (name.lowercase() in voidTags) return null
        val closing = "</$name>"
        if (text.startsWith(closing, cursor)) return null
        return closing
    }

    // ------------------------------------------------------------ CSS

    private fun css(text: String, cursor: Int): SuggestionResult? {
        val open = text.lastIndexOf('{', cursor - 1)
        val close = text.lastIndexOf('}', cursor - 1)
        if (open < 0 || close > open) return null // not inside a { } block
        val from = maxOf(open + 1, cursor - 300)
        val seg = text.substring(from, cursor)
        if (seg.lastIndexOf("/*") > seg.lastIndexOf("*/")) return null // inside a comment

        // Writing a value: display: fl
        val v = cssValueRe.find(seg)
        if (v != null) {
            val prop = v.groupValues[1].lowercase()
            val prefix = v.groupValues[2]
            val values = cssValues[prop] ?: return null
            val items = rank(values, prefix)
            if (items.isEmpty()) return null
            return SuggestionResult(cursor - prefix.length, items.map { Suggestion(it, hint = "value") })
        }

        // Writing a property name: col
        val w = cssWordRe.find(seg) ?: return null
        val prefix = w.value
        val before = seg.substring(0, seg.length - prefix.length).trimEnd()
        if (before.isNotEmpty() && !before.endsWith(";")) return null
        val items = rank(cssProps, prefix)
        if (items.isEmpty()) return null
        return SuggestionResult(
            cursor - prefix.length,
            items.map { Suggestion(it, insert = "$it: ;", cursorBack = 1, hint = "css") },
        )
    }

    // ------------------------------------------------------------ JavaScript

    private fun js(text: String, cursor: Int): SuggestionResult? {
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val line = text.substring(lineStart, cursor)
        if (line.contains("//")) return null
        if (inQuote(line, "\"'`")) return null

        // After a dot: console.lo
        val m = memberRe.find(line)
        if (m != null) {
            val obj = m.groupValues[1]
            val prefix = m.groupValues[2]
            val pool = jsMembers[obj] ?: if (prefix.isNotEmpty()) commonMembers else return null
            val items = rank(pool, prefix)
            if (items.isEmpty()) return null
            return SuggestionResult(cursor - prefix.length, items.map { Suggestion(it, hint = "js") })
        }

        // A plain word: func
        val w = wordRe.find(line) ?: return null
        val prefix = w.value
        if (prefix.length < 2) return null
        val pool = LinkedHashSet<String>(jsWords)
        if (text.length <= 100_000) {
            for (id in identRe.findAll(text)) {
                pool.add(id.value)
                if (pool.size > 2500) break
            }
        }
        val items = rank(pool, prefix)
        if (items.isEmpty()) return null
        return SuggestionResult(cursor - prefix.length, items.map { Suggestion(it, hint = "js") })
    }

    // ------------------------------------------------------------ helpers

    /** Words that start with [prefix] come first (shorter first), then words that only contain it. */
    private fun rank(candidates: Collection<String>, prefix: String): List<String> {
        val p = prefix.lowercase()
        val starts = ArrayList<String>()
        val contains = ArrayList<String>()
        for (c in candidates) {
            if (c == prefix) continue
            val lc = c.lowercase()
            if (lc.startsWith(p)) starts.add(c)
            else if (p.length >= 2 && lc.contains(p)) contains.add(c)
        }
        starts.sortWith(compareBy({ it.length }, { it }))
        contains.sortWith(compareBy({ it.length }, { it }))
        return (starts + contains).distinct().take(MAX_ITEMS)
    }

    private fun inQuote(s: String, quotes: String): Boolean {
        var open = '\u0000'
        for (c in s) {
            if (open == '\u0000') {
                if (c in quotes) open = c
            } else if (c == open) {
                open = '\u0000'
            }
        }
        return open != '\u0000'
    }
}
