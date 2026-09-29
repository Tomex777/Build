#!/usr/bin/env bash
set -Eeuo pipefail

APK="${CORTEX_RELEASE_APK:?CORTEX_RELEASE_APK must point to the QA-signed release APK}"
TEST_APK="${CORTEX_RELEASE_TEST_APK:-}"
API_LEVEL="${CORTEX_RELEASE_API_LEVEL:?CORTEX_RELEASE_API_LEVEL is required}"
OUT_DIR="$GITHUB_WORKSPACE/cortex-release-api${API_LEVEL}"
UI_DUMP="$OUT_DIR/ui.xml"
FOREGROUND="$OUT_DIR/foreground.txt"
SCREENSHOT="$OUT_DIR/home.png"
SANITY="$OUT_DIR/screenshot-sanity.txt"
PACKAGE="$OUT_DIR/package.txt"
LOGCAT="$OUT_DIR/logcat.txt"
DIAGNOSTICS="$OUT_DIR/diagnostics.txt"

mkdir -p "$OUT_DIR"
test -s "$APK"

framework_ready() {
  test "$(adb get-state 2>/dev/null || true)" = "device" || return 1
  test "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" || return 1
  adb shell pm path android 2>/dev/null | grep -q '^package:' || return 1
  adb shell am get-current-user >/dev/null 2>&1 || return 1
}

wait_for_android() {
  local attempt consecutive=0
  for attempt in $(seq 1 90); do
    if framework_ready; then
      consecutive=$((consecutive + 1))
      if (( consecutive >= 3 )); then return 0; fi
    else
      consecutive=0
    fi
    sleep 2
  done
  echo "Android framework did not stabilize for Cortex release smoke." >&2
  return 1
}

wake_and_unlock() {
  adb shell settings put system screen_off_timeout 1800000 >/dev/null 2>&1 || true
  adb shell svc power stayon true >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb shell input keyevent 82 >/dev/null 2>&1 || true
}

capture_foreground() {
  {
    echo "pid=$(adb shell pidof com.night.cortex 2>/dev/null | tr -d '\r' || true)"
    adb shell dumpsys activity activities 2>/dev/null |
      grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' | head -n 8 || true
    adb shell dumpsys window windows 2>/dev/null |
      grep -E 'mCurrentFocus|mFocusedApp' | head -n 8 || true
  } >"$FOREGROUND"
}

wait_for_cortex_foreground() {
  local attempt
  for attempt in $(seq 1 30); do
    capture_foreground
    if grep -q 'com.night.cortex/.MainActivity' "$FOREGROUND"; then return 0; fi
    sleep 1
  done
  cat "$FOREGROUND" >&2
  echo "Release Cortex MainActivity never became foreground." >&2
  return 1
}

dump_ui() {
  adb shell rm -f /sdcard/cortex-release-ui.xml >/dev/null 2>&1 || true
  adb shell uiautomator dump --compressed /sdcard/cortex-release-ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/cortex-release-ui.xml >"$UI_DUMP" 2>/dev/null || true
}

ui_is_cortex() {
  test -s "$UI_DUMP" &&
    grep -q 'package="com.night.cortex"' "$UI_DUMP" &&
    grep -Eq 'text="Cortex"|content-desc="Cortex"' "$UI_DUMP" &&
    grep -Eq 'text="Console"|content-desc="Console"' "$UI_DUMP" &&
    grep -Eq 'text="Connect Cortex Agent"|content-desc="Connect Cortex Agent"' "$UI_DUMP"
}

