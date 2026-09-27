import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { BackupService } from '../src/services/backupService.js';

function fakeRepository(tasks) {
    const saved = [];
    return {
        findAll: () => tasks,
        save: (t) => { saved.push(t); return t; },
        saved,
    };
}

test('exporta e reimporta tarefas preservando dados com virgula e aspas', () => {
    const task = {
        id: 1,
        title: 'Reunião, importante "urgente"',
        description: 'Descrição com, vírgula e "aspas"',
        dueDate: '2026-09-27',
        dueTime: '19:00',
        status: 'PENDING',
        priority: 'HIGH',
        recurrenceType: 'NONE',
        recurrenceValue: null,
        reminderIntervalMinutes: null,
        createdAt: '2026-09-27T10:00:00.000Z',
        updatedAt: '2026-09-27T10:00:00.000Z',
        completedAt: null,
    };
    const repo = fakeRepository([task]);
    const service = new BackupService(repo);

    const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'assistente-backup-'));
    const file = service.exportBackup(tmpDir);
    assert.ok(fs.existsSync(file));

    const count = service.importBackup(file);
    assert.equal(count, 1);
    assert.equal(repo.saved.length, 1);

    const restored = repo.saved[0];
    assert.equal(restored.title, 'Reunião, importante "urgente"');
    assert.equal(restored.description, 'Descrição com, vírgula e "aspas"');
    assert.equal(restored.priority, 'HIGH');
    assert.equal(restored.dueDate, '2026-09-27');
    assert.equal(restored.dueTime, '19:00');
});

test('importar arquivo com cabecalho invalido lanca excecao', () => {
    const repo = fakeRepository([]);
    const service = new BackupService(repo);
    const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'assistente-backup-'));
    const file = path.join(tmpDir, 'invalido.csv');
    fs.writeFileSync(file, 'coluna_errada,outra\n1,2\n', 'utf-8');

    assert.throws(() => service.importBackup(file));
});
