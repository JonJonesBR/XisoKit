# XisoKit

[![Compilar APK](https://github.com/JonJonesBR/XisoKit/actions/workflows/build.yml/badge.svg)](https://github.com/JonJonesBR/XisoKit/actions/workflows/build.yml)

[🇧🇷 Português](README.md) · [🇺🇸 English](README.en.md) · **🇪🇸 Español**

Crea y gestiona imágenes de disco de la **Xbox original** directamente en el teléfono, sin PC.

XisoKit convierte la carpeta de un juego en una imagen **XISO**, el formato que usan emuladores, ports y consolas modificadas. También convierte imágenes completas del disco, extrae archivos y muestra la información del juego.

## Funciones

- 📁 **Carpeta → XISO**: genera la imagen a partir de la carpeta de un juego extraído.
- 💿 **Imagen completa → XISO**: convierte la imagen entera del disco (unos 7 GB) en una XISO mucho más pequeña.
- 📂 **Extraer**: copia todos los archivos de una imagen a una carpeta.
- 🔍 **Ver contenido**: lista los archivos y carpetas de la imagen, con búsqueda por nombre.
- ✅ **Verificar integridad**: encuentra archivos truncados o ilegibles.
- 🎮 **Información del juego**: nombre, Title ID, región, versión y SHA-256 del `default.xbe`.
- 🔒 **Comprobación automática**: después de grabar, la app confirma que todos los archivos estén en la imagen.
- 🌐 **Tres idiomas**: español, portugués e inglés.

## Descarga

Descarga `XisoKit.apk` en la página de [**Releases**](https://github.com/JonJonesBR/XisoKit/releases) e instálalo en el teléfono. Android pedirá permiso para instalar apps de fuera de Play Store.

Requiere **Android 8.0** o posterior.

## Cómo usar

1. Toca **Elegir carpeta** y selecciona la carpeta del juego, la que contiene el `default.xbe`.
2. Revisa la información del juego en el panel que aparece.
3. Toca **Crear XISO** y elige dónde guardarla.

Para convertir o extraer una imagen que ya tengas, usa **Abrir imagen**. También puedes tocar una `.iso` en el gestor de archivos y abrirla con XisoKit.

## Preguntas frecuentes

**¿Qué diferencia hay entre una ISO común y una XISO?**
Una ISO común de PC usa el sistema de archivos ISO 9660, que Xbox no lee. La XISO usa el sistema de Xbox (XDVDFS), que es lo que esperan los emuladores y ports.

**¿Funciona con cualquier juego?**
Sí, con cualquier juego de Xbox original. En Burnout 3, la app también indica si la copia es compatible con el port [Takedawn](https://github.com/xenophobentx/takedawn).

**¿Por qué no puedo elegir toda la carpeta Download?**
Es una restricción de Android. Elige la subcarpeta del juego.

**¿La app incluye juegos?**
No. XisoKit no incluye ni descarga juegos. Usa copias de tus propios discos.
