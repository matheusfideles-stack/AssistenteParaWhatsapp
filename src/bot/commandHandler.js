/**
 * Repassa a mensagem recebida no WhatsApp para o backend Java (que tem toda
 * a logica de comandos, parser de linguagem natural e formatacao de
 * respostas) e traduz a resposta para o formato que o whatsappClient espera.
 */
export class CommandHandler {
    constructor(apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * @param {string} text texto recebido no chat
     * @param {{activeReminderId?: number}} context
     * @returns {Promise<{text: string, filePath?: string, clearActiveReminder?: boolean}>}
     */
    async handle(text, context = {}) {
        const response = await this.apiClient.sendMessage(text, context.activeReminderId ?? null);
        const result = {
            text: response.text,
            clearActiveReminder: Boolean(response.clearActiveReminder),
        };
        if (response.backupFileBase64) {
            result.filePath = this.apiClient.saveBackupFile(response.backupFileName, response.backupFileBase64);
        }
        return result;
    }
}
