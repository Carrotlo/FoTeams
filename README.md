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

This plugin does not expose a public developer API.

</details>
