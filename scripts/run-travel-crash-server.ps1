param()
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$runPath = Join-Path $repoPath 'run-travel-crash'
New-Item -ItemType Directory -Path $runPath -Force | Out-Null
if ((Test-Path -LiteralPath (Join-Path $repoPath 'run/eula.txt')) -and -not (Test-Path -LiteralPath (Join-Path $runPath 'eula.txt'))) {
    Copy-Item -LiteralPath (Join-Path $repoPath 'run/eula.txt') -Destination (Join-Path $runPath 'eula.txt')
}
if (-not (Test-Path -LiteralPath (Join-Path $runPath 'server.properties'))) {
    @'
server-ip=127.0.0.1
server-port=25586
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
$initPath = Join-Path $repoPath '.tools/travel-crash.init.gradle'
@'
gradle.projectsEvaluated {
    rootProject.neoForge.runs.named('server') {
        gameDirectory = rootProject.file('run-travel-crash')
        systemProperty 'lotf.validation.travelCrash', 'true'
    }
}
'@ | Set-Content -LiteralPath $initPath -Encoding ascii
& (Join-Path $PSScriptRoot 'gradle.ps1') runServer --init-script $initPath --console=plain
exit $LASTEXITCODE
