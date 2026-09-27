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
import { notifyReminder } from '../services/desktopNotifier.js';

const AUTH_DIR = path.resolve(process.cwd(), 'auth');
const REMINDER_POLL_INTERVAL_MS = 15_000;
const logger = pino({ level: 'silent' });

/**
 * Conecta ao WhatsApp usando o Baileys (biblioteca open source que fala o
 * protocolo do WhatsApp Web) - sem API paga, sem servidor externo. Voce
 * escaneia o QR code UMA vez com o proprio celular e a sessao fica salva
 * localmente em auth/ (nunca suba essa pasta para lugar nenhum - e o
 * equivalente a estar logado no seu WhatsApp).
 *
 * O bot funciona no chat "Mensagem para voce mesmo" (voce falando com voce):
 * o que voce escrever la vira comando/tarefa, e os lembretes chegam la. Toda
 * a logica de tarefas mora no backend Java - este modulo so envia/recebe
 * mensagens e repassa para o CommandHandler (que fala HTTP com o backend).
 */
export function createWhatsAppBot({ commandHandler, apiClient }) {
    let sock = null;
    let ownJid = null;
    let ownLid = null;
    let activeReminderId = null;
    let pollTimer = null;

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
                startReminderPolling();
            }

            if (connection === 'close') {
                stopReminderPolling();
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
    }

    async function handleIncoming(msg) {
        if (!msg.message || !ownJid) return;
        const remoteJid = jidNormalizedUser(msg.key.remoteJid || '');
        const isSelfChat = remoteJid === ownJid || (ownLid && remoteJid === ownLid);
        if (!isSelfChat) return; // o bot só reage no chat "Mensagem para você mesmo"

        const text = extractText(msg.message);
        if (!text) return;

        let result;
        try {
            result = await commandHandler.handle(text, { activeReminderId });
        } catch (err) {
            console.error('[WhatsApp] Erro ao falar com o backend:', err.message);
            await sock.sendMessage(ownJid, {
                text: '⚠️ Não consegui falar com o backend agora. Confirme se ele está rodando (AssistentePessoal-Backend) e tente de novo.',
            });
            return;
        }

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

    function startReminderPolling() {
        stopReminderPolling();
        pollTimer = setInterval(pollReminders, REMINDER_POLL_INTERVAL_MS);
    }

    function stopReminderPolling() {
        if (pollTimer) {
            clearInterval(pollTimer);
            pollTimer = null;
        }
    }

    async function pollReminders() {
        if (!ownJid) return;
        let reminders;
        try {
            reminders = await apiClient.fetchPendingReminders();
        } catch (err) {
            console.error('[WhatsApp] Erro ao consultar lembretes pendentes:', err.message);
            return;
        }
        for (const reminder of reminders) {
            activeReminderId = reminder.taskId;
            notifyReminder(reminder.text);
            await sock.sendMessage(ownJid, { text: reminder.text });
        }
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
