param([int]$Boot = 20, [string[]]$Only = @())
# Re-capture the 5 store-gallery source screenshots from the CURRENT code into
# bin\shots\store\, then run make_store_images.py / make_hero.py on them.
# Each widget shot is built from a %TEMP% copy as an auto-launching watch-app whose
# initial view is the page to show, with Time.now() pinned to the listed date
# (real tree untouched). The glance shot uses the REAL glance carousel via
# tools\sim\preview\real_glance.ps1. Needs dangerouslyDisableSandbox.
$ErrorActionPreference = "Continue"
$proj   = "C:\Users\yosef\Desktop\garmin app"
$work   = "C:\Users\yosef\AppData\Local\Temp\hebcal-store"
$out    = "$proj\bin\shots\store"
$simExe = "C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Sdks\connectiq-sdk-win-9.1.0-2026-03-09-6a872a80b\bin\simulator.exe"
New-Item -ItemType Directory -Force $work, $out | Out-Null

# name, device, initial view class, pinned date
$shots = @(
    @("venu3_widget_date",           "venu3",              "HebrewCalendarView", "2026-06-12"),  # כ״ז סיון תשפ״ו
    @("epix2_widget_omer",           "epix2",              "ParashaView",        "2026-05-07"),  # בהר־בחוקותי + ל״ה בעומר
    @("fr55_widget_event",           "fr55",               "ParashaView",        "2026-10-14"),  # נח + ראש חודש כסלו בעוד כ״ז ימים
    @("instinct3solar_omer_page",    "instinct3solar45mm", "OmerView",           "2026-05-07")   # ספירת העומר ל״ה בעומר
)

Get-ChildItem $proj -Directory | Where-Object { $_.Name -like "resources*" -or $_.Name -eq "source" } |
    ForEach-Object { Copy-Item -Recurse -Force $_.FullName $work }
Copy-Item -Force "$proj\monkey.jungle", "$proj\manifest.xml", "$proj\developer_key.der" $work
$m = Get-Content "$proj\manifest.xml" -Raw
($m -replace 'type="widget"', 'type="watch-app"') | Set-Content "$work\manifest.xml" -NoNewline
$appSrc = Get-Content "$proj\source\HebrewCalendarApp.mc" -Raw
$appSrc = $appSrc -replace '\(:glance\)\r?\n    function getGlanceView', "//(:glance)`n    private function unused_getGlanceView"

foreach ($s in $shots) {
    $name, $dev, $view, $date = $s
    if ($Only.Count -and $Only -notcontains $name) { continue }
    $y, $mo, $d = $date.Split("-") | ForEach-Object { [int]$_ }
    $moment = "Toybox.Time.Gregorian.moment({:year => $y, :month => $mo, :day => $d, :hour => 12, :minute => 0, :second => 0})"
    foreach ($f in @("HebrewCalendarView.mc", "ParashaView.mc", "OmerView.mc")) {
        (Get-Content "$proj\source\$f" -Raw) -replace 'Time\.now\(\)', $moment | Set-Content "$work\source\$f" -NoNewline
    }
    ($appSrc -replace 'var view = new HebrewCalendarView\(\);', "return [new $view()]; var view = new HebrewCalendarView();") |
        Set-Content "$work\source\HebrewCalendarApp.mc" -NoNewline
    Push-Location $work
    $log = monkeyc -o "s_$name.prg" -f monkey.jungle -y developer_key.der -d $dev 2>&1
    Pop-Location
    if ($LASTEXITCODE -ne 0) { Write-Output "BUILD FAIL $name"; $log | Select-Object -Last 3; continue }
    Get-Process monkeydo, simulator -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep 2
    Start-Process $simExe
    Start-Sleep 14
    Start-Process cmd -ArgumentList "/c monkeydo `"$work\s_$name.prg`" $dev" -WindowStyle Hidden
    Start-Sleep $Boot
    & "$proj\tools\sim\capture2.ps1" -OutFile "$out\$name.png" | Out-Null
    # the sim sometimes reports a tiny window right after launch: re-grab until full size
    for ($k = 0; $k -lt 5; $k++) {
        $wpx = python -c "from PIL import Image; print(Image.open(r'$out\$name.png').width)"
        if ([int]$wpx -ge 350) { break }
        Start-Sleep 3
        & "$proj\tools\sim\capture2.ps1" -OutFile "$out\$name.png" | Out-Null
    }
    Write-Output "shot $name"
}
Get-Process monkeydo, simulator -ErrorAction SilentlyContinue | Stop-Process -Force

if ($Only.Count -and $Only -notcontains "fr265_glance") { return }
# glance: real carousel, same date as the venu3 shot
& "$proj\tools\sim\preview\real_glance.ps1" -Devices fr265 -Date 2026-06-12 -Tag store -Boot 40 | Out-Null
Copy-Item -Force "$proj\bin\preview\real\full_fr265_store.png" "$out\fr265_glance.png"
Write-Output "shot fr265_glance"
