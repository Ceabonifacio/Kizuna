package com.kizuna.db;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws Exception;
    }

    private final Path file;

    public Database(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public Path file() { return file; }

    public Connection open() throws SQLException {
        try {
            Files.createDirectories(file.getParent());
            Class.forName("org.sqlite.JDBC");
        } catch (IOException | ClassNotFoundException e) {
            throw new SQLException("Could not prepare the local Kizuna database.", e);
        }
        String databasePath = file.toString().replace(File.separatorChar, '/');
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    public void initialize() throws SQLException {
        try (Connection connection = open()) {
            int version;
            try (var statement = connection.createStatement(); var result = statement.executeQuery("PRAGMA user_version")) {
                version = result.next() ? result.getInt(1) : 0;
            }
            if (version > Schema.VERSION) {
                throw new SQLException("This Kizuna database is newer than this application supports (schema " + version + ").");
            }
            if (version == 0) {
                if (hasApplicationTables(connection)) {
                    throw new SQLException("This existing Kizuna database has no schema version. Its data was left unchanged; back it up and migrate it before continuing.");
                }
                connection.setAutoCommit(false);
                try {
                    Schema.create(connection);
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("PRAGMA user_version = " + Schema.VERSION);
                    }
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } else if (version == 1) {
                connection.setAutoCommit(false);
                try {
                    Schema.create(connection);
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("PRAGMA user_version = " + Schema.VERSION);
                    }
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } else if (version != Schema.VERSION) {
                throw new SQLException("No safe migration is available for database schema " + version + ".");
            }
        }
    }

    private static boolean hasApplicationTables(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' LIMIT 1")) {
            return result.next();
        }
    }

    public <T> T transaction(SqlWork<T> work) throws Exception {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception | Error e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }

    public <T> T read(SqlWork<T> work) throws Exception {
        try (Connection connection = open()) {
            return work.run(connection);
        } catch (SQLException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Could not read the local Kizuna database.", e);
        }
    }
}
