package app.nami.compat.aniyomi

import eu.kanade.tachiyomi.animesource.model.Video
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoBinaryCompatibilityTest {

    @Test
    fun videoExposesBothV16AndV17CopyDefaultAbis() {
        val copyDefaults = Video::class.java.declaredMethods
            .filter { it.name == "copy\$default" }
            .map { it.parameterTypes.toList() }

        assertTrue(
            copyDefaults.any { parameters ->
                parameters.size == 17 &&
                    parameters.first() == Video::class.java &&
                    parameters[1] == String::class.java &&
                    parameters[2] == String::class.java &&
                    parameters[15] == Int::class.javaPrimitiveType &&
                    parameters[16] == Any::class.java
            },
            "Missing extensions-lib 16 Video.copy\$default ABI",
        )

        assertTrue(
            copyDefaults.any { parameters ->
                parameters.size == 18 &&
                    parameters.first() == Video::class.java &&
                    parameters[1] == String::class.java &&
                    parameters[2] == String::class.java &&
                    parameters[16] == Int::class.javaPrimitiveType &&
                    parameters[17] == Any::class.java
            },
            "Missing extensions-lib 17 Video.copy\$default ABI",
        )
    }

    @Test
    fun videoExposesBothV16AndV17CopyMethods() {
        val copyArities = Video::class.java.declaredMethods
            .filter { it.name == "copy" }
            .map { it.parameterCount }
            .toSet()

        assertTrue(14 in copyArities, "Missing extensions-lib 16 Video.copy ABI")
        assertTrue(15 in copyArities, "Missing extensions-lib 17 Video.copy ABI")
    }

    @Test
    fun videoExposesBothV16AndV17Constructors() {
        val constructorArities = Video::class.java.declaredConstructors
            .map { it.parameterCount }
            .toSet()

        assertTrue(14 in constructorArities, "Missing extensions-lib 16 Video constructor")
        assertTrue(15 in constructorArities, "Missing extensions-lib 17 Video constructor")
    }

    @Test
    fun v16CopyDefaultUsesReceiverValuesForMaskedArguments() {
        val original = Video(
            videoUrl = "https://example.invalid/original.mp4",
            videoTitle = "1080p",
            preferred = true,
            internalData = "legacy",
            initialized = true,
        )

        val method = Video::class.java.declaredMethods.single {
            it.name == "copy\$default" && it.parameterCount == 17
        }

        val copied = method.invoke(
            null,
            original,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            0x3fff,
            null,
        ) as Video

        assertEquals(original.videoUrl, copied.videoUrl)
        assertEquals(original.videoTitle, copied.videoTitle)
        assertEquals(original.preferred, copied.preferred)
        assertEquals(original.internalData, copied.internalData)
        assertEquals(original.initialized, copied.initialized)
    }
}
