param([string]$OutFile = "shot.png")

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;using System.Collections.Generic;using System.Runtime.InteropServices;using System.Text;
public class Cap2 {
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
  [DllImport("user32.dll")] public static extern int GetWindowThreadProcessId(IntPtr h, out int pid);
  [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
  public static List<IntPtr> Find(int[] pids) {
    var res = new List<IntPtr>();
    EnumWindows((h,l)=>{ int p; GetWindowThreadProcessId(h, out p);
      if (Array.IndexOf(pids,p)>=0 && IsWindowVisible(h)) res.Add(h); return true; }, IntPtr.Zero);
    return res;
  }
}
"@
$pids = (Get-Process simulator -ErrorAction Stop).Id
$best = $null; $bestArea = 0
foreach ($h in [Cap2]::Find($pids)) {
  $r = New-Object Cap2+RECT; [Cap2]::GetWindowRect($h, [ref]$r) | Out-Null
  $area = ($r.Right - $r.Left) * ($r.Bottom - $r.Top)
  if ($area -gt $bestArea) { $bestArea = $area; $best = $h }
}
if (-not $best) { throw "no simulator window" }
$r = New-Object Cap2+RECT; [Cap2]::GetWindowRect($best, [ref]$r) | Out-Null
$w = $r.Right - $r.Left; $h2 = $r.Bottom - $r.Top
$bmp = New-Object System.Drawing.Bitmap($w, $h2)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$hdc = $g.GetHdc()
[Cap2]::PrintWindow($best, $hdc, 3) | Out-Null
$g.ReleaseHdc($hdc); $g.Dispose()
$bmp.Save($OutFile, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output "saved $OutFile ($w x $h2)"
