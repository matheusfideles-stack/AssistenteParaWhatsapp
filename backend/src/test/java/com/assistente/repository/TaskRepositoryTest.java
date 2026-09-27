package com.assistente.repository;

import com.assistente.database.SqliteDatabaseManager;
import com.assistente.model.RecurrenceType;
import com.assistente.model.Task;
import com.assistente.model.TaskPriority;
import com.assistente.model.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de integracao real com um arquivo SQLite temporario (nao usa
 * ":memory:" porque o repositorio abre uma conexao nova por operacao, e um
 * banco em memoria do SQLite existe apenas enquanto a conexao que o criou
 * estiver aberta).
 */
class TaskRepositoryTest {

    private Path dbFile;
    private TaskRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        dbFile = Files.createTempFile("assistente-backend-test-", ".db");
        Files.delete(dbFile); // deixa o SqliteDatabaseManager criar do zero
        SqliteDatabaseManager db = new SqliteDatabaseManager(dbFile);
        db.initializeSchema();
        repository = new TaskRepository(db);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(dbFile);
        Files.deleteIfExists(Path.of(dbFile + "-shm"));
        Files.deleteIfExists(Path.of(dbFile + "-wal"));
    }

    @Test
    void insereEBuscaPorId() {
        Task task = new Task("Estudar Java", LocalDate.of(2026, 9, 27), LocalTime.of(19, 0));
        Task saved = repository.save(task);

        assertTrue(saved.getId() > 0);
        Optional<Task> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Estudar Java", found.get().getTitle());
        assertEquals(LocalDate.of(2026, 9, 27), found.get().getDueDate());
        assertEquals(LocalTime.of(19, 0), found.get().getDueTime());
    }

    @Test
    void atualizaTarefaExistente() {
        Task task = repository.save(new Task("Original", LocalDate.now(), LocalTime.NOON));
        task.setTitle("Editado");
        repository.save(task);

        Task reloaded = repository.findById(task.getId()).orElseThrow();
        assertEquals("Editado", reloaded.getTitle());
    }

    @Test
    void excluiPorId() {
        Task task = repository.save(new Task("Para excluir", LocalDate.now(), null));
        repository.deleteById(task.getId());
        assertTrue(repository.findById(task.getId()).isEmpty());
    }

    @Test
    void buscaPorData() {
        LocalDate hoje = LocalDate.now();
        repository.save(new Task("Hoje 1", hoje, LocalTime.of(9, 0)));
        repository.save(new Task("Hoje 2", hoje, LocalTime.of(18, 0)));
        repository.save(new Task("Amanhã", hoje.plusDays(1), LocalTime.of(9, 0)));

        List<Task> tasks = repository.findByDate(hoje);
        assertEquals(2, tasks.size());
    }

    @Test
    void buscaPorStatus() {
        Task task = repository.save(new Task("Pendente", LocalDate.now(), null));
        task.setStatus(TaskStatus.COMPLETED);
        repository.save(task);

        assertEquals(1, repository.findByStatus(TaskStatus.COMPLETED).size());
        assertEquals(0, repository.findByStatus(TaskStatus.PENDING).size());
    }

    @Test
    void buscaAtrasadas() {
        Task atrasada = new Task("Atrasada", LocalDate.now().minusDays(1), LocalTime.of(9, 0));
        repository.save(atrasada);
        Task futura = new Task("Futura", LocalDate.now().plusDays(1), LocalTime.of(9, 0));
        repository.save(futura);

        List<Task> overdue = repository.findOverdue(LocalDateTime.now());
        assertEquals(1, overdue.size());
        assertEquals("Atrasada", overdue.get(0).getTitle());
    }

    @Test
    void buscaPorTermo() {
        repository.save(new Task("Estudar Java avançado", LocalDate.now(), null));
        repository.save(new Task("Comprar pão", LocalDate.now(), null));

        List<Task> results = repository.search("java");
        assertEquals(1, results.size());
        assertEquals("Estudar Java avançado", results.get(0).getTitle());
    }

    @Test
    void preservaCamposDeRecorrenciaEPrioridade() {
        Task task = new Task("Pagar conta", LocalDate.of(2026, 1, 31), LocalTime.of(8, 0));
        task.setRecurrenceType(RecurrenceType.MONTHLY);
        task.setRecurrenceValue("31");
        task.setPriority(TaskPriority.HIGH);
        task.setReminderIntervalMinutes(60);
        Task saved = repository.save(task);

        Task reloaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals(RecurrenceType.MONTHLY, reloaded.getRecurrenceType());
        assertEquals("31", reloaded.getRecurrenceValue());
        assertEquals(TaskPriority.HIGH, reloaded.getPriority());
        assertEquals(60, reloaded.getReminderIntervalMinutes());
    }
}
