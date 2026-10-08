$ErrorActionPreference = 'Stop'

# Genera un instalador EXE autocontenido: Java, OCR e idiomas viajan dentro de él.
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
Copy-Item (Join-Path $PSScriptRoot 'piper-assets\voice\es_ES-sharvard-medium.onnx') $piper
Copy-Item (Join-Path $PSScriptRoot 'piper-assets\voice\es_ES-sharvard-medium.onnx.json') $piper
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
jpackage --type exe --dest $release --name 'Lector de Pantalla' --app-version '1.3.0' --input $input --main-jar LectorPantalla.jar --main-class LectorPantalla --icon (Join-Path $PSScriptRoot 'assets\sonora.ico') --vendor 'AppleAudio' --description 'Lee en voz alta el texto visible en pantalla.' --win-per-user-install --win-shortcut --win-menu --win-dir-chooser
if ($LASTEXITCODE -ne 0) { throw "Falló la creación del instalador (código $LASTEXITCODE)." }
Write-Host "Instalador creado en $release"
