package com.mainak.callguardai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts call-history fields with AES-256-GCM. The key is non-exportable
 * and held by Android Keystore. Decryption never creates a replacement key.
 */
internal object HistoryCrypto {
    private const val KEY_ALIAS = "callguard.history.aes256.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(context: Context, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plaintext)
    }

    fun decrypt(context: Context, payload: ByteArray): ByteArray {
        require(payload.isNotEmpty()) { "Empty encrypted history field" }
        val ivSize = payload[0].toInt() and 0xff
        require(ivSize in 12..16 && payload.size > 1 + ivSize) { "Invalid encrypted history field" }
        val key = getExistingKey()
            ?: throw IllegalStateException("Call-history encryption key is missing; data was preserved")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, payload.copyOfRange(1, 1 + ivSize))
        )
        return cipher.doFinal(payload.copyOfRange(1 + ivSize, payload.size))
    }

    private fun getExistingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun getOrCreateKey(): SecretKey {
        getExistingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
