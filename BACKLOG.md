# Backlog

O que ainda falta fazer no XisoKit. Itens concluídos não ficam aqui.

## Validação no aparelho

- [ ] Instalar o APK 1.3.1 e testar: abrir `.iso` pelo gerenciador, converter, verificar, extrair, cancelar uma extração no meio, copiar o SHA-256 e trocar o idioma.
- [ ] Confirmar que a pasta parcial é apagada ao cancelar ou falhar a extração (e que a mensagem avisa quando o provedor não deixa apagar).
- [ ] Conferir o “Abrir com” em pelo menos dois gerenciadores (Files by Google, Material Files etc.), tanto para `application/x-iso9660-image` quanto para `.iso` entregue como `application/octet-stream`.

## Decisões já tomadas, pendentes de teste

- [ ] Subir `targetSdk` para 37 depois de testar no Android 17. Hoje é 36 de propósito; o motivo está no `lint.xml`.
- [ ] Reavaliar Gradle/AGP quando houver uma combinação nova validada. Hoje Gradle 9.7.0 e AGP 9.3.3 estão pinados, e o aviso de versão nova está silenciado no `lint.xml`.
- [ ] Tirar screenshots e colocar nos três READMEs (precisa do app rodando).

## Robustez e UX

- [ ] Gerar o texto da lista compartilhada (`contentsAsText`) fora da thread principal; hoje ele é montado no clique e pode travar em jogos muito grandes.
- [ ] Teste de imagem truncada (cabeçalho válido, mas tabela ou conteúdo cortado) para garantir mensagem de erro clara em vez de exceção crua.
- [ ] Revisar acessibilidade: TalkBack, fontes grandes e contraste dos botões.
- [ ] Revisar o comportamento ao girar a tela ou trocar de idioma durante uma tarefa longa.

## Ideias

- [ ] Release estável por tag (hoje só existe a “Build mais recente”/nightly).
- [ ] Botão para copiar o relatório de verificação, além do SHA-256.
- [ ] Testes de UI com Compose para os fluxos principais.
