package com.example.whatsapp.data.scripts;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * Narrow adapter for functions Night intentionally exposes to sandboxed JavaScript.
 */
public final class NightJsFunction extends BaseFunction {
    @FunctionalInterface
    public interface Handler {
        Object invoke(Context cx, Scriptable scope, Scriptable thisObj, Object[] args);
    }

    private final Handler handler;

    public NightJsFunction(Handler handler) {
        this.handler = handler;
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        return handler.invoke(cx, scope, thisObj, args);
    }
}
