# FoTeams

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/foteams) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/foteams/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

GUI-first teams with roles, warps, storage, upgrades, and progression.

*Developed by Carrotio.*

## Features

- Provides GUI-first team creation and management with persistent storage.
- Supports roles, invitations, promotions, team settings, and member limits.
- Includes homes, warps, shared storage, team chat, allies, and PvP controls.
- Supports Vault banks and upgrades plus optional FoLevels team progression.
- Provides team leaderboards and a broad PlaceholderAPI expansion.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/foteams). Check the [versions page](https://modrinth.com/plugin/foteams/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** `PlaceholderAPI`, `Vault`, `FoLevels`

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/foteams <help|create|invite|invites|revoke|join|leave|disband|kick|promote|demote|pvp|ally|unally|chat|allychat|home|sethome|warp|setwarp|warps|info|settings|upgrades|echest|top|level|bal|baltop|deposit|withdraw>` — Main FoTeams command Aliases: `/team`, `/teams`.
- `/foteamsadmin <help|reload|version|editor|chatspy>` — FoTeams admin command Aliases: `/teamadmin`, `/teamsadmin`.

### Permissions

- `foteams.use` — Access to FoTeams player commands Default: `true`.
- `foteams.admin` — Access to FoTeams admin commands Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

Requires PlaceholderAPI.

- `%foteams_team_name%` — Team name.
- `%foteams_team_tag%` — Team tag.
- `%foteams_team_color% / %foteams_team_colour%` — Normalised team colour.
- `%foteams_team_description%` — Team description.
- `%foteams_team_score% / %foteams_team_score_rank%` — Team score or score rank.
- `%foteams_team_balance% / %foteams_team_balance_rank%` — Formatted team balance or balance rank.
- `%foteams_team_level%` — Team level.
- `%foteams_team_level_xp%` — Current team XP.
- `%foteams_team_level_required_xp%` — XP required for the next level.
- `%foteams_team_level_remaining_xp%` — XP remaining for the next level.
- `%foteams_team_level_progress_percent%` — Level progress percentage.
- `%foteams_team_level_rank%` — Team level rank.
- `%foteams_team_role%` — Player's role in the team.
- `%foteams_team_member_count% / %foteams_team_member_cap%` — Current members or member cap.
- `%foteams_team_owner%` — Team owner's name.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `permissions.yml` — Team role permission defaults.
- `sounds.yml` — Configurable interaction sounds.
- `swear-words.yml` — Blocked words used by team-name validation.
- `dialogs/*.yml` — Native dialog layouts and text.
- `guis/*.yml` — GUI layouts, slots, labels, and lore.

Main config areas: `max-members-per-team`, `default-members-per-team`, `max-warps-per-team`, `max-team-name-length`, `max-team-tag-length`, `invite-expiry-seconds`, `teleport-delay-seconds`, `max-team-echest-rows`, `default-team-echest-rows`, `team-pvp-default-enabled`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

FoTeams provides `me.foesio.foTeams.api.FoTeamsService` through Bukkit's `ServicesManager`. The API lives in the FoTeams jar, outside its relocated FoPluginCore classes. Compile against the **same FoTeams jar installed on the server** with `provided` scope; do not shade FoTeams into a consumer.

FoTeams is not published to a Maven repository by this project. If needed, install the built jar into your local Maven repository, using the version in this project's `pom.xml`:

```sh
mvn install:install-file -Dfile=/path/to/FoTeams-2.6.jar -DgroupId=me.foesio -DartifactId=FoTeams -Dversion=2.6 -Dpackaging=jar
```

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoTeams</artifactId>
  <version>2.6</version>
  <scope>provided</scope>
</dependency>
```

For `plugin.yml`, use `depend: [FoTeams]` when FoTeams is required or `softdepend: [FoTeams]` when optional. Keep direct API references in an integration class loaded only after checking that FoTeams is installed and enabled. Reacquire the service after a provider disable/re-enable; a cached service throws `IllegalStateException` while FoTeams is disabled. FoTeams reloads its internal team manager, but the registered API object looks up the current manager on every call.

```java
Plugin provider = Bukkit.getPluginManager().getPlugin("FoTeams");
if (provider != null && provider.isEnabled()) {
    RegisteredServiceProvider<FoTeamsService> registration =
            Bukkit.getServicesManager().getRegistration(FoTeamsService.class);
    if (registration != null) {
        FoTeamsService teams = registration.getProvider();
        teams.teamOf(player.getUniqueId()).ifPresent(team ->
                plugin.getLogger().info("Team " + team.id() + ": " + team.name()));
    }
}
```

The service has `teamOf`, `teamById`, `teamByName`, and `teamNames` lookups. `TeamSnapshot` contains immutable identity, role, score, bank, and level values at lookup time. `relation(sourceId, targetId)` is directional and returns empty if either team is absent. `home` and `warp` return cloned `Location` values; `warp` also reports whether a password exists, never the password. Reading a location does not authorize teleporting a player or bypassing FoTeams' role/password checks.

Actions return `TeamActionResult` with a status and, where relevant, a value. Denied, invalid, unchanged, full, missing invite, and cancelled requests return statuses and fire no post event. SQLite errors throw `SQLException`. `createTeam` takes an owner UUID; `createOwnerlessTeam` is for administrative integrations. `inviteMember`, `revokeInvite`, `kickMember`, `promoteMember`, `demoteMember`, and `transferOwnership` take an actor UUID and apply FoTeams' matching role rule. A null actor is an explicit trusted/system action and bypasses role checks. `joinTeam` requires a live invitation; `leaveTeam` does not let owners leave. These methods do not send player messages.

`awardScore` adds a positive amount and rejects integer overflow. `grantTeamXp` requires `team-level.enabled`, runs the same level formula and level rewards as FoLevels gains, and returns `TeamXpResult`. Its XP value is the **requested** amount; max-level caps can retain less. Team XP and levels are first updated in memory and persisted on FoTeams' configured autosave cycle or disable/reload. `creditBank` and `debitBank` adjust only the FoTeams bank ledger. `depositFromPlayer` and `withdrawToPlayer` additionally move funds through Vault and enforce the player's team role permission. They return explicit economy and compensation statuses when a transfer cannot finish. Do not use a ledger-only method when a Vault transfer is required.

All service calls and event callbacks must run in the appropriate server/player execution context. The service is **not async-safe**; do not call it directly from an async chat or database worker. Events are dispatched on the thread making the change, with Bukkit's async flag set from the actual dispatch thread. On regionized servers, consumers must schedule world/player work for the relevant entity or region.

| Event | Timing and meaning |
| --- | --- |
| `TeamPreCreateEvent` | Cancellable after name/owner validation and before insertion. Null owner means an ownerless team. |
| `TeamCreatedEvent` | After the new team is stored and indexed. Creation's initial owner is in its snapshot; no separate member event fires. |
| `TeamPreJoinEvent` | Cancellable after invitation and capacity checks, before membership changes. |
| `TeamMemberChangedEvent` | After a join, leave, kick, role change, or transfer is saved. Transfer can fire once for each affected player. Disband uses its own event. |
| `TeamRenamedEvent` | After a changed name is saved; includes the old and new name. |
| `TeamRelationChangedEvent` | After a changed directional relation is saved. A symmetric alliance/enemy change can fire twice. Disband fires neutralization events for removed directions. |
| `TeamDisbandedEvent` | After deletion; contains the former immutable team snapshot. |
| `TeamChatMessageEvent` | Cancellable after FoTeams' word filter and before team/ally delivery. Includes sender UUID, team ID, channel, and original routed message. Cancellation suppresses all delivery and chat-spy output. |

Events do not fire for startup loading, reload reconstruction, blocked requests, or unchanged values. Bukkit listeners can register with ordinary `@EventHandler` methods; import event classes from `me.foesio.foTeams.api.event`.

### Complete public API method index

Signatures use short type names; import Fo types from this plugin's `api` package and Bukkit types from Bukkit.

#### `FoTeamsService`

```java
Optional<TeamSnapshot> teamOf(UUID);
Optional<TeamSnapshot> teamById(int);
Optional<TeamSnapshot> teamByName(String);
List<String> teamNames();
Optional<TeamRelation> relation(int, int);
Optional<Location> home(int);
Optional<WarpSnapshot> warp(int, String);
List<String> warpNames(int);
TeamActionResult<TeamSnapshot> createTeam(UUID, String) throws SQLException;
TeamActionResult<TeamSnapshot> createOwnerlessTeam(String) throws SQLException;
TeamActionResult<Void> inviteMember(int, UUID, UUID) throws SQLException;
TeamActionResult<Void> revokeInvite(int, UUID, UUID) throws SQLException;
TeamActionResult<Void> joinTeam(int, UUID) throws SQLException;
TeamActionResult<Void> leaveTeam(UUID) throws SQLException;
TeamActionResult<Void> kickMember(int, UUID, UUID) throws SQLException;
TeamActionResult<Void> promoteMember(int, UUID, UUID) throws SQLException;
TeamActionResult<Void> demoteMember(int, UUID, UUID) throws SQLException;
TeamActionResult<Void> transferOwnership(int, UUID, UUID) throws SQLException;
TeamActionResult<Integer> awardScore(int, int) throws SQLException;
TeamActionResult<TeamXpResult> grantTeamXp(int, long, String) throws SQLException;
TeamActionResult<Double> creditBank(int, double) throws SQLException;
TeamActionResult<Double> debitBank(int, double) throws SQLException;
TeamActionResult<Double> depositFromPlayer(int, Player, double) throws SQLException;
TeamActionResult<Double> withdrawToPlayer(int, Player, double) throws SQLException;
```

**Other public contract types:** `TeamActionResult`, `TeamRelation`, `TeamRole`, `TeamSnapshot`, `TeamXpResult`, `WarpSnapshot`.

### Complete Bukkit event index

- `TeamChatMessageEvent`.
- `TeamCreatedEvent`.
- `TeamDisbandedEvent`.
- `TeamEvent` — abstract base for related events.
- `TeamMemberChangedEvent`.
- `TeamPreCreateEvent`.
- `TeamPreJoinEvent`.
- `TeamRelationChangedEvent`.
- `TeamRenamedEvent`.

</details>
