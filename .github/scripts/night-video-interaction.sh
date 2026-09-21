#!/usr/bin/env bash
# Night video Android 16 validation trigger v2
set -euo pipefail

APK="whatsapp-ai-android/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.example.whatsapp"
FIXTURE="/tmp/night-real-video.mp4"
APP_VIDEO="/data/user/0/$PACKAGE/cache/night-real-video.mp4"
ARTIFACTS="night-video-interaction-artifacts"

mkdir -p "$ARTIFACTS"

capture_exit_diagnostics() {
  local code="$?"
  adb logcat -d -v threadtime > "$ARTIFACTS/logcat-exit.txt" 2>/dev/null || true
  adb shell ps -A > "$ARTIFACTS/processes-exit.txt" 2>/dev/null || true
  adb shell dumpsys activity activities > "$ARTIFACTS/activities-exit.txt" 2>/dev/null || true
  if [ "$code" -ne 0 ]; then
    adb exec-out screencap -p > "$ARTIFACTS/failure-exit.png" 2>/dev/null || true
    {
      echo "exitCode=$code"
      echo "nightPid=$(adb shell pidof "$PACKAGE" 2>/dev/null || true)"
      echo "timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    } > "$ARTIFACTS/exit-status.txt"
  fi
}
trap capture_exit_diagnostics EXIT

adb install --no-streaming -r "$APK"
adb shell wm size 709x1536
adb shell wm density 240
adb logcat -c

adb push "$FIXTURE" /data/local/tmp/night-real-video.mp4 >/dev/null
adb shell run-as "$PACKAGE" cp /data/local/tmp/night-real-video.mp4 cache/night-real-video.mp4
adb shell run-as "$PACKAGE" test -s cache/night-real-video.mp4

cat >/tmp/night_video_uia.py <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

mode = sys.argv[1]
value = sys.argv[2] if len(sys.argv) > 2 else None
root = ET.parse("/tmp/window.xml").getroot()

def bounds(node):
    nums = [int(x) for x in re.findall(r"\d+", node.attrib.get("bounds", ""))]
    return nums if len(nums) == 4 else None

if mode in ("text", "desc", "text_contains"):
    attr = "content-desc" if mode == "desc" else "text"
    for node in root.iter("node"):
        actual = node.attrib.get(attr, "")
        matched = value in actual if mode == "text_contains" else actual == value
        if matched:
            b = bounds(node)
            if b:
                print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
                raise SystemExit(0)
    raise SystemExit(2)

if mode == "desc_enabled":
    for node in root.iter("node"):
        if node.attrib.get("content-desc") == value:
            print(node.attrib.get("enabled", "false"))
            raise SystemExit(0)
    raise SystemExit(2)

if mode == "times":
    values = []
    for node in root.iter("node"):
        text = node.attrib.get("text", "")
        if re.fullmatch(r"\d+:\d{2}", text):
            if text not in values:
                values.append(text)
    for item in values:
        print(item)
    raise SystemExit(0 if values else 2)

if mode == "trim":
    for node in root.iter("node"):
        text = node.attrib.get("text", "")
        if re.fullmatch(r"\d+:\d{2} [–-] \d+:\d{2}", text):
            print(text)
            raise SystemExit(0)
    raise SystemExit(2)

if mode == "bottom_seekbar":
    candidates = []
    for node in root.iter("node"):
        cls = node.attrib.get("class", "")
        b = bounds(node)
        if b and (cls == "android.widget.SeekBar" or node.attrib.get("range-info")):
            candidates.append((b[1] + b[3], b))
    if not candidates:
        raise SystemExit(2)
    _, b = max(candidates, key=lambda item: item[0])
    print(*b)
    raise SystemExit(0)

raise SystemExit(2)
PY

refresh_ui() {
  local attempt
  rm -f /tmp/window.xml /tmp/uiautomator-last.txt
  for attempt in $(seq 1 10); do
    if adb shell uiautomator dump /sdcard/window.xml > /tmp/uiautomator-last.txt 2>&1; then
      if adb exec-out cat /sdcard/window.xml > /tmp/window.xml 2>/dev/null &&
         grep -q "<hierarchy" /tmp/window.xml; then
        return 0
      fi
    fi
    sleep 1
  done

  echo "Could not obtain Android UI hierarchy after 10 attempts." >&2
  cat /tmp/uiautomator-last.txt >&2 || true
  adb exec-out screencap -p > "$ARTIFACTS/failure-ui-hierarchy.png" || true
  adb logcat -d -v threadtime > "$ARTIFACTS/logcat-ui-hierarchy-failure.txt" || true
  return 1
}

text_is_visible() {
  refresh_ui
  python3 /tmp/night_video_uia.py text "$1" >/dev/null 2>&1
}

desc_is_visible() {
  refresh_ui
  python3 /tmp/night_video_uia.py desc "$1" >/dev/null 2>&1
}

assert_text() {
  local wanted="$1"
  local attempt
  for attempt in $(seq 1 10); do
    refresh_ui
    if python3 /tmp/night_video_uia.py text "$wanted" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  cp /tmp/window.xml "$ARTIFACTS/failure-window-text.xml" 2>/dev/null || true
  echo "Night UI text did not appear: $wanted" >&2
  return 1
}

assert_desc() {
  local wanted="$1"
  local attempt
  for attempt in $(seq 1 10); do
    refresh_ui
    if python3 /tmp/night_video_uia.py desc "$wanted" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  cp /tmp/window.xml "$ARTIFACTS/failure-window-desc.xml" 2>/dev/null || true
  echo "Night UI content description did not appear: $wanted" >&2
  return 1
}

tap_text() {
  local wanted="$1"
  local coords=""
  local attempt x y
  for attempt in $(seq 1 10); do
    refresh_ui
    coords="$(python3 /tmp/night_video_uia.py text "$wanted" 2>/dev/null || true)"
    if [ -n "$coords" ]; then
      x="${coords% *}"
      y="${coords#* }"
      adb shell input tap "$x" "$y"
      sleep 1
      return 0
    fi
    sleep 1
  done
  cp /tmp/window.xml "$ARTIFACTS/failure-window-tap-text.xml" 2>/dev/null || true
  echo "Could not tap Night UI text: $wanted" >&2
  return 1
}

tap_text_contains() {
  local wanted="$1"
  local coords=""
  local attempt x y
  for attempt in $(seq 1 10); do
    refresh_ui
    coords="$(python3 /tmp/night_video_uia.py text_contains "$wanted" 2>/dev/null || true)"
    if [ -n "$coords" ]; then
      x="${coords% *}"
      y="${coords#* }"
      adb shell input tap "$x" "$y"
      sleep 1
      return 0
    fi
    sleep 1
  done
  cp /tmp/window.xml "$ARTIFACTS/failure-window-tap-text-contains.xml" 2>/dev/null || true
  echo "Could not tap Night UI text containing: $wanted" >&2
  return 1
}

tap_desc() {
  local wanted="$1"
  local coords=""
  local attempt x y
  for attempt in $(seq 1 10); do
    refresh_ui
    coords="$(python3 /tmp/night_video_uia.py desc "$wanted" 2>/dev/null || true)"
    if [ -n "$coords" ]; then
      x="${coords% *}"
      y="${coords#* }"
      adb shell input tap "$x" "$y"
      sleep 1
      return 0
    fi
    sleep 1
  done
  cp /tmp/window.xml "$ARTIFACTS/failure-window-tap-desc.xml" 2>/dev/null || true
  echo "Could not tap Night UI control: $wanted" >&2
  return 1
}

assert_alive() {
  if ! adb shell pidof "$PACKAGE" >/dev/null; then
    adb logcat -d -v threadtime > "$ARTIFACTS/logcat-process-dead.txt" 2>/dev/null || true
    echo "Night process is not alive." >&2
    return 1
  fi
}

capture_media_logcat() {
  local label="$1"
  local full="$ARTIFACTS/logcat-$label.txt"
  local filtered="$ARTIFACTS/logcat-$label-media.txt"
  adb logcat -d -v threadtime > "$full" || true
  grep -Ei \
    'NightVideo|libvlc|VLC|MediaCodec|CCodec|Codec2|ACodec|Surface|BufferQueue|Transformer|Media3|AndroidRuntime|FATAL EXCEPTION|ANR in|Fatal signal|SIGSEGV|SIGABRT|(^|[[:space:]])E/' \
    "$full" > "$filtered" || true
}

assert_no_crash() {
  adb logcat -d -v threadtime > "$ARTIFACTS/logcat-latest.txt" || true
  if grep -A5 "FATAL EXCEPTION:" "$ARTIFACTS/logcat-latest.txt" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during real video interaction test." >&2
    exit 1
  fi
  if grep -q "ANR in $PACKAGE" "$ARTIFACTS/logcat-latest.txt"; then
    echo "Night hit an ANR during real video interaction test." >&2
    exit 1
  fi
  if grep -E ">>> $PACKAGE <<<|Fatal signal (6|11)" "$ARTIFACTS/logcat-latest.txt" | grep -Eq "Fatal signal|>>> $PACKAGE <<<"; then
    if grep -B6 -A10 ">>> $PACKAGE <<<" "$ARTIFACTS/logcat-latest.txt" | grep -Eq "Fatal signal|SIGSEGV|SIGABRT"; then
      echo "Night hit a native crash during real video interaction test." >&2
      exit 1
    fi
  fi
  assert_alive
}

dismiss_fullscreen_education() {
  # Fresh Android emulators may place a system-owned immersive-mode tutorial
  # above Night. It intercepts the first tap even though video is already
  # rendering underneath, so clear it before testing Night's controls.
  adb shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1 || true

  local coords="" x y
  refresh_ui || return 0
  coords="$(python3 /tmp/night_video_uia.py text "Got it" 2>/dev/null || true)"
  if [ -n "$coords" ]; then
    x="${coords% *}"
    y="${coords#* }"
    adb shell input tap "$x" "$y"
    sleep 0.5
    refresh_ui || true
  fi
}

show_controls() {
  # dismiss_fullscreen_education already leaves a fresh hierarchy in
  # /tmp/window.xml. Reuse it rather than paying for two more UIAutomator dumps.
  dismiss_fullscreen_education
  if python3 /tmp/night_video_uia.py desc "Pause" >/dev/null 2>&1 || \
     python3 /tmp/night_video_uia.py desc "Play" >/dev/null 2>&1; then
    return 0
  fi

  adb shell input tap 354 760
  sleep 0.5
  dismiss_fullscreen_education
  if ! python3 /tmp/night_video_uia.py desc "Pause" >/dev/null 2>&1 && \
     ! python3 /tmp/night_video_uia.py desc "Play" >/dev/null 2>&1; then
    echo "Night video controls did not become visible." >&2
    adb exec-out screencap -p > "$ARTIFACTS/failure-controls-hidden.png"
    cp /tmp/window.xml "$ARTIFACTS/failure-controls-hidden.xml" 2>/dev/null || true
    capture_media_logcat "controls-hidden"
    exit 1
  fi
}

to_seconds() {
  local raw="$1"
  local m="${raw%:*}"
  local s="${raw#*:}"
  echo $((10#$m * 60 + 10#$s))
}

read_current_time() {
  local attempt value
  for attempt in $(seq 1 12); do
    show_controls
    value="$(python3 /tmp/night_video_uia.py times 2>/dev/null | head -n 1 || true)"
    if [ -n "$value" ]; then
      echo "$value"
      return 0
    fi
    sleep 1
  done

  cp /tmp/window.xml "$ARTIFACTS/failure-current-time.xml" 2>/dev/null || true
  adb exec-out screencap -p > "$ARTIFACTS/failure-current-time.png" || true
  capture_media_logcat "current-time-missing"
  echo "Night playback time was not exposed in the UI hierarchy." >&2
  return 1
}

prepare_timed_playback() {
  local label="$1"
  local seek_bounds sx1 sy1 sx2 sy2 seek_y seek_x play_coords play_x play_y

  # UIAutomator dumps are relatively expensive on the API-36 emulator. Reuse
  # this single hierarchy for both the seek bar and the pre-seek play state so
  # the 30-second fixture does not run back toward EOF while CI is inspecting it.
  show_controls
  seek_bounds="$(python3 /tmp/night_video_uia.py bottom_seekbar 2>/dev/null || true)"
  play_coords="$(python3 /tmp/night_video_uia.py desc "Play" 2>/dev/null || true)"
  if [ -z "$seek_bounds" ]; then
    echo "Could not find the playback seek bar while preparing $label." >&2
    adb exec-out screencap -p > "$ARTIFACTS/failure-$label-no-seekbar.png" || true
    cp /tmp/window.xml "$ARTIFACTS/failure-$label-no-seekbar.xml" 2>/dev/null || true
    capture_media_logcat "$label-no-seekbar"
    exit 1
  fi

  read -r sx1 sy1 sx2 sy2 <<<"$seek_bounds"
  seek_y=$(((sy1 + sy2) / 2))
  # Rewind very close to the start. This is preparation only; the dedicated
  # seek assertion later still proves a real 72% seek independently.
  seek_x=$((sx1 + (sx2 - sx1) * 3 / 100))
  adb shell input tap "$seek_x" "$seek_y"

  # If playback had already reached EOF, restart it using the coordinates from
  # the same hierarchy instead of paying for another slow UI dump.
  if [ -n "$play_coords" ]; then
    play_x="${play_coords% *}"
    play_y="${play_coords#* }"
    adb shell input tap "$play_x" "$play_y"
  fi

  sleep 0.5
  adb exec-out screencap -p > "$ARTIFACTS/$label-prepared.png" || true
}

capture_dims() {
  local path="$1"
  adb exec-out screencap -p > "$path"
  python3 - "$path" <<'PY'
import struct
import sys
with open(sys.argv[1], "rb") as f:
    sig = f.read(24)
width, height = struct.unpack(">II", sig[16:24])
print(width, height)
PY
}

echo "STEP: open and play real H.264/AAC MP4"
adb shell am force-stop "$PACKAGE"
adb shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1 || true
adb shell am start -W -n "$PACKAGE/.MediaViewerPreviewActivity" \
  --es night.preview.videoPath "$APP_VIDEO"
sleep 4
dismiss_fullscreen_education
adb exec-out screencap -p > "$ARTIFACTS/00-cold-launch.png" || true
capture_media_logcat "00-cold-launch"
assert_alive
show_controls
assert_no_crash
capture_dims "$ARTIFACTS/01-real-video-open.png" > "$ARTIFACTS/01-dimensions.txt"
capture_media_logcat "01-open"

first_time="$(read_current_time)"
first_seconds="$(to_seconds "$first_time")"
second_time="$first_time"
second_seconds="$first_seconds"
for _ in $(seq 1 8); do
  sleep 2
  second_time="$(read_current_time)"
  second_seconds="$(to_seconds "$second_time")"
  if [ "$second_seconds" -gt "$first_seconds" ]; then
    break
  fi
done
capture_media_logcat "02-playback"
assert_no_crash
if [ "$second_seconds" -le "$first_seconds" ]; then
  echo "Real MP4 playback time did not advance after recovery window: $first_time -> $second_time" >&2
  adb exec-out screencap -p > "$ARTIFACTS/failure-playback-stalled.png" || true
  exit 1
fi
printf 'first=%s\nsecond=%s\n' "$first_time" "$second_time" > "$ARTIFACTS/02-playback-progress.txt"
adb exec-out screencap -p > "$ARTIFACTS/02-playback-advanced.png" || true

echo "STEP: hide and show controls through the VLC surface"
show_controls
# Tap clear video space, away from the central previous/play/next row and
# bottom toolbar, so the native VLC-view tap bridge must toggle the overlay.
adb shell input tap 80 760
sleep 1
refresh_ui
if python3 /tmp/night_video_uia.py desc "Pause" >/dev/null 2>&1 || \
   python3 /tmp/night_video_uia.py desc "Play" >/dev/null 2>&1; then
  echo "Night video controls did not hide after tapping the video surface." >&2
  adb exec-out screencap -p > "$ARTIFACTS/failure-controls-would-not-hide.png" || true
  cp /tmp/window.xml "$ARTIFACTS/failure-controls-would-not-hide.xml" 2>/dev/null || true
  capture_media_logcat "controls-would-not-hide"
  exit 1
fi
adb exec-out screencap -p > "$ARTIFACTS/03-controls-hidden.png" || true
adb shell input tap 80 760
sleep 1
if ! desc_is_visible "Pause" && ! desc_is_visible "Play"; then
  echo "Night video controls did not reappear after tapping the VLC surface." >&2
  adb exec-out screencap -p > "$ARTIFACTS/failure-controls-would-not-show.png" || true
  cp /tmp/window.xml "$ARTIFACTS/failure-controls-would-not-show.xml" 2>/dev/null || true
  capture_media_logcat "controls-would-not-show"
  exit 1
fi
adb exec-out screencap -p > "$ARTIFACTS/04-controls-visible.png" || true

echo "STEP: pause and resume"
prepare_timed_playback "pause-resume"
show_controls
tap_desc "Pause"
paused_time="$(read_current_time)"
sleep 2
paused_after="$(read_current_time)"
paused_seconds="$(to_seconds "$paused_time")"
paused_after_seconds="$(to_seconds "$paused_after")"
if [ $((paused_after_seconds - paused_seconds)) -gt 1 ]; then
  echo "Video kept advancing while paused: $paused_time -> $paused_after" >&2
  exit 1
fi
assert_desc "Play"
tap_desc "Play"
sleep 2
resumed_time="$(read_current_time)"
resumed_seconds="$(to_seconds "$resumed_time")"
if [ "$resumed_seconds" -le "$paused_after_seconds" ]; then
  echo "Video did not resume after Play." >&2
  exit 1
fi
capture_media_logcat "03-pause-resume"
assert_no_crash

echo "STEP: playback speed"
prepare_timed_playback "speed"
show_controls
tap_text "1×"
assert_text "2×"
tap_text "2×"
speed_start="$(read_current_time)"
sleep 2
speed_end="$(read_current_time)"
speed_start_seconds="$(to_seconds "$speed_start")"
speed_end_seconds="$(to_seconds "$speed_end")"
if [ $((speed_end_seconds - speed_start_seconds)) -lt 2 ]; then
  echo "2× playback selection did not advance playback as expected." >&2
  exit 1
fi

echo "STEP: audio and subtitle tracks"
show_controls
tap_desc "Audio"
audio_track_found=false
for _ in $(seq 1 10); do
  refresh_ui
  if python3 /tmp/night_video_uia.py text_contains "Night test audio" >/dev/null 2>&1 || \
     python3 /tmp/night_video_uia.py text_contains "AAC" >/dev/null 2>&1; then
    audio_track_found=true
    break
  fi
  sleep 1
done
cp /tmp/window.xml "$ARTIFACTS/05-audio-menu.xml"
if [ "$audio_track_found" != "true" ]; then
  echo "Audio track menu did not expose the real embedded AAC track metadata." >&2
  adb exec-out screencap -p > "$ARTIFACTS/failure-audio-track.png" || true
  capture_media_logcat "audio-track-missing"
  exit 1
fi
adb shell input keyevent KEYCODE_BACK
sleep 1
show_controls
tap_desc "Subtitles"
subtitle_track_found=false
subtitle_selector=""
for _ in $(seq 1 10); do
  refresh_ui
  if python3 /tmp/night_video_uia.py text_contains "Night test subtitle" >/dev/null 2>&1; then
    subtitle_track_found=true
    subtitle_selector="Night test subtitle"
    break
  fi
  if python3 /tmp/night_video_uia.py text_contains "MOV text" >/dev/null 2>&1; then
    subtitle_track_found=true
    subtitle_selector="MOV text"
    break
  fi
  sleep 1
done
cp /tmp/window.xml "$ARTIFACTS/06-subtitle-menu.xml"
if [ "$subtitle_track_found" != "true" ]; then
  echo "Subtitle track from the real MP4 was not exposed with parsed track metadata." >&2
  adb exec-out screencap -p > "$ARTIFACTS/failure-subtitle-track.png" || true
  capture_media_logcat "subtitle-track-missing"
  exit 1
fi
tap_text_contains "$subtitle_selector"
capture_media_logcat "04-tracks"
assert_no_crash

echo "STEP: seek"
show_controls
refresh_ui
seek_bounds="$(python3 /tmp/night_video_uia.py bottom_seekbar)"
read -r sx1 sy1 sx2 sy2 <<<"$seek_bounds"
seek_y=$(((sy1 + sy2) / 2))
seek_x=$((sx1 + (sx2 - sx1) * 72 / 100))
adb shell input tap "$seek_x" "$seek_y"
sleep 1
seek_time="$(read_current_time)"
seek_seconds="$(to_seconds "$seek_time")"
if [ "$seek_seconds" -lt 15 ]; then
  echo "Seek did not move playback far enough into the real video: $seek_time" >&2
  adb exec-out screencap -p > "$ARTIFACTS/failure-seek.png"
  exit 1
fi
capture_media_logcat "05-seek"
assert_no_crash

echo "STEP: portrait and landscape"
show_controls
tap_desc "Rotate"
sleep 2
read -r landscape_w landscape_h <<<"$(capture_dims "$ARTIFACTS/04-landscape.png")"
if [ "$landscape_w" -le "$landscape_h" ]; then
  echo "Night video did not enter landscape after Rotate." >&2
  exit 1
fi
show_controls
tap_desc "Rotate"
sleep 2
read -r portrait_w portrait_h <<<"$(capture_dims "$ARTIFACTS/05-portrait.png")"
if [ "$portrait_h" -le "$portrait_w" ]; then
  echo "Night video did not return to portrait." >&2
  exit 1
fi
capture_media_logcat "06-rotation"
assert_no_crash

echo "STEP: picture in picture and return"
show_controls
tap_desc "Picture in picture"
sleep 2
adb shell dumpsys activity activities > "$ARTIFACTS/06-pip-activity.txt"
if ! grep -Eq "mLastReportedPictureInPictureMode=true|inPictureInPictureMode=true|pictureInPictureMode=true" "$ARTIFACTS/06-pip-activity.txt"; then
  echo "Night did not report entering picture-in-picture mode." >&2
  exit 1
fi
assert_no_crash
adb shell am start -W --activity-clear-top --activity-single-top \
  -n "$PACKAGE/.MediaViewerPreviewActivity" \
  --es night.preview.videoPath "$APP_VIDEO" >/dev/null
sleep 2
show_controls
capture_media_logcat "07-pip-return"
assert_no_crash

echo "STEP: next and previous video"
show_controls
refresh_ui
if [ "$(python3 /tmp/night_video_uia.py desc_enabled "Next media")" != "true" ]; then
  echo "Next media was not enabled on the first video." >&2
  exit 1
fi
tap_desc "Next media"
sleep 2
show_controls
refresh_ui
if [ "$(python3 /tmp/night_video_uia.py desc_enabled "Previous media")" != "true" ]; then
  echo "Previous media was not enabled after moving to the second video." >&2
  exit 1
fi
tap_desc "Previous media"
sleep 2
show_controls
refresh_ui
if [ "$(python3 /tmp/night_video_uia.py desc_enabled "Next media")" != "true" ]; then
  echo "Next media was not re-enabled after returning to the first video." >&2
  exit 1
fi
capture_media_logcat "08-next-previous"
assert_no_crash

echo "STEP: video editor trim and mute export"
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/.MediaEditorPreviewActivity" \
  --es night.preview.videoPath "$APP_VIDEO"
sleep 4
assert_alive
assert_text "Trim"
assert_desc "Mute"
assert_desc "Send media"
capture_media_logcat "09-editor-open"
assert_no_crash
adb exec-out screencap -p > "$ARTIFACTS/07-editor-real-video-open.png" || true

editor_first_time="$(read_current_time)"
editor_first_seconds="$(to_seconds "$editor_first_time")"
editor_second_time="$editor_first_time"
editor_second_seconds="$editor_first_seconds"
for _ in $(seq 1 8); do
  sleep 2
  editor_second_time="$(read_current_time)"
  editor_second_seconds="$(to_seconds "$editor_second_time")"
  if [ "$editor_second_seconds" -gt "$editor_first_seconds" ]; then
    break
  fi
done
capture_media_logcat "09-editor-playback"
assert_no_crash
if [ "$editor_second_seconds" -le "$editor_first_seconds" ]; then
  echo "Real MP4 editor preview did not advance after recovery window: $editor_first_time -> $editor_second_time" >&2
  adb exec-out screencap -p > "$ARTIFACTS/failure-editor-playback-stalled.png" || true
  cp /tmp/window.xml "$ARTIFACTS/failure-editor-playback-stalled.xml" 2>/dev/null || true
  exit 1
fi

refresh_ui
initial_trim="$(python3 /tmp/night_video_uia.py trim)"
refresh_ui
trim_bounds="$(python3 /tmp/night_video_uia.py bottom_seekbar)"
read -r tx1 ty1 tx2 ty2 <<<"$trim_bounds"
trim_y=$(((ty1 + ty2) / 2))

trim_changed=false
# Material3 RangeSlider changes its handles by dragging; a plain rail tap is
# intentionally not a reliable thumb move. Drag the right/end handle inward
# just like a user would when trimming the end of a clip.
trim_start_x=$((tx1 + (tx2 - tx1) * 98 / 100))
for pct in 72 65 58; do
  trim_x=$((tx1 + (tx2 - tx1) * pct / 100))
  adb shell input swipe "$trim_start_x" "$trim_y" "$trim_x" "$trim_y" 650
  sleep 1
  refresh_ui
  changed_trim="$(python3 /tmp/night_video_uia.py trim)"
  if [ "$changed_trim" != "$initial_trim" ]; then
    trim_changed=true
    break
  fi
done
if [ "$trim_changed" != "true" ]; then
  echo "Night video trim range did not change after dragging the end handle." >&2
  adb exec-out screencap -p > "$ARTIFACTS/failure-trim.png"
  cp /tmp/window.xml "$ARTIFACTS/failure-trim.xml" 2>/dev/null || true
  exit 1
fi
printf 'before=%s\nafter=%s\n' "$initial_trim" "$changed_trim" > "$ARTIFACTS/07-trim-range.txt"

tap_desc "Mute"
assert_desc "Unmute"
tap_desc "Unmute"
assert_desc "Mute"
tap_desc "Mute"
assert_desc "Unmute"
adb exec-out screencap -p > "$ARTIFACTS/08-editor-ready.png"
tap_desc "Send media"

exported_path=""
for _ in $(seq 1 90); do
  prefs="$(adb shell run-as "$PACKAGE" cat shared_prefs/night_media_preview.xml 2>/dev/null || true)"
  if printf '%s' "$prefs" | grep -q 'name="exportError"'; then
    printf '%s\n' "$prefs" > "$ARTIFACTS/export-prefs-error.xml"
    echo "Night Media3 video export reported an error." >&2
    exit 1
  fi
  exported_path="$(printf '%s\n' "$prefs" | sed -n 's/.*<string name="exportPath">\([^<]*\)<\/string>.*/\1/p' | head -n 1)"
  if [ -n "$exported_path" ]; then
    printf '%s\n' "$prefs" > "$ARTIFACTS/export-prefs.xml"
    break
  fi
  sleep 1
done
if [ -z "$exported_path" ]; then
  echo "Night video editor did not finish exporting." >&2
  exit 1
fi
adb shell run-as "$PACKAGE" test -s "$exported_path"
adb exec-out run-as "$PACKAGE" cat "$exported_path" > "$ARTIFACTS/exported-video.mp4"
ffprobe -v error -show_format -show_streams "$ARTIFACTS/exported-video.mp4" \
  > "$ARTIFACTS/09-exported-ffprobe.txt"

export_duration="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$ARTIFACTS/exported-video.mp4" | cut -d. -f1)"
if [ "$export_duration" -ge 29 ] || [ "$export_duration" -lt 10 ]; then
  echo "Trimmed export duration is unexpected: $export_duration seconds." >&2
  exit 1
fi
audio_streams="$(ffprobe -v error -select_streams a -show_entries stream=index -of csv=p=0 "$ARTIFACTS/exported-video.mp4" | wc -l)"
if [ "$audio_streams" -ne 0 ]; then
  echo "Muted export still contains an audio stream." >&2
  exit 1
fi
video_streams="$(ffprobe -v error -select_streams v -show_entries stream=index -of csv=p=0 "$ARTIFACTS/exported-video.mp4" | wc -l)"
if [ "$video_streams" -lt 1 ]; then
  echo "Exported Night video has no video stream." >&2
  exit 1
fi
capture_media_logcat "10-export"
assert_no_crash

echo "STEP: replay exported MP4"
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/.MediaViewerPreviewActivity" \
  --es night.preview.videoPath "$exported_path"
sleep 4
assert_alive
show_controls
export_first="$(read_current_time)"
sleep 2
export_second="$(read_current_time)"
if [ "$(to_seconds "$export_second")" -le "$(to_seconds "$export_first")" ]; then
  echo "Exported Night MP4 did not play after reopening." >&2
  exit 1
fi
adb exec-out screencap -p > "$ARTIFACTS/10-exported-replay.png"
capture_media_logcat "11-exported-replay"
adb logcat -d -v threadtime > "$ARTIFACTS/logcat.txt"
grep -Ei \
  'NightVideo|libvlc|VLC|MediaCodec|CCodec|Codec2|ACodec|Surface|BufferQueue|Transformer|Media3|AndroidRuntime|FATAL EXCEPTION|ANR in|Fatal signal|SIGSEGV|SIGABRT|(^|[[:space:]])E/' \
  "$ARTIFACTS/logcat.txt" > "$ARTIFACTS/logcat-media-summary.txt" || true
assert_no_crash

cat > "$ARTIFACTS/summary.txt" <<EOF
realMp4Playback=true
controlsHideShow=true
pauseResume=true
speed2x=true
audioMenu=true
subtitleMenu=true
seek=true
landscapeRoundTrip=true
pictureInPicture=true
nextPrevious=true
editorPreviewPlayback=true
trimChanged=true
mutedExport=true
exportedDurationSeconds=$export_duration
exportedReplay=true
nightVideoFallbackMarkers=$(grep -h -c 'NightVideo' "$ARTIFACTS"/logcat-*-media.txt 2>/dev/null | awk '{s+=$1} END {print s+0}')
mediaLogWarningFiles=$(find "$ARTIFACTS" -name 'logcat-*-media.txt' -size +0c | wc -l)
EOF
