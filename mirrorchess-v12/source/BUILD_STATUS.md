# Build status — MirrorChess 1.1

## Source status

The updated source tree is packaged as a complete Android Studio project with Gradle wrapper files.

Validation completed in this environment:

- starting-position perft depth 1: 20
- perft depth 2: 400
- perft depth 3: 8,902
- perft depth 4: 197,281
- Fool's Mate checkmate detection
- FEN serialize/restore
- multi-game PGN import
- Mirror profile build/serialize/restore
- Mirror continuous observe/merge/serialize smoke
- conservative insufficient-material behavior
- Maia 4,352-move vocabulary indexing sanity check

Core smoke output:

`PURE_SMOKE_OK perft4=197281 threefold=3 games=2 mirrorMoves=5 maiaIndex=796`

Additional Mirror-learning smoke output:

`MIRROR_LEARNING_OK moves=2 progress=0`

## Android build limitation of this packaging environment

A final Android compile could not begin because the Gradle wrapper needs to download Gradle 9.3.1 and this execution environment cannot resolve `services.gradle.org`.

The attempted command was:

`./gradlew :app:compileDebugKotlin --stacktrace`

It stopped at:

`java.net.UnknownHostException: services.gradle.org`

So the updated ZIP is source-complete and core-tested, but it is **not claimed as an APK-certified build from this environment**. Android Studio on a normal networked development machine remains the final Android compile gate.

## Model

The Maia ONNX binary is not bundled in the project ZIP. The app can download/import the checksum-pinned Maia-3 5M model at runtime. Manual ONNX import is now tucked under Settings → AI Model.
