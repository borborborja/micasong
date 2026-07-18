package com.micasong.player.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based encryption for the MiCaSong `.micabkp` backup archive — encrypted with a
 * password because it may contain provider credentials. Uses PBKDF2 key derivation and
 * AES-256-GCM (authenticated). The output blob is `salt(16) | iv(12) | ciphertext+tag`; decryption
 * returns null on a wrong password or tampering (the GCM tag fails). Pure JDK crypto — testable.
 */
object BackupCrypto {

    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun encrypt(plaintext: ByteArray, password: String, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val ciphertext = cipher.doFinal(plaintext)
        return salt + iv + ciphertext
    }

    fun decrypt(blob: ByteArray, password: String): ByteArray? {
        if (blob.size < SALT_LEN + IV_LEN) return null
        return runCatching {
            val salt = blob.copyOfRange(0, SALT_LEN)
            val iv = blob.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
            val ciphertext = blob.copyOfRange(SALT_LEN + IV_LEN, blob.size)
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            }
            cipher.doFinal(ciphertext)
        }.getOrNull()
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
