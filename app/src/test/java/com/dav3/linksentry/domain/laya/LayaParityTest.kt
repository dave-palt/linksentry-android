package com.dav3.linksentry.domain.laya

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Parity gate against the pinned multilingual Laya release.
 *
 * The committed fixture (litert-community/Laya-Multilingual-LiteRT,
 * fixtures/gate_rows_s256.json) carries 201 captured question rows: the exact
 * upstream question schema + state, the captured sequence token ids and
 * marker positions, the raw logits, and the official four-decimal answer
 * dictionaries at T=1.
 *
 * This test rebuilds every row with OUR tokenizer + prompt builder (token
 * ids and markers must match exactly) and decodes the captured logits with
 * OUR decoder (answers must match the official ones).
 *
 * tokenizer.json (32 MB) is NOT committed: tests read it from the directory
 * given by the `laya.validationDir` system property (default `.validation`),
 * which CI populates from the pinned, hash-checked release. A missing
 * tokenizer SKIPS the gate — it never counts as a pass.
 */
class LayaParityTest {

    private fun validationDir(): File = System.getProperty("laya.validationDir", ".validation").let(::File)

    private fun tokenizerFile(): File = File(validationDir(), "tokenizer.json")

    private fun fixtureRows(): List<Map<String, Any?>> {
        val text = javaClass.classLoader!!
            .getResourceAsStream("laya/gate_rows_s256.json")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val root = LayaJson.parse(text) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        return LayaJson.asArray(root.getValue("rows")) as List<Map<String, Any?>>
    }

    @Test
    fun `rebuilt sequences match captured ids and markers for all 201 rows`() {
        assumeTrue("tokenizer.json missing from validation dir — parity gate skipped", tokenizerFile().isFile)
        val tokenizer = LayaTokenizer(tokenizerFile())
        val builder = LayaPromptBuilder(tokenizer, maxLen = 256, headMaxLen = 256)
        val rows = fixtureRows()
        assertTrue("fixture unexpectedly small: ${rows.size}", rows.size >= 200)

        val mismatches = mutableListOf<String>()
        for (row in rows) {
            val rowId = row["row_id"] as? String ?: "?"
            val state = row["state"]

            @Suppress("UNCHECKED_CAST")
            val question = LayaJson.asObject(row.getValue("question"))
            val built = builder.build(state, question, questionId = rowId)
            val expectedIds = (row["sequence_ids"] as List<*>).map { (it as Number).toInt() }.toIntArray()
            val expectedMarkers = (row["marker_positions"] as List<*>).map { (it as Number).toInt() }.toIntArray()
            if (!built.ids.contentEquals(expectedIds)) {
                mismatches += "$rowId: sequence ids diverge (len ${built.ids.size} vs ${expectedIds.size})"
                continue
            }
            assertArrayEquals("$rowId markers", expectedMarkers, built.markers)
        }
        assertTrue(
            "sequence parity failed for ${mismatches.size}/${rows.size} rows:\n" + mismatches.take(5).joinToString("\n"),
            mismatches.isEmpty(),
        )
    }

    @Test
    fun `decoded answers match the official dictionaries for all 201 rows`() {
        val rows = fixtureRows()
        // The captured official answers are the T=1 (uncalibrated) decode of
        // the captured logits — the same dictionary the runtime produces
        // before applying the bundle's fitted temperatures in judge().
        val calibration = LayaCalibration.identity()
        val mismatches = mutableListOf<String>()
        for (row in rows) {
            val rowId = row["row_id"] as? String ?: "?"
            val rawLogits = (row["raw_logits"] as List<*>).map { (it as Number).toFloat() }.toFloatArray()
            val actLogits = (row["raw_act_logits"] as List<*>).map { (it as Number).toFloat() }.toFloatArray()

            @Suppress("UNCHECKED_CAST")
            val question = row.getValue("question") as Map<String, Any?>
            val decoded = LayaDecoder.decode(rawLogits, actLogits, question, calibration)

            @Suppress("UNCHECKED_CAST")
            val official = row.getValue("official_answer") as Map<String, Any?>
            val diff = diffDictionaries(rowId, official, decoded)
            if (diff != null) mismatches += diff
        }
        assertTrue(
            "decode parity failed for ${mismatches.size}/${rows.size} rows:\n" + mismatches.take(5).joinToString("\n"),
            mismatches.isEmpty(),
        )
    }

    /** Structural comparison with a tight tolerance on leaf numbers. */
    private fun diffDictionaries(path: String, expected: Any?, actual: Any?): String? {
        return when {
            expected is Map<*, *> && actual is Map<*, *> -> {
                if (expected.keys != actual.keys) return "$path: key sets differ"
                for (key in expected.keys) {
                    val child = diffDictionaries("$path.$key", expected[key], actual[key])
                    if (child != null) return child
                }
                null
            }
            expected is Number && actual is Number -> {
                val e = expected.toDouble()
                val a = actual.toDouble()
                if (Math.abs(e - a) > 1e-9) "$path: expected $e got $a" else null
            }
            expected is Boolean && actual is Boolean -> if (expected == actual) null else "$path: $expected vs $actual"
            expected is String && actual is String -> if (expected == actual) null else "$path: \"$expected\" vs \"$actual\""
            else -> if (expected == actual) null else "$path: type/value mismatch ($expected vs $actual)"
        }
    }
}
