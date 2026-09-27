package com.assistente.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaskTest {

    @Test
    void tarefaPendenteComDataPassadaEstaAtrasada() {
        Task task = new Task("Antiga", LocalDate.now().minusDays(1), LocalTime.NOON);
        task.setStatus(TaskStatus.PENDING);
        assertTrue(task.isOverdue(LocalDateTime.now()));
    }

    @Test
    void tarefaConcluidaNuncaEstaAtrasada() {
        Task task = new Task("Antiga", LocalDate.now().minusDays(1), LocalTime.NOON);
        task.setStatus(TaskStatus.COMPLETED);
        assertFalse(task.isOverdue(LocalDateTime.now()));
    }

    @Test
    void tarefaSemDataNuncaEstaAtrasada() {
        Task task = new Task();
        task.setStatus(TaskStatus.PENDING);
        assertNull(task.getDueDateTime());
        assertFalse(task.isOverdue(LocalDateTime.now()));
    }

    @Test
    void tarefaComRecorrenciaNoneNaoERecorrente() {
        Task task = new Task();
        task.setRecurrenceType(RecurrenceType.NONE);
        assertFalse(task.isRecurring());
    }

    @Test
    void tarefaComRecorrenciaDailyERecorrente() {
        Task task = new Task();
        task.setRecurrenceType(RecurrenceType.DAILY);
        assertTrue(task.isRecurring());
    }
}
