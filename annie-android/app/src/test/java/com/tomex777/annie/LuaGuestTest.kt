package com.tomex777.annie

import org.junit.Assert.*
import org.junit.Test

class LuaGuestTest {
    @Test fun realLuaFunctionsLoopsAndTables() {
        assertEquals("42", LuaGuest.run("function main(ctx) local n=0; for i=1,6 do n=n+i end; return n*2 end", "main.lua", "main", "{}"))
    }
    @Test fun hostLibrariesAreAbsent() {
        assertEquals("[true,true,true,true,true,true]", LuaGuest.run("function main(ctx) return {os==nil,io==nil,luajava==nil,package==nil,debug==nil,loadfile==nil} end", "main.lua", "main", "{}"))
    }
    @Test fun infiniteLoopCannotRunForever() {
        try { LuaGuest.run("function main(ctx) while true do end end", "loop.lua", "main", "{}"); fail("Loop completed") }
        catch (failure: Error) { assertTrue(failure.message.orEmpty().contains("limit")) }
    }
    @Test fun cyclicResultsAreRejected() {
        try { LuaGuest.run("function main(ctx) local t={}; t.self=t; return t end", "cycle.lua", "main", "{}"); fail("Cycle accepted") }
        catch (failure: IllegalArgumentException) { assertTrue(failure.message.orEmpty().contains("cycle")) }
    }
}
