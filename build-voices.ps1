$ErrorActionPreference = 'Stop'
python -m pip install edge-tts
python -m PyInstaller --noconfirm --clean --onefile --name edge-voice piper-assets\edge_voice.py
if ($LASTEXITCODE -ne 0) { throw "Falló la creación de edge-voice.exe." }
