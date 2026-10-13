package com.tomex777.annie

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaTable
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.MathLib
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib

/** No io, os, package, luajava, binary loading or user-accessible debug library. */
internal object LuaGuest {
    private class Limit : Error("Lua execution exceeded its instruction/time limit")

    fun run(source: String, filename: String, function: String, inputJson: String): String {
        require(source.length <= 256_000 && inputJson.length <= 64_000) { "Lua input exceeds the runtime limit" }
        require(function.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}"))) { "Invalid Lua function name" }
        val globals = Globals()
        globals.load(BaseLib())
        globals.load(PackageLib())
        globals.load(TableLib())
        globals.load(StringLib())
        globals.load(MathLib())
        var remaining = 1_000_000
        val deadline = System.nanoTime() + 5_000_000_000L
        globals.load(object : DebugLib() {
            override fun onInstruction(pc: Int, v: org.luaj.vm2.Varargs?, top: Int) {
                if (--remaining <= 0 || System.nanoTime() >= deadline) throw Limit()
                super.onInstruction(pc, v, top)
            }
        })
        listOf("package", "require", "debug", "dofile", "loadfile", "load", "loadstring", "collectgarbage", "print").forEach {
            globals.set(it, LuaValue.NIL)
        }
        LuaC.install(globals)
        globals.load(source, filename).call()
        val handler = globals.get(function)
        require(handler.isfunction()) { "Missing Lua function: $function" }
        val value = handler.call(toLua(JSONTokener(inputJson).nextValue()))
        val result = fromLua(value, 0, mutableSetOf())
        val encoded = JSONArray().put(result).toString().let { it.substring(1, it.length - 1) }
        require(encoded.length <= 64_000) { "Lua result exceeds the runtime limit" }
        return encoded
    }

    private fun toLua(value: Any?): LuaValue = when (value) {
        null, JSONObject.NULL -> LuaValue.NIL
        is String -> LuaValue.valueOf(value)
        is Boolean -> LuaValue.valueOf(value)
        is Number -> LuaValue.valueOf(value.toDouble())
        is JSONObject -> LuaTable().apply { value.keys().forEach { set(it, toLua(value.get(it))) } }
        is JSONArray -> LuaTable().apply { for (i in 0 until value.length()) set(i + 1, toLua(value.get(i))) }
        else -> error("Unsupported JSON input")
    }

    private fun fromLua(value: LuaValue, depth: Int, seen: MutableSet<LuaValue>): Any = when {
        depth > 32 -> error("Lua result is nested too deeply")
        value.isnil() -> JSONObject.NULL
        value.isboolean() -> value.toboolean()
        value.type() == LuaValue.TNUMBER -> value.todouble().also { require(it.isFinite()) }
        value.type() == LuaValue.TSTRING -> value.tojstring()
        value.istable() -> {
            require(seen.add(value)) { "Lua result contains a cycle" }
            try {
                val keys = value.checktable().keys()
                require(keys.size <= 10_000) { "Lua result has too many entries" }
                val array = keys.isNotEmpty() && keys.all { it.isint() && it.toint() in 1..keys.size }
                if (array) JSONArray().apply { for (i in 1..keys.size) put(fromLua(value.get(i), depth + 1, seen)) }
                else JSONObject().apply { keys.forEach { key ->
                    require(key.type() == LuaValue.TSTRING) { "Lua object keys must be strings" }
                    put(key.tojstring(), fromLua(value.get(key), depth + 1, seen))
                } }
            } finally { seen.remove(value) }
        }
        else -> error("Lua results must contain only JSON values")
    }
}
