# hxkiosk

App de quiosque para tablets. Bloqueia notificacoes, atalhos do sistema e saida do modo quiosque com acessibilidade, sem formatar o aparelho nem depender de Device Owner.

## Bloqueios sem formatar

No painel admin, use **Conceder permissoes do dispositivo** e ative:

1. Servico de acessibilidade hxkiosk
2. Acesso as notificacoes
3. HX KIOSK como launcher padrao

Para devolver o tablet ao uso normal, use **Liberar acesso do tablet**. Isso remove Device Owner/admin sem factory reset.

## Console remoto de teste

Com o tablet e o PC na mesma rede:

1. Ative **Permitir console remoto pelo PC** no painel admin
2. No PC, abra `http://IP_DO_TABLET:8787/`
3. Entre com a senha administrativa

Ou rode:

```powershell
.\tools\remote-test\conectar-tablet.ps1
.\tools\remote-test\conectar-tablet.ps1 -Adb
```

O parametro `-Adb` tenta abrir o espelhamento completo com scrcpy, se estiver instalado.
