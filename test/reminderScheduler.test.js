import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import dayjs from 'dayjs';
import { ReminderScheduler } from '../src/services/reminderScheduler.js';

function overdueTask(id) {
    return {
        id,
        title: `Tarefa ${id}`,
        dueDate: dayjs().format('YYYY-MM-DD'),
        dueTime: dayjs().subtract(5, 'minute').format('HH:mm'),
        status: 'PENDING',
    };
}

function futureTask(id) {
    return {
        id,
        title: `Futura ${id}`,
        dueDate: dayjs().format('YYYY-MM-DD'),
        dueTime: dayjs().add(3, 'hour').format('HH:mm'),
        status: 'PENDING',
    };
}

describe('ReminderScheduler', () => {
    let pending;
    let scheduler;

    beforeEach(() => {
        pending = [];
        const taskService = { findPending: () => pending };
        scheduler = new ReminderScheduler(taskService, () => 30);
    });

    it('dispara lembrete para tarefa vencida', () => {
        pending.push(overdueTask(1));
        const fired = [];
        scheduler.setListener((task, overdue) => fired.push(overdue));

        scheduler.checkNow();

        assert.equal(fired.length, 1);
        assert.equal(fired[0], false); // primeira vez: nao e "atrasada"
    });

    it('nao dispara para tarefa ainda no futuro', () => {
        pending.push(futureTask(2));
        let count = 0;
        scheduler.setListener(() => count++);

        scheduler.checkNow();

        assert.equal(count, 0);
    });

    it('nao repete antes do intervalo configurado', () => {
        pending.push(overdueTask(3));
        let count = 0;
        scheduler.setListener(() => count++);

        scheduler.checkNow();
        scheduler.checkNow();
        scheduler.checkNow();

        assert.equal(count, 1);
    });

    it('repete como atrasada quando intervalo ja passou', () => {
        const task = overdueTask(4);
        pending.push(task);
        const fired = [];
        scheduler.setListener((t, overdue) => fired.push(overdue));

        scheduler.checkNow(); // 1a vez: normal
        scheduler.nextFireAt.set(task.id, dayjs().subtract(1, 'minute')); // simula intervalo decorrido
        scheduler.checkNow(); // agora deve disparar como atrasada

        assert.deepEqual(fired, [false, true]);
    });

    it('para de lembrar quando a tarefa sai da lista de pendentes', () => {
        const task = overdueTask(5);
        pending.push(task);
        let count = 0;
        scheduler.setListener(() => count++);

        scheduler.checkNow();
        pending.length = 0; // tarefa foi concluida em outro lugar
        scheduler.checkNow();

        assert.equal(count, 1);
    });

    it('pausado nao dispara lembretes', () => {
        pending.push(overdueTask(6));
        let count = 0;
        scheduler.setListener(() => count++);
        scheduler.setPaused(true);

        scheduler.checkNow();

        assert.equal(count, 0);
    });
});
