package com.msi.inspector.terminal

import android.content.Context
import android.os.Process
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import java.io.IOException

/**
 * Builds the real PRoot command line for the pinned Debian rootfs.
 * PRoot maps a guest root identity; it does not grant Android root privileges.
 */
object ProotRuntime {

    fun createDebianSession(
        context: Context,
        client: TerminalSessionClient,
        transcriptRows: Int = 2_000
    ): TerminalSession {
        check(DebianEnvironment.isInstalled(context)) {
            "Debian has not been installed yet."
        }
        check(Process.is64Bit()) {
            "بيئة Debian الحالية تتطلب تشغيل التطبيق بمعمارية ARM64."
        }

        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val proot = File(nativeDir, "libproot-static.so")
        val loader = File(nativeDir, "libproot-static-loader.so")
        if (!proot.isFile || !loader.isFile) {
            throw IOException("ملفات تشغيل PRoot غير موجودة داخل التطبيق.")
        }

        val rootfs = DebianEnvironment.rootfsDirectory(context)
        val prootTemp = File(context.cacheDir, "msi-proot-tmp")
        if (!prootTemp.exists() && !prootTemp.mkdirs()) {
            throw IOException("تعذر تجهيز مجلد PRoot المؤقت.")
        }

        DebianEnvironment.refreshResolver(context)

        val environment = arrayOf(
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8",
            "TMPDIR=/tmp",
            "PROOT_LOADER=${loader.absolutePath}",
            "PROOT_TMP_DIR=${prootTemp.absolutePath}"
        )

        // Only app-visible /dev and /proc are bound. Shared storage and other apps'
        // private directories are deliberately not exposed to the guest environment.
        val arguments = arrayOf(
            "--link2symlink",
            "-0",
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-w", "/root",
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8",
            "TMPDIR=/tmp",
            "/bin/bash", "--login"
        )

        return TerminalSession(
            proot.absolutePath,
            rootfs.absolutePath,
            arguments,
            environment,
            transcriptRows,
            client
        )
    }
}
