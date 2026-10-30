package me.foesio.foTeams.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.sql.SQLException;

/**
 * Supported FoTeams integration boundary. Obtain through Bukkit's ServicesManager.
 * Calls must run in the appropriate server/player execution context; this service is not async-safe.
 * A cached instance throws IllegalStateException while FoTeams is disabled.
 */
public interface FoTeamsService {
    Optional<TeamSnapshot> teamOf(UUID playerId);
    Optional<TeamSnapshot> teamById(int teamId);
    Optional<TeamSnapshot> teamByName(String name);
    List<String> teamNames();
    /** Returns the directional relation, or empty if either team does not exist. */
    Optional<TeamRelation> relation(int sourceTeamId, int targetTeamId);
    /** Returns a defensive copy of the destination; this lookup does not authorize teleportation. */
    Optional<Location> home(int teamId);
    /** Returns a defensive copy and password-presence flag, never the password itself. */
    Optional<WarpSnapshot> warp(int teamId, String name);
    List<String> warpNames(int teamId);

    /** Creates a player-owned team after normal name and membership validation and the pre-event. */
    TeamActionResult<TeamSnapshot> createTeam(UUID ownerId, String name) throws SQLException;
    TeamActionResult<TeamSnapshot> createOwnerlessTeam(String name) throws SQLException;
    /** Invites a player. Null actorId means a trusted plugin/system action without role checks. */
    TeamActionResult<Void> inviteMember(int teamId, UUID actorId, UUID targetId) throws SQLException;
    TeamActionResult<Void> revokeInvite(int teamId, UUID actorId, UUID targetId) throws SQLException;
    /** Joins with a live invitation and a cancellable pre-event. */
    TeamActionResult<Void> joinTeam(int teamId, UUID playerId) throws SQLException;
    TeamActionResult<Void> leaveTeam(UUID playerId) throws SQLException;
    TeamActionResult<Void> kickMember(int teamId, UUID actorId, UUID targetId) throws SQLException;
    TeamActionResult<Void> promoteMember(int teamId, UUID actorId, UUID targetId) throws SQLException;
    TeamActionResult<Void> demoteMember(int teamId, UUID actorId, UUID targetId) throws SQLException;
    TeamActionResult<Void> transferOwnership(int teamId, UUID actorId, UUID newOwnerId) throws SQLException;
    /** Adds a positive score amount without integer overflow; returns the new score. */
    TeamActionResult<Integer> awardScore(int teamId, int amount) throws SQLException;
    /** Grants team progression using configured rules; XP persistence may be delayed until autosave. */
    TeamActionResult<TeamXpResult> grantTeamXp(int teamId, long amount, String reason) throws SQLException;
    /** Adds to the FoTeams ledger only; no Vault money moves. */
    TeamActionResult<Double> creditBank(int teamId, double amount) throws SQLException;
    /** Removes from the FoTeams ledger only; no Vault money moves. */
    TeamActionResult<Double> debitBank(int teamId, double amount) throws SQLException;
    /** Moves player Vault money into the team bank, with refund attempt on save failure. */
    TeamActionResult<Double> depositFromPlayer(int teamId, Player player, double amount) throws SQLException;
    /** Moves team bank money into the player's Vault balance, with restoration attempt on payout failure. */
    TeamActionResult<Double> withdrawToPlayer(int teamId, Player player, double amount) throws SQLException;
}
