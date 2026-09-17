package net.thermedwolf.groupchat.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Platform-agnostic entry point. Paper and Fabric command handlers call
 * straight into this class and just relay the resulting text.
 */
public class GroupChatService {

    private static final Logger LOGGER = Logger.getLogger(GroupChatService.class.getName());

    private final GroupManager groupManager;
    private final MessageStore messageStore;
    private final GroupHistoryStore historyStore;
    private final PlatformBridge bridge;
    private final ComposeSession composeSession = new ComposeSession();
    private final ToggleSession toggleSession = new ToggleSession();
    private final java.util.Map<UUID, Integer> pendingHistorySelection = new java.util.concurrent.ConcurrentHashMap<>();

    public GroupChatService(File dataFolder, PlatformBridge bridge) {
        this.groupManager = new GroupManager(dataFolder);
        this.messageStore = new MessageStore(dataFolder);
        this.historyStore = new GroupHistoryStore(dataFolder);
        this.bridge = bridge;
    }

    public void saveAll() {
        groupManager.save();
        messageStore.save();
        historyStore.save();
    }

    /** If player has exactly one group, return its name; otherwise empty. */
    public Optional<String> inferSingleGroup(UUID player) {
        List<Group> groups = groupManager.getGroupsForPlayer(player);
        if (groups.size() == 1) {
            return Optional.of(groups.get(0).getName());
        }
        return Optional.empty();
    }

    /**
     * Resolve a group name, supporting the omitting shorthand: if groupName is null/blank
     * and the player has exactly one group, infer it. Returns a CommandResult fail message
     * via Optional if resolution fails, or the resolved name.
     */
    public Optional<String> resolveGroupName(UUID player, String groupName) {
        if (groupName != null && !groupName.isBlank()) {
            return Optional.of(groupName);
        }
        Optional<String> inferred = inferSingleGroup(player);
        return inferred;
    }

    public String inferErrorMessage(UUID player) {
        List<Group> groups = groupManager.getGroupsForPlayer(player);
        if (groups.isEmpty()) {
            return "&cYou are not in any groups.";
        }
        StringBuilder sb = new StringBuilder("&cPlease specify a group. Your groups: &e");
        for (int i = 0; i < groups.size(); i++) {
            sb.append(groups.get(i).getName());
            if (i < groups.size() - 1) sb.append("&7, &e");
        }
        return sb.toString();
    }

