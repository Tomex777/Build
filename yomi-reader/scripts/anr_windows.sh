#!/usr/bin/env bash
# Shared Android emulator screenshot gate: report only actual on-screen,
# drawn "Application Not Responding" windows rather than stale window names.
# Kept as a small shell function so the CI contract tests can exercise it.
visible_anr() {
    local window_dump=${1:?window dump path required}
    awk '
        function finish_window() {
            if (is_anr && is_visible && is_drawn) found = 1
        }
        /^  Window #[0-9]+ Window[{]/ {
            finish_window()
            is_anr = index($0, "Application Not Responding:") != 0
            is_visible = 0
            is_drawn = 0
        }
        is_anr && /isVisible=true/ { is_visible = 1 }
        is_anr && /Surface: shown=true/ { is_drawn = 1 }
        END { finish_window(); exit(found ? 0 : 1) }
    ' "$window_dump"
}
