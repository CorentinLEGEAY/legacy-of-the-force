param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run = 'net1', [ValidateSet('None','PAID','TELEPORTED','ACTIVE_DURABLE')][string]$DropCheckpoint = 'None')
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$runPath = Join-Path $repoPath "run-travel-network-$Run"
New-Item -ItemType Directory -Path $runPath -Force | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $runPath 'server.properties'))) {
    @'
server-ip=127.0.0.1
server-port=25587
online-mode=false
enable-rcon=false
spawn-protection=0
level-name=world
level-seed=20261115
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=true
gamemode=survival
difficulty=peaceful
max-players=2
view-distance=3
simulation-distance=3
'@ | Set-Content -LiteralPath (Join-Path $runPath 'server.properties') -Encoding ascii
}
if ((Get-Content -LiteralPath (Join-Path $runPath 'server.properties')) -notcontains 'server-ip=127.0.0.1') { throw 'Validation must bind only to loopback.' }
$initPath = Join-Path $repoPath '.tools/travel-network-server.init.gradle'
@"
gradle.projectsEvaluated {
    rootProject.neoForge.runs.named('server') {
        gameDirectory = rootProject.file('run-travel-network-$Run')
        systemProperty 'lotf.validation.travelNetwork', 'true'
        systemProperty 'lotf.validation.travelDisconnect', '$DropCheckpoint'
    }
}
"@ | Set-Content -LiteralPath $initPath -Encoding ascii
& (Join-Path $PSScriptRoot 'gradle.ps1') runServer --init-script $initPath --console=plain
exit $LASTEXITCODE
