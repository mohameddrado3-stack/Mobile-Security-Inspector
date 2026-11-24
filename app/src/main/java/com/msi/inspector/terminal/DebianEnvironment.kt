package com.msi.inspector.terminal

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.StatFs
import android.system.Os
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Installs the official Debian Trixie ARM64 OCI rootfs into app-private storage.
 * The OCI layer is pinned by its SHA-256 digest and verified before extraction.
 */
object DebianEnvironment {
    const val VERSION = "debian-trixie-13.8-arm64-2026-11-23"
    const val EXPECTED_ARCHIVE_BYTES = 49_770_086L
    const val ARCHIVE_SHA256 =
        "6bf9dd8e528e8b59aba65241842ec43567ab9ff163d6d444bb4701ece0f11093"

    private const val TAG = "MSI-Debian"
    private const val MAX_UNPACKED_BYTES = 512L * 1024L * 1024L
    private const val MAX_ARCHIVE_ENTRIES = 120_000
    private const val MARKER_NAME = ".msi-debian-install"
    private const val ARCHIVE_URL =
        "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts/" +
            "dist-arm64v8/trixie/oci/blobs/sha256/" + ARCHIVE_SHA256

    data class Progress(val percent: Int, val message: String)

    fun isSupportedDevice(): Boolean =
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    fun environmentDirectory(context: Context): File =
        File(File(context.filesDir, "ms-terminal"), "debian-trixie")

    fun rootfsDirectory(context: Context): File =
        File(environmentDirectory(context), "rootfs")

    fun isInstalled(context: Context): Boolean {
        if (!isSupportedDevice()) return false
        val rootfs = rootfsDirectory(context)
        val marker = File(rootfs, MARKER_NAME)
        if (!marker.isFile ||
            !File(rootfs, "usr/bin/bash").isFile ||
            !File(rootfs, "usr/bin/apt-get").isFile ||
            !File(rootfs, "var/lib/dpkg/status").isFile
        ) return false

        return try {
            marker.readText(Charsets.UTF_8).trim() ==
                "version=$VERSION\nsha256=$ARCHIVE_SHA256"
        } catch (_: Exception) {
            false
        }
    }

    /** Must be called from a worker thread. */
    @Throws(Exception::class)
    fun install(context: Context, onProgress: (Progress) -> Unit) {
        check(isSupportedDevice()) {
            "بيئة Debian الحالية تدعم ARM64 فقط في هذا الإصدار."
        }
        if (isInstalled(context)) {
            onProgress(Progress(100, "Debian مثبت بالفعل؛ لم يتم تنزيله مرة أخرى."))
            return
        }

        val parent = environmentDirectory(context)
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("تعذر إنشاء مجلد بيئة Debian.")
        }
        val stat = StatFs(parent.absolutePath)
        val requiredSpace = 360L * 1024L * 1024L
        if (stat.availableBytes < requiredSpace) {
            throw IOException("المساحة الحرة غير كافية. يلزم توفير نحو 360 ميجابايت على الأقل.")
        }

        val archive = File(context.cacheDir, "debian-trixie-arm64-rootfs.tar.gz.part")
        val staging = File(parent, "rootfs.staging")
        val backup = File(parent, "rootfs.previous")
        deleteRecursively(staging)
        if (backup.exists()) deleteRecursively(backup)

