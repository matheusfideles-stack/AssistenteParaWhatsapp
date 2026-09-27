import { openDatabase } from './db/database.js';
import { TaskRepository } from './db/taskRepository.js';
import { TaskService } from './services/taskService.js';
import { BackupService } from './services/backupService.js';
import { ReminderScheduler } from './services/reminderScheduler.js';
import { createWhatsAppBot } from './bot/whatsappClient.js';
import { loadConfig } from './config.js';

async function main() {
    console.log('🤖 Assistente Pessoal para WhatsApp — iniciando...\n');

    const config = loadConfig();
    const db = openDatabase();
    const repository = new TaskRepository(db);
    const taskService = new TaskService(repository);
    const backupService = new BackupService(repository);

    const reminderScheduler = new ReminderScheduler(
        taskService,
        () => config.defaultReminderIntervalMinutes
    );

    const bot = createWhatsAppBot({ taskService, backupService, reminderScheduler });
    await bot.start();
    reminderScheduler.start();

    console.log('⏰ Agendador de lembretes ativo.');
    console.log('   Pressione Ctrl+C para encerrar.\n');

    const shutdown = () => {
        console.log('\n👋 Encerrando...');
        reminderScheduler.stop();
        db.close();
        process.exit(0);
    };
    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
}

main().catch((err) => {
    console.error('❌ Erro fatal ao iniciar o Assistente Pessoal:', err);
    process.exit(1);
});
