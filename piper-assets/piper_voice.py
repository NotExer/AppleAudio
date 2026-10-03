"""Genera un WAV con Piper para Lector de Pantalla."""
import sys
import wave
from pathlib import Path

from piper import PiperVoice


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit("Uso: piper-voice.exe MODELO TEXTO_SALIDA WAV_SALIDA")
    model_path, text_path, wav_path = map(Path, sys.argv[1:])
    text = text_path.read_text(encoding="utf-8").strip()
    if not text:
        return
    voice = PiperVoice.load(model_path)
    with wave.open(str(wav_path), "wb") as wav_file:
        voice.synthesize_wav(text, wav_file)


if __name__ == "__main__":
    main()
