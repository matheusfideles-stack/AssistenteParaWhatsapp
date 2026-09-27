import fs from 'node:fs';
import path from 'node:path';

const CONFIG_PATH = path.resolve(process.cwd(), 'config.json');

const DEFAULTS = {
    // URL do backend Java (AssistentePessoal-Backend) rodando localmente.
    backendUrl: 'http://localhost:8080',
};

/** Configuracao simples em config.json (criado com valores padrao se nao existir). */
export function loadConfig() {
    if (!fs.existsSync(CONFIG_PATH)) {
        fs.writeFileSync(CONFIG_PATH, JSON.stringify(DEFAULTS, null, 2) + '\n', 'utf-8');
        return { ...DEFAULTS };
    }
    try {
        const raw = JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf-8'));
        return { ...DEFAULTS, ...raw };
    } catch {
        return { ...DEFAULTS };
    }
}

export { CONFIG_PATH };
