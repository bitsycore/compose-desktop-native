param([string]$proc, [string]$out)
# Capture a window's CLIENT area by process name, via PrintWindow - works even
# when the window is occluded or not focused (unlike a screen grab). Used by the
# probe driver to screenshot native app windows deterministically.
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Cap {
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint flags);
    public struct RECT { public int L, T, R, B; }
}
"@
$h = (Get-Process $proc -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1).MainWindowHandle
if (-not $h -or $h -eq [IntPtr]::Zero) { Write-Output "NO WINDOW"; exit 1 }
$r = New-Object Cap+RECT
[Cap]::GetClientRect($h, [ref]$r) | Out-Null
$w = $r.R - $r.L; $ht = $r.B - $r.T
if ($w -le 0 -or $ht -le 0) { Write-Output "ZERO SIZE"; exit 1 }
$bmp = New-Object System.Drawing.Bitmap $w, $ht
$g = [System.Drawing.Graphics]::FromImage($bmp)
$hdc = $g.GetHdc()
# flags=3 : PW_CLIENTONLY | PW_RENDERFULLCONTENT (captures accelerated content)
[Cap]::PrintWindow($h, $hdc, 3) | Out-Null
$g.ReleaseHdc($hdc)
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Output "captured ${w}x${ht}"
