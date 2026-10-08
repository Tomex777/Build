package com.tomex777.annie

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * L2: cases generated from an operation's input schema and limits, with no hand-written case per
 * operation. Runs against a synthetic schema that uses every property type the validator supports.
 * Point `schemas` at real providers' definitions once they can be built without Android classes.
 */
class GeneratedOperationCasesTest {
    private val opId = "gen.op"

    private val schema = OperationInputSchema(
        linkedMapOf(
            "text" to OperationProperty("string", required = true, minLength = 2, maxLength = 6),
            "mode" to OperationProperty("string", enumValues = setOf("a", "b")),
            "tag" to OperationProperty("string", pattern = Regex("[a-z]+")),
            "n" to OperationProperty("number"),
            "flag" to OperationProperty("boolean"),
            "obj" to OperationProperty("object"),
            "list" to OperationProperty("array"),
        ),
        additionalProperties = false,
    )

    private fun validValue(p: OperationProperty): Any = when (p.type) {
        "string" -> when {
            p.enumValues.isNotEmpty() -> p.enumValues.first()
            p.pattern != null -> "abc"
            else -> "ok"
        }
        "number" -> 1
        "boolean" -> true
        "object" -> JSONObject()
        "array" -> JSONArray()
        else -> error("unsupported type ${p.type}")
    }

    /** A value of a different JSON type. */
    private fun wrongValue(p: OperationProperty): Any = when (p.type) {
        "string" -> 5
        else -> "not-${p.type}"
    }

    private fun minimal(): JSONObject = JSONObject().also { input ->
        schema.properties.filter { it.value.required }.forEach { (k, v) -> input.put(k, validValue(v)) }
    }

    private fun assertInvalid(input: JSONObject, label: String) {
        try {
            schema.validate(opId, input)
            fail("$label: expected INVALID_ARGUMENT")
        } catch (error: AnnieError) {
            assertEquals(label, AnnieErrorCode.INVALID_ARGUMENT, error.code)
            assertEquals(label, opId, error.operation)
            assertNull("$label: cause must not be exposed", error.cause)
        }
    }

    @Test fun validMinimalPasses() {
        schema.validate(opId, minimal())
    }

    @Test fun everyPropertyAcceptsItsValidValue() {
        schema.properties.forEach { (name, prop) ->
            schema.validate(opId, minimal().put(name, validValue(prop)))
        }
    }

    @Test fun missingRequiredFieldIsInvalid() {
        schema.properties.filter { it.value.required }.forEach { (name, _) ->
            assertInvalid(minimal().also { it.remove(name) }, "missing-required:$name")
        }
    }

    @Test fun nullCountsAsMissingForRequiredFields() {
        schema.properties.filter { it.value.required }.forEach { (name, _) ->
            assertInvalid(minimal().put(name, JSONObject.NULL), "null-required:$name")
        }
    }

    @Test fun wrongTypeIsInvalid() {
        schema.properties.forEach { (name, prop) ->
            assertInvalid(minimal().put(name, wrongValue(prop)), "wrong-type:$name")
        }
    }

    @Test fun unknownFieldIsInvalid() {
        assertInvalid(minimal().put("surprise", 1), "extra-field")
    }

    @Test fun lengthBoundsHoldAtEdgesOnRequiredStrings() {
        schema.properties.filter { it.value.required && it.value.type == "string" }.forEach { (name, prop) ->
            prop.minLength?.let { min ->
                schema.validate(opId, minimal().put(name, "x".repeat(min)))
                assertInvalid(minimal().put(name, "x".repeat(min - 1)), "min-1:$name")
            }
            prop.maxLength?.let { max ->
                schema.validate(opId, minimal().put(name, "x".repeat(max)))
                assertInvalid(minimal().put(name, "x".repeat(max + 1)), "max+1:$name")
            }
        }
    }

    @Test fun enumAndPatternRejectOutsiders() {
        schema.properties.forEach { (name, prop) ->
            if (prop.enumValues.isNotEmpty()) assertInvalid(minimal().put(name, "zzz"), "enum:$name")
            prop.pattern?.let { assertInvalid(minimal().put(name, "NOT-LOWER-123"), "pattern:$name") }
        }
    }

