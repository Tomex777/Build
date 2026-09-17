from pathlib import Path

path = Path('sora-ci/ui-smoke.sh')
text = path.read_text()

if "always_finish_activities" not in text:
    marker = "OUT=/tmp/sora-ui\nmkdir -p \"$OUT\"\n"
    replacement = marker + "\ncleanup_smoke_settings() {\n  adb shell settings put global always_finish_activities 0 >/dev/null 2>&1 || true\n}\ntrap cleanup_smoke_settings EXIT\n"
    if marker not in text:
        raise SystemExit('smoke setup marker not found')
    text = text.replace(marker, replacement, 1)

    anchor = """tap_text Play
wait_for_node Pause 15

tap_text 'Mini player'
"""
    insert = """tap_text Play
wait_for_node Pause 15
shot 06ab-music-background-resumed

# Force Android to destroy the activity when it leaves the foreground. The
# process-wide Media3 service/player must survive and the recreated activity
# must reconnect to the same now-playing state.
adb shell settings put global always_finish_activities 1
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell dumpsys activity services com.night.sora | grep -q 'MusicPlaybackService'
adb shell dumpsys media_session | grep -q 'com.night.sora'
adb shell am start -W -n com.night.sora/.MainActivity >/dev/null
sleep 4
dismiss_system_dialogs
wait_for_node Pause 15
shot 06ac-music-after-activity-recreation
adb shell settings put global always_finish_activities 0

tap_text 'Mini player'
"""
    if anchor not in text:
        raise SystemExit('current background-resume anchor not found')
    text = text.replace(anchor, insert, 1)

path.write_text(text)
print('Added activity-destruction/recreation proof to Sora music UI smoke.')
