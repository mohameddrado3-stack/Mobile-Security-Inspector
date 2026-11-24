plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.msi.inspector"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.msi.inspector"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.5.0-alpha02"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("generated/msiProotJniLibs"))

    packaging {
        jniLibs {
            // The executable is named .so so Android extracts it into nativeLibraryDir.
            useLegacyPackaging = true
            keepDebugSymbols += setOf("**/libproot-static.so", "**/libproot-static-loader.so")
        }
    }
}

// Real VT/xterm terminal view and PTY implementation (Termux terminal library; license notices retained).
dependencies {
    implementation("com.github.termux.termux-app:terminal-view:v0.118.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
}

// Download pinned PRoot executables at build time and verify their published SHA-256 hashes.
val msiProotGenerated = layout.buildDirectory.dir("generated/msiProotJniLibs/arm64-v8a")
val prepareMsiProotRuntime = tasks.register("prepareMsiProotRuntime") {
    val prootOut = msiProotGenerated.map { it.file("libproot-static.so") }
    val loaderOut = msiProotGenerated.map { it.file("libproot-static-loader.so") }
    outputs.files(prootOut, loaderOut)

    doLast {
        val outDir = msiProotGenerated.get().asFile
        outDir.mkdirs()

        data class Artifact(val url: String, val name: String, val sha256: String)
        val artifacts = listOf(
            Artifact(
                "https://github.com/proot-me/proot/releases/download/v5.3.0/proot-v5.3.0-aarch64-static",
                "libproot-static.so",
                "fa10b1a7818c2f5b1dcb5834450570c368c9ecf66d31521509621b95c4538a45"
            ),
            Artifact(
                "https://raw.githubusercontent.com/Dreamucxe/NEXTERM/c20ab5d276f601f4e91e5d58683f5ea81c8af8b4/app/src/main/jniLibs/arm64-v8a/libproot-static-loader.so",
                "libproot-static-loader.so",
                "51c3427b112edc70d1979b48209c41f332616758138de3be659cc79e50436450"
            )
        )

        artifacts.forEach { artifact ->
            val destination = outDir.resolve(artifact.name)
            val temporary = outDir.resolve(artifact.name + ".part")
            if (destination.isFile && sha256(destination) == artifact.sha256) return@forEach
            destination.delete()
            temporary.delete()

            val connection = java.net.URL(artifact.url).openConnection() as java.net.HttpURLConnection
            try {
                connection.connectTimeout = 20_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mobile-Security-Inspector-build")
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    throw GradleException("Could not download pinned ${artifact.name}: HTTP ${connection.responseCode}")
                }
                connection.inputStream.use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }

            val actual = sha256(temporary)
            if (actual != artifact.sha256) {
                temporary.delete()
                throw GradleException("SHA-256 mismatch for ${artifact.name}: expected ${artifact.sha256}, got $actual")
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete()
                throw GradleException("Could not install verified ${artifact.name} into generated JNI libraries.")
            }
        }
    }
}

fun sha256(file: java.io.File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

tasks.named("preBuild").configure {
    dependsOn(prepareMsiProotRuntime)
}
