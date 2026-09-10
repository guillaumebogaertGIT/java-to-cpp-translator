param([switch]$AppImageOnly)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $versionFile = Join-Path $PSScriptRoot 'src/main/resources/app.properties'
    $appVersion = ((Get-Content -LiteralPath $versionFile |
        Where-Object { $_ -match '^app\.version=' }) -replace '^app\.version=', '').Trim()
    if ($appVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid app.version.' }
    [xml]$pom = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'pom.xml')
    if ($pom.project.version -ne $appVersion) { throw 'pom.xml and app.properties versions must match.' }

    if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/jpackage.exe")) {
        throw 'Set JAVA_HOME to a full JDK 21 installation. See INSTALLER.md.'
    }
    $env:Path = "$env:JAVA_HOME/bin;$env:Path"
    if ((& javac -version | Out-String) -notmatch 'javac 21\.') {
        throw 'JAVA_HOME must select JDK 21.'
    }
    $wixCandidates = @(
        (Join-Path ${env:ProgramFiles(x86)} 'WiX Toolset v3.14/bin'),
        (Join-Path $PSScriptRoot 'build/tools/wix-3.14.1')
    )
    foreach ($candidate in $wixCandidates) {
        if (Test-Path (Join-Path $candidate 'candle.exe')) {
            $env:Path = "$candidate;$env:Path"
            break
        }
    }

    & .\mvnw.cmd --batch-mode --no-transfer-progress clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven build/tests failed.' }

    # Only remove generated directories whose resolved paths are inside this repository.
    function Reset-BuildDirectory([string]$relative) {
        $path = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot $relative))
        $root = [IO.Path]::GetFullPath($PSScriptRoot).TrimEnd('\') + '\'
        if (-not $path.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Generated path escaped repository: $path"
        }
        if (Test-Path -LiteralPath $path) {
            $resolved = (Resolve-Path -LiteralPath $path).Path
            if ($resolved -ne $path) {
                throw "Refusing to remove redirected directory: $path"
            }
            # OneDrive cloud directories also carry ReparsePoint. Permit only
            # the CLOUD family (0x9000?01a), never junctions or symbolic links.
            # Inspect each directory before descending, including the parents.
            function Assert-SafeDirectory([string]$directory) {
                $item = Get-Item -LiteralPath $directory -Force
                if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                    $details = & fsutil reparsepoint query $directory 2>&1
                    if ($LASTEXITCODE -ne 0 -or
                        ($details | Select-Object -First 1) -notmatch '0x9000[0-9a-f]01a\s*$') {
                        throw "Refusing to remove redirected or unknown directory: $directory"
                    }
                }
            }
            $parent = $path
            while ($parent.Length -ge $root.TrimEnd('\').Length) {
                Assert-SafeDirectory $parent
                $parent = Split-Path -Parent $parent
            }
            $pending = [Collections.Generic.Queue[string]]::new()
            $pending.Enqueue($path)
            while ($pending.Count -gt 0) {
                $directory = $pending.Dequeue()
                Assert-SafeDirectory $directory
                foreach ($child in Get-ChildItem -LiteralPath $directory -Directory -Force) {
                    $pending.Enqueue($child.FullName)
                }
            }
            Remove-Item -LiteralPath $path -Recurse -Force
        }
        New-Item -ItemType Directory -Path $path -Force | Out-Null
        return $path
    }
    $inputDirectory = Reset-BuildDirectory 'build/package-input'
    Copy-Item -LiteralPath 'target/java-to-cpp-translator.jar' -Destination $inputDirectory
    $modules = Join-Path $inputDirectory 'modules'
    New-Item -ItemType Directory -Path $modules | Out-Null
    Copy-Item 'target/javafx-modules/*-win.jar' -Destination $modules
    foreach ($module in @('base', 'graphics', 'controls')) {
        if (@(Get-ChildItem $modules -Filter "javafx-$module-*-win.jar").Count -ne 1) {
            throw "Missing or duplicate JavaFX $module module."
        }
    }
    $imageParent = Reset-BuildDirectory 'dist/app-image'
    $appImage = Join-Path $imageParent 'Java to C++ Translator'
    $imageArguments = @(
        '--type', 'app-image', '--input', $inputDirectory,
        '--main-jar', 'java-to-cpp-translator.jar', '--main-class', 'TranslatorApp',
        '--name', 'Java to C++ Translator', '--app-version', $appVersion,
        '--icon', (Join-Path $PSScriptRoot 'packaging/app-icon.ico'),
        '--dest', $imageParent,
        '--add-modules', 'java.se,jdk.crypto.ec,jdk.unsupported',
        '--jlink-options', '--strip-debug --no-header-files --no-man-pages',
        '--java-options', '--module-path=$APPDIR\modules',
        '--java-options', '--add-modules=javafx.controls'
    )
    & jpackage @imageArguments
    if ($LASTEXITCODE -ne 0) { throw 'App-image build failed.' }
    Copy-Item -LiteralPath 'packaging/diagnose-launch.ps1' -Destination $appImage

    & "$PSScriptRoot/packaging/test-packaged-app.ps1" -AppImage $appImage
    if ($AppImageOnly) { return }
    foreach ($tool in @('candle.exe', 'light.exe')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Missing $tool. See INSTALLER.md." }
    }

    $installerDirectory = Join-Path $PSScriptRoot 'dist/installer'
    New-Item -ItemType Directory -Force -Path $installerDirectory | Out-Null
    $installer = Join-Path $installerDirectory "Java to C++ Translator-$appVersion.exe"
    if (Test-Path -LiteralPath $installer) { Remove-Item -LiteralPath $installer -Force }
    & jpackage --type exe --app-image $appImage --name 'Java to C++ Translator' `
        --app-version $appVersion --dest $installerDirectory `
        --win-menu --win-menu-group 'Java to C++ Translator' --win-shortcut --win-dir-chooser `
        --win-upgrade-uuid 'a3063a2d-7643-4f2d-8c4b-c263dad25c09'
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $installer)) {
        throw 'Installer build failed.'
    }
    Write-Host "Installer created: $installer"
} finally {
    Pop-Location
}
