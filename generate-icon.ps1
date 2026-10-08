$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assets = Join-Path $PSScriptRoot 'assets'
New-Item -ItemType Directory -Force -Path $assets | Out-Null
$bitmap = [System.Drawing.Bitmap]::new(256, 256)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$background = [System.Drawing.Drawing2D.LinearGradientBrush]::new([System.Drawing.Rectangle]::new(0,0,256,256), [System.Drawing.Color]::FromArgb(75,160,126), [System.Drawing.Color]::FromArgb(37,90,138), 45)
$graphics.FillRectangle($background, 0, 0, 256, 256)
$white = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
$blue = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(43,104,137))
$graphics.FillRectangle($white, 57, 61, 142, 104)
$points = [System.Drawing.Point[]]@([System.Drawing.Point]::new(99,164), [System.Drawing.Point]::new(120,190), [System.Drawing.Point]::new(142,164))
$graphics.FillPolygon($white, $points)
for ($i=0; $i -lt 4; $i++) { $height = 24 + $i * 16; $graphics.FillRectangle($blue, 83 + $i * 25, 113 - [int]($height / 2), 13, $height) }
$icon = [System.Drawing.Icon]::FromHandle($bitmap.GetHicon())
$stream = [System.IO.File]::Create((Join-Path $assets 'sonora.ico'))
$icon.Save($stream)
$stream.Close(); $icon.Dispose(); $blue.Dispose(); $white.Dispose(); $background.Dispose(); $graphics.Dispose(); $bitmap.Dispose()
