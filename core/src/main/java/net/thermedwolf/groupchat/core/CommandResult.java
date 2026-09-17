package net.thermedwolf.groupchat.core;

/**
 * Result of a service call. {@code message} may be {@code null} for actions
 * that already delivered their own feedback (e.g. a live chat message).
 * Messages contain raw '&' colour codes - run them through {@link ChatFormat#color}
 * before displaying.
 */
public class CommandResult {

    private final boolean success;
    private final String message;

    private CommandResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static CommandResult ok(String message) {
        return new CommandResult(true, message);
    }

    public static CommandResult fail(String message) {
        return new CommandResult(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