    // ---- limits, through the registry ----

    private class FixedProvider(
        override val operations: List<OperationDefinition>,
        private val result: JSONObject,
    ) : OperationProvider {
        override val id = "gen"
        override val version = "1"
        override suspend fun invoke(operation: OperationDefinition, invocation: OperationInvocation, input: JSONObject) = result
    }

    private fun definition(maxIn: Int = 64, maxOut: Int = 64) = OperationDefinition(
        id = opId, namespace = "gen", name = "op", capability = "gen", permissions = listOf("gen.use"),
        provider = "gen", since = 1,
        input = OperationInputSchema(linkedMapOf("v" to OperationProperty("string"))),
        maxInputBytes = maxIn, maxOutputBytes = maxOut,
    )

    private val granted = OperationInvocation(
        packageId = "p", isPackage = true,
        declaredCapabilities = setOf("gen"), declaredPermissions = setOf("gen.use"), grantedPermissions = setOf("gen.use"),
    )

    private fun registry(def: OperationDefinition, result: JSONObject) =
        OperationRegistry().apply { register(FixedProvider(listOf(def), result)) }

    @Test fun oversizeInputIsResourceLimit() = runBlocking {
        val registry = registry(definition(maxIn = 32), JSONObject().put("ok", true))
        val big = JSONObject().put("v", "x".repeat(100)).toString()
        try { registry.invoke(opId, granted, big); fail() } catch (e: AnnieError) {
            assertEquals(AnnieErrorCode.RESOURCE_LIMIT, e.code)
        }
    }

    @Test fun oversizeOutputIsResourceLimitNeverTruncated() = runBlocking {
        val registry = registry(definition(maxOut = 32), JSONObject().put("blob", "y".repeat(200)))
        try { registry.invoke(opId, granted, "{}"); fail() } catch (e: AnnieError) {
            assertEquals(AnnieErrorCode.RESOURCE_LIMIT, e.code)
        }
    }

    @Test fun inputAtTheLimitStillSucceeds() = runBlocking {
        val registry = registry(definition(maxIn = 64), JSONObject().put("ok", true))
        val result = registry.invoke(opId, granted, JSONObject().put("v", "x".repeat(10)).toString())
        assertTrue(JSONObject(result).getBoolean("ok"))
    }

    @Test fun failingTwoGatesReportsTheEarliest() = runBlocking {
        val registry = registry(definition(), JSONObject().put("ok", true))
        val notPackageAndNotDeclared = granted.copy(isPackage = false, declaredCapabilities = emptySet())
        try { registry.invoke(opId, notPackageAndNotDeclared, "{}"); fail() } catch (e: AnnieError) {
            assertEquals(AnnieErrorCode.NOT_A_PACKAGE, e.code)
        }
        val undeclaredAndBadInput = granted.copy(declaredCapabilities = emptySet())
        try { registry.invoke(opId, undeclaredAndBadInput, "[1]"); fail() } catch (e: AnnieError) {
            assertEquals(AnnieErrorCode.NOT_DECLARED, e.code)
        }
        val ungrantedAndBadInput = granted.copy(grantedPermissions = emptySet())
        try { registry.invoke(opId, ungrantedAndBadInput, "[1]"); fail() } catch (e: AnnieError) {
            assertEquals(AnnieErrorCode.NOT_GRANTED, e.code)
        }
    }

    @Test fun errorShapeAlwaysHasOperationAndNoCause() = runBlocking {
        val registry = registry(definition(), JSONObject().put("ok", true))
        val bad = listOf("[1]", "{\"v\":5}", "{\"extra\":1}", "{} {}")
        bad.forEach { raw ->
            try { registry.invoke(opId, granted, raw); fail("accepted $raw") } catch (e: AnnieError) {
                assertEquals(opId, e.operation)
                assertNull(e.cause)
                assertTrue(e.message.isNotBlank())
            }
        }
    }
}