import os from 'node:os';
import path from 'node:path';
import { friendlyDate, formatTime } from '../utils/dateUtil.js';

const PRIORITY_EMOJI = { HIGH: '🔴', MEDIUM: '🟡', LOW: '⚪' };

const HELP_TEXT = `🤖 *Assistente Pessoal - comandos*

Para criar uma tarefa, é só escrever normalmente, por exemplo:
_Hoje às 19h estudar Java_
_Amanhã às 8h academia_
_Todo dia às 20h estudar Java_
_Beber água a cada 30 minutos até eu concluir_

*Consultar:*
📋 tarefas / hoje — tarefas de hoje
📅 amanhã — tarefas de amanhã
📋 todas — todas as tarefas
⏳ pendentes
✅ concluidas
⚠️ atrasadas
🔴 prioridade — alta prioridade
🔍 buscar <termo>

*Ações* (use o número #id que aparece na lista):
✅ concluir <id>
❌ cancelar <id>
🗑️ excluir <id>
⏰ adiar <id> <minutos>
📅 reagendar <id> <dd/mm[/aaaa]> <hh:mm>

*Outros:*
💾 backup — exporta suas tarefas em .csv
❓ ajuda — mostra esta mensagem

Quando um lembrete chegar, responda *1* para concluir, *2* para adiar 30 min ou *3* para cancelar.`;

export class CommandHandler {
    constructor(taskService, backupService) {
        this.taskService = taskService;
        this.backupService = backupService;
    }

