package com.codeforge.libs.terminal_engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkScriptProtocolTest {
    @Test fun parsesItems() {
        assertEquals(SdkScriptLine.Item("jdk;17", "17", true, "/p"), SdkScriptProtocol.parse("CFSDK|ITEM|jdk;17|17|1|/p"))
        assertEquals(SdkScriptLine.Item("jdk;21", "21", false, null), SdkScriptProtocol.parse("CFSDK|ITEM|jdk;21|21|0|"))
    }

    @Test fun parsesProgressErrorOk() {
        assertEquals(SdkScriptLine.Progress(100, "a b"), SdkScriptProtocol.parse("CFSDK|PROGRESS|150|a b"))
        assertEquals(SdkScriptLine.Ok("x"), SdkScriptProtocol.parse("CFSDK|OK|x"))
        assertEquals(SdkScriptLine.Error("bad"), SdkScriptProtocol.parse("CFSDK|ERR|bad"))
    }

    @Test fun ignoresForeignAndBrokenLines() {
        assertNull(SdkScriptProtocol.parse("hello"))
        assertNull(SdkScriptProtocol.parse("CFSDK|PROGRESS|x|y"))
    }

    @Test fun idsAreShellSafe() {
        assertTrue(SdkScriptProtocol.isSafeId("ndk;27d"))
        assertFalse(SdkScriptProtocol.isSafeId("a;rm -rf /"))
        assertFalse(SdkScriptProtocol.isSafeId("x'y"))
    }

    @Test fun parsesPackageLines() {
        assertEquals(
            SdkScriptLine.Pkg("git", "2.1", "2.2", 812, "U", "Distributed | VCS"),
            SdkScriptProtocol.parse("CFSDK|PKG|git|2.1|2.2|812|U|Distributed | VCS"),
        )
        assertEquals(
            SdkScriptLine.Pkg("jq", null, "1.7", 0, "", ""),
            SdkScriptProtocol.parse("CFSDK|PKG|jq||1.7|0||"),
        )
    }

    @Test fun rejectsUnsafePackageNames() {
        assertNull(SdkScriptProtocol.parse("CFSDK|PKG|a;rm|1|1|1||x"))
        assertTrue(SdkScriptProtocol.isSafePackageName("libc++_x".replace("_", "-")))
        assertFalse(SdkScriptProtocol.isSafePackageName("-bad"))
        assertFalse(SdkScriptProtocol.isSafePackageName("a b"))
    }
}
