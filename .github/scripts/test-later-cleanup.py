#!/usr/bin/env python3
"""Fast regression tests for the Later production-copy removal transform.

These tests run before reconstructing the Android project so we never ship
a release assembled from source accidentally truncated by a cleanup pass.
"""
import ast
from pathlib import Path

source = Path(".github/scripts/apply-production-ui-cleanup.py").read_text()
tree = ast.parse(source)
function = next(
    node
    for node in tree.body
    if isinstance(node, ast.FunctionDef) and node.name == "remove_all_text_calls"
)
scope = {}
exec(compile(ast.Module(body=[function], type_ignores=[]), "<cleanup-function>", "exec"), scope)
remove_all = scope["remove_all_text_calls"]
marker = "You started this, but haven't sent it yet."

kotlin = """package com.night.later

fun Example() {
    Text(
        text = "You started this, but haven't sent it yet."
    )
    Text("keep first", modifier = Modifier)
    Text(
        text = "You started this, but haven't sent it yet."
    )
    Text("keep second")
}
"""
clean, count = remove_all(kotlin, marker)
assert count == 2, f"expected two removals, got {count}"
assert clean.startswith("package com.night.later\n"), clean[:120]
assert "fun Example()" in clean
assert "keep first" in clean and "keep second" in clean
assert marker not in clean
assert clean.rstrip().endswith("}"), clean[-120:]
assert remove_all(clean, marker) == (clean, 0), "cleanup should be idempotent"

escaped_quote = """package sample

fun Demo() {
    Text(text = "Please \\"quote\\" (this)")
    Text(text = "You started this, but haven't sent it yet.")
    Text("leave intact")
}
"""
clean_escaped, count_escaped = remove_all(escaped_quote, marker)
assert count_escaped == 1
assert "Please" in clean_escaped and "leave intact" in clean_escaped
assert "package sample" in clean_escaped

print("LATER_UI_COPY_CLEANUP_REGRESSION_PASS")
