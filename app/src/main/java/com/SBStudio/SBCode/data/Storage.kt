package com.SBStudio.SBCode.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/** Where projects live and whether the app is allowed to use that folder. */
object Storage {
    const val ROOT_NAME = "SBCode"
    const val PROJECTS_NAME = "Projects"
    const val SYSTEM_NAME = "System"
    private const val OLD_ROOT_NAME = "SBProjects" // folder used by the very first version

    /** SBCode - the app's main folder in the phone's storage. */
    fun rootDir(): File = File(Environment.getExternalStorageDirectory(), ROOT_NAME)

    /** SBCode/Projects - every project is a folder inside this one. */
    fun projectsDir(): File = File(rootDir(), PROJECTS_NAME)

    /** SBCode/System - empty for now, reserved for plugins and mod files later. */
    fun systemDir(): File = File(rootDir(), SYSTEM_NAME)

    /** Android 11+: "All files access". Android 9-10: the normal storage permission. */
    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** Creates SBCode with its Projects and System folders (re-creates anything the user deleted). */
    fun ensureRoot(): Boolean {
        val projects = projectsDir()
        val system = systemDir()
        if (!projects.exists()) projects.mkdirs()
        if (!system.exists()) system.mkdirs()
        moveOldProjects()
        return projects.isDirectory
    }

    /**
     * The first version stored projects in a folder called SBProjects.
     * Move whatever is inside it to SBCode/Projects, then remove the old folder if it is empty.
     * Nothing is overwritten: if a name already exists in Projects, that item stays where it is.
     */
    private fun moveOldProjects() {
        val old = File(Environment.getExternalStorageDirectory(), OLD_ROOT_NAME)
        if (!old.isDirectory) return
        val target = projectsDir()
        old.listFiles()?.forEach { child ->
            val dest = File(target, child.name)
            if (!dest.exists()) child.renameTo(dest)
        }
        if (old.listFiles().isNullOrEmpty()) old.delete()
    }

    /** Opens the Android settings page where the user can allow "All files access" (Android 11+). */
    fun openAllFilesAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            openAppSettings(context)
            return
        }
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            )
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e2: Exception) {
                openAppSettings(context)
            }
        }
    }

    fun openAppSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        )
    }
}
