package com.msi.inspector.domain.analyzer

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCategoryCoherenceAnalyzerTest {
    @Test
    fun ignoresUndefinedCategoriesAndEmptyPermissionEvidence() {
        assertNull(
            AppCategoryCoherenceAnalyzer.note(
                ApplicationInfo.CATEGORY_UNDEFINED,
                null,
                listOf("الكاميرا")
            )
        )
        assertNull(
            AppCategoryCoherenceAnalyzer.note(
                ApplicationInfo.CATEGORY_GAME,
                "ألعاب",
                emptyList()
            )
        )
    }

    @Test
    fun flagsOnlyPermissionsOutsideTheLocalCategoryReference() {
        val note = AppCategoryCoherenceAnalyzer.note(
            ApplicationInfo.CATEGORY_GAME,
            "ألعاب",
            listOf("الكاميرا", "التقويم")
        )

        assertNotNull(note)
        assertTrue(note!!.contains("التقويم"))
    }
}
