package net.thermedwolf.groupchat.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.thermedwolf.groupchat.core.ChatFormat;
import net.thermedwolf.groupchat.core.CommandResult;
import net.thermedwolf.groupchat.core.GroupChatService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Function;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Confirmed working: this compiled clean and ran on a real 26.1.2 server
 * (CommandSourceStack, Commands.literal/argument, Component,
 * sendSuccess/getPlayerOrException all correct as written).
 * SharedSuggestionProvider.suggest(...) confirmed against the real
 * 26.1.2 class file for the tab-completion helpers below.
 */
public class GroupChatCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("group")
                .then(literal("create").then(argument("name", StringArgumentType.word())
                        .executes(ctx -> run(ctx.getSource(),
                                src -> service().createGroup(player(src).getUUID(),
                                        StringArgumentType.getString(ctx, "name"))))))
                .then(literal("delete").then(argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOwnGroups(ctx, builder))
                        .executes(ctx -> run(ctx.getSource(),
                                src -> service().deleteGroup(player(src).getUUID(),
                                        StringArgumentType.getString(ctx, "name"))))))
                .then(literal("invite").then(argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOwnGroups(ctx, builder))
                        .then(argument("player", StringArgumentType.word())
                                .suggests(GroupChatCommands::suggestOtherOnlinePlayers)
                                .executes(ctx -> run(ctx.getSource(), src -> service().invite(player(src).getUUID(),
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "player")))))))
                .then(literal("accept").then(argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestInvitedGroups(ctx, builder))
                        .executes(ctx -> run(ctx.getSource(),
                                src -> service().acceptInvite(player(src).getUUID(),
                                        StringArgumentType.getString(ctx, "name"))))))
                .then(literal("decline").then(argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestInvitedGroups(ctx, builder))
                        .executes(ctx -> run(ctx.getSource(),
                                src -> service().declineInvite(player(src).getUUID(),
                                        StringArgumentType.getString(ctx, "name"))))))
                .then(literal("leave").then(argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOwnGroups(ctx, builder))
                        .executes(ctx -> run(ctx.getSource(),
                                src -> service().leaveGroup(player(src).getUUID(),
                                        StringArgumentType.getString(ctx, "name"))))))
                .then(literal("kick").then(argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOwnGroups(ctx, builder))
                        .then(argument("player", StringArgumentType.word())
                                .suggests(GroupChatCommands::suggestOtherOnlinePlayers)
                                .executes(ctx -> run(ctx.getSource(), src -> service().kickMember(player(src).getUUID(),
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "player")))))))
                .then(literal("list").executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    String text = ChatFormat.color(service().listGroupsFormatted(player(source).getUUID()));
                    source.sendSuccess(() -> Component.literal(text), false);
                    return 1;
                }))
                .then(literal("members").then(argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOwnGroups(ctx, builder))
                        .executes(ctx -> run(ctx.getSource(),
                                src -> service().members(player(src).getUUID(),
                                        StringArgumentType.getString(ctx, "name")))))));

        registerGroupMessageCommand(dispatcher, "gmsg", false);
        registerGroupMessageCommand(dispatcher, "gm", false);
        registerGroupMessageCommand(dispatcher, "gmoffline", true);

        dispatcher.register(literal("dm")
                .then(argument("player", StringArgumentType.word())
                        .suggests(GroupChatCommands::suggestOtherOnlinePlayers)
                        .then(argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    CommandResult result = service().sendDirectMessage(
                                            player(ctx.getSource()).getUUID(),
                                            StringArgumentType.getString(ctx, "player"),
                                            StringArgumentType.getString(ctx, "message"));
                                    sendResult(ctx.getSource(), result);
                                    return 1;
                                }))));

        dispatcher.register(literal("unread")
                .then(argument("page", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            String raw = StringArgumentType.getString(ctx, "page");
                            if (raw.equalsIgnoreCase("clear")) {
                                for (String line : service().viewAndClearUnread(player(source).getUUID())) {
                                    source.sendSuccess(() -> Component.literal(line), false);
                                }
                                return 1;
                            }
                            try {
                                int page = Integer.parseInt(raw);
                                for (String line : service().viewUnreadPage(player(source).getUUID(), page)) {
                                    source.sendSuccess(() -> Component.literal(line), false);
                                }
                            } catch (NumberFormatException ex) {
                                for (String line : service().viewAndClearUnread(player(source).getUUID())) {
                                    source.sendSuccess(() -> Component.literal(line), false);
                                }
                            }
                            return 1;
                        }))
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    for (String line : service().viewAndClearUnread(player(source).getUUID())) {
                        source.sendSuccess(() -> Component.literal(line), false);
                    }
                    return 1;
                }));

        dispatcher.register(literal("groupgui").executes(ctx -> {
            GroupChatMod.gui.openMainMenu(player(ctx.getSource()));
            return 1;
        }));
    }

    /**
     * /gmsg, /gm (alias), and /gmoffline (persistent variant) all share this shape.
     */
    private static void registerGroupMessageCommand(CommandDispatcher<CommandSourceStack> dispatcher, String name,
            boolean persistent) {
        LiteralArgumentBuilder<CommandSourceStack> command = literal(name)
                .then(argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOwnGroups(ctx, builder))
                        .then(argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    CommandResult result = persistent
                                            ? service().sendGroupMessagePersistent(player(ctx.getSource()).getUUID(),
                                                    StringArgumentType.getString(ctx, "name"),
                                                    StringArgumentType.getString(ctx, "message"))
                                            : service().sendGroupMessage(player(ctx.getSource()).getUUID(),
                                                    StringArgumentType.getString(ctx, "name"),
                                                    StringArgumentType.getString(ctx, "message"));
                                    sendResult(ctx.getSource(), result);
                                    return 1;
                                })));
        dispatcher.register(command);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOwnGroups(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(service().getGroupNamesForPlayer(player(ctx.getSource()).getUUID()),
                builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestInvitedGroups(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(service().getInvitedGroupNames(player(ctx.getSource()).getUUID()),
                builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOtherOnlinePlayers(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        ServerPlayer self = player(ctx.getSource());
        String selfName = self.getName().getString();
        String[] names = self.level().getServer().getPlayerNames();
        return SharedSuggestionProvider.suggest(
                java.util.Arrays.stream(names).filter(n -> !n.equals(selfName)), builder);
    }

    private static GroupChatService service() {
        return GroupChatMod.service;
    }

    /**
     * getPlayerOrException() throws a checked CommandSyntaxException (for when
     * the command sender isn't a player, e.g. run from console). The generic
     * Function<CommandSourceStack, CommandResult> lambdas used by run() below
     * can't declare checked exceptions, so this converts it to an unchecked
     * one at the boundary. Anyone hitting this will get a stack trace instead
     * of a friendly message - fine for now since these are player-only chat
     * commands, but worth wrapping in a nicer error if you ever expose them
     * to console/command blocks.
     */
    private static ServerPlayer player(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("This command can only be used by a player.", e);
        }
    }

    private static int run(CommandSourceStack source, Function<CommandSourceStack, CommandResult> action) {
        sendResult(source, action.apply(source));
        return 1;
    }

    private static void sendResult(CommandSourceStack source, CommandResult result) {
        if (result.getMessage() == null) {
            return;
        }
        String text = ChatFormat.color(result.getMessage());
        source.sendSuccess(() -> Component.literal(text), false);
    }
}