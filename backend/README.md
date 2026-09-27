# Backend Java (detalhes técnicos)

> Para instalar e rodar o assistente, veja o [README principal](../README.md)
> — `npm start`, na raiz do projeto, já sobe este backend automaticamente.
> Este arquivo é sobre como o backend funciona por dentro.

Backend em **Java + Spring Boot** com toda a lógica de tarefas e lembretes
(parser de linguagem natural, regras de negócio, agendador de lembretes).
O bot em Node.js fala com ele por HTTP em `localhost` — nenhum dado sai do
seu PC.

## Por quê Java aqui, e não tudo em Node?

- **Node.js** fica só com a parte que ele faz bem: falar o protocolo do
  WhatsApp (via [Baileys](https://github.com/WhiskeySockets/Baileys)).
- **Java** cuida de tudo o resto: entender a frase, guardar a tarefa,
  decidir quando lembrar. É o mesmo código (já testado) que também roda no
  [app desktop](../../AssistentePessoal) original — não foi duplicado, foi
  reaproveitado.
- O bot Node vira um "carteiro": recebe a mensagem, manda pro backend,
  devolve a resposta pronta. É o Java quem decide o que fazer e qual texto
  responder.

```
Você (WhatsApp) ──▶ Bot Node.js ──HTTP──▶ Backend Java ──▶ SQLite
                     (Baileys)     (localhost:8080)      (data/assistente.db)
```

## Rodando o backend sozinho (para desenvolvimento)

Normalmente você não precisa fazer isso — `npm start` na raiz cuida disso.
Mas para depurar o backend isoladamente:

```bash
cd backend
mvn spring-boot:run
```

## Testes

```bash
cd backend
mvn test
```

78 testes (JUnit 5 + Mockito): parser de linguagem natural, regras de
negócio, repositório SQLite (integração real, com arquivo temporário),
agendador de lembretes e os comandos do bot (`CommandHandlerTest`).

## API REST

Dois endpoints, pensados para serem simples de consumir do bot Node:

### `POST /api/message`
Recebe o texto que o usuário digitou no WhatsApp e devolve a resposta já
formatada (mesmo texto que aparece no chat).

```json
// Request
{ "text": "Hoje às 19h estudar Java", "activeReminderId": null }

// Response
{
  "text": "✅ *Tarefa criada!*\n📌 Estudar Java\n📅 Hoje (2026-09-27)\n⏰ 19:00\n#1",
  "clearActiveReminder": false,
  "backupFileName": null,
  "backupFileBase64": null
}
```

Quando o comando é `backup`, a resposta vem com `backupFileName` e
`backupFileBase64` preenchidos (o bot Node decodifica e anexa o arquivo).

### `GET /api/reminders/pending`
O bot Node consulta este endpoint a cada 15 segundos (polling). Cada
chamada **consome** a fila — os lembretes retornados não aparecem de novo
na próxima consulta.

```json
[
  { "taskId": 1, "text": "🔔 *LEMBRETE*\nEstá na hora de: *Estudar Java*..." }
]
```

## Estrutura

```
backend/
├── pom.xml
├── src/main/java/com/assistente/
│   ├── model/            # Task, TaskStatus, TaskPriority, RecurrenceType
│   ├── parser/            # TaskParser (linguagem natural PT-BR), ParsedTask
│   ├── util/              # DateTimeUtil (formatação de datas em PT-BR)
│   ├── database/          # SqliteDatabaseManager
│   ├── repository/        # TaskRepository (JDBC puro sobre SQLite)
│   ├── scheduler/         # ReminderScheduler (verifica vencidas a cada 15s)
│   ├── service/           # TaskService, BackupService (regras de negócio)
│   └── backend/
│       ├── BackendApplication.java   # main Spring Boot + configuração dos beans
│       ├── CommandHandler.java       # interpreta comandos, formata respostas
│       ├── CommandResult.java
│       ├── ReminderQueueService.java # liga o ReminderScheduler a fila de polling
│       └── web/                      # controllers REST (MessageController, ReminderController)
└── src/test/java/...
```

`model`, `parser`, `util`, `scheduler` e `service` são os **mesmos arquivos**
(idênticos ou quase) do [app desktop](../../AssistentePessoal) — só o
`repository` mudou (era MySQL, agora é SQLite) e tudo que é `backend/*` é
novo (a API REST em si).

## Configuração

`src/main/resources/application.properties`:

```properties
server.port=8080
assistente.db-path=data/assistente.db
assistente.default-reminder-interval-minutes=30
```

## Solução de problemas

**"Port 8080 was already in use"**
Já tem uma instância rodando (verifique processos Java), ou troque a porta
aqui (e no `config.json` da raiz do projeto, campo `backendUrl`).

**Erro de compatibilidade do Mockito nos testes**
Se aparecer erro de "Unknown Java version" do Byte Buddy, é porque seu JDK
é mais novo que a versão que o Spring Boot fixa por padrão — o `pom.xml` já
sobrepõe isso (`byte-buddy.version`), então normalmente não deve acontecer.
Se acontecer numa versão de JDK ainda mais nova, atualize essa propriedade.
