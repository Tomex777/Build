#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "cobalt-upstream"
modules = root / "modules"

logger_fqcn = "com.github.auties00.cobalt.telemetry.log.Logger"
replacements = (
    ("import java.lang.System.Logger.Level;", f"import {logger_fqcn}.Level;"),
    ("import java.lang.System.Logger;", f"import {logger_fqcn};"),
    ("java.lang.System.Logger.Level", f"{logger_fqcn}.Level"),
    ("java.lang.System.Logger", logger_fqcn),
    ("System.Logger.Level", f"{logger_fqcn}.Level"),
    ("System.Logger", logger_fqcn),
)

changed_files = 0
changed_refs = 0
for path in modules.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    original = text
    for old, new in replacements:
        count = text.count(old)
        if count:
            changed_refs += count
            text = text.replace(old, new)
    if text != original:
        path.write_text(text, encoding="utf-8")
        changed_files += 1

log_path = modules / "telemetry-core/src/main/java/com/github/auties00/cobalt/telemetry/log/Log.java"
text = log_path.read_text(encoding="utf-8")
old = "return System.getLogger(owner.getName());"
new = "return Logger.getLogger(owner.getName());"
if old not in text:
    raise SystemExit("Expected System.getLogger call was not found in Log.java")
log_path.write_text(text.replace(old, new), encoding="utf-8")

logger_path = modules / "telemetry-core/src/main/java/com/github/auties00/cobalt/telemetry/log/Logger.java"
logger_path.write_text(r'''package com.github.auties00.cobalt.telemetry.log;

import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Supplier;

/**
 * Android-compatible replacement for the JDK system logger.
 *
 * Cobalt uses only a small logging facade; Android does not expose
 * The Android runtime lacks the JDK logger level type, so this adapter preserves Cobalt's source-level
 * Logger/Level shape while delegating to java.util.logging.
 */
public final class Logger {
    public enum Level {
        ALL(Integer.MIN_VALUE),
        TRACE(400),
        DEBUG(500),
        INFO(800),
        WARNING(900),
        ERROR(1000),
        OFF(Integer.MAX_VALUE);

        private final int severity;

        Level(int severity) {
            this.severity = severity;
        }

        public int getSeverity() {
            return severity;
        }
    }

    private final java.util.logging.Logger delegate;

    private Logger(String name) {
        this.delegate = java.util.logging.Logger.getLogger(Objects.requireNonNull(name));
    }

    public static Logger getLogger(String name) {
        return new Logger(name);
    }

    public String getName() {
        return delegate.getName();
    }

    public boolean isLoggable(Level level) {
        return delegate.isLoggable(toJul(level));
    }

    public void log(Level level, String message) {
        delegate.log(toJul(level), message);
    }

    public void log(Level level, Supplier<String> messageSupplier) {
        delegate.log(toJul(level), messageSupplier);
    }

    public void log(Level level, Object object) {
        delegate.log(toJul(level), String.valueOf(object));
    }

    public void log(Level level, String message, Throwable thrown) {
        delegate.log(toJul(level), message, thrown);
    }

    public void log(Level level, Supplier<String> messageSupplier, Throwable thrown) {
        delegate.log(toJul(level), thrown, messageSupplier);
    }

    public void log(Level level, String format, Object... parameters) {
        delegate.log(toJul(level), format, parameters);
    }

    public void log(Level level, ResourceBundle bundle, String message, Throwable thrown) {
        delegate.logrb(toJul(level), delegate.getName(), null, bundle, message, thrown);
    }

    public void log(Level level, ResourceBundle bundle, String format, Object... parameters) {
        delegate.logrb(toJul(level), delegate.getName(), null, bundle, format, parameters);
    }

    private static java.util.logging.Level toJul(Level level) {
        Objects.requireNonNull(level);
        return switch (level) {
            case ALL -> java.util.logging.Level.ALL;
            case TRACE -> java.util.logging.Level.FINER;
            case DEBUG -> java.util.logging.Level.FINE;
            case INFO -> java.util.logging.Level.INFO;
            case WARNING -> java.util.logging.Level.WARNING;
            case ERROR -> java.util.logging.Level.SEVERE;
            case OFF -> java.util.logging.Level.OFF;
        };
    }
}
''', encoding="utf-8")

print(f"Patched {changed_refs} System.Logger references across {changed_files} Java files")
print(f"Added Android logger shim: {logger_path.relative_to(root)}")
