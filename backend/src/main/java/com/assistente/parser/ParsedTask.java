package com.assistente.parser;

import com.assistente.model.RecurrenceType;
import com.assistente.model.TaskPriority;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Resultado da interpretacao de uma frase em linguagem natural pelo {@link TaskParser}.
 * E um DTO simples - vira um {@link com.assistente.model.Task} atraves do TaskService.
 */
public class ParsedTask {

    private String title;
    private LocalDate date;
    private LocalTime time;
    private RecurrenceType recurrenceType = RecurrenceType.NONE;
    private String recurrenceValue;
    private Integer reminderIntervalMinutes;
    private TaskPriority priority = TaskPriority.MEDIUM;
    private final String originalText;

    public ParsedTask(String originalText) {
        this.originalText = originalText;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
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

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public String getOriginalText() {
        return originalText;
    }

    @Override
    public String toString() {
        return "ParsedTask{title='%s', date=%s, time=%s, recurrence=%s/%s, reminderInterval=%s, priority=%s}"
                .formatted(title, date, time, recurrenceType, recurrenceValue, reminderIntervalMinutes, priority);
    }
}
