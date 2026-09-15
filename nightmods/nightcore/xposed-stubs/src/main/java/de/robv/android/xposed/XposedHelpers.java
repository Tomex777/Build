package de.robv.android.xposed;

/** Compile-only subset of XposedHelpers. Supplied by LSPosed ET at runtime. */
public final class XposedHelpers {
    private XposedHelpers() {}

    public static XC_MethodHook.Unhook findAndHookMethod(
            Class<?> clazz,
            String methodName,
            Object... parameterTypesAndCallback) {
        return null;
    }
}
