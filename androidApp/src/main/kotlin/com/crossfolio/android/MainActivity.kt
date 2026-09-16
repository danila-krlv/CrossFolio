package com.crossfolio.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.crossfolio.android.ui.navigation.TabBarScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val coordinator = (application as CrossFolioApplication).appContainer.tabBarCoordinator
        setContent {
            TabBarScreen(coordinator)
        }
    }
}
