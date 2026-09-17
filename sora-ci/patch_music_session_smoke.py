from pathlib import Path

path = Path('sora-ci/ui-smoke.sh')
text = path.read_text()
old = '''shot 06a-music-playing\n\ntap_text 'Mini player'\nwait_for_node Pause 10\n'''
new = '''shot 06a-music-playing\n\n# Background playback must survive leaving the activity and expose a Media3 session.\nadb shell input keyevent KEYCODE_HOME\nsleep 4\nadb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'\nadb shell dumpsys media_session | grep -q 'com.night.sora'\nadb shell input keyevent KEYCODE_MEDIA_PAUSE\nsleep 2\nadb shell am start -W -n com.night.sora/.MainActivity >/dev/null\nsleep 3\ndismiss_system_dialogs\nwait_for_node Play 10\nshot 06aa-music-background-paused\ntap_text Play\nwait_for_node Pause 15\n\ntap_text 'Mini player'\nwait_for_node Pause 10\n'''
if new not in text:
    if old not in text:
        raise SystemExit('music background smoke insertion marker not found')
    text = text.replace(old, new, 1)
path.write_text(text)
print('Applied Sora Media3 background playback smoke assertions.')
