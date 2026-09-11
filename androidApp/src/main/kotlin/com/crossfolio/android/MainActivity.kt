package com.crossfolio.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.crossfolio.android.storage.AndroidProfilePreferencesStorage
import com.crossfolio.android.storage.AndroidProfileSecureStorage
import com.crossfolio.android.ui.TabBarScreen
import com.crossfolio.common.navigation.TabBarCoordinator
import com.crossfolio.common.profile.ProfileViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val coordinator = TabBarCoordinator(
            profileViewModel = ProfileViewModel(
                preferencesStorage = AndroidProfilePreferencesStorage(this),
                secureStorage = AndroidProfileSecureStorage(this),
            ),
        )
        setContent {
            TabBarScreen(coordinator)
        }
    }
}
