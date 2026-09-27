package com.assistente.scheduler;

import com.assistente.model.Task;
import com.assistente.model.TaskStatus;
import com.assistente.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderSchedulerTest {

    @Mock
    private TaskService taskService;

    private ReminderScheduler scheduler;
    private List<Task> pending;

    @BeforeEach
    void setUp() {
        pending = new ArrayList<>();
        // lenient() porque o teste de "pausado" retorna antes de chamar findPending().
        lenient().when(taskService.findPending()).thenAnswer(inv -> pending);
        scheduler = new ReminderScheduler(taskService, () -> 30);
    }

    private Task overdueTask(long id) {
        Task t = new Task("Tarefa " + id, LocalDate.now(), LocalTime.now().minusMinutes(5));
        t.setId(id);
        t.setStatus(TaskStatus.PENDING);
        return t;
    }

    private Task futureTask(long id) {
        Task t = new Task("Futura " + id, LocalDate.now(), LocalTime.now().plusHours(3));
        t.setId(id);
        t.setStatus(TaskStatus.PENDING);
        return t;
    }

    @Test
    void disparaLembreteParaTarefaVencida() {
        pending.add(overdueTask(1L));
        AtomicInteger fireCount = new AtomicInteger();
        List<Boolean> overdueFlags = new ArrayList<>();
        scheduler.setListener((task, overdue) -> {
            fireCount.incrementAndGet();
            overdueFlags.add(overdue);
        });

        scheduler.checkNow();

        assertEquals(1, fireCount.get());
        assertFalse(overdueFlags.get(0)); // primeira vez: nao e "atrasada", e o disparo em cima da hora
    }

    @Test
    void naoDisparaParaTarefaAindaNoFuturo() {
        pending.add(futureTask(2L));
        AtomicInteger fireCount = new AtomicInteger();
        scheduler.setListener((task, overdue) -> fireCount.incrementAndGet());

        scheduler.checkNow();

        assertEquals(0, fireCount.get());
    }

    @Test
    void naoRepeteAntesDoIntervaloConfigurado() {
        pending.add(overdueTask(3L));
        AtomicInteger fireCount = new AtomicInteger();
        scheduler.setListener((task, overdue) -> fireCount.incrementAndGet());

        scheduler.checkNow(); // dispara 1a vez
        scheduler.checkNow(); // interval de 30 min nao passou -> nao deve disparar de novo
        scheduler.checkNow();

        assertEquals(1, fireCount.get());
    }

    @Test
    void repeteComoAtrasadaQuandoIntervaloJaPassou() throws Exception {
        Task task = overdueTask(4L);
        pending.add(task);
        AtomicInteger fireCount = new AtomicInteger();
        List<Boolean> overdueFlags = new ArrayList<>();
        scheduler.setListener((t, overdue) -> {
            fireCount.incrementAndGet();
            overdueFlags.add(overdue);
        });

        scheduler.checkNow(); // 1a vez: dispara normal
        forceIntervalElapsed(task.getId());
        scheduler.checkNow(); // agora deve disparar como atrasada

        assertEquals(2, fireCount.get());
        assertFalse(overdueFlags.get(0));
        assertEquals(Boolean.TRUE, overdueFlags.get(1));
    }

    @Test
    void paraDeLembrarQuandoTarefaSaiDaListaDePendentes() {
        Task task = overdueTask(5L);
        pending.add(task);
        AtomicInteger fireCount = new AtomicInteger();
        scheduler.setListener((t, overdue) -> fireCount.incrementAndGet());

        scheduler.checkNow();
        pending.remove(task); // tarefa foi concluida em outro lugar
        scheduler.checkNow();

        assertEquals(1, fireCount.get());
    }

    @Test
    void pausadoNaoDisparaLembretes() {
        pending.add(overdueTask(6L));
        AtomicInteger fireCount = new AtomicInteger();
        scheduler.setListener((t, overdue) -> fireCount.incrementAndGet());
        scheduler.setPaused(true);

        scheduler.checkNow();

        assertEquals(0, fireCount.get());
    }

    /** Usa reflexao para simular que o intervalo de repeticao ja passou, sem precisar esperar o tempo real. */
    @SuppressWarnings("unchecked")
    private void forceIntervalElapsed(long taskId) throws Exception {
        Field field = ReminderScheduler.class.getDeclaredField("nextFireAt");
        field.setAccessible(true);
        Map<Long, LocalDateTime> map = (Map<Long, LocalDateTime>) field.get(scheduler);
        map.put(taskId, LocalDateTime.now().minusMinutes(1));
    }
}
