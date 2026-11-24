# MS Terminal — Architecture and Safety Contract

Status: implementation baseline for `0.5.0-alpha02`; real-device validation remains pending.
Target: an embedded, persistent Linux userland inside Mobile Security Inspector.

## Product goal

MS Terminal must be a real interactive terminal, not a screen that prints simulated command results and not a collection of pre-filled buttons. It must support a real PTY session, shell input/output, ANSI terminal behavior, package installation, persistent home files, command history, and saved scripts.

The first Linux environment will be Debian stable on the device's native ABI. The environment will be installed only when the user explicitly starts setup. It is separate from the existing app-audit engine: a terminal setup or runtime failure must not prevent the security scanner and app manager from starting.

This is an original MSI feature. It is not a rebranded Termux app, and it will not claim compatibility with every Termux package. Debian's APT repositories provide the Linux package ecosystem.

## Architecture

1. **Android UI**
   - A dedicated internal `TerminalActivity` and terminal screen.
   - A real VT/xterm-compatible terminal renderer with scrollback, selection, soft-keyboard support, and a small row of terminal modifier keys.
   - Separate setup, installation-progress, ready, unsupported-device, and runtime-error states.
   - No shell execution or filesystem work on the Android main thread.

2. **PTY and terminal engine**
   - Use a maintained terminal-emulation component with a real pseudo-terminal (PTY) session.
   - The PTY process must receive the actual terminal dimensions and environment variables.
   - Session lifecycle is explicit: start, resize, send input, receive output, exit, and cleanup.
   - Do not emulate terminal output with hard-coded text.

3. **Native PRoot runtime**
   - Build PRoot and its required native libraries from pinned source versions for supported Android ABIs.
   - Package PRoot, its loader, and native dependencies inside the APK under `jniLibs/<abi>/`, using Android's install-time native-library extraction.
   - Resolve the installed runtime through `ApplicationInfo.nativeLibraryDir`; do not download a native executable and try to launch it from a writable app-data directory.
   - Set and validate the runtime library path and loader path explicitly.
   - On first use, probe the exact PRoot command line with a harmless shell command. Cache the successful mode for that device/runtime combination. If no mode works, show the diagnostic rather than an empty terminal.

4. **Persistent Debian userland**
   - Install the selected Debian OCI root filesystem into app-private storage, under the application's files directory.
   - Resolve the image manifest for the actual device ABI and verify every downloaded layer against its SHA-256 digest before extraction.
   - Extract into a temporary installation directory; reject absolute paths, parent-directory traversal, and unsafe archive entries; atomically promote the completed rootfs only after validation.
   - Keep the rootfs, `/root`, `/var/lib/dpkg`, APT state, package cache, shell configuration, and user files between sessions.
   - Check an installation marker and version/digest before setup. Do not reinstall an intact environment or redownload already installed packages.
   - A reset or removal action must require a clear confirmation and explain that it deletes the Linux environment and its files.

5. **Package management**
   - Use Debian `apt`/`dpkg` inside PRoot. A small `pkg` compatibility wrapper may map familiar package commands to APT, but the UI must clearly identify this as Debian APT, not Termux's package repository.
   - APT remains responsible for dependency resolution, package state, and already-installed package detection.
   - Package indexes and package archives are verified by the distro's configured signed repositories.
   - The initial setup downloads a base image only. Optional utilities are installed from signed repositories and are never bundled as unverified executable downloads.

6. **Storage**
   - Keep the rootfs, home directory, command history, snippets, and scripts in app-private storage.
   - This data survives app process restarts and normal device restarts, but Android clears it if the user clears app data or uninstalls the app.
   - Access to shared files is opt-in through Android's Storage Access Framework (user-selected files/folders). Do not request broad storage access by default.
   - Never bind other apps' private data directories into the Linux environment.

## Android execution constraint

Android 10 and later block `execve()` of files in an app's writable home directory for apps targeting API 29 or later. Native executables that must be started by the app therefore need to be packaged in the APK and extracted by Android into the app's native-library directory at install time. The Linux rootfs is data, not a place from which MSI launches native Android executables directly; PRoot's packaged loader mediates guest execution.

Gradle packaging must preserve the extracted native-library files. Build validation must check that required native libraries exist for each ABI included in the APK. Runtime validation must check file existence, ELF architecture, dynamic dependencies, loader path, PRoot exit code, and diagnostic output.

## Permissions

