package com.tomex777.annie

/**
 * Annie's unified installable unit. A package can be backed by one loose JavaScript file or by
 * a project directory; the runtime can migrate both storage shapes behind this metadata model.
 */
internal data class AnniePackageManifest(
    val packageId: String,
    val displayName: String,
    val version: String,
    val apiVersion: String,
    val entryPoint: String,
    val permissions: Set<String> = emptySet(),
    val commands: List<AnniePackageCommand> = emptyList(),
    val services: List<AnniePackageService> = emptyList(),
    val serviceDependencies: List<AnniePackageServiceDependency> = emptyList(),
    val assets: List<AnniePackageAsset> = emptyList(),
    val background: AnniePackageBackground = AnniePackageBackground.NONE,
    val dependencies: Map<String, String> = emptyMap(),
    val capabilities: Set<String> = emptySet(),
    val generated: Boolean = false,
) {
    companion object {
        const val CURRENT_API_VERSION = "1"
        private const val SYNTHETIC_VERSION = "0.0.0-local"

        /** Metadata synthesized for existing scripts that predate manifest.json. */
        fun forExistingProject(projectId: String, displayName: String, entryPoint: String) =
            AnniePackageManifest(
                packageId = projectId,
                displayName = displayName,
                version = SYNTHETIC_VERSION,
                apiVersion = CURRENT_API_VERSION,
                entryPoint = entryPoint,
                generated = true,
            )
    }
}

internal data class AnniePackageCommand(
    val name: String,
    val description: String = "",
)

/** Schema identifiers are package-scoped; a short service name alone is never globally unique. */
internal data class AnniePackageService(
    val name: String,
    val version: String,
    val inputSchema: String,
    val outputSchema: String,
)

internal data class AnniePackageServiceDependency(
    val packageId: String,
    val name: String,
    val version: String,
    val inputSchema: String,
    val outputSchema: String,
)

internal const val SERVICE_INVOKE_CAPABILITY = "services.invoke"
internal const val MAX_SERVICE_MESSAGE_BYTES = 64 * 1024
internal fun servicePermission(providerPackageId: String, serviceName: String) =
    "service:$providerPackageId/$serviceName"

internal data class AnniePackageAsset(
    val logicalId: String,
    val relativePath: String,
    val mimeType: String? = null,
)

internal enum class AnniePackageBackground {
    NONE,
    TASKS,
    MEDIA,
}
