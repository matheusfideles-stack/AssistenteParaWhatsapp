import fs from 'node:fs';
import path from 'node:path';
import {
    default as makeWASocket,
    useMultiFileAuthState,
    DisconnectReason,
    jidNormalizedUser,
} from '@whiskeysockets/baileys';
import { Boom } from '@hapi/boom';
import pino from 'pino';
import qrcode from 'qrcode-terminal';
import { CommandHandler } from './commandHandler.js';
import { formatTime } from '../utils/dateUtil.js';

const AUTH_DIR = path.resolve(process.cwd(), 'auth');
const logger = pino({ level: 'silent' });

/**
 * Conecta ao WhatsApp usando o Baileys (biblioteca open source que fala o
 * protocolo do WhatsApp Web) - sem API paga, sem servidor externo. Voce
 * escaneia o QR code UMA vez com o proprio celular e a sessao fica salva
 * localmente em auth/ (nunca suba essa pasta para lugar nenhum - e o
 * equivalente a estar logado no seu WhatsApp).
 *
 * O bot funciona no chat "Mensagem para voce mesmo" (voce falando com voce):
 * o que voce escrever la vira comando/tarefa, e os lembretes chegam la.
 */
export function createWhatsAppBot({ taskService, backupService, reminderScheduler }) {
    const commandHandler = new CommandHandler(taskService, backupService);
    let sock = null;
    let ownJid = null;
    let ownLid = null;
    let activeReminderId = null;

    async function start() {
        fs.mkdirSync(AUTH_DIR, { recursive: true });
        const { state, saveCreds } = await useMultiFileAuthState(AUTH_DIR);

        sock = makeWASocket({
            auth: state,
            logger,
            printQRInTerminal: false,
            browser: ['Assistente Pessoal', 'Chrome', '1.0.0'],
        });

        sock.ev.on('creds.update', saveCreds);

        sock.ev.on('connection.update', (update) => {
            const { connection, lastDisconnect, qr } = update;

            if (qr) {
                console.log('\n📱 Escaneie o QR code abaixo no WhatsApp do seu celular');
                console.log('   (Configurações → Aparelhos conectados → Conectar aparelho):\n');
                qrcode.generate(qr, { small: true });
            }

            if (connection === 'open') {
                ownJid = jidNormalizedUser(sock.user.id);
                // O WhatsApp pode endereçar o chat "Mensagem para você mesmo" tanto
                // pelo JID baseado em número (@s.whatsapp.net) quanto pelo LID, um
                // identificador de privacidade mais recente (@lid) - sem checar os
                // dois, o bot nao reconhece o proprio chat e ignora as mensagens.
                ownLid = sock.user.lid ? jidNormalizedUser(sock.user.lid) : null;
                console.log('✅ Conectado ao WhatsApp!');
                console.log(`   Escreva no chat "Mensagem para você mesmo" para criar tarefas.\n`);
            }

            if (connection === 'close') {
                const statusCode = new Boom(lastDisconnect?.error)?.output?.statusCode;
                const shouldReconnect = statusCode !== DisconnectReason.loggedOut;
                console.log('⚠️  Conexão encerrada.', shouldReconnect ? 'Reconectando...' : 'Sessão desconectada.');
                if (shouldReconnect) {
                    start();
                } else {
                    console.log('   Apague a pasta auth/ e rode novamente para logar de novo.');
                }
            }
        });

        sock.ev.on('messages.upsert', async ({ messages, type }) => {
            if (type !== 'notify') return;
            for (const msg of messages) {
                await handleIncoming(msg);
            }
        });

        reminderScheduler.setListener((task, overdue) => {
            sendReminder(task, overdue).catch((err) =>
                console.error('[WhatsApp] Erro ao enviar lembrete:', err.message));
        });
    }

    async function handleIncoming(msg) {
        if (!msg.message || !ownJid) return;
        const remoteJid = jidNormalizedUser(msg.key.remoteJid || '');
        const isSelfChat = remoteJid === ownJid || (ownLid && remoteJid === ownLid);
        if (!isSelfChat) return; // o bot só reage no chat "Mensagem para você mesmo"

        const text = extractText(msg.message);
        if (!text) return;

        const result = commandHandler.handle(text, { activeReminderId });
        if (result.clearActiveReminder) {
            activeReminderId = null;
        }

        if (result.text) {
            await sock.sendMessage(ownJid, { text: result.text });
        }
        if (result.filePath) {
            await sock.sendMessage(ownJid, {
                document: fs.readFileSync(result.filePath),
                fileName: path.basename(result.filePath),
                mimetype: 'text/csv',
            });
        }
    }

    async function sendReminder(task, overdue) {
        if (!ownJid) return;
        const time = formatTime(task.dueTime);
        const header = overdue ? '⚠️ *TAREFA ATRASADA*' : '🔔 *LEMBRETE*';
        const body = overdue
            ? `Você ainda não concluiu:\n*${task.title}*\n⏰ Horário: ${time}`
            : `Está na hora de:\n*${task.title}*\n⏰ ${time}`;
        const text = `${header}\n${body}\n\nResponda:\n1️⃣ Concluir\n2️⃣ Adiar 30 min\n3️⃣ Cancelar\n\n(ou use: concluir #${task.id} / adiar #${task.id} <min> / cancelar #${task.id})`;

        activeReminderId = task.id;
        await sock.sendMessage(ownJid, { text });
    }

    return { start };
}

function extractText(message) {
    return (
        message.conversation ||
        message.extendedTextMessage?.text ||
        message.imageMessage?.caption ||
        message.videoMessage?.caption ||
        null
    );
}