- `INTERNET`: required for first-time rootfs setup and later APT package operations. It is a normal install-time permission, not a runtime prompt.
- `ACCESS_NETWORK_STATE`: optional, only if the UI needs to display current connectivity state.
- Shared-file access: use the Storage Access Framework only after the user chooses a file or folder.
- Do not request Accessibility, Device Admin, VPN, all-files access, or Shizuku merely to make the terminal appear more powerful.
- Do not claim Root access. No app permission can turn PRoot into kernel-level root.

## Honest capability boundaries

PRoot provides a Linux userland without giving the Android app real root privileges or a separate kernel. Commands that require kernel-level root, kernel modules, real mount operations, namespaces, cgroups, or a full init system may fail. Performance-sensitive and filesystem-heavy work can be slower because PRoot intercepts system calls. Linux processes remain subject to Android process and battery management.

The environment cannot read other apps' private data, silently change their permissions, disable or uninstall them, or bypass Android's sandbox. Device and network checks must be limited to systems the user owns or is authorized to administer.

## Initial curated tools

Prefer practical local administration and defensive diagnostics: shell utilities, APT, certificates, `curl`, `git`, `python3`, `openssl`, checksum/file utilities, `openssh-client`, text editors, process inspection, DNS lookup, and basic network configuration inspection. Do not preinstall attack frameworks or tools whose purpose is unauthorized access.

## Third-party components and licensing

- Use only terminal-emulation components whose license and notices are verified for the exact pinned version. Preserve the original copyright and license notices.
- PRoot is GPL-2.0 in the selected Termux package recipe; `libtalloc` is GPL-3.0; `libandroid-shmem` is BSD 3-Clause. Pin versions and source hashes, retain the corresponding source and notices, and document redistribution obligations.
- Do not copy NEXTERM application source. Its publicly visible implementation was used only to validate architectural constraints.
- Do not copy the Termux application wholesale or imply that MSI owns upstream Termux. Reuse only components under a compatible license and comply with their conditions.

## Delivery gates

A version is not ready for the user until all applicable gates pass:

1. Gradle debug build succeeds.
2. Every packaged native library matches its ABI and all required dependencies are present.
3. SHA-256 checks pass for downloaded image layers and extracted installation metadata.
4. Unit tests cover archive path traversal, corrupted downloads, interrupted setup/resume, failed extraction rollback, missing native libraries, and repeated setup without duplicate downloads.
5. Instrumented tests cover PTY input/output, terminal resizing, ANSI colors, keyboard shortcuts, process exit, and session cleanup.
6. A real Android 11 device test confirms installation, initial setup, persistence after app restart, APT install/reopen behavior, and a clear failure message on unsupported devices.

CI success proves only that the build and automated checks passed. It does not prove that the APK was installed or tested on the user's physical phone.


## Implemented in v0.5.0-alpha02

- Terminal View and a real PTY session are embedded in a dedicated screen.
- Debian Trixie ARM64 rootfs setup downloads the official debuerreotype OCI layer (49,770,086 bytes) from the `dist-arm64v8/trixie` artifact branch and verifies SHA-256 `6bf9dd8e528e8b59aba65241842ec43567ab9ff163d6d444bb4701ece0f11093` before extraction.
- The rootfs is extracted into a staging directory under app-private storage. Absolute archive paths and parent-directory traversal are rejected; symbolic and hard links are handled separately; special device nodes are not recreated. Installation is promoted only after the marker and required binaries are present.
- The installed rootfs survives normal app restarts and device restarts. Clearing app data or uninstalling MSI removes it.
- PRoot 5.3.0 static ARM64 executable and its matching loader are fetched during the Gradle build from pinned URLs, SHA-256 checked, and packaged under `lib/arm64-v8a/` so Android extracts them to `nativeLibraryDir`.
- Runtime starts Debian with PRoot as a separate process and opens `/dev` and `/proc` only. Shared storage and other apps' private data are not bound into the guest.
- Android `INTERNET` permission is added for first-time setup and APT; DNS resolver entries are refreshed from the active Android network when available.
- The current implementation supports 64-bit ARM64 processes only. Other ABIs retain the Android Shell fallback and receive an explicit unsupported message.

## Validation still required

- CI must pass the Gradle build and verify that the two PRoot binaries inside the APK still match their expected SHA-256 values.
- The downloaded Debian layer has a pinned digest, but full installation, symlink handling, PRoot loader startup, APT package installation, and persistence still require instrumented and real-device tests.
- CI success alone is not evidence that the rootfs was installed or that PRoot launched successfully on a physical Android 11 device.
- No Root capability, kernel isolation, or cross-application access is provided.
