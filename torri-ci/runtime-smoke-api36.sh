#!/usr/bin/env bash
set -euo pipefail

RUNTIME_DIR="$GITHUB_WORKSPACE/torri-output/runtime"
APK="$GITHUB_WORKSPACE/torri-output/Torri-x86_64-debug.apk"
PACKAGE="app.torri.dev"
BOOTSTRAP_SENTINEL="/sdcard/Android/data/$PACKAGE/files/TorriCiStorage/.bootstrap-complete"
MAIN_ACTIVITY="eu.kanade.tachiyomi.ui.main.MainActivity"
BOOTSTRAP_ACTIVITY="eu.kanade.tachiyomi.ui.ci.TorriCiBootstrapActivity"
mkdir -p "$RUNTIME_DIR"

capture_diagnostics() {
    local result=$?
    trap - EXIT
    set +e
    adb -s emulator-5554 logcat -d -b all > "$RUNTIME_DIR/logcat.txt"
    adb -s emulator-5554 shell dumpsys activity activities > "$RUNTIME_DIR/activities.txt"
    adb -s emulator-5554 shell uiautomator dump /sdcard/torri-final.xml >/dev/null 2>&1
    adb -s emulator-5554 pull /sdcard/torri-final.xml "$RUNTIME_DIR/final-window.xml" >/dev/null 2>&1
    exit "$result"
}
trap capture_diagnostics EXIT

assert_no_torri_crash() {
    local focus=""
    focus="$(adb -s emulator-5554 shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus=' || true)"
    if [[ "$focus" == *"app.torri.dev/eu.kanade.tachiyomi.crash.CrashActivity"* ]]; then
        echo "Torri entered CrashActivity during API 36 runtime smoke: $focus" >&2
        adb -s emulator-5554 logcat -d -b all > "$RUNTIME_DIR/logcat.txt" || true
        grep -A 40 -B 4 'GlobalExceptionHandler' "$RUNTIME_DIR/logcat.txt" | tail -n 120 >&2 || true
        return 1
    fi
}

capture() {
    local name="$1"
    assert_no_torri_crash
    adb -s emulator-5554 exec-out screencap -p > "$RUNTIME_DIR/$name.png"
    file "$RUNTIME_DIR/$name.png" | grep -q 'PNG image data'
    test "$(stat -c%s "$RUNTIME_DIR/$name.png")" -gt 12000
}

wait_for_torri_focus() {
    local focus=""
    for attempt in $(seq 1 30); do
        focus="$(adb -s emulator-5554 shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus=' || true)"
        if [[ "$focus" == *"app.torri.dev/eu.kanade.tachiyomi.crash.CrashActivity"* ]]; then
            echo "Torri entered CrashActivity while waiting for foreground: $focus" >&2
            echo "$focus" > "$RUNTIME_DIR/current-focus.txt"
            return 1
        fi
        if [[ "$focus" == *"$PACKAGE"* ]]; then
            echo "$focus" > "$RUNTIME_DIR/current-focus.txt"
            return 0
        fi
        if [[ "$focus" == *"Application Not Responding"* || "$focus" == *"has stopped"* ]]; then
            echo "Unexpected system error dialog while waiting for Torri: $focus" >&2
            echo "$focus" > "$RUNTIME_DIR/current-focus.txt"
            return 1
        fi
        echo "Waiting for Torri foreground window (attempt $attempt/30, focus=${focus:-unknown})"
        sleep 1
    done
    echo "Torri never became the focused window" >&2
    adb -s emulator-5554 shell dumpsys window > "$RUNTIME_DIR/window-manager-timeout.txt" 2>/dev/null || true
    return 1
}

dump_ui() {
    adb -s emulator-5554 shell uiautomator dump /sdcard/torri-window.xml >/dev/null
    adb -s emulator-5554 pull /sdcard/torri-window.xml "$RUNTIME_DIR/window.xml" >/dev/null
}

