package com.dav3.linksentry.data.laya

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * File-level behaviour of the model store: status transitions over the real
 * filesDir, pinned-name filtering, and delete. (The pinned hashes belong to
 * the real multi-hundred-MB release, so a full "Installed" state can only be
 * reached with the genuine bundle — its acceptance is exercised by the SHA
 * equality itself.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LayaModelStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `fresh install reports not installed`() {
        val store = LayaModelStore(context)
        store.delete()
        assertEquals(LayaModelStatus.NotInstalled, store.status())
    }

    @Test
    fun `partial or wrong content reports corrupt with the offending files`() {
        val store = LayaModelStore(context)
        store.delete()
        // One file present but with wrong content.
        java.io.File(store.modelDir, "tokenizer.json").writeText("{}")
        val status = store.status()
        assertTrue(status is LayaModelStatus.Corrupt)
        assertTrue((status as LayaModelStatus.Corrupt).mismatched.contains("tokenizer.json"))
    }

    @Test
    fun `delete removes the bundle`() {
        val store = LayaModelStore(context)
        java.io.File(store.modelDir, "tokenizer.json").writeText("{}")
        store.delete()
        assertEquals(LayaModelStatus.NotInstalled, store.status())
    }

    @Test
    fun `only pinned bundle names count as model files`() {
        val store = LayaModelStore(context)
        assertTrue(store.isModelFile("tokenizer.json"))
        assertTrue(store.isModelFile("token_embeddings_fp16.bin"))
        assertFalse(store.isModelFile("notes.txt"))
        assertFalse(store.isModelFile("laya_ml_s512_embeds_fp32.tflite"))
    }

    @Test
    fun `pinned set covers exactly the six bundle files`() {
        assertEquals(6, LayaModelStore.PINNED_SHA256.size)
        assertEquals(LayaModelStore.PINNED_SHA256.keys, LayaModelStore.REQUIRED_FILES.keys)
        // Hashes are lowercase hex, 64 chars.
        LayaModelStore.PINNED_SHA256.values.forEach { hash ->
            assertTrue(hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' })
        }
    }
}
