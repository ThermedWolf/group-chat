# GroupChat

Group chat + offline mailbox for Minecraft servers, built for **both Paper and Fabric**.

## Why two separate outputs instead of one jar

Paper and Fabric aren't really compatible at the "single artifact" level — Paper
plugins run entirely server-side against the Bukkit API and get loaded via
`plugin.yml`; Fabric mods use a completely different classloader, mapping
system, and command framework (Brigadier), and get loaded via
`fabric.mod.json`. There's no single `.jar` that satisfies both loaders.

So this project uses the standard multi-loader pattern instead:

```
groupchat/
├── core/     platform-agnostic logic: groups, mailbox, JSON persistence
├── paper/    thin adapter -> Bukkit/Paper commands & events
└── fabric/   thin adapter -> Brigadier commands & Fabric API events
```

`core` has zero Minecraft dependencies — just plain Java + Gson — and contains
all the actual behavior (creating groups, invites, routing messages,
queuing offline mail). `paper` and `fabric` each implement a small
`PlatformBridge` interface (send a message, check if a player's online,
resolve a name to a UUID) and wire up their platform's commands to call into
`core.GroupChatService`. Add the logic once, get both builds.

Building `paper/` gives you a normal Paper plugin jar. Building `fabric/`
gives you a **server-side Fabric mod** jar (it doesn't touch anything
client-side, since chat routing is all server logic) — install it on a
Fabric server the same as any other mod.

## Commands (identical on both platforms)

| Command | Description |
|---|---|
| `/group create <name>` | Create a group, you become the owner |
| `/group delete <name>` | Delete a group you own |
| `/group invite <name> <player>` | Invite a player (must be a member yourself) |
| `/group accept <name>` / `/group decline <name>` | Respond to an invite |
| `/group leave <name>` | Leave a group |
| `/group list` | List groups you're in |
| `/group members <name>` | List a group's members |
| `/gmsg <group> <message>` | Message the whole group at once — only members see it |
| `/pm <player> <message>` | Private message; queued automatically if they're offline |
| `/unread` | View and clear your queued/offline messages |
| `/groupgui` | Open the graphical menu (see below) |

Offline members of a group, or an offline `/pm` target, get the message
stored in their mailbox. On join, they see:

```
[GroupChat] You have 3 unread messages. Type /unread to view them.
```

Data is stored as `groups.json` and `messages.json` in the plugin/mod's
data folder (`plugins/GroupChat/` on Paper, `<world>/groupchat/` on Fabric).

## GUI (`/groupgui`)

Opens a chest-style menu with three options:

- **Create Group** — opens a virtual anvil GUI. Type a name in the rename
  field and click the output item to submit (this is the standard "free
  text input" trick for Minecraft GUIs, since no vanilla menu has a real
  text box outside anvils/books/signs).
- **My Groups** — lists groups you're in. Click one to message it, invite
  a player to it, leave it, or go back.
- **Message a Player** — lists online players. Click one to start a
  private message.

Both "message this group" and "message this player" work the same way:
picking a target closes the GUI and arms compose mode, then whatever you
type in chat next gets sent as that message instead of posting publicly.
Type `cancel` to back out without sending anything. This exists because an
inventory click can't collect free-text input, so the GUI's job is picking
*who*, and chat is still how you type *what*.

Every GUI action has a matching chat command underneath — the GUI is a
convenience layer over the same `GroupChatService` calls, not a separate
system, so the two stay in sync automatically.

## Building — targeting Minecraft 26.3

Minecraft 26.1 was a big one for modders: the game shipped **unobfuscated**
for the first time, and Fabric dropped Yarn mappings entirely in favor of
Mojang's own official names. That changes both the build tooling and a lot
of class/method names compared to older Fabric mods:

- Fabric Loom's plugin id changed from `fabric-loom` to `net.fabricmc.fabric-loom`,
  there's no more `mappings(...)` dependency line, and `modImplementation`
  became plain `implementation`. `settings.gradle.kts` needs a
  `pluginManagement` block pointing at `https://maven.fabricmc.net/` so
  Gradle can resolve the Loom plugin itself (a separate lookup from regular
  dependencies).
- Minecraft 26.3 requires **JDK 26**.
- Class names changed to Mojang's own (`ServerPlayerEntity` → `ServerPlayer`,
  `ServerCommandSource` → `CommandSourceStack`, `Text` → `Component`,
  `CommandManager.literal/argument` → `Commands.literal/argument`, etc).
- `fabric.mod.json`'s entrypoint key has to match the interface your main
  class implements — `"server"` for `DedicatedServerModInitializer`, not
  `"main"` (which is for `ModInitializer`).

This has now actually been built and run on a real 26.1.2 server and upgraded
to 26.3, so the `fabric/` module is in solid shape: confirmed working versions
are Loom `1.18.2`, Fabric Loader `0.19.5`, Fabric API `0.160.6+26.3`.
The trickier API pieces (`MinecraftServer#services().nameToIdCache()` for
offline player lookups, the `NameAndId` record, `AnvilMenu`/`ChestMenu`/
`Slot`/`DataComponents` for the GUI) were all confirmed by inspecting the
actual compiled classes from a 26.1.2 server jar rather than guessed, so
they should be reliable.

**One remaining unverified piece:** the GUI's "message this group/player"
flow captures your next chat message via Fabric API's
`ServerMessageEvents.ALLOW_CHAT_MESSAGE` (in `GroupChatMod.java`). That
event itself is Fabric API's own abstraction (not vanilla Minecraft), and I
didn't have a `fabric-api` jar to inspect the same way — only the vanilla
Minecraft ones — so if `fabric/` fails to compile, this is the first place
to check. Everything it calls into (`PlayerChatMessage#signedContent()`) is
independently confirmed.

You'll need **JDK 26** and Gradle installed (or use the included `gradlew`/`gradlew.bat` wrapper).

```bash
# from the project root
gradle build

# Paper plugin jar:
paper/build/libs/groupchat-paper-1.0.0.jar

# Fabric mod jar:
fabric/build/libs/groupchat-fabric-1.0.0.jar
```

If something in `fabric/` doesn't compile, it's almost always either a
version mismatch in `fabric/build.gradle.kts` (check current recommended
Loom/Loader/Fabric API versions at https://fabricmc.net/develop/) or the
`ServerMessageEvents` flagged above — the `core/` module never needs to
change, and `paper/` shouldn't either (Bukkit's public API is a stable
abstraction layer unaffected by Minecraft's internal mapping changes).

## Notes / possible follow-ups

- No permission-node gating yet (any player can use any command). Easy to
  add via Bukkit's `PluginManager` permissions / Fabric's permission API
  or LuBan/LuckPerms if you use one.
- Group names are case-insensitive, 2–24 chars, letters/numbers/underscore.
- No message history beyond the offline mailbox — read messages aren't kept.
- The Fabric player-picker GUI shows generic player-head icons rather than
  each player's actual skin (Paper's does show real skins, via
  `SkullMeta#setOwningPlayer`) — skipped to avoid guessing at the
  data-component API for skin textures. Names are still fully readable, so
  this is cosmetic only.
