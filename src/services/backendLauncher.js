import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const BACKEND_DIR = path.resolve(process.cwd(), 'backend');
const TARGET_DIR = path.join(BACKEND_DIR, 'target');
const READY_TIMEOUT_MS = 45_000;
const POLL_INTERVAL_MS = 1_000;

/**
 * Garante que o backend Java (backend/) esteja rodando antes do bot
 * conectar no WhatsApp. Se ja estiver rodando (por exemplo, iniciado a mao
 * em outro terminal), nao faz nada. Caso contrario, sobe o jar ja compilado
 * (backend/target/*.jar) como um processo filho e espera ele responder.
 *
 * @returns {Promise<import('node:child_process').ChildProcess | null>}
 *   O processo filho iniciado (para poder encerrar junto com o bot), ou
 *   null se o backend ja estava rodando por conta propria.
 */
export async function ensureBackendRunning(apiClient, backendUrl) {
    if (await isBackendUp(apiClient)) {
        console.log(`Backend já estava rodando em ${backendUrl}`);
        return null;
    }

    const jarPath = findBackendJar(TARGET_DIR);
    if (!jarPath) {
        throw new Error(
            'Backend Java ainda não foi compilado. Rode "npm run build:backend" primeiro ' +
            '(precisa de Java 21+ e Maven instalados).'
        );
    }

    console.log('Iniciando o backend Java automaticamente...');
    const child = spawn('java', ['-jar', jarPath], {
        cwd: BACKEND_DIR,
        stdio: ['ignore', 'pipe', 'pipe'],
    });

    child.stderr.on('data', (chunk) => {
        const text = chunk.toString();
        if (text.includes('ERROR') || text.includes('Exception')) {
            process.stderr.write(`[backend] ${text}`);
        }
    });

    child.on('exit', (code) => {
        if (code !== null && code !== 0) {
            console.error(`[backend] processo encerrado inesperadamente (código ${code})`);
        }
    });

    await waitUntilReady(apiClient, child);
    console.log(`Backend Java pronto em ${backendUrl}`);
    return child;
}

/** Exportada separadamente para ser testável sem precisar mockar child_process. */
export function findBackendJar(targetDir) {
    if (!fs.existsSync(targetDir)) return null;
    const jar = fs.readdirSync(targetDir).find((f) => /^assistente-backend-.*\.jar$/.test(f));
    return jar ? path.join(targetDir, jar) : null;
}

async function isBackendUp(apiClient) {
    try {
        await apiClient.fetchPendingReminders();
        return true;
    } catch {
        return false;
    }
}

async function waitUntilReady(apiClient, child) {
    const deadline = Date.now() + READY_TIMEOUT_MS;
    while (Date.now() < deadline) {
        if (child.exitCode !== null) {
            throw new Error(`Backend Java encerrou antes de ficar pronto (código ${child.exitCode}).`);
        }
        if (await isBackendUp(apiClient)) {
            return;
        }
        await sleep(POLL_INTERVAL_MS);
    }
    throw new Error('Backend Java não respondeu a tempo (45s). Veja os logs acima para erros.');
}

function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}
