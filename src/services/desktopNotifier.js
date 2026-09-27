import notifier from 'node-notifier';

/**
 * Notificacao nativa do Windows (toast) para cada lembrete, complementando
 * a mensagem no WhatsApp - util quando o celular esta longe mas o PC (onde
 * o bot roda) esta por perto. Usa o texto ja formatado para WhatsApp (com
 * *negrito* e emojis) e extrai so o essencial para a notificacao.
 */
export function notifyReminder(reminderText) {
    const { title, body } = parseReminderText(reminderText);
    notifier.notify({
        title,
        message: body,
        appID: 'Assistente Pessoal',
        sound: true,
    });
}

/** Exportada separadamente para ser testável sem disparar notificações reais do SO. */
export function parseReminderText(text) {
    const lines = text.split('\n').filter((l) => l.trim().length > 0);
    const title = stripFormatting(lines[0] || '🔔 Lembrete');
    const body = stripFormatting(lines[1] || '');
    return { title, body };
}

function stripFormatting(line) {
    return line.replace(/\*/g, '').trim();
}
