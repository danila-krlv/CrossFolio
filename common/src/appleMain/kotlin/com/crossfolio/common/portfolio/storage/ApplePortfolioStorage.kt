package com.crossfolio.common.portfolio.storage

import androidx.room3.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

fun createApplePortfolioStorage(
    databasePath: String = defaultPortfolioDatabasePath(),
): PortfolioStorage = createRoomPortfolioStorage(
    Room.databaseBuilder<PortfolioDatabase>(name = databasePath),
)

@OptIn(ExperimentalForeignApi::class)
private fun defaultPortfolioDatabasePath(): String {
    val baseDirectory = requireNotNull(
        NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String,
    )
    val directory = "$baseDirectory/CrossFolio"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return "$directory/crossfolio.db"
}
