package com.assistente.service;

import com.assistente.model.RecurrenceType;
import com.assistente.model.Task;
import com.assistente.model.TaskPriority;
import com.assistente.model.TaskStatus;
import com.assistente.repository.TaskRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Exporta e importa todas as tarefas em um arquivo de backup local (CSV),
 * sem depender de nenhuma ferramenta externa (nao usa mysqldump nem
 * bibliotecas de terceiros) - apenas leitura/escrita via JDBC e java.io.
 */
public class BackupService {

    private static final String HEADER =
            "id,title,description,due_date,due_time,status,priority,recurrence_type,recurrence_value,reminder_interval,created_at,updated_at,completed_at";

    private final TaskRepository repository;

    public BackupService(TaskRepository repository) {
        this.repository = repository;
    }

    /** Gera, por exemplo, backup-2026-09-27.csv com todas as tarefas atuais. */
    public Path exportBackup(Path targetDirectory) {
        try {
            Files.createDirectories(targetDirectory);
            String fileName = "backup-" + LocalDate.now() + ".csv";
            Path target = targetDirectory.resolve(fileName);
            List<Task> tasks = repository.findAll();

            try (Writer w = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                w.write(HEADER);
                w.write("\n");
                for (Task t : tasks) {
                    w.write(toCsvLine(t));
                    w.write("\n");
                }
            }
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao exportar backup: " + e.getMessage(), e);
        }
    }

    /** Restaura tarefas a partir de um arquivo gerado por exportBackup. Tarefas existentes (mesmo id) sao atualizadas. */
    public int importBackup(Path backupFile) {
        try (BufferedReader reader = Files.newBufferedReader(backupFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine(); // cabecalho
            if (line == null || !line.startsWith("id,title")) {
                throw new IllegalArgumentException("Arquivo de backup invalido: cabecalho nao reconhecido.");
            }
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                Task task = fromCsvLine(line);
                repository.save(task);
                count++;
            }
            return count;
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao importar backup: " + e.getMessage(), e);
        }
    }

    private String toCsvLine(Task t) {
        List<String> fields = new ArrayList<>();
        fields.add(str(t.getId()));
        fields.add(csv(t.getTitle()));
        fields.add(csv(t.getDescription()));
        fields.add(str(t.getDueDate()));
        fields.add(str(t.getDueTime()));
        fields.add(str(t.getStatus()));
        fields.add(str(t.getPriority()));
        fields.add(str(t.getRecurrenceType()));
        fields.add(csv(t.getRecurrenceValue()));
        fields.add(str(t.getReminderIntervalMinutes()));
        fields.add(str(t.getCreatedAt()));
        fields.add(str(t.getUpdatedAt()));
        fields.add(str(t.getCompletedAt()));
        return String.join(",", fields);
    }

    private Task fromCsvLine(String line) {
        String[] parts = splitCsv(line);
        Task t = new Task();
        t.setId(parts[0].isEmpty() ? null : Long.parseLong(parts[0]));
        t.setTitle(parts[1]);
        t.setDescription(parts[2].isEmpty() ? null : parts[2]);
        t.setDueDate(parts[3].isEmpty() ? null : LocalDate.parse(parts[3]));
        t.setDueTime(parts[4].isEmpty() ? null : LocalTime.parse(parts[4]));
        t.setStatus(parts[5].isEmpty() ? TaskStatus.PENDING : TaskStatus.valueOf(parts[5]));
        t.setPriority(parts[6].isEmpty() ? TaskPriority.MEDIUM : TaskPriority.valueOf(parts[6]));
        t.setRecurrenceType(parts[7].isEmpty() ? RecurrenceType.NONE : RecurrenceType.valueOf(parts[7]));
        t.setRecurrenceValue(parts[8].isEmpty() ? null : parts[8]);
        t.setReminderIntervalMinutes(parts[9].isEmpty() ? null : Integer.parseInt(parts[9]));
        t.setCreatedAt(parts[10].isEmpty() ? LocalDateTime.now() : LocalDateTime.parse(parts[10]));
        t.setUpdatedAt(parts[11].isEmpty() ? LocalDateTime.now() : LocalDateTime.parse(parts[11]));
        t.setCompletedAt(parts[12].isEmpty() ? null : LocalDateTime.parse(parts[12]));
        return t;
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    /**
     * Parser simples de CSV com suporte a campos entre aspas contendo virgulas.
     * Ja devolve os campos totalmente "desescapados" (sem as aspas delimitadoras
     * e com "" convertido para "), entao o chamador usa o resultado diretamente.
     */
    private String[] splitCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
