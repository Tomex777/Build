package com.tomex777.annie

import org.json.JSONArray
import org.json.JSONObject

/** Encodes values crossing native background persistence without relying on non-public org.json helpers. */
internal fun encodeScriptJsonValue(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject, is JSONArray -> value.toString()
    is String -> JSONObject.quote(value)
    is Boolean -> value.toString()
    is Number -> {
        val numeric = value.toDouble()
        require(numeric.isFinite()) { "JSON numbers must be finite" }
        value.toString()
    }
    else -> JSONObject.quote(value.toString())
}