    public CommandResult createGroup(UUID uuid, String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{2,24}")) {
            return CommandResult.fail("&cGroup names must be 2-24 characters: letters, numbers, underscore.");
        }
        boolean created = groupManager.createGroup(name, uuid);
        if (created) {
            LOGGER.log(Level.INFO, "Group ''{0}'' created by {1}", new Object[]{name, uuid});
        }
        return created
                ? CommandResult.ok("&aGroup '" + name + "' created. You are the owner.")
                : CommandResult.fail("&cA group with that name already exists.");
    }

    public CommandResult deleteGroup(UUID uuid, String name) {
        // support inferred group when name is null/blank and player has single group
        if (name == null || name.isBlank()) {
            Optional<String> inferred = resolveGroupName(uuid, name);
            if (inferred.isEmpty()) return CommandResult.fail(inferErrorMessage(uuid));
            name = inferred.get();
        }
        boolean deleted = groupManager.deleteGroup(name, uuid);
        if (deleted) {
            LOGGER.log(Level.INFO, "Group ''{0}'' deleted by {1}", new Object[]{name, uuid});
            // clear toggles for this group for all players
            String deletedName = name;
            toggleSession.clearForGroup(deletedName);
        }
        return deleted
                ? CommandResult.ok("&aGroup '" + name + "' deleted.")
                : CommandResult.fail("&cGroup not found, or you are not the owner.");
    }

    public CommandResult invite(UUID uuid, String groupName, String targetName) {
        if (groupName == null || groupName.isBlank()) {
            Optional<String> inferred = resolveGroupName(uuid, groupName);
            if (inferred.isEmpty()) return CommandResult.fail(inferErrorMessage(uuid));
            groupName = inferred.get();
        }
        Optional<Group> groupOpt = groupManager.getGroup(groupName);
        if (groupOpt.isEmpty()) {
            return CommandResult.fail("&cGroup not found.");
        }
        Group group = groupOpt.get();
        if (!group.isMember(uuid)) {
            return CommandResult.fail("&cYou are not a member of this group.");
        }

        Optional<UUID> targetOpt = bridge.getUuidByName(targetName);
        if (targetOpt.isEmpty()) {
            return CommandResult.fail("&cPlayer '" + targetName + "' was not found.");
        }
        UUID target = targetOpt.get();
        if (group.isMember(target)) {
            return CommandResult.fail("&cThat player is already a member.");
        }
        if (group.isInvited(target)) {
            return CommandResult.fail("&cThat player already has a pending invite.");
        }

        if (!groupManager.invite(groupName, target)) {
            return CommandResult.fail("&cCould not invite that player.");
        }
        LOGGER.log(Level.INFO, "{0} invited {1} to group ''{2}''", new Object[]{uuid, target, group.getName()});

        if (bridge.isOnline(target)) {
            bridge.sendMessage(target, "&e[GroupChat] &7You've been invited to join '" + group.getName()
                    + "' by " + bridge.getName(uuid) + ". Type &e/group accept " + group.getName() + "&7 to join.");
        }
        return CommandResult.ok("&aInvited " + bridge.getName(target) + " to '" + group.getName() + "'.");
    }

    public CommandResult acceptInvite(UUID uuid, String groupName) {
        if (groupName == null || groupName.isBlank()) {
            List<String> invited = getInvitedGroupNames(uuid);
            if (invited.size() == 1) {
                groupName = invited.get(0);
            } else if (invited.isEmpty()) {
                return CommandResult.fail("&cNo pending invites.");
            } else {
                StringBuilder sb = new StringBuilder("&cPlease specify a group. Pending invites: &e");
                for (int i = 0; i < invited.size(); i++) {
                    sb.append(invited.get(i));
                    if (i < invited.size() - 1) sb.append("&7, &e");
                }
                return CommandResult.fail(sb.toString());
            }
        }
        boolean accepted = groupManager.acceptInvite(groupName, uuid);
        if (!accepted) {
            return CommandResult.fail("&cNo pending invite for that group.");
        }
        LOGGER.log(Level.INFO, "{0} accepted invite to group ''{1}''", new Object[]{uuid, groupName});
        groupManager.getGroup(groupName).ifPresent(group -> {
            String notice = "&e[GroupChat] &7" + bridge.getName(uuid) + " joined '" + group.getName() + "'.";
            for (UUID member : group.getMembers()) {
                if (!member.equals(uuid) && bridge.isOnline(member)) {
                    bridge.sendMessage(member, notice);
                }
            }
        });
        return CommandResult.ok("&aYou joined '" + groupName + "'.");
    }

    public CommandResult declineInvite(UUID uuid, String groupName) {
        if (groupName == null || groupName.isBlank()) {
            List<String> invited = getInvitedGroupNames(uuid);
            if (invited.size() == 1) {
                groupName = invited.get(0);
            } else if (invited.isEmpty()) {
                return CommandResult.fail("&cNo pending invites.");
            } else {
                StringBuilder sb = new StringBuilder("&cPlease specify a group. Pending invites: &e");
                for (int i = 0; i < invited.size(); i++) {
                    sb.append(invited.get(i));
                    if (i < invited.size() - 1) sb.append("&7, &e");
                }
                return CommandResult.fail(sb.toString());
            }
        }
        boolean declined = groupManager.declineInvite(groupName, uuid);
        if (declined) {
            LOGGER.log(Level.INFO, "{0} declined invite to group ''{1}''", new Object[]{uuid, groupName});
        }
        return declined
                ? CommandResult.ok("&7Invite declined.")
                : CommandResult.fail("&cNo pending invite for that group.");
    }

    public CommandResult leaveGroup(UUID uuid, String groupName) {
        if (groupName == null || groupName.isBlank()) {
            Optional<String> inferred = resolveGroupName(uuid, groupName);
            if (inferred.isEmpty()) return CommandResult.fail(inferErrorMessage(uuid));
            groupName = inferred.get();
        }
        // Capture owner before leave to detect transfer
        Optional<Group> before = groupManager.getGroup(groupName);
        UUID oldOwner = before.map(Group::getOwner).orElse(null);
        final String finalGroupName = groupName;
        boolean left = groupManager.leaveGroup(groupName, uuid);
        if (left) {
            LOGGER.log(Level.INFO, "{0} left group ''{1}''", new Object[]{uuid, finalGroupName});
            // If leaver had toggle on this group, clear it
            toggleSession.getGroup(uuid).ifPresent(g -> {
                if (g.equalsIgnoreCase(finalGroupName)) toggleSession.clear(uuid);
            });
            // Notify new owner if ownership transferred
            if (oldOwner != null && oldOwner.equals(uuid)) {
                groupManager.getGroup(groupName).ifPresent(g -> {
                    UUID newOwner = g.getOwner();
                    if (!newOwner.equals(uuid) && bridge.isOnline(newOwner)) {
                        bridge.sendMessage(newOwner,
                                "&e[GroupChat] &7You are now the owner of '" + g.getName() + "' (previous owner left).");
                    }
                });
            }
        }
        return left
                ? CommandResult.ok("&7You left '" + groupName + "'.")
                : CommandResult.fail("&cYou are not a member of that group.");
    }

    public CommandResult kickMember(UUID requester, String groupName, String targetName) {
        // inferred group support when groupName is null/blank
        if (groupName == null || groupName.isBlank()) {
            Optional<String> inferred = resolveGroupName(requester, groupName);
            if (inferred.isEmpty()) return CommandResult.fail(inferErrorMessage(requester));
            groupName = inferred.get();
        }
        // When called via shorthand /group kick <player> (single group), targetName will be in groupName slot.
        // Paper layer handles ambiguity; this fallback keeps service robust.
        Optional<Group> groupOpt = groupManager.getGroup(groupName);
        if (groupOpt.isEmpty()) {
            return CommandResult.fail("&cGroup not found.");
        }
        Group group = groupOpt.get();
        if (!group.getOwner().equals(requester)) {
            return CommandResult.fail("&cOnly the group owner can kick members.");
        }
        Optional<UUID> targetOpt = bridge.getUuidByName(targetName);
        if (targetOpt.isEmpty()) {
            return CommandResult.fail("&cPlayer '" + targetName + "' was not found.");
        }
        UUID target = targetOpt.get();
        if (target.equals(requester)) {
            return CommandResult.fail("&cYou can't kick yourself - use /group delete instead.");
        }
        if (!group.isMember(target)) {
            return CommandResult.fail("&cThat player is not a member of this group.");
        }
        if (!groupManager.kickMember(groupName, target)) {
            return CommandResult.fail("&cCould not remove that player.");
        }
        LOGGER.log(Level.INFO, "{0} kicked {1} from group ''{2}''", new Object[]{requester, target, group.getName()});
        if (bridge.isOnline(target)) {
            bridge.sendMessage(target, "&cYou were removed from '" + group.getName() + "'.");
        }
        // clear toggle if kicked player had it enabled for this group
        toggleSession.getGroup(target).ifPresent(g -> {
            if (g.equalsIgnoreCase(group.getName())) toggleSession.clear(target);
        });
        return CommandResult.ok("&aRemoved " + bridge.getName(target) + " from '" + group.getName() + "'.");
    }

    public String listGroupsFormatted(UUID uuid) {
        List<Group> groups = groupManager.getGroupsForPlayer(uuid);
        if (groups.isEmpty()) {
            return "&7You are not in any groups.";
        }
        StringBuilder sb = new StringBuilder("&7Your groups: &e");
        for (int i = 0; i < groups.size(); i++) {
            sb.append(groups.get(i).getName());
            if (i < groups.size() - 1) {
                sb.append("&7, &e");
            }
        }
        return sb.toString();
    }

    public CommandResult members(UUID requester, String groupName) {
        if (groupName == null || groupName.isBlank()) {
            Optional<String> inferred = resolveGroupName(requester, groupName);
            if (inferred.isEmpty()) return CommandResult.fail(inferErrorMessage(requester));
            groupName = inferred.get();
        }
        Optional<Group> groupOpt = groupManager.getGroup(groupName);
        if (groupOpt.isEmpty()) {
            return CommandResult.fail("&cGroup not found.");
        }
        Group group = groupOpt.get();
        if (!group.isMember(requester)) {
            return CommandResult.fail("&cYou are not a member of that group.");
        }
        StringBuilder sb = new StringBuilder("&7Members of '" + group.getName() + "': &e");
        Iterator<UUID> it = group.getMembers().iterator();
        while (it.hasNext()) {
            UUID member = it.next();
            sb.append(bridge.getName(member));
            if (member.equals(group.getOwner())) {
                sb.append(" &6(owner)");
            }
            if (it.hasNext()) {
                sb.append("&7, &e");
            }
        }
        return CommandResult.ok(sb.toString());
    }

    /** Backwards-compatible overload — prefer members(UUID, String). */
    @Deprecated
    public CommandResult members(String groupName) {
        return CommandResult.fail("&cUsage: /group members requires membership check — call members(requester, name).");
    }

    private static String validateAndSanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String sanitized = ChatFormat.sanitizeUserText(message).trim();
        if (sanitized.length() > MessageStore.MAX_MESSAGE_LENGTH) {
            return null;
        }
        return sanitized;
    }

    /**
     * Sends a message to every member of a group. Online members get it
     * immediately; offline members get it queued in their mailbox.
     */
    public CommandResult sendGroupMessage(UUID sender, String groupName, String message) {
        // inferred group support for gmsg shorthand etc.
        if (groupName == null || groupName.isBlank()) {
            Optional<String> inferred = resolveGroupName(sender, groupName);
            if (inferred.isEmpty()) return CommandResult.fail(inferErrorMessage(sender));
            groupName = inferred.get();
        }
        String sanitized = validateAndSanitizeMessage(message);
        if (sanitized == null) {
            if (message != null && ChatFormat.sanitizeUserText(message).trim().length() > MessageStore.MAX_MESSAGE_LENGTH) {
                return CommandResult.fail("&cMessage too long (max " + MessageStore.MAX_MESSAGE_LENGTH + " characters).");
            }
            return CommandResult.fail("&cMessage cannot be empty.");
        }
        message = sanitized;

        Optional<Group> groupOpt = groupManager.getGroup(groupName);
        if (groupOpt.isEmpty()) {
            return CommandResult.fail("&cGroup not found.");
        }
        Group group = groupOpt.get();
        if (!group.isMember(sender)) {
            return CommandResult.fail("&cYou are not a member of that group.");
        }

        String formatted = "&8[&d" + group.getName() + "&8] &f" + bridge.getName(sender) + "&7: &f" + message;
        long now = System.currentTimeMillis();

        // Persist rolling history for every member (sender + all recipients)
        GroupHistoryEntry historyEntry = new GroupHistoryEntry(sender, bridge.getName(sender), message, now);
        for (UUID member : group.getMembers()) {
            historyStore.addMessage(member, group.getName(), historyEntry);
        }

        for (UUID member : group.getMembers()) {
            if (bridge.isOnline(member)) {
                bridge.sendMessage(member, formatted);
            } else if (!member.equals(sender)) {
                boolean queued = messageStore.addMessage(member,
                        new PendingMessage(sender, bridge.getName(sender), group.getName(), message, now));
                if (!queued) {
                    bridge.sendMessage(sender,
                            "&cMailbox full for " + bridge.getName(member) + " (" + MessageStore.MAX_MAILBOX_SIZE + "/" + MessageStore.MAX_MAILBOX_SIZE + ") — message not queued for them.");
                }
            }
        }
        return CommandResult.ok(null);
    }

    /**
     * Like sendGroupMessage(), but guarantees the message ends up in every
     * member's mailbox (via /unread) regardless of whether they were online -
     * online members still get it live too, they just also get a durable
     * copy. Useful for anything you want to be sure isn't missed in chat
     * scrollback (announcements, important group updates, etc).
     */
    public CommandResult sendGroupMessagePersistent(UUID sender, String groupName, String message) {
        if (groupName == null || groupName.isBlank()) {
            Optional<String> inferred = resolveGroupName(sender, groupName);
            if (inferred.isEmpty()) return CommandResult.fail(inferErrorMessage(sender));
            groupName = inferred.get();
        }
        String sanitized = validateAndSanitizeMessage(message);
        if (sanitized == null) {
            if (message != null && ChatFormat.sanitizeUserText(message).trim().length() > MessageStore.MAX_MESSAGE_LENGTH) {
                return CommandResult.fail("&cMessage too long (max " + MessageStore.MAX_MESSAGE_LENGTH + " characters).");
            }
            return CommandResult.fail("&cMessage cannot be empty.");
        }
        message = sanitized;

        Optional<Group> groupOpt = groupManager.getGroup(groupName);
        if (groupOpt.isEmpty()) {
            return CommandResult.fail("&cGroup not found.");
        }
        Group group = groupOpt.get();
        if (!group.isMember(sender)) {
            return CommandResult.fail("&cYou are not a member of that group.");
        }

        String formatted = "&8[&d" + group.getName() + "&8] &f" + bridge.getName(sender) + "&7: &f" + message;
        long now = System.currentTimeMillis();

        GroupHistoryEntry historyEntry = new GroupHistoryEntry(sender, bridge.getName(sender), message, now);
        for (UUID member : group.getMembers()) {
            historyStore.addMessage(member, group.getName(), historyEntry);
        }

        for (UUID member : group.getMembers()) {
            if (bridge.isOnline(member)) {
                bridge.sendMessage(member, formatted);
            }
            if (!member.equals(sender)) {
                boolean queued = messageStore.addMessage(member,
                        new PendingMessage(sender, bridge.getName(sender), group.getName(), message, now));
                if (!queued) {
                    bridge.sendMessage(sender,
                            "&cMailbox full for " + bridge.getName(member) + " (" + MessageStore.MAX_MAILBOX_SIZE + "/" + MessageStore.MAX_MAILBOX_SIZE + ") — message not queued for them.");
                }
            }
        }
        return CommandResult.ok(null);
    }

    /**
     * Sends (or queues, if offline) a private message to a single player.
     */
    public CommandResult sendDirectMessage(UUID sender, String targetName, String message) {
        String sanitized = validateAndSanitizeMessage(message);
        if (sanitized == null) {
            if (message != null && ChatFormat.sanitizeUserText(message).trim().length() > MessageStore.MAX_MESSAGE_LENGTH) {
                return CommandResult.fail("&cMessage too long (max " + MessageStore.MAX_MESSAGE_LENGTH + " characters).");
            }
            return CommandResult.fail("&cMessage cannot be empty.");
        }
        message = sanitized;

        Optional<UUID> targetOpt = bridge.getUuidByName(targetName);
        if (targetOpt.isEmpty()) {
            return CommandResult.fail("&cPlayer '" + targetName + "' was not found.");
        }
        UUID target = targetOpt.get();
        if (target.equals(sender)) {
            return CommandResult.fail("&cYou can't message yourself.");
        }

        if (bridge.isOnline(target)) {
            bridge.sendMessage(target, "&8[&bPM&8] &f" + bridge.getName(sender) + " &7-> you: &f" + message);
            bridge.sendMessage(sender, "&8[&bPM&8] &7you -> " + bridge.getName(target) + ": &f" + message);
            return CommandResult.ok(null);
        }

        boolean queued = messageStore.addMessage(target,
                new PendingMessage(sender, bridge.getName(sender), null, message, System.currentTimeMillis()));
        if (!queued) {
            return CommandResult.fail("&c" + bridge.getName(target) + "'s mailbox is full (" + MessageStore.MAX_MAILBOX_SIZE + "/" + MessageStore.MAX_MAILBOX_SIZE + "). Try again later.");
        }
        return CommandResult.ok("&7" + bridge.getName(target) + " is offline. Your message will be delivered when they join.");
    }

    public int getUnreadCount(UUID uuid) {
        return messageStore.getUnreadCount(uuid);
    }

    /** Formats all pending messages for display and clears the mailbox. */
    public List<String> viewAndClearUnread(UUID uuid) {
        List<PendingMessage> messages = messageStore.getMessages(uuid);
        if (messages.isEmpty()) {
            return List.of(ChatFormat.color("&7You have no unread messages."));
        }
        List<String> lines = new ArrayList<>();
        lines.add(ChatFormat.color("&e--- " + messages.size() + " unread message" + (messages.size() == 1 ? "" : "s") + " ---"));
        for (PendingMessage m : messages) {
            String ctx = m.getContext() != null ? ChatFormat.sanitizeUserText(m.getContext()) : null;
            String from = ChatFormat.sanitizeUserText(m.getFromName());
            String msg = ChatFormat.sanitizeUserText(m.getMessage());
            String context = ctx != null ? "&8[&d" + ctx + "&8] " : "&8[&bPM&8] ";
            lines.add(ChatFormat.color(context + "&f" + from + "&7: &f" + msg));
        }
        messageStore.clearMessages(uuid);
        return lines;
    }

    /**
     * Paginated view without clearing. Page is 1-indexed for UX, 10 per page.
     */
    public List<String> viewUnreadPage(UUID uuid, int page) {
        if (page < 1) {
            page = 1;
        }
        int pageSize = 10;
        int total = messageStore.getUnreadCount(uuid);
        if (total == 0) {
            return List.of(ChatFormat.color("&7You have no unread messages."));
        }
        int totalPages = (total + pageSize - 1) / pageSize;
        if (page > totalPages) {
            page = totalPages;
        }
        List<PendingMessage> messages = messageStore.getMessagesPaged(uuid, page - 1, pageSize);
        List<String> lines = new ArrayList<>();
        lines.add(ChatFormat.color("&e--- Unread " + total + " messages — page " + page + "/" + totalPages + " ---"));
        for (PendingMessage m : messages) {
            String ctx = m.getContext() != null ? ChatFormat.sanitizeUserText(m.getContext()) : null;
            String from = ChatFormat.sanitizeUserText(m.getFromName());
            String msg = ChatFormat.sanitizeUserText(m.getMessage());
            String context = ctx != null ? "&8[&d" + ctx + "&8] " : "&8[&bPM&8] ";
            lines.add(ChatFormat.color(context + "&f" + from + "&7: &f" + msg));
        }
        if (totalPages > 1) {
            lines.add(ChatFormat.color("&7Use &e/unread " + (page < totalPages ? page + 1 : 1) + "&7 to see "
                    + (page < totalPages ? "next" : "first") + " page, &e/unread clear&7 to clear all."));
        }
        return lines;
    }

    // ---- History ----
    public List<String> getGroupHistory(UUID requester, String groupName, int count) {
        if (count < 1) count = 1;
        if (count > GroupHistoryStore.MAX_HISTORY_PER_GROUP) count = GroupHistoryStore.MAX_HISTORY_PER_GROUP;
        if (groupName == null || groupName.isBlank()) {
            Optional<String> inferred = resolveGroupName(requester, groupName);
            if (inferred.isPresent()) {
                groupName = inferred.get();
            } else {
                List<Group> groups = groupManager.getGroupsForPlayer(requester);
                if (groups.isEmpty()) {
                    return List.of(ChatFormat.color("&cYou are not in any groups."));
                }
                // Multiple groups - enter chat selection mode (like /group toggle)
                pendingHistorySelection.put(requester, count);
                StringBuilder sb = new StringBuilder("&eYou are part of multiple groups, type the name of one of the groups in chat to select it: &a");
                for (int i = 0; i < groups.size(); i++) {
                    sb.append(groups.get(i).getName());
                    if (i < groups.size() - 1) sb.append("&7, &a");
                }
                sb.append("&e. &7You can also re-run &e/group history <group> [1-5] &7or &e/gmsg history <group> [1-5]&7. Type &ccancel &7to exit.");
                return List.of(ChatFormat.color(sb.toString()));
            }
        } else {
            // Explicit group provided while pending - clear pending and proceed
            pendingHistorySelection.remove(requester);
        }

        Optional<Group> groupOpt = groupManager.getGroup(groupName);
        if (groupOpt.isEmpty()) {
            return List.of(ChatFormat.color("&cGroup not found."));
        }
        Group group = groupOpt.get();
        if (!group.isMember(requester)) {
            return List.of(ChatFormat.color("&cYou are not a member of that group."));
        }

        List<GroupHistoryEntry> all = historyStore.getHistory(requester, group.getName());
        if (all.isEmpty()) {
            return List.of(ChatFormat.color("&7No recent messages in '" + group.getName() + "'."));
        }
        // oldest->newest, take last 'count'
        int from = Math.max(0, all.size() - count);
        List<GroupHistoryEntry> slice = all.subList(from, all.size());

        List<String> lines = new ArrayList<>();
        lines.add(ChatFormat.color("&e--- Last " + slice.size() + " messages in '" + group.getName() + "' ---"));
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        for (GroupHistoryEntry e : slice) {
            String time = java.time.Instant.ofEpochMilli(e.getTimestamp())
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(fmt);
            String fromName = ChatFormat.sanitizeUserText(e.getFromName());
            String msg = ChatFormat.sanitizeUserText(e.getMessage());
            lines.add(ChatFormat.color("&8[" + time + "] &f" + fromName + "&7: &f" + msg));
        }
        return lines;
    }

    // ---- Toggle ----
    public CommandResult toggleGroupChat(UUID player, String groupName) {
        if (groupName == null || groupName.isBlank()) {
            Optional<String> inferred = resolveGroupName(player, groupName);
            if (inferred.isPresent()) {
                groupName = inferred.get();
            } else {
                List<Group> groups = groupManager.getGroupsForPlayer(player);
                if (groups.isEmpty()) {
                    return CommandResult.fail("&cYou are not in any groups.");
                }
                // Multiple groups - enter chat selection mode
                toggleSession.beginPending(player);
                StringBuilder sb = new StringBuilder("&eYou are part of multiple groups, type the name of one of the groups in chat to select it: &a");
                for (int i = 0; i < groups.size(); i++) {
                    sb.append(groups.get(i).getName());
                    if (i < groups.size() - 1) sb.append("&7, &a");
                }
                sb.append("&e. &7You can also re-run &e/group toggle <group> &7or &e/gmsg toggle <group>&7. Type &ccancel &7to exit.");
                return CommandResult.ok(sb.toString());
            }
        } else {
            // Explicit group provided while pending - clear pending first and proceed to toggle
            toggleSession.clearPending(player);
        }
        Optional<Group> groupOpt = groupManager.getGroup(groupName);
        if (groupOpt.isEmpty()) {
            return CommandResult.fail("&cGroup not found.");
        }
        Group group = groupOpt.get();
        if (!group.isMember(player)) {
            return CommandResult.fail("&cYou are not a member of that group.");
        }
        boolean nowEnabled = toggleSession.toggle(player, group.getName());
        // If composing was active, cancel it when toggling (user explicitly chose toggle mode)
        // Also clear pending selection if any
        toggleSession.clearPending(player);
        if (nowEnabled) {
            composeSession.cancel(player);
            return CommandResult.ok("&aToggled group chat ON for '" + group.getName() + "'. &7All your messages will go there. Type &ecancel&7 or &e/group " + group.getName() + " toggle&7 again to turn off.");
        } else {
            return CommandResult.ok("&7Toggled group chat OFF. Your messages will go to public chat again.");
        }
    }

    public boolean isPendingHistorySelection(UUID player) {
        return pendingHistorySelection.containsKey(player);
    }

    public Optional<CommandResult> tryHandlePendingHistorySelection(UUID player, String message) {
        Integer pendingCount = pendingHistorySelection.get(player);
        if (pendingCount == null) {
            return Optional.empty();
        }
        String trimmed = message.trim();
        if (trimmed.equalsIgnoreCase("cancel")) {
            pendingHistorySelection.remove(player);
            bridge.sendMessage(player, "&7Cancelled history selection.");
            return Optional.of(CommandResult.ok(null));
        }
        String candidate = trimmed;
        if (candidate.contains(" ")) {
            candidate = candidate.split("\\s+")[0];
        }
        Optional<Group> groupOpt = groupManager.getGroup(candidate);
        if (groupOpt.isEmpty()) {
            bridge.sendMessage(player, ChatFormat.color("&cGroup '" + ChatFormat.sanitizeUserText(candidate) + "' not found. &7Try again or type &ccancel &7to exit. Your groups: &a"
                    + String.join("&7, &a", getGroupNamesForPlayer(player))));
            return Optional.of(CommandResult.ok(null));
        }
        Group group = groupOpt.get();
        if (!group.isMember(player)) {
            bridge.sendMessage(player, ChatFormat.color("&cYou are not a member of '" + group.getName() + "'. &7Try again or type &ccancel &7to exit."));
            return Optional.of(CommandResult.ok(null));
        }
        // Valid selection - show history with remembered count
        int count = pendingCount;
        pendingHistorySelection.remove(player);
        List<String> lines = getGroupHistoryInternal(player, group.getName(), count);
        for (String line : lines) {
            bridge.sendMessage(player, line);
        }
        return Optional.of(CommandResult.ok(null));
    }

    // Internal helper that assumes groupName is already resolved (no pending logic)
    private List<String> getGroupHistoryInternal(UUID requester, String groupName, int count) {
        if (count < 1) count = 1;
        if (count > GroupHistoryStore.MAX_HISTORY_PER_GROUP) count = GroupHistoryStore.MAX_HISTORY_PER_GROUP;
        Optional<Group> groupOpt = groupManager.getGroup(groupName);
        if (groupOpt.isEmpty()) {
            return List.of(ChatFormat.color("&cGroup not found."));
        }
        Group group = groupOpt.get();
        if (!group.isMember(requester)) {
            return List.of(ChatFormat.color("&cYou are not a member of that group."));
        }
        List<GroupHistoryEntry> all = historyStore.getHistory(requester, group.getName());
        if (all.isEmpty()) {
            return List.of(ChatFormat.color("&7No recent messages in '" + group.getName() + "'."));
        }
        int from = Math.max(0, all.size() - count);
        List<GroupHistoryEntry> slice = all.subList(from, all.size());
        List<String> lines = new ArrayList<>();
        lines.add(ChatFormat.color("&e--- Last " + slice.size() + " messages in '" + group.getName() + "' ---"));
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        for (GroupHistoryEntry e : slice) {
            String time = java.time.Instant.ofEpochMilli(e.getTimestamp())
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(fmt);
            String fromName = ChatFormat.sanitizeUserText(e.getFromName());
            String msg = ChatFormat.sanitizeUserText(e.getMessage());
            lines.add(ChatFormat.color("&8[" + time + "] &f" + fromName + "&7: &f" + msg));
        }
        return lines;
    }

    public boolean isPendingToggleSelection(UUID player) {
        return toggleSession.isPendingSelection(player);
    }

    public Optional<CommandResult> tryHandlePendingToggleSelection(UUID player, String message) {
        if (!toggleSession.isPendingSelection(player)) {
            return Optional.empty();
        }
        String trimmed = message.trim();
        if (trimmed.equalsIgnoreCase("cancel")) {
            toggleSession.clearPending(player);
            bridge.sendMessage(player, "&7Cancelled toggle selection.");
            return Optional.of(CommandResult.ok(null));
        }
        // Allow only single-word group names; if they typed with spaces, take first token as attempt but also give error
        String candidate = trimmed;
        // Extract first word if contains spaces (common mistake: typing message instead of group name)
        if (candidate.contains(" ")) {
            candidate = candidate.split("\\s+")[0];
            // But we still want to hint they typed extra text
        }
        Optional<Group> groupOpt = groupManager.getGroup(candidate);
        if (groupOpt.isEmpty()) {
            bridge.sendMessage(player, ChatFormat.color("&cGroup '" + ChatFormat.sanitizeUserText(candidate) + "' not found. &7Try again or type &ccancel &7to exit. Your groups: &a"
                    + String.join("&7, &a", getGroupNamesForPlayer(player))));
            return Optional.of(CommandResult.ok(null));
        }
        Group group = groupOpt.get();
        if (!group.isMember(player)) {
            bridge.sendMessage(player, ChatFormat.color("&cYou are not a member of '" + group.getName() + "'. &7Try again or type &ccancel &7to exit."));
            return Optional.of(CommandResult.ok(null));
        }
        // Valid selection - toggle on
        toggleSession.clearPending(player);
        boolean nowEnabled = toggleSession.toggle(player, group.getName());
        composeSession.cancel(player);
        if (nowEnabled) {
            bridge.sendMessage(player, ChatFormat.color("&aToggled group chat ON for '" + group.getName() + "'. &7All your messages will go there. Type &ecancel&7 or &e/group " + group.getName() + " toggle&7 again to turn off."));
        } else {
            bridge.sendMessage(player, ChatFormat.color("&7Toggled group chat OFF. Your messages will go to public chat again."));
        }
        return Optional.of(CommandResult.ok(null));
    }

    public boolean isToggled(UUID player) {
        return toggleSession.isToggled(player);
    }

    public Optional<String> getToggledGroup(UUID player) {
        return toggleSession.getGroup(player);
    }

    public void clearToggle(UUID player) {
        toggleSession.clear(player);
        toggleSession.clearPending(player);
        pendingHistorySelection.remove(player);
        composeSession.cancel(player);
    }

    public void clearToggleOnDisconnect(UUID player) {
        toggleSession.clear(player);
        toggleSession.clearPending(player);
        pendingHistorySelection.remove(player);
        composeSession.cancel(player);
    }

    public void clearPendingHistory(UUID player) {
        pendingHistorySelection.remove(player);
    }

    /**
     * Handles a normal chat message when toggle is active. Compose has priority and is handled elsewhere.
     * Returns non-empty Optional if the message was consumed (channelled to group or cancelled).
     */
    public Optional<CommandResult> tryHandleToggledChat(UUID sender, String message) {
        Optional<String> toggledOpt = toggleSession.getGroup(sender);
        if (toggledOpt.isEmpty()) {
            return Optional.empty();
        }
        String groupName = toggledOpt.get();

        // 'cancel' anywhere while toggled or composing exits both modes
        if (message.trim().equalsIgnoreCase("cancel")) {
            toggleSession.clear(sender);
            composeSession.cancel(sender);
            bridge.sendMessage(sender, "&7Toggled chat off. Cancelled.");
            return Optional.of(CommandResult.ok(null));
        }

        CommandResult result = sendGroupMessage(sender, groupName, message);
        if (result.getMessage() != null) {
            bridge.sendMessage(sender, result.getMessage());
            // If group not found or not member any more, auto-disable toggle
            if (!result.isSuccess()) {
                // keep toggle on failure? No, clear if membership issue
                if (result.getMessage().contains("not a member") || result.getMessage().contains("Group not found")) {
                    toggleSession.clear(sender);
                    bridge.sendMessage(sender, "&7Toggle disabled due to error.");
                }
            }
        }
        return Optional.of(result);
    }

    /**
     * Unified entry for platforms: checks pending history/toggle selection, then compose, then toggle. Returns true if consumed.
     */
    public boolean handleChatIntercept(UUID sender, String plainMessage) {
        // Pending history selection (from /group history without group) - highest priority
        Optional<CommandResult> pendingHistory = tryHandlePendingHistorySelection(sender, plainMessage);
        if (pendingHistory.isPresent()) {
            return true;
        }
        // Pending toggle selection has next priority - waiting for group name via chat
        Optional<CommandResult> pending = tryHandlePendingToggleSelection(sender, plainMessage);
        if (pending.isPresent()) {
            return true;
        }
        // Compose has priority (GUI flow)
        Optional<CommandResult> compose = tryHandleChatAsCompose(sender, plainMessage);
        if (compose.isPresent()) {
            // If message was "cancel" while composing, that already cleared compose. But if user was also pending or toggled, clear those too
            if (plainMessage.trim().equalsIgnoreCase("cancel")) {
                toggleSession.clear(sender);
                toggleSession.clearPending(sender);
                pendingHistorySelection.remove(sender);
            }
            return true;
        }
        // Check toggle
        Optional<CommandResult> toggled = tryHandleToggledChat(sender, plainMessage);
        if (toggled.isPresent()) {
            return true;
        }
        // If user typed cancel while pending history/toggle but not yet consumed (e.g. no pending flag set but typed cancel anyway), ensure cleanup
        if (plainMessage.trim().equalsIgnoreCase("cancel")) {
            boolean hadPending = pendingHistorySelection.remove(sender) != null;
            if (toggleSession.isPendingSelection(sender)) {
                toggleSession.clearPending(sender);
                hadPending = true;
            }
            if (hadPending) {
                bridge.sendMessage(sender, "&7Cancelled.");
                return true;
            }
        }
        return false;
    }

    // ---- GUI support ----
    // The GUI needs raw Group objects to build its menus (names, member
    // counts, etc). These are read-only views; all mutation still goes
    // through the methods above so command and GUI paths can't drift apart.

    public List<Group> getGroupsForPlayer(UUID uuid) {
        return groupManager.getGroupsForPlayer(uuid);
    }

    /** Group names the player is a member of - handy for tab completion. */
    public List<String> getGroupNamesForPlayer(UUID uuid) {
        return groupManager.getGroupsForPlayer(uuid).stream().map(Group::getName).toList();
    }

    /** Group names the player has a pending invite to - handy for accept/decline tab completion. */
    public List<String> getInvitedGroupNames(UUID uuid) {
        return groupManager.getGroupsWithPendingInvite(uuid).stream().map(Group::getName).toList();
    }

    /**
     * Formats a "you have pending invites" notice for the join listener, or
     * returns null if there's nothing to show. Queried live off current
     * invite state rather than stored as a message, so it's always accurate
     * even if invites were accepted/declined/changed since being sent -
     * unlike the offline mailbox, there's nothing to "consume" here.
     */
    public String getPendingInviteNotice(UUID uuid) {
        List<String> invited = getInvitedGroupNames(uuid);
        if (invited.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("&e[GroupChat] &7You have a pending invite to: &a");
        for (int i = 0; i < invited.size(); i++) {
            sb.append(invited.get(i));
            if (i < invited.size() - 1) {
                sb.append("&7, &a");
            }
        }
        sb.append("&7. Type &a/group accept <name>&7 to join.");
        return sb.toString();
    }

    public Optional<Group> getGroup(String name) {
        return groupManager.getGroup(name);
    }

    public String getDisplayName(UUID uuid) {
        return bridge.getName(uuid);
    }

    /**
     * Call when the player picks "message this group" in the GUI. Validates
     * membership the same way sendGroupMessage() does, then arms compose
     * mode so their next chat message gets routed as the group message.
     */
    public CommandResult startComposeGroupMessage(UUID sender, String groupName) {
        Optional<Group> groupOpt = groupManager.getGroup(groupName);
        if (groupOpt.isEmpty()) {
            return CommandResult.fail("&cGroup not found.");
        }
        if (!groupOpt.get().isMember(sender)) {
            return CommandResult.fail("&cYou are not a member of that group.");
        }
        composeSession.beginGroupMessage(sender, groupOpt.get().getName());
        return CommandResult.ok("&eType your message in chat and send it - it'll go to '" + groupOpt.get().getName()
                + "'. Type &ccancel&e to back out.");
    }

    /** Call when the player picks a player to message in the GUI. */
    public CommandResult startComposeDirectMessage(UUID sender, UUID targetUuid) {
        if (targetUuid.equals(sender)) {
            return CommandResult.fail("&cYou can't message yourself.");
        }
        String targetName = bridge.getName(targetUuid);
        composeSession.beginDirectMessage(sender, targetName);
        return CommandResult.ok("&eType your message in chat and send it - it'll go to " + targetName
                + ". Type &ccancel&e to back out.");
    }

    public boolean isComposing(UUID uuid) {
        return composeSession.isComposing(uuid);
    }

    /**
     * Platforms call this from their chat-message hook, BEFORE letting the
     * message post normally. Returns empty if the player wasn't in compose
     * mode (platform should let the chat message through as usual).
     * Otherwise the message was consumed - the platform should cancel/absorb
     * the original chat event and rely on the feedback already sent here.
     */
    public Optional<CommandResult> tryHandleChatAsCompose(UUID sender, String message) {
        Optional<ComposeSession.Pending> pendingOpt = composeSession.consume(sender);
        if (pendingOpt.isEmpty()) {
            return Optional.empty();
        }
        ComposeSession.Pending pending = pendingOpt.get();

        if (message.trim().equalsIgnoreCase("cancel")) {
            bridge.sendMessage(sender, "&7Cancelled.");
            return Optional.of(CommandResult.ok(null));
        }

        CommandResult result = pending.isGroup()
                ? sendGroupMessage(sender, pending.targetName(), message)
                : sendDirectMessage(sender, pending.targetName(), message);

        if (result.getMessage() != null) {
            bridge.sendMessage(sender, result.getMessage());
        }
        return Optional.of(result);
    }
}