package com.crossfolio.common.portfolio.storage

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.NativeSQLiteDriver

internal actual fun portfolioSQLiteDriver(): SQLiteDriver = NativeSQLiteDriver()
