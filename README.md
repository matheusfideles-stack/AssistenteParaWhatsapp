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

- **Sem WhatsApp Business API, sem Twilio, sem Selenium.** Usa o
  [Baileys](https://github.com/WhiskeySockets/Baileys), uma biblioteca open
  source e gratuita que fala o mesmo protocolo do WhatsApp Web.
- Você **escaneia um QR code uma única vez**, exatamente como ao abrir o
  WhatsApp Web no navegador — o bot passa a ser "mais um aparelho conectado"
  na sua própria conta.
- Toda a lógica roda **no seu PC**, sem servidor externo, sem nuvem.
- Os dados ficam num arquivo SQLite local (`data/assistente.db`), usando o
  módulo `node:sqlite` **nativo do Node.js** (nenhuma dependência externa
  para o banco, nenhuma compilação, zero configuração).
- O bot só reage a mensagens no chat **"Mensagem para você mesmo"** — a
  conversa que você tem consigo mesmo no WhatsApp. Mensagens em outros chats
  são ignoradas.

> **Importante sobre o Baileys:** por não ser a API oficial da Meta, existe
> (baixo, mas real) risco de a conta sofrer alguma restrição em casos de uso
> muito agressivo. Para um bot pessoal, de baixo volume, falando só com você
> mesmo, o risco é mínimo — mas isso é diferente de usar a API oficial paga.
> Fica registrado aqui por transparência.

## Requisitos

- **Node.js 22.5 ou mais recente** (para o módulo nativo `node:sqlite`).
  Baixe em https://nodejs.org — gratuito.
- Um número de WhatsApp ativo no seu celular (o mesmo que você já usa).
- Conexão com a internet (o WhatsApp em si exige internet; "gratuito" aqui
  significa sem custo financeiro, não sem internet).

## Instalação

```bash
cd AssistenteWhatsapp
npm install
```

Isso baixa apenas bibliotecas gratuitas e de código aberto: Baileys (conexão
WhatsApp), dayjs (datas), pino (log interno do Baileys) e qrcode-terminal
(mostrar o QR no terminal). **Nenhuma compilação nativa é necessária** — o
banco de dados usa o SQLite embutido no próprio Node.

## Como rodar

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

**Para os lembretes funcionarem, o processo precisa continuar rodando** —
deixe o terminal aberto, ou rode como um processo em segundo plano (veja
abaixo).

### Rodar em segundo plano (Windows)

Com o [PM2](https://pm2.keymetrics.io/) (gratuito):
```bash
npm install -g pm2
pm2 start src/index.js --name assistente-whatsapp
pm2 save
pm2 startup
```

Ou simplesmente deixe uma janela do PowerShell minimizada rodando
`npm start` sempre que quiser usar o assistente.

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

## Estrutura do projeto

```
AssistenteWhatsapp/
├── package.json
├── config.json                # criado automaticamente (intervalo padrão de lembrete)
├── data/assistente.db         # banco SQLite (gerado automaticamente, gitignored)
├── auth/                      # credenciais da sessão do WhatsApp (NUNCA versionar)
├── src/
│   ├── index.js                # ponto de entrada
│   ├── config.js
│   ├── db/
│   │   ├── database.js         # abre o SQLite (node:sqlite)
│   │   └── taskRepository.js   # CRUD de tarefas
│   ├── parser/
│   │   └── taskParser.js       # interpretador de linguagem natural (PT-BR)
│   ├── services/
│   │   ├── taskService.js      # regras de negocio (criar/concluir/adiar/recorrencia)
│   │   ├── reminderScheduler.js # verifica tarefas vencidas a cada 15s
│   │   └── backupService.js    # exporta/importa CSV
│   └── bot/
│       ├── whatsappClient.js   # conexão Baileys, QR code, envio de mensagens
│       └── commandHandler.js   # interpreta comandos e formata respostas
└── test/                       # testes (node --test)
```

## Testes

```bash
npm test
```

Roda os testes com o executor nativo do Node (`node:test` — sem
dependências extras): parser de linguagem natural, regras de negócio
(criação, conclusão, cancelamento, adiamento, recorrência), agendador de
lembretes e exportação/importação de backup. 65 testes, incluindo casos
reais encontrados durante o desenvolvimento (ex.: um bug em que palavras
terminadas em "do" — como "avança**do**" — eram cortadas por engano, porque
"do" também é uma preposição removida do título).

## Backup

Digite **backup** no chat — o bot exporta todas as tarefas para um `.csv` em
`~/AssistenteWhatsapp-backups/` e envia o arquivo de volta pelo próprio
WhatsApp, como documento anexado.

## Configuração

`config.json` (criado automaticamente na primeira execução):
```json
{
  "defaultReminderIntervalMinutes": 30
}
```
Esse é o intervalo padrão em que uma tarefa vencida continua sendo
relembrada até ser concluída, quando a tarefa não especifica seu próprio
intervalo (`a cada X minutos`).

## Solução de problemas

**O QR code não aparece / expira antes de escanear**
Reinicie `npm start` para gerar um novo QR.

**"Sessão desconectada" depois de rodar antes normalmente**
Você desconectou o aparelho pelo celular (Aparelhos conectados). Apague a
pasta `auth/` e rode `npm start` de novo para reconectar com um novo QR.

**O bot não responde no chat**
Confirme que está escrevendo no chat **"Mensagem para você mesmo"** — o bot
ignora mensagens em qualquer outro chat/contato/grupo, de propósito, para
nunca responder a ninguém além de você.

**Erro `Cannot find module 'node:sqlite'`**
Sua versão do Node é mais antiga que 22.5. Atualize em https://nodejs.org.

## Custo

**R$ 0,00.** Node.js, Baileys, dayjs, pino, qrcode-terminal e o SQLite
embutido no Node são todos gratuitos e de código aberto. Não é usada
nenhuma API paga do WhatsApp, nenhum servidor em nuvem, nenhum cartão de
crédito.