wait_for_cortex_ui() {
  local attempt wait_used=0 coords wait_x wait_y
  for attempt in $(seq 1 18); do
    dump_ui
    if ui_is_cortex; then return 0; fi

    if test -s "$UI_DUMP" &&
       grep -q 'package="android"' "$UI_DUMP" &&
       grep -Fq "Cortex isn't responding" "$UI_DUMP"; then
      cat "$UI_DUMP" >&2
      echo "Release Cortex displayed an ANR dialog." >&2
      return 1
    fi

    # Headless emulator images can occasionally raise a platform system ANR.
    # Recover only those exact platform dialogs once and re-prove Cortex afterward.
    if (( wait_used == 0 )) && test -s "$UI_DUMP" &&
       grep -Eq "System UI isn't responding|Process system isn't responding" "$UI_DUMP"; then
      coords="$(python3 - "$UI_DUMP" <<'PY'
import re, sys
text=open(sys.argv[1], encoding='utf-8', errors='replace').read()
m=re.search(r'resource-id="android:id/aerr_wait"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', text)
if m:
    x1,y1,x2,y2=map(int,m.groups())
    print((x1+x2)//2, (y1+y2)//2)
PY
)"
      read -r wait_x wait_y <<<"$coords"
      if [[ "$wait_x" =~ ^[0-9]+$ && "$wait_y" =~ ^[0-9]+$ ]]; then
        {
          echo "Targeted platform system ANR recovery at $wait_x,$wait_y"
          cat "$UI_DUMP"
        } >>"$DIAGNOSTICS"
        adb shell input tap "$wait_x" "$wait_y"
        wait_used=1
        wait_for_android
        wake_and_unlock
        adb shell am start -n com.night.cortex/.MainActivity >/dev/null
        continue
      fi
    fi
    sleep 1
  done
  cat "$UI_DUMP" >&2 || true
  echo "Release Cortex UI hierarchy did not become ready." >&2
  return 1
}

