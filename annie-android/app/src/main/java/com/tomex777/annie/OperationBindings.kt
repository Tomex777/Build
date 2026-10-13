package com.tomex777.annie

import org.json.JSONArray
import org.json.JSONObject

/** API 2 wrappers and declarations derive their paths and arguments from the same registry. */
internal fun operationBindingMetadata(definitions: List<OperationDefinition>): String = JSONArray().apply {
    definitions.forEach { definition ->
        put(JSONObject().put("id", definition.id).put("path", OperationTypings.jsPath(definition))
            .put("positional", JSONArray(definition.js.positional)).put("options", JSONArray(definition.js.optionsKeys))
            .put("spread", definition.js.spreadArg != null))
    }
}.toString()
