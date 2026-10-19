#!/usr/bin/env bash
set -euo pipefail
# One-time, additive namespace modernization. The original patch-based
# prototype remains in EchoSpace/source.tar.xz for historical reference.
old=EchoSpace/app/src/main/java/com/nexa/echospace
new=EchoSpace/app/src/main/java/com/night/echospace
if [ -d "$old" ]; then
  mkdir -p EchoSpace/app/src/main/java/com/night
  git mv "$old" "$new"
fi
oldtest=EchoSpace/app/src/test/java/com/nexa/echospace
newtest=EchoSpace/app/src/test/java/com/night/echospace
if [ -d "$oldtest" ]; then
  mkdir -p EchoSpace/app/src/test/java/com/night
  git mv "$oldtest" "$newtest"
fi
python3 - <<'PY'
from pathlib import Path
targets=[
    *Path("EchoSpace/app/src/main/java/com/night/echospace").rglob("*.kt"),
    *Path("EchoSpace/app/src/test/java/com/night/echospace").rglob("*.kt"),
    Path("EchoSpace/app/build.gradle.kts"),
    Path(".github/workflows/echospace-production-ci.yml"),
    Path(".github/scripts/echospace_production_emulator.py")
]
for path in targets:
    assert path.exists(),path
    original=path.read_text()
    modified=original.replace("com.nexa.echospace","com.night.echospace")
    if original!=modified:
        path.write_text(modified)
        print("MIGRATED_PACKAGE",path)
gradle=Path("EchoSpace/app/build.gradle.kts").read_text()
assert 'namespace = "com.night.echospace"' in gradle
assert 'applicationId = "com.night.echospace"' in gradle
assert not list(Path("EchoSpace/app/src/main/java/com/nexa").rglob("*.kt")) if Path("EchoSpace/app/src/main/java/com/nexa").exists() else True
assert not list(Path("EchoSpace/app/src/test/java/com/nexa").rglob("*.kt")) if Path("EchoSpace/app/src/test/java/com/nexa").exists() else True
PY
git add -A EchoSpace/app/src/main/java EchoSpace/app/src/test/java \
  EchoSpace/app/build.gradle.kts \
  .github/workflows/echospace-production-ci.yml \
  .github/scripts/echospace_production_emulator.py
git diff --cached --stat
