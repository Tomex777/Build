package com.night.cortex.hosting

enum class HostingProviderId { BOT_HOSTING, AZURE }

data class HostingCapabilities(
    val files: Boolean,
    val logs: Boolean,
    val metrics: Boolean,
    val restart: Boolean,
    val environment: Boolean,
    val serverManagement: Boolean,
    val toolkit: Boolean,
)

interface HostingProvider {
    val id: HostingProviderId
    val displayName: String
    val capabilities: HostingCapabilities
}

object BotHostingProvider : HostingProvider {
    override val id = HostingProviderId.BOT_HOSTING
    override val displayName = "Bot-Hosting.net"
    override val capabilities = HostingCapabilities(
        files = true,
        logs = true,
        metrics = true,
        restart = true,
        environment = true,
        serverManagement = false,
        toolkit = false,
    )
}

object AzureProvider : HostingProvider {
    override val id = HostingProviderId.AZURE
    override val displayName = "Azure"
    override val capabilities = HostingCapabilities(
        files = true,
        logs = true,
        metrics = true,
        restart = true,
        environment = true,
        serverManagement = true,
        toolkit = true,
    )
}

fun providerFor(id: HostingProviderId): HostingProvider = when (id) {
    HostingProviderId.BOT_HOSTING -> BotHostingProvider
    HostingProviderId.AZURE -> AzureProvider
}
