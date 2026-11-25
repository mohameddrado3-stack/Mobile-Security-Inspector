package com.msi.inspector.data.preferences

import android.content.Context
import com.msi.inspector.design.theme.MsiThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MsiPreferencesTest {
    @Test
    fun defaultsAreConservativeAndScheduleIsStable() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("msi_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val preferences = MsiPreferences(context)

        assertEquals(MsiThemeMode.DARK to false, preferences.loadTheme())
        assertEquals(9 to 0, preferences.loadScanSchedule())
        assertFalse(preferences.isPeriodicScanEnabled())
        assertFalse(preferences.isOnboardingComplete())
    }

    @Test
    fun themeAndOptionalSchedulePersistAcrossInstances() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("msi_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val preferences = MsiPreferences(context)
        preferences.saveTheme(MsiThemeMode.LIGHT, true)
        preferences.saveScanSchedule(14, 45)
        preferences.setPeriodicScanEnabled(true)
        preferences.setOnboardingComplete()

        val restored = MsiPreferences(context)

        assertEquals(MsiThemeMode.LIGHT to true, restored.loadTheme())
        assertEquals(14 to 45, restored.loadScanSchedule())
        assertTrue(restored.isPeriodicScanEnabled())
        assertTrue(restored.isOnboardingComplete())
    }
}