find_coords() {
    local needle="$1"
    python3 - "$RUNTIME_DIR/window.xml" "$needle" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, needle = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
nodes = list(root.iter("node"))

def score(node):
    values = (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
    if any(value == needle for value in values):
        return 2
    if any(needle.casefold() in value.casefold() for value in values if value):
        return 1
    return 0

for wanted_score in (2, 1):
    for node in nodes:
        if score(node) != wanted_score:
            continue
        points = re.findall(r"\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if len(points) != 2:
            continue
        (x1, y1), (x2, y2) = ((int(x), int(y)) for x, y in points)
        print((x1 + x2) // 2, (y1 + y2) // 2)
        raise SystemExit(0)
raise SystemExit(1)
PY
}

find_exact_coords() {
    local needle="$1"
    python3 - "$RUNTIME_DIR/window.xml" "$needle" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, needle = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()

for node in root.iter("node"):
    values = (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
    if not any(value == needle for value in values):
        continue
    points = re.findall(r"\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if len(points) != 2:
        continue
    (x1, y1), (x2, y2) = ((int(x), int(y)) for x, y in points)
    print((x1 + x2) // 2, (y1 + y2) // 2)
    raise SystemExit(0)
raise SystemExit(1)
PY
}

return_to_main_navigation() {
    local coords=""
    for attempt in $(seq 1 5); do
        dump_ui
        coords="$(find_exact_coords "More" 2>/dev/null || true)"
        if [[ -n "$coords" ]]; then
            return 0
        fi
        assert_no_torri_crash
        adb -s emulator-5554 shell input keyevent 4
        sleep 1
    done

    dump_ui
    coords="$(find_exact_coords "More" 2>/dev/null || true)"
    if [[ -n "$coords" ]]; then
        return 0
    fi

    echo "Could not return to Torri main navigation after nested source/search flow" >&2
    return 1
}

tap_text() {
    local needle="$1"
    local coords=""
    for attempt in $(seq 1 6); do
        dump_ui
        coords="$(find_coords "$needle" 2>/dev/null || true)"
        if [[ -n "$coords" ]]; then
            read -r x y <<<"$coords"
            adb -s emulator-5554 shell input tap "$x" "$y"
            sleep 1
            return 0
        fi
        adb -s emulator-5554 shell input swipe 540 1500 540 620 320
        sleep 1
    done

    echo "Could not find UI text: $needle" >&2
    dump_ui || true
    python3 - "$RUNTIME_DIR/window.xml" <<'PY' >&2 || true
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
seen = []
for node in root.iter("node"):
    for key in ("text", "content-desc"):
        value = node.attrib.get(key, "").strip()
        if value and value not in seen:
            seen.append(value)
print("\n".join(seen))
PY
    return 1
}

wait_for_text() {
    local needle="$1"
    local attempts="${2:-12}"
    for attempt in $(seq 1 "$attempts"); do
        dump_ui
        if [[ -n "$(find_coords "$needle" 2>/dev/null || true)" ]]; then
            return 0
        fi
        assert_no_torri_crash
        sleep 1
    done
    echo "Timed out waiting for UI text: $needle" >&2
    return 1
}

scroll_until_text() {
    local needle="$1"
    local attempts="${2:-8}"
    local coords=""
    for attempt in $(seq 1 "$attempts"); do
        dump_ui
        coords="$(find_coords "$needle" 2>/dev/null || true)"
        if [[ -n "$coords" ]]; then
            return 0
        fi
        assert_no_torri_crash
        # Reader settings is a vertically scrollable Mihon pane. Keep the
        # sticky tab row visible while moving deeper settings into the viewport.
        adb -s emulator-5554 shell input swipe 540 1840 540 920 320
        sleep 1
    done
    echo "Timed out scrolling for UI text: $needle" >&2
    return 1
}

tap_reader_seekbar_middle() {
    local coords=""
    dump_ui
    coords="$(python3 - "$RUNTIME_DIR/window.xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("class") != "android.widget.SeekBar":
        continue
    points = re.findall(r"\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if len(points) != 2:
        continue
    (x1, y1), (x2, y2) = ((int(x), int(y)) for x, y in points)
    print((x1 + x2) // 2, (y1 + y2) // 2)
    raise SystemExit(0)
raise SystemExit(1)
PY
)"
    [[ -n "$coords" ]]
    read -r x y <<<"$coords"
    adb -s emulator-5554 shell input tap "$x" "$y"
    sleep 2
}

read_chapter_progress() {
    local manga_title="$1"
    local chapter_name="$2"
    local tag="$3"
    local db="$RUNTIME_DIR/tachiyomi-$tag.db"

    rm -f "$db" "$db-wal" "$db-shm"
    adb -s emulator-5554 exec-out run-as "$PACKAGE" cat databases/tachiyomi.db > "$db"
    test -s "$db"
    if adb -s emulator-5554 shell "run-as $PACKAGE sh -c 'test -f databases/tachiyomi.db-wal'" >/dev/null 2>&1; then
        adb -s emulator-5554 exec-out run-as "$PACKAGE" cat databases/tachiyomi.db-wal > "$db-wal"
    fi

    python3 - "$db" "$manga_title" "$chapter_name" <<'PY'
import sqlite3
import sys

db, manga_title, chapter_name = sys.argv[1:]
connection = sqlite3.connect(db)
row = connection.execute(
    """
    SELECT c.last_page_read, c.read
    FROM chapters c
    JOIN mangas m ON m._id = c.manga_id
    WHERE m.title = ? AND c.name = ?
    ORDER BY c._id DESC
    LIMIT 1
    """,
    (manga_title, chapter_name),
).fetchone()
connection.close()
if row is None:
    raise SystemExit(f"No chapter progress row for {manga_title} / {chapter_name}")
print(f"{int(row[0])}:{int(row[1])}")
PY
}

open_red_chapter_from_main() {
    adb -s emulator-5554 shell am start -n "$PACKAGE/$MAIN_ACTIVITY" > "$RUNTIME_DIR/relaunch-main.txt"
    wait_for_torri_focus
    wait_for_text "Library" 12
    tap_text "Browse"
    wait_for_text "Sources" 12
    tap_text "Sources"
    tap_text "Local source"
    wait_for_text "Torri Red" 15
    tap_text "Torri Red"
    wait_for_text "Chapter 1" 15
    tap_text "Chapter 1"
    sleep 4
    wait_for_torri_focus
    show_reader_controls
    wait_for_text "Chapter 1" 12
}

tap_text_if_present() {
    local needle="$1"
    local coords=""
    dump_ui
    coords="$(find_coords "$needle" 2>/dev/null || true)"
    if [[ -z "$coords" ]]; then
        return 1
    fi
    read -r x y <<<"$coords"
    adb -s emulator-5554 shell input tap "$x" "$y"
    sleep 1
    return 0
}

show_reader_controls() {
    dump_ui
    if [[ -n "$(find_coords "Reading mode" 2>/dev/null || true)" ]]; then
        return 0
    fi

    adb -s emulator-5554 shell input tap 540 960
    wait_for_text "Reading mode" 10
}

run_bootstrap() {
    adb -s emulator-5554 shell rm -f "$BOOTSTRAP_SENTINEL" >/dev/null 2>&1 || true
    adb -s emulator-5554 shell am start -n "$PACKAGE/$BOOTSTRAP_ACTIVITY" | tee "$RUNTIME_DIR/bootstrap.txt"

    local bootstrap_ready=false
    for attempt in $(seq 1 90); do
        if adb -s emulator-5554 shell "test -s '$BOOTSTRAP_SENTINEL'" >/dev/null 2>&1; then
            bootstrap_ready=true
            break
        fi
        assert_no_torri_crash
        sleep 1
    done

    if [[ "$bootstrap_ready" != true ]]; then
        echo "Torri CI bootstrap did not complete within 90 seconds" >&2
        adb -s emulator-5554 logcat -d -b all > "$RUNTIME_DIR/bootstrap-timeout-logcat.txt" || true
        adb -s emulator-5554 shell dumpsys activity activities > "$RUNTIME_DIR/bootstrap-timeout-activities.txt" || true
        return 1
    fi

    adb -s emulator-5554 shell am force-stop "$PACKAGE"
}

sdk_level=""
for attempt in $(seq 1 24); do
    sdk_level="$(adb -s emulator-5554 shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
    if [[ "$sdk_level" == "36" ]] && adb -s emulator-5554 shell pm path android >/dev/null 2>&1; then
        break
    fi
    echo "Waiting for responsive API 36 package services (attempt $attempt, SDK=${sdk_level:-unknown})"
    sleep 10
done
[[ "$sdk_level" == "36" ]]
adb -s emulator-5554 shell pm path android
sleep 10

test -s "$APK"
adb -s emulator-5554 install -r "$APK"

# Generate deterministic Local Source fixtures and persist first-run state.
run_bootstrap

# Capture the SDK 36 startup path immediately, then the settled Library screen.
adb -s emulator-5554 logcat -c || true
adb -s emulator-5554 shell am start -n "$PACKAGE/$MAIN_ACTIVITY" > "$RUNTIME_DIR/launch.txt"
wait_for_torri_focus
capture "00-startup-light"
sleep 4
wait_for_torri_focus
adb -s emulator-5554 shell pidof "$PACKAGE" | tee "$RUNTIME_DIR/pid.txt"
test -s "$RUNTIME_DIR/pid.txt"
capture "01-library-light"

# More/About identity surfaces.
tap_text "More"
wait_for_text "Downloaded only" 12
capture "02-more-light"

# Mature More destinations should remain reachable through Torri's branded shell.
tap_text "Download Queue"
sleep 1
capture "02a-download-queue-light"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Downloaded only" 12

tap_text "Categories"
sleep 1
capture "02b-categories-light"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Downloaded only" 12

tap_text "Statistics"
sleep 1
capture "02c-statistics-light"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Downloaded only" 12

tap_text "About"
sleep 1
capture "03-about-light"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Downloaded only" 12

tap_text "History"
sleep 1
capture "03a-history-light"

tap_text "Updates"
sleep 1
capture "03b-updates-light"

tap_text "More"
wait_for_text "Downloaded only" 12
tap_text "Data and storage"
wait_for_text "Storage location" 12
wait_for_text "Backup and restore" 12
capture "03c-data-storage-light"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Downloaded only" 12

# Source and extension surfaces.
tap_text "Browse"
sleep 2
capture "04-sources-light"
tap_text "Extensions"
sleep 2
capture "05-extensions-light"
tap_text "Sources"
sleep 1
tap_text "Local source"
sleep 3
capture "06-local-source-light"

# Cover extremes. Local Source sorts these fixture titles alphabetically; keeping
# that order means the helper only needs to scroll forward.
for title in "Torri Blue" "Torri Bright" "Torri Dark" "Torri Green" "Torri Missing" "Torri Monochrome" "Torri Red"; do
    tap_text "$title"
    if [[ "$title" != "Torri Missing" ]]; then
        wait_for_text "Chapter 1" 15
    else
        # Missing deliberately has no cover and no chapter so it cannot synthesize a cover.
        wait_for_text "0 chapters" 15
    fi
    sleep 1
    slug="$(echo "$title" | tr '[:upper:] ' '[:lower:]-')"
    capture "details-light-$slug"

    if [[ "$title" == "Torri Red" ]]; then
        tap_text "Chapter 1"
        sleep 4

        # Android shows an immersive-mode education bubble on a fresh emulator.
        # Dismiss it so the evidence proves Torri's real reader, not System UI.
        tap_text_if_present "Got it" || true
        sleep 1
        wait_for_torri_focus
        capture "07-reader-light"

        adb -s emulator-5554 shell pidof "$PACKAGE" | tee "$RUNTIME_DIR/reader-pid.txt"
        test -s "$RUNTIME_DIR/reader-pid.txt"

        # Reveal the mature Mihon reader chrome and keep a hierarchy snapshot
        # so reader controls are proven independently of page rendering.
        show_reader_controls
        capture "08-reader-controls-light"
        dump_ui
        cp "$RUNTIME_DIR/window.xml" "$RUNTIME_DIR/reader-controls.xml"

        # Select a non-final page in the six-page real Local Source chapter.
        # The visible fixture label makes before/after restore evidence reviewable.
        tap_reader_seekbar_middle
        capture "08-restore-target-light"

        # Kill the process while the reader is active. With Torri stopped, the
        # copied SQLite/WAL pair is stable and represents durable reader progress.
        adb -s emulator-5554 shell am force-stop "$PACKAGE"
        saved_progress="$(read_chapter_progress "Torri Red" "Chapter 1" "process-death")"
        IFS=: read -r saved_page_index saved_read <<<"$saved_progress"
        (( saved_page_index > 0 && saved_page_index < 5 ))
        [[ "$saved_read" == "0" ]]
        printf '%s\n' "$saved_progress" > "$RUNTIME_DIR/process-death-progress.txt"

        # Relaunch normally, navigate through Torri's production screens, and
        # reopen the same chapter. A wrong restored page will overwrite
        # last_page_read and fail the comparison after the second force-stop.
        open_red_chapter_from_main
        capture "08-reopened-position-light"
        adb -s emulator-5554 shell am force-stop "$PACKAGE"
        reopened_progress="$(read_chapter_progress "Torri Red" "Chapter 1" "reopened")"
        [[ "$reopened_progress" == "$saved_progress" ]]
        printf '%s\n' "$reopened_progress" > "$RUNTIME_DIR/reopened-progress.txt"

        # Re-enter once more so the remaining reader acceptance continues from
        # the exact durable position rather than a synthetic test activity.
        open_red_chapter_from_main

        # The red fixture has two real Local Source chapters. Exercise actual
        # next/previous chapter transitions through Mihon's reader controls.
        tap_text "Next chapter"
        sleep 2
        show_reader_controls
        wait_for_text "Chapter 2" 12
        capture "08a-chapter-next-light"

        tap_text "Previous chapter"
        sleep 2
        show_reader_controls
        wait_for_text "Chapter 1" 12
        capture "08b-chapter-return-light"

        # Exercise a non-paged viewer without replacing or mocking the reader,
        # then restore the per-series mode back to Mihon's default.
        tap_text "Reading mode"
        wait_for_text "Long strip" 10
        capture "08c-reading-mode-light"
        tap_text "Long strip"
        tap_text "Apply"
        sleep 2
        show_reader_controls
        capture "08d-long-strip-light"

        # Scroll across several real page holders so recycling and decoding are
        # exercised instead of accepting only the first long-strip frame.
        adb -s emulator-5554 shell input tap 540 960
        for _ in 1 2 3; do
            adb -s emulator-5554 shell input swipe 540 1850 540 550 420
            sleep 1
        done
        capture "08d-long-strip-scrolled-light"
        show_reader_controls

        tap_text "Reading mode"
        wait_for_text "Revert to default" 10
        tap_text "Revert to default"
        sleep 2
        show_reader_controls

        # Exercise per-series orientation using a portrait-safe choice so CI
        # validates the setting path without making later coordinate checks flaky.
        tap_text "Rotation"
        wait_for_text "Locked portrait" 10
        tap_text "Locked portrait"
        tap_text "Apply"
        sleep 1
        show_reader_controls
        capture "08e-orientation-light"

        tap_text "Rotation"
        wait_for_text "Revert to default" 10
        tap_text "Revert to default"
        sleep 1
        show_reader_controls

        # Reader settings opens on the Reading mode pane in current Mihon.
        # Exercise a real scale-mode change, restore Fit screen, then visit
        # General and Custom filter as distinct mature reader surfaces.
        tap_text "Settings"
        wait_for_text "Tap zones" 10
        scroll_until_text "Scale type" 8
        capture "08f-reader-settings-reading-mode-light"

        tap_text "Fit width"
        sleep 1
        capture "08g-scale-fit-width-light"
        tap_text "Fit screen"
        sleep 1

        tap_text "General"
        wait_for_text "Background color" 10
        capture "08h-reader-settings-general-light"

        tap_text "Custom filter"
        wait_for_text "Color filter" 10
        capture "08i-reader-settings-filter-light"

        # Change a real reader preference, then prove the settings sheet can be
        # closed without destroying the active chapter/reader state.
        tap_text "Color filter"
        wait_for_text "Color filter" 10
        capture "08j-reader-filter-changed-light"
        adb -s emulator-5554 shell input keyevent 4
        show_reader_controls
        capture "08k-reader-after-settings-light"

        # Validate a real reader background/foreground lifecycle. Monkey uses
        # the launcher's normal task semantics, so an intact ReaderActivity task
        # must return to the same active chapter rather than being reconstructed
        # as a fake acceptance shell.
        adb -s emulator-5554 shell input keyevent 3
        sleep 2
        adb -s emulator-5554 shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1             > "$RUNTIME_DIR/reader-foreground-resume.txt"
        wait_for_torri_focus
        show_reader_controls
        wait_for_text "Chapter 1" 12
        capture "08l-reader-foreground-resume-light"

        # Back out robustly even when the first Back only closes reader chrome.
        returned_to_details=false
        for attempt in $(seq 1 3); do
            adb -s emulator-5554 shell input keyevent 4
            sleep 1
            dump_ui
            if [[ -n "$(find_coords "Add to library" 2>/dev/null || true)" ]]; then
                returned_to_details=true
                break
            fi
        done
        [[ "$returned_to_details" == true ]]
    fi

    adb -s emulator-5554 shell input keyevent 4
    wait_for_text "Local source" 12
    sleep 1
done

# Exercise Local Source search and prove the result can be opened.
tap_text "Search"
adb -s emulator-5554 shell input text "Torri%sRed"
adb -s emulator-5554 shell input keyevent 66
sleep 2
capture "09-search-light"
tap_text "Torri Red"
sleep 2
capture "10-search-details-light"

# Back from details must restore the nested Local Source/search context first.
# Then unwind that nested stack until the real main-navigation "More" target
# is present. This deliberately uses exact matching so "More options" can
# never masquerade as the main destination.
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Filter" 12
return_to_main_navigation

# Finish on Torri's Settings surface instead of ending inside the source.
tap_text "More"
wait_for_text "Settings" 12
tap_text "Settings"
# App Settings now opens on Torri's category root (Appearance/Library/Reader/...).
# Keep the reader-sheet "General" assertion above: that is a separate Mihon reader surface.
wait_for_text "Appearance" 12
capture "11-settings-light"

# Force ACTUAL Android dark mode and verify UiModeManager before dark-theme QA.
# This prevents a fixture title such as "Torri Dark" from being mislabeled as
# proof of a dark application theme.
adb -s emulator-5554 shell cmd uimode night yes
sleep 2
adb -s emulator-5554 shell dumpsys uimode > "$RUNTIME_DIR/uimode-dark.txt"
grep -Eq 'mNightMode=2|mComputedNightMode=true' "$RUNTIME_DIR/uimode-dark.txt"
wait_for_torri_focus
wait_for_text "Appearance" 12
capture "12-settings-dark"

adb -s emulator-5554 shell input keyevent 4
wait_for_text "Downloaded only" 12
capture "13-more-dark"

tap_text "About"
wait_for_text "Torri" 12
capture "13a-about-dark"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Downloaded only" 12

tap_text "Download Queue"
sleep 1
capture "13b-download-queue-dark"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Downloaded only" 12

tap_text "Categories"
sleep 1
capture "13c-categories-dark"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Downloaded only" 12

tap_text "Statistics"
sleep 1
capture "13d-statistics-dark"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Downloaded only" 12

tap_text "History"
sleep 1
capture "13e-history-dark"

tap_text "Updates"
sleep 1
capture "13f-updates-dark"

tap_text "More"
wait_for_text "Downloaded only" 12
tap_text "Data and storage"
wait_for_text "Storage location" 12
wait_for_text "Backup and restore" 12
capture "13g-data-storage-dark"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Downloaded only" 12

tap_text "Library"
sleep 1
capture "14-library-dark"

tap_text "Browse"
wait_for_text "Sources" 12
capture "15-browse-dark"

tap_text "Sources"
tap_text "Local source"
wait_for_text "Torri Red" 12
capture "16-local-source-dark"

# Cover-adaptive details in a real dark system theme.
tap_text "Torri Red"
wait_for_text "Chapter 1" 15
sleep 1
capture "17-details-cover-dark"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Local source" 12

# Missing-cover details must use Torri's deliberate branded fallback in dark mode.
tap_text "Torri Missing"
wait_for_text "0 chapters" 15
sleep 1
capture "18-details-missing-cover-dark"
adb -s emulator-5554 shell input keyevent 4
wait_for_text "Local source" 12

# Reader and reader-settings coverage in actual dark mode.
tap_text "Torri Red"
wait_for_text "Chapter 1" 15
tap_text "Chapter 1"
sleep 4
tap_text_if_present "Got it" || true
wait_for_torri_focus
capture "19-reader-dark"

adb -s emulator-5554 shell input tap 540 960
wait_for_text "Reading mode" 10
capture "20-reader-controls-dark"

tap_text "Settings"
wait_for_text "General" 10
tap_text "Custom filter"
wait_for_text "Color filter" 10
capture "21-reader-settings-dark"

adb -s emulator-5554 shell input keyevent 4
wait_for_text "Reading mode" 10
capture "22-reader-after-settings-dark"

returned_to_details=false
for attempt in $(seq 1 3); do
    adb -s emulator-5554 shell input keyevent 4
    sleep 1
    dump_ui
    if [[ -n "$(find_coords "Add to library" 2>/dev/null || true)" ]]; then
        returned_to_details=true
        break
    fi
done
[[ "$returned_to_details" == true ]]

adb -s emulator-5554 logcat -d -b all > "$RUNTIME_DIR/logcat.txt"
if grep -A 80 'FATAL EXCEPTION' "$RUNTIME_DIR/logcat.txt" | grep -q "$PACKAGE" ||
   grep -Fq 'GlobalExceptionHandler:' "$RUNTIME_DIR/logcat.txt"; then
    echo "Torri crashed during API 36 runtime smoke" >&2
    exit 1
fi

# A cover-adaptive details run is not valid if Local Source cover URIs failed.
if grep -F 'TorriCiStorage' "$RUNTIME_DIR/logcat.txt" | grep -Fq 'FileNotFoundException'; then
    echo "Torri Local Source cover/page fixture failed to load" >&2
    grep -F 'TorriCiStorage' "$RUNTIME_DIR/logcat.txt" | grep -F 'FileNotFoundException' >&2 || true
    exit 1
fi

shot_count="$(find "$RUNTIME_DIR" -maxdepth 1 -name '*.png' | wc -l)"
test "$shot_count" -ge 58
echo "Captured $shot_count Torri API 36 light/dark screenshots"


# Finalization gate: exercise the actual app.torri release APK on this emulator.
RELEASE_EVIDENCE_DIR="$RUNTIME_DIR/release" bash "$GITHUB_WORKSPACE/torri-ci/runtime-release-smoke.sh" 36
