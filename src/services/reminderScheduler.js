import dayjs from 'dayjs';

const CHECK_INTERVAL_MS = 15_000;

/**
 * Verifica periodicamente (setInterval puro do Node, sem dependencias) quais
 * tarefas venceram e devem gerar um lembrete. Uma vez vencida, a tarefa
 * continua sendo relembrada no intervalo configurado (o especifico dela, se
 * houver, ou o intervalo padrao) ate ser concluida ou cancelada.
 */
export class ReminderScheduler {
    constructor(taskService, getDefaultIntervalMinutes) {
        this.taskService = taskService;
        this.getDefaultIntervalMinutes = getDefaultIntervalMinutes;
        this.nextFireAt = new Map(); // taskId -> dayjs
        this.listener = null;
        this.paused = false;
        this.timer = null;
    }

    setListener(listener) {
        this.listener = listener;
    }

    setPaused(paused) {
        this.paused = paused;
    }

    isPaused() {
        return this.paused;
    }

    start() {
        if (this.timer) return;
        this.timer = setInterval(() => this.#safeCheck(), CHECK_INTERVAL_MS);
        this.timer.unref?.();
    }

    stop() {
        if (this.timer) {
            clearInterval(this.timer);
            this.timer = null;
        }
    }

    /** Forca uma verificacao imediata (usado nos testes e apos criar/editar uma tarefa). */
    checkNow() {
        this.#safeCheck();
    }

    forget(taskId) {
        this.nextFireAt.delete(taskId);
    }

    #safeCheck() {
        try {
            this.#checkDueTasks();
        } catch (err) {
            console.error('[ReminderScheduler] Erro ao verificar tarefas:', err.message);
        }
    }

    #checkDueTasks() {
        if (this.paused) return;
        const now = dayjs();
        const pending = this.taskService.findPending();
        const pendingIds = new Set(pending.map((t) => t.id));

        for (const id of [...this.nextFireAt.keys()]) {
            if (!pendingIds.has(id)) this.nextFireAt.delete(id);
        }

        for (const task of pending) {
            if (!task.dueDate) continue;
            const due = dayjs(`${task.dueDate}T${task.dueTime || '00:00'}`);
            if (now.isBefore(due)) continue;

            const scheduled = this.nextFireAt.get(task.id);
            if (!scheduled) {
                this.#fire(task, false);
                this.nextFireAt.set(task.id, now.add(this.#effectiveInterval(task), 'minute'));
            } else if (!now.isBefore(scheduled)) {
                this.#fire(task, true);
                this.nextFireAt.set(task.id, now.add(this.#effectiveInterval(task), 'minute'));
            }
        }
    }

    #effectiveInterval(task) {
        if (task.reminderIntervalMinutes && task.reminderIntervalMinutes > 0) {
            return task.reminderIntervalMinutes;
        }
        const def = this.getDefaultIntervalMinutes();
        return def > 0 ? def : 30;
    }

    #fire(task, overdue) {
        if (this.listener) this.listener(task, overdue);
    }
}
