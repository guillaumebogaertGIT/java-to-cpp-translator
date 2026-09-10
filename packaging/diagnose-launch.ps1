$ErrorActionPreference = 'Stop'
$app = Join-Path $PSScriptRoot 'app'
$java = Join-Path $PSScriptRoot 'runtime/bin/java.exe'
$logDirectory = Join-Path $env:LOCALAPPDATA 'Java to C++ Translator/logs'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$log = Join-Path $logDirectory 'launcher.log'
Write-Host "Launcher output: $log"
Write-Host "Application output: $logDirectory/application.log"
# Uses only the installed runtime and dependencies, independent of PATH and current directory.
& $java --module-path (Join-Path $app 'modules') --add-modules javafx.controls `
    -cp (Join-Path $app 'java-to-cpp-translator.jar') TranslatorApp 2>&1 |
    Tee-Object -FilePath $log
exit $LASTEXITCODE
