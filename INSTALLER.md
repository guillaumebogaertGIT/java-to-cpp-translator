# Windows EXE installer

The existing working app-image at `dist/Java to C++ Translator/` is preserved.
`package-installer.ps1` wraps that image in a Windows installer using JDK 21's
`jpackage`; it does not rebuild or change the GUI or translator.

## Required build tools

- JDK 21 (`java`, `javac`, and `jpackage` on PATH).
- **WiX Toolset 3.14.1 build tools**, providing `candle.exe` and `light.exe`.
  Download **wix314.exe** from the
  [official WiX 3.14.1 release](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm)
  and install it. A VS Code/Visual Studio extension alone is not sufficient.
  This setup targets the WiX 3 tools required by this JDK's jpackage.

Add the installed WiX `bin` directory (normally
`C:\Program Files (x86)\WiX Toolset v3.14\bin`) to your Windows user PATH,
then restart VS Code. Verify in its terminal:

```powershell
Get-Command jpackage, candle, light
```

For the current terminal only, if WiX was installed at that location:

```powershell
$env:Path += ';C:\Program Files (x86)\WiX Toolset v3.14\bin'
```

## Build

From the project root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\package-installer.ps1
```

The execution-policy override applies only to this process; it does not change
Windows policy. The script freshly compiles and runs all 110 translation checks
before packaging. It stops on any test failure or missing packaging prerequisite.

Expected output after a successful build:

```text
dist/installer/Java to C++ Translator-1.0.0.exe
```

The installer requests a Start Menu entry and desktop shortcut, lets the user
choose the installation directory, and uses normal Windows Installer registration
for removal through Installed Apps / Add or Remove Programs. The installer version
is 1.0.0. The existing app-image's internal launcher metadata is left unchanged.
A stable upgrade UUID is included for future installer releases.

Java and JavaFX come from the existing app-image, so end users do not need a JDK,
Maven, VS Code, or a separate JavaFX SDK. Rebuild the app-image using the existing
working packaging process before this step if application code changes.

## Verification status

The 110 freshly compiled translation checks passed. The initial Maven test run
failed while loading its existing test-class output, so the installer script uses
the standalone test runner rather than depending on those cached class files.
The installer build was attempted, but jpackage reported missing `candle.exe` and
`light.exe`. No installer EXE has been produced or installed yet.

After WiX is installed and the build succeeds, verify installation in Windows:

1. Run the EXE and finish the installation wizard.
2. Launch the app from both the Start Menu and desktop shortcuts.
3. Translate a small Java example without Maven or VS Code.
4. Remove it through Windows Settings > Apps > Installed apps and confirm the
   installed app and shortcuts are removed.

`/dist/`, `/build/`, and `/target/` are already ignored by Git. Do not commit the
installer, bundled runtime, or other generated packaging artifacts.

Reference: [JDK 21 jpackage options](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html).
