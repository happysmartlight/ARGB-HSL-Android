package com.example.data

import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimecodeCryptoTest {
    @Test
    fun derivesKeysFromReferenceVector() {
        val (encKey, macKey) = TimecodeCrypto.deriveKeysForTest(
            "00112233445566778899aabbccddeeff".hexToBytes()
        )

        assertEquals(
            "2f24e3d05c72fa0d2b8356a6b7cffa36e60e9c891953a2444f79bbf6603394a3",
            encKey.toHex()
        )
        assertEquals(
            "ffae6e116beed5ec87f390caff54aee44102b9f14e4e34f472fe7284eb4a6a43",
            macKey.toHex()
        )
    }

    @Test
    fun decryptsReferenceEnvelope() {
        val decrypted = TimecodeCrypto.decryptTimecode(referenceEnvelope)

        assertEquals(1, decrypted.getInt("version"))
        assertEquals("HSL", decrypted.getString("hello"))
    }

    @Test
    fun rejectsTamperedEnvelope() {
        val envelope = JSONObject(referenceEnvelope)
        val blob = Base64.decode(envelope.getString("hsl_timecode"), Base64.DEFAULT)
        blob[40] = (blob[40].toInt() xor 0x01).toByte()
        envelope.put("hsl_timecode", Base64.encodeToString(blob, Base64.NO_WRAP))

        expectCryptoFailure {
            TimecodeCrypto.decryptTimecode(envelope.toString())
        }
    }

    @Test
    fun rejectsPlaintextJson() {
        expectCryptoFailure {
            TimecodeCrypto.decryptTimecode("""{"version":1,"hello":"HSL"}""")
        }
    }

    private fun expectCryptoFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected TimecodeCryptoException")
        } catch (_: TimecodeCryptoException) {
            // Expected.
        }
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val referenceEnvelope =
            """{ "hsl_timecode": "SFNMVAEAESIzRFVmd4iZqrvM3e7//+7dzLuqmYh3ZlVEMyIRANRxfa6EYNI1hrMgijS8AQHU126GC7t+DG6pp+PaATaexSwXuXtDHCZoo4c46by7jSKsWjz/bByp+jHp", "v": 1 }"""
    }
}
