package com.tomex777.annie

/**
 * Generates annie.generated.d.ts from the operation registry (the only source of truth).
 * Output must be byte-stable: registry order, two-space indent, "\n" line ends, trailing newline.
 */
internal object OperationTypings {
    private class Node {
        val children = linkedMapOf<String, Node>()
        var operation: OperationDefinition? = null
    }

    fun jsPath(definition: OperationDefinition): String = definition.js.path ?: definition.id

    fun dts(definitions: List<OperationDefinition>): String {
        val root = Node()
        definitions.forEach { definition ->
            var node = root
            jsPath(definition).split('.').forEach { part -> node = node.children.getOrPut(part) { Node() } }
            node.operation = definition
        }
        val out = StringBuilder()
        out.append("// GENERATED from the Annie operation registry. Do not edit by hand.\n")
        out.append("// Regenerate: ANNIE_UPDATE_GENERATED=1 ./gradlew testDebugUnitTest --tests '*OperationGeneratedArtifactsTest*'\n")
        out.append("\n")
        out.append("type AnnieErrorCode =\n")
        val codes = AnnieErrorCode.values().map { "\"${it.name}\"" }
        out.append(codes.joinToString("\n") { "  | $it" }).append(";\n")
        out.append("\n")
        out.append("interface AnnieError extends Error {\n")
        out.append("  code: AnnieErrorCode;\n")
        out.append("  operation: string;\n")
        out.append("  retryable: boolean;\n")
        out.append("  retryAfterMs?: number;\n")
        out.append("  permission?: string;\n")
        out.append("}\n")
        out.append("\n")
        out.append("interface AnnieMessage {\n")
        out.append("  type: string;\n")
        out.append("  [key: string]: unknown;\n")
        out.append("}\n")
        out.append("\n")
        out.append("interface AnnieMessageHandle {\n")
        out.append("  id: string;\n")
        out.append("  packageId: string;\n")
        out.append("  conversationId: string;\n")
        out.append("  createdAt: number;\n")
        out.append("}\n")
        out.append("\n")
        out.append("interface Annie {\n")
        emit(root, "  ", out)
        out.append("}\n")
        return out.toString()
    }

    private fun emit(node: Node, indent: String, out: StringBuilder) {
        node.children.forEach { (name, child) ->
            val operation = child.operation
            if (operation != null) {
                out.append(indent).append("/**\n")
                out.append(indent).append(" * Registry operation ${operation.id}.\n")
                if (operation.docs.isNotBlank()) out.append(indent).append(" * ${operation.docs}\n")
                out.append(indent).append(" * @throws AnnieError ")
                    .append(operation.errors.map { it.name }.sorted().joinToString(", ")).append("\n")
                out.append(indent).append(" */\n")
                out.append(indent).append(name).append("(").append(parameters(operation)).append("): Promise<")
                    .append(operation.js.returns).append(">;\n")
            } else {
                out.append(indent).append(name).append(": {\n")
                emit(child, "$indent  ", out)
                out.append(indent).append("};\n")
            }
        }
    }

    private fun parameters(operation: OperationDefinition): String {
        val schema = operation.input
        val binding = operation.js
        val params = mutableListOf<String>()
        binding.positional.forEach { key ->
            val property = schema.properties.getValue(key)
            params += key + (if (property.required) "" else "?") + ": " + tsType(property)
        }
        if (binding.optionsKeys.isNotEmpty()) {
            val fields = binding.optionsKeys.joinToString("; ") { key ->
                key + "?: " + tsType(schema.properties.getValue(key))
            }
            params += "options?: { $fields }"
        }
        binding.spreadArg?.let { name ->
            val anyRequired = schema.properties.values.any { it.required }
            params += name + (if (anyRequired) "" else "?") + ": " + objectType(schema)
        }
        return params.joinToString(", ")
    }

    private fun objectType(schema: OperationInputSchema): String {
        if (schema.properties.isEmpty()) return "{}"
        return "{ " + schema.properties.entries.joinToString("; ") { (name, property) ->
            name + (if (property.required) "" else "?") + ": " + tsType(property)
        } + " }"
    }

    private fun tsType(property: OperationProperty): String {
        if (property.enumValues.isNotEmpty()) return property.enumValues.joinToString(" | ") { "\"$it\"" }
        return when (property.type) {
            "string" -> "string"
            "number" -> "number"
            "boolean" -> "boolean"
            "any" -> property.tsType ?: "unknown"
            "array" -> property.tsType ?: "unknown[]"
            "object" -> property.nested?.let { objectType(it) } ?: (property.tsType ?: "Record<string, unknown>")
            else -> error("Unsupported registry schema type '${property.type}'")
        }
    }
}
