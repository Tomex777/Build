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

echo 'Yomi ABR static contracts passed (emulator/device review still required).'
