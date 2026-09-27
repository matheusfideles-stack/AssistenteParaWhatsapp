import fs from 'node:fs';
import path from 'node:path';

const LOG_FILE = path.resolve(process.cwd(), 'session-errors.log');

const NOISE_PATTERNS = [
    'Failed to decrypt message',
    'Session error',
    'Closing session',
    'Closing open session',
];

function isNoise(args) {
    const text = args.map((a) => (typeof a === 'string' ? a : '')).join(' ');
    return NOISE_PATTERNS.some((pattern) => text.includes(pattern));
}

/**
 * O Baileys usa a lib libsignal internamente para a criptografia de sessao,
 * e ela escreve avisos ("Failed to decrypt", "Bad MAC", "Closing session...")
 * direto no console via console.log/console.error, sem passar pelo logger
 * do Baileys (que ja fica silenciado em whatsappClient.js). Sao esperados ao
 * reconectar (renegociacao de sessao) e nao indicam falha real, mas poluem o
 * terminal - aqui sao redirecionados para um arquivo em vez de somem ou
 * atrapalham a leitura do log normal do bot.
 */
export function redirectSessionNoiseToFile() {
    const originalLog = console.log.bind(console);
    const originalError = console.error.bind(console);

    console.log = (...args) => {
        if (isNoise(args)) {
            appendToFile(args);
            return;
        }
        originalLog(...args);
    };

    console.error = (...args) => {
        if (isNoise(args)) {
            appendToFile(args);
            return;
        }
        originalError(...args);
    };
}

function appendToFile(args) {
    const line = `[${new Date().toISOString()}] ${args.map(stringify).join(' ')}\n`;
    fs.appendFile(LOG_FILE, line, () => {});
}

function stringify(value) {
    if (typeof value === 'string') return value;
    try {
        return JSON.stringify(value);
    } catch {
        return String(value);
    }
}
