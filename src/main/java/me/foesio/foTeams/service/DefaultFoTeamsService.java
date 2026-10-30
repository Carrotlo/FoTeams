package me.foesio.foTeams.service;

import me.foesio.foTeams.FoTeams;
import me.foesio.foTeams.api.FoTeamsService;
import me.foesio.foTeams.api.TeamActionResult;
import me.foesio.foTeams.api.TeamXpResult;
import me.foesio.foTeams.api.TeamRelation;
import me.foesio.foTeams.api.TeamSnapshot;
import me.foesio.foTeams.api.WarpSnapshot;
import me.foesio.foTeams.model.Team;
import me.foesio.foTeams.model.TeamAction;
import me.foesio.foTeams.model.TeamRole;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.sql.SQLException;

/** Looks up the current TeamService on each call so FoTeams reloads do not stale this service. */
public final class DefaultFoTeamsService implements FoTeamsService {
    private final FoTeams plugin;

    public DefaultFoTeamsService(FoTeams plugin) { this.plugin = plugin; }

    private TeamService service() {
        if (!plugin.isEnabled() || plugin.getTeamService() == null) {
            throw new IllegalStateException("FoTeams is not available");
        }
        return plugin.getTeamService();
    }

    @Override public Optional<TeamSnapshot> teamOf(UUID playerId) {
        TeamService current = service();
        return playerId == null ? Optional.empty() : current.teamOf(playerId).map(TeamSnapshots::of);
    }

    @Override public Optional<TeamSnapshot> teamById(int teamId) {
        return service().byId(teamId).map(TeamSnapshots::of);
    }

    @Override public Optional<TeamSnapshot> teamByName(String name) {
        TeamService current = service();
        return name == null ? Optional.empty() : current.byName(name).map(TeamSnapshots::of);
    }

    @Override public List<String> teamNames() { return List.copyOf(service().teamNames()); }

    @Override public Optional<TeamRelation> relation(int sourceTeamId, int targetTeamId) {
        TeamService current = service();
        Team source = current.byId(sourceTeamId).orElse(null);
        Team target = current.byId(targetTeamId).orElse(null);
        return source == null || target == null ? Optional.empty()
                : Optional.of(TeamRelation.valueOf(current.relation(source, target).name()));
    }

    @Override public Optional<Location> home(int teamId) {
        return service().byId(teamId).map(Team::getHome).map(Location::clone);
    }

    @Override public Optional<WarpSnapshot> warp(int teamId, String name) {
        TeamService current = service();
        if (name == null) return Optional.empty();
        Team team = current.byId(teamId).orElse(null);
        if (team == null) return Optional.empty();
        String key = name.toLowerCase(Locale.ROOT);
        Location location = team.getWarps().get(key);
        return location == null ? Optional.empty()
                : Optional.of(new WarpSnapshot(key, location, current.warpRequiresPassword(team, key)));
    }

    @Override public List<String> warpNames(int teamId) {
        return service().byId(teamId).map(team -> team.getWarps().keySet().stream().sorted().toList()).orElseGet(List::of);
    }

    @Override public TeamActionResult<TeamSnapshot> createTeam(UUID ownerId, String name) throws SQLException {
        TeamService current = service();
        if (ownerId == null || name == null) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        try {
            return TeamActionResult.success(TeamSnapshots.of(current.createTeam(ownerId, name)));
        } catch (TeamOperationCancelledException failure) {
            return TeamActionResult.failure(TeamActionResult.Status.CANCELLED);
        } catch (IllegalArgumentException failure) {
            return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        } catch (IllegalStateException failure) {
            return TeamActionResult.failure(TeamActionResult.Status.DENIED);
        }
    }

    @Override public TeamActionResult<TeamSnapshot> createOwnerlessTeam(String name) throws SQLException {
        TeamService current = service();
        if (name == null) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        try {
            return TeamActionResult.success(TeamSnapshots.of(current.createOwnerlessTeam(name)));
        } catch (TeamOperationCancelledException failure) {
            return TeamActionResult.failure(TeamActionResult.Status.CANCELLED);
        } catch (IllegalArgumentException failure) {
            return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        }
    }

    private boolean permitted(Team team, UUID actorId, TeamAction action) {
        return actorId == null || (service().teamOf(actorId).map(Team::getId).orElse(-1) == team.getId()
                && service().can(team, actorId, action));
    }

