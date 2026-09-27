package com.assistente.service;

import com.assistente.model.RecurrenceType;
import com.assistente.model.Task;
import com.assistente.model.TaskPriority;
import com.assistente.model.TaskStatus;
import com.assistente.parser.TaskParser;
import com.assistente.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(repository, new TaskParser());
        // save() simplesmente devolve a mesma tarefa recebida (com id fake se necessario).
        // lenient() porque nem todo teste desta classe chega a chamar save().
        lenient().when(repository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(99L);
            }
            return t;
        });
    }

    @Test
    void criaTarefaAPartirDeTexto() {
        Task task = service.createFromText("Hoje às 19h estudar Java");
        assertEquals("Estudar Java", task.getTitle());
        assertEquals(LocalTime.of(19, 0), task.getDueTime());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        verify(repository).save(any(Task.class));
    }

    @Test
    void edicaoAtualizaTarefaExistente() {
        Task task = new Task("Original", LocalDate.now(), LocalTime.of(10, 0));
        task.setId(5L);
        task.setStatus(TaskStatus.PENDING);

        task.setTitle("Editado");
        service.updateTask(task);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(repository).save(captor.capture());
        assertEquals("Editado", captor.getValue().getTitle());
    }

    @Test
    void edicaoSemIdLancaExcecao() {
        Task task = new Task("Sem id", LocalDate.now(), LocalTime.NOON);
        assertThrows(IllegalArgumentException.class, () -> service.updateTask(task));
    }

    @Test
    void conclusaoMarcaStatusEDataDeConclusao() {
        Task task = new Task("Estudar", LocalDate.now(), LocalTime.of(19, 0));
        task.setId(1L);
        task.setStatus(TaskStatus.PENDING);
        task.setRecurrenceType(RecurrenceType.NONE);
        when(repository.findById(1L)).thenReturn(Optional.of(task));

        Task result = service.complete(1L);

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getCompletedAt());
        // Tarefa nao recorrente: nao deve gerar nenhuma outra ocorrencia (so 1 save, o da conclusao).
        verify(repository, times(1)).save(any(Task.class));
    }

    @Test
    void conclusaoDeTarefaRecorrenteDiariaGeraProximaOcorrencia() {
        Task task = new Task("Estudar Java", LocalDate.of(2026, 9, 27), LocalTime.of(19, 0));
        task.setId(2L);
        task.setStatus(TaskStatus.PENDING);
        task.setRecurrenceType(RecurrenceType.DAILY);
        when(repository.findById(2L)).thenReturn(Optional.of(task));

        service.complete(2L);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(repository, times(2)).save(captor.capture());
        Task next = captor.getAllValues().get(1);
        assertEquals(LocalDate.of(2026, 9, 28), next.getDueDate());
        assertEquals(TaskStatus.PENDING, next.getStatus());
        assertEquals(RecurrenceType.DAILY, next.getRecurrenceType());
    }

    @Test
    void conclusaoDeTarefaRecorrenteSemanalGeraProximaOcorrencia() {
        Task task = new Task("Reunião", LocalDate.of(2026, 9, 28), LocalTime.of(9, 0)); // uma segunda-feira
        task.setId(3L);
        task.setStatus(TaskStatus.PENDING);
        task.setRecurrenceType(RecurrenceType.WEEKLY);
        task.setRecurrenceValue(String.valueOf(DayOfWeek.MONDAY.getValue()));
        when(repository.findById(3L)).thenReturn(Optional.of(task));

        service.complete(3L);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(repository, times(2)).save(captor.capture());
        assertEquals(LocalDate.of(2026, 10, 5), captor.getAllValues().get(1).getDueDate());
    }

    @Test
    void conclusaoDeTarefaRecorrenteMensalGeraProximaOcorrenciaComClampDeDia() {
        Task task = new Task("Pagar conta", LocalDate.of(2026, 1, 31), LocalTime.of(8, 0));
        task.setId(4L);
        task.setStatus(TaskStatus.PENDING);
        task.setRecurrenceType(RecurrenceType.MONTHLY);
        task.setRecurrenceValue("31");
        when(repository.findById(4L)).thenReturn(Optional.of(task));

        service.complete(4L);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(repository, times(2)).save(captor.capture());
        // Fevereiro de 2026 nao tem dia 31 -> deve cair no ultimo dia do mes (28).
        assertEquals(LocalDate.of(2026, 2, 28), captor.getAllValues().get(1).getDueDate());
    }

    @Test
    void cancelamentoMarcaStatusCancelled() {
        Task task = new Task("Tarefa", LocalDate.now(), LocalTime.NOON);
        task.setId(6L);
        when(repository.findById(6L)).thenReturn(Optional.of(task));

        Task result = service.cancel(6L);

        assertEquals(TaskStatus.CANCELLED, result.getStatus());
    }

    @Test
    void adiamentoSomaMinutosAoHorarioAgendado() {
        LocalDate hoje = LocalDate.now();
        Task task = new Task("Reunião", hoje, LocalTime.of(23, 50));
        task.setId(7L);
        task.setStatus(TaskStatus.PENDING);
        when(repository.findById(7L)).thenReturn(Optional.of(task));

        Task result = service.postpone(7L, 30);

        // 23:50 + 30min = 00:20 do dia seguinte.
        assertEquals(hoje.plusDays(1), result.getDueDate());
        assertEquals(LocalTime.of(0, 20), result.getDueTime());
        assertEquals(TaskStatus.PENDING, result.getStatus());
    }

    @Test
    void adiamentoDeTarefaJaAtrasadaContaAPartirDeAgora() {
        Task task = new Task("Tarefa antiga", LocalDate.now().minusDays(2), LocalTime.of(8, 0));
        task.setId(8L);
        task.setStatus(TaskStatus.PENDING);
        when(repository.findById(8L)).thenReturn(Optional.of(task));

        Task result = service.postpone(8L, 10);

        assertTrue(!result.getDueDateTime().isBefore(java.time.LocalDateTime.now().minusSeconds(5)));
    }

    @Test
    void buscaTarefaAtrasada() {
        Task overdue = new Task("Atrasada", LocalDate.now().minusDays(1), LocalTime.of(9, 0));
        when(repository.findOverdue(any())).thenReturn(java.util.List.of(overdue));

        var result = service.findOverdue();

        assertEquals(1, result.size());
        assertEquals("Atrasada", result.get(0).getTitle());
    }

    @Test
    void tarefaInexistenteLancaExcecao() {
        when(repository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.get(123L));
    }

    @Test
    void criarTarefaDiretaAplicaValoresPadrao() {
        Task task = new Task();
        task.setTitle("Tarefa manual");
        Task saved = service.createTask(task);
        assertEquals(TaskStatus.PENDING, saved.getStatus());
        assertEquals(TaskPriority.MEDIUM, saved.getPriority());
        assertEquals(RecurrenceType.NONE, saved.getRecurrenceType());
    }
}
