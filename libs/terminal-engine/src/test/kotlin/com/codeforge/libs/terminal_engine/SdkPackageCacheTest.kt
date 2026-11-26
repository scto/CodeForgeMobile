package com.codeforge.libs.terminal_engine

import com.codeforge.core.domain.model.ToolItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SdkPackageCacheTest {
    @Test
    fun roundTrip() {
        val items = listOf(
            ToolItem("platforms;android-36", "36", true, "/sdk/platforms/android-36"),
            ToolItem("ndk;r28c", "28.2", false, null)
        )
        assertEquals(items, SdkPackageCache.decode(SdkPackageCache.encode(items)))
        assertEquals(emptyList<ToolItem>(), SdkPackageCache.decode("kaputt\n|x|1|"))
    }
}
