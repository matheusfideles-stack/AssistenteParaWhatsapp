package com.assistente.backend;

import com.assistente.model.Task;
import com.assistente.service.BackupService;
import com.assistente.service.TaskService;
import com.assistente.util.DateTimeUtil;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta as mensagens recebidas no chat "Mensagem para voce mesmo" do
 * WhatsApp e devolve a resposta ja formatada (texto pronto para enviar de
 * volta). Portado 1:1 do commandHandler.js do bot Node - mesmos comandos,
 * mesmas mensagens, mesmo comportamento.
 */
public class CommandHandler {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final String HELP_TEXT = """
            *Assistente Pessoal - comandos*

            Para criar uma tarefa, é só escrever normalmente, por exemplo:
            _Hoje às 19h estudar Java_
            _Amanhã às 8h academia_
            _Todo dia às 20h estudar Java_
            _Beber água a cada 30 minutos até eu concluir_

            *Consultar:*
            tarefas / hoje — tarefas de hoje
            amanhã — tarefas de amanhã
            todas — todas as tarefas
            pendentes
            concluidas
            atrasadas
            proximas — próximas 24h
            prioridade — alta prioridade
            buscar <termo>

            *Ações* (use o número #id que aparece na lista):
            concluir <id>
            cancelar <id>
            excluir <id>
            adiar <id> <minutos>
            reagendar <id> <dd/mm[/aaaa]> <hh:mm>

            *Outros:*
            backup — exporta suas tarefas em .csv
            ajuda — mostra esta mensagem

            Quando um lembrete chegar, responda *1* (ou "concluir"), *2* (ou "adiar") ou *3* (ou "cancelar").""";

    private static final Pattern P_BUSCAR = Pattern.compile("^(buscar|pesquisar)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_CONCLUIR = Pattern.compile("^conclu[ií]r\\s*#?(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_CANCELAR = Pattern.compile("^cancelar\\s*#?(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_EXCLUIR = Pattern.compile("^(excluir|apagar)\\s*#?(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_ADIAR = Pattern.compile("^adiar\\s*#?(\\d+)\\s+(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_REAGENDAR = Pattern.compile(
            "^reagendar\\s*#?(\\d+)\\s+(\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?)\\s+(\\d{1,2}:\\d{2})$",
            Pattern.CASE_INSENSITIVE);

    private final TaskService taskService;
    private final BackupService backupService;
    private final Path backupDirectory;

    public CommandHandler(TaskService taskService, BackupService backupService, Path backupDirectory) {
        this.taskService = taskService;
        this.backupService = backupService;
        this.backupDirectory = backupDirectory;
    }

