package me.foesio.foTeams.storage;

import me.foesio.foTeams.model.RelationType;
import me.foesio.foTeams.model.Team;
import me.foesio.foTeams.model.TeamInvite;
import me.foesio.foTeams.model.TeamRole;
import me.foesio.foTeams.util.InventoryCodec;
import me.foesio.foTeams.util.LocationUtil;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TeamRepository {
    private final Database database;

    public TeamRepository(Database database) {
        this.database = database;
    }

    public Map<Integer, Team> loadTeams() throws SQLException {
        Map<Integer, Team> teams = new HashMap<>();
        Connection connection = database.connection();
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT * FROM teams")) {
            while (resultSet.next()) {
                Team team = new Team(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getString("tag"),
                        resultSet.getString("description"),
                        resultSet.getString("color"),
                        UUID.fromString(resultSet.getString("owner_uuid")),
                        LocationUtil.deserialize(resultSet.getString("home")),
                        resultSet.getInt("score"),
                        resultSet.getDouble("balance"),
                        resultSet.getInt("team_level"),
                        resultSet.getLong("team_xp"),
                        resultSet.getInt("team_pvp_enabled") == 1,
                        resultSet.getInt("member_cap"),
                        resultSet.getInt("echest_rows"),
                        resultSet.getLong("created_at")
                );
                team.getEchestContents().addAll(InventoryCodec.decode(resultSet.getString("echest")));
                teams.put(team.getId(), team);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM team_members")) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Team team = teams.get(resultSet.getInt("team_id"));
                if (team == null) {
                    continue;
                }
                UUID playerId = UUID.fromString(resultSet.getString("player_uuid"));
                TeamRole role = TeamRole.valueOf(resultSet.getString("role"));
                if (role == TeamRole.ADMIN) {
                    team.getAdmins().add(playerId);
                } else if (role == TeamRole.MEMBER) {
                    team.getMembers().add(playerId);
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM team_warps")) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Team team = teams.get(resultSet.getInt("team_id"));
                if (team != null) {
                    String warpName = resultSet.getString("name").toLowerCase();
                    team.getWarps().put(warpName, LocationUtil.deserialize(resultSet.getString("location")));
                    team.getWarpPasswords().put(warpName, resultSet.getString("password"));
                }
            }
        }
        return teams;
    }

    public Map<UUID, List<TeamInvite>> loadInvites() throws SQLException {
        Map<UUID, List<TeamInvite>> invites = new HashMap<>();
        try (PreparedStatement statement = database.connection().prepareStatement("SELECT * FROM team_invites")) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                TeamInvite invite = new TeamInvite(
                        resultSet.getInt("team_id"),
                        UUID.fromString(resultSet.getString("player_uuid")),
                        UUID.fromString(resultSet.getString("inviter_uuid")),
                        resultSet.getLong("created_at"),
                        resultSet.getLong("expires_at")
                );
                invites.computeIfAbsent(invite.playerId(), ignored -> new ArrayList<>()).add(invite);
            }
        }
        return invites;
    }

    public Map<Integer, Map<Integer, RelationType>> loadRelations() throws SQLException {
        Map<Integer, Map<Integer, RelationType>> relations = new HashMap<>();
        try (PreparedStatement statement = database.connection().prepareStatement("SELECT * FROM team_relations")) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                relations.computeIfAbsent(resultSet.getInt("source_team_id"), ignored -> new HashMap<>())
                        .put(resultSet.getInt("target_team_id"), RelationType.valueOf(resultSet.getString("relation")));
            }
        }
        return relations;
    }

    public Team createTeam(String name, UUID ownerId, String defaultTag, String defaultDescription, String color, boolean teamPvpEnabled, int memberCap, int echestRows) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "INSERT INTO teams(name, tag, description, color, owner_uuid, home, score, balance, team_level, team_xp, team_pvp_enabled, member_cap, echest_rows, created_at, echest) VALUES(?, ?, ?, ?, ?, ?, 0, 0, 1, 0, ?, ?, ?, ?, '')",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, defaultTag);
            statement.setString(3, defaultDescription);
            statement.setString(4, color);
            statement.setString(5, ownerId.toString());
            statement.setString(6, null);
            statement.setInt(7, teamPvpEnabled ? 1 : 0);
            statement.setInt(8, memberCap);
            statement.setInt(9, echestRows);
            statement.setLong(10, now);
            statement.executeUpdate();
            ResultSet keys = statement.getGeneratedKeys();
            if (!keys.next()) {
                throw new SQLException("No key returned for team create");
            }
            return new Team(keys.getInt(1), name, defaultTag, defaultDescription, color, ownerId, null, 0, 0, 1, 0, teamPvpEnabled, memberCap, echestRows, now);
        }
    }

    public void saveTeam(Team team) throws SQLException {
        inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE teams SET name = ?, tag = ?, description = ?, color = ?, owner_uuid = ?, home = ?, score = ?, balance = ?, team_level = ?, team_xp = ?, team_pvp_enabled = ?, member_cap = ?, echest_rows = ?, echest = ? WHERE id = ?")) {
                statement.setString(1, team.getName());
                statement.setString(2, team.getTag());
                statement.setString(3, team.getDescription());
                statement.setString(4, team.getColor());
                statement.setString(5, team.getOwnerId().toString());
                statement.setString(6, LocationUtil.serialize(team.getHome()));
                statement.setInt(7, team.getScore());
                statement.setDouble(8, team.getBalance());
                statement.setInt(9, team.getTeamLevel());
                statement.setLong(10, team.getTeamXp());
                statement.setInt(11, team.isTeamPvpProtectionEnabled() ? 1 : 0);
                statement.setInt(12, team.getMemberCap());
                statement.setInt(13, team.getEchestRows());
                statement.setString(14, InventoryCodec.encode(team.getEchestContents()));
                statement.setInt(15, team.getId());
                statement.executeUpdate();
            }

            try (PreparedStatement deleteMembers = connection.prepareStatement("DELETE FROM team_members WHERE team_id = ?");
                 PreparedStatement insertMembers = connection.prepareStatement("INSERT INTO team_members(team_id, player_uuid, role) VALUES(?, ?, ?)")) {
                deleteMembers.setInt(1, team.getId());
                deleteMembers.executeUpdate();
                for (UUID admin : team.getAdmins()) {
                    insertMembers.setInt(1, team.getId());
                    insertMembers.setString(2, admin.toString());
                    insertMembers.setString(3, TeamRole.ADMIN.name());
                    insertMembers.addBatch();
                }
                for (UUID member : team.getMembers()) {
                    insertMembers.setInt(1, team.getId());
                    insertMembers.setString(2, member.toString());
                    insertMembers.setString(3, TeamRole.MEMBER.name());
                    insertMembers.addBatch();
                }
                insertMembers.executeBatch();
            }

            try (PreparedStatement deleteWarps = connection.prepareStatement("DELETE FROM team_warps WHERE team_id = ?");
                 PreparedStatement insertWarps = connection.prepareStatement("INSERT INTO team_warps(team_id, name, location, password) VALUES(?, ?, ?, ?)")) {
                deleteWarps.setInt(1, team.getId());
                deleteWarps.executeUpdate();
                for (Map.Entry<String, Location> entry : team.getWarps().entrySet()) {
                    insertWarps.setInt(1, team.getId());
                    insertWarps.setString(2, entry.getKey());
                    insertWarps.setString(3, LocationUtil.serialize(entry.getValue()));
                    insertWarps.setString(4, team.getWarpPasswords().get(entry.getKey()));
                    insertWarps.addBatch();
                }
                insertWarps.executeBatch();
            }
        });
    }

    public void saveTeamLevel(Team team) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement("UPDATE teams SET team_level = ?, team_xp = ? WHERE id = ?")) {
            statement.setInt(1, team.getTeamLevel());
            statement.setLong(2, team.getTeamXp());
            statement.setInt(3, team.getId());
            statement.executeUpdate();
        }
    }

    public void saveTeamLevels(Collection<Team> teams) throws SQLException {
        if (teams.isEmpty()) {
            return;
        }

        inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE teams SET team_level = ?, team_xp = ? WHERE id = ?")) {
                for (Team team : teams) {
                    statement.setInt(1, team.getTeamLevel());
                    statement.setLong(2, team.getTeamXp());
                    statement.setInt(3, team.getId());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    public void saveTeamEchest(Team team) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement("UPDATE teams SET echest_rows = ?, echest = ? WHERE id = ?")) {
            statement.setInt(1, team.getEchestRows());
            statement.setString(2, InventoryCodec.encode(team.getEchestContents()));
            statement.setInt(3, team.getId());
            statement.executeUpdate();
        }
    }

    public void deleteTeam(int teamId) throws SQLException {
        inTransaction(connection -> {
            try (PreparedStatement deleteRelationsA = connection.prepareStatement("DELETE FROM team_relations WHERE source_team_id = ? OR target_team_id = ?");
                 PreparedStatement deleteInvites = connection.prepareStatement("DELETE FROM team_invites WHERE team_id = ?");
                 PreparedStatement deleteMembers = connection.prepareStatement("DELETE FROM team_members WHERE team_id = ?");
                 PreparedStatement deleteWarps = connection.prepareStatement("DELETE FROM team_warps WHERE team_id = ?");
                 PreparedStatement deleteTeam = connection.prepareStatement("DELETE FROM teams WHERE id = ?")) {
                deleteRelationsA.setInt(1, teamId);
                deleteRelationsA.setInt(2, teamId);
                deleteRelationsA.executeUpdate();
                deleteInvites.setInt(1, teamId);
                deleteInvites.executeUpdate();
                deleteMembers.setInt(1, teamId);
                deleteMembers.executeUpdate();
                deleteWarps.setInt(1, teamId);
                deleteWarps.executeUpdate();
                deleteTeam.setInt(1, teamId);
                deleteTeam.executeUpdate();
            }
        });
    }

    public void saveInvites(List<TeamInvite> invites) throws SQLException {
        inTransaction(connection -> {
            try (Statement clear = connection.createStatement()) {
                clear.executeUpdate("DELETE FROM team_invites");
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO team_invites(team_id, player_uuid, inviter_uuid, created_at, expires_at) VALUES(?, ?, ?, ?, ?)")) {
                for (TeamInvite invite : invites) {
                    statement.setInt(1, invite.teamId());
                    statement.setString(2, invite.playerId().toString());
                    statement.setString(3, invite.inviterId().toString());
                    statement.setLong(4, invite.createdAt());
                    statement.setLong(5, invite.expiresAt());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    public void saveRelations(Map<Integer, Map<Integer, RelationType>> relations) throws SQLException {
        inTransaction(connection -> {
            try (Statement clear = connection.createStatement()) {
                clear.executeUpdate("DELETE FROM team_relations");
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO team_relations(source_team_id, target_team_id, relation) VALUES(?, ?, ?)")) {
                for (Map.Entry<Integer, Map<Integer, RelationType>> source : relations.entrySet()) {
                    for (Map.Entry<Integer, RelationType> relation : source.getValue().entrySet()) {
                        statement.setInt(1, source.getKey());
                        statement.setInt(2, relation.getKey());
                        statement.setString(3, relation.getValue().name());
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
            }
        });
    }

    private void inTransaction(SqlAction action) throws SQLException {
        Connection connection = database.connection();
        boolean previousAutoCommit = connection.getAutoCommit();
        try {
            if (previousAutoCommit) {
                connection.setAutoCommit(false);
            }
            action.run(connection);
            if (previousAutoCommit) {
                connection.commit();
            }
        } catch (SQLException exception) {
            if (previousAutoCommit) {
                connection.rollback();
            }
            throw exception;
        } finally {
            if (previousAutoCommit) {
                connection.setAutoCommit(true);
            }
        }
    }

    private interface SqlAction {
        void run(Connection connection) throws SQLException;
    }
}
