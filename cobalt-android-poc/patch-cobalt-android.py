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

# Do not instantiate the desktop Warden passkey backend during linked-client
# class initialization. QR/pairing-code linking itself does not require a
# passkey; defer Warden creation until an actual integrity/passkey challenge.
passkey_path = modules / "lib/src/main/java/com/github/auties00/cobalt/client/linked/LinkedWhatsAppClientPasskeyAuthenticator.java"
passkey_text = passkey_path.read_text(encoding="utf-8")
old_passkey = "return SystemPasskeyAuthenticator.create(onQrCode);"
new_passkey = "return request -> SystemPasskeyAuthenticator.create(onQrCode).assertCredential(request);"
if old_passkey not in passkey_text:
    raise SystemExit("Expected eager SystemPasskeyAuthenticator creation was not found")
passkey_path.write_text(passkey_text.replace(old_passkey, new_passkey), encoding="utf-8")

# Android has no java.lang.foreign API. Cobalt's MemorySegment overloads in
# DataUtils are unused by production code (only a benchmark exercises them),
# while the byte[] and ByteBuffer implementations are the real wire paths.
# Remove only the foreign-memory specialization so DataUtils can initialize
# on Android without changing any wire-format behavior.
data_utils_path = modules / "wire/wire-core/src/main/java/com/github/auties00/cobalt/wire/core/util/DataUtils.java"
data_utils = data_utils_path.read_text(encoding="utf-8")
data_utils = data_utils.replace("import java.lang.foreign.MemorySegment;\n", "")
data_utils = data_utils.replace("import java.lang.foreign.ValueLayout;\n", "")

segment_fields_start = data_utils.find(
    "    /**\n"
    "     * Reads and writes {@code short} values from a {@link MemorySegment}"
)
segment_fields_end = data_utils.find("    static {\n", segment_fields_start)
if segment_fields_start < 0 or segment_fields_end < 0:
    raise SystemExit("Could not locate DataUtils MemorySegment field block")
data_utils = data_utils[:segment_fields_start] + data_utils[segment_fields_end:]

segment_methods_start = data_utils.find(
    "    /**\n"
    "     * Reads a {@code short} from {@code segment}"
)
segment_methods_end = data_utils.find(
    "    /**\n"
    "     * Returns a random integer in {@code [0, bound)}.",
    segment_methods_start
)
if segment_methods_start < 0 or segment_methods_end < 0:
    raise SystemExit("Could not locate DataUtils MemorySegment method block")
data_utils = data_utils[:segment_methods_start] + data_utils[segment_methods_end:]

if "java.lang.foreign" in data_utils or "MemorySegment" in data_utils or "ValueLayout" in data_utils:
    raise SystemExit("Foreign-memory references remain in patched DataUtils")
data_utils_path.write_text(data_utils, encoding="utf-8")


# Android's java.nio.file.Files surface does not provide the Java 11
# readString/writeString helpers across our minSdk range. Replace the few
# production uses with Java-7-era byte APIs that Android API 26 supports.
file_api_patches = {
    modules / "lib/src/main/java/com/github/auties00/cobalt/store/linked/protobuf/persistent/PersistentLinkedWhatsAppStoreFactory.java": (
        ("Files.writeString(temp, sessionId);",
         "Files.write(temp, sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8));"),
        ("var sessionId = Files.readString(pointer).strip();",
         "var sessionId = new String(Files.readAllBytes(pointer), java.nio.charset.StandardCharsets.UTF_8).trim();"),
    ),
    modules / "lib/src/main/java/com/github/auties00/cobalt/store/cloud/protobuf/PersistentCloudWhatsAppStoreFactory.java": (
        ("Files.writeString(temp, phoneNumberId);",
         "Files.write(temp, phoneNumberId.getBytes(java.nio.charset.StandardCharsets.UTF_8));"),
        ("var phoneNumberId = Files.readString(pointer).strip();",
         "var phoneNumberId = new String(Files.readAllBytes(pointer), java.nio.charset.StandardCharsets.UTF_8).trim();"),
    ),
    modules / "lib/src/main/java/com/github/auties00/cobalt/client/linked/WhatsAppLinkedClientErrorHandler.java": (
        ("Files.writeString(path, stackTraceWriter.toString(), StandardOpenOption.CREATE);",
         "Files.write(path, stackTraceWriter.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), StandardOpenOption.CREATE);"),
    ),
    modules / "lib/src/main/java/com/github/auties00/cobalt/client/linked/info/WhatsAppAndroidClientInfo.java": (
        ("Files.writeString(path, json.toJSONString());",
         "Files.write(path, json.toJSONString().getBytes(java.nio.charset.StandardCharsets.UTF_8));"),
    ),
}

