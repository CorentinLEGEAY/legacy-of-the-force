param([Parameter(ValueFromRemainingArguments=$true)][string[]]$GradleArguments)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
if (-not $env:JAVA_HOME) {
    $localJdk = Get-ChildItem -LiteralPath (Join-Path $repoRoot '.tools/java') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
}
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
Push-Location $repoRoot
try {
    & ./gradlew.bat @GradleArguments
    exit $LASTEXITCODE
} finally { Pop-Location }
