"""Genera un WAV con Piper para Lector de Pantalla."""
import sys
import wave
from pathlib import Path

from piper import PiperVoice
from piper.config import SynthesisConfig


def main() -> None:
    if len(sys.argv) not in (4, 5):
        raise SystemExit("Uso: piper-voice.exe MODELO TEXTO_SALIDA WAV_SALIDA [HABLANTE]")
    model_path, text_path, wav_path = map(Path, sys.argv[1:4])
    speaker_id = int(sys.argv[4]) if len(sys.argv) == 5 else None
    text = text_path.read_text(encoding="utf-8").strip()
    if not text:
        return
    voice = PiperVoice.load(model_path)
    with wave.open(str(wav_path), "wb") as wav_file:
        voice.synthesize_wav(text, wav_file, syn_config=SynthesisConfig(speaker_id=speaker_id))


if __name__ == "__main__":
    main()
