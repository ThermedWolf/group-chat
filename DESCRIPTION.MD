# Group Chat

Whisper to your whole crew at once — without everyone else on the server reading along.

Group Chat adds persistent chat groups to your server. Create a group, invite your friends, and message everyone in it with a single command — no more `/msg`-ing the same three people one at a time, and no more accidentally leaking a private conversation into public chat. Message someone (or a whole group) while they're offline, and they'll get it the moment they log back in.

Available for **both Paper and Fabric** — same features, same commands, same data format, whichever loader your server runs.

![Main menu](https://cdn.modrinth.com/data/cached_images/07e46455785edf90cad68dcd28d426f015002f39_0.webp)

## Features

- **Group chat** — message every member of a group at once with `/gmsg` (or the shorter `/gm`)
- **Offline mailbox** — messages to offline players or offline group members are held and delivered on next join, with an unread count so nothing gets missed
- **Guaranteed delivery option** — `/gmoffline` drops a copy into _every_ member's mailbox, online or not, for anything you want to be sure isn't lost in chat scrollback
- **Direct messages** — `/dm` for one-on-one, queued automatically if the recipient's offline
- **Full group management** — create, invite, accept/decline, leave, and owner-only kick
- **Message history** — last 5 messages per group, per player (`/group history`, `/gmsg history`) — so you can catch up after being AFK
- **Toggle chat** — lock your chat to a group (`/group toggle`) and just type normally, no prefix needed until you toggle off or type `cancel`
- **Graphical menu** — `/groupgui` opens a point-and-click interface for creating groups, browsing your groups, inviting or kicking members, and picking who to message, if you'd rather not remember command syntax
- **Tab-completion everywhere** — group names, player names, and pending invites all autocomplete
- **Zero external dependencies** — just the plugin/mod, no other downloads required beyond Fabric API on the Fabric side

![Creating a group](https://cdn.modrinth.com/data/cached_images/c8582c0795a79d26a6f2e3c7bd265b1d35170bcc_0.webp)

![Group chat in action](https://cdn.modrinth.com/data/cached_images/c6234184e047a1e25b61a6cd362e208369a84e97_0.webp)

## Commands

| Command                                            | Description                                                                           |
| -------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `/group create <name>`                             | Create a group — you become the owner                                                 |
| `/group invite <group> <player>`                   | Invite a player to a group you're in                                                  |
| `/group accept <group>` / `/group decline <group>` | Respond to an invite                                                                  |
| `/group kick <group> <player>`                     | Remove a member (owner only)                                                          |
| `/group leave <group>`                             | Leave a group                                                                         |
| `/group delete <group>`                            | Delete a group you own                                                                |
| `/group list`                                      | List the groups you're in                                                             |
| `/group members <group>`                           | List a group's members                                                                |
| `/gmsg <group> <message>` (alias `/gm`)            | Message a group — online members see it live, offline members get it queued           |
| `/gmoffline <group> <message>`                     | Same, but _everyone_ gets a durable copy in their mailbox regardless of online status |
| `/gmsg history [group] [1-5]` / `/group history`   | View last 5 messages of a group (omit group if in exactly 1)                          |
| `/gmsg toggle [group]` / `/group toggle [group]`   | Toggle chat mode — next chat lines go to that group until toggled off / `cancel`      |
| `/dm <player> <message>`                           | Private message; queued if they're offline                                            |
| `/unread [page\|clear]`                            | Read and clear your queued messages (paginated)                                       |
| `/groupgui`                                        | Open the graphical menu                                                               |

![Managing a group](https://cdn.modrinth.com/data/cached_images/6507e2c3fcbe5ce404ff263ec6c14610b34823fc_0.webp)

![Offline message delivery on join](https://cdn.modrinth.com/data/cached_images/d43de7001566593214273ec5a940f6cd8c3b6e47_0.webp)

## Requirements

- **Minecraft 26.3+**
- **Paper**: no additional dependencies
- **Fabric**: requires Fabric API

## Data & privacy

All group and message data is stored locally on your server (`plugins/GroupChat/` on Paper, `groupchat/` on Fabric — via `FabricLoader.getGameDir()`) as plain JSON — nothing is sent anywhere else.

## Notes

- Group names: 2–24 characters, letters/numbers/underscores, case-insensitive
- Permissions: `groupchat.use` (default `true`) gates all commands, `groupchat.admin` (default `op`, reserved)
- Mailbox: hard caps 500 chars / 200 messages per player, `§` sanitized, expired (>30d) pruned; group history keeps last 5 per group regardless of mailbox (`/group history`)
