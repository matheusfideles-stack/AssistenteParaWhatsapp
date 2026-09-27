package com.assistente.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gerencia a conexao com o banco SQLite local (data/assistente.db). Sem
 * servidor, sem configuracao, sem internet - o arquivo e criado sozinho na
 * primeira execucao.
 */
public class SqliteDatabaseManager {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                description TEXT,
                due_date TEXT,
                due_time TEXT,
                status TEXT NOT NULL DEFAULT 'PENDING',
                priority TEXT NOT NULL DEFAULT 'MEDIUM',
                recurrence_type TEXT NOT NULL DEFAULT 'NONE',
                recurrence_value TEXT,
                reminder_interval INTEGER,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                completed_at TEXT
            )
            """;
    private static final String CREATE_INDEX_DUE_SQL =
            "CREATE INDEX IF NOT EXISTS idx_due ON tasks (due_date, due_time)";
    private static final String CREATE_INDEX_STATUS_SQL =
            "CREATE INDEX IF NOT EXISTS idx_status ON tasks (status)";

    private final String jdbcUrl;

    public SqliteDatabaseManager(Path dbFile) {
        try {
            Files.createDirectories(dbFile.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel criar a pasta do banco: " + e.getMessage(), e);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Driver JDBC do SQLite nao encontrado no classpath.", e);
        }
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();
    }

    /** Abre uma nova conexao. Chamador e responsavel por fechar (try-with-resources). */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    /** Garante que a tabela "tasks" e seus indices existam. */
    public void initializeSchema() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute(CREATE_TABLE_SQL);
            st.execute(CREATE_INDEX_DUE_SQL);
            st.execute(CREATE_INDEX_STATUS_SQL);
        } catch (SQLException e) {
            throw new IllegalStateException("Nao foi possivel inicializar o banco SQLite em "
                    + jdbcUrl + ": " + e.getMessage(), e);
        }
    }
}
