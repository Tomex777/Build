package dev.tomex.youtube.core

import dev.tomex.youtube.api.AnonymousSession
import dev.tomex.youtube.api.SessionProvider
import dev.tomex.youtube.api.YouTubeEngine

/**
 * Stable host entrypoint. Host apps depend on [YouTubeEngine] and do not need parser,
 * client-strategy, player-script, or transport implementation details.
 */
object YouTubeEngineFactory {
    @JvmStatic
    @JvmOverloads
    fun create(session: SessionProvider = AnonymousSession): YouTubeEngine =
        NativeYouTubeEngine(session = session)
}
