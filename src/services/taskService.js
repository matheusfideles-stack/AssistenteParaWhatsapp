import dayjs from 'dayjs';
import { parseTask } from '../parser/taskParser.js';

/** Regras de negocio de tarefas: criacao por linguagem natural, conclusao, */
/** cancelamento, adiamento, reagendamento e recorrencia. */
export class TaskService {
    constructor(repository) {
        this.repository = repository;
    }

    createFromText(text) {
        const parsed = parseTask(text);
        const task = {
            title: parsed.title,
            dueDate: parsed.date,
            dueTime: parsed.time,
            recurrenceType: parsed.recurrenceType,
            recurrenceValue: parsed.recurrenceValue,
            reminderIntervalMinutes: parsed.reminderIntervalMinutes,
            priority: parsed.priority,
            status: 'PENDING',
        };
        return this.repository.save(task);
    }

    createTask(task) {
        task.status = task.status || 'PENDING';
        task.priority = task.priority || 'MEDIUM';
        task.recurrenceType = task.recurrenceType || 'NONE';
        return this.repository.save(task);
    }

    updateTask(task) {
        if (!task.id) {
            throw new Error('Tarefa sem id não pode ser atualizada.');
        }
        return this.repository.save(task);
    }

    deleteTask(id) {
        this.repository.deleteById(id);
    }

    /** Marca como concluida e, se for recorrente, agenda automaticamente a proxima ocorrencia. */
    complete(id) {
        const task = this.get(id);
        task.status = 'COMPLETED';
        task.completedAt = dayjs().toISOString();
        this.repository.save(task);

        if (task.recurrenceType && task.recurrenceType !== 'NONE') {
            this.generateNextOccurrence(task);
        }
        return task;
    }

    cancel(id) {
        const task = this.get(id);
        task.status = 'CANCELLED';
        this.repository.save(task);
        return task;
    }

    /** Adia a tarefa em N minutos a partir do horario agendado (ou de agora, se ja atrasada). */
    postpone(id, minutes) {
        const task = this.get(id);
        const now = dayjs();
        let base = task.dueDate ? dayjs(`${task.dueDate}T${task.dueTime || '00:00'}`) : now;
        if (base.isBefore(now)) {
            base = now;
        }
        const target = base.add(minutes, 'minute');
        task.dueDate = target.format('YYYY-MM-DD');
        task.dueTime = target.format('HH:mm');
        task.status = 'PENDING';
        this.repository.save(task);
        return task;
    }

    reschedule(id, dateStr, timeStr) {
        const task = this.get(id);
        task.dueDate = dateStr;
        task.dueTime = timeStr;
        task.status = 'PENDING';
        this.repository.save(task);
        return task;
    }

    /** Gera a proxima ocorrencia de uma tarefa recorrente concluida, como nova tarefa PENDING. */
    generateNextOccurrence(completed) {
        const base = completed.dueDate ? dayjs(completed.dueDate) : dayjs();
        let nextDate;
        if (completed.recurrenceType === 'DAILY') {
            nextDate = base.add(1, 'day');
        } else if (completed.recurrenceType === 'WEEKLY') {
            nextDate = base.add(1, 'week');
        } else if (completed.recurrenceType === 'MONTHLY') {
            const day = completed.recurrenceValue ? parseInt(completed.recurrenceValue, 10) : base.date();
            const nextMonth = base.add(1, 'month').date(1);
            nextDate = nextMonth.date(Math.min(day, nextMonth.daysInMonth()));
        } else {
            return null;
        }

        const next = {
            title: completed.title,
            description: completed.description,
            dueDate: nextDate.format('YYYY-MM-DD'),
            dueTime: completed.dueTime,
            priority: completed.priority,
            recurrenceType: completed.recurrenceType,
            recurrenceValue: completed.recurrenceValue,
            reminderIntervalMinutes: completed.reminderIntervalMinutes,
            status: 'PENDING',
        };
        return this.repository.save(next);
    }

    get(id) {
        const task = this.repository.findById(id);
        if (!task) {
            throw new Error(`Tarefa não encontrada: id=${id}`);
        }
        return task;
    }

    find(id) {
        return this.repository.findById(id);
    }

    findAll() {
        return this.repository.findAll();
    }

    findToday() {
        return this.repository.findByDate(dayjs().format('YYYY-MM-DD'));
    }

    findTomorrow() {
        return this.repository.findByDate(dayjs().add(1, 'day').format('YYYY-MM-DD'));
    }

    findByDate(dateStr) {
        return this.repository.findByDate(dateStr);
    }

    findPending() {
        return this.repository.findPending();
    }

    findCompleted() {
        return this.repository.findByStatus('COMPLETED');
    }

    findOverdue() {
        return this.repository.findOverdue(dayjs().toISOString());
    }

    findHighPriority() {
        return this.repository.findPending().filter((t) => t.priority === 'HIGH');
    }

    search(term) {
        if (!term || !term.trim()) return this.findAll();
        return this.repository.search(term.trim());
    }

    findNextUpcoming() {
        const now = dayjs();
        const upcoming = this.repository.findPending()
            .filter((t) => t.dueDate)
            .map((t) => ({ task: t, due: dayjs(`${t.dueDate}T${t.dueTime || '00:00'}`) }))
            .filter(({ due }) => !due.isBefore(now))
            .sort((a, b) => a.due.valueOf() - b.due.valueOf());
        return upcoming.length ? upcoming[0].task : null;
    }
}
