param([string[]]$Devices, [string]$Date = "2027-03-08", [string]$Tag = "long", [int]$Boot = 22)
# Capture the REAL glance carousel (not the harness) for each device with the
# glance pinned to a given Gregorian date (default 2027-03-08 = כ״ט אדר א׳, the
# longest date). Builds from a %TEMP% copy with Time.now() replaced in the
# glance only; fresh simulator per device; verifies the capture is not black.
# Output: bin\preview\real\<device>_<Tag>.png (cropped to the display) +
# bin\preview\real\sheet_<Tag>.png. Needs dangerouslyDisableSandbox.
$ErrorActionPreference = "Continue"
$proj = "C:\Users\yosef\Desktop\garmin app"
$work = "C:\Users\yosef\AppData\Local\Temp\hebcal-real"
$out  = "$proj\bin\preview\real"
$devDir = "C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Devices"
$simExe = "C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Sdks\connectiq-sdk-win-9.1.0-2026-03-09-6a872a80b\bin\simulator.exe"
New-Item -ItemType Directory -Force $work, $out | Out-Null
Get-ChildItem $proj -Directory | Where-Object { $_.Name -like "resources*" -or $_.Name -eq "source" } |
    ForEach-Object { Copy-Item -Recurse -Force $_.FullName $work }
Copy-Item -Force "$proj\monkey.jungle", "$proj\manifest.xml", "$proj\developer_key.der" $work
$y, $m, $d = $Date.Split("-") | ForEach-Object { [int]$_ }
$g = "$work\source\HebrewCalendarGlanceView.mc"
$t = Get-Content $g -Raw
$t = $t -replace 'new HebrewDate\(Time\.now\(\)\)', "new HebrewDate(Toybox.Time.Gregorian.moment({:year => $y, :month => $m, :day => $d, :hour => 12, :minute => 0, :second => 0}))"
Set-Content $g $t -NoNewline
Push-Location $work
foreach ($dev in $Devices) {
    $log = monkeyc -o "r_$dev.prg" -f monkey.jungle -y developer_key.der -d $dev 2>&1
    if ($LASTEXITCODE -ne 0) { Write-Output "BUILD FAIL $dev"; $log | Select-Object -Last 3; continue }
    $sim = Get-Content "$devDir\$dev\simulator.json" -Raw | ConvertFrom-Json
    $loc = $sim.display.location
    $ok = $false
    for ($a = 1; $a -le 4 -and -not $ok; $a++) {
        Get-Process monkeydo, simulator -ErrorAction SilentlyContinue | Stop-Process -Force
        Start-Sleep 2
        Start-Process $simExe
        Start-Sleep 14
        Start-Process cmd -ArgumentList "/c monkeydo `"$work\r_$dev.prg`" $dev" -WindowStyle Hidden
        Start-Sleep $Boot
        & "$proj\tools\sim\capture2.ps1" -OutFile "$out\full_${dev}_$Tag.png" | Out-Null
        $lit = python -c "from PIL import Image; im=Image.open(r'$out\full_${dev}_$Tag.png').convert('L').crop(($($loc.x),$($loc.y),$($loc.x+$loc.width),$($loc.y+$loc.height))); print(sum(1 for p in im.getdata() if p>180))"
        if ([int]$lit -gt 150) { $ok = $true }
    }
    python -c "from PIL import Image; Image.open(r'$out\full_${dev}_$Tag.png').convert('RGB').crop(($($loc.x-8),$($loc.y-8),$($loc.x+$loc.width+8),$($loc.y+$loc.height+8))).save(r'$out\${dev}_$Tag.png')"
    Write-Output ("{0} {1}" -f $(if ($ok) { "shot" } else { "BLANK" }), $dev)
}
Pop-Location
Get-Process monkeydo, simulator -ErrorAction SilentlyContinue | Stop-Process -Force
python -c "
from PIL import Image, ImageDraw
import os,sys
out=r'$out'; tag='$Tag'; devs='$($Devices -join ',')'.split(',')
ims=[(d,Image.open(os.path.join(out,d+'_'+tag+'.png'))) for d in devs if os.path.exists(os.path.join(out,d+'_'+tag+'.png'))]
ims=[(d,i.resize((360,round(i.height*360/i.width)))) for d,i in ims]
cols=3; rows=(len(ims)+cols-1)//cols; H=max(i.height for _,i in ims)+20
s=Image.new('RGB',(cols*370,rows*H),'white'); dr=ImageDraw.Draw(s)
for k,(d,i) in enumerate(ims):
    x=(k%cols)*370; y=(k//cols)*H; dr.text((x+4,y+2),d,fill='black'); s.paste(i,(x,y+16))
s.save(os.path.join(out,'sheet_'+tag+'.png'))"
Write-Output "sheet: $out\sheet_$Tag.png"