    @Override public TeamActionResult<Void> inviteMember(int teamId, UUID actorId, UUID targetId) throws SQLException {
        TeamService current = service();
        if (targetId == null) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        Team team = current.byId(teamId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        if (!permitted(team, actorId, TeamAction.INVITE)) return TeamActionResult.failure(TeamActionResult.Status.DENIED);
        if (service().teamOf(targetId).isPresent()) return TeamActionResult.failure(TeamActionResult.Status.DENIED);
        if (service().hasInvite(targetId, teamId)) return TeamActionResult.failure(TeamActionResult.Status.NO_CHANGE);
        service().invite(team, actorId == null ? Team.NO_OWNER_UUID : actorId, targetId);
        return TeamActionResult.success(null);
    }

    @Override public TeamActionResult<Void> revokeInvite(int teamId, UUID actorId, UUID targetId) throws SQLException {
        TeamService current = service();
        if (targetId == null) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        Team team = current.byId(teamId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        TeamRole actorRole = actorId == null ? TeamRole.OWNER : team.roleOf(actorId);
        if (actorRole != TeamRole.OWNER && actorRole != TeamRole.ADMIN) {
            return TeamActionResult.failure(TeamActionResult.Status.DENIED);
        }
        if (!service().revokeInvite(team, targetId)) return TeamActionResult.failure(TeamActionResult.Status.NO_CHANGE);
        return TeamActionResult.success(null);
    }

    @Override public TeamActionResult<Void> joinTeam(int teamId, UUID playerId) throws SQLException {
        TeamService current = service();
        if (playerId == null) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        Team team = current.byId(teamId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        try {
            service().join(playerId, team);
            return TeamActionResult.success(null);
        } catch (TeamOperationCancelledException failure) {
            return TeamActionResult.failure(TeamActionResult.Status.CANCELLED);
        } catch (IllegalStateException failure) {
            return TeamActionResult.failure(switch (String.valueOf(failure.getMessage())) {
                case "full" -> TeamActionResult.Status.FULL;
                case "no_invite" -> TeamActionResult.Status.NOT_INVITED;
                default -> TeamActionResult.Status.DENIED;
            });
        }
    }

    @Override public TeamActionResult<Void> leaveTeam(UUID playerId) throws SQLException {
        TeamService current = service();
        if (playerId == null) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        Team team = current.teamOf(playerId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        if (team.roleOf(playerId) == TeamRole.OWNER) return TeamActionResult.failure(TeamActionResult.Status.DENIED);
        service().leave(playerId);
        return TeamActionResult.success(null);
    }

    @Override public TeamActionResult<Void> kickMember(int teamId, UUID actorId, UUID targetId) throws SQLException {
        Team team = service().byId(teamId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        if (!permitted(team, actorId, TeamAction.KICK)) return TeamActionResult.failure(TeamActionResult.Status.DENIED);
        if (targetId == null || team.roleOf(targetId) == null) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        if (team.roleOf(targetId) == TeamRole.OWNER) return TeamActionResult.failure(TeamActionResult.Status.DENIED);
        service().kick(team, targetId);
        return TeamActionResult.success(null);
    }

    @Override public TeamActionResult<Void> promoteMember(int teamId, UUID actorId, UUID targetId) throws SQLException {
        Team team = service().byId(teamId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        if (!permitted(team, actorId, TeamAction.PROMOTE)) return TeamActionResult.failure(TeamActionResult.Status.DENIED);
        if (targetId == null || team.roleOf(targetId) != TeamRole.MEMBER) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        if (!service().promote(team, targetId)) return TeamActionResult.failure(TeamActionResult.Status.FULL);
        return TeamActionResult.success(null);
    }

    @Override public TeamActionResult<Void> demoteMember(int teamId, UUID actorId, UUID targetId) throws SQLException {
        Team team = service().byId(teamId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        if (!permitted(team, actorId, TeamAction.DEMOTE)) return TeamActionResult.failure(TeamActionResult.Status.DENIED);
        if (targetId == null || team.roleOf(targetId) != TeamRole.ADMIN) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        service().demote(team, targetId);
        return TeamActionResult.success(null);
    }

    @Override public TeamActionResult<Void> transferOwnership(int teamId, UUID actorId, UUID newOwnerId) throws SQLException {
        Team team = service().byId(teamId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        if (!permitted(team, actorId, TeamAction.TRANSFER)) return TeamActionResult.failure(TeamActionResult.Status.DENIED);
        if (newOwnerId == null || team.roleOf(newOwnerId) == null) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        if (team.roleOf(newOwnerId) == TeamRole.OWNER) return TeamActionResult.failure(TeamActionResult.Status.NO_CHANGE);
        service().transfer(team, newOwnerId);
        return TeamActionResult.success(null);
    }

    @Override public TeamActionResult<Integer> awardScore(int teamId, int amount) throws SQLException {
        Team team = service().byId(teamId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        if (amount <= 0) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        try {
            return TeamActionResult.success(service().awardScore(team, amount));
        } catch (ArithmeticException failure) {
            return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        }
    }

    @Override public TeamActionResult<TeamXpResult> grantTeamXp(int teamId, long amount, String reason) throws SQLException {
        Team team = service().byId(teamId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        if (amount <= 0) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        if (!plugin.getTeamLevelService().isConfiguredEnabled()) {
            return TeamActionResult.failure(TeamActionResult.Status.UNAVAILABLE);
        }
        TeamService.TeamLevelResult result = plugin.getTeamLevelService().grantXp(team, amount, reason);
        return TeamActionResult.success(new TeamXpResult(result.gainedXp(), result.oldLevel(),
                result.newLevel(), result.currentXp(), result.requiredXp(), result.gainedLevels()));
    }

    private boolean validCredit(Team team, double amount) {
        return Double.isFinite(amount) && amount > 0D && Double.isFinite(team.getBalance() + amount);
    }

    @Override public TeamActionResult<Double> creditBank(int teamId, double amount) throws SQLException {
        Team team = service().byId(teamId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        if (!validCredit(team, amount)) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        service().deposit(team, amount);
        return TeamActionResult.success(team.getBalance());
    }

    @Override public TeamActionResult<Double> debitBank(int teamId, double amount) throws SQLException {
        Team team = service().byId(teamId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        if (!Double.isFinite(amount) || amount <= 0D) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        if (team.getBalance() < amount) return TeamActionResult.failure(TeamActionResult.Status.INSUFFICIENT_FUNDS);
        service().withdraw(team, amount);
        return TeamActionResult.success(team.getBalance());
    }

    @Override public TeamActionResult<Double> depositFromPlayer(int teamId, Player player, double amount) throws SQLException {
        Team team = service().byId(teamId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        if (player == null || !validCredit(team, amount)) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        if (!permitted(team, player.getUniqueId(), TeamAction.DEPOSIT)) return TeamActionResult.failure(TeamActionResult.Status.DENIED);
        if (!plugin.getEconomyService().isAvailable()) return TeamActionResult.failure(TeamActionResult.Status.UNAVAILABLE);
        try {
            if (plugin.getEconomyService().balance(player) < amount) return TeamActionResult.failure(TeamActionResult.Status.INSUFFICIENT_FUNDS);
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Could not check player balance for team deposit: " + failure.getMessage());
            return TeamActionResult.failure(TeamActionResult.Status.ECONOMY_FAILURE);
        }
        if (!safeEconomyWithdraw(player, amount)) return TeamActionResult.failure(TeamActionResult.Status.ECONOMY_FAILURE);
        try {
            service().deposit(team, amount);
            return TeamActionResult.success(team.getBalance());
        } catch (SQLException | RuntimeException failure) {
            if (safeEconomyDeposit(player, amount)) {
                return TeamActionResult.failure(TeamActionResult.Status.SAVE_FAILED_REFUNDED);
            }
            plugin.getLogger().severe("Failed to refund player after team bank save failed: " + player.getUniqueId());
            return TeamActionResult.failure(TeamActionResult.Status.COMPENSATION_FAILED);
        }
    }

    @Override public TeamActionResult<Double> withdrawToPlayer(int teamId, Player player, double amount) throws SQLException {
        Team team = service().byId(teamId).orElse(null);
        if (team == null) return TeamActionResult.failure(TeamActionResult.Status.NOT_FOUND);
        if (player == null || !Double.isFinite(amount) || amount <= 0D) return TeamActionResult.failure(TeamActionResult.Status.INVALID);
        if (!permitted(team, player.getUniqueId(), TeamAction.WITHDRAW)) return TeamActionResult.failure(TeamActionResult.Status.DENIED);
        if (!plugin.getEconomyService().isAvailable()) return TeamActionResult.failure(TeamActionResult.Status.UNAVAILABLE);
        if (team.getBalance() < amount) return TeamActionResult.failure(TeamActionResult.Status.INSUFFICIENT_FUNDS);
        service().withdraw(team, amount);
        if (safeEconomyDeposit(player, amount)) return TeamActionResult.success(team.getBalance());
        try {
            service().deposit(team, amount);
            return TeamActionResult.failure(TeamActionResult.Status.ECONOMY_FAILURE);
        } catch (SQLException | RuntimeException failure) {
            plugin.getLogger().severe("Failed to restore team bank after economy payout failed: " + teamId);
            return TeamActionResult.failure(TeamActionResult.Status.COMPENSATION_FAILED);
        }
    }

    private boolean safeEconomyWithdraw(Player player, double amount) {
        try {
            return plugin.getEconomyService().withdraw(player, amount);
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Economy withdrawal failed for " + player.getUniqueId() + ": " + failure.getMessage());
            return false;
        }
    }

    private boolean safeEconomyDeposit(Player player, double amount) {
        try {
            return plugin.getEconomyService().deposit(player, amount);
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Economy deposit failed for " + player.getUniqueId() + ": " + failure.getMessage());
            return false;
        }
    }
}