validate_png() {
  python3 - "$SCREENSHOT" "$SANITY" <<'PY'
import struct, sys, zlib
path, report = sys.argv[1:3]
data=open(path,'rb').read()
if not data.startswith(b'\x89PNG\r\n\x1a\n'):
    raise SystemExit('release screenshot is not PNG')
pos=8; width=height=depth=color=interlace=None; compressed=bytearray()
while pos+12 <= len(data):
    length=struct.unpack('>I',data[pos:pos+4])[0]
    kind=data[pos+4:pos+8]; payload=data[pos+8:pos+8+length]; pos += 12+length
    if kind==b'IHDR':
        width,height,depth,color,_,_,interlace=struct.unpack('>IIBBBBB',payload)
    elif kind==b'IDAT': compressed.extend(payload)
    elif kind==b'IEND': break
channels={0:1,2:3,4:2,6:4}.get(color)
if not width or not height or depth!=8 or interlace!=0 or channels is None:
    raise SystemExit('unsupported release screenshot format')
raw=zlib.decompress(bytes(compressed)); stride=width*channels; prior=bytearray(stride); offset=0
mn=255; mx=0; nonblack=0; unique=set(); step=max(1,(width*height)//100000)
def paeth(a,b,c):
    p=a+b-c; pa=abs(p-a); pb=abs(p-b); pc=abs(p-c)
    return a if pa<=pb and pa<=pc else b if pb<=pc else c
for y in range(height):
    filt=raw[offset]; offset+=1
    scan=bytearray(raw[offset:offset+stride]); offset+=stride
    recon=bytearray(stride)
    for i,v in enumerate(scan):
        left=recon[i-channels] if i>=channels else 0
        up=prior[i]; ul=prior[i-channels] if i>=channels else 0
        if filt==0: out=v
        elif filt==1: out=(v+left)&255
        elif filt==2: out=(v+up)&255
        elif filt==3: out=(v+((left+up)//2))&255
        elif filt==4: out=(v+paeth(left,up,ul))&255
        else: raise SystemExit('unsupported PNG filter')
        recon[i]=out
    for x in range(width):
        i=x*channels
        rgb=(recon[i],)*3 if color in (0,4) else tuple(recon[i:i+3])
        b=max(rgb); mn=min(mn,b); mx=max(mx,b)
        if b>12: nonblack+=1
        if (y*width+x)%step==0 and len(unique)<256: unique.add(rgb)
    prior=recon
fraction=nonblack/(width*height)
with open(report,'w') as f:
    f.write(f'size={width}x{height}\nbrightness_min={mn}\nbrightness_max={mx}\n')
    f.write(f'nonblack_fraction={fraction:.6f}\nsampled_unique_colors={len(unique)}\n')
# API 36 ATD/SwiftShader can lose only the host framebuffer after a valid UI
# hierarchy. Preserve that diagnostic instead of pretending it is a product frame.
if mx<=12 or mx-mn<=6 or fraction<0.01 or len(unique)<8:
    if int(sys.argv[0] != ''): pass
    raise SystemExit(2)
PY
}

wait_for_android
wake_and_unlock
adb uninstall com.night.cortex >/dev/null 2>&1 || true
adb install -r "$APK"
adb shell dumpsys package com.night.cortex >"$PACKAGE"
grep -q 'versionName=1.0.0' "$PACKAGE"
grep -q 'versionCode=100' "$PACKAGE"
grep -q 'minSdk=26' "$PACKAGE"
grep -q 'targetSdk=36' "$PACKAGE"

adb logcat -c >/dev/null 2>&1 || true
if [[ "$API_LEVEL" == "36" ]]; then
  test -s "$TEST_APK"
  adb install -r -g "$TEST_APK"
  INSTRUMENTATION="$OUT_DIR/instrumentation.txt"
  set +e
  timeout 10m adb shell am instrument -w -r \
    -e class com.night.cortex.CortexReleaseVisualTest \
    com.night.cortex.test/androidx.test.runner.AndroidJUnitRunner >"$INSTRUMENTATION" 2>&1
  instrumentation_rc=$?
  set -e
  cat "$INSTRUMENTATION"
  if (( instrumentation_rc != 0 )); then
    adb logcat -d -v threadtime >"$LOGCAT" 2>&1 || true
    tail -n 250 "$LOGCAT" >&2 || true
    echo "Release Compose screenshot instrumentation failed on API $API_LEVEL." >&2
    exit "$instrumentation_rc"
  fi
  if ! grep -q '^OK (1 test)' "$INSTRUMENTATION"; then
    adb logcat -d -v threadtime >"$LOGCAT" 2>&1 || true
    tail -n 250 "$LOGCAT" >&2 || true
    echo "Release Compose screenshot instrumentation did not report its expected passing test." >&2
    exit 1
  fi
  adb exec-out cat /sdcard/Android/data/com.night.cortex/cache/cortex-release-home.png >"$SCREENSHOT"
  test -s "$SCREENSHOT"
  validate_png
fi

adb shell am force-stop com.night.cortex
adb shell am start -n com.night.cortex/.MainActivity >/dev/null
wake_and_unlock
wait_for_cortex_foreground
wait_for_cortex_ui

if [[ "$API_LEVEL" != "36" ]]; then
  pixel_rc=2
  for attempt in $(seq 1 10); do
    adb exec-out screencap -p >"$SCREENSHOT"
    test -s "$SCREENSHOT"
    set +e
    validate_png
    pixel_rc=$?
    set -e
    if (( pixel_rc == 0 )); then
      echo "Captured a rendered Cortex release frame on API $API_LEVEL (attempt $attempt)." >>"$DIAGNOSTICS"
      break
    fi
    if (( pixel_rc != 2 )); then
      cat "$SANITY" >&2 || true
      echo "Release screenshot could not be decoded." >&2
      exit "$pixel_rc"
    fi
    echo "Release screenshot attempt $attempt had no rendered pixels; waiting for the compositor." >>"$DIAGNOSTICS"
    sleep 1
  done
  if (( pixel_rc != 0 )); then
    cat "$SANITY" >&2 || true
    echo "Release screenshot remained black after ten compositor retries." >&2
    exit "$pixel_rc"
  fi
fi

# Prove the actual minified release package survives process recreation.
adb shell am force-stop com.night.cortex
adb shell am start -n com.night.cortex/.MainActivity >/dev/null
wake_and_unlock
wait_for_cortex_foreground
wait_for_cortex_ui

adb logcat -b crash -d -v threadtime >"$LOGCAT" 2>&1 || true
if grep -Eq 'FATAL EXCEPTION|ANR in com\.night\.cortex' "$LOGCAT" &&
   grep -q 'com.night.cortex' "$LOGCAT"; then
  cat "$LOGCAT" >&2
  exit 1
fi

echo "Cortex release APK acceptance passed on API $API_LEVEL."
