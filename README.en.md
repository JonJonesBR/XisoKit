# XisoKit

[![Build APK](https://github.com/JonJonesBR/XisoKit/actions/workflows/build.yml/badge.svg)](https://github.com/JonJonesBR/XisoKit/actions/workflows/build.yml)

[🇧🇷 Português](README.md) · **🇺🇸 English** · [🇪🇸 Español](README.es.md)

Create and manage **original Xbox** disc images right on your phone, no PC needed.

XisoKit turns a game folder into an **XISO** image, the format used by emulators, ports and modded consoles. It also converts full disc images, extracts files and shows the game's details.

## Features

- 📁 **Folder → XISO**: builds the image from an extracted game folder.
- 💿 **Full image → XISO**: converts the full disc image (about 7 GB) into a much smaller XISO.
- 📂 **Extract**: copies every file from an image into a folder.
- 🔍 **View contents**: lists the image's files and folders, with search.
- ✅ **Verify integrity**: finds truncated or unreadable files.
- 🎮 **Game details**: name, Title ID, region, version and SHA-256 of `default.xbe`.
- 🔒 **Automatic check**: after writing, the app confirms every file made it into the image.
- 🌐 **Three languages**: English, Portuguese and Spanish.

## Download

Get `XisoKit.apk` from the [**Releases**](https://github.com/JonJonesBR/XisoKit/releases) page and install it on your phone. Android will ask for permission to install apps from outside the Play Store.

Requires **Android 8.0** or newer.

## How to use

1. Tap **Choose folder** and select the game folder, the one that contains `default.xbe`.
2. Check the game details in the panel that appears.
3. Tap **Create XISO** and choose where to save it.

To convert or extract an image you already have, use **Open image**. You can also tap an `.iso` in your file manager and open it with XisoKit.

## FAQ

**What is the difference between a regular ISO and an XISO?**
A regular PC ISO uses the ISO 9660 file system, which the Xbox cannot read. An XISO uses the Xbox file system (XDVDFS), which emulators and ports expect.

**Does it work with any game?**
Yes, with any original Xbox game. For Burnout 3, the app also tells you whether the copy works with the [Takedawn](https://github.com/xenophobentx/takedawn) port.

**Why can't I pick the whole Download folder?**
Android does not allow it. Pick the game's subfolder.

**Does the app include games?**
No. XisoKit neither includes nor downloads games. Use copies of your own discs.
