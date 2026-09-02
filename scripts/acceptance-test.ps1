<#
.SYNOPSIS
    Runs the FactoryScope inspector acceptance suite inside a real Mindustry client.

.DESCRIPTION
    Builds FactoryScope and the acceptance harness, loads both into a throwaway sandbox, and lets the
    harness drive the HUD button and the world clicks through Arc's own input dispatch. The harness
    writes its results to the game log; this script turns them into an exit code.

    The player's saves, settings and installed mods are never read or written.

    Prefer `gradlew acceptanceTest`, which builds the jars first and then calls this.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\acceptance-test.ps1
#>
[CmdletBinding()]
param(
    [string]$MindustryPath,
    [string]$ModJar,
    [switch]$SkipBuild,
    [switch]$KeepSandbox,
    # BCP 47 language tag, e.g. "en-US" or "pt-BR". A fresh sandbox has no language setting, so
    # Mindustry falls back to the JVM default locale and this is enough to steer it.
    [string]$Locale,
    # Writes a PNG of key screens into <sandbox>/Mindustry/shots. Implies -KeepSandbox so they survive.
    [switch]$Capture,
    [int]$TimeoutSeconds = 300
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'mindustry.ps1')

$projectRoot = Split-Path -Parent $PSScriptRoot
$modJar = if($ModJar){ [IO.Path]::GetFullPath($ModJar) }else{ Join-Path $projectRoot 'build\libs\FactoryScopeDesktop.jar' }
$harnessJar = Join-Path $projectRoot 'build\libs\FactoryScopeAcceptance.jar'

function Write-Step($message){ Write-Host "==> $message" -ForegroundColor Cyan }
function Write-Fail($message){ Write-Host "!!! $message" -ForegroundColor Red }

if(-not $SkipBuild){
    Write-Step 'Building FactoryScope and the acceptance harness'
    Push-Location $projectRoot
    try{
        & (Join-Path $projectRoot 'gradlew.bat') jar acceptanceJar
        if($LASTEXITCODE -ne 0){ throw "gradle build failed with exit code $LASTEXITCODE" }
    }finally{
        Pop-Location
    }
}

foreach($jar in @($modJar, $harnessJar)){
    if(-not (Test-Path -LiteralPath $jar)){ throw "expected jar not found: $jar" }
}

# a jar older than its own sources would make the whole run meaningless
function Assert-Fresh($jar, $sources){
    $newest = Get-ChildItem -LiteralPath $sources -Recurse -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if($newest -and $newest.LastWriteTime -gt (Get-Item $jar).LastWriteTime){
        throw "$(Split-Path -Leaf $jar) is older than $($newest.Name); rebuild before running with -SkipBuild"
    }
}
if(-not $ModJar){
    Assert-Fresh $modJar @((Join-Path $projectRoot 'src'), (Join-Path $projectRoot 'assets'), (Join-Path $projectRoot 'mod.hjson'))
}
Assert-Fresh $harnessJar @((Join-Path $projectRoot 'acceptance'))

$running = @(Get-Process -Name 'Mindustry' -ErrorAction SilentlyContinue)
if($running.Count -gt 0){
    throw "Mindustry is already running (pid $($running[0].Id)); close it before running the acceptance suite."
}

Write-Step 'Locating Mindustry'
$gamePath = Find-MindustryInstall -Hint $MindustryPath
if(-not $gamePath){
    throw 'No Mindustry installation found. Pass -MindustryPath with the folder containing Mindustry.exe.'
}
$launcher = Get-MindustryLauncher -InstallPath $gamePath
if(-not $launcher){ throw "No launcher found under $gamePath" }
Write-Host "    install: $gamePath"

$arguments = @($launcher.Arguments)
if($Capture){
    if($launcher.Kind -ne 'java'){
        throw "-Capture needs the bundled JRE launcher; none was found under $gamePath"
    }
    $arguments = @('-Dfactoryscope.capture=true') + $arguments
    $KeepSandbox = $true
}
if($Locale){
    if($launcher.Kind -ne 'java'){
        throw "-Locale needs the bundled JRE launcher; none was found under $gamePath"
    }
    $parts = $Locale -split '[-_]'
    $jvm = @("-Duser.language=$($parts[0])")
    if($parts.Count -gt 1){ $jvm += "-Duser.country=$($parts[1])" }
    $arguments = $jvm + $arguments
    Write-Host "    locale:  $Locale"
}

$sandbox = Join-Path ([IO.Path]::GetTempPath()) ("factoryscope-acceptance-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
$sandboxData = Join-Path $sandbox 'Mindustry'
$sandboxMods = Join-Path $sandboxData 'mods'
New-Item -ItemType Directory -Force -Path $sandboxMods | Out-Null
Copy-Item -LiteralPath $modJar -Destination $sandboxMods -Force
Copy-Item -LiteralPath $harnessJar -Destination $sandboxMods -Force

# The Steam desktop jar enables Steam solely from this classpath resource. Running it as a Steam client
# also imports subscribed Workshop mods, which is outside the sandbox. The release modifier keeps the
# same client code while skipping Steam initialization and its Workshop inventory.
Set-Content -LiteralPath (Join-Path $sandbox 'version.properties') -Value @(
    'number=8',
    'build=159.7',
    'modifier=release',
    'type=official',
    'commitHash=unknown'
) -Encoding ascii

$logFile = Join-Path $sandboxData 'last_log.txt'
Write-Step "Running the acceptance suite in $sandbox"

$previousAppData = $env:APPDATA
try{
    $env:APPDATA = $sandbox
    $process = Start-Process -FilePath $launcher.Path -ArgumentList $arguments `
        -WorkingDirectory $sandbox -PassThru -WindowStyle Minimized
}finally{
    $env:APPDATA = $previousAppData
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$finished = $false

try{
    while((Get-Date) -lt $deadline){
        if(Test-Path -LiteralPath $logFile){
            $log = Get-Content -LiteralPath $logFile -Raw -ErrorAction SilentlyContinue
            if($log -and ($log.Contains('RESULT PASS') -or $log.Contains('RESULT FAIL'))){
                $finished = $true
                break
            }
        }
        if($process.HasExited){
            Write-Fail "The game exited with code $($process.ExitCode) before the suite finished."
            break
        }
        Start-Sleep -Milliseconds 500
    }
}finally{
    if(-not $process.HasExited){
        $process.CloseMainWindow() | Out-Null
        if(-not $process.WaitForExit(10000)){ $process.Kill() }
    }
}

if(-not (Test-Path -LiteralPath $logFile)){
    Write-Fail "No log was produced at $logFile"
    exit 1
}

$lines = @(Get-Content -LiteralPath $logFile -Encoding UTF8 | Where-Object { $_ -match '\[HARNESS\]' })
$lines | ForEach-Object { Write-Host $_ }

$passed = $finished -and @($lines | Where-Object { $_ -match 'RESULT PASS' }).Count -gt 0
if(-not $finished){
    Write-Fail "The acceptance suite did not finish within $TimeoutSeconds seconds."
}

Write-Host ''
if($passed){
    Write-Host 'ACCEPTANCE SUITE PASSED' -ForegroundColor Green
}else{
    Write-Fail 'ACCEPTANCE SUITE FAILED'
}

if($passed -and -not $KeepSandbox){
    Copy-Item -LiteralPath $logFile -Destination (Join-Path $projectRoot 'build\acceptance-test.log') -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}else{
    Write-Host "Full log: $logFile"
}

exit ([int](-not $passed))
