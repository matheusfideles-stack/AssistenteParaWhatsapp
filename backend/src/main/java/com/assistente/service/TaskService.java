package com.assistente.service;

import com.assistente.model.RecurrenceType;
import com.assistente.model.Task;
import com.assistente.model.TaskPriority;
import com.assistente.model.TaskStatus;
import com.assistente.parser.ParsedTask;
import com.assistente.parser.TaskParser;
import com.assistente.repository.TaskRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Regras de negocio de tarefas: criacao a partir de linguagem natural,
 * conclusao, cancelamento, adiamento, reagendamento e geracao automatica
 * da proxima ocorrencia de tarefas recorrentes.
 */
public class TaskService {

    private final TaskRepository repository;
    private final TaskParser parser;

    public TaskService(TaskRepository repository, TaskParser parser) {
        this.repository = repository;
        this.parser = parser;
    }

    /** Cria e persiste uma tarefa a partir de uma frase em linguagem natural. */
    public Task createFromText(String text) {
        ParsedTask parsed = parser.parse(text);
        Task task = new Task();
        task.setTitle(parsed.getTitle());
        task.setDueDate(parsed.getDate());
        task.setDueTime(parsed.getTime());
        task.setRecurrenceType(parsed.getRecurrenceType());
        task.setRecurrenceValue(parsed.getRecurrenceValue());
        task.setReminderIntervalMinutes(parsed.getReminderIntervalMinutes());
        task.setPriority(parsed.getPriority());
        task.setStatus(TaskStatus.PENDING);
        return repository.save(task);
    }

    public Task createTask(Task task) {
        if (task.getStatus() == null) {
            task.setStatus(TaskStatus.PENDING);
        }
        if (task.getPriority() == null) {
            task.setPriority(TaskPriority.MEDIUM);
        }
        if (task.getRecurrenceType() == null) {
            task.setRecurrenceType(RecurrenceType.NONE);
        }
        return repository.save(task);
    }

    public Task updateTask(Task task) {
        if (task.getId() == null) {
            throw new IllegalArgumentException("Tarefa sem id nao pode ser atualizada.");
        }
        return repository.save(task);
    }

    public void deleteTask(long id) {
        repository.deleteById(id);
    }

    /** Marca como concluida e, se for recorrente, agenda automaticamente a proxima ocorrencia. */
    public Task complete(long id) {
        Task task = get(id);
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        repository.save(task);

        if (task.isRecurring()) {
            generateNextOccurrence(task);
        }
        return task;
    }

    public Task cancel(long id) {
        Task task = get(id);
        task.setStatus(TaskStatus.CANCELLED);
        repository.save(task);
        return task;
    }

    /** Adia a tarefa em N minutos a partir do horario atualmente agendado (ou de agora, se ja atrasada). */
    public Task postpone(long id, int minutes) {
        Task task = get(id);
        LocalDateTime base = task.getDueDateTime();
        LocalDateTime now = LocalDateTime.now();
        if (base == null || base.isBefore(now)) {
            base = now;
        }
        LocalDateTime newTime = base.plusMinutes(minutes);
        task.setDueDate(newTime.toLocalDate());
        task.setDueTime(newTime.toLocalTime());
        task.setStatus(TaskStatus.PENDING);
        repository.save(task);
        return task;
    }

    public Task reschedule(long id, LocalDate newDate, LocalTime newTime) {
        Task task = get(id);
        task.setDueDate(newDate);
        task.setDueTime(newTime);
        task.setStatus(TaskStatus.PENDING);
        repository.save(task);
        return task;
    }

    /** Gera a proxima ocorrencia de uma tarefa recorrente concluida, como uma nova tarefa PENDING. */
    public Task generateNextOccurrence(Task completed) {
        LocalDate base = completed.getDueDate() != null ? completed.getDueDate() : LocalDate.now();
        LocalDate nextDate = switch (completed.getRecurrenceType()) {
            case DAILY -> base.plusDays(1);
            case WEEKLY -> base.plusWeeks(1);
            case MONTHLY -> nextMonthDate(base, completed.getRecurrenceValue());
            case NONE -> null;
        };
        if (nextDate == null) {
            return null;
        }

        Task next = new Task();
        next.setTitle(completed.getTitle());
        next.setDescription(completed.getDescription());
        next.setDueDate(nextDate);
        next.setDueTime(completed.getDueTime());
        next.setPriority(completed.getPriority());
        next.setRecurrenceType(completed.getRecurrenceType());
        next.setRecurrenceValue(completed.getRecurrenceValue());
        next.setReminderIntervalMinutes(completed.getReminderIntervalMinutes());
        next.setStatus(TaskStatus.PENDING);
        return repository.save(next);
    }

    private LocalDate nextMonthDate(LocalDate base, String recurrenceValue) {
        int day = recurrenceValue != null ? Integer.parseInt(recurrenceValue) : base.getDayOfMonth();
        YearMonth nextMonth = YearMonth.from(base).plusMonths(1);
        int clamped = Math.min(day, nextMonth.lengthOfMonth());
        return nextMonth.atDay(clamped);
    }

    public Task get(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tarefa nao encontrada: id=" + id));
    }

    public Optional<Task> find(long id) {
        return repository.findById(id);
    }

    public List<Task> findAll() {
        return repository.findAll();
    }

    public List<Task> findToday() {
        return repository.findByDate(LocalDate.now());
    }

    public List<Task> findByDate(LocalDate date) {
        return repository.findByDate(date);
    }

    public List<Task> findPending() {
        return repository.findByStatus(TaskStatus.PENDING);
    }

    public List<Task> findCompleted() {
        return repository.findByStatus(TaskStatus.COMPLETED);
    }

    public List<Task> findOverdue() {
        return repository.findOverdue(LocalDateTime.now());
    }

    public List<Task> findHighPriority() {
        return repository.findAll().stream()
                .filter(t -> t.getPriority() == TaskPriority.HIGH)
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .toList();
    }

    public List<Task> findTomorrow() {
        return repository.findByDate(LocalDate.now().plusDays(1));
    }

    /** Tarefas pendentes com vencimento entre agora e daqui a N horas, ordenadas por horario. */
    public List<Task> findUpcoming(int hours) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime limit = now.plusHours(hours);
        return repository.findByStatus(TaskStatus.PENDING).stream()
                .filter(t -> t.getDueDateTime() != null)
                .filter(t -> !t.getDueDateTime().isBefore(now) && !t.getDueDateTime().isAfter(limit))
                .sorted(Comparator.comparing(Task::getDueDateTime))
                .toList();
    }

    public List<Task> search(String term) {
        if (term == null || term.isBlank()) {
            return findAll();
        }
        return repository.search(term.trim());
    }

    /** Proxima tarefa pendente a vencer (usada no card "PROXIMA TAREFA" do dashboard). */
    public Optional<Task> findNextUpcoming() {
        LocalDateTime now = LocalDateTime.now();
        return repository.findByStatus(TaskStatus.PENDING).stream()
                .filter(t -> t.getDueDateTime() != null && !t.getDueDateTime().isBefore(now))
                .min(Comparator.comparing(Task::getDueDateTime));
    }
}
