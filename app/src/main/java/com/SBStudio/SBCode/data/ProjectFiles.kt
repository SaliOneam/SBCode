package com.SBStudio.SBCode.data

import java.io.File

data class ProjectInfo(
    val name: String,
    val dir: File,
    val lastModified: Long,
    val hasHtml: Boolean,
    val hasCss: Boolean,
    val hasJs: Boolean,
)

data class TreeNode(val file: File, val depth: Int, val isDir: Boolean)

sealed class ReadResult {
    class Text(val text: String) : ReadResult()
    class Unsupported(val reason: String) : ReadResult()
}

/** All file and project operations. No Android classes are used here on purpose. */
object ProjectFiles {

    private val invalidChars = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
    private const val MAX_EDIT_BYTES = 2L * 1024 * 1024

    /**
     * Returns an error message, or null when the name is fine.
     * The duplicate check ignores upper/lower case, so "Test" and "test" count as the same name.
     * [ignore] is the item being renamed (it may keep its own name).
     */
    fun validateName(
        raw: String,
        parent: File,
        existsMessage: String,
        ignore: File? = null,
    ): String? {
        val n = raw.trim()
        if (n.isEmpty()) return "Enter a name"
        if (n == "." || n == "..") return "This name isn't allowed"
        if (n.any { it in invalidChars || it.code < 32 }) return "Name can't contain \\ / : * ? \" < > |"
        if (n.startsWith(".")) return "Name can't start with a dot"
        if (n.endsWith(".")) return "Name can't end with a dot"
        if (n.length > 80) return "Name is too long"
        val clash = parent.listFiles()?.any {
            it.name.equals(n, ignoreCase = true) && (ignore == null || it.name != ignore.name)
        } ?: false
        if (clash) return existsMessage
        return null
    }

    fun listProjects(root: File): List<ProjectInfo> {
        val dirs = root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: return emptyList()
        return dirs.map { scan(it) }.sortedByDescending { it.lastModified }
    }

    fun scan(dir: File): ProjectInfo {
        var last = dir.lastModified()
        var html = false
        var css = false
        var js = false
        dir.walkTopDown().maxDepth(5).forEach { f ->
            if (f.lastModified() > last) last = f.lastModified()
            if (f.isFile) {
                when (f.extension.lowercase()) {
                    "html", "htm" -> html = true
                    "css" -> css = true
                    "js", "mjs" -> js = true
                }
            }
        }
        return ProjectInfo(dir.name, dir, last, html, css, js)
    }

    fun createProject(root: File, name: String): Result<ProjectInfo> = runCatching {
        val dir = File(root, name.trim())
        if (!dir.mkdirs()) error("Couldn't create the project folder")
        scan(dir)
    }

    fun flattenTree(root: File, expanded: Set<String>): List<TreeNode> {
        val out = ArrayList<TreeNode>()
        fun walk(dir: File, depth: Int) {
            val children = dir.listFiles()
                ?.filter { !it.name.startsWith(".") }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: return
            for (c in children) {
                out.add(TreeNode(c, depth, c.isDirectory))
                if (c.isDirectory && c.absolutePath in expanded) walk(c, depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    fun createFile(parent: File, name: String): Result<File> = runCatching {
        val f = File(parent, name.trim())
        if (!f.createNewFile()) error("Couldn't create the file")
        f
    }

    fun createFolder(parent: File, name: String): Result<File> = runCatching {
        val f = File(parent, name.trim())
        if (!f.mkdirs()) error("Couldn't create the folder")
        f
    }

    fun rename(file: File, newName: String): Result<File> = runCatching {
        val target = File(file.parentFile, newName.trim())
        if (!file.renameTo(target)) error("Couldn't rename")
        target
    }

    fun delete(file: File): Boolean = if (file.isDirectory) file.deleteRecursively() else file.delete()

    fun readText(file: File): ReadResult {
        if (file.length() > MAX_EDIT_BYTES) return ReadResult.Unsupported("This file is larger than 2 MB")
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            return ReadResult.Unsupported("Couldn't read this file")
        }
        if (bytes.any { it == 0.toByte() }) return ReadResult.Unsupported("This looks like a binary file, not text")
        return ReadResult.Text(String(bytes, Charsets.UTF_8))
    }

    fun writeText(file: File, text: String): Result<Unit> = runCatching { file.writeText(text) }

    fun timeAgo(then: Long, now: Long = System.currentTimeMillis()): String {
        val s = ((now - then) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60}m ago"
            s < 86400 -> "${s / 3600}h ago"
            s < 86400L * 30 -> "${s / 86400}d ago"
            else -> "${s / (86400L * 30)}mo ago"
        }
    }
}
