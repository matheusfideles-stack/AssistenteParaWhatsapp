import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { ensureBackendRunning, findBackendJar } from '../src/services/backendLauncher.js';

function fakeApiClient(upFromStart) {
    let up = upFromStart;
    return {
        setUp(value) {
            up = value;
        },
        async fetchPendingReminders() {
            if (!up) throw new Error('ECONNREFUSED');
            return [];
        },
    };
}

describe('findBackendJar', () => {
    it('retorna null quando a pasta target nao existe', () => {
        const dir = path.join(os.tmpdir(), 'assistente-launcher-test-' + Date.now(), 'nao-existe');
        assert.equal(findBackendJar(dir), null);
    });

    it('retorna null quando nao ha jar do assistente-backend na pasta', () => {
        const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'assistente-launcher-test-'));
        fs.writeFileSync(path.join(dir, 'outra-coisa.txt'), 'x');
        assert.equal(findBackendJar(dir), null);
        fs.rmSync(dir, { recursive: true, force: true });
    });

    it('encontra o jar do backend pelo padrao de nome', () => {
        const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'assistente-launcher-test-'));
        fs.writeFileSync(path.join(dir, 'assistente-backend-1.0.0.jar'), 'fake jar');
        const found = findBackendJar(dir);
        assert.equal(found, path.join(dir, 'assistente-backend-1.0.0.jar'));
        fs.rmSync(dir, { recursive: true, force: true });
    });
});

describe('ensureBackendRunning', () => {
    it('nao inicia nenhum processo quando o backend ja esta respondendo', async () => {
        const api = fakeApiClient(true);
        const result = await ensureBackendRunning(api, 'http://localhost:8080');
        assert.equal(result, null);
    });
});