for path, patches in file_api_patches.items():
    text = path.read_text(encoding="utf-8")
    for old, new in patches:
        if old not in text:
            raise SystemExit(f"Expected Android Files API target not found in {path.relative_to(root)}: {old}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")

# Guard against accidentally leaving these unsupported Java 11 convenience
# calls in production Cobalt code.
main_java_root = modules / "lib/src/main/java"
for path in main_java_root.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    if "Files.writeString(" in text or "Files.readString(" in text:
        raise SystemExit(f"Unsupported Files.readString/writeString remains in {path.relative_to(root)}")


# Android does not implement Project Loom virtual-thread APIs. Preserve
# Cobalt's call-site shape by redirecting those factories to a tiny
# compatibility layer backed by ordinary named platform threads and a cached
# executor. This changes scheduling, not protocol behaviour.
lib_main = modules / "lib/src/main/java"
virtual_replacements = (
    ("Thread.ofVirtual()", "com.github.auties00.cobalt.util.AndroidThreads.ofPlatform()"),
    ("Thread.startVirtualThread(", "com.github.auties00.cobalt.util.AndroidThreads.startVirtualThread("),
    ("Executors.newVirtualThreadPerTaskExecutor()", "com.github.auties00.cobalt.util.AndroidThreads.newPerTaskExecutor()"),
)

virtual_changed_files = 0
virtual_changed_refs = 0
for path in lib_main.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    original = text
    for old, new in virtual_replacements:
        count = text.count(old)
        if count:
            virtual_changed_refs += count
            text = text.replace(old, new)
    if text != original:
        path.write_text(text, encoding="utf-8")
        virtual_changed_files += 1

android_threads_path = modules / "lib/src/main/java/com/github/auties00/cobalt/util/AndroidThreads.java"
android_threads_path.write_text(r'''package com.github.auties00.cobalt.util;

import java.util.List;
import java.util.Collection;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Android compatibility layer for Cobalt's Loom call sites.
 *
 * Android does not expose JVM virtual threads, so the Android build maps them
 * to ordinary platform threads. The public surface intentionally mirrors only
 * the tiny subset Cobalt uses: named start/unstarted threads, startVirtualThread,
 * and a per-task executor.
 */
public final class AndroidThreads {
    private AndroidThreads() {
        throw new AssertionError("No instances");
    }

    public static Builder ofPlatform() {
        return new Builder();
    }

    public static Thread startVirtualThread(Runnable task) {
        return ofPlatform().start(task);
    }

    public static PerTaskExecutor newPerTaskExecutor() {
        return new PerTaskExecutor();
    }

    public static final class Builder {
        private String name;

        public Builder name(String value) {
            this.name = value;
            return this;
        }

        public Thread unstarted(Runnable task) {
            var thread = new Thread(task);
            if (name != null) {
                thread.setName(name);
            }
            return thread;
        }

        public Thread start(Runnable task) {
            var thread = unstarted(task);
            thread.start();
            return thread;
        }
    }

    /**
     * Executor with an explicit close() method so Java-25 try-with-resources
     * call sites don't dispatch to ExecutorService.close(), which is absent
     * from Android's older java.util.concurrent surface.
     */
    public static final class PerTaskExecutor extends AbstractExecutorService implements AutoCloseable {
        private final ExecutorService delegate;

        private PerTaskExecutor() {
            var sequence = new java.util.concurrent.atomic.AtomicInteger();
            ThreadFactory factory = runnable -> {
                var thread = new Thread(runnable);
                thread.setName("cobalt-worker-" + sequence.incrementAndGet());
                return thread;
            };
            this.delegate = Executors.newCachedThreadPool(factory);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(command);
        }

        @Override
        public void close() {
            delegate.shutdown();
        }
    }
}
''', encoding="utf-8")

