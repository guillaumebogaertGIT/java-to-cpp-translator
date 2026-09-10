param([Parameter(Mandatory)][string]$AppImage)
$ErrorActionPreference = 'Stop'
$AppImage = (Resolve-Path -LiteralPath $AppImage).Path
$config = Get-Content -LiteralPath (Join-Path $AppImage 'app/Java to C++ Translator.cfg')
if ($config -notcontains 'app.mainclass=TranslatorApp') { throw 'Unexpected launcher main class.' }
$report = Join-Path ([IO.Path]::GetTempPath()) ("translator-smoke-" + [guid]::NewGuid() + '.txt')
$launcher = Join-Path $AppImage 'Java to C++ Translator.exe'
$process = Start-Process -FilePath $launcher -ArgumentList ('"--smoke-test=' + $report + '"') -PassThru
try {
    if (-not $process.WaitForExit(45000)) {
        Stop-Process -Id $process.Id -Force
        throw 'Packaged launch timed out. Run diagnose-launch.ps1 in the app directory.'
    }
    if ($process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $report)) {
        throw 'Packaged launch failed. See %LOCALAPPDATA%/Java to C++ Translator/logs or diagnose-launch.ps1.'
    }
    $result = Get-Content -LiteralPath $report
    if ($result -notmatch '^PASS v') { throw "Invalid smoke test result: $result" }
    Write-Host $result
} finally {
    if (Test-Path -LiteralPath $report) { Remove-Item -LiteralPath $report -Force }
}
