package com.assistente.repository;

import com.assistente.database.SqliteDatabaseManager;
import com.assistente.model.RecurrenceType;
import com.assistente.model.Task;
import com.assistente.model.TaskPriority;
import com.assistente.model.TaskStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acesso a tabela "tasks" no SQLite. Datas/horas sao guardadas como TEXT em
 * formato ISO-8601 (yyyy-MM-dd / HH:mm:ss), que o SQLite ordena e compara
 * corretamente como se fossem strings.
 */
public class TaskRepository {

    private final SqliteDatabaseManager db;

    public TaskRepository(SqliteDatabaseManager db) {
        this.db = db;
    }

    public Task save(Task task) {
        if (task.getId() == null) {
            return insert(task);
        }
        return update(task);
    }

    private Task insert(Task task) {
        String sql = """
                INSERT INTO tasks
                    (title, description, due_date, due_time, status, priority,
                     recurrence_type, recurrence_value, reminder_interval,
                     created_at, updated_at, completed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, task);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    task.setId(keys.getLong(1));
                }
            }
            return task;
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao inserir tarefa: " + e.getMessage(), e);
        }
    }

    private Task update(Task task) {
        String sql = """
                UPDATE tasks SET title=?, description=?, due_date=?, due_time=?, status=?,
                    priority=?, recurrence_type=?, recurrence_value=?, reminder_interval=?,
                    created_at=?, updated_at=?, completed_at=?
                WHERE id=?
                """;
        task.setUpdatedAt(LocalDateTime.now());
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, task);
            ps.setLong(13, task.getId());
            ps.executeUpdate();
            return task;
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao atualizar tarefa: " + e.getMessage(), e);
        }
    }

    private void bind(PreparedStatement ps, Task t) throws SQLException {
        ps.setString(1, t.getTitle());
        ps.setString(2, t.getDescription());
        ps.setString(3, t.getDueDate() == null ? null : t.getDueDate().toString());
        ps.setString(4, t.getDueTime() == null ? null : t.getDueTime().toString());
        ps.setString(5, t.getStatus().name());
        ps.setString(6, t.getPriority().name());
        ps.setString(7, t.getRecurrenceType().name());
        ps.setString(8, t.getRecurrenceValue());
        if (t.getReminderIntervalMinutes() == null) {
            ps.setNull(9, java.sql.Types.INTEGER);
        } else {
            ps.setInt(9, t.getReminderIntervalMinutes());
        }
        ps.setString(10, t.getCreatedAt().toString());
        ps.setString(11, t.getUpdatedAt().toString());
        ps.setString(12, t.getCompletedAt() == null ? null : t.getCompletedAt().toString());
    }

    public void deleteById(long id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM tasks WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao excluir tarefa: " + e.getMessage(), e);
        }
    }

    public Optional<Task> findById(long id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM tasks WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao buscar tarefa: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<Task> findAll() {
        String sql = "SELECT * FROM tasks ORDER BY due_date IS NULL, due_date, due_time IS NULL, due_time, id";
        return query(sql);
    }

    public List<Task> findByDate(LocalDate date) {
        String sql = "SELECT * FROM tasks WHERE due_date=? ORDER BY due_time IS NULL, due_time, id";
        return query(sql, ps -> ps.setString(1, date.toString()));
    }

    public List<Task> findByStatus(TaskStatus status) {
        String sql = "SELECT * FROM tasks WHERE status=? ORDER BY due_date IS NULL, due_date, due_time IS NULL, due_time, id";
        return query(sql, ps -> ps.setString(1, status.name()));
    }

    public List<Task> findOverdue(LocalDateTime now) {
        String sql = """
                SELECT * FROM tasks
                WHERE status='PENDING' AND due_date IS NOT NULL
                  AND (due_date < ? OR (due_date = ? AND due_time IS NOT NULL AND due_time < ?))
                ORDER BY due_date, due_time
                """;
        return query(sql, ps -> {
            ps.setString(1, now.toLocalDate().toString());
            ps.setString(2, now.toLocalDate().toString());
            ps.setString(3, now.toLocalTime().toString());
        });
    }

    public List<Task> findPendingWithDueDateTimeBetween(LocalDateTime from, LocalDateTime to) {
        // Usado pelo scheduler para descobrir tarefas que vencem dentro da janela de verificacao.
        return findAll().stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .filter(t -> t.getDueDateTime() != null)
                .filter(t -> !t.getDueDateTime().isBefore(from) && t.getDueDateTime().isBefore(to))
                .toList();
    }

    public List<Task> search(String term) {
        String sql = "SELECT * FROM tasks WHERE title LIKE ? OR description LIKE ? " +
                "ORDER BY due_date IS NULL, due_date, due_time IS NULL, due_time, id";
        String like = "%" + term + "%";
        return query(sql, ps -> {
            ps.setString(1, like);
            ps.setString(2, like);
        });
    }

    public List<Task> findRecurring() {
        String sql = "SELECT * FROM tasks WHERE recurrence_type <> 'NONE'";
        return query(sql);
    }

    private List<Task> query(String sql) {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            List<Task> list = new ArrayList<>();
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao consultar tarefas: " + e.getMessage(), e);
        }
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private List<Task> query(String sql, Binder binder) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<Task> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao consultar tarefas: " + e.getMessage(), e);
        }
    }

    private Task map(ResultSet rs) throws SQLException {
        Task t = new Task();
        t.setId(rs.getLong("id"));
        t.setTitle(rs.getString("title"));
        t.setDescription(rs.getString("description"));
        String due = rs.getString("due_date");
        t.setDueDate(due == null ? null : LocalDate.parse(due));
        String time = rs.getString("due_time");
        t.setDueTime(time == null ? null : LocalTime.parse(time));
        t.setStatus(TaskStatus.valueOf(rs.getString("status")));
        t.setPriority(TaskPriority.valueOf(rs.getString("priority")));
        t.setRecurrenceType(RecurrenceType.valueOf(rs.getString("recurrence_type")));
        t.setRecurrenceValue(rs.getString("recurrence_value"));
        int interval = rs.getInt("reminder_interval");
        t.setReminderIntervalMinutes(rs.wasNull() ? null : interval);
        String created = rs.getString("created_at");
        t.setCreatedAt(created == null ? null : LocalDateTime.parse(created));
        String updated = rs.getString("updated_at");
        t.setUpdatedAt(updated == null ? null : LocalDateTime.parse(updated));
        String completed = rs.getString("completed_at");
        t.setCompletedAt(completed == null ? null : LocalDateTime.parse(completed));
        return t;
    }
}
