import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import dayjs from 'dayjs';
import { DatabaseSync } from 'node:sqlite';
import { TaskRepository } from '../src/db/taskRepository.js';
import { TaskService } from '../src/services/taskService.js';

const CREATE_TABLE_SQL = `
CREATE TABLE tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    description TEXT,
    due_date TEXT,
    due_time TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    priority TEXT NOT NULL DEFAULT 'MEDIUM',
    recurrence_type TEXT NOT NULL DEFAULT 'NONE',
    recurrence_value TEXT,
    reminder_interval INTEGER,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    completed_at TEXT
);`;

function freshService() {
    const db = new DatabaseSync(':memory:');
    db.exec(CREATE_TABLE_SQL);
    const repository = new TaskRepository(db);
    return { service: new TaskService(repository), repository, db };
}

describe('TaskService', () => {
    let service;

    beforeEach(() => {
        ({ service } = freshService());
    });

    it('cria tarefa a partir de texto', () => {
        const task = service.createFromText('Hoje às 19h estudar Java');
        assert.equal(task.title, 'Estudar Java');
        assert.equal(task.dueTime, '19:00');
        assert.equal(task.status, 'PENDING');
        assert.ok(task.id);
    });

    it('edicao atualiza tarefa existente', () => {
        const task = service.createFromText('Original hoje às 10h');
        task.title = 'Editado';
        service.updateTask(task);
        const loaded = service.get(task.id);
        assert.equal(loaded.title, 'Editado');
    });

    it('edicao sem id lanca excecao', () => {
        assert.throws(() => service.updateTask({ title: 'Sem id' }));
    });

    it('conclusao marca status e data de conclusao', () => {
        const task = service.createFromText('Estudar hoje às 19h');
        const result = service.complete(task.id);
        assert.equal(result.status, 'COMPLETED');
        assert.ok(result.completedAt);
    });

    it('conclusao de tarefa recorrente diaria gera proxima ocorrencia', () => {
        const task = service.createTask({
            title: 'Estudar Java', dueDate: '2026-09-27', dueTime: '19:00',
            recurrenceType: 'DAILY',
        });
        service.complete(task.id);
        const all = service.findAll();
        const next = all.find((t) => t.id !== task.id);
        assert.ok(next, 'deveria ter gerado uma nova tarefa');
        assert.equal(next.dueDate, '2026-09-28');
        assert.equal(next.status, 'PENDING');
        assert.equal(next.recurrenceType, 'DAILY');
    });

    it('conclusao de tarefa recorrente semanal gera proxima ocorrencia', () => {
        const task = service.createTask({
            title: 'Reunião', dueDate: '2026-09-28', dueTime: '09:00', // uma segunda-feira
            recurrenceType: 'WEEKLY', recurrenceValue: '1',
        });
        service.complete(task.id);
        const next = service.findAll().find((t) => t.id !== task.id);
        assert.equal(next.dueDate, '2026-10-05');
    });

    it('conclusao de tarefa recorrente mensal faz clamp de dia inexistente', () => {
        const task = service.createTask({
            title: 'Pagar conta', dueDate: '2026-01-31', dueTime: '08:00',
            recurrenceType: 'MONTHLY', recurrenceValue: '31',
        });
        service.complete(task.id);
        const next = service.findAll().find((t) => t.id !== task.id);
        // Fevereiro de 2026 nao tem dia 31 -> cai no ultimo dia do mes (28).
        assert.equal(next.dueDate, '2026-02-28');
    });

    it('cancelamento marca status cancelled', () => {
        const task = service.createFromText('Tarefa hoje às 10h');
        const result = service.cancel(task.id);
        assert.equal(result.status, 'CANCELLED');
    });

    it('adiamento soma minutos ao horario agendado', () => {
        const hoje = dayjs().format('YYYY-MM-DD');
        const task = service.createTask({ title: 'Reunião', dueDate: hoje, dueTime: '23:50' });
        const result = service.postpone(task.id, 30);
        assert.equal(result.dueDate, dayjs(hoje).add(1, 'day').format('YYYY-MM-DD'));
        assert.equal(result.dueTime, '00:20');
        assert.equal(result.status, 'PENDING');
    });

    it('adiamento de tarefa ja atrasada conta a partir de agora', () => {
        const task = service.createTask({
            title: 'Antiga', dueDate: dayjs().subtract(2, 'day').format('YYYY-MM-DD'), dueTime: '08:00',
        });
        const result = service.postpone(task.id, 10);
        const due = dayjs(`${result.dueDate}T${result.dueTime}`);
        assert.ok(!due.isBefore(dayjs().subtract(5, 'second')));
    });

    it('busca tarefas atrasadas', () => {
        service.createTask({
            title: 'Atrasada', dueDate: dayjs().subtract(1, 'day').format('YYYY-MM-DD'), dueTime: '09:00',
        });
        const overdue = service.findOverdue();
        assert.equal(overdue.length, 1);
        assert.equal(overdue[0].title, 'Atrasada');
    });

    it('tarefa inexistente lanca excecao', () => {
        assert.throws(() => service.get(999));
    });

    it('criar tarefa direta aplica valores padrao', () => {
        const task = service.createTask({ title: 'Tarefa manual' });
        assert.equal(task.status, 'PENDING');
        assert.equal(task.priority, 'MEDIUM');
        assert.equal(task.recurrenceType, 'NONE');
    });
});
