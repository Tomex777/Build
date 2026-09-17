package com.night.cortex.hosting

enum class HostingPowerAction { START, STOP, RESTART }

data class HostingRuntime(
    val runtime: String? = null,
    val version: String? = null,
    val entryFile: String? = null,
    val startCommand: String? = null,
)

data class HostingSnapshot(
    val state: String,
    val cpuPercent: Double? = null,
    val memoryUsedBytes: Long? = null,
    val memoryLimitBytes: Long? = null,
    val diskUsedBytes: Long? = null,
    val diskLimitBytes: Long? = null,
    val uptimeMs: Long? = null,
    val runtime: HostingRuntime? = null,
)

data class HostingFileEntry(
    val name: String,
    val type: String,
    val sizeBytes: Long = 0,
    val modifiedAt: String? = null,
)

interface HostingControlClient {
    fun snapshot(): HostingSnapshot
    fun logs(limit: Int = 200): List<String>
    fun power(action: HostingPowerAction): HostingSnapshot?
    fun listFiles(path: String = "/"): List<HostingFileEntry>
    fun readText(path: String): String
    fun writeText(path: String, content: String)
}
