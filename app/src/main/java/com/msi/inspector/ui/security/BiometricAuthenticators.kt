package com.msi.inspector.ui.security

import android.os.Build
import androidx.biometric.BiometricManager

fun allowedAuthenticators(): Int =
    if (Build.VERSION.SDK_INT >= 30) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    }
