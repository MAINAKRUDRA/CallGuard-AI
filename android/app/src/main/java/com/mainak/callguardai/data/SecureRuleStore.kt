package com.mainak.callguardai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores all allow/block rules as one AES-256-GCM authenticated ciphertext.
 * The AES key is non-exportable and protected by Android Keystore.
 */
class SecureRuleStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized fun allowed(): Set<String> = readRules().first
    @Synchronized fun blocked(): Set<String> = readRules().second

    @Synchronized fun addAllow(number: String): Boolean = update { it.first.add(number) }
    @Synchronized fun addBlock(number: String): Boolean = update { it.second.add(number) }
    @Synchronized fun removeAllow(number: String): Boolean = update { it.first.remove(number) }
    @Synchronized fun removeBlock(number: String): Boolean = update { it.second.remove(number) }

    @Synchronized private fun update(change: (Pair<MutableSet<String>, MutableSet<String>>) -> Boolean): Boolean {
        val current = readRules()
        val mutable = Pair(current.first.toMutableSet(), current.second.toMutableSet())
        val changed = change(mutable)
        if (!changed) return true
        val payload = JSONObject()
            .put("allow", JSONArray(mutable.first.toList()))
            .put("block", JSONArray(mutable.second.toList()))
            .toString()
        return try {
            val encrypted = encrypt(payload.toByteArray(StandardCharsets.UTF_8))
            prefs.edit().putString(DATA_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()
        } catch (error: Exception) {
            throw IllegalStateException("Unable to encrypt local call rules", error)
        }
    }

    @Synchronized private fun readRules(): Pair<Set<String>, Set<String>> {
        val encryptedText = prefs.getString(DATA_KEY, null)
        if (encryptedText != null) {
            return try {
                val bytes = Base64.decode(encryptedText, Base64.NO_WRAP)
                decode(String(decrypt(bytes), StandardCharsets.UTF_8))
            } catch (error: Exception) {
                // Fail closed for rule integrity: do not silently overwrite corrupted ciphertext.
                throw IllegalStateException("Encrypted call rules could not be authenticated", error)
            }
        }
        return migrateLegacyRules()
    }

    private fun migrateLegacyRules(): Pair<Set<String>, Set<String>> {
        val legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val allow = legacy.getStringSet("allow_numbers", emptySet()).orEmpty().toSet()
        val block = legacy.getStringSet("block_numbers", emptySet()).orEmpty().toSet()
        if (allow.isNotEmpty() || block.isNotEmpty()) {
            val payload = JSONObject()
                .put("allow", JSONArray(allow.toList()))
                .put("block", JSONArray(block.toList()))
                .toString()
            try {
                val encrypted = encrypt(payload.toByteArray(StandardCharsets.UTF_8))
                if (!prefs.edit().putString(DATA_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
                    throw IllegalStateException("Could not persist migrated encrypted rules")
                }
                legacy.edit().remove("allow_numbers").remove("block_numbers").commit()
            } catch (error: Exception) {
                throw IllegalStateException("Could not migrate legacy call rules", error)
            }
        }
        return Pair(allow, block)
    }

    private fun decode(json: String): Pair<Set<String>, Set<String>> {
        val obj = JSONObject(json)
        fun values(key: String): Set<String> {
            val array = obj.optJSONArray(key) ?: JSONArray()
            return buildSet { for (i in 0 until array.length()) add(array.getString(i)) }
        }
        return Pair(values("allow"), values("block"))
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    private fun decrypt(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty()) { "Empty encrypted payload" }
        val ivSize = payload[0].toInt() and 0xff
        require(ivSize in 12..16 && payload.size > 1 + ivSize) { "Invalid encrypted payload" }
        val iv = payload.copyOfRange(1, 1 + ivSize)
        val ciphertext = payload.copyOfRange(1 + ivSize, payload.size)
        val key = getExistingKey()
            ?: throw IllegalStateException("Call rules encryption key is missing; encrypted rules were preserved")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
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

    private companion object {
        const val PREFS = "callguard_secure_rules"
        const val DATA_KEY = "rules_aes_gcm"
        const val LEGACY_PREFS = "callguard_rules"
        const val KEY_ALIAS = "callguard.rules.aes256.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