for path in lib_main.rglob("*.java"):
    if path == android_threads_path:
        continue
    text = path.read_text(encoding="utf-8")
    if "Thread.ofVirtual()" in text or "Thread.startVirtualThread(" in text or "newVirtualThreadPerTaskExecutor()" in text:
        raise SystemExit(f"Virtual-thread API remains in {path.relative_to(root)}")

print(f"Patched {virtual_changed_refs} virtual-thread references across {virtual_changed_files} Java files")


# Java 21 SequencedCollection added List.getFirst/getLast. Android's Java
# collection surface does not expose those APIs across minSdk 26, so backport
# the production List usages used by the linked-message/login paths.
sequenced_changed = 0
for path in modules.rglob("*.java"):
    if "src/main/java" not in path.as_posix():
        continue
    text = path.read_text(encoding="utf-8")
    original = text
    # Cobalt's production getFirst() call sites are list-like indexed
    # collections. Maven compilation below guards this assumption.
    text = text.replace(".getFirst()", ".get(0)")
    if text != original:
        sequenced_changed += 1
        path.write_text(text, encoding="utf-8")

get_last_patches = {
    modules / "lib/src/main/java/com/github/auties00/cobalt/client/linked/LiveLinkedWhatsAppClient.java": (
        ("store.signalStore().preKeys().getLast().id()",
         "store.signalStore().preKeys().get(store.signalStore().preKeys().size() - 1).id()"),
    ),
    modules / "lib/src/main/java/com/github/auties00/cobalt/export/LiveChatExporterService.java": (
        ("names.getLast()", "names.get(names.size() - 1)"),
    ),
    modules / "lib/src/main/java/com/github/auties00/cobalt/sync/handler/UnarchiveChatsSettingHandler.java": (
        ("mutations.getLast()", "mutations.get(mutations.size() - 1)"),
    ),
}
for path, patches in get_last_patches.items():
    text = path.read_text(encoding="utf-8")
    for old, new in patches:
        if old in text:
            text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")

# Final normalization pass. Some generated/rewritten sources can be touched by
# earlier compatibility transforms in this same script, so normalize getFirst
# once more immediately before validation.
for path in modules.rglob("*.java"):
    if "src/main/java" not in path.as_posix():
        continue
    text = path.read_text(encoding="utf-8")
    if ".getFirst()" in text:
        text = text.replace(".getFirst()", ".get(0)")
        path.write_text(text, encoding="utf-8")

# Stanza.children() is a SequencedCollection, not necessarily a List, so the
# generic getFirst -> get(0) rewrite is invalid here. Iterator order preserves
# SequencedCollection's first-element semantics on Android-compatible APIs.
stanza_path = modules / "stanza-core/src/main/java/com/github/auties00/cobalt/stanza/model/Stanza.java"
stanza_text = stanza_path.read_text(encoding="utf-8")
stanza_text = stanza_text.replace("Optional.ofNullable(children.get(0))", "Optional.ofNullable(children.iterator().next())")
stanza_text = stanza_text.replace("Stream.of(children.get(0))", "Stream.of(children.iterator().next())")
stanza_path.write_text(stanza_text, encoding="utf-8")

for path in modules.rglob("*.java"):
    if "src/main/java" not in path.as_posix():
        continue
    text = path.read_text(encoding="utf-8")
    if ".getFirst()" in text or ".getLast()" in text:
        leftovers = [line.strip() for line in text.splitlines() if ".getFirst()" in line or ".getLast()" in line]
        raise SystemExit(f"Java 21 list accessor remains in {path.relative_to(root)}: {leftovers[:8]}")

print(f"Backported Java 21 list accessors in {sequenced_changed} source files")

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
