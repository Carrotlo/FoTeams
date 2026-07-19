package me.foesio.foTeams.service;

import me.foesio.foTeams.model.RelationType;
import me.foesio.foTeams.model.Team;
import me.foesio.foTeams.model.TeamAction;
import me.foesio.foTeams.model.TeamInvite;
import me.foesio.foTeams.model.TeamRole;
import me.foesio.foTeams.storage.TeamRepository;
import me.foesio.foTeams.util.TagColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TeamService {
    public static final int MAX_ADMINS = 7;
    private static final Pattern TEAM_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private final TeamRepository repository;
    private final RolePermissionService rolePermissions;
    private final Logger logger;
    private final int maxMembers;
    private final int defaultMemberCap;
    private final int maxWarps;
    private final int maxNameLength;
    private final int maxTagLength;
    private final int inviteExpirySeconds;
    private final boolean defaultTeamPvpEnabled;
    private final int maxEchestRows;
    private final int defaultEchestRows;
    private final double teamSizeUpgradeBaseCost;
    private final double teamSizeUpgradeMultiplier;
    private final double echestUpgradeBaseCost;
    private final double echestUpgradeMultiplier;
    private final Map<Integer, Team> teams = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> teamByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, List<TeamInvite>> invites = new HashMap<>();
    private final Map<Integer, Map<Integer, RelationType>> relations = new HashMap<>();
    private final Map<UUID, Long> lastKillWindow = new HashMap<>();
    private final Set<Integer> dirtyTeamLevelIds = ConcurrentHashMap.newKeySet();

    public record Settings(
            int maxMembers,
            int defaultMemberCap,
            int maxWarps,
            int maxNameLength,
            int maxTagLength,
            int inviteExpirySeconds,
            boolean defaultTeamPvpEnabled,
            int maxEchestRows,
            int defaultEchestRows,
            double teamSizeUpgradeBaseCost,
            double teamSizeUpgradeMultiplier,
            double echestUpgradeBaseCost,
            double echestUpgradeMultiplier) {
    }

    public TeamService(TeamRepository repository, RolePermissionService rolePermissions, Logger logger, Settings settings) throws SQLException {
        this.repository = repository;
        this.rolePermissions = rolePermissions;
        this.logger = Objects.requireNonNull(logger, "logger");
        Settings safeSettings = Objects.requireNonNull(settings, "settings");
        this.maxMembers = Math.max(1, safeSettings.maxMembers());
        this.defaultMemberCap = Math.max(1, Math.min(safeSettings.defaultMemberCap(), this.maxMembers));
        this.maxWarps = Math.max(0, safeSettings.maxWarps());
        this.maxNameLength = Math.max(1, safeSettings.maxNameLength());
        this.maxTagLength = Math.max(1, safeSettings.maxTagLength());
        this.inviteExpirySeconds = Math.max(1, safeSettings.inviteExpirySeconds());
        this.defaultTeamPvpEnabled = safeSettings.defaultTeamPvpEnabled();
        this.maxEchestRows = Math.max(1, Math.min(6, safeSettings.maxEchestRows()));
        this.defaultEchestRows = Math.max(1, Math.min(safeSettings.defaultEchestRows(), this.maxEchestRows));
        this.teamSizeUpgradeBaseCost = Double.isFinite(safeSettings.teamSizeUpgradeBaseCost()) ? Math.max(0.01D, safeSettings.teamSizeUpgradeBaseCost()) : 0.01D;
        this.teamSizeUpgradeMultiplier = Double.isFinite(safeSettings.teamSizeUpgradeMultiplier()) ? Math.max(1.0D, safeSettings.teamSizeUpgradeMultiplier()) : 1.0D;
        this.echestUpgradeBaseCost = Double.isFinite(safeSettings.echestUpgradeBaseCost()) ? Math.max(0.01D, safeSettings.echestUpgradeBaseCost()) : 0.01D;
        this.echestUpgradeMultiplier = Double.isFinite(safeSettings.echestUpgradeMultiplier()) ? Math.max(1.0D, safeSettings.echestUpgradeMultiplier()) : 1.0D;
        load();
    }

    private void load() throws SQLException {
        teams.clear();
        teamByPlayer.clear();
        invites.clear();
        relations.clear();
        teams.putAll(repository.loadTeams());
        invites.putAll(repository.loadInvites());
        relations.putAll(repository.loadRelations());
        for (Team team : teams.values()) {
            boolean changed = false;
            if (team.getTag() == null || team.getTag().isBlank()) {
                team.setTag(defaultTag(team.getName()));
                changed = true;
            }
            if (team.getDescription() == null || team.getDescription().isBlank()) {
                team.setDescription("A fresh team.");
                changed = true;
            }
            if (TagColorUtil.normalize(team.getColor()) == null) {
                team.setColor("#03fc88");
                changed = true;
            }
            int normalizedCap = clamp(team.getMemberCap() <= 0 ? defaultMemberCap : team.getMemberCap(), 1, maxMembers);
            if (normalizedCap != team.getMemberCap()) {
                team.setMemberCap(normalizedCap);
                changed = true;
            }
            int normalizedRows = clamp(team.getEchestRows() <= 0 ? defaultEchestRows : team.getEchestRows(), 1, maxEchestRows);
            if (normalizedRows != team.getEchestRows()) {
                team.setEchestRows(normalizedRows);
                changed = true;
            }
            if (!Double.isFinite(team.getBalance()) || team.getBalance() < 0) {
                team.setBalance(0);
                changed = true;
            }
            if (team.getScore() < 0) {
                team.setScore(0);
                changed = true;
            }
            if (team.getTeamLevel() < 1) {
                team.setTeamLevel(1);
                changed = true;
            }
            if (team.getTeamXp() < 0) {
                team.setTeamXp(0);
                changed = true;
            }
            if (changed) {
                repository.saveTeam(team);
            }
            if (team.hasOwner()) {
                teamByPlayer.put(team.getOwnerId(), team.getId());
            }
            team.getAdmins().forEach(uuid -> teamByPlayer.put(uuid, team.getId()));
            team.getMembers().forEach(uuid -> teamByPlayer.put(uuid, team.getId()));
        }
    }

    public void saveAll() throws SQLException {
        for (Team team : teams.values()) {
            repository.saveTeam(team);
        }
        repository.saveInvites(invites.values().stream().flatMap(Collection::stream).toList());
        repository.saveRelations(relations);
        dirtyTeamLevelIds.clear();
    }

    public void flushDirtyTeamLevels() throws SQLException {
        if (dirtyTeamLevelIds.isEmpty()) {
            return;
        }

        List<Team> dirtyTeams = new ArrayList<>();
        for (int teamId : dirtyTeamLevelIds) {
            Team team = teams.get(teamId);
            if (team != null) {
                dirtyTeams.add(team);
            }
        }

        if (!dirtyTeams.isEmpty()) {
            repository.saveTeamLevels(dirtyTeams);
        }

        for (Team team : dirtyTeams) {
            dirtyTeamLevelIds.remove(team.getId());
        }
        dirtyTeamLevelIds.removeIf(teamId -> !teams.containsKey(teamId));
    }

    public void saveTeamEchest(Team team) throws SQLException {
        repository.saveTeamEchest(team);
    }

    public void saveTeam(Team team) throws SQLException {
        repository.saveTeam(team);
        dirtyTeamLevelIds.remove(team.getId());
    }

    public Collection<Team> teams() {
        return teams.values();
    }

    public Optional<Team> teamOf(UUID playerId) {
        Integer id = teamByPlayer.get(playerId);
        return id == null ? Optional.empty() : Optional.ofNullable(teams.get(id));
    }

    public Optional<Team> byName(String name) {
        return teams.values().stream().filter(team -> team.getName().equalsIgnoreCase(name)).findFirst();
    }

    public Optional<Team> byId(int id) {
        return Optional.ofNullable(teams.get(id));
    }

    public Team createTeam(Player owner, String name) throws SQLException {
        Team team = repository.createTeam(name, owner.getUniqueId(), defaultTag(name), "A fresh team.", "#03fc88", defaultTeamPvpEnabled, defaultMemberCap, defaultEchestRows);
        teams.put(team.getId(), team);
        teamByPlayer.put(owner.getUniqueId(), team.getId());
        return team;
    }

    public Team createOwnerlessTeam(String name) throws SQLException {
        Team team = repository.createTeam(name, Team.NO_OWNER_UUID, defaultTag(name), "A fresh team.", "#03fc88", defaultTeamPvpEnabled, defaultMemberCap, defaultEchestRows);
        teams.put(team.getId(), team);
        return team;
    }

    public boolean isNameTaken(String name) {
        return byName(name).isPresent();
    }

    public boolean hasKnownPlayerName(String name) {
        String normalizedName = normalizePlayerLookup(name);
        if (normalizedName.isEmpty()) {
            return false;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return true;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (playerNameMatches(player.getName(), normalizedName)) {
                return true;
            }
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && playerNameMatches(offline.getName(), normalizedName)) {
                return true;
            }
        }
        return false;
    }

    public boolean can(Team team, UUID playerId, TeamAction action) {
        return rolePermissions.can(team.roleOf(playerId), action);
    }

    public TeamRole roleOf(Team team, UUID playerId) {
        return team.roleOf(playerId);
    }

    public boolean isFull(Team team) {
        return team.getMemberCount() >= team.getMemberCap();
    }

    public int maxMembers() {
        return maxMembers;
    }

    public int defaultMemberCap() {
        return defaultMemberCap;
    }

    public int maxWarps() {
        return maxWarps;
    }

    public int maxAdmins() {
        return MAX_ADMINS;
    }

    public int maxEchestRows() {
        return maxEchestRows;
    }

    public double nextMemberCapUpgradeCost(Team team) {
        if (team.getMemberCap() >= maxMembers) {
            return -1D;
        }
        int purchasedLevels = Math.max(0, team.getMemberCap() - defaultMemberCap);
        return teamSizeUpgradeBaseCost * Math.pow(teamSizeUpgradeMultiplier, purchasedLevels);
    }

    public double nextEchestRowsUpgradeCost(Team team) {
        if (team.getEchestRows() >= maxEchestRows) {
            return -1D;
        }
        int purchasedLevels = Math.max(0, team.getEchestRows() - defaultEchestRows);
        return echestUpgradeBaseCost * Math.pow(echestUpgradeMultiplier, purchasedLevels);
    }

    public boolean upgradeMemberCap(Team team) throws SQLException {
        if (team.getMemberCap() >= maxMembers) {
            return false;
        }
        int previousCap = team.getMemberCap();
        team.setMemberCap(team.getMemberCap() + 1);
        try {
            repository.saveTeam(team);
        } catch (SQLException | RuntimeException exception) {
            team.setMemberCap(previousCap);
            throw exception;
        }
        return true;
    }

    public boolean upgradeEchestRows(Team team) throws SQLException {
        if (team.getEchestRows() >= maxEchestRows) {
            return false;
        }
        int previousRows = team.getEchestRows();
        team.setEchestRows(team.getEchestRows() + 1);
        try {
            repository.saveTeam(team);
        } catch (SQLException | RuntimeException exception) {
            team.setEchestRows(previousRows);
            throw exception;
        }
        return true;
    }

    public int maxNameLength() {
        return maxNameLength;
    }

    public int maxTagLength() {
        return maxTagLength;
    }

    public boolean isValidTeamName(String name) {
        return isValidTeamNameLength(name) && isValidTeamNameCharacters(name);
    }

    public boolean isValidTeamNameLength(String name) {
        return name != null && !name.isBlank() && name.length() <= maxNameLength;
    }

    public boolean isValidTeamNameCharacters(String name) {
        return name != null && TEAM_NAME_PATTERN.matcher(name).matches();
    }

    public boolean isValidTag(String tag) {
        return tag != null && !tag.isBlank() && !tag.contains(" ") && tag.trim().length() <= maxTagLength;
    }

    public void invite(Team team, UUID inviterId, UUID targetId) throws SQLException {
        TeamInvite invite = new TeamInvite(team.getId(), targetId, inviterId, System.currentTimeMillis(), System.currentTimeMillis() + inviteExpirySeconds * 1000L);
        invites.computeIfAbsent(targetId, ignored -> new ArrayList<>()).removeIf(existing -> existing.teamId() == team.getId());
        invites.computeIfAbsent(targetId, ignored -> new ArrayList<>()).add(invite);
        repository.saveInvites(invites.values().stream().flatMap(Collection::stream).toList());
    }

    public List<TeamInvite> teamInvites(Team team) {
        pruneExpiredInvites();
        return invites.values().stream()
                .flatMap(Collection::stream)
                .filter(invite -> invite.teamId() == team.getId())
                .toList();
    }

    public List<TeamInvite> invites(UUID playerId) {
        pruneExpired(playerId);
        return invites.getOrDefault(playerId, Collections.emptyList());
    }

    public boolean revokeInvite(Team team, UUID playerId) throws SQLException {
        List<TeamInvite> existing = invites.get(playerId);
        if (existing == null) {
            return false;
        }
        boolean removed = existing.removeIf(invite -> invite.teamId() == team.getId());
        if (existing.isEmpty()) {
            invites.remove(playerId);
        }
        if (removed) {
            repository.saveInvites(invites.values().stream().flatMap(Collection::stream).toList());
        }
        return removed;
    }

    private void pruneExpired(UUID playerId) {
        List<TeamInvite> existing = invites.get(playerId);
        if (existing == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (existing.removeIf(invite -> invite.isExpired(now))) {
            try {
                repository.saveInvites(invites.values().stream().flatMap(Collection::stream).toList());
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Failed to save pruned expired team invites for player " + playerId + ".", exception);
            }
        }
    }

    private void pruneExpiredInvites() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (List<TeamInvite> existing : invites.values()) {
            changed |= existing.removeIf(invite -> invite.isExpired(now));
        }
        invites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (changed) {
            try {
                repository.saveInvites(invites.values().stream().flatMap(Collection::stream).toList());
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Failed to save pruned expired team invites.", exception);
            }
        }
    }

    public boolean hasInvite(UUID playerId, int teamId) {
        pruneExpired(playerId);
        return invites.getOrDefault(playerId, Collections.emptyList()).stream().anyMatch(invite -> invite.teamId() == teamId);
    }

    public void join(Player player, Team team) throws SQLException {
        if (isFull(team)) {
            throw new IllegalStateException("full");
        }
        team.getMembers().add(player.getUniqueId());
        teamByPlayer.put(player.getUniqueId(), team.getId());
        invites.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>()).removeIf(invite -> invite.teamId() == team.getId());
        repository.saveTeam(team);
        repository.saveInvites(invites.values().stream().flatMap(Collection::stream).toList());
    }

    public void leave(Player player) throws SQLException {
        Team team = teamOf(player.getUniqueId()).orElseThrow();
        TeamRole role = team.roleOf(player.getUniqueId());
        if (role == TeamRole.OWNER) {
            throw new IllegalStateException("owner_cannot_leave");
        }
        team.getAdmins().remove(player.getUniqueId());
        team.getMembers().remove(player.getUniqueId());
        teamByPlayer.remove(player.getUniqueId());
        repository.saveTeam(team);
    }

    public void disband(Team team) throws SQLException {
        Set<UUID> allMembers = allMembers(team);
        allMembers.forEach(teamByPlayer::remove);
        teams.remove(team.getId());
        relations.remove(team.getId());
        relations.values().forEach(map -> map.remove(team.getId()));
        invites.values().forEach(list -> list.removeIf(invite -> invite.teamId() == team.getId()));
        dirtyTeamLevelIds.remove(team.getId());
        repository.deleteTeam(team.getId());
        repository.saveInvites(invites.values().stream().flatMap(Collection::stream).toList());
        repository.saveRelations(relations);
    }

    public Set<UUID> allMembers(Team team) {
        Set<UUID> all = new HashSet<>();
        if (team.hasOwner()) {
            all.add(team.getOwnerId());
        }
        all.addAll(team.getAdmins());
        all.addAll(team.getMembers());
        return all;
    }

    public void kick(Team team, UUID targetId) throws SQLException {
        team.getAdmins().remove(targetId);
        team.getMembers().remove(targetId);
        teamByPlayer.remove(targetId);
        repository.saveTeam(team);
    }

    public boolean promote(Team team, UUID targetId) throws SQLException {
        if (Objects.equals(team.getOwnerId(), targetId)) {
            return false;
        }
        if (!team.getMembers().contains(targetId)) {
            return false;
        }
        if (team.getAdmins().size() >= MAX_ADMINS) {
            return false;
        }
        team.getMembers().remove(targetId);
        team.getAdmins().add(targetId);
        repository.saveTeam(team);
        return true;
    }

    public void demote(Team team, UUID targetId) throws SQLException {
        if (team.getAdmins().remove(targetId)) {
            team.getMembers().add(targetId);
        }
        repository.saveTeam(team);
    }

    public void transfer(Team team, UUID newOwner) throws SQLException {
        UUID previousOwner = team.getOwnerId();
        team.getAdmins().remove(newOwner);
        team.getMembers().remove(newOwner);
        if (team.hasOwner()) {
            if (team.getAdmins().size() < MAX_ADMINS) {
                team.getAdmins().add(previousOwner);
            } else {
                team.getMembers().add(previousOwner);
            }
        }
        team.setOwnerId(newOwner);
        repository.saveTeam(team);
    }

    public void updateName(Team team, String name) throws SQLException {
        team.setName(name);
        repository.saveTeam(team);
    }

    public void updateTag(Team team, String tag) throws SQLException {
        team.setTag(tag);
        repository.saveTeam(team);
    }

    public void updateDescription(Team team, String description) throws SQLException {
        team.setDescription(description);
        repository.saveTeam(team);
    }

    public boolean setTeamPvpProtection(Team team, boolean enabled) throws SQLException {
        if (team.isTeamPvpProtectionEnabled() == enabled) {
            return false;
        }
        team.setTeamPvpProtectionEnabled(enabled);
        repository.saveTeam(team);
        return true;
    }

    public boolean updateTagColor(Team team, String color) throws SQLException {
        String normalized = TagColorUtil.normalize(color);
        if (normalized == null) {
            return false;
        }
        team.setColor(normalized);
        repository.saveTeam(team);
        return true;
    }

    public void setHome(Team team, Location home) throws SQLException {
        team.setHome(home);
        repository.saveTeam(team);
    }

    public void deleteHome(Team team) throws SQLException {
        team.setHome(null);
        repository.saveTeam(team);
    }

    public boolean canAddWarp(Team team, String name) {
        return team.getWarps().containsKey(name.toLowerCase(Locale.ROOT)) || team.getWarps().size() < maxWarps;
    }

    public void setWarp(Team team, String name, Location location, String password) throws SQLException {
        team.getWarps().put(name.toLowerCase(Locale.ROOT), location);
        team.getWarpPasswords().put(name.toLowerCase(Locale.ROOT), password == null || password.isBlank() ? null : password);
        repository.saveTeam(team);
    }

    public void deleteWarp(Team team, String name) throws SQLException {
        team.getWarps().remove(name.toLowerCase(Locale.ROOT));
        team.getWarpPasswords().remove(name.toLowerCase(Locale.ROOT));
        repository.saveTeam(team);
    }

    public boolean warpRequiresPassword(Team team, String name) {
        String password = team.getWarpPasswords().get(name.toLowerCase(Locale.ROOT));
        return password != null && !password.isBlank();
    }

    public boolean matchesWarpPassword(Team team, String name, String password) {
        String expected = team.getWarpPasswords().get(name.toLowerCase(Locale.ROOT));
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equals(password);
    }

    public boolean requestOrAcceptAlly(Team team, Team other) throws SQLException {
        if (hasIncomingAllyRequest(team, other)) {
            relations.computeIfAbsent(team.getId(), ignored -> new HashMap<>()).put(other.getId(), RelationType.ALLY);
            relations.computeIfAbsent(other.getId(), ignored -> new HashMap<>()).put(team.getId(), RelationType.ALLY);
            repository.saveRelations(relations);
            return true;
        }
        relations.computeIfAbsent(team.getId(), ignored -> new HashMap<>()).put(other.getId(), RelationType.ALLY_REQUEST);
        relations.computeIfAbsent(other.getId(), ignored -> new HashMap<>()).remove(team.getId());
        repository.saveRelations(relations);
        return false;
    }

    public void setEnemy(Team team, Team other) throws SQLException {
        relations.computeIfAbsent(team.getId(), ignored -> new HashMap<>()).put(other.getId(), RelationType.ENEMY);
        relations.computeIfAbsent(other.getId(), ignored -> new HashMap<>()).put(team.getId(), RelationType.ENEMY);
        repository.saveRelations(relations);
    }

    public void clearRelation(Team team, Team other) throws SQLException {
        relations.computeIfAbsent(team.getId(), ignored -> new HashMap<>()).remove(other.getId());
        relations.computeIfAbsent(other.getId(), ignored -> new HashMap<>()).remove(team.getId());
        repository.saveRelations(relations);
    }

    public RelationType relation(Team team, Team other) {
        return relations.getOrDefault(team.getId(), Map.of()).getOrDefault(other.getId(), RelationType.NEUTRAL);
    }

    public boolean hasIncomingAllyRequest(Team team, Team other) {
        return relations.getOrDefault(other.getId(), Map.of()).getOrDefault(team.getId(), RelationType.NEUTRAL) == RelationType.ALLY_REQUEST;
    }

    public List<Team> alliesOf(Team team) {
        Map<Integer, RelationType> map = relations.getOrDefault(team.getId(), Map.of());
        return map.entrySet().stream()
                .filter(entry -> entry.getValue() == RelationType.ALLY)
                .map(entry -> teams.get(entry.getKey()))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Team> sortedByScore() {
        return teams.values().stream().sorted(Comparator.comparingInt(Team::getScore).reversed()).toList();
    }

    public List<Team> sortedByBalance() {
        return teams.values().stream().sorted(Comparator.comparingDouble(Team::getBalance).reversed()).toList();
    }

    public List<Team> sortedByLevel() {
        return teams.values().stream()
                .sorted(Comparator.comparingInt(Team::getTeamLevel).reversed()
                        .thenComparing(Comparator.comparingLong(Team::getTeamXp).reversed())
                        .thenComparing(Team::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public int scoreRank(Team team) {
        return indexOf(sortedByScore(), team);
    }

    public int balanceRank(Team team) {
        return indexOf(sortedByBalance(), team);
    }

    public int levelRank(Team team) {
        return indexOf(sortedByLevel(), team);
    }

    private int indexOf(List<Team> teams, Team target) {
        for (int index = 0; index < teams.size(); index++) {
            if (teams.get(index).getId() == target.getId()) {
                return index + 1;
            }
        }
        return -1;
    }

    public void deposit(Team team, double amount) throws SQLException {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("amount must be positive and finite");
        }
        double previousBalance = team.getBalance();
        team.addBalance(amount);
        try {
            repository.saveTeam(team);
        } catch (SQLException | RuntimeException exception) {
            team.setBalance(previousBalance);
            throw exception;
        }
    }

    public void withdraw(Team team, double amount) throws SQLException {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("amount must be positive and finite");
        }
        double previousBalance = team.getBalance();
        team.addBalance(-amount);
        try {
            repository.saveTeam(team);
        } catch (SQLException | RuntimeException exception) {
            team.setBalance(previousBalance);
            throw exception;
        }
    }

    public void setBalance(Team team, double amount) throws SQLException {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative and finite");
        }
        double previousBalance = team.getBalance();
        team.setBalance(amount);
        try {
            repository.saveTeam(team);
        } catch (SQLException | RuntimeException exception) {
            team.setBalance(previousBalance);
            throw exception;
        }
    }

    public void setScore(Team team, int amount) throws SQLException {
        int previousScore = team.getScore();
        team.setScore(amount);
        try {
            repository.saveTeam(team);
        } catch (SQLException | RuntimeException exception) {
            team.setScore(previousScore);
            throw exception;
        }
    }

    public TeamLevelResult addTeamLevelXp(Team team, long amount, double baseRequiredXp, double multiplier, int maxLevel) throws SQLException {
        if (amount <= 0L) {
            return new TeamLevelResult(0L, team.getTeamLevel(), team.getTeamLevel(), team.getTeamXp(), requiredTeamLevelXp(team.getTeamLevel(), baseRequiredXp, multiplier), List.of());
        }
        int storedLevel = team.getTeamLevel();
        long storedXp = team.getTeamXp();
        int oldLevel = Math.max(1, team.getTeamLevel());
        team.setTeamLevel(oldLevel);
        if (team.getTeamXp() < 0L) {
            team.setTeamXp(0L);
        }
        long currentXp = team.getTeamXp();
        if (Long.MAX_VALUE - currentXp < amount) {
            team.setTeamXp(Long.MAX_VALUE);
        } else {
            team.addTeamXp(amount);
        }
        List<Integer> gainedLevels = normalizeTeamLevel(team, baseRequiredXp, multiplier, maxLevel);
        if (team.getTeamLevel() != storedLevel || team.getTeamXp() != storedXp) {
            markTeamLevelDirty(team);
        }
        return new TeamLevelResult(amount, oldLevel, team.getTeamLevel(), team.getTeamXp(), requiredTeamLevelXp(team.getTeamLevel(), baseRequiredXp, multiplier), gainedLevels);
    }

    public long requiredTeamLevelXp(int level, double baseRequiredXp, double multiplier) {
        int safeLevel = Math.max(1, level);
        double required = Math.max(1.0D, baseRequiredXp) * Math.pow(Math.max(1.01D, multiplier), safeLevel - 1);
        if (Double.isNaN(required) || Double.isInfinite(required) || required > Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, Math.round(required));
    }

    private List<Integer> normalizeTeamLevel(Team team, double baseRequiredXp, double multiplier, int maxLevel) {
        List<Integer> gained = new ArrayList<>();
        while (true) {
            long required = requiredTeamLevelXp(team.getTeamLevel(), baseRequiredXp, multiplier);
            if (team.getTeamXp() < required) {
                break;
            }
            if (maxLevel > 0 && team.getTeamLevel() >= maxLevel) {
                team.setTeamXp(Math.min(team.getTeamXp(), required - 1L));
                break;
            }
            team.setTeamXp(team.getTeamXp() - required);
            team.setTeamLevel(team.getTeamLevel() + 1);
            gained.add(team.getTeamLevel());
        }
        return gained;
    }

    private void markTeamLevelDirty(Team team) {
        dirtyTeamLevelIds.add(team.getId());
    }

    public void addKill(Player killer, Player victim, boolean countFriendly, boolean countAllies, int antiFarmWindowSeconds) throws SQLException {
        Optional<Team> killerTeamOptional = teamOf(killer.getUniqueId());
        Optional<Team> victimTeamOptional = teamOf(victim.getUniqueId());
        if (killerTeamOptional.isEmpty()) {
            return;
        }
        Team killerTeam = killerTeamOptional.get();
        Team victimTeam = victimTeamOptional.orElse(null);
        if (victimTeam != null && killerTeam.getId() == victimTeam.getId() && !countFriendly) {
            return;
        }
        if (victimTeam != null && relation(killerTeam, victimTeam) == RelationType.ALLY && !countAllies) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastKillWindow.get(killer.getUniqueId());
        if (previous != null && now - previous < antiFarmWindowSeconds * 1000L) {
            return;
        }
        lastKillWindow.put(killer.getUniqueId(), now);
        killerTeam.addScore(1);
        repository.saveTeam(killerTeam);
    }

    public String defaultTag(String name) {
        String sanitized = name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (sanitized.isBlank()) {
            sanitized = "TEAM";
        }
        return sanitized.substring(0, Math.min(maxTagLength, Math.max(1, sanitized.length())));
    }

    public List<OfflinePlayer> playersInDisplayOrder(Team team) {
        List<OfflinePlayer> list = new ArrayList<>();
        if (team.hasOwner()) {
            list.add(Bukkit.getOfflinePlayer(team.getOwnerId()));
        }
        list.addAll(team.getAdmins().stream().map(Bukkit::getOfflinePlayer).toList());
        list.addAll(team.getMembers().stream().map(Bukkit::getOfflinePlayer).toList());
        return list;
    }

    public List<String> teamNames() {
        return teams.values().stream().map(Team::getName).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
    }

    public Optional<Team> teamOfPlayerName(String name) {
        String normalizedName = normalizePlayerLookup(name);
        if (normalizedName.isEmpty()) {
            return Optional.empty();
        }
        Optional<Team> exact = teamOfPlayerName(normalizedName, true);
        if (exact.isPresent()) {
            return exact;
        }
        return teamOfPlayerName(normalizedName, false);
    }

    private Optional<Team> teamOfPlayerName(String normalizedName, boolean exactOnly) {
        for (Team team : teams.values()) {
            for (UUID memberId : allMembers(team)) {
                if (playerNameMatches(playerName(memberId), normalizedName, exactOnly)) {
                    return Optional.of(team);
                }
            }
        }
        return Optional.empty();
    }

    private boolean playerNameMatches(String name, String normalizedInput) {
        return playerNameMatches(name, normalizedInput, false);
    }

    private boolean playerNameMatches(String name, String normalizedInput, boolean exactOnly) {
        String normalizedName = normalizePlayerLookup(name);
        if (normalizedName.equals(normalizedInput)) {
            return true;
        }
        return !exactOnly && withoutBedrockPrefix(normalizedName).equals(withoutBedrockPrefix(normalizedInput));
    }

    private String normalizePlayerLookup(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    private String withoutBedrockPrefix(String input) {
        return input.startsWith(".") ? input.substring(1) : input;
    }

    private String playerName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        return offline.getName() == null ? playerId.toString().substring(0, 8) : offline.getName();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record TeamLevelResult(long gainedXp, int oldLevel, int newLevel, long currentXp, long requiredXp, List<Integer> gainedLevels) {
    }
}
