import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

/**
 * Cliente HTTP para o backend Java (Spring Boot). O bot Node nao guarda mais
 * nenhuma tarefa localmente - so repassa o texto recebido no WhatsApp para
 * o backend e devolve a resposta ja formatada. Ver AssistentePessoal-Backend.
 */
export class ApiClient {
    constructor(baseUrl) {
        this.baseUrl = baseUrl.replace(/\/$/, '');
    }

    /** @returns {Promise<{text: string, clearActiveReminder: boolean, backupFileName: string|null, backupFileBase64: string|null}>} */
    async sendMessage(text, activeReminderId) {
        const res = await fetch(`${this.baseUrl}/api/message`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json; charset=utf-8' },
            body: JSON.stringify({ text, activeReminderId: activeReminderId ?? null }),
        });
        if (!res.ok) {
            throw new Error(`Backend respondeu ${res.status} ao processar mensagem`);
        }
        return res.json();
    }

    /** @returns {Promise<Array<{taskId: number, text: string}>>} */
    async fetchPendingReminders() {
        const res = await fetch(`${this.baseUrl}/api/reminders/pending`);
        if (!res.ok) {
            throw new Error(`Backend respondeu ${res.status} ao buscar lembretes`);
        }
        return res.json();
    }

    /** Salva o backup (recebido em base64) num arquivo temporario para anexar no WhatsApp. */
    saveBackupFile(fileName, base64Content) {
        const dir = path.join(os.tmpdir(), 'assistente-whatsapp-backups');
        fs.mkdirSync(dir, { recursive: true });
        const filePath = path.join(dir, fileName);
        fs.writeFileSync(filePath, Buffer.from(base64Content, 'base64'));
        return filePath;
    }
}
