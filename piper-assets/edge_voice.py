"""Genera un MP3 con una voz neuronal de Microsoft Edge sin clave de Azure."""
import asyncio
import sys
from pathlib import Path

import edge_tts


async def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit("Uso: edge-voice.exe VOZ TEXTO_SALIDA MP3_SALIDA")
    voice, text_path, audio_path = sys.argv[1:]
    text = Path(text_path).read_text(encoding="utf-8").strip()
    if text:
        await edge_tts.Communicate(text, voice).save(audio_path)


if __name__ == "__main__":
    asyncio.run(main())
