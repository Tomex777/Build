#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/anr_windows.sh"

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

cat > "$tmpdir/stale.txt" <<'EOF'
  Window #2 Window{111 u0 Application Not Responding: system}:
    WindowStateAnimator{333 Application Not Responding: system}:
      Surface: shown=false mDrawState=DRAW_PENDING
    isOnScreen=true
    isVisible=true
  Window #3 Window{222 u0 app.yomi.reader.dev/app.yomi.reader.ReaderActivity}:
      Surface: shown=true
    isVisible=true
EOF
if visible_anr "$tmpdir/stale.txt"; then
    echo "FAIL: stale invisible ANR window was mistaken for a blocking dialog" >&2
    exit 1
fi

cat > "$tmpdir/real.txt" <<'EOF'
  Window #2 Window{111 u0 Application Not Responding: com.android.systemui}:
    WindowStateAnimator{444 Application Not Responding: com.android.systemui}:
      Surface: shown=true mDrawState=HAS_DRAWN
    isOnScreen=true
    isVisible=true
  Window #3 Window{222 u0 app.yomi.reader.dev/app.yomi.reader.ReaderActivity}:
      Surface: shown=true
    isVisible=true
EOF
if ! visible_anr "$tmpdir/real.txt"; then
    echo "FAIL: a visible drawn system ANR dialog was ignored" >&2
    exit 1
fi

cat > "$tmpdir/hidden.txt" <<'EOF'
  Window #2 Window{111 u0 Application Not Responding: com.android.launcher3}:
      Surface: shown=true
    isOnScreen=false
    isVisible=false
  Window #3 Window{222 u0 app.yomi.reader.dev/app.yomi.reader.ReaderActivity}:
      Surface: shown=true
    isVisible=true
EOF
if visible_anr "$tmpdir/hidden.txt"; then
    echo "FAIL: hidden dialog was classified as visible" >&2
    exit 1
fi

cat > "$tmpdir/multidialog.txt" <<'EOF'
  Window #2 Window{111 u0 Application Not Responding: system}:
      Surface: shown=false
    isVisible=true
  Window #3 Window{222 u0 Application Not Responding: com.android.launcher3}:
      Surface: shown=true
    isVisible=true
  Window #4 Window{333 u0 app.yomi.reader.dev/app.yomi.reader.ReaderActivity}:
    isVisible=true
EOF
if ! visible_anr "$tmpdir/multidialog.txt"; then
    echo "FAIL: the second of multiple ANR windows was missed" >&2
    exit 1
fi
echo "PASS: Android visible ANR classifier (stale, drawn, hidden, multi-window)"
