package com.night.sora.extension

/**
 * Only providers explicitly declaring the `catalog` capability may populate
 * Sora's discovery/search shelves. A normal watch/read source extension can be
 * installed without ever changing the catalog UI or mixing its browse results
 * into Sora's metadata providers.
 *
 * Diagnostic API-test extensions are excluded even if they expose browse/search
 * methods, because those methods exist only to exercise the contract.
 */
fun InstalledExtension.isCatalogProvider(): Boolean =
    error == null &&
        descriptor?.capabilities?.contains("catalog") == true &&
        descriptor.capabilities.contains("diagnostic") != true
