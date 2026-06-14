param([int]$X = 197, [int]$Y = 150)  # coords relative to simulator window

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32Click {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hwnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
"@

$proc = Get-Process simulator -ErrorAction Stop | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
$hwnd = $proc.MainWindowHandle
[Win32Click]::SetForegroundWindow($hwnd) | Out-Null
Start-Sleep -Milliseconds 300

$rect = New-Object Win32Click+RECT
[Win32Click]::GetWindowRect($hwnd, [ref]$rect) | Out-Null
$ax = $rect.Left + $X
$ay = $rect.Top + $Y
[Win32Click]::SetCursorPos($ax, $ay) | Out-Null
Start-Sleep -Milliseconds 150
[Win32Click]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero)   # left down
Start-Sleep -Milliseconds 80
[Win32Click]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)   # left up
Write-Output "clicked window-relative ($X,$Y) = screen ($ax,$ay)"