        try {
            onProgress(Progress(0, "تنزيل Debian Trixie الرسمي (حوالي 50 ميجابايت)…"))
            downloadAndVerify(archive) { downloaded ->
                val percent = ((downloaded * 40L) / EXPECTED_ARCHIVE_BYTES).toInt()
                    .coerceIn(0, 40)
                onProgress(Progress(
                    percent,
                    "تنزيل Debian: ${downloaded / (1024 * 1024)} / 47 ميجابايت"
                ))
            }

            onProgress(Progress(42, "تم التحقق من SHA-256. جارٍ تجهيز نظام الملفات…"))
            if (!staging.mkdirs()) throw IOException("تعذر إنشاء مجلد التثبيت المؤقت.")
            extractRootfs(archive, staging) { extracted, total ->
                val percent = if (total <= 0L) 75 else
                    (42 + ((extracted * 43L) / total)).toInt().coerceIn(42, 85)
                onProgress(Progress(percent, "فك ملفات Debian…"))
            }

            File(staging, MARKER_NAME).writeText(
                "version=$VERSION\nsha256=$ARCHIVE_SHA256",
                Charsets.UTF_8
            )

            val finalRootfs = rootfsDirectory(context)
            if (finalRootfs.exists() && !finalRootfs.renameTo(backup)) {
                throw IOException("تعذر الاحتفاظ بالبيئة السابقة قبل الاستبدال.")
            }
            if (!staging.renameTo(finalRootfs)) {
                if (backup.exists()) backup.renameTo(finalRootfs)
                throw IOException("تعذر تثبيت بيئة Debian بعد فك الملفات.")
            }
            if (backup.exists()) deleteRecursively(backup)
            check(isInstalled(context)) { "فشل فحص سلامة بيئة Debian بعد التثبيت." }
            onProgress(Progress(100, "تم تثبيت Debian Trixie بنجاح."))
        } catch (error: Exception) {
            deleteRecursively(staging)
            val finalRootfs = rootfsDirectory(context)
            if (!finalRootfs.exists() && backup.exists()) backup.renameTo(finalRootfs)
            throw error
        } finally {
            if (archive.exists()) archive.delete()
        }
    }

    /** Refresh DNS using the active Android network without exposing shared storage. */
    fun refreshResolver(context: Context) {
        val rootfs = rootfsDirectory(context)
        val etc = File(rootfs, "etc")
        if (!etc.isDirectory) return

        val resolver = File(etc, "resolv.conf")
        val dnsServers = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val properties = if (network != null) cm.getLinkProperties(network) else null
            properties?.dnsServers.orEmpty()
                .filterNot { it.isAnyLocalAddress || it.isLinkLocalAddress }
                .map { it.hostAddress?.substringBefore('%') }
                .filterNotNull()
                .distinct()
        } catch (error: Exception) {
            Log.w(TAG, "Could not read Android DNS configuration", error)
            emptyList()
        }
        if (dnsServers.isEmpty()) return

        try {
            if (Files.isSymbolicLink(resolver.toPath())) {
                Files.deleteIfExists(resolver.toPath())
            }
            resolver.writeText(
                dnsServers.joinToString(separator = "\n", postfix = "\n") { "nameserver $it" },
                Charsets.UTF_8
            )
        } catch (error: Exception) {
            Log.w(TAG, "Could not update Debian resolv.conf", error)
        }
    }

    private fun downloadAndVerify(destination: File, onProgress: (Long) -> Unit) {
        val connection = (URL(ARCHIVE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "Mobile-Security-Inspector/MS-Terminal")
        }
        try {
            connection.connect()
            val response = connection.responseCode
            if (response !in 200..299) throw IOException("تعذر تنزيل Debian (HTTP $response).")
            val statedLength = connection.contentLengthLong
            if (statedLength > 0 && statedLength != EXPECTED_ARCHIVE_BYTES) {
                throw IOException("حجم ملف Debian لا يطابق النسخة المثبتة.")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                DigestInputStream(input, digest).use { checked ->
                    BufferedOutputStream(FileOutputStream(destination)).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = checked.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > EXPECTED_ARCHIVE_BYTES) {
                                throw IOException("حجم ملف Debian تجاوز الحد المتوقع.")
                            }
                            output.write(buffer, 0, read)
                            onProgress(total)
                        }
                    }
                }
            }
            if (total != EXPECTED_ARCHIVE_BYTES) {
                throw IOException("ملف Debian غير مكتمل؛ أعد المحاولة.")
            }
            val actual = digest.digest().toHex()
            if (!actual.equals(ARCHIVE_SHA256, ignoreCase = true)) {
                throw IOException("فشل التحقق من SHA-256 لملف Debian.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractRootfs(
        archive: File,
        destination: File,
        onProgress: (Long, Long) -> Unit
    ) {
        val rootCanonical = destination.canonicalFile
        val pendingLinks = ArrayList<TarArchiveEntry>()
        val directoryModes = ArrayList<Pair<File, Int>>()
        var unpackedBytes = 0L
        var entries = 0

        BufferedInputStream(FileInputStream(archive)).use { fileInput ->
            GzipCompressorInputStream(fileInput).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        entries++
                        if (entries > MAX_ARCHIVE_ENTRIES) {
                            throw IOException("أرشيف Debian يحتوي على عدد غير متوقع من الملفات.")
                        }
                        val relative = safeArchivePath(entry.name) ?: continue
                        if (relative.isEmpty()) continue
                        val target = File(destination, relative)
                        assertInsideRoot(rootCanonical, target)
                        ensureSafeParents(destination, target.parentFile)

                        when {
                            entry.isDirectory -> {
                                if (target.exists() && !target.isDirectory) {
                                    throw IOException("تعارض في مسار مجلد داخل أرشيف Debian.")
                                }
                                if (!target.exists() && !target.mkdirs()) {
                                    throw IOException("تعذر إنشاء مجلد داخل Debian.")
                                }
                                directoryModes.add(target to (entry.mode and 0xFFF))
                            }
                            entry.isSymbolicLink || entry.isLink -> pendingLinks.add(entry)
                            entry.isFile -> {
                                val size = entry.size
                                if (size < 0L || size > MAX_UNPACKED_BYTES ||
                                    unpackedBytes + size > MAX_UNPACKED_BYTES
                                ) {
                                    throw IOException("أرشيف Debian تجاوز حد الحجم المفكوك.")
                                }
                                if (target.exists() && target.isDirectory) {
                                    throw IOException("تعارض ملف ومجلد داخل أرشيف Debian.")
                                }
                                target.parentFile?.let {
                                    if (!it.exists() && !it.mkdirs()) {
                                        throw IOException("تعذر تجهيز مسار ملف داخل Debian.")
                                    }
                                }
                                FileOutputStream(target).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var remaining = size
                                    while (remaining > 0L) {
                                        val read = tar.read(
                                            buffer, 0,
                                            minOf(buffer.size.toLong(), remaining).toInt()
                                        )
                                        if (read < 0) throw IOException("ملف ناقص داخل أرشيف Debian.")
                                        output.write(buffer, 0, read)
                                        remaining -= read
                                    }
                                }
                                unpackedBytes += size
                                applyMode(target, entry.mode)
                                onProgress(unpackedBytes, MAX_UNPACKED_BYTES)
                            }
                            else -> {
                                // Device nodes and FIFOs are intentionally not recreated.
                                // PRoot provides app-visible /dev and /proc at runtime.
                            }
                        }
                    }
                }
            }
        }

        // Hard links are created after regular files have been extracted.
        pendingLinks.filter { it.isLink }.forEach { entry ->
            val relative = safeArchivePath(entry.name) ?: return@forEach
            val linkRelative = safeArchivePath(entry.linkName)
                ?: throw IOException("رابط غير صالح داخل أرشيف Debian.")
            val target = File(destination, relative)
            val source = File(destination, linkRelative)
            assertInsideRoot(rootCanonical, target)
            assertInsideRoot(rootCanonical, source)
            ensureSafeParents(destination, target.parentFile)
            if (!source.exists() || !source.isFile) {
                throw IOException("هدف رابط غير موجود داخل أرشيف Debian.")
            }
            if (target.exists()) target.delete()
            try {
                Files.createLink(target.toPath(), source.toPath())
            } catch (_: Exception) {
                source.copyTo(target, overwrite = true)
            }
            applyMode(target, entry.mode)
        }

        // Absolute symlinks are guest-root paths, not host filesystem paths.
        pendingLinks.filter { it.isSymbolicLink }.forEach { entry ->
            val relative = safeArchivePath(entry.name) ?: return@forEach
            val linkTarget = entry.linkName
            if (linkTarget.isBlank() || linkTarget.contains('\u0000')) {
                throw IOException("رابط رمزي غير صالح داخل أرشيف Debian.")
            }
            val resolved = resolveGuestLink(relative, linkTarget)
            if (resolved == ".." || resolved.startsWith("../")) {
                throw IOException("رابط رمزي يتجاوز جذر بيئة Debian.")
            }
            val link = File(destination, relative)
            assertInsideRoot(rootCanonical, link)
            ensureSafeParents(destination, link.parentFile)
            if (link.exists() || Files.isSymbolicLink(link.toPath())) {
                deleteRecursively(link)
            }
            Files.createSymbolicLink(link.toPath(), java.nio.file.Paths.get(linkTarget))
        }

        // Apply directory permissions last so their children can be created.
        directoryModes.asReversed().forEach { (directory, mode) ->
            applyMode(directory, mode)
        }
    }

    private fun safeArchivePath(raw: String): String? {
        if (raw.isBlank() || raw.contains('\u0000') || raw.startsWith("/") ||
            raw.startsWith("\\") || Regex("^[A-Za-z]:").containsMatchIn(raw)
        ) {
            throw IOException("مسار غير آمن داخل أرشيف Debian.")
        }
        val normalized = raw.replace('\\', '/')
        val components = normalized.split('/')
        if (components.any { it == ".." }) {
            throw IOException("مسار يحاول الخروج من مجلد Debian.")
        }
        return components.filter { it.isNotEmpty() && it != "." }.joinToString("/")
    }

    private fun resolveGuestLink(entryRelative: String, linkTarget: String): String {
        val parent = java.nio.file.Paths.get(entryRelative).parent
            ?: java.nio.file.Paths.get("")
        val target = if (linkTarget.startsWith("/")) {
            java.nio.file.Paths.get(linkTarget.removePrefix("/"))
        } else {
            parent.resolve(linkTarget)
        }
        return target.normalize().toString().replace('\\', '/')
    }

    private fun assertInsideRoot(root: File, candidate: File) {
        val rootPath = root.absoluteFile.toPath().normalize().toString()
        val candidatePath = candidate.absoluteFile.toPath().normalize().toString()
        if (candidatePath != rootPath &&
            !candidatePath.startsWith(rootPath + File.separator)
        ) {
            throw IOException("أرشيف Debian حاول الكتابة خارج مجلد التثبيت.")
        }
    }

    private fun ensureSafeParents(root: File, parent: File?) {
        if (parent == null) return
        val rootPath = root.canonicalFile.toPath()
        val parentPath = parent.toPath().toAbsolutePath().normalize()
        if (!parentPath.startsWith(rootPath)) {
            throw IOException("مسار مجلد غير آمن داخل أرشيف Debian.")
        }
        var current = root
        val relative = rootPath.relativize(parentPath)
        for (component in relative) {
            current = File(current, component.toString())
            if (Files.isSymbolicLink(current.toPath())) {
                throw IOException("تم اكتشاف مجلد وسيط رمزي أثناء فك Debian.")
            }
            if (current.exists() && !current.isDirectory) {
                throw IOException("مسار مجلد غير صالح داخل أرشيف Debian.")
            }
            if (!current.exists() && !current.mkdir()) {
                throw IOException("تعذر إنشاء مجلد أثناء فك Debian.")
            }
        }
    }

    private fun applyMode(file: File, rawMode: Int) {
        try {
            Os.chmod(file.absolutePath, rawMode and 0xFFF)
        } catch (error: Exception) {
            Log.w(TAG, "Could not apply archive mode to ${file.name}", error)
        }
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists() && !Files.isSymbolicLink(file.toPath())) return
        if (Files.isSymbolicLink(file.toPath()) || file.isFile) {
            Files.deleteIfExists(file.toPath())
            return
        }
        file.listFiles()?.forEach { child -> deleteRecursively(child) }
        Files.deleteIfExists(file.toPath())
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(Locale.ROOT, byte) }
}
