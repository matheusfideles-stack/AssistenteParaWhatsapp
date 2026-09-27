import fs from 'node:fs';
import path from 'node:path';
import dayjs from 'dayjs';

const HEADER = 'id,title,description,due_date,due_time,status,priority,recurrence_type,recurrence_value,reminder_interval,created_at,updated_at,completed_at';

/** Exporta/importa todas as tarefas em um arquivo CSV local - sem nenhuma dependencia externa. */
export class BackupService {
    constructor(repository) {
        this.repository = repository;
    }

    exportBackup(targetDirectory) {
        fs.mkdirSync(targetDirectory, { recursive: true });
        const fileName = `backup-${dayjs().format('YYYY-MM-DD')}.csv`;
        const target = path.join(targetDirectory, fileName);
        const tasks = this.repository.findAll();

        const lines = [HEADER, ...tasks.map(toCsvLine)];
        fs.writeFileSync(target, lines.join('\n') + '\n', 'utf-8');
        return target;
    }

    importBackup(backupFile) {
        const content = fs.readFileSync(backupFile, 'utf-8');
        const lines = content.split(/\r?\n/).filter((l) => l.length > 0);
        if (lines.length === 0 || !lines[0].startsWith('id,title')) {
            throw new Error('Arquivo de backup inválido: cabeçalho não reconhecido.');
        }
        let count = 0;
        for (const line of lines.slice(1)) {
            const task = fromCsvLine(line);
            this.repository.save(task);
            count++;
        }
        return count;
    }
}

function toCsvLine(t) {
    return [
        str(t.id), csv(t.title), csv(t.description), str(t.dueDate), str(t.dueTime),
        str(t.status), str(t.priority), str(t.recurrenceType), csv(t.recurrenceValue),
        str(t.reminderIntervalMinutes), str(t.createdAt), str(t.updatedAt), str(t.completedAt),
    ].join(',');
}

function fromCsvLine(line) {
    const parts = splitCsv(line);
    return {
        id: parts[0] ? Number(parts[0]) : null,
        title: parts[1],
        description: parts[2] || null,
        dueDate: parts[3] || null,
        dueTime: parts[4] || null,
        status: parts[5] || 'PENDING',
        priority: parts[6] || 'MEDIUM',
        recurrenceType: parts[7] || 'NONE',
        recurrenceValue: parts[8] || null,
        reminderIntervalMinutes: parts[9] ? Number(parts[9]) : null,
        createdAt: parts[10] || dayjs().toISOString(),
        updatedAt: parts[11] || dayjs().toISOString(),
        completedAt: parts[12] || null,
    };
}

function str(value) {
    return value === null || value === undefined ? '' : String(value);
}

function csv(value) {
    if (value === null || value === undefined) return '';
    const escaped = String(value).replace(/"/g, '""');
    if (/[",\n]/.test(escaped)) {
        return `"${escaped}"`;
    }
    return escaped;
}

/**
 * Parser simples de CSV com suporte a campos entre aspas contendo virgulas.
 * Ja devolve os campos totalmente "desescapados" (sem aspas delimitadoras,
 * com "" convertido para "), pronto para uso direto pelo chamador.
 */
function splitCsv(line) {
    const fields = [];
    let current = '';
    let inQuotes = false;
    for (let i = 0; i < line.length; i++) {
        const c = line[i];
        if (inQuotes) {
            if (c === '"') {
                if (line[i + 1] === '"') {
                    current += '"';
                    i++;
                } else {
                    inQuotes = false;
                }
            } else {
                current += c;
            }
        } else if (c === '"') {
            inQuotes = true;
        } else if (c === ',') {
            fields.push(current);
            current = '';
        } else {
            current += c;
        }
    }
    fields.push(current);
    return fields;
}
