package dev.nightmods.core.hook.adapters;

/** Explicit compatibility gate for version-sensitive target hooks. */
public enum TargetCompatibility {
    SUPPORTED,
    ANALYSIS_REQUIRED,
    UNSUPPORTED
}
