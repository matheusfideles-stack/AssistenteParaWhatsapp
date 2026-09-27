import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { ApiClient } from '../src/services/apiClient.js';

describe('ApiClient', () => {
    let originalFetch;
    let calls;

    beforeEach(() => {
        originalFetch = global.fetch;
        calls = [];
    });

    afterEach(() => {
        global.fetch = originalFetch;
    });

    function stubFetch(response, ok = true, status = 200) {
        global.fetch = async (url, options) => {
            calls.push({ url, options });
            return { ok, status, json: async () => response };
        };
    }

    it('sendMessage faz POST em /api/message com o texto e activeReminderId', async () => {
        stubFetch({ text: 'ok', clearActiveReminder: false });
        const client = new ApiClient('http://localhost:8080');

        const result = await client.sendMessage('hoje às 19h estudar java', 5);

        assert.equal(calls.length, 1);
        assert.equal(calls[0].url, 'http://localhost:8080/api/message');
        assert.equal(calls[0].options.method, 'POST');
        const body = JSON.parse(calls[0].options.body);
        assert.equal(body.text, 'hoje às 19h estudar java');
        assert.equal(body.activeReminderId, 5);
        assert.equal(result.text, 'ok');
    });

    it('sendMessage envia activeReminderId null quando omitido', async () => {
        stubFetch({ text: 'ok' });
        const client = new ApiClient('http://localhost:8080');

        await client.sendMessage('ajuda');

        const body = JSON.parse(calls[0].options.body);
        assert.equal(body.activeReminderId, null);
    });

    it('sendMessage lanca erro quando o backend responde com status de erro', async () => {
        stubFetch({}, false, 500);
        const client = new ApiClient('http://localhost:8080');

        await assert.rejects(() => client.sendMessage('oi'), /500/);
    });

    it('fetchPendingReminders faz GET em /api/reminders/pending', async () => {
        stubFetch([{ taskId: 1, text: '🔔 LEMBRETE' }]);
        const client = new ApiClient('http://localhost:8080/');

        const reminders = await client.fetchPendingReminders();

        assert.equal(calls[0].url, 'http://localhost:8080/api/reminders/pending');
        assert.equal(reminders.length, 1);
        assert.equal(reminders[0].taskId, 1);
    });

    it('saveBackupFile decodifica o base64 e salva num arquivo temporario', () => {
        const client = new ApiClient('http://localhost:8080');
        const content = 'id,title\n1,Teste';
        const base64 = Buffer.from(content, 'utf-8').toString('base64');

        const filePath = client.saveBackupFile('backup-teste.csv', base64);

        assert.ok(fs.existsSync(filePath));
        assert.equal(fs.readFileSync(filePath, 'utf-8'), content);
        fs.unlinkSync(filePath);
    });
});
