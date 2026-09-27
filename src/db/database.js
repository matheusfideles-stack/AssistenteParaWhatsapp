import { DatabaseSync } from 'node:sqlite';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DATA_DIR = path.resolve(__dirname, '..', '..', 'data');
const DB_PATH = path.join(DATA_DIR, 'assistente.db');

const CREATE_TABLE_SQL = `
CREATE TABLE IF NOT EXISTS tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    description TEXT,
    due_date TEXT,
    due_time TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    priority TEXT NOT NULL DEFAULT 'MEDIUM',
    recurrence_type TEXT NOT NULL DEFAULT 'NONE',
    recurrence_value TEXT,
    reminder_interval INTEGER,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    completed_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_tasks_due ON tasks(due_date, due_time);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
`;

/**
 * Abre (criando se necessario) o banco SQLite local em data/assistente.db,
 * usando o modulo nativo `node:sqlite` do proprio Node.js (nenhuma
 * dependencia externa, nenhuma compilacao nativa necessaria). Arquivo unico,
 * sem servidor - zero configuracao, 100% offline.
 */
export function openDatabase() {
    fs.mkdirSync(DATA_DIR, { recursive: true });
    const db = new DatabaseSync(DB_PATH);
    db.exec('PRAGMA journal_mode = WAL');
    db.exec(CREATE_TABLE_SQL);
    return db;
}

export { DB_PATH, DATA_DIR };
