from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one match, found {count}')
    path.write_text(text.replace(old, new, 1))

controller = Path('sora-overlay/app/src/main/java/com/night/sora/playback/MusicPlaybackController.kt')
replace_once(
    controller,
    '''    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)\n    private val player = ExoPlayer.Builder(context.applicationContext).build()''',
    '''    private val appContext = context.applicationContext\n    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)\n    private val player = ExoPlayer.Builder(appContext).build()''',
    'controller application context',
)
replace_once(
    controller,
    '''    fun updateExtensions(value: List<InstalledExtension>) {\n        extensions = value\n    }\n''',
    '''    fun updateExtensions(value: List<InstalledExtension>) {\n        extensions = value\n    }\n\n    fun sessionPlayer(): Player = player\n''',
    'controller session player',
)
replace_once(
    controller,
    '''    ) {\n        updateExtensions(availableExtensions)\n        baseQueue = normalizeQueue(sourceQueue, track)''',
    '''    ) {\n        MusicPlaybackService.ensureStarted(appContext)\n        updateExtensions(availableExtensions)\n        baseQueue = normalizeQueue(sourceQueue, track)''',
    'controller service start',
)

app = Path('sora-overlay/app/src/main/java/com/night/sora/ui/SoraApp.kt')
replace_once(
    app,
    'import com.night.sora.playback.MusicPlaybackController\n',
    'import com.night.sora.playback.MusicPlaybackController\nimport com.night.sora.playback.MusicPlaybackRuntime\n',
    'runtime import',
)
replace_once(
    app,
    '    val musicPlayer = remember { MusicPlaybackController(context.applicationContext, extensionManager) }',
    '    val musicPlayer = remember { MusicPlaybackRuntime.get(context.applicationContext, extensionManager) }',
    'shared player runtime',
)
replace_once(
    app,
    '    DisposableEffect(musicPlayer) { onDispose { musicPlayer.release() } }\n',
    '',
    'activity no longer releases service player',
)

gradle = Path('sora-overlay/app/build.gradle.kts')
replace_once(
    gradle,
    '    implementation("androidx.media3:media3-ui:$media3Version")\n',
    '    implementation("androidx.media3:media3-ui:$media3Version")\n    implementation("androidx.media3:media3-session:$media3Version")\n',
    'media3 session dependency',
)

manifest = Path('sora-overlay/app/src/main/AndroidManifest.xml')
replace_once(
    manifest,
    '''    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n''',
    '''    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />\n''',
    'playback foreground permissions',
)
replace_once(
    manifest,
    '''        <activity\n            android:name=".MainActivity"''',
    '''        <service\n            android:name=".playback.MusicPlaybackService"\n            android:exported="true"\n            android:foregroundServiceType="mediaPlayback">\n            <intent-filter>\n                <action android:name="androidx.media3.session.MediaSessionService" />\n            </intent-filter>\n        </service>\n\n        <activity\n            android:name=".MainActivity"''',
    'playback service manifest',
)

print('Applied Sora Media3 session/background playback integration.')
