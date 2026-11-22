param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run='ms1', [ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Source='ap4')
$ErrorActionPreference='Stop'
$repoPath=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot));$profile=Join-Path $repoPath "run-adventure-client-solo-$Run";$sourceWorld=Join-Path $repoPath "run-adventure-$Source/world";$world=Join-Path $profile 'saves/RewardsValidation'
if(Test-Path -LiteralPath $profile) { throw 'Fresh isolated solo profile required.' }
if(-not (Test-Path -LiteralPath (Join-Path $sourceWorld 'validation/adventure-preparation-server.nbt'))) { throw 'Stopped native preparation world required.' }
$lockProbe=[IO.File]::Open((Join-Path $sourceWorld 'session.lock'),[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None);$lockProbe.Dispose()
New-Item -ItemType Directory -Path $world -Force | Out-Null
Get-ChildItem -LiteralPath $sourceWorld -Force | Copy-Item -Destination $world -Recurse
if(Test-Path -LiteralPath (Join-Path $world 'validation/adventure-rewards-server.nbt')) { throw 'Fresh reward sequence required; do not replay a completed source.' }
& (Join-Path $PSScriptRoot 'test-travel-network.ps1') -Run $Run -PrepareOnly
if($LASTEXITCODE -ne 0) { throw 'Native client preparation failed.' }
$fixtureConfig=Join-Path $world 'serverconfig';New-Item -ItemType Directory -Path $fixtureConfig -Force | Out-Null
@'
[rpg]
alignmentThresholds = [-100, -10, 10, 100]
[rpg.farm]
windowTicks = 7200
'@ | Set-Content -LiteralPath (Join-Path $fixtureConfig 'legacyoftheforce-server.toml') -Encoding ascii
$launch=Get-Content -LiteralPath (Join-Path $repoPath '.tools/travel-network-launch.json') -Raw | ConvertFrom-Json
$template=Get-Content -LiteralPath (Join-Path $repoPath 'build/moddev/clientTwoRunProgramArgs.txt') -Raw
$marker=$template.IndexOf('# User Supplied Program Arguments');if($marker -lt 0) { throw 'Pinned argument template changed.' }
@'
version:3955
onboardAccessibility:false
renderDistance:3
simulationDistance:5
maxFps:30
pauseOnLostFocus:false
fullscreen:false
enableVsync:false
lang:fr_fr
guiScale:2
tutorialStep:none
'@ | Set-Content -LiteralPath (Join-Path $profile 'options.txt') -Encoding ascii
$program=Join-Path $repoPath ".tools/mob-rewards-solo-$Run-args.txt"
($template.Substring(0,$marker)+"# User Supplied Program Arguments`n--username`nAdventureOne`n--quickPlaySingleplayer`nRewardsValidation`n") | Set-Content -LiteralPath $program -Encoding utf8NoBOM
$arguments=[Collections.Generic.List[string]]::new();foreach($arg in $launch.arguments) { $arguments.Add([string]$arg) }
$main=$arguments.IndexOf('net.neoforged.devlaunch.Main');if($main -lt 0) { throw 'Unexpected native launcher.' }
foreach($arg in @('-Xms256M','-Xmx2048M','-Dlotf.validation.adventureRewards=true','-Dlotf.validation.adventureRewardsSolo=true')) { $arguments.Insert($main,$arg);$main++ }
$arguments[$arguments.Count-1]='@'+$program.Replace('\','\\')
$info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=Join-Path $repoPath '.tools/java-client-two/bin/java.exe'
foreach($arg in $arguments) { $info.ArgumentList.Add($arg) };foreach($entry in $launch.environment.PSObject.Properties) { $info.Environment[$entry.Name]=[string]$entry.Value }
$info.WorkingDirectory=$profile;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$client=[Diagnostics.Process]::new();$client.StartInfo=$info;$client.Start() | Out-Null;$out=$client.StandardOutput.ReadToEndAsync();$err=$client.StandardError.ReadToEndAsync();Write-Output "STARTED: native integrated RPG PID=$($client.Id)";$forced=$false
try {
    $deadline=(Get-Date).AddSeconds(660)
    while(-not $client.HasExited -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 250 }
} finally {
    if(-not $client.HasExited) { $forced=$true;$client.Kill();$client.WaitForExit(5000) | Out-Null }
    ($out.GetAwaiter().GetResult()+$err.GetAwaiter().GetResult()) | Set-Content -LiteralPath (Join-Path $repoPath ".tools/mob-rewards-solo-$Run-console.log")
    $exitCode=$client.ExitCode
    @{run=$Run;source=$Source;profile=$profile;pid=$client.Id;exitCode=$exitCode;forced=$forced;finishedUtc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $repoPath ".tools/mob-rewards-solo-$Run-result.json")
    $client.Dispose()
}
if($exitCode -ne 0 -or $forced -or -not (Test-Path -LiteralPath (Join-Path $world 'validation/adventure-rewards-server.nbt')) -or -not (Test-Path -LiteralPath (Join-Path $profile 'validation/adventure-rewards-client.nbt'))) { throw 'Native integrated reward scenario incomplete; inspect retained logs.' }
Write-Output 'OBSERVED: native solo attacks and integrated reward proofs; run independent verifier.'