    /** @param activeReminderId id da tarefa do ultimo lembrete enviado, ou null se nenhum. */
    public CommandResult handle(String rawText, Long activeReminderId) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            return CommandResult.of("Não entendi. Digite *ajuda* para ver os comandos.");
        }
        String lower = text.toLowerCase(Locale.ROOT);

        if (activeReminderId != null) {
            String shortcut = normalizeReminderShortcut(lower);
            if (shortcut != null) {
                return handleReminderShortcut(shortcut, activeReminderId);
            }
        }

        if (lower.equals("ajuda") || lower.equals("menu") || lower.equals("help") || lower.equals("?")) {
            return CommandResult.of(HELP_TEXT);
        }
        if (lower.equals("tarefas") || lower.equals("hoje")) {
            return CommandResult.of(listTasks(taskService.findToday(), "TAREFAS DE HOJE"));
        }
        if (lower.equals("amanha") || lower.equals("amanhã")) {
            return CommandResult.of(listTasks(taskService.findTomorrow(), "TAREFAS DE AMANHÃ"));
        }
        if (lower.equals("todas")) {
            return CommandResult.of(listTasks(taskService.findAll(), "TODAS AS TAREFAS"));
        }
        if (lower.equals("pendentes")) {
            return CommandResult.of(listTasks(taskService.findPending(), "TAREFAS PENDENTES"));
        }
        if (lower.equals("concluidas") || lower.equals("concluídas")) {
            return CommandResult.of(listTasks(taskService.findCompleted(), "TAREFAS CONCLUÍDAS"));
        }
        if (lower.equals("atrasadas")) {
            return CommandResult.of(listTasks(taskService.findOverdue(), "TAREFAS ATRASADAS"));
        }
        if (lower.equals("proximas") || lower.equals("próximas")) {
            return CommandResult.of(listTasks(taskService.findUpcoming(24), "PRÓXIMAS 24H"));
        }
        if (lower.equals("prioridade") || lower.equals("urgentes") || lower.equals("prioridades")) {
            return CommandResult.of(listTasks(taskService.findHighPriority(), "ALTA PRIORIDADE"));
        }
        if (lower.equals("backup")) {
            return exportBackup();
        }

        Matcher m;
        if ((m = P_BUSCAR.matcher(text)).matches()) {
            return CommandResult.of(listTasks(taskService.search(m.group(2)), "RESULTADOS PARA \"" + m.group(2) + "\""));
        }
        if ((m = P_CONCLUIR.matcher(text)).matches()) {
            return complete(Long.parseLong(m.group(1)));
        }
        if ((m = P_CANCELAR.matcher(text)).matches()) {
            return cancel(Long.parseLong(m.group(1)));
        }
        if ((m = P_EXCLUIR.matcher(text)).matches()) {
            return delete(Long.parseLong(m.group(2)));
        }
        if ((m = P_ADIAR.matcher(text)).matches()) {
            return postpone(Long.parseLong(m.group(1)), Integer.parseInt(m.group(2)));
        }
        if ((m = P_REAGENDAR.matcher(text)).matches()) {
            return reschedule(Long.parseLong(m.group(1)), m.group(2), m.group(3));
        }

        // Nenhum comando reconhecido -> trata como criacao de tarefa em linguagem natural
        return createFromText(text);
    }

    /** Aceita tanto o numero do atalho (1/2/3) quanto a palavra correspondente, digitados sem #id. */
    private String normalizeReminderShortcut(String lower) {
        if (lower.equals("1") || lower.equals("concluir")) {
            return "1";
        }
        if (lower.equals("2") || lower.equals("adiar")) {
            return "2";
        }
        if (lower.equals("3") || lower.equals("cancelar")) {
            return "3";
        }
        return null;
    }

    private CommandResult handleReminderShortcut(String digit, long taskId) {
        try {
            if (digit.equals("1")) {
                Task task = taskService.complete(taskId);
                return CommandResult.clearingReminder("*Tarefa concluída!*\n" + task.getTitle());
            }
            if (digit.equals("2")) {
                Task task = taskService.postpone(taskId, 30);
                return CommandResult.clearingReminder(
                        "Tudo bem. Vou te lembrar novamente às *" + formatTime(task.getDueTime()) + "*.");
            }
            Task task = taskService.cancel(taskId);
            return CommandResult.clearingReminder("Tarefa cancelada: " + task.getTitle());
        } catch (RuntimeException e) {
            return CommandResult.clearingReminder(e.getMessage());
        }
    }

    private CommandResult createFromText(String text) {
        try {
            Task task = taskService.createFromText(text);
            String data = task.getDueDate() == null ? "" : DateTimeUtil.friendlyDate(task.getDueDate());
            String hora = formatTime(task.getDueTime());
            StringBuilder msg = new StringBuilder()
                    .append("*Tarefa criada!*\n").append(task.getTitle())
                    .append("\n").append(data).append(" (").append(task.getDueDate()).append(")")
                    .append("\n").append(hora)
                    .append("\n#").append(task.getId());
            if (task.isRecurring()) {
                msg.append("\nRecorrente (").append(recurrenceLabel(task)).append(")");
            }
            return CommandResult.of(msg.toString());
        } catch (RuntimeException e) {
            return CommandResult.of("Não consegui entender: " + e.getMessage() + "\n\nDigite *ajuda* para ver exemplos.");
        }
    }

    private CommandResult complete(long id) {
        try {
            Task task = taskService.complete(id);
            return CommandResult.of("*Tarefa concluída!*\n" + task.getTitle());
        } catch (RuntimeException e) {
            return CommandResult.of(e.getMessage());
        }
    }

    private CommandResult cancel(long id) {
        try {
            Task task = taskService.cancel(id);
            return CommandResult.of("Tarefa cancelada: " + task.getTitle());
        } catch (RuntimeException e) {
            return CommandResult.of(e.getMessage());
        }
    }

    private CommandResult delete(long id) {
        try {
            Task task = taskService.get(id);
            taskService.deleteTask(id);
            return CommandResult.of("Tarefa excluída: " + task.getTitle());
        } catch (RuntimeException e) {
            return CommandResult.of(e.getMessage());
        }
    }

    private CommandResult postpone(long id, int minutes) {
        try {
            Task task = taskService.postpone(id, minutes);
            return CommandResult.of("*" + task.getTitle() + "* adiada para "
                    + DateTimeUtil.friendlyDate(task.getDueDate()) + " às " + formatTime(task.getDueTime()) + ".");
        } catch (RuntimeException e) {
            return CommandResult.of(e.getMessage());
        }
    }

    private CommandResult reschedule(long id, String dateText, String timeText) {
        try {
            LocalDate date = parseBrDate(dateText);
            LocalTime time = LocalTime.parse(timeText);
            Task task = taskService.reschedule(id, date, time);
            return CommandResult.of("*" + task.getTitle() + "* reagendada para "
                    + DateTimeUtil.friendlyDate(task.getDueDate()) + " às " + formatTime(task.getDueTime()) + ".");
        } catch (RuntimeException e) {
            return CommandResult.of(e.getMessage());
        }
    }

    private CommandResult exportBackup() {
        Path file = backupService.exportBackup(backupDirectory);
        return CommandResult.withFile("Backup exportado! Enviando o arquivo...", file);
    }

    private String listTasks(List<Task> tasks, String title) {
        if (tasks.isEmpty()) {
            return title + "\n\nNenhuma tarefa encontrada.";
        }
        StringBuilder sb = new StringBuilder(title).append("\n\n");
        for (Task t : tasks) {
            String time = formatTime(t.getDueTime());
            String priority = switch (t.getPriority()) {
                case HIGH -> " (alta)";
                case MEDIUM -> "";
                case LOW -> " (baixa)";
            };
            String rec = t.isRecurring() ? " (recorrente)" : "";
            String status = switch (t.getStatus()) {
                case COMPLETED -> "[concluída] ";
                case CANCELLED -> "[cancelada] ";
                default -> "";
            };
            sb.append(status).append("#").append(t.getId()).append(" ").append(time)
                    .append(" ").append(t.getTitle()).append(priority).append(rec).append("\n");
        }
        sb.append("\nTotal: ").append(tasks.size());
        return sb.toString();
    }

    private String formatTime(LocalTime time) {
        return time == null ? "--:--" : time.format(TIME_FMT);
    }

    private String recurrenceLabel(Task task) {
        return switch (task.getRecurrenceType()) {
            case DAILY -> "todo dia";
            case WEEKLY -> "toda semana";
            case MONTHLY -> "todo mês, dia " + task.getRecurrenceValue();
            case NONE -> "";
        };
    }

    /** Converte "dd/mm" ou "dd/mm/aaaa" assumindo o ano atual quando omitido. */
    private LocalDate parseBrDate(String text) {
        String[] parts = text.split("/");
        int day = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int year = parts.length > 2 ? Integer.parseInt(parts[2]) : LocalDate.now().getYear();
        if (year < 100) {
            year += 2000;
        }
        return LocalDate.of(year, month, day);
    }
}
