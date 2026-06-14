param([string]$OutFile = "shot.png")
# Assumes monkeydo has loaded the widget (sim shows the glance over the watchface).
# Focuses the sim, taps the glance band to open the widget, then captures it.

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;using System.Runtime.InteropServices;
public class Shot {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x,int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f,uint x,uint y,uint d,int e);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
$proc = Get-Process simulator | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
$h = $proc.MainWindowHandle
[Shot]::SetForegroundWindow($h) | Out-Null
Start-Sleep -Milliseconds 800
$r = New-Object Shot+RECT; [Shot]::GetWindowRect($h, [ref]$r) | Out-Null
$w = $r.Right - $r.Left; $ht = $r.Bottom - $r.Top
$cx = $r.Left + [int]($w * 0.50)
$gy = $r.Top  + [int]($ht * 0.43)   # glance band sits ~43% down the window
# Tap the glance band twice (focus, then open) — harmless if already open
[Shot]::SetCursorPos($cx, $gy); Start-Sleep -Milliseconds 250
[Shot]::mouse_event(0x2,0,0,0,0); [Shot]::mouse_event(0x4,0,0,0,0)
Start-Sleep -Milliseconds 1500
# Re-read rect (window may have resized) and capture
[Shot]::GetWindowRect($h, [ref]$r) | Out-Null
$w = $r.Right - $r.Left; $ht = $r.Bottom - $r.Top
$bmp = New-Object System.Drawing.Bitmap($w, $ht)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$hdc = $g.GetHdc()
[Shot]::PrintWindow($h, $hdc, 3) | Out-Null
$g.ReleaseHdc($hdc); $g.Dispose()
$bmp.Save($OutFile, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output "saved $OutFile ($w x $ht)"
