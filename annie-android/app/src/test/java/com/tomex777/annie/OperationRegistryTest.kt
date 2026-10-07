package com.tomex777.annie

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationRegistryTest {
    private class FakeProvider(
        private val definition: OperationDefinition,
        private val result: JSONObject = JSONObject().put("ok", true),
        private val failure: Throwable? = null,
    ) : OperationProvider {
        override val id: String = "fake"
        override val version: String = "1"
        override val operations: List<OperationDefinition> = listOf(definition)

        override suspend fun invoke(
            operation: OperationDefinition,
            invocation: OperationInvocation,
            input: JSONObject,
        ): JSONObject {
            failure?.let { throw it }
            return result
        }
    }

    private fun definition(
        id: String = "test.echo",
        property: OperationProperty = OperationProperty("string", required = true),
    ) = OperationDefinition(
        id = id,
        namespace = id.substringBeforeLast('.'),
        name = id.substringAfterLast('.'),
        capability = "test.echo",
        permissions = listOf("test.echo"),
        provider = "fake",
        since = 1,
        input = OperationInputSchema(linkedMapOf("value" to property)),
    )

    private fun invocation(
        isPackage: Boolean = true,
        capabilities: Set<String> = setOf("test.echo"),
        permissions: Set<String> = setOf("test.echo"),
        granted: Set<String> = setOf("test.echo"),
    ) = OperationInvocation(
        packageId = "pkg",
        isPackage = isPackage,
        declaredCapabilities = capabilities,
        declaredPermissions = permissions,
        grantedPermissions = granted,
    )

    @Test fun registryRejectsDuplicateOperationIds() {
        val first = definition()
        val registry = OperationRegistry().apply { register(FakeProvider(first)) }
        try {
            registry.register(FakeProvider(definition()))
            assertTrue("duplicate registration should fail", false)
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("already registered"))
        }
    }

    @Test fun gateOrderStartsWithPackageCheck() = runBlocking {
        val op = definition()
        val registry = OperationRegistry().apply { register(FakeProvider(op)) }
        val error = runCatching {
            registry.invoke(
                op.id,
                invocation(isPackage = false, capabilities = emptySet(), permissions = emptySet(), granted = emptySet()),
                JSONObject().put("value", 1).toString(),
            )
        }.exceptionOrNull() as AnnieError
        assertEquals(AnnieErrorCode.NOT_A_PACKAGE, error.code)
        assertEquals(op.id, error.operation)
    }

    @Test fun declaredAndGrantedPermissionsAreDistinctGates() = runBlocking {
        val op = definition()
        val registry = OperationRegistry().apply { register(FakeProvider(op)) }

        val notDeclared = runCatching {
            registry.invoke(op.id, invocation(permissions = emptySet(), granted = emptySet()), JSONObject().put("value", "x").toString())
        }.exceptionOrNull() as AnnieError
        assertEquals(AnnieErrorCode.NOT_DECLARED, notDeclared.code)
        assertEquals("test.echo", notDeclared.permission)

        val notGranted = runCatching {
            registry.invoke(op.id, invocation(granted = emptySet()), JSONObject().put("value", "x").toString())
        }.exceptionOrNull() as AnnieError
        assertEquals(AnnieErrorCode.NOT_GRANTED, notGranted.code)
        assertEquals("test.echo", notGranted.permission)
    }

    @Test fun optionalEmptyStringRemainsCompatibleWithLegacyWrappers() = runBlocking {
        val op = definition(property = OperationProperty(
            type = "string",
            required = false,
            pattern = Regex("[A-Za-z]{2,8}"),
        ))
        val registry = OperationRegistry().apply { register(FakeProvider(op)) }
        val json = registry.invoke(op.id, invocation(), JSONObject().put("value", "").toString())
        assertTrue(JSONObject(json).optBoolean("ok"))
    }

    @Test fun unknownFieldsAreInvalidInStrictRegistrySchema() = runBlocking {
        val op = definition()
        val registry = OperationRegistry().apply { register(FakeProvider(op)) }
        val error = runCatching {
            registry.invoke(
                op.id,
                invocation(),
                JSONObject().put("value", "x").put("extra", true).toString(),
            )
        }.exceptionOrNull() as AnnieError
        assertEquals(AnnieErrorCode.INVALID_ARGUMENT, error.code)
    }

    @Test fun publicErrorEnvelopeContainsNoCause() {
        val json = AnnieError(
            AnnieErrorCode.RATE_LIMITED,
            "try again",
            "test.echo",
            retryable = true,
            retryAfterMs = 5000,
            permission = "test.echo",
        ).toPublicJson()
        assertEquals("RATE_LIMITED", json.getString("code"))
        assertEquals("test.echo", json.getString("operation"))
        assertTrue(json.getBoolean("retryable"))
        assertEquals(5000, json.getLong("retryAfterMs"))
        assertFalse(json.has("cause"))
    }
    @Test fun everyDeclaredCapabilityMustBePresent() = runBlocking {
        val op = definition().copy(capabilities = setOf("test.echo", "test.network"))
        val registry = OperationRegistry().apply { register(FakeProvider(op)) }
        val error = runCatching {
            registry.invoke(op.id, invocation(capabilities = setOf("test.echo")), JSONObject().put("value", "x").toString())
        }.exceptionOrNull() as AnnieError
        assertEquals(AnnieErrorCode.NOT_DECLARED, error.code)
        assertEquals(op.id, error.operation)
    }

    @Test fun providerCannotEmitUndeclaredErrorCode() = runBlocking {
        val op = definition()
        val registry = OperationRegistry().apply {
            register(
                FakeProvider(
                    op,
                    failure = AnnieError(
                        AnnieErrorCode.UNSUPPORTED,
                        "fake provider failure",
                        op.id,
                    ),
                )
            )
        }
        val error = runCatching {
            registry.invoke(op.id, invocation(), JSONObject().put("value", "x").toString())
        }.exceptionOrNull() as AnnieError
        assertEquals(AnnieErrorCode.INTERNAL, error.code)
        assertEquals(op.id, error.operation)
    }

    @Test fun objectSchemaAcceptsJsonObjects() = runBlocking {
        val op = definition().copy(input = OperationInputSchema(
            linkedMapOf("value" to OperationProperty("object", required = true)),
        ))
        val registry = OperationRegistry().apply { register(FakeProvider(op)) }
        val json = registry.invoke(
            op.id,
            invocation(),
            JSONObject().put("value", JSONObject().put("nested", true)).toString(),
        )
        assertTrue(JSONObject(json).optBoolean("ok"))
    }
}
