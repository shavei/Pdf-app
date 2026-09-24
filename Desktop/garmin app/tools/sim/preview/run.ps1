param([string[]]$Devices, [string]$Phase = "all")
# Preview harness: builds each device twice from a scratch COPY of the project
# in %TEMP%\hebcal-preview (never touches the real tree) as a watch-app that
# auto-launches, then captures glance / date / parasha / page3 (Solar) to
# bin\preview\shots. The "glance" build draws the REAL glance view into an
# offscreen bitmap sized to the device's glance contentArea (red outline), so
# no carousel navigation is needed. Contact sheet: python sheet.py <devices...>
# Restart the simulator first if it has run other builds of this app (stale
# font cache shows garbled glyphs). Usage:
#   .\tools\sim\preview\run.ps1 -Devices fenix7s,venu2s   (needs dangerouslyDisableSandbox)
$ErrorActionPreference = "Continue"
$proj = "C:\Users\yosef\Desktop\garmin app"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$work = "$env:TEMP\hebcal-preview\proj"   # OUTSIDE the project: monkeyc compiles every .mc under it
$shots = "$proj\bin\preview\shots"
$devDir = "C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Devices"
New-Item -ItemType Directory -Force $shots | Out-Null
Add-Type -AssemblyName System.Windows.Forms

if ($Phase -eq "all" -or $Phase -eq "build") {
    if (Test-Path $work) { Remove-Item -Recurse -Force $work }
    New-Item -ItemType Directory $work | Out-Null
    Get-ChildItem $proj -Directory | Where-Object { $_.Name -like "resources*" -or $_.Name -eq "source" } |
        ForEach-Object { Copy-Item -Recurse $_.FullName "$work\$($_.Name)" }
    Copy-Item "$proj\monkey.jungle","$proj\manifest.xml","$proj\developer_key.der" $work
    Copy-Item "$here\GlancePreview.mc.txt" "$work\source\GlancePreview.mc"
    $m = Get-Content "$work\manifest.xml" -Raw
    ($m -replace 'type="widget"', 'type="watch-app"') | Set-Content "$work\manifest.xml" -NoNewline
    $a = "$work\source\HebrewCalendarApp.mc"
    $src = Get-Content $a -Raw
    $src = $src -replace '\(:glance\)\r?\n    function getGlanceView', "//(:glance)`n    private function unused_getGlanceView"
    $src = $src -replace 'var view = new HebrewCalendarView\(\);', 'if (PreviewDims.GLANCE) { return [new GlancePreviewView()]; } var view = new HebrewCalendarView();'
    Set-Content $a $src -NoNewline
    Push-Location $work
    foreach ($d in $Devices) {
        $sim = Get-Content "$devDir\$d\simulator.json" -Raw | ConvertFrom-Json
        $ca = $sim.glance.contentArea
        foreach ($g in @("true","false")) {
            "import Toybox.Lang;`nmodule PreviewDims { const W = $($ca.width); const H = $($ca.height); const GLANCE = $g; }" |
                Set-Content "$work\source\PreviewDims.mc"
            $out = if ($g -eq "true") { "g_$d.prg" } else { "w_$d.prg" }
            $log = monkeyc -o $out -f monkey.jungle -y developer_key.der -d $d 2>&1
            if ($LASTEXITCODE -ne 0) { Write-Output "BUILD FAIL $out"; $log | Select-Object -Last 5 } else { Write-Output "built $out" }
        }
    }
    Pop-Location
}

if ($Phase -eq "all" -or $Phase -eq "shoot") {
    foreach ($d in $Devices) {
        $sim = Get-Content "$devDir\$d\simulator.json" -Raw | ConvertFrom-Json
        $touch = [bool]$sim.display.isTouch
        $solar = ((Get-Content "$devDir\$d\compiler.json" -Raw | ConvertFrom-Json).resolution.width -le 176)
        Start-Process cmd -ArgumentList "/c monkeydo `"$work\g_$d.prg`" $d" -WindowStyle Hidden
        Start-Sleep 16
        & "$proj\tools\sim\capture2.ps1" -OutFile "$shots\${d}_1glance.png" | Out-Null
        Start-Process cmd -ArgumentList "/c monkeydo `"$work\w_$d.prg`" $d" -WindowStyle Hidden
        Start-Sleep 14
        & "$proj\tools\sim\capture2.ps1" -OutFile "$shots\${d}_2date.png" | Out-Null
        if ($touch) {
            & "$proj\tools\sim\click.ps1" -X 250 -Y 400 | Out-Null
        } else {
            & "$proj\tools\sim\click.ps1" -X 250 -Y 350 | Out-Null; Start-Sleep 1
            [System.Windows.Forms.SendKeys]::SendWait('{ENTER}'); Start-Sleep 1
            [System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
        }
        Start-Sleep 2
        & "$proj\tools\sim\capture2.ps1" -OutFile "$shots\${d}_3parasha.png" | Out-Null
        if ($solar) {
            [System.Windows.Forms.SendKeys]::SendWait('{ENTER}'); Start-Sleep 2
            & "$proj\tools\sim\capture2.ps1" -OutFile "$shots\${d}_4omer.png" | Out-Null
        }
        Write-Output "shot $d (touch=$touch solar=$solar)"
    }
}
