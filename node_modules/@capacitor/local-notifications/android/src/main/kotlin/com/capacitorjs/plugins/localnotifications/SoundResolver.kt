package com.capacitorjs.plugins.localnotifications

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Resolves a notification `sound` value to a playable [Uri].
 *
 * Order of resolution:
 *  1. `res/raw/<name>` (a bundled Android raw resource) — returned as an
 *     `android.resource://` URI.
 *  2. The app's web assets — `assets/public/` (Capacitor) and `assets/www/`
 *     (Cordova) — matching the exact filename or the hashed `<base>__<hash>.<ext>`
 *     variant the build may emit. The asset is copied into app storage and served
 *     through [LocalNotificationsAssetProvider] as a `content://` URI (this is how
 *     the legacy plugin made web-bundled sounds playable on Android 8+).
 *
 * Returns null when no matching sound can be found.
 */
internal object SoundResolver {

    private const val AUTHORITY_SUFFIX = ".localnotifications.fileprovider"
    private const val COPY_DIR = "ln_sounds"
    private val ASSET_FOLDERS = arrayOf("public", "www")

    fun resolveUri(context: Context, sound: String?): Uri? {
        if (sound.isNullOrEmpty()) return null

        val base = baseName(sound)
        val resId = context.resources.getIdentifier(base, "raw", context.packageName)
        if (resId != 0) {
            return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.packageName + "/raw/" + base)
        }

        return resolveFromAssets(context, sound)
    }

    /** File name without any directory or extension (the res/raw + channel-id key). */
    fun baseName(sound: String): String {
        var name = sound
        if (name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1)
        if (name.contains(".")) name = name.substring(0, name.lastIndexOf('.'))
        return name
    }

    private fun resolveFromAssets(context: Context, sound: String): Uri? {
        val fileName = if (sound.contains("/")) sound.substring(sound.lastIndexOf('/') + 1) else sound
        val dot = fileName.lastIndexOf('.')
        val base = if (dot >= 0) fileName.substring(0, dot) else fileName
        val ext = if (dot >= 0) fileName.substring(dot + 1) else ""
        val assets = context.assets

        var resolvedPath: String? = null
        for (folder in ASSET_FOLDERS) {
            val files = try { assets.list(folder) } catch (e: Exception) { null } ?: continue
            val exact = files.firstOrNull { it.equals(fileName, ignoreCase = true) }
            if (exact != null) {
                resolvedPath = "$folder/$exact"
                break
            }
            if (ext.isNotEmpty()) {
                val regex = Regex("^" + Regex.escape(base) + "__.+\\." + Regex.escape(ext) + "$", RegexOption.IGNORE_CASE)
                val match = files.firstOrNull { regex.matches(it) }
                if (match != null) {
                    resolvedPath = "$folder/$match"
                    break
                }
            }
        }

        val assetPath = resolvedPath ?: return null
        val outName = assetPath.substring(assetPath.lastIndexOf('/') + 1)
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, COPY_DIR)
        dir.mkdirs()
        val outFile = File(dir, outName)
        if (!outFile.exists()) {
            try {
                assets.open(assetPath).use { input -> outFile.outputStream().use { input.copyTo(it) } }
            } catch (e: Exception) {
                return null
            }
        }

        return try {
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, outFile)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
