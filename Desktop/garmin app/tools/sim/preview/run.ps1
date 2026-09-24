param([string[]]$Devices, [string]$Phase = "all", [switch]$All, [switch]$Force)
# Preview harness — worst-case text-fit check across devices.
# Builds each device ONCE from a %TEMP% copy of the project (real tree untouched;
# monkeyc compiles every .mc under the project, so the harness lives in *.mc.txt)
# as an auto-launching watch-app whose view (CyclePreview.mc.txt) steps through:
#   glance + date page on the longest date (כ״ט אדר א׳), parasha page on
#   אחרי מות־קדושים and on the longest countdown (ראש חודש אדר א׳ בעוד י״ד ימים),
#   plus the Solar Omer/events page on both. The glance is the REAL glance view
#   drawn into an offscreen bitmap sized to the device's glance contentArea (red
#   outline). The view prints "STATE n"; we capture each state to
#   bin\preview\shots\<device>_<n>.png. Contact sheet: python sheet.py <devices...>
# Usage (needs dangerouslyDisableSandbox):
#   .\tools\sim\preview\run.ps1 -Devices fenix7s,venu2s
#   .\tools\sim\preview\run.ps1 -All            (every product in manifest.xml)
# Restart the simulator first if it has run other builds of this app (stale font
# cache shows garbled glyphs).
$ErrorActionPreference = "Continue"
$proj  = "C:\Users\yosef\Desktop\garmin app"
$here  = Split-Path -Parent $MyInvocation.MyCommand.Path
$work  = "$env:TEMP\hebcal-preview\proj"
$shots = "$proj\bin\preview\shots"
$devDir = "C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Devices"
New-Item -ItemType Directory -Force $shots | Out-Null
if ($All) {
    $Devices = [regex]::Matches((Get-Content "$proj\manifest.xml" -Raw), 'product id="([^"]+)"') |
        ForEach-Object { $_.Groups[1].Value }
}

if ($Phase -eq "all" -or $Phase -eq "build") {
    if (Test-Path $work) { Remove-Item -Recurse -Force $work }
    New-Item -ItemType Directory -Force $work | Out-Null
    Get-ChildItem $proj -Directory | Where-Object { $_.Name -like "resources*" -or $_.Name -eq "source" } |
        ForEach-Object { Copy-Item -Recurse $_.FullName "$work\$($_.Name)" }
    Copy-Item "$proj\monkey.jungle","$proj\manifest.xml","$proj\developer_key.der" $work
    Copy-Item "$here\CyclePreview.mc.txt" "$work\source\CyclePreview.mc"
    $m = Get-Content "$work\manifest.xml" -Raw
    ($m -replace 'type="widget"', 'type="watch-app"') | Set-Content "$work\manifest.xml" -NoNewline
    $a = "$work\source\HebrewCalendarApp.mc"
    $src = Get-Content $a -Raw
    $src = $src -replace '\(:glance\)\r?\n    function getGlanceView', "//(:glance)`n    private function unused_getGlanceView"
    $src = $src -replace 'var view = new HebrewCalendarView\(\);', 'return [new CyclePreviewView()]; var view = new HebrewCalendarView();'
    Set-Content $a $src -NoNewline
    foreach ($f in @("HebrewCalendarView.mc","ParashaView.mc","OmerView.mc","HebrewCalendarGlanceView.mc")) {
        $p = "$work\source\$f"
        (Get-Content $p -Raw) -replace 'Time\.now\(\)', 'PreviewDims.now()' | Set-Content $p -NoNewline
    }
    Push-Location $work
    foreach ($d in $Devices) {
        $sim = Get-Content "$devDir\$d\simulator.json" -Raw | ConvertFrom-Json
        $ca = $sim.glance.contentArea
        "import Toybox.Lang;`n(:glance)`nmodule PreviewDims { const W = $($ca.width); const H = $($ca.height); }" |
            Set-Content "$work\source\PreviewSize.mc"
        $log = monkeyc -o "p_$d.prg" -f monkey.jungle -y developer_key.der -d $d 2>&1
        if ($LASTEXITCODE -ne 0) { Write-Output "BUILD FAIL $d"; $log | Select-Object -Last 5 }
        else { Write-Output "built $d" }
    }
    Pop-Location
}

