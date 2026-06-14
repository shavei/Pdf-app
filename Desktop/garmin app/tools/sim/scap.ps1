param([string]$OutFile = "shot.png")

# Real screen capture of the simulator window (PrintWindow returns stale/blank
# frames for the CIQ simulator's GPU-rendered canvas — use CopyFromScreen).
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class W32s { [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; } }
"@
$p = Get-Process simulator -ErrorAction Stop | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
[W32s]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
Start-Sleep -Milliseconds 700
$r = New-Object W32s+RECT
[W32s]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
$w = $r.Right - $r.Left
$h = $r.Bottom - $r.Top
$bmp = New-Object System.Drawing.Bitmap($w, $h)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size)
$g.Dispose()
$bmp.Save($OutFile, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output "saved $OutFile ($w x $h)"
