# Shared helpers for locating the local Mindustry installation.
# Dot-source this file; it defines functions and exports nothing else.
#
# Nothing here is machine specific: the Steam library list is read from the registry and from
# libraryfolders.vdf, so additional Steam libraries on other drives are found too.

function Get-SteamLibraryRoots{
    $roots = @()

    foreach($key in @('HKCU:\Software\Valve\Steam', 'HKLM:\SOFTWARE\WOW6432Node\Valve\Steam', 'HKLM:\SOFTWARE\Valve\Steam')){
        try{
            $entry = Get-ItemProperty -Path $key -ErrorAction Stop
        }catch{
            continue
        }
        foreach($name in @('SteamPath', 'InstallPath')){
            if($entry.PSObject.Properties.Name -contains $name -and $entry.$name){
                $roots += [IO.Path]::GetFullPath($entry.$name)
            }
        }
    }

    # every Steam install lists its libraries, including ones on other drives
    $libraries = @()
    foreach($root in ($roots | Select-Object -Unique)){
        $libraries += $root
        $vdf = Join-Path $root 'steamapps\libraryfolders.vdf'
        if(Test-Path -LiteralPath $vdf){
            foreach($match in ([regex]'"path"\s+"([^"]+)"').Matches((Get-Content -LiteralPath $vdf -Raw))){
                $libraries += $match.Groups[1].Value -replace '\\\\', '\'
            }
        }
    }

    $libraries | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -Unique
}

function Find-MindustryInstall{
    param([string]$Hint)

    $candidates = @()
    if($Hint){ $candidates += $Hint }
    foreach($library in Get-SteamLibraryRoots){
        $candidates += (Join-Path $library 'steamapps\common\Mindustry')
    }
    $candidates += @(
        (Join-Path $env:LOCALAPPDATA 'Programs\Mindustry'),
        (Join-Path $env:ProgramFiles 'Mindustry'),
        (Join-Path ${env:ProgramFiles(x86)} 'Mindustry')
    )

    foreach($candidate in ($candidates | Where-Object { $_ } | Select-Object -Unique)){
        if(-not (Test-Path -LiteralPath $candidate)){ continue }
        $jar = Join-Path $candidate 'jre\desktop.jar'
        $exe = Join-Path $candidate 'Mindustry.exe'
        if((Test-Path -LiteralPath $jar) -or (Test-Path -LiteralPath $exe)){
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    return $null
}

function Get-MindustryDataDir{
    param([Parameter(Mandatory = $true)][string]$InstallPath)

    # Steam builds keep their data next to the executable (Vars.loadSettings uses a local "saves/"
    # folder when Version.isSteam); every other build uses %APPDATA%\Mindustry.
    $portable = Join-Path $InstallPath 'saves'
    $appData = Join-Path $env:APPDATA 'Mindustry'

    $best = $null
    $bestTime = [DateTime]::MinValue
    foreach($candidate in @($portable, $appData)){
        if(-not (Test-Path -LiteralPath $candidate)){ continue }
        # the folder actually in use is the one whose settings were written most recently
        $marker = Get-ChildItem -LiteralPath $candidate -Filter 'settings.bin' -ErrorAction SilentlyContinue |
            Select-Object -First 1
        $time = if($marker){ $marker.LastWriteTime } else { [DateTime]::MinValue }
        if($time -gt $bestTime -or -not $best){
            $best = $candidate
            $bestTime = $time
        }
    }
    return $best
}

function Get-MindustryLauncher{
    param([Parameter(Mandatory = $true)][string]$InstallPath)

    # Prefer the bundled JRE: it lets the smoke test choose its own working directory, and therefore
    # its own data directory, without touching the player's saves or settings.
    $java = Join-Path $InstallPath 'jre\bin\java.exe'
    $jar = Join-Path $InstallPath 'jre\desktop.jar'
    if((Test-Path -LiteralPath $java) -and (Test-Path -LiteralPath $jar)){
        return [pscustomobject]@{
            Kind = 'java'
            Path = $java
            # quoted because a Steam library path usually contains spaces
            Arguments = @('-cp', ('"' + $jar + '"'), 'mindustry.desktop.DesktopLauncher')
        }
    }

    $exe = Join-Path $InstallPath 'Mindustry.exe'
    if(Test-Path -LiteralPath $exe){
        return [pscustomobject]@{ Kind = 'exe'; Path = $exe; Arguments = @() }
    }
    return $null
}