    /**
     * @param {string} rawText texto recebido no chat
     * @param {{activeReminderId?: number}} context
     * @returns {{text: string, filePath?: string, clearActiveReminder?: boolean}}
     */
    handle(rawText, context = {}) {
        const text = (rawText || '').trim();
        if (!text) {
            return { text: 'Não entendi. Digite *ajuda* para ver os comandos.' };
        }
        const lower = text.toLowerCase();

        // Resposta numerica rapida a um lembrete ativo (1=concluir, 2=adiar 30, 3=cancelar)
        if (context.activeReminderId && /^[123]$/.test(text)) {
            return this.#handleReminderShortcut(text, context.activeReminderId);
        }

        if (['ajuda', 'menu', 'help', '?'].includes(lower)) {
            return { text: HELP_TEXT };
        }
        if (['tarefas', 'hoje'].includes(lower)) {
            return { text: this.#listTasks(this.taskService.findToday(), '📅 TAREFAS DE HOJE') };
        }
        if (lower === 'amanha' || lower === 'amanhã') {
            return { text: this.#listTasks(this.taskService.findTomorrow(), '📅 TAREFAS DE AMANHÃ') };
        }
        if (lower === 'todas') {
            return { text: this.#listTasks(this.taskService.findAll(), '📋 TODAS AS TAREFAS') };
        }
        if (lower === 'pendentes') {
            return { text: this.#listTasks(this.taskService.findPending(), '⏳ TAREFAS PENDENTES') };
        }
        if (['concluidas', 'concluídas'].includes(lower)) {
            return { text: this.#listTasks(this.taskService.findCompleted(), '✅ TAREFAS CONCLUÍDAS') };
        }
        if (lower === 'atrasadas') {
            return { text: this.#listTasks(this.taskService.findOverdue(), '⚠️ TAREFAS ATRASADAS') };
        }
        if (['prioridade', 'urgentes', 'prioridades'].includes(lower)) {
            return { text: this.#listTasks(this.taskService.findHighPriority(), '🔴 ALTA PRIORIDADE') };
        }
        if (lower === 'backup') {
            return this.#exportBackup();
        }

        let m;
        if ((m = /^(buscar|pesquisar)\s+(.+)$/i.exec(text))) {
            return { text: this.#listTasks(this.taskService.search(m[2]), `🔍 RESULTADOS PARA "${m[2]}"`) };
        }
        if ((m = /^conclu[ií]r\s*#?(\d+)$/i.exec(text))) {
            return this.#complete(Number(m[1]));
        }
        if ((m = /^cancelar\s*#?(\d+)$/i.exec(text))) {
            return this.#cancel(Number(m[1]));
        }
        if ((m = /^(excluir|apagar)\s*#?(\d+)$/i.exec(text))) {
            return this.#delete(Number(m[2]));
        }
        if ((m = /^adiar\s*#?(\d+)\s+(\d+)$/i.exec(text))) {
            return this.#postpone(Number(m[1]), Number(m[2]));
        }
        if ((m = /^reagendar\s*#?(\d+)\s+(\d{1,2}\/\d{1,2}(?:\/\d{2,4})?)\s+(\d{1,2}:\d{2})$/i.exec(text))) {
            return this.#reschedule(Number(m[1]), m[2], m[3]);
        }

        // Nenhum comando reconhecido -> trata como criacao de tarefa em linguagem natural
        return this.#createFromText(text);
    }

    #handleReminderShortcut(digit, taskId) {
        try {
            if (digit === '1') {
                const task = this.taskService.complete(taskId);
                return { text: `✅ *Tarefa concluída!*\n${task.title}`, clearActiveReminder: true };
            }
            if (digit === '2') {
                const task = this.taskService.postpone(taskId, 30);
                return {
                    text: `⏰ Tudo bem. Vou te lembrar novamente às *${task.dueTime}*.`,
                    clearActiveReminder: true,
                };
            }
            const task = this.taskService.cancel(taskId);
            return { text: `❌ Tarefa cancelada: ${task.title}`, clearActiveReminder: true };
        } catch (err) {
            return { text: `⚠️ ${err.message}`, clearActiveReminder: true };
        }
    }

    #createFromText(text) {
        try {
            const task = this.taskService.createFromText(text);
            const data = friendlyDate(task.dueDate);
            const hora = formatTime(task.dueTime);
            let msg = `✅ *Tarefa criada!*\n📌 ${task.title}\n📅 ${data} (${task.dueDate})\n⏰ ${hora}\n#${task.id}`;
            if (task.recurrenceType !== 'NONE') {
                msg += `\n🔁 Recorrente (${recurrenceLabel(task)})`;
            }
            return { text: msg };
        } catch (err) {
            return { text: `⚠️ Não consegui entender: ${err.message}\n\nDigite *ajuda* para ver exemplos.` };
        }
    }

    #complete(id) {
        try {
            const task = this.taskService.complete(id);
            return { text: `✅ *Tarefa concluída!*\n${task.title}` };
        } catch (err) {
            return { text: `⚠️ ${err.message}` };
        }
    }

    #cancel(id) {
        try {
            const task = this.taskService.cancel(id);
            return { text: `❌ Tarefa cancelada: ${task.title}` };
        } catch (err) {
            return { text: `⚠️ ${err.message}` };
        }
    }

    #delete(id) {
        try {
            const task = this.taskService.get(id);
            this.taskService.deleteTask(id);
            return { text: `🗑️ Tarefa excluída: ${task.title}` };
        } catch (err) {
            return { text: `⚠️ ${err.message}` };
        }
    }

    #postpone(id, minutes) {
        try {
            const task = this.taskService.postpone(id, minutes);
            return { text: `⏰ *${task.title}* adiada para ${friendlyDate(task.dueDate)} às ${task.dueTime}.` };
        } catch (err) {
            return { text: `⚠️ ${err.message}` };
        }
    }

    #reschedule(id, dateText, timeText) {
        try {
            const dateStr = parseBrDate(dateText);
            const task = this.taskService.reschedule(id, dateStr, timeText);
            return { text: `📅 *${task.title}* reagendada para ${friendlyDate(task.dueDate)} às ${task.dueTime}.` };
        } catch (err) {
            return { text: `⚠️ ${err.message}` };
        }
    }

    #exportBackup() {
        const dir = path.join(os.homedir(), 'AssistenteWhatsapp-backups');
        const file = this.backupService.exportBackup(dir);
        return { text: `💾 Backup exportado! Enviando o arquivo...`, filePath: file };
    }

    #listTasks(tasks, title) {
        if (tasks.length === 0) {
            return `${title}\n\nNenhuma tarefa encontrada.`;
        }
        const lines = tasks.map((t) => {
            const time = formatTime(t.dueTime);
            const emoji = PRIORITY_EMOJI[t.priority] || '';
            const rec = t.recurrenceType && t.recurrenceType !== 'NONE' ? ' 🔁' : '';
            const statusIcon = t.status === 'COMPLETED' ? '✅' : t.status === 'CANCELLED' ? '❌' : '⏰';
            return `${statusIcon} #${t.id} ${time} ${emoji} ${t.title}${rec}`;
        });
        return `${title}\n\n${lines.join('\n')}\n\nTotal: ${tasks.length}`;
    }
}

function recurrenceLabel(task) {
    if (task.recurrenceType === 'DAILY') return 'todo dia';
    if (task.recurrenceType === 'WEEKLY') return 'toda semana';
    if (task.recurrenceType === 'MONTHLY') return `todo mês, dia ${task.recurrenceValue}`;
    return '';
}

/** Converte "dd/mm" ou "dd/mm/aaaa" para "YYYY-MM-DD", assumindo o ano atual quando omitido. */
function parseBrDate(text) {
    const parts = text.split('/').map((p) => parseInt(p, 10));
    const [day, month, yearRaw] = parts;
    let year = yearRaw || new Date().getFullYear();
    if (year < 100) year += 2000;
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}
