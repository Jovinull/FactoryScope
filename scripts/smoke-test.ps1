<#
.SYNOPSIS
    Builds FactoryScope, loads it in the real Mindustry client and reports what the game log says.

.DESCRIPTION
    The client is started with its working directory pointed at a throwaway sandbox, so it creates its
    own settings, mods folder and log there. The player's saves, schematics and installed mods are
    never read or written.

    The test succeeds when the log shows FactoryScope initialising and contains no FactoryScope error.

.PARAMETER Install
    Additionally copy the built jar into the real Mindustry mods folder, replacing only a previous
    FactoryScope artifact.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1 -Install
#>
[CmdletBinding()]
param(
    [string]$MindustryPath,
    [switch]$SkipBuild,
    [switch]$Install,
    [switch]$KeepSandbox,
    [int]$TimeoutSeconds = 240
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'mindustry.ps1')

$projectRoot = Split-Path -Parent $PSScriptRoot
$jarName = 'FactoryScopeDesktop.jar'
$builtJar = Join-Path $projectRoot "build\libs\$jarName"

function Write-Step($message){ Write-Host "==> $message" -ForegroundColor Cyan }
function Write-Fail($message){ Write-Host "!!! $message" -ForegroundColor Red }

# --------------------------------------------------------------------------- build

if(-not $SkipBuild){
    Write-Step 'Building FactoryScope'
    Push-Location $projectRoot
    try{
        & (Join-Path $projectRoot 'gradlew.bat') clean test jar
        if($LASTEXITCODE -ne 0){ throw "gradle build failed with exit code $LASTEXITCODE" }
    }finally{
        Pop-Location
    }
}

if(-not (Test-Path -LiteralPath $builtJar)){ throw "expected jar not found: $builtJar" }

$jarInfo = Get-Item $builtJar
$declaredVersion = (Select-String -LiteralPath (Join-Path $projectRoot 'mod.hjson') -Pattern '^\s*version:\s*"([^"]+)"').Matches[0].Groups[1].Value

# a jar older than the sources it is built from would make the whole run meaningless
$newestSource = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src'), (Join-Path $projectRoot 'assets') -Recurse -File |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if($newestSource -and $newestSource.LastWriteTime -gt $jarInfo.LastWriteTime){
    throw "the jar is older than $($newestSource.Name); rebuild before running with -SkipBuild"
}

Write-Host "    jar: $builtJar ($([math]::Round($jarInfo.Length / 1KB)) KB, version $declaredVersion)"

# --------------------------------------------------------------------------- discover

$running = @(Get-Process -Name 'Mindustry' -ErrorAction SilentlyContinue)
if($running.Count -gt 0){
    throw "Mindustry is already running (pid $($running[0].Id)); close it so the smoke test cannot be confused by it."
}

Write-Step 'Locating Mindustry'
$gamePath = Find-MindustryInstall -Hint $MindustryPath
if(-not $gamePath){
    throw 'No Mindustry installation found. Pass -MindustryPath with the folder containing Mindustry.exe.'
}
$launcher = Get-MindustryLauncher -InstallPath $gamePath
if(-not $launcher){ throw "No launcher found under $gamePath" }
$dataDir = Get-MindustryDataDir -InstallPath $gamePath

Write-Host "    install:  $gamePath"
Write-Host "    launcher: $($launcher.Path)"
Write-Host "    data dir: $dataDir"

# --------------------------------------------------------------------------- install

if($Install){
    if(-not $dataDir){ throw 'Could not determine the Mindustry data directory.' }
    $modsDir = Join-Path $dataDir 'mods'
    New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
    # replace only our own artifact; anything else in the folder belongs to the player
    Copy-Item -LiteralPath $builtJar -Destination (Join-Path $modsDir $jarName) -Force
    Write-Step "Installed into $modsDir"
}

# --------------------------------------------------------------------------- sandbox

