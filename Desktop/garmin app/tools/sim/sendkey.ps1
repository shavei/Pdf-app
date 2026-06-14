param([string]$Keys = "{ENTER}")

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32Act {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hwnd);
}
"@
Add-Type -AssemblyName System.Windows.Forms

$proc = Get-Process simulator -ErrorAction Stop | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
[Win32Act]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null
Start-Sleep -Milliseconds 400
[System.Windows.Forms.SendKeys]::SendWait($Keys)
Write-Output "sent $Keys"
