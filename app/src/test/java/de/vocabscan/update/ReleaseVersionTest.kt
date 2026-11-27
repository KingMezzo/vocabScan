package de.vocabscan.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun comparesNumericVersionsAndOptionalPrefix() {
        assertTrue(ReleaseVersion.compare("v1.10.0", "1.9.9")!! > 0)
        assertTrue(ReleaseVersion.compare("2.0.0", "10.0.0")!! < 0)
        assertEquals(0, ReleaseVersion.compare("v1.0.0", "1.0"))
        assertEquals(0, ReleaseVersion.compare("1.0.0+build.2", "1.0.0+build.1"))
        assertTrue(ReleaseVersion.compare("1.0.0", "1.0.0-rc.2")!! > 0)
        assertTrue(ReleaseVersion.compare("1.0.0-rc.10", "1.0.0-rc.2")!! > 0)
        assertNull(ReleaseVersion.compare("latest", "1.0.0"))
        assertNull(ReleaseVersion.compare("1.0", ""))
    }
}
