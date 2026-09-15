package de.robv.android.xposed;

/** Compile-only subset of the legacy Xposed callback API. Supplied by LSPosed ET at runtime. */
public abstract class XC_MethodHook {
    public XC_MethodHook() {}

    protected void beforeHookedMethod(MethodHookParam<?> param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam<?> param) throws Throwable {}

    public static final class MethodHookParam<T> {
        public Object thisObject;
        public Object[] args;
    }

    public class Unhook {}
}
