package com.example.data

import android.util.Base64
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class TimecodeCryptoException(message: String) : Exception(message)

object TimecodeCrypto {
    private val appSecret =
        "HSL.TIMECODE.v1.4f2c8a91-6b0e-4d3a-9c77-happysmartlight".toByteArray(Charsets.UTF_8)
    private const val pbkdf2Iterations = 200_000
    private val magic = byteArrayOf(0x48, 0x53, 0x4C, 0x54)
    private const val formatVersion = 1
    private const val saltLength = 16
    private const val nonceLength = 16
    private const val tagLength = 32

    fun decryptTimecode(fileText: String): JSONObject {
        val envelope = try {
            JSONObject(fileText)
        } catch (_: Exception) {
            throw TimecodeCryptoException("File khong phai JSON hop le.")
        }

        if (!envelope.has("hsl_timecode")) {
            throw TimecodeCryptoException("File khong phai dinh dang timecode HSL da ma hoa.")
        }
        if (envelope.optInt("v", formatVersion) != formatVersion) {
            throw TimecodeCryptoException("Phien ban container chua duoc ho tro.")
        }

        val blob = try {
            Base64.decode(envelope.getString("hsl_timecode"), Base64.DEFAULT)
        } catch (_: Exception) {
            throw TimecodeCryptoException("Du lieu ma hoa bi loi base64.")
        }

        val minLength = magic.size + 1 + saltLength + nonceLength + tagLength
        if (blob.size < minLength) {
            throw TimecodeCryptoException("File timecode qua ngan.")
        }
        if (!blob.copyOfRange(0, magic.size).contentEquals(magic)) {
            throw TimecodeCryptoException("Sai magic HSLT.")
        }
        if (blob[magic.size].toInt() != formatVersion) {
            throw TimecodeCryptoException("Phien ban ma hoa chua duoc ho tro.")
        }

        val saltStart = magic.size + 1
        val nonceStart = saltStart + saltLength
        val cipherStart = nonceStart + nonceLength
        val cipherEnd = blob.size - tagLength
        val salt = blob.copyOfRange(saltStart, nonceStart)
        val nonce = blob.copyOfRange(nonceStart, cipherStart)
        val ciphertext = blob.copyOfRange(cipherStart, cipherEnd)
        val tag = blob.copyOfRange(cipherEnd, blob.size)

        val (encKey, macKey) = deriveKeys(salt)
        val header = magic + byteArrayOf(formatVersion.toByte()) + salt + nonce
        val expectedTag = hmacSha256(macKey, header + ciphertext)
        if (!MessageDigest.isEqual(expectedTag, tag)) {
            throw TimecodeCryptoException("Chu ky khong khop, file co the da bi sua.")
        }

        val stream = keystream(encKey, nonce, ciphertext.size)
        val plain = ByteArray(ciphertext.size)
        for (i in ciphertext.indices) {
            plain[i] = (ciphertext[i].toInt() xor stream[i].toInt()).toByte()
        }

        return try {
            JSONObject(String(plain, Charsets.UTF_8))
        } catch (_: Exception) {
            throw TimecodeCryptoException("Noi dung sau giai ma khong phai JSON hop le.")
        }
    }

    internal fun deriveKeysForTest(salt: ByteArray): Pair<ByteArray, ByteArray> = deriveKeys(salt)

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256")
            .apply { init(SecretKeySpec(key, "HmacSHA256")) }
            .doFinal(data)

    private fun deriveKeys(salt: ByteArray): Pair<ByteArray, ByteArray> {
        val keyMaterial = try {
            val spec = PBEKeySpec(
                String(appSecret, Charsets.UTF_8).toCharArray(),
                salt,
                pbkdf2Iterations,
                64 * 8
            )
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } catch (_: Exception) {
            pbkdf2HmacSha256(appSecret, salt, pbkdf2Iterations, 64)
        }
        return keyMaterial.copyOfRange(0, 32) to keyMaterial.copyOfRange(32, 64)
    }

    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val hLen = 32
        val blocks = (length + hLen - 1) / hLen
        val output = ByteArray(blocks * hLen)
        var offset = 0

        for (blockIndex in 1..blocks) {
            val saltBlock = salt + ByteBuffer.allocate(4).putInt(blockIndex).array()
            var u = hmacSha256(password, saltBlock)
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = hmacSha256(password, u)
                for (i in t.indices) {
                    t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
                }
            }
            System.arraycopy(t, 0, output, offset, hLen)
            offset += hLen
        }

        return output.copyOf(length)
    }

    private fun keystream(encKey: ByteArray, nonce: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length)
        var counter = 0L
        var pos = 0
        while (pos < length) {
            val ctr = ByteBuffer.allocate(8).putLong(counter).array()
            val block = hmacSha256(encKey, nonce + ctr)
            val n = minOf(block.size, length - pos)
            System.arraycopy(block, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }
}
