# Assistente Pessoal — Bot do WhatsApp

Bot pessoal de tarefas e lembretes que roda **dentro do seu próprio
WhatsApp**, no chat "Mensagem para você mesmo" — 100% local, gratuito e sem
nenhuma API paga.

```
Você: Hoje às 19h estudar Java
Bot:  ✅ Tarefa criada!
      📌 Estudar Java
      📅 Hoje (2026-09-27)
      ⏰ 19:00
      #1

(às 19h)
Bot:  🔔 LEMBRETE
      Está na hora de: *Estudar Java*
      ⏰ 19:00

      Responda:
      1️⃣ Concluir
      2️⃣ Adiar 30 min
      3️⃣ Cancelar

Você: 1
Bot:  ✅ Tarefa concluída!
      Estudar Java
```

---

## Como funciona (sem violar as regras que você definiu)

Este projeto tem **duas partes**, que precisam rodar juntas:

1. **[AssistentePessoal-Backend](../AssistentePessoal-Backend)** (Java +
   Spring Boot) — tem toda a lógica: entende a frase, guarda a tarefa no
   SQLite, decide quando lembrar. É o mesmo código (testado) que também
   roda no [app desktop](../AssistentePessoal) original.
2. **Este bot** (Node.js) — só fala o protocolo do WhatsApp (via
   [Baileys](https://github.com/WhiskeySockets/Baileys)) e repassa
   mensagens de/para o backend Java por HTTP local.

```
Você (WhatsApp) ──▶ Bot Node.js ──HTTP──▶ Backend Java ──▶ SQLite
                     (Baileys)     (localhost:8080)      (data/assistente.db)
```

- **Sem WhatsApp Business API, sem Twilio, sem Selenium.** Usa o Baileys,
  biblioteca open source e gratuita que fala o mesmo protocolo do WhatsApp
  Web.
- Você **escaneia um QR code uma única vez**, exatamente como ao abrir o
  WhatsApp Web no navegador — o bot passa a ser "mais um aparelho conectado"
  na sua própria conta.
- Toda a lógica roda **no seu PC**, sem servidor externo, sem nuvem — os
  dois processos (bot e backend) falam só entre si em `localhost`.
- O bot só reage a mensagens no chat **"Mensagem para você mesmo"** — a
  conversa que você tem consigo mesmo no WhatsApp. Mensagens em outros chats
  são ignoradas.

> **Importante sobre o Baileys:** por não ser a API oficial da Meta, existe
> (baixo, mas real) risco de a conta sofrer alguma restrição em casos de uso
> muito agressivo. Para um bot pessoal, de baixo volume, falando só com você
> mesmo, o risco é mínimo — mas isso é diferente de usar a API oficial paga.
> Fica registrado aqui por transparência.

## Requisitos

- **Node.js 20 ou mais recente**. Baixe em https://nodejs.org — gratuito.
- **[AssistentePessoal-Backend](../AssistentePessoal-Backend) rodando**
  (requer Java 21+ e Maven — veja o README de lá).
- Um número de WhatsApp ativo no seu celular (o mesmo que você já usa).
- Conexão com a internet (o WhatsApp em si exige internet; "gratuito" aqui
  significa sem custo financeiro, não sem internet).

## Instalação

```bash
cd AssistenteWhatsapp
npm install
```

Isso baixa apenas bibliotecas gratuitas e de código aberto: Baileys (conexão
WhatsApp), pino (log interno do Baileys) e qrcode-terminal (mostrar o QR no
terminal). Nenhum banco de dados aqui — o bot não guarda nada localmente,
só conversa com o backend Java.

## Como rodar

**1. Primeiro, suba o backend** (em outro terminal):
```bash
cd ../AssistentePessoal-Backend
mvn spring-boot:run
```

**2. Depois, suba o bot:**
```bash
npm start
```

Na primeira vez, um QR code aparece no terminal. No celular:
**WhatsApp → Configurações (⋮ ou ⚙) → Aparelhos conectados → Conectar
aparelho** → aponte a câmera para o QR do terminal.

Depois de conectado, abra a conversa **"Mensagem para você mesmo"** no
WhatsApp (ícone do seu próprio perfil no topo da lista de chats, ou pesquise
seu próprio nome) e comece a escrever.

A sessão fica salva em `auth/` — nas próximas vezes que rodar `npm start`,
não precisa escanear o QR de novo (a menos que desconecte o aparelho pelo
celular ou apague a pasta `auth/`).

**Para os lembretes funcionarem, os dois processos (bot e backend) precisam
continuar rodando** — deixe os terminais abertos, ou rode como processos em
segundo plano (veja abaixo).

### Rodar em segundo plano (Windows)

Com o [PM2](https://pm2.keymetrics.io/) (gratuito), para o bot:
```bash
npm install -g pm2
pm2 start src/index.js --name assistente-whatsapp
pm2 save
pm2 startup
```

O backend Java pode rodar como serviço com [WinSW](https://github.com/winsw/winsw)
ou, mais simples, numa janela do PowerShell minimizada com `mvn spring-boot:run`.

## Comandos no chat

Além de criar tarefas escrevendo em linguagem natural (mesmas regras do
parser: `hoje`, `amanhã`, `daqui 30 minutos`, `todo dia às Xh`, `a cada 30
minutos até eu concluir`, `prioridade alta`, etc. — veja exemplos digitando
**ajuda**), você pode:

| Comando | Efeito |
|---|---|
| `ajuda` | mostra o menu de comandos |
| `tarefas` ou `hoje` | tarefas de hoje |
| `amanhã` | tarefas de amanhã |
| `todas` | todas as tarefas |
| `pendentes` | tarefas pendentes |
| `concluidas` | tarefas concluídas |
| `atrasadas` | tarefas atrasadas |
| `prioridade` | tarefas de alta prioridade |
| `buscar <termo>` | pesquisa por título/descrição |
| `concluir #<id>` | marca como concluída |
| `cancelar #<id>` | cancela a tarefa |
| `excluir #<id>` | apaga a tarefa |
| `adiar #<id> <minutos>` | adia N minutos |
| `reagendar #<id> <dd/mm> <hh:mm>` | reagenda para nova data/hora |
| `backup` | exporta e envia um `.csv` com todas as tarefas |

Quando um lembrete chega, responder só **1**, **2** ou **3** já
concluir/adia 30 min/cancela a tarefa daquele lembrete.

Todos esses comandos são interpretados **pelo backend Java** — este bot só
repassa o texto e devolve a resposta.

## Estrutura do projeto

```
AssistenteWhatsapp/
├── package.json
├── config.json                # criado automaticamente (URL do backend)
├── auth/                       # credenciais da sessão do WhatsApp (NUNCA versionar)
├── src/
│   ├── index.js                 # ponto de entrada
│   ├── config.js
│   ├── services/
│   │   └── apiClient.js         # cliente HTTP para o backend Java
│   └── bot/
│       ├── whatsappClient.js    # conexão Baileys, QR code, envio de mensagens, polling de lembretes
│       └── commandHandler.js    # repassa mensagens para o backend e traduz a resposta
└── test/                        # testes (node --test)
```

Note que não há mais `db/`, `parser/` nem a maior parte de `services/` — essa
lógica mudou para o [AssistentePessoal-Backend](../AssistentePessoal-Backend).

## Testes

```bash
npm test
```

Roda os testes com o executor nativo do Node (`node:test` — sem
dependências extras): o `ApiClient` (chamadas HTTP, com `fetch` mockado) e o
`CommandHandler` (repassa corretamente texto/activeReminderId e trata a
resposta do backend, incluindo o caso de backup). A lógica de negócio em si
(parser, regras de tarefas, agendador) tem sua própria suíte de testes no
backend Java (`mvn test` lá, 78 testes).

## Backup

Digite **backup** no chat — o backend Java exporta todas as tarefas para
`.csv`, manda para o bot em base64, e o bot salva num arquivo temporário e
envia de volta pelo próprio WhatsApp, como documento anexado.

## Configuração

`config.json` (criado automaticamente na primeira execução):
```json
{
  "backendUrl": "http://localhost:8080"
}
```
Só precisa mudar se você rodar o backend Java em outra porta/máquina.

## Solução de problemas

**"Não consegui falar com o backend agora"**
O [AssistentePessoal-Backend](../AssistentePessoal-Backend) não está
rodando. Suba-o com `mvn spring-boot:run` antes de usar o bot.

**O QR code não aparece / expira antes de escanear**
Reinicie `npm start` para gerar um novo QR.

**"Sessão desconectada" depois de rodar antes normalmente**
Você desconectou o aparelho pelo celular (Aparelhos conectados). Apague a
pasta `auth/` e rode `npm start` de novo para reconectar com um novo QR.

**O bot não responde no chat**
Confirme que está escrevendo no chat **"Mensagem para você mesmo"** — o bot
ignora mensagens em qualquer outro chat/contato/grupo, de propósito, para
nunca responder a ninguém além de você.

## Custo

**R$ 0,00.** Node.js, Java, Maven, Baileys, Spring Boot, pino,
qrcode-terminal e o driver SQLite são todos gratuitos e de código aberto.
Não é usada nenhuma API paga do WhatsApp, nenhum servidor em nuvem, nenhum
cartão de crédito.
