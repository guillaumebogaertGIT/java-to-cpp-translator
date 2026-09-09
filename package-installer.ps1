$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    # Compile the existing standalone test runner afresh, avoiding stale Maven output.
    New-Item -ItemType Directory -Force -Path build/installer-tests | Out-Null
    & javac -d build/installer-tests src/Translator.java src/CppFormatter.java src/TranslatorTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    & java -cp build/installer-tests TranslatorTest
    if ($LASTEXITCODE -ne 0) { throw 'Translation checks failed. Packaging stopped.' }

    $wixBinCandidates = @(
        (Join-Path ${env:ProgramFiles(x86)} 'WiX Toolset v3.14\bin'),
        (Join-Path $env:ProgramFiles 'WiX Toolset v3.14\bin'),
        (Join-Path ${env:ProgramFiles(x86)} 'WiX Toolset v3.14.1\bin'),
        (Join-Path $env:ProgramFiles 'WiX Toolset v3.14.1\bin')
    )
    foreach ($candidate in $wixBinCandidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            $env:Path = "$candidate;$env:Path"
            break
        }
    }

    foreach ($tool in @('jpackage', 'candle', 'light')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
            throw "Missing $tool. Use JDK 21 and install WiX Toolset 3.14.1 build tools; add its bin directory to PATH. See INSTALLER.md."
        }
    }

    $iconPath = Join-Path $PSScriptRoot 'packaging/app-icon.ico'
    if (-not (Test-Path -LiteralPath $iconPath)) {
        throw "Missing Windows icon: $iconPath. Generate packaging/app-icon.ico before packaging."
    }

    $appImage = Join-Path $PSScriptRoot 'dist/Java to C++ Translator'
    foreach ($required in @('Java to C++ Translator.exe', 'app/.jpackage.xml', 'runtime/lib/modules')) {
        if (-not (Test-Path -LiteralPath (Join-Path $appImage $required))) {
            throw "The existing app-image is incomplete: $required is missing. Rebuild the working app-image first."
        }
    }
    # Reuse the working launcher, JavaFX libraries, and bundled runtime unchanged.
    # Windows Installer supplies Installed Apps registration and uninstallation.
    # Bump the app version to force a fresh launcher + shortcut icon when re-installing.
    $packageArguments = @(
        '--type', 'exe',
        '--app-image', $appImage,
        '--name', 'Java to C++ Translator',
        '--app-version', '1.0.1',
        '--icon', $iconPath,
        '--dest', (Join-Path $PSScriptRoot 'dist/installer'),
        '--win-menu', '--win-menu-group', 'Java to C++ Translator',
        '--win-shortcut', '--win-dir-chooser',
        '--win-upgrade-uuid', 'a3063a2d-7643-4f2d-8c4b-c263dad25c09'
    )
    & jpackage @packageArguments
    if ($LASTEXITCODE -ne 0) { throw 'jpackage installer creation failed.' }
    $installer = Join-Path $PSScriptRoot 'dist/installer/Java to C++ Translator-1.0.1.exe'
    if (-not (Test-Path -LiteralPath $installer)) { throw 'Expected installer was not produced.' }
    Write-Host "Installer created: $installer"
} finally {
    Pop-Location
}
