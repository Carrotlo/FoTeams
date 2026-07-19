package me.foesio.foTeams.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Team {
    public static final UUID NO_OWNER_UUID = new UUID(0L, 0L);
    private final int id;
    private String name;
    private String tag;
    private String description;
    private String color;
    private UUID ownerId;
    private Location home;
    private int score;
    private double balance;
    private int teamLevel;
    private long teamXp;
    private boolean teamPvpProtectionEnabled;
    private int memberCap;
    private int echestRows;
    private final long createdAt;
    private final Set<UUID> admins = new HashSet<>();
    private final Set<UUID> members = new HashSet<>();
    private final Map<String, Location> warps = new HashMap<>();
    private final Map<String, String> warpPasswords = new HashMap<>();
    private final List<ItemStack> echestContents = new ArrayList<>();

    public Team(int id, String name, String tag, String description, String color, UUID ownerId, Location home, int score, double balance, int teamLevel, long teamXp, boolean teamPvpProtectionEnabled, int memberCap, int echestRows, long createdAt) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.description = description;
        this.color = color;
        this.ownerId = ownerId;
        this.home = home;
        this.score = score;
        this.balance = balance;
        this.teamLevel = teamLevel;
        this.teamXp = teamXp;
        this.teamPvpProtectionEnabled = teamPvpProtectionEnabled;
        this.memberCap = memberCap;
        this.echestRows = echestRows;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public Location getHome() {
        return home;
    }

    public void setHome(Location home) {
        this.home = home;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void addScore(int amount) {
        this.score += amount;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void addBalance(double amount) {
        this.balance += amount;
    }

    public int getTeamLevel() {
        return teamLevel;
    }

    public void setTeamLevel(int teamLevel) {
        this.teamLevel = teamLevel;
    }

    public long getTeamXp() {
        return teamXp;
    }

    public void setTeamXp(long teamXp) {
        this.teamXp = teamXp;
    }

    public void addTeamXp(long amount) {
        this.teamXp += amount;
    }

    public boolean isTeamPvpProtectionEnabled() {
        return teamPvpProtectionEnabled;
    }

    public void setTeamPvpProtectionEnabled(boolean teamPvpProtectionEnabled) {
        this.teamPvpProtectionEnabled = teamPvpProtectionEnabled;
    }

    public int getMemberCap() {
        return memberCap;
    }

    public void setMemberCap(int memberCap) {
        this.memberCap = memberCap;
    }

    public int getEchestRows() {
        return echestRows;
    }

    public void setEchestRows(int echestRows) {
        this.echestRows = echestRows;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Set<UUID> getAdmins() {
        return admins;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Map<String, Location> getWarps() {
        return warps;
    }

    public Map<String, String> getWarpPasswords() {
        return warpPasswords;
    }

    public List<ItemStack> getEchestContents() {
        return echestContents;
    }

    public TeamRole roleOf(UUID playerId) {
        if (hasOwner() && ownerId.equals(playerId)) {
            return TeamRole.OWNER;
        }
        if (admins.contains(playerId)) {
            return TeamRole.ADMIN;
        }
        if (members.contains(playerId)) {
            return TeamRole.MEMBER;
        }
        return null;
    }

    public boolean isMember(UUID playerId) {
        return roleOf(playerId) != null;
    }

    public boolean hasOwner() {
        return ownerId != null && !NO_OWNER_UUID.equals(ownerId);
    }

    public int getMemberCount() {
        return (hasOwner() ? 1 : 0) + admins.size() + members.size();
    }
}
