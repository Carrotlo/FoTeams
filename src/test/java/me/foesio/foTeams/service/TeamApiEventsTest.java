package me.foesio.foTeams.service;

import me.foesio.foTeams.api.event.TeamCreatedEvent;
import me.foesio.foTeams.api.event.TeamDisbandedEvent;
import me.foesio.foTeams.api.event.TeamMemberChangedEvent;
import me.foesio.foTeams.api.event.TeamPreCreateEvent;
import me.foesio.foTeams.api.event.TeamPreJoinEvent;
import me.foesio.foTeams.api.event.TeamRelationChangedEvent;
import me.foesio.foTeams.api.TeamSnapshot;
import me.foesio.foTeams.api.WarpSnapshot;
import me.foesio.foTeams.model.RelationType;
import me.foesio.foTeams.model.Team;
import me.foesio.foTeams.storage.Database;
import me.foesio.foTeams.storage.TeamRepository;
import org.bukkit.event.Event;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamApiEventsTest {
    private Connection connection;
    private TeamService service;
    private final List<Event> events = new ArrayList<>();
    private final AtomicBoolean cancelCreate = new AtomicBoolean();
    private final AtomicBoolean cancelJoin = new AtomicBoolean();

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE teams (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, tag TEXT NOT NULL, description TEXT NOT NULL, color TEXT NOT NULL, owner_uuid TEXT NOT NULL, home TEXT, score INTEGER NOT NULL DEFAULT 0, balance REAL NOT NULL DEFAULT 0, team_level INTEGER NOT NULL DEFAULT 1, team_xp INTEGER NOT NULL DEFAULT 0, team_pvp_enabled INTEGER NOT NULL DEFAULT 1, member_cap INTEGER NOT NULL DEFAULT 20, echest_rows INTEGER NOT NULL DEFAULT 3, created_at INTEGER NOT NULL, echest TEXT NOT NULL DEFAULT '')");
            statement.executeUpdate("CREATE TABLE team_members (team_id INTEGER NOT NULL, player_uuid TEXT NOT NULL, role TEXT NOT NULL, PRIMARY KEY(team_id, player_uuid))");
            statement.executeUpdate("CREATE TABLE team_invites (team_id INTEGER NOT NULL, player_uuid TEXT NOT NULL, inviter_uuid TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, PRIMARY KEY(team_id, player_uuid))");
            statement.executeUpdate("CREATE TABLE team_relations (source_team_id INTEGER NOT NULL, target_team_id INTEGER NOT NULL, relation TEXT NOT NULL, PRIMARY KEY(source_team_id, target_team_id))");
            statement.executeUpdate("CREATE TABLE team_warps (team_id INTEGER NOT NULL, name TEXT NOT NULL, location TEXT NOT NULL, password TEXT, PRIMARY KEY(team_id, name))");
        }
        Database database = new Database(null);
        Field field = Database.class.getDeclaredField("connection");
        field.setAccessible(true);
        field.set(database, connection);
        TeamRepository repository = new TeamRepository(database);
        Plugin owner = mock(Plugin.class);
        when(owner.isEnabled()).thenReturn(true);
        TeamService.Settings settings = new TeamService.Settings(30, 20, 5, 12, 4, 300,
                true, 6, 3, 100, 1.2, 100, 1.2);
        service = new TeamService(repository, null, Logger.getLogger("TeamApiEventsTest"), settings,
                owner, event -> {
                    events.add(event);
                    if (event instanceof TeamPreCreateEvent pre && cancelCreate.get()) pre.setCancelled(true);
                    if (event instanceof TeamPreJoinEvent pre && cancelJoin.get()) pre.setCancelled(true);
                });
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    @Test
    void cancelledCreationAndJoinLeaveNoPartialMembership() throws Exception {
        UUID owner = UUID.randomUUID();
        cancelCreate.set(true);
        assertThrows(TeamOperationCancelledException.class, () -> service.createTeam(owner, "Alpha"));
        assertEquals(0, count("teams"));
        assertEquals(List.of(TeamPreCreateEvent.class), eventTypes());

        cancelCreate.set(false);
        events.clear();
        Team team = service.createTeam(owner, "Alpha");
        assertEquals(List.of(TeamPreCreateEvent.class, TeamCreatedEvent.class), eventTypes());

        UUID invitee = UUID.randomUUID();
        service.invite(team, owner, invitee);
        cancelJoin.set(true);
        events.clear();
        assertThrows(TeamOperationCancelledException.class, () -> service.join(invitee, team));
        assertTrue(service.teamOf(invitee).isEmpty());
        assertTrue(service.hasInvite(invitee, team.getId()));
        assertEquals(0, count("team_members"));
        assertEquals(List.of(TeamPreJoinEvent.class), eventTypes());

        cancelJoin.set(false);
        events.clear();
        service.join(invitee, team);
        assertEquals(List.of(TeamPreJoinEvent.class, TeamMemberChangedEvent.class), eventTypes());
        assertEquals(team.getId(), service.teamOf(invitee).orElseThrow().getId());
        assertEquals(1, count("team_members"));
        assertFalse(service.hasInvite(invitee, team.getId()));
    }

    @Test
    void failedMemberSaveRestoresMemoryAndFiresNoPostEvent() throws Exception {
        Team team = service.createTeam(UUID.randomUUID(), "Alpha");
        UUID invitee = UUID.randomUUID();
        service.invite(team, team.getOwnerId(), invitee);
        service.join(invitee, team);
        events.clear();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE team_members");
        }
        assertThrows(SQLException.class, () -> service.leave(invitee));
        assertEquals(team.getId(), service.teamOf(invitee).orElseThrow().getId());
        assertTrue(team.getMembers().contains(invitee));
        assertTrue(events.isEmpty());
    }

    @Test
    void relationAndDisbandEventsFollowStoredChanges() throws Exception {
        Team alpha = service.createOwnerlessTeam("Alpha");
        Team beta = service.createOwnerlessTeam("Beta");
        events.clear();
        assertFalse(service.requestOrAcceptAlly(alpha, beta));
        assertEquals(RelationType.ALLY_REQUEST, service.relation(alpha, beta));
        assertEquals(1, events.stream().filter(TeamRelationChangedEvent.class::isInstance).count());
        events.clear();
        service.disband(alpha);
        assertEquals(1, events.stream().filter(TeamRelationChangedEvent.class::isInstance).count());
        assertInstanceOf(TeamDisbandedEvent.class, events.getLast());
        assertEquals(1, count("teams"));
        assertEquals(0, count("team_relations"));
    }

    @Test
    void repeatedAllyActionKeepsExistingAllianceAndEmitsNoChange() throws Exception {
        Team alpha = service.createOwnerlessTeam("Alpha");
        Team beta = service.createOwnerlessTeam("Beta");
        service.requestOrAcceptAlly(alpha, beta);
        service.requestOrAcceptAlly(beta, alpha);
        events.clear();
        assertTrue(service.requestOrAcceptAlly(alpha, beta));
        assertEquals(RelationType.ALLY, service.relation(alpha, beta));
        assertEquals(RelationType.ALLY, service.relation(beta, alpha));
        assertTrue(events.isEmpty());
    }

    @Test
    void failedRelationSaveRestoresMemoryAndFiresNoEvent() throws Exception {
        Team alpha = service.createOwnerlessTeam("Alpha");
        Team beta = service.createOwnerlessTeam("Beta");
        events.clear();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE team_relations");
        }
        assertThrows(SQLException.class, () -> service.setEnemy(alpha, beta));
        assertEquals(RelationType.NEUTRAL, service.relation(alpha, beta));
        assertEquals(RelationType.NEUTRAL, service.relation(beta, alpha));
        assertTrue(events.isEmpty());
    }

    @Test
    void snapshotAndWarpCannotMutateStoredValues() throws Exception {
        Team team = service.createTeam(UUID.randomUUID(), "Alpha");
        TeamSnapshot snapshot = TeamSnapshots.of(team);
        UUID laterMember = UUID.randomUUID();
        team.getMembers().add(laterMember);
        assertFalse(snapshot.roles().containsKey(laterMember));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.roles().clear());

        Location source = new Location(null, 1, 2, 3);
        WarpSnapshot warp = new WarpSnapshot("base", source, true);
        source.setX(9);
        assertEquals(1, warp.location().getX());
        Location returned = warp.location();
        returned.setX(20);
        assertEquals(1, warp.location().getX());
    }

    @Test
    void deletedTeamCannotBeSavedOrDisbandedTwice() throws Exception {
        Team team = service.createOwnerlessTeam("Alpha");
        service.disband(team);
        events.clear();
        assertThrows(SQLException.class, () -> service.saveTeam(team));
        assertThrows(IllegalStateException.class, () -> service.disband(team));
        assertTrue(events.isEmpty());
    }

    @Test
    void savingUnchangedTeamDoesNotCreateTransitionEvents() throws Exception {
        service.createTeam(UUID.randomUUID(), "Alpha");
        events.clear();
        service.saveAll();
        service.saveAll();
        assertTrue(events.isEmpty());
        assertEquals(1, count("teams"));
    }

    private int count(String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return result.getInt(1);
        }
    }

    private List<? extends Class<?>> eventTypes() {
        return events.stream().map(Event::getClass).toList();
    }
}
