package net.thermedwolf.groupchat.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Platform-agnostic entry point. Paper and Fabric command handlers call
 * straight into this class and just relay the resulting text.
 */
public class GroupChatService {

    private final GroupManager groupManager;
    private final MessageStore messageStore;
    private final PlatformBridge bridge;
    private final ComposeSession composeSession = new ComposeSession();

    public GroupChatService(File dataFolder, PlatformBridge bridge) {
        this.groupManager = new GroupManager(dataFolder);
        this.messageStore = new MessageStore(dataFolder);
        this.bridge = bridge;
    }

    public void saveAll() {
        groupManager.save();
        messageStore.save();
    }

    public CommandResult createGroup(UUID uuid, String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{2,24}")) {
            return CommandResult.fail("&cGroup names must be 2-24 characters: letters, numbers, underscore.");
        }
        boolean created = groupManager.createGroup(name, uuid);
        return created
                ? CommandResult.ok("&aGroup '" + name + "' created. You are the owner.")
                : CommandResult.fail("&cA group with that name already exists.");
    }

    public CommandResult deleteGroup(UUID uuid, String name) {
        boolean deleted = groupManager.deleteGroup(name, uuid);
        return deleted
                ? CommandResult.ok("&aGroup '" + name + "' deleted.")
                : CommandResult.fail("&cGroup not found, or you are not the owner.");
    }

    public CommandResult invite(UUID uuid, String groupName, String targetName) {
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

        if (!groupManager.invite(groupName, target)) {
            return CommandResult.fail("&cCould not invite that player.");
        }

        if (bridge.isOnline(target)) {
            bridge.sendMessage(target, "&e[GroupChat] &7You've been invited to join '" + group.getName()
                    + "' by " + bridge.getName(uuid) + ". Type &e/group accept " + group.getName() + "&7 to join.");
        }
        return CommandResult.ok("&aInvited " + bridge.getName(target) + " to '" + group.getName() + "'.");
    }

    public CommandResult acceptInvite(UUID uuid, String groupName) {
        boolean accepted = groupManager.acceptInvite(groupName, uuid);
        if (!accepted) {
            return CommandResult.fail("&cNo pending invite for that group.");
        }
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
        boolean declined = groupManager.declineInvite(groupName, uuid);
        return declined
                ? CommandResult.ok("&7Invite declined.")
                : CommandResult.fail("&cNo pending invite for that group.");
    }

    public CommandResult leaveGroup(UUID uuid, String groupName) {
        boolean left = groupManager.leaveGroup(groupName, uuid);
        return left
                ? CommandResult.ok("&7You left '" + groupName + "'.")
                : CommandResult.fail("&cYou are not a member of that group.");
    }

    public CommandResult kickMember(UUID requester, String groupName, String targetName) {
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
        if (bridge.isOnline(target)) {
            bridge.sendMessage(target, "&cYou were removed from '" + group.getName() + "'.");
        }
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

    public CommandResult members(String groupName) {
        Optional<Group> groupOpt = groupManager.getGroup(groupName);
        if (groupOpt.isEmpty()) {
            return CommandResult.fail("&cGroup not found.");
        }
        Group group = groupOpt.get();
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

    /**
     * Sends a message to every member of a group. Online members get it
     * immediately; offline members get it queued in their mailbox.
     */
    public CommandResult sendGroupMessage(UUID sender, String groupName, String message) {
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

        for (UUID member : group.getMembers()) {
            if (bridge.isOnline(member)) {
                bridge.sendMessage(member, formatted);
            } else if (!member.equals(sender)) {
                messageStore.addMessage(member, new PendingMessage(sender, bridge.getName(sender), group.getName(), message, now));
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

        for (UUID member : group.getMembers()) {
            if (bridge.isOnline(member)) {
                bridge.sendMessage(member, formatted);
            }
            if (!member.equals(sender)) {
                messageStore.addMessage(member, new PendingMessage(sender, bridge.getName(sender), group.getName(), message, now));
            }
        }
        return CommandResult.ok(null);
    }

    /**
     * Sends (or queues, if offline) a private message to a single player.
     */
    public CommandResult sendDirectMessage(UUID sender, String targetName, String message) {
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

        messageStore.addMessage(target, new PendingMessage(sender, bridge.getName(sender), null, message, System.currentTimeMillis()));
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
            String context = m.getContext() != null ? "&8[&d" + m.getContext() + "&8] " : "&8[&bPM&8] ";
            lines.add(ChatFormat.color(context + "&f" + m.getFromName() + "&7: &f" + m.getMessage()));
        }
        messageStore.clearMessages(uuid);
        return lines;
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