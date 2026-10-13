# Calculadora Flutuante

App Android com um balão flutuante que fica por cima de qualquer app (Uber, 99, inDrive...).
Toque no balão e abre uma linha com **3 caixas que se somam** e **1 caixa com o resultado**,
para calcular quanto você ganhou na corrida sem sair do app de corridas.

```
 (R$)  [Limpar] [Sair]
┌───────────────────────────────────────────┐
│ [ 12,50 ] + [ 3,00 ] + [ 2 ]    = R$ 17,50 │
│ [ 120 ]   ÷ [ 12 ]   × [ 6,00 ] = R$ 60,00 │
└───────────────────────────────────────────┘
```

A segunda linha calcula o gasto com combustível do dia:
**km rodados ÷ km/l × preço do litro = gasto**. Esses três valores ficam salvos
mesmo fechando o balão; o botão **Limpar** apaga só os valores das corridas.

## Como usar

1. Abra o app **Calculadora Flutuante**.
2. Toque em **Permitir sobrepor outros apps** e ative a permissão.
3. Toque em **Iniciar balão**. O balão verde "R$" aparece na tela.
4. Abra o app de corridas. Toque no balão para abrir/fechar a calculadora.
5. Arraste o balão para mudar de lugar. **Limpar** zera os valores, **Sair** fecha o balão.

Aceita vírgula ou ponto como decimal (`12,50` ou `12.50`).

## Como baixar o APK

Cada push gera o APK automaticamente pelo GitHub Actions:

1. Vá na aba **Actions** do repositório → workflow **Gerar APK** → última execução.
2. Baixe o artefato **calculadora-flutuante-apk** (vem em .zip; dentro está o `app-debug.apk`).
3. Passe para o celular e instale (permita "instalar apps de fontes desconhecidas").

## Compilar localmente

Requer Android SDK (ou Android Studio):

```
./gradlew assembleDebug
```

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

Requisitos: Android 8.0 ou superior. Não funciona no iPhone (o iOS não permite balões sobre outros apps).
