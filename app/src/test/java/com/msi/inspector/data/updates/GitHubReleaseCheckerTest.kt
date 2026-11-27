package com.msi.inspector.data.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseCheckerTest {
    @Test
    fun stableReleaseSupersedesPrereleaseWithSameCoreVersion() {
        assertTrue(GitHubReleaseChecker.compareVersions("0.9.0", "0.9.0-rc.1") > 0)
    }

    @Test
    fun prereleaseIdentifiersUseNumericOrdering() {
        assertTrue(GitHubReleaseChecker.compareVersions("0.9.0-rc.10", "0.9.0-rc.2") > 0)
    }

    @Test
    fun stableVersionsCompareEachNumericComponent() {
        assertTrue(GitHubReleaseChecker.compareVersions("0.10.0", "0.9.99") > 0)
        assertTrue(GitHubReleaseChecker.compareVersions("0.8.1", "0.8.2") < 0)
    }

    @Test
    fun buildMetadataDoesNotChangeVersionPrecedence() {
        assertEquals(
            0,
            GitHubReleaseChecker.compareVersions("1.2.3+build.5", "1.2.3+build.9")
        )
    }

    @Test
    fun longerNumericCoreIdentifiersDoNotOverflow() {
        assertTrue(
            GitHubReleaseChecker.compareVersions(
                "999999999999999999999.0.0",
                "999999999999999999998.99.99"
            ) > 0
        )
    }
}
