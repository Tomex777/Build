#!/usr/bin/env python3
"""Production hardening of the real, expanded EchoSpace Android sources.

Run AFTER six historical source transforms, before building.  Refuse drift.
This affects EchoSpace only.  Sound Atlas has its own Android audio lifecycle.
"""
from pathlib import Path
R=Path("EchoSpace/app/src/main/java/com/nexa/echospace")
activity=R/"MainActivity.kt"
service=R/"audio/SpatialAudioService.kt"

def replace_once(path,old,new,what):
    s=path.read_text()
    assert s.count(old)==1, f"{what}: expected one anchor in {path}, got {s.count(old)}"
    path.write_text(s.replace(old,new,1))

replace_once(activity,
    "import android.os.IBinder\n",
    "import android.os.IBinder\nimport androidx.core.view.WindowCompat\n"
    "import androidx.core.view.WindowInsetsCompat\n"
    "import androidx.core.view.WindowInsetsControllerCompat\n",
    "immutable immersive imports")

replace_once(activity,
    """        window.statusBarColor = android.graphics.Color.rgb(5, 9, 17)
        window.navigationBarColor = android.graphics.Color.rgb(5, 9, 17)
        setContent {""",
    """        window.statusBarColor = android.graphics.Color.rgb(5, 9, 17)
        window.navigationBarColor = android.graphics.Color.rgb(5, 9, 17)
        applyImmersiveBars()
        setContent {""",
    "fullscreen window initialization")

replace_once(activity,
    """    override fun onStart() {
        super.onStart()
        bound = bindService""",
    """    private fun applyImmersiveBars() {
        // Hide status/navigation bars: swipe temporarily reveals native system UI.
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveBars()
    }
    override fun onStart() {
        super.onStart()
        bound = bindService""",
    "immersive reapplication after dialogs")

replace_once(service,
    "import android.app.Service\n",
    "import android.app.Service\nimport android.content.BroadcastReceiver\n"
    "import android.content.Context\nimport android.content.IntentFilter\n"
    "import android.os.Build\n",
    "headphones unplug imports")
replace_once(service,
    """    private var currentSceneTitle = "Your environment"

    private val focusListener""",
    """    private var currentSceneTitle = "Your environment"
    private var noisyRegistered = false
    // Never continue spatial headphone playback through a suddenly exposed speaker.
    private val becomingNoisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    private val focusListener""",
    "audio route receiver")
replace_once(service,
    """        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()""",
    """        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) pause()""",
    "respect phone calls and transient focus duck")
replace_once(service,
    """        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val store = SceneStore(this)""",
    """        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val routeFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(becomingNoisy, routeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(becomingNoisy, routeFilter)
        }
        noisyRegistered = true
        val store = SceneStore(this)""",
    "register headphone route receiver safely")
replace_once(service,
    """    override fun onDestroy() {
        scheduledStop?.let(handler::removeCallbacks)
        engine.close()""",
    """    override fun onDestroy() {
        if (noisyRegistered) {
            unregisterReceiver(becomingNoisy)
            noisyRegistered = false
        }
        scheduledStop?.let(handler::removeCallbacks)
        engine.close()""",
    "release route receiver reliably")

print("ECHOSPACE_PRODUCTION_HARDENING_OK immersive, audio focus, safe unplug")
