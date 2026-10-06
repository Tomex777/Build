package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

class AnniePackageTest {
    @Test fun existingLooseJavaScriptProjectReceivesMinimalGeneratedPackageMetadata() {
        val project = ScriptProject(
            id = "hello",
            name = "Hello",
            entryPath = "hello.js",
            files = mapOf("hello.js" to "annie.commands.register({ name: 'hello' });"),
        )

        assertEquals("hello", project.manifest.packageId)
        assertEquals("Hello", project.manifest.displayName)
        assertEquals("hello.js", project.manifest.entryPoint)
        assertEquals(AnniePackageManifest.CURRENT_API_VERSION, project.manifest.apiVersion)
        assertTrue(project.manifest.generated)
        assertTrue(project.manifest.permissions.isEmpty())
    }

    @Test fun packageModelCarriesDeclarationsWithoutChangingScriptProjectShape() {
        val manifest = AnniePackageManifest(
            packageId = "com.example.chess",
            displayName = "Chess",
            version = "1.2.0",
            apiVersion = "1",
            entryPoint = "main.js",
            permissions = setOf("assets.read"),
            services = listOf(AnniePackageService("resolve", "1.0", "{url:string}", "{title:string}")),
            assets = listOf(AnniePackageAsset("board", "assets/board.webp", "image/webp")),
            background = AnniePackageBackground.TASKS,
            dependencies = mapOf("com.example.rules" to "^1"),
            capabilities = setOf("interactive", "fullscreen"),
        )

        assertEquals("com.example.chess", manifest.packageId)
        assertEquals("resolve", manifest.services.single().name)
        assertEquals("assets/board.webp", manifest.assets.single().relativePath)
        assertEquals(AnniePackageBackground.TASKS, manifest.background)
        assertEquals(setOf("interactive", "fullscreen"), manifest.capabilities)
    }
    @Test fun packageManifestCarriesM0bCompatibilityTrustAndNetworkMetadata() {
        val manifest = AnniePackageManifest(
            packageId = "com.example.demo",
            displayName = "Demo",
            version = "1.0.0",
            apiVersion = "1",
            entryPoint = "main.js",
            requires = mapOf("annie" to ">=1 <3"),
            publisher = AnniePackagePublisher("com.example.publisher", "Example"),
            networkHosts = setOf("api.example.com", "*.cdn.example.com"),
        )

        assertEquals(">=1 <3", manifest.requires["annie"])
        assertEquals("com.example.publisher", manifest.publisher?.id)
        assertEquals(setOf("api.example.com", "*.cdn.example.com"), manifest.networkHosts)
    }

    @Test fun compatibleAnnieRequirementIsAccepted() {
        val directory = File(System.getProperty("java.io.tmpdir"), "annie-requires-" + System.currentTimeMillis()).apply { mkdirs() }
        try {
            File(directory, "manifest.json").writeText("""
                {
                  "packageId": "com.example.ok",
                  "displayName": "OK",
                  "version": "1.0.0",
                  "apiVersion": "1",
                  "requires": { "annie": ">=1 <3" },
                  "entryPoint": "main.js"
                }
            """.trimIndent())
            val manifest = AnniePackageArchive.readManifestIfPresent(
                directory, "fallback", "Fallback", setOf("main.js"),
            )
            assertEquals(">=1 <3", manifest?.requires?.get("annie"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun incompatibleAnnieRequirementIsRejected() {
        val directory = File(System.getProperty("java.io.tmpdir"), "annie-requires-" + System.currentTimeMillis() + 1).apply { mkdirs() }
        try {
            File(directory, "manifest.json").writeText("""
                {
                  "packageId": "com.example.nope",
                  "displayName": "Nope",
                  "version": "1.0.0",
                  "apiVersion": "1",
                  "requires": { "annie": ">=2 <3" },
                  "entryPoint": "main.js"
                }
            """.trimIndent())
            val error = runCatching {
                AnniePackageArchive.readManifestIfPresent(
                    directory, "fallback", "Fallback", setOf("main.js"),
                )
            }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("incompatible Annie version"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
