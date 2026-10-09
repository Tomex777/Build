package com.tomex777.annie

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Conformance L1 (static registry checks) and L2 (cases generated from each operation's own entry). */
class OperationConformanceTest {
    private class NoBackend : AndroidCapabilityBackend {
        override suspend fun speak(ownerPackageId: String, text: String, languageTag: String?, queueMode: String) = JSONObject()
        override suspend fun ttsStatus(ownerPackageId: String, utteranceId: String) = JSONObject()
        override suspend fun stopSpeech(ownerPackageId: String) = JSONObject()
        override suspend fun recognizeText(imageFile: File) = JSONObject()
        override suspend fun listen(languageTag: String?, prompt: String?) = JSONObject()
        override suspend fun pickTextDocument(mimeType: String) = JSONObject()
        override suspend fun inspectMedia(mediaFile: File) = JSONObject()
        override suspend fun postNotification(ownerPackageId: String, key: String?, title: String, text: String) = JSONObject()
        override suspend fun updateNotification(ownerPackageId: String, key: String, title: String, text: String) = JSONObject()
        override suspend fun cancelNotification(ownerPackageId: String, key: String) = JSONObject()
    }

    private val noAssets = object : PackageAssetResolver {
        override fun resolveAssetFile(projectId: String, logicalId: String): File = File("unused")
    }

    private fun coreDefinitions() = CoreAndroidOperationProvider(NoBackend(), noAssets).operations

    /** Core Android operations plus the downloads provider (its definitions are Android-free). */
    private fun allDefinitions() = coreDefinitions() + downloadOperationDefinitions() + messageOperationDefinitions()

    /** Echo provider that re-hosts a definition so generated cases never touch Android. */
    private class EchoProvider(
        override val operations: List<OperationDefinition>,
        private val output: () -> JSONObject = { JSONObject().put("ok", true) },
    ) : OperationProvider {
        override val id = "fake"
        override val version = "1"
        override suspend fun invoke(operation: OperationDefinition, invocation: OperationInvocation, input: JSONObject) = output()
    }

    private fun rehost(definition: OperationDefinition) = definition.copy(provider = "fake")

    private fun registryFor(definition: OperationDefinition, output: () -> JSONObject = { JSONObject().put("ok", true) }) =
        OperationRegistry().apply { register(EchoProvider(listOf(rehost(definition)), output)) }

    private fun invocationFor(d: OperationDefinition) = OperationInvocation(
        packageId = "pkg", isPackage = true,
        declaredCapabilities = d.capabilities, declaredPermissions = d.permissions.toSet(),
        grantedPermissions = d.permissions.toSet(),
    )

    private fun sample(property: OperationProperty): Any = when (property.type) {
        "string" -> {
            val enumValue = property.enumValues.firstOrNull()
            when {
                enumValue != null -> enumValue
                property.pattern != null -> listOf("a", "ab", "abc", "a1", "en", "en-US", "x.y", "key", "k_1")
                    .first { property.pattern.matches(it) && it.length >= (property.minLength ?: 0) && it.length <= (property.maxLength ?: Int.MAX_VALUE) }
                else -> "x".repeat((property.minLength ?: 1).coerceAtLeast(1))
            }
        }
        "object" -> JSONObject()
        "array" -> org.json.JSONArray()
        "number" -> 1
        "boolean" -> true
        "any" -> "x"
        else -> error("unsupported type ${property.type}")
    }

    private fun minimalInput(d: OperationDefinition) = JSONObject().also { json ->
        d.input.properties.filterValues { it.required }.forEach { (name, property) -> json.put(name, sample(property)) }
    }

    private fun wrongTypeValue(type: String): Any = if (type == "string") 12345 else "wrong"

    private fun errorOf(block: suspend () -> Unit): AnnieError =
        runCatching { runBlocking { block() } }.exceptionOrNull() as? AnnieError ?: error("expected an AnnieError")

    // ---------------- L1: static registry checks ----------------

    @Test fun l1_idsAreUniqueAndDerivedFromNamespaceAndName() {
        val ids = allDefinitions().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        allDefinitions().forEach { assertEquals(it.id, it.namespace + "." + it.name) }
    }

    @Test fun l1_everyReleasedIdStillExists() {
        val locked = javaClass.classLoader!!.getResourceAsStream("operation-ids.lock")!!
            .bufferedReader().readLines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }
        val current = allDefinitions().map { it.id }.toSet()
        val removed = locked.filterNot { it in current }
        assertTrue("Operation ids must never be removed or reused: $removed", removed.isEmpty())
    }

    @Test fun l1_strictSchemasLimitsAndErrorsAreSane() {
        allDefinitions().forEach { d ->
            assertTrue("${d.id} must be strict", !d.input.additionalProperties)
            assertTrue(d.maxInputBytes > 0 && d.maxOutputBytes > 0 && d.since > 0)
            listOf(AnnieErrorCode.NOT_A_PACKAGE, AnnieErrorCode.NOT_DECLARED, AnnieErrorCode.NOT_GRANTED).forEach {
                assertTrue("${d.id} must declare gate error $it", it in d.errors)
            }
            d.input.properties.forEach { (name, p) ->
                if (p.minLength != null && p.maxLength != null) assertTrue("${d.id}.$name min<=max", p.minLength <= p.maxLength)
            }
            assertTrue(d.permissions.isNotEmpty() && d.capabilities.isNotEmpty())
        }
    }

    @Test fun l1_objectPropertiesDeclareNestedSchemaOrAreExplicitlyFreeForm() {
        fun check(id: String, path: String, schema: OperationInputSchema) {
            assertTrue("$id$path must be strict", !schema.additionalProperties)
            schema.properties.forEach { (name, p) ->
                if (p.type == "object" || p.type == "any") assertTrue(
                    "$id.$path$name needs a nested schema or freeForm = true", p.nested != null || p.freeForm,
                )
                p.nested?.let { check(id, "$path$name.", it) }
            }
        }
        allDefinitions().forEach { check(it.id, "", it.input) }
    }

    @Test fun l1_registrationAcceptsTheWholeCoreProvider() {
        OperationRegistry().apply {
            register(CoreAndroidOperationProvider(NoBackend(), noAssets))
            register(object : OperationProvider {
                override val id = DOWNLOADS_PROVIDER_ID
                override val version = "1"
                override val operations = downloadOperationDefinitions()
                override suspend fun invoke(operation: OperationDefinition, invocation: OperationInvocation, input: JSONObject) = JSONObject()
            })
        }
    }

    // ---------------- L2: generated per-operation cases ----------------

    @Test fun l2_validMinimalSucceeds() = runBlocking {
        allDefinitions().forEach { d ->
            val json = registryFor(d).invoke(d.id, invocationFor(d), minimalInput(d).toString())
            assertTrue(d.id, JSONObject(json).optBoolean("ok"))
        }
    }

    @Test fun l2_missingRequiredIsInvalidArgument() {
        allDefinitions().forEach { d ->
            d.input.properties.filterValues { it.required }.keys.forEach { name ->
                val input = minimalInput(d).also { it.remove(name) }
                val error = errorOf { registryFor(d).invoke(d.id, invocationFor(d), input.toString()) }
                assertEquals("${d.id} missing $name", AnnieErrorCode.INVALID_ARGUMENT, error.code)
                assertEquals(d.id, error.operation)
            }
        }
    }

    @Test fun l2_wrongTypeIsInvalidArgument() {
        allDefinitions().forEach { d ->
            d.input.properties.forEach { (name, property) ->
                val input = minimalInput(d).put(name, wrongTypeValue(property.type))
                val error = errorOf { registryFor(d).invoke(d.id, invocationFor(d), input.toString()) }
                assertEquals("${d.id}.$name", AnnieErrorCode.INVALID_ARGUMENT, error.code)
            }
        }
    }

    @Test fun l2_extraFieldIsInvalidInStrictRegistry() {
        allDefinitions().forEach { d ->
            val error = errorOf { registryFor(d).invoke(d.id, invocationFor(d), minimalInput(d).put("zzUnknown", 1).toString()) }
            assertEquals(d.id, AnnieErrorCode.INVALID_ARGUMENT, error.code)
        }
    }

    @Test fun l2_lengthBoundsAreExactAndEnumsRejectUnknownValues() = runBlocking {
        allDefinitions().forEach { d ->
            d.input.properties.filterValues { it.type == "string" && it.pattern == null && it.enumValues.isEmpty() }.forEach { (name, p) ->
                p.maxLength?.let { max ->
                    registryFor(d).invoke(d.id, invocationFor(d), minimalInput(d).put(name, "x".repeat(max)).toString())
                    val error = errorOf { registryFor(d).invoke(d.id, invocationFor(d), minimalInput(d).put(name, "x".repeat(max + 1)).toString()) }
                    assertEquals("${d.id}.$name max", AnnieErrorCode.INVALID_ARGUMENT, error.code)
                }
                p.minLength?.takeIf { it > 0 && p.required }?.let { min ->
                    val error = errorOf { registryFor(d).invoke(d.id, invocationFor(d), minimalInput(d).put(name, "x".repeat(min - 1)).toString()) }
                    assertEquals("${d.id}.$name min", AnnieErrorCode.INVALID_ARGUMENT, error.code)
                }
            }
            d.input.properties.filterValues { it.enumValues.isNotEmpty() }.forEach { (name, _) ->
                val error = errorOf { registryFor(d).invoke(d.id, invocationFor(d), minimalInput(d).put(name, "not-an-option").toString()) }
                assertEquals("${d.id}.$name enum", AnnieErrorCode.INVALID_ARGUMENT, error.code)
            }
        }
    }

    @Test fun l2_oversizeInputIsResourceLimit() {
        allDefinitions().forEach { d ->
            val tiny = d.copy(maxInputBytes = 4)
            val error = errorOf { registryFor(tiny).invoke(d.id, invocationFor(d), minimalInput(d).toString().padEnd(64, ' ')) }
            assertEquals(d.id, AnnieErrorCode.RESOURCE_LIMIT, error.code)
        }
    }

    @Test fun l2_oversizeOutputIsResourceLimitNeverTruncated() {
        allDefinitions().forEach { d ->
            val tiny = d.copy(maxOutputBytes = 8)
            val error = errorOf {
                registryFor(tiny) { JSONObject().put("payload", "y".repeat(100)) }.invoke(d.id, invocationFor(d), minimalInput(d).toString())
            }
            assertEquals(d.id, AnnieErrorCode.RESOURCE_LIMIT, error.code)
        }
    }

    @Test fun l2_gatesReportInOrderPackageDeclaredGranted() {
        allDefinitions().forEach { d ->
            val input = minimalInput(d).toString()
            val loose = invocationFor(d).copy(isPackage = false, declaredCapabilities = emptySet(), declaredPermissions = emptySet(), grantedPermissions = emptySet())
            assertEquals(AnnieErrorCode.NOT_A_PACKAGE, errorOf { registryFor(d).invoke(d.id, loose, input) }.code)

            d.permissions.forEach { permission ->
                val undeclared = invocationFor(d).copy(declaredPermissions = d.permissions.toSet() - permission)
                val e1 = errorOf { registryFor(d).invoke(d.id, undeclared, input) }
                assertEquals(AnnieErrorCode.NOT_DECLARED, e1.code); assertEquals(permission, e1.permission)
                val ungranted = invocationFor(d).copy(grantedPermissions = d.permissions.toSet() - permission)
                val e2 = errorOf { registryFor(d).invoke(d.id, ungranted, input) }
                assertEquals(AnnieErrorCode.NOT_GRANTED, e2.code); assertEquals(permission, e2.permission)
            }
            // A call that fails two gates (not granted AND invalid input) reports the earlier gate.
            val both = errorOf { registryFor(d).invoke(d.id, invocationFor(d).copy(grantedPermissions = emptySet()), """{"zzUnknown":1}""") }
            assertEquals(AnnieErrorCode.NOT_GRANTED, both.code)
        }
    }

    @Test fun l2_unknownOperationIsUnsupported() {
        val d = allDefinitions().first()
        val error = errorOf { OperationRegistry().invoke(d.id, invocationFor(d), "{}") }
        assertEquals(AnnieErrorCode.UNSUPPORTED, error.code)
        assertEquals(d.id, error.operation)
    }

    @Test fun l2_everyFailureHasTheErrorShape() {
        val d = allDefinitions().first { it.input.properties.any { (_, p) -> p.required } }
        val error = errorOf { registryFor(d).invoke(d.id, invocationFor(d), "{}") }
        val json = error.toPublicJson()
        assertEquals(d.id, json.getString("operation"))
        assertTrue(json.has("code") && json.has("message") && json.has("retryable"))
        assertTrue(!json.has("cause"))
    }

    // ---------------- Nested strictness and declared provider errors ----------------

    private fun downloadsStart() = allDefinitions().first { it.id == "downloads.start" }

    private fun startInput(completion: String) =
        """{"url":"https://example.com/a.bin","completionAction":$completion}"""

    @Test fun l2_nestedUnknownFieldIsInvalidArgument() {
        val d = downloadsStart()
        val e = errorOf { registryFor(d).invoke(d.id, invocationFor(d), startInput("""{"action":"done","zz":1}""")) }
        assertEquals(AnnieErrorCode.INVALID_ARGUMENT, e.code)
        assertTrue(e.message, "completionAction.zz" in e.message)
    }

    @Test fun l2_nestedRequiredWrongTypeAndPatternAreInvalidArgument() {
        val d = downloadsStart()
        listOf("""{}""", """{"action":12}""", """{"action":"bad action!"}""", """{"action":"${"a".repeat(129)}"}""").forEach { bad ->
            val e = errorOf { registryFor(d).invoke(d.id, invocationFor(d), startInput(bad)) }
            assertEquals(bad, AnnieErrorCode.INVALID_ARGUMENT, e.code)
        }
        runBlocking { registryFor(d).invoke(d.id, invocationFor(d), startInput("""{"action":"done","payload":{"k":1}}""")) }
        runBlocking { registryFor(d).invoke(d.id, invocationFor(d), startInput("""{"action":"done","payload":5}""")) }
    }

    private fun throwingRegistry(definition: OperationDefinition, failure: Throwable): OperationRegistry =
        OperationRegistry().apply {
            register(object : OperationProvider {
                override val id = "fake"
                override val version = "1"
                override val operations = listOf(rehost(definition))
                override suspend fun invoke(operation: OperationDefinition, invocation: OperationInvocation, input: JSONObject): JSONObject = throw failure
            })
        }

    @Test fun l2_providerTypedErrorOutsideDeclaredSetBecomesInternal() {
        val d = allDefinitions().first { it.id == "android.tts.stop" } // does not declare NETWORK_ERROR
        assertTrue(AnnieErrorCode.NETWORK_ERROR !in d.errors)
        val e = errorOf { throwingRegistry(d, AnnieError(AnnieErrorCode.NETWORK_ERROR, "boom", d.id)).invoke(d.id, invocationFor(d), "{}") }
        assertEquals(AnnieErrorCode.INTERNAL, e.code)
        assertTrue(e.message, "NETWORK_ERROR" in e.message)
        assertEquals(d.id, e.operation)
    }

    @Test fun l2_providerTypedErrorInsideDeclaredSetKeepsRetryFields() {
        val d = downloadsStart() // declares RATE_LIMITED
        val failure = AnnieError(AnnieErrorCode.RATE_LIMITED, "slow down", d.id, retryable = true, retryAfterMs = 1500)
        val e = errorOf { throwingRegistry(d, failure).invoke(d.id, invocationFor(d), startInput("""{"action":"done"}""")) }
        assertEquals(AnnieErrorCode.RATE_LIMITED, e.code)
        assertEquals(1500L, e.retryAfterMs)
        assertTrue(e.retryable)
    }
}
