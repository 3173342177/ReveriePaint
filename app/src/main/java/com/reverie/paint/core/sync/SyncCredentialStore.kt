/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.reverie.paint.core.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object SyncCredentialStore {
    private const val PREFS = "paint_prefs"
    private const val KEY_ENCRYPTED = "sync_password_enc"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "reverie_sync_password"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12

    fun savePassword(
        context: Context,
        password: String,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (password.isEmpty()) {
            prefs.edit().remove(KEY_ENCRYPTED).apply()
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val packed = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, packed, 0, iv.size)
            System.arraycopy(cipherText, 0, packed, iv.size, cipherText.size)
            prefs
                .edit()
                .putString(KEY_ENCRYPTED, Base64.encodeToString(packed, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("ReverieSync", "保存同步凭据失败", e)
        }
    }

    fun loadPassword(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encoded = prefs.getString(KEY_ENCRYPTED, null) ?: return ""
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            if (packed.size <= IV_LEN) return ""
            val iv = packed.copyOfRange(0, IV_LEN)
            val cipherText = packed.copyOfRange(IV_LEN, packed.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("ReverieSync", "读取同步凭据失败", e)
            ""
        }
    }

    fun clearPassword(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ENCRYPTED).apply()
        try {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec =
            KeyGenParameterSpec
                .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
