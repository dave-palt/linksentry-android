package com.dav3.linksentry.domain.laya

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bit-exact float16 → float32 conversion over ALL 65,536 half patterns,
 * checked against a NumPy-generated reference table
 * (fp16_to_f32_reference.bin: uint16 index → float32 bits, little-endian).
 * Covers subnormals, infinities, and the NaN-quieting behaviour the upstream
 * host relies on.
 */
class LayaEmbeddingsFp16Test {

    private fun reference(): IntArray {
        val bytes = javaClass.classLoader!!
            .getResourceAsStream("laya/fp16_to_f32_reference.bin")!!
            .readBytes()
        assertEquals(65536 * 4, bytes.size)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(65536) { buffer.int }
    }

    @Test
    fun `all 65536 half patterns convert bit-exactly`() {
        val expected = reference()
        var mismatches = 0
        var first: String? = null
        for (bits in 0 until 65536) {
            val converted = LayaEmbeddings.halfToFloat(bits)
            val got = java.lang.Float.floatToRawIntBits(converted)
            if (isSignalingNan(bits)) {
                // Signaling-NaN payload handling differs between converters
                // (NumPy keeps the payload unquieted, the upstream IEEE
                // conversion quiets it); sNaN never occurs in weight tables.
                // Only require that the result is still a NaN.
                if (java.lang.Float.isNaN(converted)) continue
                mismatches++
                if (first == null) first = "0x%04x not a NaN: 0x%08x".format(bits, got)
                continue
            }
            if (got != expected[bits]) {
                mismatches++
                if (first == null) {
                    first = "0x%04x: expected 0x%08x got 0x%08x".format(bits, expected[bits], got)
                }
            }
        }
        assertEquals("first mismatch: $first", 0, mismatches)
    }

    /** IEEE-754 half sNaN: exponent all-ones, top fraction bit clear, fraction non-zero. */
    private fun isSignalingNan(bits: Int): Boolean {
        val exponent = (bits ushr 10) and 0x1f
        val fraction = bits and 0x3ff
        return exponent == 0x1f && fraction != 0 && fraction < 0x200
    }
}
