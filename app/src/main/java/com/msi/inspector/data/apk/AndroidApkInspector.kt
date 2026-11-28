package com.msi.inspector.data.apk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.msi.inspector.domain.model.ApkInspectionResult
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidApkInspector(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    suspend fun inspect(uri: Uri): ApkInspectionResult = withContext(Dispatchers.IO) {
        val fileName = displayName(uri)
        if (!fileName.endsWith(".apk", ignoreCase = true)) {
            throw IllegalArgumentException("اختار ملفًا بامتداد APK.")
        }

        val directory = File(appContext.cacheDir, "apk-scan").apply { mkdirs() }
        directory.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - CACHE_RETENTION_MS }
            ?.forEach { it.delete() }
        val cachedFile = File.createTempFile("msi-inspect-", ".apk", directory)

        try {
            copyBounded(uri, cachedFile)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES
            }
            val packageInfo = packageManager.getPackageArchiveInfo(cachedFile.absolutePath, flags)
                ?: throw IllegalArgumentException("Android لم يتمكن من قراءة بيانات حزمة APK.")

            val applicationInfo = packageInfo.applicationInfo
                ?: throw IllegalArgumentException("ملف APK لا يحتوي على بيانات تطبيق قابلة للقراءة.")
            applicationInfo.sourceDir = cachedFile.absolutePath
            applicationInfo.publicSourceDir = cachedFile.absolutePath

            val requested = packageInfo.requestedPermissions.orEmpty().distinct()
            val dangerous = mutableListOf<String>()
            val unclassified = mutableListOf<String>()
            requested.forEach { permission ->
                val protection = runCatching {
                    packageManager.getPermissionInfo(permission, 0).protectionLevel and
                        PermissionInfo.PROTECTION_MASK_BASE
                }.getOrNull()
                when (protection) {
                    PermissionInfo.PROTECTION_DANGEROUS -> dangerous += permission
                    null -> unclassified += permission
                }
            }

            val targetSdk = applicationInfo.targetSdkVersion
            val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val cleartextTraffic = targetSdk < Build.VERSION_CODES.P ||
                applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0

            val signingFingerprint = signingCertificate(packageInfo)
            val warnings = buildList {
                if (debuggable) add("علامة debuggable مفعّلة في ملف التطبيق.")
                if (dangerous.isNotEmpty()) add("يطلب الملف ${dangerous.size} صلاحية مصنفة حساسة من Android.")
                if (unclassified.isNotEmpty()) add("تعذر تصنيف ${unclassified.size} صلاحية على هذا الجهاز.")
                if (cleartextTraffic) add("إعداد التطبيق يسمح باتصالات HTTP غير المشفرة وفق بيانات Manifest المتاحة.")
                if (signingFingerprint == null) add("تعذر استخراج بصمة شهادة التوقيع.")
                if (targetSdk < 28) add("يستهدف التطبيق مستوى Android قديمًا نسبيًا؛ راجع توافقه وتحديثاته.")
                if (isEmpty()) add("لم تظهر مؤشرات المراجعة المحددة في هذا الفحص.")
            }

            ApkInspectionResult(
                cachedFile = cachedFile,
                fileName = fileName,
                fileSizeBytes = cachedFile.length(),
                appName = runCatching { applicationInfo.loadLabel(packageManager).toString() }
                    .getOrDefault(packageInfo.packageName),
                packageName = packageInfo.packageName,
                versionName = packageInfo.versionName ?: "غير متاح",
                targetSdk = targetSdk,
                isDebuggable = debuggable,
                allowsCleartextTraffic = cleartextTraffic,
                requestedPermissions = requested,
                dangerousPermissions = dangerous,
                unclassifiedPermissions = unclassified,
                signerSha256 = signingFingerprint,
                warnings = warnings
            )
        } catch (error: Exception) {
            cachedFile.delete()
            throw error
        }
    }

    private fun displayName(uri: Uri): String {
        val name = appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
        return name?.takeIf(String::isNotBlank) ?: uri.lastPathSegment.orEmpty()
    }

    private fun copyBounded(uri: Uri, destination: File) {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("تعذر فتح ملف APK المحدد.")
        var total = 0L
        input.use { source ->
            FileOutputStream(destination).use { target ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_APK_SIZE_BYTES) {
                        throw IllegalArgumentException("حجم الملف أكبر من الحد المدعوم لفحص APK.")
                    }
                    target.write(buffer, 0, count)
                }
                target.flush()
            }
        }
        if (total == 0L) throw IllegalArgumentException("ملف APK فارغ.")
    }

    @Suppress("DEPRECATION")
    private fun signingCertificate(packageInfo: PackageInfo): String? {
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            packageInfo.signatures?.toList().orEmpty()
        }
        val certificate = certificates.firstOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray())
        return digest.joinToString("") { byte -> "%02X".format(byte) }
    }

    companion object {
        private const val MAX_APK_SIZE_BYTES = 512L * 1024L * 1024L
        private const val CACHE_RETENTION_MS = 48L * 60L * 60L * 1000L
    }
}
