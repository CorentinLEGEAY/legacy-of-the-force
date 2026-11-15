param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run = 'boss1')
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot)); $runPath = Join-Path $repoPath "run-boss-$Run"
New-Item -ItemType Directory -Path $runPath -Force | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $runPath 'server.properties'))) {
@'
server-ip=127.0.0.1
server-port=25588
online-mode=false
enable-rcon=false
spawn-protection=0
level-name=world
level-seed=20261115
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
difficulty=normal
gamemode=survival
view-distance=3
simulation-distance=3
'@ | Set-Content -LiteralPath (Join-Path $runPath 'server.properties') -Encoding ascii
}
if ((Get-Content -LiteralPath (Join-Path $runPath 'server.properties')) -notcontains 'server-ip=127.0.0.1') { throw 'Loopback fixture required.' }
$init = Join-Path $repoPath '.tools/boss-server.init.gradle'
@"
gradle.projectsEvaluated {
    rootProject.neoForge.runs.named('server') {
        gameDirectory = rootProject.file('run-boss-$Run')
        systemProperty 'lotf.validation.bossDedicated', 'true'
        systemProperty 'neoforge.enableGameTest', 'true'
    }
}
"@ | Set-Content -LiteralPath $init -Encoding ascii
& (Join-Path $PSScriptRoot 'gradle.ps1') runServer --init-script $init --console=plain
exit $LASTEXITCODE
