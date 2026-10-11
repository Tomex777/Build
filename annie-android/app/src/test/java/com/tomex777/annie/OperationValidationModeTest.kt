package com.tomex777.annie

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Imported legacy packages keep permissive unknown-field handling; apiVersion 2 is strict. */
class OperationValidationModeTest {
    private val schema = OperationInputSchema(
        mapOf("known" to OperationProperty(type = "object", nested = OperationInputSchema(
            mapOf("requiredValue" to OperationProperty(type = "string", required = true))
        )))
    )

    @Test fun unknownTopLevelAndNestedFieldsFollowDialect() {
        val value = JSONObject("""{"known":{"requiredValue":"x","extra":true},"surprise":42}""")
        schema.validate("example.test", value, mode = ValidationMode.LEGACY)
        val error = assertThrows(AnnieError::class.java) {
            schema.validate("example.test", value, mode = ValidationMode.STRICT)
        }
        assertEquals(AnnieErrorCode.INVALID_ARGUMENT, error.code)
    }

    @Test fun requiredFieldsRemainRequiredForLegacyPackages() {
        val error = assertThrows(AnnieError::class.java) {
            schema.validate("example.test", JSONObject("""{"known":{}}"""), mode = ValidationMode.LEGACY)
        }
        assertEquals(AnnieErrorCode.INVALID_ARGUMENT, error.code)
    }
}
