package com.tomex777.annie

import java.io.File

private class NullBackend : AndroidCapabilityBackend {
    override suspend fun speak(ownerPackageId: String, text: String, languageTag: String?, queueMode: String) = org.json.JSONObject()
    override suspend fun ttsStatus(ownerPackageId: String, utteranceId: String) = org.json.JSONObject()
    override suspend fun stopSpeech(ownerPackageId: String) = org.json.JSONObject()
    override suspend fun recognizeText(imageFile: File) = org.json.JSONObject()
    override suspend fun listen(languageTag: String?, prompt: String?) = org.json.JSONObject()
    override suspend fun pickTextDocument(mimeType: String) = org.json.JSONObject()
    override suspend fun inspectMedia(mediaFile: File) = org.json.JSONObject()
    override suspend fun postNotification(ownerPackageId: String, key: String?, title: String, text: String) = org.json.JSONObject()
    override suspend fun updateNotification(ownerPackageId: String, key: String, title: String, text: String) = org.json.JSONObject()
    override suspend fun cancelNotification(ownerPackageId: String, key: String) = org.json.JSONObject()
}

fun main(args: Array<String>) {
    val defs = CoreAndroidOperationProvider(NullBackend(), object : PackageAssetResolver {
        override fun resolveAssetFile(projectId: String, logicalId: String) = File("unused")
    }).operations + downloadOperationDefinitions() + messageOperationDefinitions()

    val ids = defs.map { it.id }
    check(ids.size == ids.distinct().size) { "Duplicate operation IDs" }
    val paths = defs.map(OperationTypings::jsPath)
    check(paths.size == paths.distinct().size) { "Duplicate JS paths" }
    defs.forEach { definition ->
        val names = definition.js.positional + definition.js.optionsKeys
        check(names.size == names.distinct().size)
        if (definition.js.spreadArg == null) check(names.toSet() == definition.input.properties.keys)
    }

    val generated = OperationTypings.dts(defs)
    val expected = File(args.single()).readText().replace("\r\n", "\n")
    check(generated == expected) {
        val mismatch = generated.zip(expected).indexOfFirst { (a, b) -> a != b }
        "Generated type drift: first mismatch at character $mismatch; generated ${generated.length}, committed ${expected.length}"
    }
    println("Operation artifact drift PASS: ${ids.size} operations; ${generated.length} characters")
}
