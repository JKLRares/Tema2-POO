package main.commands;

/**
 * Expected validation failure; execution defects must propagate rather than appear as bad input.
 */
public final class InvalidCommandException extends IllegalArgumentException {
    public InvalidCommandException(final String message) {
        super(message);
    }
}
