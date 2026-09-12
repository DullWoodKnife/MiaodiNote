package com.miaodi.note.utils

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * MD5-based encryption/decryption utility.
 * Uses MD5 hash of password as AES key for actual encryption (bidirectional).
 *
 * NOTE: MD5 is considered cryptographically weak for key derivation.
 * This implementation is provided per user request.
 * For production use with sensitive data, consider PBKDF2, bcrypt, or Argon2.
 */
object Md5Utils {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val IV_LENGTH = 16

    /**
     * Encrypt plain text with a password using MD5-derived key.
     * Returns base64(IV + encryptedBytes).
     *
     * @throws IllegalArgumentException if password or plainText is blank
     * @throws EncryptionException if encryption fails
     */
    @Throws(IllegalArgumentException::class, EncryptionException::class)
    fun encrypt(plainText: String, password: String): String {
        require(password.isNotBlank()) { "Password cannot be blank" }
        require(plainText.isNotBlank()) { "Plain text cannot be blank" }

        try {
            val keyBytes = md5Bytes(password)
            val secretKey = SecretKeySpec(keyBytes, ALGORITHM)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = iv + encrypted
            return Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: NoSuchAlgorithmException) {
            throw EncryptionException("Encryption algorithm not available", e)
        } catch (e: NoSuchPaddingException) {
            throw EncryptionException("Padding scheme not available", e)
        } catch (e: Exception) {
            throw EncryptionException("Encryption failed", e)
        }
    }

    /**
     * Decrypt base64 ciphertext with a password using MD5-derived key.
     *
     * @throws IllegalArgumentException if password or cipherText is blank
     * @throws DecryptionException if decryption fails (wrong password, invalid format, etc.)
     */
    @Throws(IllegalArgumentException::class, DecryptionException::class)
    fun decrypt(cipherText: String, password: String): String {
        require(password.isNotBlank()) { "Password cannot be blank" }
        require(cipherText.isNotBlank()) { "Cipher text cannot be blank" }

        try {
            val keyBytes = md5Bytes(password)
            val secretKey = SecretKeySpec(keyBytes, ALGORITHM)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val combined = Base64.decode(cipherText, Base64.DEFAULT)

            if (combined.size < IV_LENGTH) {
                throw DecryptionException("Invalid cipher text: too short")
            }

            val iv = combined.copyOfRange(0, IV_LENGTH)
            val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            return String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw DecryptionException("Invalid Base64 encoding", e)
        } catch (e: BadPaddingException) {
            throw DecryptionException("Wrong password or corrupted data", e)
        } catch (e: IllegalBlockSizeException) {
            throw DecryptionException("Invalid block size", e)
        } catch (e: NoSuchAlgorithmException) {
            throw DecryptionException("Decryption algorithm not available", e)
        } catch (e: NoSuchPaddingException) {
            throw DecryptionException("Padding scheme not available", e)
        } catch (e: Exception) {
            throw DecryptionException("Decryption failed", e)
        }
    }

    /**
     * Simple MD5 hash of a string (returns hex string).
     */
    fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** 别名：返回 MD5 hex 摘要（用户要求 PIN 等校验场景使用 MD5） */
    fun md5Hex(input: String): String = md5(input)

    @Throws(NoSuchAlgorithmException::class)
    private fun md5Bytes(input: String): ByteArray {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
    }

    class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class DecryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
