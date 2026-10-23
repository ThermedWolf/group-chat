# Changelog

All notable changes to GroupChat will be documented in this file.

This project follows [Semantic Versioning](https://semver.org/). The initial public release (1.0) was published to Modrinth before this repository existed. The commits below constitute everything that shipped after that baseline.

---

## [Unreleased] - 2.1.0

### Added

- **Server-wide announcements** (`/announce [highlight] <message>`):
  - Delivered live to every online player; queued in the mailbox of every known offline player (via `/unread` on next join).
  - `highlight` argument adds on-screen emphasis on top of the chat line: subtitle-style title + level-up sound on Paper, action-bar overlay on Fabric.
  - Admin-only: `groupchat.admin` (Paper, default op) / gamemaster permission level (Fabric). Console can send (scheduler-friendly). Feedback summarises live/queued/full-mailbox counts.
  - Recipient set: every player who has joined since install, tracked in new `known-players.json` (`KnownPlayerStore`). Chosen over per-platform offline-player enumeration (Paper's `getOfflinePlayers()` has no Fabric equivalent) so behaviour is identical on both platforms.
- **Automatic join messages** (`/joinmsg add|list|remove|clear|cooldown|preview`):
  - Admin-configured messages shown to each player on join (max 10 × 300 chars), stored in `joinmessages.json` (`JoinMessageStore`).
  - Per-player cooldown in minutes counts from the last actual show: `cooldown 30` → "joins 3× within 30 minutes, sees the message once". `0` = every join. "Last shown" state persisted (30-day retention).
  - `preview` renders them as a joining player would see them, ignoring the cooldown.
  - Same permission model as `/announce`; console can manage everything except preview.
- **Message styling** (`MarkdownFormat`, core): Markdown/MiniMessage-style subset → legacy `§` codes, identical on both platforms. `**bold**`, `*italic*`, `__underline__`, `~~strike~~`, `||spoiler||`, `` `code` ``, all 16 colour tags (`<red>…</red>`, …), style tags (`<bold>`, `<italic>`, `<underlined>`, `<strikethrough>`, `<obfuscated>`, `<reset>` + short forms) with proper nesting (close re-applies still-open codes). Raw `§` in input stripped first; legacy `&`-codes keep working. Applies to announcements + join messages.
- **Platform bridge additions** (`PlatformBridge`): `getOnlinePlayerUuids()` and `sendHighlight(UUID, String)` (Paper: Adventure `Title` + `Sound.ENTITY_PLAYER_LEVELUP`; Fabric: `sendOverlayMessage`).
- **Mailbox message types** (`PendingMessage`): new `type` field (`group`/`dm`/`announcement`) with legacy-JSON fallback based on `context`; `/unread` renders announcement entries with the `[Announce]` tag.

### Fixed

- 26.3 API compatibility caught by compiling against the real jars:
  - Fabric: `CommandSourceStack.hasPermission(int)` no longer exists → level-based `permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS))` (equal to old level 2).
  - Fabric: `Player.displayClientMessage(Component, boolean)` was renamed → `sendOverlayMessage(Component)`.
  - Paper: switched deprecated `Player#sendTitle(String,String,int,int,int)` to Adventure `Title.title(...)` with `LegacyComponentSerializer` so `§` styling renders in titles.
- DRY: duplicated mailbox-line rendering in `viewAndClearUnread`/`viewUnreadPage` extracted to `formatMailboxLine`.

### Technical notes

- New core files: `MarkdownFormat`, `KnownPlayerStore` (`known-players.json`), `JoinMessageStore` (`joinmessages.json`). New Paper files: `AnnounceCommand`, `JoinMsgCommand`. `saveAll()` includes the two new stores; both platforms deliver join messages via their existing join hooks (`JoinListener` / `ServerPlayConnectionEvents.JOIN`).
- Verified: `:core:compileJava` (Gradle, JDK 27), full `fabric` + `paper` sources compiled with `javac` against the cached `minecraft-merged-26.3` jar + fabric-api 0.163.0 and against `paper-api 26.3.build.8-alpha` respectively; 15-case `MarkdownFormat` parser test suite passes.

---

## [2.0.0] - 2026-09-17

Second public release — same Paper + Fabric dual-target, now hardened, with group history and toggle-chat.

> **Minecraft target:** 26.3 · **JDK:** 26 · **Fabric Loom:** 1.18.2 / Loader 0.19.5 / Fabric API 0.160.6+26.3 · **Paper API:** 26.3.build.8-alpha

### Added

- **Per-group message history (last 5)** — persisted in `history.json` per player per group (FIFO, oldest → newest with timestamps). View with flexible syntax (`b783312`, `24b397c`):
  - `/group history` / `/group <group> history [1-5]` / `/group history <group> [1-5]`
  - `/gmsg history [group] [1-5]` / `/gmsg <group> history [1-5]` (and `gm`/`gmoffline` aliases)
  - `/gm history`, `/gmoffline history` — same
  - Shorthand: if you are in exactly 1 group the group argument is optional (`inferSingleGroup`/`resolveGroupName`). `getGroupHistory` API added to `GroupChatService`; history is also written for every `sendGroupMessage*` call for **all** members (online and offline).
  - New core types: `GroupHistoryStore`, `GroupHistoryEntry`, `ToggleSession` (`core/src/main/java/.../core/`).
- **Toggle chat mode** — stay in a group's chat without prefixing every line (`24b397c`, `44a75a4`):
  - `/group toggle` / `/group <group> toggle`
  - `/gmsg toggle [group]` / `/gmsg <group> toggle` (and `gm`/`gmoffline` variants)
  - Once enabled, every normal chat line is routed to the toggled group until `/group toggle` again, `cancel` in chat, or disconnect/quit (cleared via `QuitListener` on Paper and `DISCONNECT` on Fabric, `clearToggleOnDisconnect`). `ToggleSession` is in-memory only.
  - Unified chat intercept (`ChatComposeListener` on Paper, `ServerMessageEvents.ALLOW_CHAT_MESSAGE` on Fabric) now handles both compose-mode and toggle-mode.
- **Interactive selection when in multiple groups** (`44a75a4`, `920956d`):
  - Running `/group toggle` or `/group history` (and `/gmsg toggle|history` etc.) without a group while in 2+ groups no longer fails — it enters a pending chat-selection: `You are part of multiple groups, type the name of one of the groups in chat to select it: <groups>`. Type the group name or `cancel`. Pending state is per-player, cleared on cancel/disconnect/explicit group choice. History variant preserves the requested count (e.g. `/group history 3` → pending → shows 3).
  - Re-running `/group toggle <group>` while already pending toggles directly.
- **Improved startup logging**:
  - Fabric (`b7ac626`): logs absolute data folder on start (`GroupChat data folder: <abs>`) and `Loaded <n> groups from <abs>/groups.json`; broadcasts data folder.
  - Paper (`e2c9d5e`): mirrors Fabric — `onEnable` now logs same via plugin logger (visible regardless of JUL level). Core adds `GroupManager.getGroupsFile()` and `GroupChatService.getTotalGroupCount()`/`getGroupsFile()`.

### Fixed

- **Fabric persistence after restart** (`b7ac626`): `server.getServerDirectory()/groupchat` resolved to a relative/ephemeral CWD on some launchers, so saves appeared lost after stop/rejoin. Switched canonical location to `FabricLoader.getInstance().getGameDir()/groupchat`, with migration from legacy `/world/config` paths. Logs absolute paths on load/save. Paper was unaffected (`plugins/GroupChat` was already absolute). (`GroupManager`, `GroupChatMod`)
- **Paper parity logging** (`e2c9d5e`): `GroupChatPlugin` now reports data folder + loaded count like Fabric.

### Security & Hardening

Full audit from `4e4b774` — 10 findings fixed:

- **Atomic persistence & corrupt-data resilience** (`GroupManager`, `MessageStore`): write via `tmp` + `ATOMIC_MOVE`, backup corrupt JSON (`.corrupt.<ts>`), validate entries on load (name regex `^[A-Za-z0-9_]{2,24}$`, case-insensitive, `owner in members`, entry validation), use `Logger` instead of `printStackTrace`.
- **Mailbox DoS protection** (`MessageStore`, `ChatFormat`): hard caps **500 chars** per message / **200 messages** per player (newest beyond cap rejected), strip `§` formatting codes, prune expired (>30 days) and oversized entries on load.
- **`/unread` pagination** (`paper/UnreadCommand`, `fabric/GroupChatCommands`): `/unread [page|clear]` — paginated view + explicit `clear` (Paper + Fabric).
- **Access control** (`GroupCommand`, `GroupChatCommands`, `PaperPlatformBridge`): `/group members` now requires membership, `invite` deduped (`already invited` check), **permission nodes** `groupchat.use` (default `true`) with `hasPermission` gates on all Paper commands (`DmCommand`, `PmCommand`, `GmOfflineCommand`, `GmsgCommand`, `GroupCommand`, `GroupGuiCommand`, `UnreadCommand`), `groupchat.admin` (default `op`, reserved).
- **Sync-blocking mitigation** (`PaperPlatformBridge`): `ConcurrentHashMap` cache for `name ↔ UUID` to avoid repeated `Bukkit.getOfflinePlayer` hits.
- **Reproducible builds** (`paper/build.gradle.kts`): pin `paper-api` to `26.3.build.8-alpha`.
- **Audit logging & ownership**: log `create/delete/invite/accept/decline/leave/kick` and owner transfer on leave; notify new owner when previous owner leaves.

### Changed

- **Platform upgrade** (`b783312`): retargeted to **Minecraft 26.3** (Paper 26.3, Fabric 26.3), **JDK 26**, Loom `1.18.2`; mappings now use Mojang official names (no Yarn), `modImplementation` → `implementation`, `pluginManagement` with `https://maven.fabricmc.net/`, `fabric.mod.json` entrypoint `"server"` for `DedicatedServerModInitializer`.
- **`plugin.yml` usages** updated for new `history`/`toggle` syntax and single-group shorthand (`/group <group> <history|toggle> [count]`, `/gmsg`/`gmoffline` toggle/history aliases).
- **Core lifecycle**: `GroupChatService.saveAll()` now includes `history`; `leave`/`kick`/`delete` clean up history and toggle state; `QuitListener` added.

### Technical notes

- Commits included in this release (oldest → newest):
  - `b783312` feat: upgrade to Minecraft 26.3
  - `4e4b774` security: harden GroupChat
  - `24b397c` feat: add per-group history (last 5) and toggle chat mode
  - `44a75a4` feat: interactive /group toggle selection when multiple groups
  - `b7ac626` fix(fabric): persist groups correctly after restart
  - `920956d` feat: interactive /group history selection when multiple groups
  - `e2c9d5e` feat(paper): log loaded groups count like fabric
- Build outputs: `paper/build/libs/groupchat-paper-2.0.0.jar`, `fabric/build/libs/groupchat-fabric-2.0.0.jar`
- No external runtime dependencies beyond Fabric API on Fabric; Gson remains `compileOnly` (provided by Paper/vanilla).

---

## [1.0.0] - Pre-repository

Initial Modrinth release. Repository was created afterwards — no git history for this version. Features at that point: group create/delete/invite/accept/decline/leave/kick/list/members, `gmsg`/`gmoffline`/`dm`/`pm`/`unread`, offline mailbox (`groups.json`/`messages.json`), `/groupgui` GUI, Paper + Fabric dual build.
