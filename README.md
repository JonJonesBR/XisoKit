# XisoKit

[![Compilar APK](https://github.com/JonJonesBR/XisoKit/actions/workflows/build.yml/badge.svg)](https://github.com/JonJonesBR/XisoKit/actions/workflows/build.yml)

**🇧🇷 Português** · [🇺🇸 English](README.en.md) · [🇪🇸 Español](README.es.md)

Crie e gerencie imagens de disco do **Xbox original** direto no celular, sem precisar de PC.

O XisoKit transforma a pasta de um jogo numa imagem **XISO**, o formato usado por emuladores, ports e consoles modificados. Também converte imagens completas do disco, extrai arquivos e mostra as informações do jogo.

## Recursos

- 📁 **Pasta → XISO**: gera a imagem a partir da pasta de um jogo extraído.
- 💿 **Imagem completa → XISO**: converte a imagem inteira do disco (cerca de 7 GB) numa XISO bem menor.
- 📂 **Extrair**: copia todos os arquivos de uma imagem para uma pasta.
- 🔍 **Ver conteúdo**: lista arquivos e pastas da imagem, com busca por nome.
- ✅ **Verificar integridade**: encontra arquivos truncados ou ilegíveis.
- 🎮 **Informações do jogo**: nome, Title ID, região, versão e SHA-256 do `default.xbe`.
- 🔒 **Conferência automática**: depois de gravar, o app confere se todos os arquivos estão na imagem.
- 🌐 **Três idiomas**: português, inglês e espanhol.

## Download

Baixe o `XisoKit.apk` na página de [**Releases**](https://github.com/JonJonesBR/XisoKit/releases) e instale no celular. O Android vai pedir permissão para instalar apps de fora da Play Store.

Requer **Android 8.0** ou mais novo.

## Como usar

1. Toque em **Escolher pasta** e selecione a pasta do jogo, aquela onde está o `default.xbe`.
2. Confira as informações do jogo no quadro que aparece.
3. Toque em **Criar XISO** e escolha onde salvar.

Para converter ou extrair uma imagem que você já tem, use **Abrir imagem**. Também dá para tocar numa `.iso` no gerenciador de arquivos e escolher abrir com o XisoKit.

## Perguntas frequentes

**Qual a diferença entre uma ISO comum e uma XISO?**
Uma ISO comum de PC usa o sistema de arquivos ISO 9660, que o Xbox não lê. A XISO usa o sistema do Xbox (XDVDFS), o que emuladores e ports esperam.

**Funciona com qualquer jogo?**
Sim, com qualquer jogo do Xbox original. Para o Burnout 3, o app também avisa se a cópia é compatível com o port [Takedawn](https://github.com/xenophobentx/takedawn).

**Por que não consigo escolher a pasta Download inteira?**
É uma restrição do Android. Escolha a subpasta do jogo.

**O app vem com jogos?**
Não. O XisoKit não inclui nem baixa jogos. Use cópias dos seus próprios discos.
