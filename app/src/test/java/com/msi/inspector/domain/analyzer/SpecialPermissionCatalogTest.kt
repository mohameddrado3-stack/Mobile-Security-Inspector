package com.msi.inspector.domain.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialPermissionCatalogTest {
    @Test
    fun knownPermissionIsDescribedAsARequestNotAnActiveGrant() {
        val label = SpecialPermissionCatalog.requestLabel("android.permission.SYSTEM_ALERT_WINDOW")

        assertTrue(label.orEmpty().contains("طلب صلاحية الظهور فوق التطبيقات"))
        assertTrue(label.orEmpty().contains("حالة التفعيل غير مؤكدة"))
    }

    @Test
    fun unknownPermissionHasNoCatalogLabel() {
        assertNull(SpecialPermissionCatalog.requestLabel("android.permission.UNKNOWN"))
    }

    @Test
    fun catalogDoesNotReturnAnEmptyLabel() {
        assertEquals(null, SpecialPermissionCatalog.requestLabel(""))
    }
}
