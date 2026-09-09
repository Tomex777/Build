$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Out = Join-Path $env:TEMP "mirror-chess-core-smoke.jar"
& kotlinc `
  "$Root\app\src\main\java\com\night\mirrorchess\chess\ChessCore.kt" `
  "$Root\app\src\main\java\com\night\mirrorchess\chess\Pgn.kt" `
  "$Root\app\src\main\java\com\night\mirrorchess\ai\MovePredictor.kt" `
  "$Root\app\src\main\java\com\night\mirrorchess\ai\Maia3Encoding.kt" `
  "$Root\app\src\main\java\com\night\mirrorchess\mirror\MirrorProfile.kt" `
  "$Root\tools\CoreSmoke.kt" `
  -include-runtime -d $Out
& java -jar $Out
