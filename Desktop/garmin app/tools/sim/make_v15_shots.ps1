# Capture glance + widget date page + widget parasha page for all 16 devices.
# Output: bin\shots\v15\<device>_{glance,widget_date,widget_parasha}.png
#
# Glance comes from the real widget build (bin\<dev>.prg) — the sim shows the
# glance carousel after monkeydo. Widget pages come from an auto-launching
# watch-app preview build (bin\preview_<dev>.prg) because opening widgets from
# the glance is unreliable per profile (App Lock, key quirks — see MEMORY.md).
# Builds the preview variants first (temporarily flips manifest/app, restores
# via git), then loops devices. ENTER advances to the parasha page; if the
# capture didn't change, ENTER is retried up to 3 times.

$ErrorActionPreference = "Continue"
$proj  = "C:\Users\yosef\Desktop\garmin app"
$shots = "$proj\bin\shots\v15"
New-Item -ItemType Directory -Force $shots | Out-Null
Set-Location $proj

$devices = @("epix2","fenix7","fenix847mm","fr165m","fr255","fr265","fr55","fr955",
             "fr965","instinct2","instinct3amoled45mm","instinct3amoled50mm",
             "instinct3solar45mm","venu2","venu3","vivoactive5")

Add-Type -AssemblyName System.Windows.Forms
$sig = @"
using System; using System.Runtime.InteropServices;
public class W32Shot { [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h); }
"@
try { Add-Type $sig } catch {}

function Fg-Sim {
    $p = Get-Process simulator -ErrorAction SilentlyContinue |
         Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if ($p) { [W32Shot]::SetForegroundWindow($p.MainWindowHandle) | Out-Null; Start-Sleep -Milliseconds 400 }
}

# ---- Phase 1: build auto-launching preview variants -------------------------
Write-Output "phase1: building preview variants"
$m = "$proj\manifest.xml"
(Get-Content $m -Raw) -replace 'type="widget"', 'type="watch-app"' | Set-Content $m -NoNewline
$a = "$proj\source\HebrewCalendarApp.mc"
$src = Get-Content $a -Raw
$src = $src -replace '\(:glance\)\r?\n    function getGlanceView', "//(:glance)`n    private function unused_getGlanceView"
Set-Content $a $src -NoNewline

foreach ($d in $devices) {
    if (-not (Test-Path "$proj\bin\preview_$d.prg")) {
        monkeyc -o "bin\preview_$d.prg" -f monkey.jungle -y "developer_key.der" -d $d 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Output "BUILD FAIL preview_$d" }
    }
}
git -C "C:\Users\yosef" checkout -- "Desktop/garmin app/manifest.xml" "Desktop/garmin app/source/HebrewCalendarApp.mc"
Write-Output "phase1 done (source restored)"

# ---- Phase 2: capture loop ---------------------------------------------------
if (-not (Get-Process simulator -ErrorAction SilentlyContinue)) {
    Start-Process "C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Sdks\connectiq-sdk-win-9.1.0-2026-03-09-6a872a80b\bin\simulator.exe"
    Start-Sleep 12
}

foreach ($d in $devices) {
    # -- glance (real widget build)
    Start-Process cmd -ArgumentList "/c monkeydo `"$proj\bin\$d.prg`" $d" -WindowStyle Hidden
    Start-Sleep 16
    if ($d -eq "fr55") {  # boots to watchface; DOWN reaches the glance carousel
        & "$proj\tools\sim\click.ps1" -X 250 -Y 400 | Out-Null; Start-Sleep 1
        Fg-Sim; [System.Windows.Forms.SendKeys]::SendWait('{DOWN}'); Start-Sleep 2
    }
    & "$proj\tools\sim\capture.ps1" -OutFile "$shots\${d}_glance.png" | Out-Null

    # -- widget pages (auto-launching preview build)
    Start-Process cmd -ArgumentList "/c monkeydo `"$proj\bin\preview_$d.prg`" $d" -WindowStyle Hidden
    Start-Sleep 16
    & "$proj\tools\sim\click.ps1" -X 250 -Y 400 | Out-Null; Start-Sleep 1
    & "$proj\tools\sim\capture.ps1" -OutFile "$shots\${d}_widget_date.png" | Out-Null

    $dateHash = (Get-FileHash "$shots\${d}_widget_date.png" -Algorithm MD5).Hash
    for ($try = 1; $try -le 3; $try++) {
        Fg-Sim
        [System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
        Start-Sleep 3
        & "$proj\tools\sim\capture.ps1" -OutFile "$shots\${d}_widget_parasha.png" | Out-Null
        $parashaHash = (Get-FileHash "$shots\${d}_widget_parasha.png" -Algorithm MD5).Hash
        if ($parashaHash -ne $dateHash) { break }
        Write-Output "$d retry ENTER ($try)"
    }
    Write-Output "$d done"
}
Write-Output "ALL DONE -> $shots"
