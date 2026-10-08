package com.codeforge.libs.dependency_updater_impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    private fun cmp(a: String, b: String) = Integer.signum(VersionComparator.compare(a, b))

    @Test fun numeric() {
        assertEquals(-1, cmp("1.9", "1.10"))
        assertEquals(0, cmp("1.0", "1.0.0"))
        assertEquals(1, cmp("4.0.3", "4.0.1"))
        assertEquals(-1, cmp("1.0", "1.0.1"))
    }

    @Test fun qualifiers() {
        assertEquals(-1, cmp("1.0-alpha01", "1.0-beta01"))
        assertEquals(-1, cmp("1.0-beta02", "1.0-rc01"))
        assertEquals(-1, cmp("1.0-rc01", "1.0"))
        assertEquals(1, cmp("1.0.1", "1.0-rc01"))
        assertEquals(-1, cmp("1.0-alpha03", "1.0-alpha10"))
        assertEquals(1, cmp("1.0-sp1", "1.0"))
        assertEquals(0, cmp("1.0-final", "1.0"))
    }

    @Test fun stability() {
        assertEquals(Stability.STABLE, VersionComparator.stabilityOf("32.1.2-jre"))
        assertEquals(Stability.ALPHA, VersionComparator.stabilityOf("1.7.0-alpha03"))
        assertEquals(Stability.RC, VersionComparator.stabilityOf("2.0.0-RC1"))
        assertEquals(Stability.UNSTABLE, VersionComparator.stabilityOf("1.0-SNAPSHOT"))
        assertEquals(Stability.BETA, VersionComparator.stabilityOf("2.1.0-Beta1"))
    }

    @Test fun latestPolicy() {
        val all = listOf("1.0.0", "1.1.0", "1.2.0-alpha01", "1.2.0-rc01", "1.1.1", "1.3.0-SNAPSHOT")
        assertEquals("1.1.1", UpdatePolicy.latest("1.0.0", all))
        assertEquals("1.2.0-rc01", UpdatePolicy.latest("1.2.0-alpha01", all))
        assertNull(UpdatePolicy.latest("1.1.1", listOf("1.1.1", "1.1.0")))
    }

    @Test fun upgradable() {
        assertTrue(UpdatePolicy.isUpgradable("1.2.3"))
        assertFalse(UpdatePolicy.isUpgradable("1.+"))
        assertFalse(UpdatePolicy.isUpgradable("[1.0,2.0)"))
        assertFalse(UpdatePolicy.isUpgradable("latest.release"))
        assertFalse(UpdatePolicy.isUpgradable("\${v}"))
    }
}
