# AppleAudio

Aplicación Java para Windows: al pulsar un atajo global captura la pantalla, extrae su texto y lo lee en voz alta.

## Requisitos

- Java 21 o superior.
- [Tesseract OCR para Windows](https://github.com/UB-Mannheim/tesseract/wiki), instalado y disponible como `tesseract` en el `PATH`. La aplicación también busca la instalación habitual en `C:\Program Files\Tesseract-OCR`.
- Los datos de idioma de Tesseract que se quieran usar (`spa`, `eng`, etc.).

## Ejecutar

En PowerShell, desde esta carpeta:

```powershell
.\build.ps1
.\run.ps1
```

`build.ps1` descarga únicamente JNA, la biblioteca que permite registrar el atajo global de Windows, y genera `LectorPantalla.jar`.

Configura el atajo haciendo clic en el campo y pulsando la combinación deseada. La app queda escuchando incluso si su ventana no tiene el foco. Selecciona idioma, voz y velocidad; los cambios se aplican al instante.

No se conservan capturas: se crea un PNG temporal durante el OCR y se borra al terminar.

## Distribución

`package.ps1` deja ambos formatos en `release`:

- `AppleAudio\AppleAudio.exe`: edición portátil. Se abre directamente desde esa carpeta y no instala nada en AppData.
- `AppleAudio-1.4.0.exe`: instalador opcional para quien prefiera accesos directos y menú Inicio.
