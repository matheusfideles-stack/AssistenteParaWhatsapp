import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { parseReminderText } from '../src/services/desktopNotifier.js';

describe('parseReminderText', () => {
    it('extrai titulo e corpo da mensagem de lembrete, sem os asteriscos de negrito', () => {
        const text = '*LEMBRETE*\nEstá na hora de: *Teste 123*\n17:14\n\nResponda: 1 Concluir | 2 Adiar 30min | 3 Cancelar';
        const { title, body } = parseReminderText(text);
        assert.equal(title, 'LEMBRETE');
        assert.equal(body, 'Está na hora de: Teste 123');
    });

    it('extrai titulo e corpo de uma tarefa atrasada', () => {
        const text = '*TAREFA ATRASADA*\nVocê ainda não concluiu: *Estudar Java*\nHorário: 19:00';
        const { title, body } = parseReminderText(text);
        assert.equal(title, 'TAREFA ATRASADA');
        assert.equal(body, 'Você ainda não concluiu: Estudar Java');
    });

    it('usa titulo padrao quando o texto so tem uma linha', () => {
        const { title, body } = parseReminderText('Lembrete simples');
        assert.equal(title, 'Lembrete simples');
        assert.equal(body, '');
    });
});
