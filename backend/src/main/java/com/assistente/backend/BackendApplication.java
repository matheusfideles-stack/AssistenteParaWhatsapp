package com.assistente.backend;

import com.assistente.database.SqliteDatabaseManager;
import com.assistente.parser.TaskParser;
import com.assistente.repository.TaskRepository;
import com.assistente.scheduler.ReminderScheduler;
import com.assistente.service.BackupService;
import com.assistente.service.TaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @Configuration
    static class BeansConfig {

        @Bean
        SqliteDatabaseManager sqliteDatabaseManager(@Value("${assistente.db-path}") String dbPath) {
            SqliteDatabaseManager manager = new SqliteDatabaseManager(Paths.get(dbPath));
            manager.initializeSchema();
            return manager;
        }

        @Bean
        TaskRepository taskRepository(SqliteDatabaseManager db) {
            return new TaskRepository(db);
        }

        @Bean
        TaskParser taskParser() {
            return new TaskParser();
        }

        @Bean
        TaskService taskService(TaskRepository repository, TaskParser parser) {
            return new TaskService(repository, parser);
        }

        @Bean
        BackupService backupService(TaskRepository repository) {
            return new BackupService(repository);
        }

        @Bean
        ReminderScheduler reminderScheduler(
                TaskService taskService,
                @Value("${assistente.default-reminder-interval-minutes}") int defaultIntervalMinutes) {
            return new ReminderScheduler(taskService, () -> defaultIntervalMinutes);
        }

        @Bean
        Path backupDirectory() {
            return Paths.get(System.getProperty("user.home"), "AssistentePessoal-Backend-backups");
        }

        @Bean
        CommandHandler commandHandler(TaskService taskService, BackupService backupService, Path backupDirectory) {
            return new CommandHandler(taskService, backupService, backupDirectory);
        }
    }
}
