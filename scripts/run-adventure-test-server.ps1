param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run='ap1', [ValidateSet('Prep','Travel','Journey')][string]$Mode='Prep')
$ErrorActionPreference='Stop'
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
$validationFlag=if($Mode -eq 'Prep') {'adventurePrep'} else {'adventureTravel'}
$journeyFlag=if($Mode -eq 'Journey') { "systemProperty 'lotf.validation.adventureJourney', 'true'" } else {''}
$init=Join-Path $repoPath '.tools/adventure-server.init.gradle'
@"
gradle.projectsEvaluated {
    rootProject.neoForge.runs.named('server') {
        gameDirectory = rootProject.file('run-adventure-$Run')
        systemProperty 'lotf.validation.$validationFlag', 'true'
        systemProperty 'neoforge.enableGameTest', 'true'
        $journeyFlag
    }
}
"@ | Set-Content -LiteralPath $init -Encoding ascii
& (Join-Path $PSScriptRoot 'gradle.ps1') runServer --init-script $init --console=plain
exit $LASTEXITCODE
