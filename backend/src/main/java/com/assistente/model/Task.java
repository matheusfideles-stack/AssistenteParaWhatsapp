package com.assistente.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Representa uma tarefa/lembrete do usuario.
 * Espelha a tabela "tasks" do banco de dados.
 */
public class Task {

    private Long id;
    private String title;
    private String description;
    private LocalDate dueDate;
    private LocalTime dueTime;
    private TaskStatus status = TaskStatus.PENDING;
    private TaskPriority priority = TaskPriority.MEDIUM;
    private RecurrenceType recurrenceType = RecurrenceType.NONE;
    private String recurrenceValue;
    private Integer reminderIntervalMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    /** Controle em memoria: proximo instante em que um lembrete de repeticao deve soar. */
    private transient LocalDateTime nextReminderAt;

    public Task() {
    }

    public Task(String title, LocalDate dueDate, LocalTime dueTime) {
        this.title = title;
        this.dueDate = dueDate;
        this.dueTime = dueTime;
    }

    public LocalDateTime getDueDateTime() {
        if (dueDate == null) {
            return null;
        }
        return LocalDateTime.of(dueDate, dueTime == null ? LocalTime.MIDNIGHT : dueTime);
    }

    public boolean isOverdue(LocalDateTime now) {
        LocalDateTime due = getDueDateTime();
        return status == TaskStatus.PENDING && due != null && due.isBefore(now);
    }

    public boolean isRecurring() {
        return recurrenceType != null && recurrenceType != RecurrenceType.NONE;
    }

    // ----- getters / setters -----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalTime dueTime) {
        this.dueTime = dueTime;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public RecurrenceType getRecurrenceType() {
        return recurrenceType;
    }

    public void setRecurrenceType(RecurrenceType recurrenceType) {
        this.recurrenceType = recurrenceType;
    }

    public String getRecurrenceValue() {
        return recurrenceValue;
    }

    public void setRecurrenceValue(String recurrenceValue) {
        this.recurrenceValue = recurrenceValue;
    }

    public Integer getReminderIntervalMinutes() {
        return reminderIntervalMinutes;
    }

    public void setReminderIntervalMinutes(Integer reminderIntervalMinutes) {
        this.reminderIntervalMinutes = reminderIntervalMinutes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getNextReminderAt() {
        return nextReminderAt;
    }

    public void setNextReminderAt(LocalDateTime nextReminderAt) {
        this.nextReminderAt = nextReminderAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task task)) return false;
        return Objects.equals(id, task.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Task{id=%s, title='%s', dueDate=%s, dueTime=%s, status=%s, priority=%s}"
                .formatted(id, title, dueDate, dueTime, status, priority);
    }
}
