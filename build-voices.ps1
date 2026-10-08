$ErrorActionPreference = 'Stop'
python -m pip install edge-tts piper-tts
python -m PyInstaller --noconfirm --clean --onefile --name piper-voice --collect-data piper --exclude-module torch piper-assets\piper_voice.py
if ($LASTEXITCODE -ne 0) { throw "Falló la creación de piper-voice.exe." }
python -m PyInstaller --noconfirm --clean --onefile --name edge-voice piper-assets\edge_voice.py
if ($LASTEXITCODE -ne 0) { throw "Falló la creación de edge-voice.exe." }
