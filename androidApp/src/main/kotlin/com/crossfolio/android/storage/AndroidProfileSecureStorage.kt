package com.crossfolio.android.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.crossfolio.common.profile.ProfileSecureStorage
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidProfileSecureStorage(
    context: Context,
    preferencesName: String = "profile_secure_preferences",
    private val keyAlias: String = "crossfolio_coinmarketcap_api_key",
) : ProfileSecureStorage {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override var coinMarketCapApiKey: String
        get() {
            val encodedValue = preferences.getString(API_KEY, null) ?: return ""
            return try {
                val encryptedValue = Base64.decode(encodedValue, Base64.NO_WRAP)
                if (encryptedValue.size <= IV_LENGTH_BYTES) return ""
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(TAG_LENGTH_BITS, encryptedValue, 0, IV_LENGTH_BYTES),
                )
                String(
                    cipher.doFinal(encryptedValue, IV_LENGTH_BYTES, encryptedValue.size - IV_LENGTH_BYTES),
                    Charsets.UTF_8,
                )
            } catch (_: GeneralSecurityException) {
                ""
            } catch (_: IllegalArgumentException) {
                ""
            }
        }
        set(value) {
            if (value.isEmpty()) {
                preferences.edit().remove(API_KEY).apply()
                return
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encryptedValue = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            preferences.edit()
                .putString(API_KEY, Base64.encodeToString(encryptedValue, Base64.NO_WRAP))
                .apply()
        }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val API_KEY = "coinmarketcap_api_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val IV_LENGTH_BYTES = 12
    }
}
