import dayjs from 'dayjs';

/** Acesso a tabela "tasks" no SQLite via node:sqlite (nativo do Node, sincrono). */
export class TaskRepository {
    constructor(db) {
        this.db = db;
    }

    save(task) {
        if (task.id) {
            return this.#update(task);
        }
        return this.#insert(task);
    }

    #insert(task) {
        const now = dayjs().toISOString();
        task.createdAt = task.createdAt || now;
        task.updatedAt = now;
        const stmt = this.db.prepare(`
            INSERT INTO tasks
                (title, description, due_date, due_time, status, priority,
                 recurrence_type, recurrence_value, reminder_interval,
                 created_at, updated_at, completed_at)
            VALUES (@title, @description, @dueDate, @dueTime, @status, @priority,
                    @recurrenceType, @recurrenceValue, @reminderIntervalMinutes,
                    @createdAt, @updatedAt, @completedAt)
        `);
        const info = stmt.run(toInsertRow(task));
        task.id = info.lastInsertRowid;
        return task;
    }

    #update(task) {
        task.updatedAt = dayjs().toISOString();
        const stmt = this.db.prepare(`
            UPDATE tasks SET title=@title, description=@description, due_date=@dueDate,
                due_time=@dueTime, status=@status, priority=@priority,
                recurrence_type=@recurrenceType, recurrence_value=@recurrenceValue,
                reminder_interval=@reminderIntervalMinutes, created_at=@createdAt,
                updated_at=@updatedAt, completed_at=@completedAt
            WHERE id=@id
        `);
        stmt.run(toUpdateRow(task));
        return task;
    }

    deleteById(id) {
        this.db.prepare('DELETE FROM tasks WHERE id=?').run(id);
    }

    findById(id) {
        const row = this.db.prepare('SELECT * FROM tasks WHERE id=?').get(id);
        return row ? fromRow(row) : null;
    }

    findAll() {
        const rows = this.db.prepare(
            'SELECT * FROM tasks ORDER BY due_date IS NULL, due_date, due_time IS NULL, due_time, id'
        ).all();
        return rows.map(fromRow);
    }

    findByDate(dateStr) {
        const rows = this.db.prepare(
            'SELECT * FROM tasks WHERE due_date=? ORDER BY due_time IS NULL, due_time, id'
        ).all(dateStr);
        return rows.map(fromRow);
    }

    findByStatus(status) {
        const rows = this.db.prepare(
            'SELECT * FROM tasks WHERE status=? ORDER BY due_date IS NULL, due_date, due_time IS NULL, due_time, id'
        ).all(status);
        return rows.map(fromRow);
    }

    findPending() {
        return this.findByStatus('PENDING');
    }

    findOverdue(nowIso) {
        const now = dayjs(nowIso);
        return this.findPending().filter((t) => {
            if (!t.dueDate) return false;
            const due = dayjs(`${t.dueDate}T${t.dueTime || '00:00'}`);
            return due.isBefore(now);
        });
    }

    search(term) {
        const like = `%${term}%`;
        const rows = this.db.prepare(
            `SELECT * FROM tasks WHERE title LIKE ? OR description LIKE ?
             ORDER BY due_date IS NULL, due_date, due_time IS NULL, due_time, id`
        ).all(like, like);
        return rows.map(fromRow);
    }
}

// node:sqlite (diferente do better-sqlite3) rejeita parametros nomeados que
// nao aparecem no SQL da consulta ("Unknown named parameter") - por isso o
// INSERT (sem @id, ja que e auto-incremento) e o UPDATE (com @id) usam
// objetos de parametros distintos, em vez de um unico toRow() compartilhado.

function toInsertRow(task) {
    return {
        title: task.title,
        description: task.description ?? null,
        dueDate: task.dueDate ?? null,
        dueTime: task.dueTime ?? null,
        status: task.status ?? 'PENDING',
        priority: task.priority ?? 'MEDIUM',
        recurrenceType: task.recurrenceType ?? 'NONE',
        recurrenceValue: task.recurrenceValue ?? null,
        reminderIntervalMinutes: task.reminderIntervalMinutes ?? null,
        createdAt: task.createdAt,
        updatedAt: task.updatedAt,
        completedAt: task.completedAt ?? null,
    };
}

function toUpdateRow(task) {
    return { ...toInsertRow(task), id: task.id };
}

function fromRow(row) {
    return {
        id: row.id,
        title: row.title,
        description: row.description,
        dueDate: row.due_date,
        dueTime: row.due_time,
        status: row.status,
        priority: row.priority,
        recurrenceType: row.recurrence_type,
        recurrenceValue: row.recurrence_value,
        reminderIntervalMinutes: row.reminder_interval,
        createdAt: row.created_at,
        updatedAt: row.updated_at,
        completedAt: row.completed_at,
    };
}
