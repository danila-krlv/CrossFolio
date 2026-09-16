package com.crossfolio.common.portfolio.storage

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

internal actual fun portfolioSQLiteDriver(): SQLiteDriver = BundledSQLiteDriver()
