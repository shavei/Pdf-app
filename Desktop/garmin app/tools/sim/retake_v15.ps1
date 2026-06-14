# Retake v1.5.0 screenshots after the layout fixes (lowered parasha page,
# round-screen glance inset). Date-page shots are unchanged by the fixes and
# are NOT retaken. Glances are captured in the carousel's FOCUSED slot
# (margin-click + DOWN; fr55 boots to watchface and needs display-click + 2x DOWN).
# Parasha: touch devices tap once from the date page; button devices use ENTER.
param([string]$Phase = "all")

$ErrorActionPreference = "Continue"
$proj  = "C:\Users\yosef\Desktop\garmin app"
$shots = "$proj\bin\shots\v15"
Set-Location $proj

$devices  = @("epix2","fenix7","fenix847mm","fr165m","fr255","fr265","fr55","fr955",
              "fr965","instinct2","instinct3amoled45mm","instinct3amoled50mm",
              "instinct3solar45mm","venu2","venu3","vivoactive5")
$touch    = @("epix2","fenix7","fenix847mm","fr165m","fr265","fr955","fr965",
              "venu2","venu3","vivoactive5")
$nonTouchParasha = @("fr55","fr255","instinct3amoled45mm","instinct3amoled50mm")

Add-Type -AssemblyName System.Windows.Forms

function Send-Key([string]$k) {
    [System.Windows.Forms.SendKeys]::SendWait($k)
}

if ($Phase -eq "all" -or $Phase -eq "build") {
    Write-Output "== rebuilding real builds =="
    foreach ($d in $devices) {
        monkeyc -o "bin\$d.prg" -f monkey.jungle -y "developer_key.der" -d $d 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Output "BUILD FAIL $d" } else { Write-Output "built $d" }
    }
    Write-Output "== rebuilding preview builds =="
    $m = "$proj\manifest.xml"
    (Get-Content $m -Raw) -replace 'type="widget"', 'type="watch-app"' | Set-Content $m -NoNewline
    $a = "$proj\source\HebrewCalendarApp.mc"
    $src = Get-Content $a -Raw
    $src = $src -replace '\(:glance\)\r?\n    function getGlanceView', "//(:glance)`n    private function unused_getGlanceView"
    Set-Content $a $src -NoNewline
    foreach ($d in $devices) {
        monkeyc -o "bin\preview_$d.prg" -f monkey.jungle -y "developer_key.der" -d $d 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Output "BUILD FAIL preview_$d" } else { Write-Output "built preview_$d" }
    }
    git -C "C:\Users\yosef" checkout -- "Desktop/garmin app/manifest.xml" "Desktop/garmin app/source/HebrewCalendarApp.mc"
    Write-Output "== builds done (source restored) =="
}

if ($Phase -eq "all" -or $Phase -eq "glance") {
    Write-Output "== glance captures (focused slot) =="
    foreach ($d in $devices) {
        Start-Process cmd -ArgumentList "/c monkeydo `"$proj\bin\$d.prg`" $d" -WindowStyle Hidden
        Start-Sleep 18
        if ($d -eq "fr55") {
            & "$proj\tools\sim\click.ps1" -X 250 -Y 350 | Out-Null; Start-Sleep 1
            Send-Key '{DOWN}'; Start-Sleep 2
        } else {
            & "$proj\tools\sim\click.ps1" -X 60 -Y 90 | Out-Null; Start-Sleep 1
        }
        Send-Key '{DOWN}'; Start-Sleep 2
        & "$proj\tools\sim\capture.ps1" -OutFile "$shots\${d}_glance.png" | Out-Null
        Write-Output "glance $d"
    }
}

if ($Phase -eq "all" -or $Phase -eq "parasha") {
    Write-Output "== parasha captures (non-Solar layouts) =="
    foreach ($d in ($touch + $nonTouchParasha)) {
        Start-Process cmd -ArgumentList "/c monkeydo `"$proj\bin\preview_$d.prg`" $d" -WindowStyle Hidden
        Start-Sleep 18
        if ($touch -contains $d) {
            & "$proj\tools\sim\click.ps1" -X 250 -Y 400 | Out-Null   # tap = onSelect -> page 2
            Start-Sleep 2
        } else {
            # focus only (no touch on these) — ENTER pushes page 2; a second
            # ENTER would POP back, so send exactly one and review after
            & "$proj\tools\sim\click.ps1" -X 250 -Y 350 | Out-Null; Start-Sleep 1
            Send-Key '{ENTER}'; Start-Sleep 2
        }
        & "$proj\tools\sim\capture.ps1" -OutFile "$shots\${d}_widget_parasha.png" | Out-Null
        Write-Output "parasha $d"
    }
    Write-Output "== ALL DONE =="
}
