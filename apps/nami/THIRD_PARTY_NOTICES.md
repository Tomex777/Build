# Third-party notices

## Aniyomi

Aniyomi v0.18.2.1 is maintained by the Aniyomi Open Source Project and is licensed under Apache License 2.0. Reference baseline: `aniyomiorg/aniyomi` commit `97414446b8a95994c72dd33c41c971a89d4d25b8`.

Nami's isolated anime compatibility module adapts the Aniyomi anime source contracts and host-side network integration. The current implementation targets a limited extensions-lib v16 surface and does not claim v17 binary compatibility. The compatibility module also adapts Aniyomi's `JsoupExtensions.kt` helper methods into the expected `eu.kanade.tachiyomi.util.JsoupExtensionsKt` JVM class for legacy extensions. It also preserves the extensions-lib coroutine `OkHttpClient.get/post` JVM signatures and defaults from Aniyomi's `Requests.kt` so installed extension APKs link against `RequestsKt`. The Nami anime details presentation also uses Aniyomi's anime details experience as its behavior and layout reference. Relevant Apache-2.0 license text is retained in `licenses/ANIYOMI-APACHE-2.0.txt`; adapted API files carry local attribution headers.

Nami does not copy the Aniyomi application architecture, manga product features, player, or downloader in this phase.
