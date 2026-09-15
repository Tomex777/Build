package com.night.sora.extension

/**
 * Provider policy owned by Sora Core.
 *
 * Extensions marked `diagnostic` are useful for validating the extension API,
 * but they must never leak test/demo catalogue rows into normal user shelves.
 */
fun InstalledExtension.isCatalogProvider(): Boolean =
    error == null && descriptor?.capabilities?.contains("diagnostic") != true