if ($Phase -eq "all" -or $Phase -eq "shoot") {
    # Robust capture: fresh simulator per device, every capture in a job with a
    # timeout (PrintWindow can hang), up to 3 attempts, and RESUMABLE — devices
    # whose shots all exist are skipped (pass -Force to redo). Failures are
    # listed at the end and in bin\preview\failed.txt.
    $simExe = "C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Sdks\connectiq-sdk-win-9.1.0-2026-03-09-6a872a80b\bin\simulator.exe"
    $failed = @()
    function Stop-Sim {
        Get-CimInstance Win32_Process -Filter "Name='cmd.exe'" | Where-Object { $_.CommandLine -like '*monkeydo*' } |
            ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        Get-Process monkeydo, simulator -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
        Start-Sleep 1
    }
    function Start-Sim {
        Start-Process $simExe
        $t = Get-Date
        while (((Get-Date) - $t).TotalSeconds -lt 30) {
            $p = Get-Process simulator -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 }
            if ($p) { Start-Sleep 3; return $true }
            Start-Sleep -Milliseconds 500
        }
        return $false
    }
    foreach ($d in $Devices) {
        $solar = ((Get-Content "$devDir\$d\compiler.json" -Raw | ConvertFrom-Json).resolution.width -le 176)
        $n = if ($solar) { 7 } else { 5 }
        $have = @(0..($n - 1) | Where-Object { Test-Path "$shots\${d}_$_.png" }).Count
        if ($have -eq $n -and -not $Force) { Write-Output "skip $d (done)"; continue }
        $ok = $false
        for ($attempt = 1; $attempt -le 3 -and -not $ok; $attempt++) {
            Stop-Sim
            if (-not (Start-Sim)) { Write-Output "  $d attempt ${attempt}: simulator did not start"; continue }
            $log = "$env:TEMP\hebcal-preview\log_$d.txt"
            if (Test-Path $log) { Remove-Item $log -Force }
            Start-Process cmd -ArgumentList "/c monkeydo `"$work\p_$d.prg`" $d > `"$log`" 2>&1" -WindowStyle Hidden
            $got = 0; $t0 = Get-Date; $bad = $false
            while ($got -lt $n -and -not $bad -and ((Get-Date) - $t0).TotalSeconds -lt 75) {
                Start-Sleep -Milliseconds 300
                $txt = if (Test-Path $log) { Get-Content $log -Raw -ErrorAction SilentlyContinue } else { "" }
                if ($txt -match "STATE $got\b") {
                    Start-Sleep -Milliseconds 800
                    $out = "$shots\${d}_$got.png"
                    $job = Start-Job -ScriptBlock { param($c, $o) & $c -OutFile $o } -ArgumentList "$proj\tools\sim\capture2.ps1", $out
                    if (Wait-Job $job -Timeout 12) { Receive-Job $job | Out-Null } else { $bad = $true }
                    Remove-Job $job -Force
                    if (-not $bad -and (Test-Path $out)) {
                        # every state differs from the previous one — an identical
                        # image means a stale frame: wait and capture this state again
                        $prev = "$shots\${d}_$($got - 1).png"
                        if ($got -gt 0 -and (Test-Path $prev) -and
                            (Get-FileHash $out).Hash -eq (Get-FileHash $prev).Hash) {
                            Start-Sleep -Milliseconds 700
                            Remove-Item $out -Force
                            continue
                        }
                        $got++
                    } else { $bad = $true }
                }
            }
            if ($got -eq $n) { $ok = $true } else { Write-Output "  $d attempt $attempt failed ($got/$n)" }
        }
        if ($ok) { Write-Output "shot $d ($n states)" } else { $failed += $d; Write-Output "FAILED $d" }
    }
    Stop-Sim
    $failed | Set-Content "$proj\bin\preview\failed.txt"
    Write-Output "ALLDONE failed=$($failed.Count) $($failed -join ',')"
}
