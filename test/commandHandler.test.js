import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import dayjs from 'dayjs';
import { DatabaseSync } from 'node:sqlite';
import { TaskRepository } from '../src/db/taskRepository.js';
import { TaskService } from '../src/services/taskService.js';
import { BackupService } from '../src/services/backupService.js';
import { CommandHandler } from '../src/bot/commandHandler.js';

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

describe('CommandHandler', () => {
    let handler;
    let taskService;

    beforeEach(() => {
        const db = new DatabaseSync(':memory:');
        db.exec(CREATE_TABLE_SQL);
        const repository = new TaskRepository(db);
        taskService = new TaskService(repository);
        const backupService = new BackupService(repository);
        handler = new CommandHandler(taskService, backupService);
    });

    it('texto livre cria uma tarefa', () => {
        const result = handler.handle('Hoje às 19h estudar Java');
        assert.match(result.text, /Tarefa criada/);
        assert.match(result.text, /Estudar Java/);
        assert.equal(taskService.findAll().length, 1);
    });

    it('ajuda mostra o menu de comandos', () => {
        const result = handler.handle('ajuda');
        assert.match(result.text, /comandos/i);
    });

    it('tarefas lista as tarefas de hoje', () => {
        handler.handle('Hoje às 19h estudar Java');
        const result = handler.handle('tarefas');
        assert.match(result.text, /TAREFAS DE HOJE/);
        assert.match(result.text, /Estudar Java/);
    });

    it('lista vazia informa que nao ha tarefas', () => {
        const result = handler.handle('atrasadas');
        assert.match(result.text, /Nenhuma tarefa encontrada/);
    });

    it('concluir por id marca a tarefa como concluida', () => {
        handler.handle('Hoje às 19h estudar Java');
        const task = taskService.findAll()[0];
        const result = handler.handle(`concluir #${task.id}`);
        assert.match(result.text, /concluída/);
        assert.equal(taskService.get(task.id).status, 'COMPLETED');
    });

    it('concluir id inexistente retorna erro amigavel', () => {
        const result = handler.handle('concluir #999');
        assert.match(result.text, /⚠️/);
    });

    it('cancelar por id marca como cancelada', () => {
        handler.handle('Hoje às 19h estudar Java');
        const task = taskService.findAll()[0];
        const result = handler.handle(`cancelar #${task.id}`);
        assert.match(result.text, /cancelada/);
        assert.equal(taskService.get(task.id).status, 'CANCELLED');
    });

    it('adiar por id e minutos reagenda a tarefa', () => {
        handler.handle('Hoje às 19h estudar Java');
        const task = taskService.findAll()[0];
        const result = handler.handle(`adiar #${task.id} 30`);
        assert.match(result.text, /adiada/);
        assert.equal(taskService.get(task.id).dueTime, '19:30');
    });

    it('excluir por id remove a tarefa', () => {
        handler.handle('Hoje às 19h estudar Java');
        const task = taskService.findAll()[0];
        handler.handle(`excluir #${task.id}`);
        assert.equal(taskService.find(task.id), null);
    });

    it('resposta numerica 1 conclui o lembrete ativo', () => {
        handler.handle('Hoje às 19h estudar Java');
        const task = taskService.findAll()[0];
        const result = handler.handle('1', { activeReminderId: task.id });
        assert.match(result.text, /concluída/);
        assert.equal(result.clearActiveReminder, true);
        assert.equal(taskService.get(task.id).status, 'COMPLETED');
    });

    it('resposta numerica 2 adia o lembrete ativo em 30 minutos', () => {
        handler.handle('Hoje às 19h estudar Java');
        const task = taskService.findAll()[0];
        handler.handle('2', { activeReminderId: task.id });
        assert.equal(taskService.get(task.id).dueTime, '19:30');
    });

    it('resposta numerica 3 cancela o lembrete ativo', () => {
        handler.handle('Hoje às 19h estudar Java');
        const task = taskService.findAll()[0];
        handler.handle('3', { activeReminderId: task.id });
        assert.equal(taskService.get(task.id).status, 'CANCELLED');
    });

    it('sem lembrete ativo, "1" sozinho vira tarefa em vez de comando', () => {
        const result = handler.handle('1');
        // sem activeReminderId, "1" nao bate em nenhum comando conhecido -> vira criacao de tarefa
        assert.match(result.text, /Tarefa criada|Não consegui entender/);
    });

    it('buscar encontra por termo', () => {
        handler.handle('Hoje às 19h estudar Java avançado');
        const result = handler.handle('buscar Java');
        assert.match(result.text, /RESULTADOS PARA/);
        assert.match(result.text, /Java avançado/);
    });

    it('reagendar altera data e hora', () => {
        handler.handle('Hoje às 19h estudar Java');
        const task = taskService.findAll()[0];
        const amanha = dayjs().add(1, 'day');
        const result = handler.handle(`reagendar #${task.id} ${amanha.format('DD/MM/YYYY')} 08:00`);
        assert.match(result.text, /reagendada/);
        const updated = taskService.get(task.id);
        assert.equal(updated.dueDate, amanha.format('YYYY-MM-DD'));
        assert.equal(updated.dueTime, '08:00');
    });
});
