import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { CommandHandler } from '../src/bot/commandHandler.js';

function fakeApiClient(sendMessageResult) {
    const calls = { sendMessage: [], saveBackupFile: [] };
    return {
        calls,
        async sendMessage(text, activeReminderId) {
            calls.sendMessage.push({ text, activeReminderId });
            return sendMessageResult;
        },
        saveBackupFile(fileName, base64) {
            calls.saveBackupFile.push({ fileName, base64 });
            return `/tmp/${fileName}`;
        },
    };
}

describe('CommandHandler (proxy para o backend Java)', () => {
    it('repassa o texto e o activeReminderId para o backend', async () => {
        const api = fakeApiClient({ text: 'ok', clearActiveReminder: false });
        const handler = new CommandHandler(api);

        await handler.handle('Hoje às 19h estudar Java', { activeReminderId: 42 });

        assert.equal(api.calls.sendMessage.length, 1);
        assert.equal(api.calls.sendMessage[0].text, 'Hoje às 19h estudar Java');
        assert.equal(api.calls.sendMessage[0].activeReminderId, 42);
    });

    it('usa null quando nao ha activeReminderId', async () => {
        const api = fakeApiClient({ text: 'ok', clearActiveReminder: false });
        const handler = new CommandHandler(api);

        await handler.handle('ajuda');

        assert.equal(api.calls.sendMessage[0].activeReminderId, null);
    });

    it('devolve o texto e clearActiveReminder da resposta do backend', async () => {
        const api = fakeApiClient({ text: 'Tarefa concluída!', clearActiveReminder: true });
        const handler = new CommandHandler(api);

        const result = await handler.handle('1', { activeReminderId: 7 });

        assert.equal(result.text, 'Tarefa concluída!');
        assert.equal(result.clearActiveReminder, true);
        assert.equal(result.filePath, undefined);
    });

    it('quando o backend manda um backup, salva o arquivo e inclui filePath', async () => {
        const api = fakeApiClient({
            text: 'Backup exportado!',
            clearActiveReminder: false,
            backupFileName: 'backup-2026-09-27.csv',
            backupFileBase64: 'aWQsdGl0dWxv',
        });
        const handler = new CommandHandler(api);

        const result = await handler.handle('backup');

        assert.equal(result.filePath, '/tmp/backup-2026-09-27.csv');
        assert.equal(api.calls.saveBackupFile.length, 1);
        assert.equal(api.calls.saveBackupFile[0].fileName, 'backup-2026-09-27.csv');
        assert.equal(api.calls.saveBackupFile[0].base64, 'aWQsdGl0dWxv');
    });

    it('nao tenta salvar arquivo quando o backend nao manda backup', async () => {
        const api = fakeApiClient({ text: 'sem backup', clearActiveReminder: false });
        const handler = new CommandHandler(api);

        await handler.handle('tarefas');

        assert.equal(api.calls.saveBackupFile.length, 0);
    });
});
