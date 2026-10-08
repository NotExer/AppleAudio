$ErrorActionPreference = 'Stop'

# Genera una edición portátil y un instalador autocontenidos.
& (Join-Path $PSScriptRoot 'build.ps1')
$input = Join-Path $PSScriptRoot 'package-input'
$release = Join-Path $PSScriptRoot 'release'
if (Test-Path $input) { Remove-Item -LiteralPath $input -Recurse -Force }
New-Item -ItemType Directory -Force -Path $input, $release | Out-Null

Copy-Item (Join-Path $PSScriptRoot 'LectorPantalla.jar') $input
Copy-Item (Join-Path $PSScriptRoot 'lib\*.jar') $input
$piper = Join-Path $input 'piper'
New-Item -ItemType Directory -Force -Path $piper | Out-Null
Copy-Item (Join-Path $PSScriptRoot 'dist\piper-voice.exe') $piper
Copy-Item (Join-Path $PSScriptRoot 'dist\edge-voice.exe') $piper
Copy-Item (Join-Path $PSScriptRoot 'piper-assets\voice\*.onnx') $piper
Copy-Item (Join-Path $PSScriptRoot 'piper-assets\voice\*.onnx.json') $piper
$ocrDestination = Join-Path $input 'tesseract'
New-Item -ItemType Directory -Force -Path $ocrDestination | Out-Null
Copy-Item 'C:\Program Files\Tesseract-OCR\tesseract.exe' $ocrDestination
Copy-Item 'C:\Program Files\Tesseract-OCR\*.dll' $ocrDestination
New-Item -ItemType Directory -Force -Path (Join-Path $ocrDestination 'tessdata') | Out-Null
Copy-Item 'C:\Program Files\Tesseract-OCR\tessdata\eng.traineddata' (Join-Path $ocrDestination 'tessdata')
Copy-Item 'C:\Program Files\Tesseract-OCR\tessdata\osd.traineddata' (Join-Path $ocrDestination 'tessdata')
Copy-Item (Join-Path $PSScriptRoot 'ocr-languages\*.traineddata') (Join-Path $ocrDestination 'tessdata')

$wix = Join-Path $PSScriptRoot 'tools\wix'
$env:Path = "$wix;$env:Path"
& (Join-Path $PSScriptRoot 'generate-icon.ps1')
$portable = Join-Path $release 'AppleAudio'
if (Test-Path -LiteralPath $portable) { Remove-Item -LiteralPath $portable -Recurse -Force }
$common = @('--dest', $release, '--name', 'AppleAudio', '--app-version', '1.5.0', '--input', $input, '--main-jar', 'LectorPantalla.jar', '--main-class', 'LectorPantalla', '--icon', (Join-Path $PSScriptRoot 'assets\sonora.ico'), '--vendor', 'AppleAudio', '--description', 'Lee en voz alta el texto visible en pantalla.')
jpackage --type app-image @common
if ($LASTEXITCODE -ne 0) { throw "Falló la creación de la edición portátil (código $LASTEXITCODE)." }
jpackage --type exe @common --win-per-user-install --win-shortcut --win-menu --win-dir-chooser
if ($LASTEXITCODE -ne 0) { throw "Falló la creación del instalador (código $LASTEXITCODE)." }
Copy-Item (Join-Path $release 'AppleAudio-1.5.0.exe') (Join-Path $portable 'AppleAudio-Setup.exe') -Force
Write-Host "Edición portátil: $(Join-Path $portable 'AppleAudio.exe')"
Write-Host "Instalador: $(Join-Path $portable 'AppleAudio-Setup.exe')"