$sandbox = Join-Path ([IO.Path]::GetTempPath()) ("factoryscope-smoke-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
$sandboxData = Join-Path $sandbox 'saves'
$sandboxMods = Join-Path $sandboxData 'mods'
New-Item -ItemType Directory -Force -Path $sandboxMods | Out-Null
Copy-Item -LiteralPath $builtJar -Destination (Join-Path $sandboxMods $jarName) -Force

# stops the Steam build from asking Steam to relaunch the game outside our sandbox
Set-Content -LiteralPath (Join-Path $sandbox 'steam_appid.txt') -Value '1127400' -Encoding ascii

$logFile = Join-Path $sandboxData 'last_log.txt'
Write-Step "Starting Mindustry in sandbox $sandbox"

$started = Get-Date
$process = Start-Process -FilePath $launcher.Path -ArgumentList $launcher.Arguments `
    -WorkingDirectory $sandbox -PassThru -WindowStyle Minimized

# --------------------------------------------------------------------------- watch the log

# bound to the version being tested: a log left by any other build cannot satisfy it
$readySignal = "[FactoryScope] $declaredVersion inspector ready"
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$ready = $false

try{
    while((Get-Date) -lt $deadline){
        if($process.HasExited){
            Write-Fail "The game exited after $([int]((Get-Date) - $started).TotalSeconds)s with code $($process.ExitCode)."
            break
        }
        if(Test-Path -LiteralPath $logFile){
            $log = Get-Content -LiteralPath $logFile -Raw -ErrorAction SilentlyContinue
            if($log -and $log.Contains($readySignal)){
                $ready = $true
                Start-Sleep -Seconds 3   # let any late initialisation error reach the log
                break
            }
        }
        Start-Sleep -Milliseconds 500
    }
}finally{
    if(-not $process.HasExited){
        Write-Step 'Stopping the test instance'
        $process.CloseMainWindow() | Out-Null
        if(-not $process.WaitForExit(10000)){ $process.Kill() }
    }
}

# --------------------------------------------------------------------------- verdict

if(-not (Test-Path -LiteralPath $logFile)){
    Write-Fail "No log was produced at $logFile"
    exit 1
}

$logLines = Get-Content -LiteralPath $logFile

Write-Step 'FactoryScope log lines'
$modLines = @($logLines | Where-Object { $_ -match 'FactoryScope|factory-scope' })
if($modLines.Count -gt 0){ $modLines | ForEach-Object { Write-Host "    $_" } } else { Write-Host '    (none)' }

$problemPattern = 'NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|NoSuchFieldError|' +
    'IncompatibleClassChangeError|UnsupportedClassVersionError|Failed to load mod|Error loading bundle'

$problems = @($logLines | Where-Object { $_ -match $problemPattern })
$modErrors = @($logLines | Where-Object { $_ -match 'factoryscope\.' -and $_ -match 'at |Exception|Error' })

Write-Host ''
if(-not $ready){
    Write-Fail 'FactoryScope did not report that it initialised within the timeout.'
}
if($problems.Count -gt 0){
    Write-Fail 'Suspicious log lines:'
    $problems | ForEach-Object { Write-Host "    $_" }
}
if($modErrors.Count -gt 0){
    Write-Fail 'FactoryScope stack frames in the log:'
    $modErrors | ForEach-Object { Write-Host "    $_" }
}

$success = $ready -and $problems.Count -eq 0 -and $modErrors.Count -eq 0
if($success){
    Write-Host 'SMOKE TEST PASSED: FactoryScope loaded cleanly.' -ForegroundColor Green
}else{
    Write-Fail 'SMOKE TEST FAILED'
}

Write-Host ''
if($success -and -not $KeepSandbox){
    Copy-Item -LiteralPath $logFile -Destination (Join-Path $projectRoot 'build\smoke-test.log') -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "Log kept at $(Join-Path $projectRoot 'build\smoke-test.log'); sandbox removed."
}else{
    Write-Host "Full log: $logFile"
    Write-Host 'The sandbox has been left in place for inspection.'
}

exit ([int](-not $success))
