param([string]$Dev)

$proj = "C:\Users\yosef\Desktop\garmin app"
$shots = "$proj\bin\shots"

# Launch the app in the simulator
Start-Process monkeydo -ArgumentList "`"$proj\bin\$Dev.prg`"", $Dev -WindowStyle Hidden
Start-Sleep 10

# Glance carousel screenshot
& "$proj\tools\sim\capture.ps1" -OutFile "$shots\${Dev}_glance.png"

# Click display (select glance), click again (open widget) at window fraction
$proc = Get-Process simulator | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32R { [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hwnd, out RECT rect);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; } }
"@
$rect = New-Object Win32R+RECT
[Win32R]::GetWindowRect($proc.MainWindowHandle, [ref]$rect) | Out-Null
$w = $rect.Right - $rect.Left
$h = $rect.Bottom - $rect.Top

# Tap display twice: first selects the glance, second opens the widget (touch devices)
& "$proj\tools\sim\click.ps1" -X ([int]($w * 0.37)) -Y ([int]($h * 0.26))
Start-Sleep 2
& "$proj\tools\sim\click.ps1" -X ([int]($w * 0.37)) -Y ([int]($h * 0.26))
Start-Sleep 2
& "$proj\tools\sim\click.ps1" -X ([int]($w * 0.42)) -Y ([int]($h * 0.40))
Start-Sleep 3

& "$proj\tools\sim\capture.ps1" -OutFile "$shots\${Dev}_widget.png"
