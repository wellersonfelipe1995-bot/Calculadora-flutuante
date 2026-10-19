# Meus Ganhos

App Android com um balão flutuante que fica por cima de qualquer app (Uber, 99, inDrive...).
Toque no balão e abre uma linha com **3 caixas que se somam** e **1 caixa com o resultado**,
para calcular quanto você ganhou na corrida sem sair do app de corridas.

```
 (R$)
┌───────────────────────────────────────────┐
│ [ 12,50 ] + [ 3,00 ] + [ 2 ]    = R$ 17,50 │
│ [ 120 ]   ÷ [ 12 ]   × [ 6,00 ] = R$ 60,00 │
└───────────────────────────────────────────┘
```

A segunda linha calcula o gasto com combustível do dia:
**km rodados ÷ km/l × preço do litro = gasto**. Abaixo do gasto aparece quanto
ele representa, em %, do ganho bruto (soma da primeira linha). Esses três valores ficam salvos
mesmo fechando o balão.

## Como usar

1. Abra o app **Meus Ganhos**.
2. Toque em **Permitir sobrepor outros apps** e ative a permissão.
3. Toque em **Iniciar balão**. O balão verde "R$" aparece na tela. Das próximas vezes,
   abrir o app pelo ícone já liga o balão direto; a tela do app (com o histórico) abre
   pelo botão **Histórico** do balão ou pela notificação.
4. Abra o app de corridas. Toque no balão para abrir/fechar a calculadora.
5. Arraste o balão para mudar de lugar. Embaixo do painel ficam **⛽ Abasteci**, **Histórico** e
   **Sair** (que fecha o balão; arrastar o balão até a lixeira faz o mesmo).

Aceita vírgula ou ponto como decimal (`12,50` ou `12.50`).

## Tela do app

A tela do app tem abas na parte de baixo:

- **Início**: card do mês atual (líquido em destaque; bruto, combustível, por fora e média
  líquida por dia) e os últimos dias.
- **Histórico**: todos os dias em cards, separados por mês.
- **Desempenho**: o card de resumo de cada mês.
- **Ajustes**: permissão do balão, Iniciar/Parar balão e informações dos lembretes.

Layouts: `res/layout/activity_main.xml` (tela e abas), `item_resumo_mes.xml` (card do mês) e
`item_historico_dia.xml` (card do dia); código em `HistoricoActivity.kt`.

## Histórico diário

Tudo que é digitado no balão é salvo automaticamente num banco de dados no próprio
celular (SQLite), **um registro por dia**. Ao mudar qualquer caixa, o registro de hoje é
atualizado (não cria um novo). Quando a data vira, as caixas começam vazias e um novo
registro começa; km/l e preço do litro são mantidos do último dia.

A tela inicial do app mostra o histórico agrupado por mês. Cada mês traz o total
**por fora** (3ª caixa: corridas por fora e gorjetas), bruto, combustível e líquido; abaixo,
cada dia com os mesmos dados. Segure o dedo sobre um dia para apagá-lo.

O botão **Histórico** no balão abre o app direto nessa lista. Tocar num dia do histórico
abre o balão nele para corrigir.

### Dias atrasados e dia livre

No topo do painel do balão aparece o dia que está sendo editado (**Hoje**, **Ontem**, ...).
As setas ◀ ▶ trocam o dia, para lançar valores atrasados na data certa. Ao tocar no balão,
o painel sempre volta para hoje. A caixa **Dia livre** marca folga.

### Abastecimento no meio do dia

Se abastecer no meio do dia (e o km/l ou o preço mudarem), toque em **⛽ Abasteci** no balão
antes de trocar os valores: o km e o gasto até ali ficam guardados, o km volta a zero e você
coloca o km/l e o preço novos. O gasto, a % e o histórico somam todos os trechos do dia.
**Desfazer** junta os trechos de volta numa linha só.

### Lembretes

Se o dia não tiver nenhum valor nem estiver marcado como dia livre, o app avisa
às **23h** do mesmo dia e às **8h** do dia seguinte. Tocar na notificação abre o balão já no
dia que ficou sem registro.

### Resumo do mês

Cada mês do histórico mostra: por fora, bruto, combustível, líquido, dias trabalhados,
dias livres e média por dia trabalhado.

Os dados ficam só no celular: desinstalar o app apaga o histórico.

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
