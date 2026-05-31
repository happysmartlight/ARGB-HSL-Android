# Task brief — Update Android plugin to decrypt the HSL Timecode file

> **Audience:** the AI developer working on the **Android app** (Kotlin).
> **Goal:** the desktop HSL Tool now writes `timecode_config.json` **encrypted**. The Android
> plugin currently parses it as plain JSON — that will break. You must add a decrypt step that
> turns the file back into the same JSON object you parse today, then leave the rest of your
> pipeline unchanged.

This is a **format/obfuscation lock**, not real cryptographic secrecy: the secret key is embedded
in both apps. Treat the key as a shared constant, not a credential.

---

## 1. What changed

| Before | After |
|--------|-------|
| File on disk = plain JSON (the object in the integration guide, Section 2) | File on disk = `{ "hsl_timecode": "<base64>", "v": 1 }` — an encrypted container |
| You did `JSONObject(fileText)` and parsed it | You must **decrypt first**, then `JSONObject(decryptedText)` |

Everything after parsing (mock→real mapping, baking presets, building the playlist, uploading)
**stays exactly the same**. Only the file-reading entry point changes.

Authoritative reference for the format & algorithm: **Section 0** of
[`timecode-json-android-plugin.md`](timecode-json-android-plugin.md). The desktop source of truth
is [`services/timecode_crypto.py`](../services/timecode_crypto.py). Every constant must match it.

---

## 2. What to do (checklist)

- [ ] Add a new file `TimecodeCrypto.kt` (decrypt-only is enough; add encrypt only if the app saves files).
- [ ] No new Gradle dependencies — use the platform `javax.crypto` + `java.security` + `android.util.Base64`.
- [ ] Find the single place where you currently read the file and do `JSONObject(text)` / parse.
- [ ] Replace that call with `decryptTimecode(fileText)` (returns the same `JSONObject` you parse now).
- [ ] Wrap it in error handling: a bad/old/tampered file must show a clear message, not crash.
- [ ] Validate against the **test vector** (Section 4) before testing with a real file.

---

## 3. Code to add

Create `TimecodeCrypto.kt`:

```kotlin
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/** Thrown when the file is not a valid HSL timecode file or was tampered with. */
class TimecodeCryptoException(message: String) : Exception(message)

object TimecodeCrypto {
    // MUST match services/timecode_crypto.py in the desktop tool.
    private val APP_SECRET =
        "HSL.TIMECODE.v1.4f2c8a91-6b0e-4d3a-9c77-happysmartlight".toByteArray(Charsets.UTF_8)
    private const val PBKDF2_ITERS = 200_000
    private val MAGIC = byteArrayOf(0x48, 0x53, 0x4C, 0x54) // "HSLT"
    private const val FORMAT_VER = 1
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 16
    private const val TAG_LEN = 32

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256")
            .apply { init(SecretKeySpec(key, "HmacSHA256")) }
            .doFinal(data)

    private fun deriveKeys(salt: ByteArray): Pair<ByteArray, ByteArray> {
        // APP_SECRET is ASCII, so char[] maps 1:1 to the Python bytes.
        val spec = PBEKeySpec(
            String(APP_SECRET, Charsets.UTF_8).toCharArray(), salt, PBKDF2_ITERS, 64 * 8
        )
        val km = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return km.copyOfRange(0, 32) to km.copyOfRange(32, 64)
    }

    private fun keystream(encKey: ByteArray, nonce: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length)
        var counter = 0L
        var pos = 0
        while (pos < length) {
            val ctr = ByteBuffer.allocate(8).putLong(counter).array() // big-endian
            val block = hmacSha256(encKey, nonce + ctr)
            val n = minOf(block.size, length - pos)
            System.arraycopy(block, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }

    /** Decrypt the file text and return the inner JSON object to parse. */
    fun decryptTimecode(fileText: String): JSONObject {
        val envelope = try {
            JSONObject(fileText)
        } catch (e: Exception) {
            throw TimecodeCryptoException("Not valid JSON")
        }
        if (!envelope.has("hsl_timecode")) {
            throw TimecodeCryptoException("Not an encrypted HSL timecode file")
        }
        val blob = try {
            Base64.decode(envelope.getString("hsl_timecode"), Base64.DEFAULT)
        } catch (e: Exception) {
            throw TimecodeCryptoException("Corrupt base64")
        }

        val minLen = 4 + 1 + SALT_LEN + NONCE_LEN + TAG_LEN
        if (blob.size < minLen) throw TimecodeCryptoException("File too short")
        if (!blob.copyOfRange(0, 4).contentEquals(MAGIC)) throw TimecodeCryptoException("Bad magic")
        if (blob[4].toInt() != FORMAT_VER) throw TimecodeCryptoException("Unsupported version")

        val salt = blob.copyOfRange(5, 5 + SALT_LEN)
        val nonce = blob.copyOfRange(5 + SALT_LEN, 5 + SALT_LEN + NONCE_LEN)
        val ct = blob.copyOfRange(5 + SALT_LEN + NONCE_LEN, blob.size - TAG_LEN)
        val tag = blob.copyOfRange(blob.size - TAG_LEN, blob.size)

        val (encKey, macKey) = deriveKeys(salt)

        // Verify BEFORE decrypting (Encrypt-then-MAC).
        val header = MAGIC + byteArrayOf(FORMAT_VER.toByte()) + salt + nonce
        val expected = hmacSha256(macKey, header + ct)
        if (!MessageDigest.isEqual(expected, tag)) {
            throw TimecodeCryptoException("Tag mismatch — file tampered")
        }

        val ks = keystream(encKey, nonce, ct.size)
        val plain = ByteArray(ct.size)
        for (i in ct.indices) plain[i] = (ct[i].toInt() xor ks[i].toInt()).toByte()

        return try {
            JSONObject(String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            throw TimecodeCryptoException("Decrypted content is not valid JSON")
        }
    }
}
```

