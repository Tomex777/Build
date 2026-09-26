from pathlib import Path

path = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/VideoPlayerScreen.kt")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    text = text.replace(old, new, 1)

replace_once(
    "package com.night.sora.ui.screens\n\nimport androidx.activity.compose.BackHandler\n",
    "package com.night.sora.ui.screens\n\nimport android.app.Activity\nimport android.content.Context\nimport android.content.ContextWrapper\nimport androidx.activity.compose.BackHandler\n",
    "android activity imports",
)

replace_once(
    "import androidx.compose.ui.viewinterop.AndroidView\n",
    "import androidx.compose.ui.viewinterop.AndroidView\nimport androidx.core.view.WindowCompat\nimport androidx.core.view.WindowInsetsCompat\nimport androidx.core.view.WindowInsetsControllerCompat\n",
    "window inset imports",
)

replace_once(
    "    val context = LocalContext.current\n    val view = LocalView.current\n",
    "    val context = LocalContext.current\n    val view = LocalView.current\n    val activity = remember(context) { context.findActivity() }\n",
    "activity lookup",
)

old_effect = '''    DisposableEffect(view, player) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
            player.release()
        }
    }
'''
new_effect = '''    DisposableEffect(view, player, activity) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        val insetsController = activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).also { controller ->
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = previousKeepScreenOn
            player.release()
        }
    }
'''
replace_once(old_effect, new_effect, "fullscreen lifecycle")

text += '''\nprivate tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}\n'''

path.write_text(text)
print("Player fullscreen lifecycle applied to", path)
