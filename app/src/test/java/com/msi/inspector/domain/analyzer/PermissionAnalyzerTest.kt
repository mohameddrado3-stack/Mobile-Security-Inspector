package com.msi.inspector.domain.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionAnalyzerTest {
    @Test
    fun mapsKnownAndroidPermissionsToTheirExactCategories() {
        assertEquals("الكاميرا", PermissionAnalyzer.categoryFor("android.permission.CAMERA"))
        assertEquals("الموقع", PermissionAnalyzer.categoryFor("android.permission.ACCESS_FINE_LOCATION"))
        assertEquals("الأجهزة القريبة والبلوتوث", PermissionAnalyzer.categoryFor("android.permission.BLUETOOTH_SCAN"))
        assertEquals("الصور والفيديو والصوت", PermissionAnalyzer.categoryFor("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))
    }

    @Test
    fun doesNotClassifyUnknownPermissionsBySubstring() {
        assertNull(PermissionAnalyzer.categoryFor("com.example.permission.CAMERA_HELPER"))
        assertNull(PermissionAnalyzer.categoryFor("android.permission.NOT_A_CAMERA_PERMISSION"))
        assertNull(PermissionAnalyzer.categoryFor("vendor.permission.ACCESS_FINE_LOCATION_PROXY"))
    }
}
