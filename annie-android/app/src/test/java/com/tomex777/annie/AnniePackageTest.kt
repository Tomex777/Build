package com.tomex777.annie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
