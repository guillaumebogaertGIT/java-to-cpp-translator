$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    # Compile the existing standalone test runner afresh, avoiding stale Maven output.
    New-Item -ItemType Directory -Force -Path build/installer-tests | Out-Null
    & javac -d build/installer-tests src/Translator.java src/CppFormatter.java src/TranslatorTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    & java -cp build/installer-tests TranslatorTest
    if ($LASTEXITCODE -ne 0) { throw 'Translation checks failed. Packaging stopped.' }

    foreach ($tool in @('jpackage', 'candle', 'light')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
            throw "Missing $tool. Use JDK 21 and install WiX Toolset 3.14.1 build tools; add its bin directory to PATH. See INSTALLER.md."
        }
    }
    $appImage = Join-Path $PSScriptRoot 'dist/Java to C++ Translator'
    foreach ($required in @('Java to C++ Translator.exe', 'app/.jpackage.xml', 'runtime/lib/modules')) {
        if (-not (Test-Path -LiteralPath (Join-Path $appImage $required))) {
            throw "The existing app-image is incomplete: $required is missing. Rebuild the working app-image first."
        }
    }
    # Reuse the working launcher, JavaFX libraries, and bundled runtime unchanged.
    # Windows Installer supplies Installed Apps registration and uninstallation.
    $packageArguments = @(
        '--type', 'exe',
        '--app-image', $appImage,
        '--name', 'Java to C++ Translator',
        '--app-version', '1.0.0',
        '--dest', (Join-Path $PSScriptRoot 'dist/installer'),
        '--win-menu', '--win-menu-group', 'Java to C++ Translator',
        '--win-shortcut', '--win-dir-chooser',
        '--win-upgrade-uuid', 'a3063a2d-7643-4f2d-8c4b-c263dad25c09'
    )
    & jpackage @packageArguments
    if ($LASTEXITCODE -ne 0) { throw 'jpackage installer creation failed.' }
    $installer = Join-Path $PSScriptRoot 'dist/installer/Java to C++ Translator-1.0.0.exe'
    if (-not (Test-Path -LiteralPath $installer)) { throw 'Expected installer was not produced.' }
    Write-Host "Installer created: $installer"
} finally {
    Pop-Location
}
