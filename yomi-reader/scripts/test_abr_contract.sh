#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
home="$root/app/src/main/java/app/yomi/reader/MainActivity.kt"
gradle="$root/app/build.gradle.kts"

# ABR static guards supplement, not replace, actual emulator/device testing.
grep -Eq '^[[:space:]]*minSdk = 26$' "$gradle"
grep -Fq 'BasicTextField(' "$home"
! grep -Eq '^[[:space:]]*TextField\(' "$home"
! grep -Fq 'import androidx.compose.material3.TextField' "$home"
grep -Fq 'cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)' "$home"
grep -Fq 'keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)' "$home"
grep -Fq 'val uniqueRecent = recent.filterNot { it.id == currentBook?.id }' "$home"
grep -Fq 'items(uniqueRecent.take(5)' "$home"
grep -Fq 'painter = painterResource(R.drawable.ic_yomi)' "$home"

# Mihon's toolbar geometry and modal sheets must not create dead touch zones
# across the reader's central page surface.
reader="$root/app/src/main/java/app/yomi/reader/ReaderActivity.kt"
bars="$root/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt"
grep -Fq 'onTopBarBottom = { topBarBottomPx = it }' "$reader"
grep -Fq 'onBottomBarTop = { bottomBarTopPx = it }' "$reader"
grep -Fq 'val dialogOpen = settingsOpen.value' "$reader"
grep -Fq 'routingToChrome = dialogOpen ||' "$reader"
grep -Fq 'onGloballyPositioned' "$bars"
! grep -Fq 'height.toFloat() - dp(205)' "$reader"

# Catch syntax failures in the emulator interaction gate before booting Android.
python3 -m py_compile "$root/scripts/verify_reader_settings_taps.py"

echo 'Yomi ABR static contracts passed (emulator/device review still required).'
