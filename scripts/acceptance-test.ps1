<#
.SYNOPSIS
    Windows compatibility wrapper for the portable Gradle acceptance launcher.

.DESCRIPTION
    The Java launcher owns Mindustry discovery, isolation, process handling and log validation. New
    scripts should call `gradlew acceptanceTest` directly on every platform.
#>
[CmdletBinding()]
param(
    [string]$MindustryPath,
    [string]$MindustryJar,
    [string]$ModJar,
    [string]$Locale,
    [switch]$Capture,
    [switch]$KeepSandbox,
    [int]$TimeoutSeconds = 300
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $projectRoot 'gradlew.bat'
$arguments = @('acceptanceTest')
if($MindustryPath){ $arguments += "-PmindustryPath=$MindustryPath" }
if($MindustryJar){ $arguments += "-PmindustryJar=$MindustryJar" }
if($ModJar){ $arguments += "-PmodJar=$ModJar" }
if($Locale){ $arguments += "-Plocale=$Locale" }
if($Capture){ $arguments += '-Pcapture=true' }
if($KeepSandbox){ $arguments += '-PkeepSandbox=true' }
if($TimeoutSeconds -ne 300){ $arguments += "-PtimeoutSeconds=$TimeoutSeconds" }

Push-Location $projectRoot
try{
    & $gradle @arguments
    exit $LASTEXITCODE
}finally{
    Pop-Location
}