---

## 4. Wire it into the existing parse flow

Find your current file-reading code. It probably looks like one of these:

```kotlin
// BEFORE
val text = file.readText(Charsets.UTF_8)
val config = JSONObject(text)            // <-- old plaintext parse
parseTimecodeConfig(config)
```

Change it to:

```kotlin
// AFTER
val text = file.readText(Charsets.UTF_8)
val config = try {
    TimecodeCrypto.decryptTimecode(text)
} catch (e: TimecodeCryptoException) {
    showError("File không phải định dạng Timecode HSL hợp lệ hoặc đã bị chỉnh sửa.\n${e.message}")
    return
}
parseTimecodeConfig(config)              // unchanged — same object as before
```

> The object returned by `decryptTimecode` is **identical** to what `JSONObject(text)` used to
> return for the old plaintext files. Do not change `parseTimecodeConfig` or anything downstream.

---

## 5. Test vector — validate before touching real files

Hard-code this in a unit test. With the production `APP_SECRET` and the fixed salt/nonce below,
your code must reproduce these exact bytes and decrypt back to the plaintext.

| Field | Value |
|-------|-------|
| plaintext | `{"version":1,"hello":"HSL"}` |
| salt (hex) | `00112233445566778899aabbccddeeff` |
| nonce (hex) | `ffeeddccbbaa99887766554433221100` |
| encKey (hex) | `2f24e3d05c72fa0d2b8356a6b7cffa36e60e9c891953a2444f79bbf6603394a3` |
| macKey (hex) | `ffae6e116beed5ec87f390caff54aee44102b9f14e4e34f472fe7284eb4a6a43` |
| ciphertext (hex) | `d4717dae8460d23586b3208a34bc0101d4d76e860bbb7e0c6ea9a7` |
| tag (hex) | `e3da01369ec52c17b97b431c2668a38738e9bcbb8d22ac5a3cff6c1ca9fa31e9` |

End-to-end envelope (decrypting it must return `{"version":1,"hello":"HSL"}`):

```json
{ "hsl_timecode": "SFNMVAEAESIzRFVmd4iZqrvM3e7//+7dzLuqmYh3ZlVEMyIRANRxfa6EYNI1hrMgijS8AQHU126GC7t+DG6pp+PaATaexSwXuXtDHCZoo4c46by7jSKsWjz/bByp+jHp", "v": 1 }
```

Suggested test assertions:

1. `deriveKeys(salt)` returns the `encKey`/`macKey` above → confirms PBKDF2 params match.
2. `decryptTimecode(envelope)` returns a `JSONObject` equal to `{"version":1,"hello":"HSL"}`.
3. Flip any byte of the base64 → `decryptTimecode` throws `TimecodeCryptoException` (tag check).
4. Pass an old plaintext JSON (no `hsl_timecode` key) → throws `TimecodeCryptoException`.

> Real files use a **random** salt/nonce each save, so their base64 differs every time. Only the
> decrypted plaintext is stable. The fixed values above exist solely to verify the algorithm.

---

## 6. Common pitfalls

- **Counter endianness:** the CTR counter is **big-endian uint64** (`ByteBuffer.putLong`, which is
  big-endian by default). Little-endian → wrong keystream → garbage/tag mismatch.
- **PBKDF2 algorithm name:** must be `PBKDF2WithHmacSHA256` (not `...WithHmacSHA1`, the old default).
- **Key length:** derive **64 bytes** (`64 * 8` bits), split 32/32. Not 32.
- **Verify before decrypt:** always check the tag with `MessageDigest.isEqual` (constant-time)
  before XOR-ing. Never parse unauthenticated plaintext.
- **APP_SECRET drift:** if the desktop tool changes `APP_SECRET`, update it here too or all new
  files fail to decrypt. Keep them in sync.
- **Don't log the decrypted content or keys** in release builds.
