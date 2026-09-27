package com.assistente.scheduler;

import com.assistente.model.Task;
import com.assistente.model.TaskStatus;
import com.assistente.service.TaskService;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * Verifica periodicamente, usando um ScheduledExecutorService puro do Java
 * (sem dependencias externas, sem internet), quais tarefas venceram e devem
 * gerar um lembrete. Uma vez que uma tarefa vence, ela continua sendo
 * relembrada no intervalo configurado (o especifico da tarefa, se houver, ou
 * o intervalo padrao das configuracoes) ate ser concluida ou cancelada.
 */
public class ReminderScheduler {

    /** Reagida sempre que uma tarefa deve gerar um lembrete visivel/sonoro. */
    public interface ReminderListener {
        /** overdue = false na primeira vez (exatamente no horario); true nas repeticoes seguintes. */
        void onReminder(Task task, boolean overdue);
    }

    private static final long CHECK_INTERVAL_SECONDS = 15;

    private final TaskService taskService;
    private final IntSupplier defaultIntervalMinutesSupplier;
    private final Map<Long, LocalDateTime> nextFireAt = new ConcurrentHashMap<>();
    private volatile ReminderListener listener;
    private volatile boolean paused = false;

    private ScheduledExecutorService executor;

    public ReminderScheduler(TaskService taskService, IntSupplier defaultIntervalMinutesSupplier) {
        this.taskService = taskService;
        this.defaultIntervalMinutesSupplier = defaultIntervalMinutesSupplier;
    }

    public void setListener(ReminderListener listener) {
        this.listener = listener;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isPaused() {
        return paused;
    }

    public synchronized void start() {
        if (executor != null && !executor.isShutdown()) {
            return;
        }
        ThreadFactory daemonFactory = r -> {
            Thread t = new Thread(r, "reminder-scheduler");
            t.setDaemon(true);
            return t;
        };
        executor = Executors.newSingleThreadScheduledExecutor(daemonFactory);
        executor.scheduleAtFixedRate(this::safeCheck, 0, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** Forca uma verificacao imediata (util apos criar/editar uma tarefa na UI). */
    public void checkNow() {
        safeCheck();
    }

    private void safeCheck() {
        try {
            checkDueTasks();
        } catch (Exception e) {
            // Nunca deixa o agendador morrer por causa de um erro pontual (ex: MySQL temporariamente indisponivel).
            System.err.println("[ReminderScheduler] Erro ao verificar tarefas: " + e.getMessage());
        }
    }

    private void checkDueTasks() {
        if (paused) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        var pending = taskService.findPending();

        // Remove do controle tarefas que deixaram de estar pendentes.
        nextFireAt.keySet().retainAll(pending.stream().map(Task::getId).toList());

        for (Task task : pending) {
            if (task.getStatus() != TaskStatus.PENDING) {
                continue;
            }
            LocalDateTime due = task.getDueDateTime();
            if (due == null || now.isBefore(due)) {
                continue;
            }

            LocalDateTime scheduledNext = nextFireAt.get(task.getId());
            if (scheduledNext == null) {
                fire(task, false);
                nextFireAt.put(task.getId(), now.plusMinutes(effectiveInterval(task)));
            } else if (!now.isBefore(scheduledNext)) {
                fire(task, true);
                nextFireAt.put(task.getId(), now.plusMinutes(effectiveInterval(task)));
            }
        }
    }

    private int effectiveInterval(Task task) {
        Integer specific = task.getReminderIntervalMinutes();
        if (specific != null && specific > 0) {
            return specific;
        }
        int def = defaultIntervalMinutesSupplier.getAsInt();
        return def > 0 ? def : 30;
    }

    private void fire(Task task, boolean overdue) {
        ReminderListener l = listener;
        if (l != null) {
            l.onReminder(task, overdue);
        }
    }

    /** Chamado quando uma tarefa e concluida/cancelada fora do fluxo normal, para parar de relembrar na hora. */
    public void forget(long taskId) {
        nextFireAt.remove(taskId);
    }
}
