package com.assistente.backend;

import com.assistente.model.Task;
import com.assistente.scheduler.ReminderScheduler;
import com.assistente.util.DateTimeUtil;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Liga o ReminderScheduler (que roda em background, verificando tarefas
 * vencidas a cada 15s) a uma fila de mensagens ja formatadas. O bot do
 * WhatsApp (em Node.js) consulta essa fila por polling em GET
 * /api/reminders/pending e a esvazia a cada consulta.
 */
@Service
public class ReminderQueueService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ReminderScheduler scheduler;
    private final ConcurrentLinkedQueue<ReminderMessage> pending = new ConcurrentLinkedQueue<>();

    public ReminderQueueService(ReminderScheduler scheduler) {
        this.scheduler = scheduler;
        this.scheduler.setListener(this::onReminder);
    }

    @PostConstruct
    public void start() {
        scheduler.start();
    }

    @PreDestroy
    public void stop() {
        scheduler.stop();
    }

    private void onReminder(Task task, boolean overdue) {
        String time = task.getDueTime() == null ? "--:--" : task.getDueTime().format(TIME_FMT);
        String header = overdue ? "⚠️ *TAREFA ATRASADA*" : "🔔 *LEMBRETE*";
        String body = overdue
                ? "Você ainda não concluiu:\n*" + task.getTitle() + "*\n⏰ Horário: " + time
                : "Está na hora de:\n*" + task.getTitle() + "*\n⏰ " + time;
        String text = header + "\n" + body
                + "\n\nResponda:\n1️⃣ Concluir\n2️⃣ Adiar 30 min\n3️⃣ Cancelar"
                + "\n\n(ou use: concluir #" + task.getId() + " / adiar #" + task.getId()
                + " <min> / cancelar #" + task.getId() + ")";
        pending.add(new ReminderMessage(task.getId(), text));
    }

    /** Retorna e remove todos os lembretes pendentes de envio. */
    public List<ReminderMessage> drainPending() {
        List<ReminderMessage> drained = new ArrayList<>();
        ReminderMessage msg;
        while ((msg = pending.poll()) != null) {
            drained.add(msg);
        }
        return drained;
    }

    public record ReminderMessage(long taskId, String text) {
    }
}
