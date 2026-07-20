package pw.x4.ninety.data.persistence

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Boundary used by Room mappers; tests can provide a deterministic implementation. */
interface SecretCodec {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
}

/** AES/GCM key never leaves Android Keystore. Ciphertext includes a random IV. */
class AndroidKeystoreSecretCodec(
    private val alias: String = KEY_ALIAS,
) : SecretCodec {
    override fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        require(iv.size in 1..255) { "unsupported GCM IV length" }
        val packed = ByteArray(1 + iv.size + encrypted.size)
        packed[0] = iv.size.toByte()
        iv.copyInto(packed, destinationOffset = 1)
        encrypted.copyInto(packed, destinationOffset = 1 + iv.size)
        return PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    override fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        // Forward-compatible recovery for development builds that may have stored plaintext.
        if (!value.startsWith(PREFIX)) return value
        val packed = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
        require(packed.isNotEmpty()) { "empty encrypted payload" }
        val ivSize = packed[0].toInt() and 0xff
        require(ivSize > 0 && packed.size > 1 + ivSize) { "invalid encrypted payload" }
        val iv = packed.copyOfRange(1, 1 + ivSize)
        val encrypted = packed.copyOfRange(1 + ivSize, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "ninety-storage-v1"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "enc:v1:"
    }
}
