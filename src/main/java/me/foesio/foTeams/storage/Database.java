package me.foesio.foTeams.storage;

import me.foesio.foTeams.FoTeams;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public final class Database {
    private final FoTeams plugin;
    private Connection connection;

    public Database(FoTeams plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File databaseFile = new File(plugin.getDataFolder(), "teams.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS teams (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL UNIQUE," +
                    "tag TEXT NOT NULL," +
                    "description TEXT NOT NULL," +
                    "color TEXT NOT NULL," +
                    "owner_uuid TEXT NOT NULL," +
                    "home TEXT," +
                    "score INTEGER NOT NULL DEFAULT 0," +
                    "balance REAL NOT NULL DEFAULT 0," +
                    "team_level INTEGER NOT NULL DEFAULT 1," +
                    "team_xp INTEGER NOT NULL DEFAULT 0," +
                    "team_pvp_enabled INTEGER NOT NULL DEFAULT 1," +
                    "member_cap INTEGER NOT NULL DEFAULT 0," +
                    "echest_rows INTEGER NOT NULL DEFAULT 0," +
                    "created_at INTEGER NOT NULL," +
                    "echest TEXT NOT NULL DEFAULT ''" +
                    ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS team_members (team_id INTEGER NOT NULL, player_uuid TEXT NOT NULL, role TEXT NOT NULL, PRIMARY KEY(team_id, player_uuid))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS team_invites (team_id INTEGER NOT NULL, player_uuid TEXT NOT NULL, inviter_uuid TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, PRIMARY KEY(team_id, player_uuid))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS team_relations (source_team_id INTEGER NOT NULL, target_team_id INTEGER NOT NULL, relation TEXT NOT NULL, PRIMARY KEY(source_team_id, target_team_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS team_warps (team_id INTEGER NOT NULL, name TEXT NOT NULL, location TEXT NOT NULL, password TEXT, PRIMARY KEY(team_id, name))");
            int addedColumns = 0;
            addedColumns += addColumnIfMissing(statement, "team_warps", "password TEXT") ? 1 : 0;
            addedColumns += addColumnIfMissing(statement, "teams", "tag TEXT NOT NULL DEFAULT ''") ? 1 : 0;
            addedColumns += addColumnIfMissing(statement, "teams", "description TEXT NOT NULL DEFAULT 'A fresh team.'") ? 1 : 0;
            addedColumns += addColumnIfMissing(statement, "teams", "color TEXT NOT NULL DEFAULT '#03fc88'") ? 1 : 0;
            addedColumns += addColumnIfMissing(statement, "teams", "home TEXT") ? 1 : 0;
            addedColumns += addColumnIfMissing(statement, "teams", "score INTEGER NOT NULL DEFAULT 0") ? 1 : 0;
            addedColumns += addColumnIfMissing(statement, "teams", "balance REAL NOT NULL DEFAULT 0") ? 1 : 0;
            addedColumns += addColumnIfMissing(statement, "teams", "team_level INTEGER NOT NULL DEFAULT 1") ? 1 : 0;
            addedColumns += addColumnIfMissing(statement, "teams", "team_xp INTEGER NOT NULL DEFAULT 0") ? 1 : 0;
            addedColumns += addColumnIfMissing(statement, "teams", "team_pvp_enabled INTEGER NOT NULL DEFAULT 1") ? 1 : 0;
            addedColumns += addColumnIfMissing(statement, "teams", "member_cap INTEGER NOT NULL DEFAULT 0") ? 1 : 0;
            addedColumns += addColumnIfMissing(statement, "teams", "echest_rows INTEGER NOT NULL DEFAULT 0") ? 1 : 0;
            addedColumns += addColumnIfMissing(statement, "teams", "created_at INTEGER NOT NULL DEFAULT 0") ? 1 : 0;
            addedColumns += addColumnIfMissing(statement, "teams", "echest TEXT NOT NULL DEFAULT ''") ? 1 : 0;
            if (addedColumns > 0) {
                plugin.getLogger().info("Updated teams.db schema with " + addedColumns + " missing column(s). Existing team rows were left intact.");
            }
        }
    }

    private boolean addColumnIfMissing(Statement statement, String table, String definition) throws SQLException {
        String column = definition.split("\\s+", 2)[0];
        if (hasColumn(statement, table, column)) {
            return false;
        }
        statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + definition);
        return true;
    }

    private boolean hasColumn(Statement statement, String table, String column) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public Connection connection() {
        return connection;
    }

    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to close teams.db cleanly.", exception);
        } finally {
            connection = null;
        }
    }
}
