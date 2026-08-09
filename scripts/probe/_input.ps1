param(
    [string]$proc,          # process name of the target window
    [string]$action,        # click | hold | hover | move
    [double]$fx,            # fractional X within the window client area (0..1)
    [double]$fy,            # fractional Y
    [int]$holdMs = 80       # press duration for 'hold'
)
# Window-CLIENT-relative synthetic input against a native app window, addressed
# by process name - so probes don't depend on where the window landed or on it
# being the foreground window (it's raised to foreground first). Coordinates are
# FRACTIONS of the client rect, so they're resolution/DPI independent.
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class In {
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint x, uint y, uint d, UIntPtr e);
    public struct RECT { public int L, T, R, B; }
    public struct POINT { public int X, Y; }
    public const uint DOWN = 2, UP = 4;
}
"@
$h = (Get-Process $proc -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1).MainWindowHandle
if (-not $h -or $h -eq [IntPtr]::Zero) { Write-Output "NO WINDOW"; exit 1 }
$r = New-Object In+RECT
[In]::GetClientRect($h, [ref]$r) | Out-Null
$p = New-Object In+POINT
$p.X = [int](($r.R - $r.L) * $fx); $p.Y = [int](($r.B - $r.T) * $fy)
[In]::ClientToScreen($h, [ref]$p) | Out-Null
[In]::SetForegroundWindow($h) | Out-Null
Start-Sleep -Milliseconds 120
[In]::SetCursorPos($p.X, $p.Y) | Out-Null
switch ($action) {
    "move"  { }
    "hover" { Start-Sleep -Milliseconds 250 }
    "click" { Start-Sleep -Milliseconds 120; [In]::mouse_event([In]::DOWN,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 60; [In]::mouse_event([In]::UP,0,0,0,[UIntPtr]::Zero) }
    "hold"  { Start-Sleep -Milliseconds 120; [In]::mouse_event([In]::DOWN,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds $holdMs; [In]::mouse_event([In]::UP,0,0,0,[UIntPtr]::Zero) }
}
Write-Output "$action at $($p.X),$($p.Y)"
