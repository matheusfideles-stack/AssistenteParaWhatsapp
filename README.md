# Assistente Pessoal — Bot do WhatsApp

Bot pessoal de tarefas e lembretes que roda **dentro do seu próprio
WhatsApp**, no chat "Mensagem para você mesmo" — 100% local, gratuito e sem
nenhuma API paga.

```
Você: Hoje às 19h estudar Java
Bot:   Tarefa criada!
       Estudar Java
       Hoje (2026-09-27)
       19:00
      #1

(às 19h)
Bot:   LEMBRETE
      Está na hora de: *Estudar Java*
       19:00

      Responda:
      1️⃣ Concluir
      2️⃣ Adiar 30 min
      3️⃣ Cancelar

Você: 1
Bot:   Tarefa concluída!
      Estudar Java
```

---

## Como funciona

Um projeto só, mas com duas partes por baixo dos panos:

- **Node.js** fala o protocolo do WhatsApp (via
  [Baileys](https://github.com/WhiskeySockets/Baileys), open source e
  gratuito) e repassa suas mensagens.
- **Java (Spring Boot)**, na pasta [`backend/`](backend), entende a frase,
  guarda a tarefa, decide quando lembrar — é o mesmo código (já testado)
  que também roda no [app desktop](../AssistentePessoal) original.

Você só roda **um comando** (`npm start`) — ele sobe o backend Java
automaticamente por baixo dos panos, sem você precisar abrir dois
terminais:

```
Você (WhatsApp) ──▶ Bot Node.js ──HTTP local──▶ Backend Java ──▶ SQLite
                     (Baileys)      (127.0.0.1:8080)          (backend/data/assistente.db)
```

- Você **escaneia um QR code uma única vez**, exatamente como ao abrir o
  WhatsApp Web no navegador — o bot passa a ser "mais um aparelho conectado"
  na sua própria conta.
- Toda a lógica roda **no seu PC**, sem servidor externo, sem nuvem — os
  dois processos só falam entre si em `localhost`, nada sai da sua máquina.
- O bot só reage a mensagens no chat **"Mensagem para você mesmo"** — a
  conversa que você tem consigo mesmo no WhatsApp. Mensagens em outros chats
  são ignoradas.

> **Importante sobre o Baileys:** por não ser a API oficial da Meta, existe
> (baixo, mas real) risco de a conta sofrer alguma restrição em casos de uso
> muito agressivo. Para um bot pessoal, de baixo volume, falando só com você
> mesmo, o risco é mínimo — mas isso é diferente de usar a API oficial paga.
> Fica registrado aqui por transparência.

## Requisitos

- **Node.js 20+** — https://nodejs.org (gratuito)
- **Java 21+** — https://adoptium.net (gratuito)
- **Maven 3.9+** — https://maven.apache.org (gratuito)
- Um número de WhatsApp ativo no seu celular (o mesmo que você já usa)
- Conexão com a internet (o WhatsApp em si exige internet; "gratuito" aqui
  significa sem custo financeiro, não sem internet)

## Instalação

```bash
cd AssistenteWhatsapp
npm install
npm run build:backend
```

O `npm run build:backend` compila o backend Java uma vez (gera
`backend/target/assistente-backend-1.0.0.jar`) — só precisa rodar de novo
se você alterar o código do backend.

## Como rodar

```bash
npm start
```

Isso:
1. Sobe o backend Java automaticamente (se ainda não estiver rodando)
2. Conecta no WhatsApp e mostra um QR code no terminal (na primeira vez)
3. Fica ouvindo mensagens e disparando lembretes

No celular: **WhatsApp → Configurações (⋮ ou ⚙) → Aparelhos conectados →
Conectar aparelho** → aponte a câmera para o QR do terminal.

Depois de conectado, abra a conversa **"Mensagem para você mesmo"** no
WhatsApp (ícone do seu próprio perfil no topo da lista de chats, ou pesquise
seu próprio nome) e comece a escrever.

A sessão do WhatsApp fica salva em `auth/` — nas próximas vezes que rodar
`npm start`, não precisa escanear o QR de novo (a menos que desconecte o
aparelho pelo celular ou apague a pasta `auth/`).

**Para os lembretes funcionarem, o processo precisa continuar rodando** —
deixe o terminal aberto, ou rode como um processo em segundo plano (veja
abaixo). Ao encerrar com `Ctrl+C`, o backend Java é encerrado junto
automaticamente.

### Rodar em segundo plano (Windows)

Com o [PM2](https://pm2.keymetrics.io/) (gratuito):
```bash
npm install -g pm2
pm2 start src/index.js --name assistente-whatsapp
pm2 save
pm2 startup
```

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
| `proximas` | tarefas das próximas 24h |
| `prioridade` | tarefas de alta prioridade |
| `buscar <termo>` | pesquisa por título/descrição |
| `concluir #<id>` | marca como concluída |
| `cancelar #<id>` | cancela a tarefa |
| `excluir #<id>` | apaga a tarefa |
| `adiar #<id> <minutos>` | adia N minutos |
| `reagendar #<id> <dd/mm> <hh:mm>` | reagenda para nova data/hora |
| `backup` | exporta e envia um `.csv` com todas as tarefas |

Quando um lembrete chega, responder **1**/**concluir**, **2**/**adiar** ou
**3**/**cancelar** já conclui/adia 30 min/cancela a tarefa daquele lembrete.

Além da mensagem no WhatsApp, cada lembrete também dispara uma **notificação
nativa do Windows** (toast) no PC onde o bot está rodando — útil quando o
celular está longe.

## Estrutura do projeto

```
AssistenteWhatsapp/
├── package.json
├── config.json                  # criado automaticamente (URL do backend)
├── CLAUDE.md
├── auth/                        # credenciais da sessão do WhatsApp (NUNCA versionar)
├── src/
│   ├── index.js                   # ponto de entrada
│   ├── config.js
│   ├── services/
│   │   ├── apiClient.js           # cliente HTTP para o backend Java
│   │   └── backendLauncher.js     # sobe o backend Java automaticamente
│   └── bot/
│       ├── whatsappClient.js      # conexão Baileys, QR code, polling de lembretes
│       └── commandHandler.js      # repassa mensagens para o backend e traduz a resposta
├── test/                          # testes do bot (node --test)
└── backend/                       # backend Java (Spring Boot) — ver backend/README.md
    ├── pom.xml
    ├── src/main/java/com/assistente/...
    └── src/test/java/...
```

## Testes

**Bot (Node.js):**
```bash
npm test
```
`ApiClient`, `CommandHandler`, `backendLauncher` e `desktopNotifier` — 17
testes com `node:test`, sem dependências extras.

**Backend (Java):**
```bash
cd backend && mvn test
```
78 testes (JUnit 5 + Mockito): parser de linguagem natural, regras de
negócio, repositório SQLite (integração real) e os comandos do bot. Veja
[backend/README.md](backend/README.md) para detalhes da API REST.

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
Só precisa mudar se você rodar o backend Java em outra porta.

## Solução de problemas

**"Backend Java ainda não foi compilado"**
Rode `npm run build:backend` (precisa de Java 21+ e Maven instalados).

**"Backend Java não respondeu a tempo (45s)"**
Confira os logs impressos no terminal — geralmente é porta 8080 já em uso
por outro processo, ou erro de compilação. Veja
[backend/README.md](backend/README.md#solução-de-problemas).

**O QR code não aparece / expira antes de escanear**
Reinicie `npm start` para gerar um novo QR.

**"Sessão desconectada" depois de rodar antes normalmente**
Você desconectou o aparelho pelo celular (Aparelhos conectados). Apague a
pasta `auth/` e rode `npm start` de novo para reconectar com um novo QR.

**O bot não responde no chat**
Confirme que está escrevendo no chat **"Mensagem para você mesmo"** — o bot
ignora mensagens em qualquer outro chat/contato/grupo, de propósito, para
nunca responder a ninguém além de você.


