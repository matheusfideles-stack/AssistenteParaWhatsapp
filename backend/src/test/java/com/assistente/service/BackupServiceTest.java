package com.assistente.service;

import com.assistente.model.Task;
import com.assistente.model.TaskPriority;
import com.assistente.model.TaskStatus;
import com.assistente.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock
    private TaskRepository repository;

    private BackupService backupService;

    @BeforeEach
    void setUp() {
        backupService = new BackupService(repository);
    }

    @Test
    void exportaEReimportaTarefasPreservandoDadosComVirgulaEAspas(@TempDir Path tempDir) {
        Task task = new Task("Reunião, importante \"urgente\"", LocalDate.of(2026, 9, 27), LocalTime.of(19, 0));
        task.setId(1L);
        task.setDescription("Descrição com, vírgula e \"aspas\"");
        task.setPriority(TaskPriority.HIGH);
        task.setStatus(TaskStatus.PENDING);

        when(repository.findAll()).thenReturn(List.of(task));

        Path backupFile = backupService.exportBackup(tempDir);
        assertTrue(backupFile.toFile().exists());

        when(repository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        int imported = backupService.importBackup(backupFile);
        assertEquals(1, imported);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(repository).save(captor.capture());
        Task restored = captor.getValue();

        assertEquals("Reunião, importante \"urgente\"", restored.getTitle());
        assertEquals("Descrição com, vírgula e \"aspas\"", restored.getDescription());
        assertEquals(TaskPriority.HIGH, restored.getPriority());
        assertEquals(LocalDate.of(2026, 9, 27), restored.getDueDate());
        assertEquals(LocalTime.of(19, 0), restored.getDueTime());
    }

    @Test
    void importarArquivoComCabecalhoInvalidoLancaExcecao(@TempDir Path tempDir) throws Exception {
        Path bad = tempDir.resolve("invalido.csv");
        java.nio.file.Files.writeString(bad, "coluna_errada,outra\n1,2\n");

        assertTrue(assertThrowsIllegalArgument(bad));
    }

    private boolean assertThrowsIllegalArgument(Path file) {
        try {
            backupService.importBackup(file);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
