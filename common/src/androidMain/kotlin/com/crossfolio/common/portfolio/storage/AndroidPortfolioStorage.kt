package com.crossfolio.common.portfolio.storage

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun createAndroidPortfolioStorage(
    context: Context,
    databaseName: String = "crossfolio.db",
): PortfolioStorage {
    val appContext = context.applicationContext
    val databasePath = appContext.getDatabasePath(databaseName).absolutePath
    return createRoomPortfolioStorage(
        Room.databaseBuilder<PortfolioDatabase>(
            context = appContext,
            name = databasePath,
        ),
        BundledSQLiteDriver(),
    )
}
