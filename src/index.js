import { ApiClient } from './services/apiClient.js';
import { ensureBackendRunning } from './services/backendLauncher.js';
import { redirectSessionNoiseToFile } from './services/sessionLogFilter.js';
import { CommandHandler } from './bot/commandHandler.js';
import { createWhatsAppBot } from './bot/whatsappClient.js';
import { loadConfig } from './config.js';

async function main() {
    redirectSessionNoiseToFile();
    console.log('🤖 Assistente Pessoal para WhatsApp — iniciando...\n');

    const config = loadConfig();
    const apiClient = new ApiClient(config.backendUrl);
    const commandHandler = new CommandHandler(apiClient);

    const backendProcess = await ensureBackendRunning(apiClient, config.backendUrl);

    const bot = createWhatsAppBot({ commandHandler, apiClient });
    await bot.start();

    console.log('   Pressione Ctrl+C para encerrar.\n');

    const shutdown = () => {
        console.log('\n👋 Encerrando...');
        if (backendProcess) {
            backendProcess.kill();
        }
        process.exit(0);
    };
    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
}

main().catch((err) => {
    console.error('❌ Erro fatal ao iniciar o Assistente Pessoal:', err.message);
    process.exit(1);
});
