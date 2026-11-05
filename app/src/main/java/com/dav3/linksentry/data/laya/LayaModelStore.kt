package com.dav3.linksentry.data.laya

import android.content.Context
import android.net.Uri
import com.dav3.linksentry.domain.laya.LinkVerdict
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Status of the optional, user-imported Laya model bundle.
 *
 * The bundle is [LinkVerdict]-capable model files the user side-loads from a
 * computer (LinkSentry has no INTERNET permission and never downloads).
 * Every file is verified against the pinned SHA-256 of the exact upstream
 * release before it is accepted.
 */
sealed interface LayaModelStatus {

    /** No model imported yet (or removed). */
    data object NotInstalled : LayaModelStatus

    /** Files exist but at least one hash does not match the pinned release. */
    data class Corrupt(val mismatched: List<String>) : LayaModelStatus

    /** Complete bundle present and verified. */
    data class Installed(val totalBytes: Long) : LayaModelStatus
}

/**
 * Manages the imported Laya model bundle under filesDir/laya: pinned-hash
 * verification, SAF import (staged + atomic), and removal. Pure file work —
 * no network, no model runtime.
 */
@Singleton
class LayaModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** filesDir/laya — internal storage; no permission needed to write. */
    val modelDir: File
        get() = File(context.filesDir, "laya").apply { mkdirs() }

    /** A human-readable name for each required file (import dialog copy). */
    val requiredFiles: Map<String, String> = REQUIRED_FILES

    /** Current install state, computed from the files on disk. */
    fun status(): LayaModelStatus {
        val mismatched = mutableListOf<String>()
        var total = 0L
        var present = 0
        for ((name, hash) in PINNED_SHA256) {
            val file = File(modelDir, name)
            if (!file.isFile) continue
            present++
            total += file.length()
            if (sha256(file) != hash) mismatched += name
        }
        return when {
            present == 0 -> LayaModelStatus.NotInstalled
            present < PINNED_SHA256.size || mismatched.isNotEmpty() ->
                LayaModelStatus.Corrupt(
                    mismatched = mismatched.ifEmpty {
                        PINNED_SHA256.keys.filterNot { File(modelDir, it).isFile }
                    },
                )
            else -> LayaModelStatus.Installed(totalBytes = total)
        }
    }

    /**
     * Imports the given SAF documents into the model dir. Files are staged,
     * hashed, and only moved into place when EVERY file in [uris] is a
     * known name with the pinned hash — partial or tampered bundles never
     * replace an existing install. Returns the per-file result for UI copy.
     */
    suspend fun import(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "laya-import").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val staged = mutableMapOf<String, File>()
            for (uri in uris) {
                val name = queryDisplayName(uri) ?: continue
                if (name !in PINNED_SHA256) continue // ignore unrelated picks
                val target = File(staging, name)
                copyAndHash(uri, target)?.let { digest ->
                    if (digest != PINNED_SHA256[name]) {
                        return@withContext ImportResult(
                            ok = false,
                            failedFile = name,
                            message = "$name does not match the pinned release (SHA-256 mismatch)",
                        )
                    }
                    staged[name] = target
                } ?: return@withContext ImportResult(
                    ok = false,
                    failedFile = name,
                    message = "Could not read $name",
                )
            }
            val missing = PINNED_SHA256.keys.filterNot { it in staged }
            if (missing.isNotEmpty()) {
                return@withContext ImportResult(
                    ok = false,
                    failedFile = missing.first(),
                    message = "Bundle incomplete — still missing: ${missing.joinToString()}",
                )
            }
            // Atomic swap: everything verified, replace the install.
            for ((name, file) in staged) {
                if (!file.renameTo(File(modelDir, name))) {
                    file.copyTo(File(modelDir, name), overwrite = true)
                    file.delete()
                }
            }
            ImportResult(ok = true, failedFile = null, message = null)
        } catch (e: IOException) {
            Timber.w(e, "Laya model import failed")
            ImportResult(ok = false, failedFile = null, message = e.message ?: "Import failed")
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Deletes the imported bundle. */
    fun delete() {
        modelDir.deleteRecursively()
    }

    /** True when [name] is one of the expected bundle files. */
    fun isModelFile(name: String): Boolean = name in PINNED_SHA256

    private fun copyAndHash(uri: Uri, target: File): String? {
        val resolver = context.contentResolver
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(1 shl 20)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        digest.update(buf, 0, read)
                    }
                }
            } ?: return null
        } catch (e: IOException) {
            Timber.w(e, "copy failed for %s", uri)
            return null
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun queryDisplayName(uri: Uri): String? {
        // OpenDocument returns a content:// with a display name in the provider.
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')
        }.getOrNull()
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { digest ->
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Outcome of one import attempt, for Settings copy. */
    data class ImportResult(
        val ok: Boolean,
        val failedFile: String?,
        val message: String?,
    )

    companion object {
        /**
         * The pinned multilingual bundle (convaiinnovations/laya@1c5edc17 via
         * litert-community/Laya-Multilingual-LiteRT, s256 wfp16 window) with
         * the release's SHA-256 digests. Model files are only accepted when
         * they match — a wrong or tampered file never reaches the runtime.
         */
        val PINNED_SHA256: Map<String, String> = linkedMapOf(
            "laya_ml_s256_embeds_wfp16.tflite" to
                "712f2fea6b2c39759b4c46274cd83b7653f2539fedc4d7569e4c7bf903894ac8",
            "laya_ml_act_head_fp32.tflite" to
                "30da532a8b752fdc82351740b0194d440a13d342ed77b9f8848978f1a96cf16d",
            "laya_ml_calibration.json" to
                "80e148c68154b607e7bb66446f064b1fe5da649e1074c917b25e02551a566f2c",
            "tokenizer.json" to
                "609d8f4c067cd3950f88594c5a802616cea245823836ef5848ee4fc40aab5b6f",
            "token_embeddings_fp16.bin" to
                "58608f7bbd72e03adb3e2db8c4942407058eaeaac38e3abec1d6cb01c1512265",
            "token_embeddings.json" to
                "8b25844d858ace3833b69354a60536111e9bbcecb2e305bee7971e8dfcbd83ef",
        )

        /** Import-dialog copy for each file of the bundle. */
        val REQUIRED_FILES: Map<String, String> = linkedMapOf(
            "laya_ml_s256_embeds_wfp16.tflite" to "model graph (239 MB)",
            "laya_ml_act_head_fp32.tflite" to "action head graph (~1 MB)",
            "laya_ml_calibration.json" to "calibration (~2 KB)",
            "tokenizer.json" to "tokenizer (32 MB)",
            "token_embeddings_fp16.bin" to "embedding table (375 MB)",
            "token_embeddings.json" to "embedding metadata (~1 KB)",
        )
    }
}
