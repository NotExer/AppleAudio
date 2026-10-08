$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assets = Join-Path $PSScriptRoot 'assets'
New-Item -ItemType Directory -Force -Path $assets | Out-Null
$bitmap = [System.Drawing.Bitmap]::new(256, 256)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$background = [System.Drawing.Drawing2D.LinearGradientBrush]::new([System.Drawing.Rectangle]::new(0,0,256,256), [System.Drawing.Color]::FromArgb(35,50,81), [System.Drawing.Color]::FromArgb(18,27,45), 45)
$graphics.FillRectangle($background, 0, 0, 256, 256)
$wave = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(119,235,190))
$heights = @(48,84,120,84,48)
for ($i=0; $i -lt 5; $i++) { $height = $heights[$i]; $graphics.FillRectangle($wave, 65 + $i * 27, 128 - [int]($height / 2), 14, $height) }
$icon = [System.Drawing.Icon]::FromHandle($bitmap.GetHicon())
$stream = [System.IO.File]::Create((Join-Path $assets 'sonora.ico'))
$icon.Save($stream)
$stream.Close(); $icon.Dispose(); $wave.Dispose(); $background.Dispose(); $graphics.Dispose(); $bitmap.Dispose()
