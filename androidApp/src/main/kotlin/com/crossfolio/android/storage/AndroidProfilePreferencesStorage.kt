package com.crossfolio.android.storage

import android.content.Context
import com.crossfolio.common.profile.AppTheme
import com.crossfolio.common.profile.ProfilePreferencesStorage

class AndroidProfilePreferencesStorage(
    context: Context,
    preferencesName: String = "profile_preferences",
) : ProfilePreferencesStorage {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override var userName: String
        get() = preferences.getString(USER_NAME_KEY, "").orEmpty()
        set(value) {
            preferences.edit().putString(USER_NAME_KEY, value).apply()
        }

    override var theme: AppTheme
        get() = preferences.getString(THEME_KEY, null)
            ?.let { storedTheme -> AppTheme.entries.firstOrNull { it.name == storedTheme } }
            ?: AppTheme.SYSTEM
        set(value) {
            preferences.edit().putString(THEME_KEY, value.name).apply()
        }

    private companion object {
        const val USER_NAME_KEY = "user_name"
        const val THEME_KEY = "theme"
    }
}
