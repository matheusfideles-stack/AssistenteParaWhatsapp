import { ApiClient } from './services/apiClient.js';
import { CommandHandler } from './bot/commandHandler.js';
import { createWhatsAppBot } from './bot/whatsappClient.js';
import { loadConfig } from './config.js';

async function main() {
    console.log('🤖 Assistente Pessoal para WhatsApp — iniciando...\n');

    const config = loadConfig();
    const apiClient = new ApiClient(config.backendUrl);
    const commandHandler = new CommandHandler(apiClient);

    await checkBackend(apiClient, config.backendUrl);

    const bot = createWhatsAppBot({ commandHandler, apiClient });
    await bot.start();

    console.log('   Pressione Ctrl+C para encerrar.\n');

    process.on('SIGINT', () => {
        console.log('\n👋 Encerrando...');
        process.exit(0);
    });
    process.on('SIGTERM', () => {
        process.exit(0);
    });
}

/** Avisa cedo se o backend Java nao estiver rodando, em vez de falhar so quando a primeira mensagem chegar. */
async function checkBackend(apiClient, backendUrl) {
    try {
        await apiClient.fetchPendingReminders();
        console.log(`✅ Backend conectado em ${backendUrl}`);
    } catch (err) {
        console.log(`⚠️  Não consegui falar com o backend em ${backendUrl} (${err.message}).`);
        console.log('   Rode o AssistentePessoal-Backend antes (mvn spring-boot:run) - o bot funciona,');
        console.log('   mas nenhuma tarefa será criada até o backend estar disponível.\n');
    }
}

main().catch((err) => {
    console.error('❌ Erro fatal ao iniciar o Assistente Pessoal:', err);
    process.exit(1);
});
