package com.crossfolio.android.storage

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import com.crossfolio.android.network.NetworkManagerChecks
import com.crossfolio.common.profile.AppTheme
import java.security.KeyStore
import java.util.UUID

class ProfileStorageInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        testPreferencesStoragePersistsUserNameAndTheme()
        testSecureStorageEncryptsAndPersistsApiKey()
        val networkTests = NetworkManagerChecks.run()
        finish(Activity.RESULT_OK, Bundle().apply {
            putString("result", "2 storage and $networkTests network tests passed")
        })
    }

    private fun testPreferencesStoragePersistsUserNameAndTheme() {
        val preferencesName = "profile_preferences_test_${UUID.randomUUID()}"
        val context = targetContext

        try {
            AndroidProfilePreferencesStorage(context, preferencesName).apply {
                userName = "Danila"
                theme = AppTheme.DARK
            }

            AndroidProfilePreferencesStorage(context, preferencesName).also { storage ->
                check(storage.userName == "Danila")
                check(storage.theme == AppTheme.DARK)
            }
        } finally {
            context.deleteSharedPreferences(preferencesName)
        }
    }

    private fun testSecureStorageEncryptsAndPersistsApiKey() {
        val preferencesName = "profile_secure_preferences_test_${UUID.randomUUID()}"
        val keyAlias = "crossfolio_test_${UUID.randomUUID()}"
        val apiKey = "test-api-key"
        val context = targetContext

        try {
            AndroidProfileSecureStorage(context, preferencesName, keyAlias)
                .coinMarketCapApiKey = apiKey

            val persistedValue = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .getString("coinmarketcap_api_key", null)
            check(persistedValue != null)
            check(!persistedValue.contains(apiKey))
            check(
                AndroidProfileSecureStorage(context, preferencesName, keyAlias).coinMarketCapApiKey ==
                    apiKey,
            )
        } finally {
            context.deleteSharedPreferences(preferencesName)
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(keyAlias)
            }
        }
    }
}
