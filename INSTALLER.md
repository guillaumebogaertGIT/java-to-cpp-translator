# Windows build and upgrade

## Prerequisites

Use a full Windows x64 JDK 21 and WiX 3.14.1 build tools. Set `JAVA_HOME`
to the JDK directory for the current terminal. The script prepends that JDK's
`bin` directory to PATH and checks its compiler version.

WiX can be on PATH, installed in
`C:\Program Files (x86)\WiX Toolset v3.14\bin`, or extracted from the
[official WiX 3.14.1 portable archive](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm)
into `build/tools/wix-3.14.1`. No system-wide PATH change is required.

## Version and identity

The distributed version is `1.0.3`. Set `app.version` in
`src/main/resources/app.properties` and mirror it in the top-level
`pom.xml` version. Packaging refuses mismatched values. Runtime UI, updater,
native launcher, and installer all use this version.

Never change the upgrade UUID:
`a3063a2d-7643-4f2d-8c4b-c263dad25c09`.

Keep the application name, installation scope (per-machine), and Start menu
group stable: `Java to C++ Translator`. Install a higher version over the
existing installation; do not uninstall first.

## Build and test

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\package-installer.ps1
```

The script runs a clean Maven package build under JDK 21, including all 110
translator checks and 9 updater/version checks. These are plain Java runners
executed by the Maven exec plugin; Surefire's zero JUnit tests is expected.

Maven produces a thin application JAR and the Windows JavaFX base, graphics,
and controls module JARs (including native DLLs). Packaging copies only those
files into a dedicated input directory. JavaFX is loaded through
`--module-path=$APPDIR\modules --add-modules=javafx.controls`.
The native entry point remains `TranslatorApp`. The bundled JDK runtime
includes desktop, HTTP, and TLS support and needs no machine-installed Java.

The app image is built at `dist/app-image/Java to C++ Translator`.
Before any installer is built, its actual EXE is launched with an opt-in
smoke-test argument. That test checks the visible JavaFX window and updater
button, fires Translate on supported Java input, checks C++ output, constructs
the HTTP client, writes a temporary success report, and exits. Missing reports,
nonzero exits, and timeouts stop packaging.

Use `-AppImageOnly` to stop after this check. To recheck an image or installation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\test-packaged-app.ps1 -AppImage "C:\Program Files\Java to C++ Translator"
```

Successful full build output:
`dist/installer/Java to C++ Translator-1.0.3.exe`.

## Upgrade verification

Run the new installer over the existing app. Check Installed Apps for one entry
at the new version, and launch both the installed EXE and Start menu shortcut.
Confirm the updater icon, translation, and window controls work. Automated smoke
tests do not replace checking a normal shortcut launch. Installation may require
Windows administrator approval.

## Launch diagnostics

Local verification on 2026-09-10: JDK 21.0.12 clean Maven build, all 110
translator checks and 9 updater/version checks, and the native app-image smoke
test passed. The v1.0.3 EXE installer upgraded this machine's existing v1.0.1
installation without uninstalling first. An unelevated silent attempt returned
error 1730/exit 1603; rerunning with Windows administrator elevation succeeded
with exit 0. Installed Apps contains exactly one entry at v1.0.3. The installed
MSI's UpgradeCode is the UUID specified above.

The installed native launcher passed the same smoke test. A normal Start menu
shortcut launch opened TranslatorApp from Program Files. Windows UI Automation
entered Java input, clicked Translate, and verified the generated C++ output.
The visible updater button was clicked and successfully reported v1.0.3 as the
latest version. This run tested v1.0.1 to v1.0.3; a separate v1.0.2 installation
was not available on this machine.

Repeated packaging also exposed a OneDrive cleanup issue: cloud directories
carry the ReparsePoint attribute even though they are not junctions. Cleanup now
accepts only the Windows CLOUD tag family and checks parents and descendant
directories before removal. Other reparse tags still stop cleanup. Rebuilding
inside OneDrive passed, and a nested-junction check confirmed that cleanup
refuses the junction and preserves its target. See Microsoft's
[reparse tag documentation](https://learn.microsoft.com/en-us/windows/win32/fileio/reparse-point-tags).

Application startup and uncaught errors are appended to
`%LOCALAPPDATA%\Java to C++ Translator\logs\application.log`.
The previous log is retained when it exceeds 1 MiB.

For failures before JavaFX starts, run `diagnose-launch.ps1` from the installed
application directory with PowerShell. It uses the bundled Java executable,
module path, and application JAR and captures initial launcher errors in
`launcher.log` in the same log directory. It also opens the normal application
when the runtime is healthy; close the window to finish the diagnostic command.

## v1.0.2 failure

The failing v1.0.2 launcher named `TranslatorApp`, which extends JavaFX
`Application`, but supplied JavaFX only inside a shaded classpath JAR.
The JVM's JavaFX launch path requires the JavaFX graphics module to be resolved.
A console reproduction printed: "JavaFX runtime components are missing, and are
required to run this application". The console-less EXE hid that error.

The old packaging input also included Maven test/build folders and the original
unshaded JAR. The checked-in packaging script reused a stale app image instead
of rebuilding it. v1.0.3 rebuilds from source, supplies JavaFX modules explicitly,
and tests the native launch before creating an installer. Spaces in the app name
are supported by argument arrays and paths relative to the installed app.

References: [OpenJFX setup](https://openjfx.io/openjfx-docs/),
[JDK 21 jpackage options](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html).

Do not commit `build/`, `target/`, `dist/`, downloaded tools, runtime images,
installers, logs, or temporary reports.
