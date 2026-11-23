param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run='ap1', [ValidateSet('Prep','Travel','Journey','Recovery','Progress','ProgressRecovery','Rewards','RewardsRecovery','Block','BlockRecover','Impulse','ImpulseCrash','ImpulseRecover','ImpulseUnload','SaberWorkshop','SaberRecover','SaberCrash','SaberPickup','SaberPickupCrash','SaberIo','SaberCheckpointRecover')][string]$Mode='Prep', [switch]$Duel, [switch]$KillHeld, [ValidatePattern('^(|PREPARED|PAYMENT_DURABLE|HELD|ENTITY_DURABLE|CLOSED)$')][string]$TransferStage='')
$ErrorActionPreference='Stop'
if($Mode -eq 'SaberIo' -and $TransferStage -notin @('PREPARED','PAYMENT_DURABLE','HELD','CLOSED')) {throw 'SaberIo requires a supported TransferStage.'}
if($Mode -eq 'SaberPickupCrash' -and -not $TransferStage) {throw 'SaberPickupCrash requires TransferStage.'}
if($TransferStage -and $Mode -notin @('SaberCrash','SaberPickupCrash','SaberIo')) { throw 'TransferStage requires isolated SaberCrash, SaberPickupCrash or SaberIo mode.' }
$repoPath=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot));$runPath=Join-Path $repoPath "run-adventure-$Run"
New-Item -ItemType Directory -Path $runPath -Force | Out-Null
if(-not (Test-Path -LiteralPath (Join-Path $runPath 'server.properties'))) {
@'
server-ip=127.0.0.1
server-port=25586
online-mode=false
enable-rcon=false
spawn-protection=0
level-name=world
level-seed=20261116
level-type=minecraft:normal
difficulty=peaceful
gamemode=survival
view-distance=3
simulation-distance=3
'@ | Set-Content -LiteralPath (Join-Path $runPath 'server.properties') -Encoding ascii
}
if((Get-Content -LiteralPath (Join-Path $runPath 'server.properties')) -notcontains 'server-ip=127.0.0.1') { throw 'Loopback fixture required.' }
if($Mode -ne 'Prep') {
    (Get-Content -LiteralPath (Join-Path $runPath 'server.properties')).Replace('difficulty=peaceful','difficulty=normal') | Set-Content -LiteralPath (Join-Path $runPath 'server.properties') -Encoding ascii
}
$validationFlag=if($Mode -eq 'Prep') {'adventurePrep'} elseif($Mode -eq 'Recovery') {'adventureRecovery'} elseif($Mode -eq 'Progress') {'adventureProgress'} elseif($Mode -eq 'ProgressRecovery') {'adventureProgressRecovery'} elseif($Mode -eq 'Rewards') {'adventureRewards'} elseif($Mode -eq 'RewardsRecovery') {'adventureRewardsRecovery'} elseif($Mode -in @('Block','BlockRecover')) {'adventureBlock'} elseif($Mode -eq 'SaberIo') {'adventureSaberIo'} elseif($Mode -eq 'SaberCheckpointRecover') {'adventureSaberCheckpointRecover'} elseif($Mode -in @('SaberPickup','SaberPickupCrash')) {'adventureSaberPickup'} elseif($Mode -in @('SaberWorkshop','SaberRecover','SaberCrash')) {'adventureSaberWorkshop'} elseif($Mode -eq 'Impulse') {'adventureImpulse'} elseif($Mode -in @('ImpulseCrash','ImpulseRecover','ImpulseUnload')) {'adventureImpulseRestart'} else {'adventureTravel'}
$journeyFlag=if($Mode -eq 'Journey') { "systemProperty 'lotf.validation.adventureJourney', 'true'" } else {''}
$duelFlag=if($Duel) { "systemProperty 'lotf.validation.adventureDuel', 'true'" } else {''}
$blockFlag=if($Mode -eq 'SaberPickupCrash') { "systemProperty 'lotf.validation.saberPickupCrash', 'true'" } elseif($Mode -eq 'SaberCrash') { "systemProperty 'lotf.validation.saberCrash', 'true'" } elseif($Mode -eq 'SaberRecover') { "systemProperty 'lotf.validation.saberRecover', 'true'" } elseif($Mode -eq 'ImpulseUnload') { "systemProperty 'lotf.validation.impulseUnload', 'true'" } elseif($Mode -eq 'ImpulseRecover') { "systemProperty 'lotf.validation.impulseRecover', 'true'" } elseif($KillHeld) { "systemProperty 'lotf.validation.blockCrash', 'true'" } elseif($Mode -eq 'BlockRecover') { "systemProperty 'lotf.validation.blockRecover', 'true'" } else {''}
$checkpointFlag=if($Mode -eq 'SaberIo') { "systemProperty 'lotf.validation.saberIoBoundary', '$TransferStage'" } elseif($TransferStage) { "systemProperty 'lotf.validation.saberCheckpoint', '$TransferStage'" } else {''}
$init=Join-Path $repoPath '.tools/adventure-server.init.gradle'
if($Mode -in @('Block','BlockRecover','Impulse','ImpulseCrash','ImpulseRecover','ImpulseUnload')) {
    $fixtureConfig=Join-Path $runPath 'world/serverconfig';New-Item -ItemType Directory -Path $fixtureConfig -Force | Out-Null
    @'
[rpg]
energyRegenPerSecond = 0.0
'@ | Set-Content -LiteralPath (Join-Path $fixtureConfig 'legacyoftheforce-server.toml') -Encoding ascii
}
if($Mode -in @('ImpulseCrash','ImpulseUnload')) {
    $impulseConfig=Join-Path $runPath 'world/serverconfig/legacyoftheforce-force.toml'
    "[mob]`ncooldownTicks = 1200`n" | Set-Content -LiteralPath $impulseConfig -Encoding ascii
}
if($Mode -in @('Rewards','RewardsRecovery')) {
    $fixtureConfig=Join-Path $runPath 'world/serverconfig';New-Item -ItemType Directory -Path $fixtureConfig -Force | Out-Null
    @'
[rpg]
alignmentThresholds = [-100, -10, 10, 100]
[rpg.farm]
windowTicks = 7200
'@ | Set-Content -LiteralPath (Join-Path $fixtureConfig 'legacyoftheforce-server.toml') -Encoding ascii
}
@"
gradle.projectsEvaluated {
    rootProject.neoForge.runs.named('server') {
        gameDirectory = rootProject.file('run-adventure-$Run')
        systemProperty 'lotf.validation.$validationFlag', 'true'
        systemProperty 'neoforge.enableGameTest', 'true'
        $blockFlag
        $checkpointFlag
        $journeyFlag
        $duelFlag
    }
}
"@ | Set-Content -LiteralPath $init -Encoding ascii
& (Join-Path $PSScriptRoot 'gradle.ps1') runServer --init-script $init --console=plain
exit $LASTEXITCODE
